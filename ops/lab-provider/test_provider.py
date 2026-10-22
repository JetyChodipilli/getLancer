"""Executable protocol/lifecycle tests with an explicitly disposable fake hypervisor.

These tests do not run or certify KVM, systemd, jailer, guest networking or images.
The production CLI has no test driver option or environment-variable bypass.
"""
import copy
import datetime as dt
import hashlib
import http.client
import io
import json
import os
from pathlib import Path
import socket
import stat
import struct
import tempfile
import threading
import time
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import uuid
import zipfile
import warnings

from builder_agent import extract_archive, compile_source
from firecracker import Firecracker
from guest_agent import Agent, OperationScope, invoke
from inventory import Inventory, asset, trusted_path
from protocol import command, digest, encode, FIELDS, instant, Rejected, result
from runner import Runner
from transport import Gateway, RunnerHandler, forward, read_frame
from service import stale_socket
from guest_native import NativeHTTP


def uid():
    return str(uuid.uuid4())


def timestamp(value):
    return dt.datetime.fromtimestamp(value, dt.timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")


class Clock:
    def __init__(self):
        self.now = time.time()
    def __call__(self):
        return self.now


class DisposableHypervisor:
    """Test-only physical state, deliberately independent from the durable runner DB."""
    def __init__(self, clock):
        self.clock, self.active = clock, {}
        self.starts = self.requests = self.stops = 0
        self.cleanup_ok = self.start_ok = self.health_ok = True
        self.output = "fixed operation result"
        self.raise_after_launch = False
    def start(self, entry, cmd, slot):
        self.starts += 1
        self.active[cmd["runId"]] = (instant(cmd["expiresAt"]), slot)
        if self.raise_after_launch:
            raise OSError("simulated crash after launch")
        return self.start_ok
    def reap_supervisor_deadlines(self):
        for run_id, (expiry, _) in list(self.active.items()):
            if expiry <= self.clock():
                del self.active[run_id]
    def healthy(self, cmd):
        self.reap_supervisor_deadlines()
        return self.health_ok and cmd["runId"] in self.active
    def request(self, cmd):
        self.requests += 1
        return self.output, "scenario completed"
    def discover(self):
        return set(self.active)
    def stop(self, run_id):
        self.stops += 1
        if self.cleanup_ok:
            self.active.pop(run_id, None)
        return self.cleanup_ok


class DisposableOperationScope:
    """Test controller seam; production requires the kernel's guest cgroup-v2 implementation."""
    def child_setup(self):
        pass
    def close(self):
        pass


class EntrypointTests(unittest.TestCase):
    def test_offline_build_recipes_select_fixed_tools_and_reject_unknown_languages(self):
        sources = {"python": ("main.py", "print('ready')"), "java": ("Main.java", "class Main {}"),
            "javascript": ("main.mjs", "console.log('ready')"), "typescript": ("main.ts", "const n:number=1;"),
            "go": ("main.go", "package main;func main(){}"), "dotnet": ("Main.csproj", "<Project/>"),
            "php": ("main.php", "<?php echo 'ready';")}
        tools = {"python": "/usr/bin/python3", "java": "/usr/bin/javac", "javascript": "/usr/bin/node",
            "typescript": "/opt/toolchains/typescript/bin/tsc", "go": "/usr/bin/go", "dotnet": "/usr/bin/dotnet", "php": "/usr/bin/php"}
        for language, (entry, body) in sources.items():
            with tempfile.TemporaryDirectory() as folder, patch("builder_agent.tool") as execute:
                source = Path(folder)
                (source/entry).write_text(body)
                recipe = {"language": language, "sourceEntry": entry, "installPrefix": "opt/lab", "runtimePolicy": {"scenarios": {}}}
                compile_source(source, recipe)
                self.assertEqual(execute.call_args_list[0].args[0][0], tools[language])
                if language == "dotnet":
                    restore = execute.call_args_list[0].args[0]
                    self.assertEqual(restore[restore.index("--source")+1], "/opt/offline-nuget")
                    self.assertIn("--no-restore", execute.call_args_list[1].args[0])
                with self.assertRaises(Rejected):
                    compile_source(source, dict(recipe, language="uploaded-shell"))

    def test_isolated_python_entrypoints_import_from_unrelated_working_directory(self):
        import subprocess
        import sys
        directory = Path(__file__).resolve().parent
        with tempfile.TemporaryDirectory() as folder:
            for name in ("service.py", "guest_agent.py", "builder_agent.py", "trusted_build.py"):
                run = subprocess.run([sys.executable, "-I", "-c",
                    "import runpy,sys;runpy.run_path(sys.argv[1],run_name='isolated_import');print('IMPORTED')",
                    str(directory/name)], cwd=folder, capture_output=True, text=True, timeout=5)
                self.assertEqual(run.returncode, 0, run.stderr)
                self.assertEqual(run.stdout.strip(), "IMPORTED")

    def test_stale_socket_recovery_accepts_only_root_or_configured_gateway_socket(self):
        from unittest.mock import MagicMock
        for owner in (0, 61999, 12345):
            for kind in (stat.S_IFSOCK, stat.S_IFREG):
                path = MagicMock()
                path.exists.return_value = True
                path.is_symlink.return_value = False
                path.lstat.return_value = SimpleNamespace(st_mode=kind | 0o600, st_uid=owner)
                with patch("service.Path", return_value=path):
                    if kind == stat.S_IFSOCK and owner in (0, 61999):
                        stale_socket("/root-controlled/control.sock", 61999)
                        path.unlink.assert_called_once_with()
                    else:
                        with self.assertRaises(Rejected):
                            stale_socket("/root-controlled/control.sock", 61999)
                        path.unlink.assert_not_called()

    def test_native_evidence_rejects_foreign_replayed_and_misbound_events_atomically(self):
        import copy
        policy = {"argv": ["/bin/true"], "cwd": "/fixture", "source": "/fixture/source.py",
            "sourceSha256": "a"*64, "language": "python", "pattern": "security",
            "operations": {"authorize": ["identity"]}, "redisSha256": None}
        cmd = {"runId": uid(), "operationId": "authorize", "inputs": {"identity": "editor"}}
        request_id = uid()
        event = {"runId": cmd["runId"], "sourceHash": policy["sourceSha256"], "requestId": request_id,
            "sequence": 1, "type": "AUTH_ALLOWED", "edge": "service-policy",
            "recordedAt": timestamp(time.time()), "summary": "Synthetic role allowed."}
        value = {"runId": cmd["runId"], "sourceHash": policy["sourceSha256"], "requestId": request_id,
            "pattern": "security", "status": 200, "durationMs": 1, "state": {}, "events": [event]}
        for key, invalid in (("runId", uid()), ("sourceHash", "b"*64), ("requestId", uid()),
                ("sequence", 0), ("sequence", True), ("type", "CACHE_HIT"), ("edge", "emulator-ledger"),
                ("recordedAt", "not-a-time")):
            native = NativeHTTP(policy)
            native.binding = cmd
            bad = copy.deepcopy(value)
            bad["events"].append(dict(event, sequence=2))
            bad["events"][1][key] = invalid
            with patch.object(native, "healthy", return_value=True), patch.object(native, "call", return_value=(200, bad)):
                with self.assertRaises(Rejected, msg=key):
                    native.request(cmd)
            self.assertEqual(native.sequence, 0, "Rejected batches must not partially advance authority")
        native = NativeHTTP(policy)
        native.binding = cmd
        with patch.object(native, "healthy", return_value=True), patch.object(native, "call", return_value=(200, value)) as call:
            self.assertEqual(json.loads(native.request(cmd)["output"])["events"][0]["sequence"], 1)
            with self.assertRaises(Rejected):
                native.request(cmd)
            calls = call.call_count
            with self.assertRaises(Rejected):
                native.request(dict(cmd, runId=uid()))
            self.assertEqual(call.call_count, calls, "Foreign commands must fail before forwarding")
            value["events"][0]["sequence"] = 2
            self.assertEqual(json.loads(native.request(cmd)["output"])["events"][0]["sequence"], 2)

    def test_native_bridge_uses_actual_python_http_source_and_current_operation_state(self):
        import sys
        source = Path(__file__).resolve().parents[2]/"labs/python/server.py"
        with socket.socket() as reserved:
            reserved.bind(("127.0.0.1", 0))
            port = reserved.getsockname()[1]
        policy = {"argv": [sys.executable, str(source)], "cwd": str(source.parent), "source": str(source),
            "sourceSha256": hashlib.sha256(source.read_bytes()).hexdigest(), "language": "python",
            "pattern": "security", "operations": {"authorize": ["identity"], "revoke": [], "reset": []}, "redisSha256": None}
        cmd = {"runId": uid(), "expiresAt": timestamp(time.time()+10), "operationId": "authorize", "inputs": {"identity": "editor"}}
        with patch.object(NativeHTTP, "PORT", port):
            native = NativeHTTP(policy)
            try:
                native.start(cmd)
                first = json.loads(native.request(cmd)["output"])
                self.assertEqual(first["status"], 200)
                native.request(dict(cmd, operationId="revoke", inputs={}))
                revoked = json.loads(native.request(cmd)["output"])
                self.assertEqual(revoked["status"], 403)
                self.assertEqual(revoked["runId"], cmd["runId"])
                self.assertEqual(revoked["sourceHash"], policy["sourceSha256"])
                with self.assertRaises(Rejected):
                    native.request(dict(cmd, inputs={"identity": "editor", "url": "https://outside.invalid"}))
            finally:
                native.close()
            self.assertFalse(native.healthy())


def inventory_value(now):
    return {"version": 1, "entries": [{"manifestSha256": "a" * 64, "imageDigest": "sha256:" + "b" * 64,
      "sourceSha256": "c" * 64, "archiveSha256": "d" * 64, "kernel": "/reviewed/kernel", "kernelSha256": "e" * 64,
      "rootfs": "/reviewed/runtime.ext4", "rootfsSha256": "b" * 64, "archive": "/reviewed/source.zip",
      "memoryMiB": 128, "approvedUntil": timestamp(now + 86400), "scenarios": {"sample": {
        "operations": ["evaluate", "reset"], "inputs": {"message": {"type": "TEXT", "required": False,
          "maxLength": 80, "min": 0, "max": 100, "choices": []}, "count": {"type": "INTEGER", "required": False,
          "maxLength": 5, "min": 0, "max": 100, "choices": []}}}}}]}


class ProviderFixture:
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.clock, self.epoch = Clock(), uid()
        self.inventory = Inventory(inventory_value(self.clock()))
        self.driver = DisposableHypervisor(self.clock)
        # Only filesystem asset opening is substituted. Real inventory/schema/operation checks still run.
        self.assets = patch("inventory.asset", side_effect=lambda path, sha: path)
        self.assets.start()
        self.runner = Runner(self.temp.name, self.epoch, self.inventory, self.driver, self.clock)

    def tearDown(self):
        self.runner.close()
        self.assets.stop()
        self.temp.cleanup()

    def cmd(self, **changes):
        value = dict(runId=uid(), leaseGeneration=1, operatorEpoch=self.epoch, commandId=uid(), manifestSha256="a" * 64,
                     imageDigest="sha256:" + "b" * 64, expiresAt=timestamp(self.clock() + 299), action="START",
                     scenarioId="sample", operationId=None, inputs={"message": "hello"}, memoryMiB=128)
        value.update(changes)
        return value

    def send(self, cmd):
        return self.runner.execute(encode(cmd))

    def change(self, cmd, **values):
        return dict(cmd, commandId=uid(), **values)

    def restart(self, epoch=None):
        self.runner.close()
        self.runner = Runner(self.temp.name, epoch or self.epoch, self.inventory, self.driver, self.clock)


class LifecycleTests(ProviderFixture, unittest.TestCase):
    def test_start_request_duplicate_result_stop_with_java_identity_echo(self):
        cmd = self.cmd()
        started = self.send(cmd)
        self.assertEqual(started["state"], "RUNNING")
        for key in ("runId", "leaseGeneration", "operatorEpoch", "commandId", "manifestSha256", "imageDigest", "expiresAt"):
            self.assertEqual(started[key], cmd[key])
        request = self.change(cmd, action="REQUEST", operationId="evaluate")
        first = self.send(request)
        self.assertEqual(first["state"], "RESULT")
        self.assertEqual(self.send(request), first)
        self.assertEqual(self.driver.requests, 1)
        stop = self.change(cmd, action="STOP", leaseGeneration=2, inputs={})
        self.assertEqual(self.send(stop)["state"], "CLEANED")
        self.assertTrue(self.send(stop)["everHealthy"])
        self.assertFalse(self.driver.active)
        with self.assertRaises(Rejected):
            self.send(cmd)

    def test_concurrent_retries_launch_only_one_vm(self):
        cmd = self.cmd()
        results = []
        threads = [threading.Thread(target=lambda: results.append(self.send(cmd))) for _ in range(8)]
        for thread in threads:
            thread.start()
        for thread in threads:
            thread.join()
        self.assertEqual(len(results), 8)
        self.assertTrue(all(r["state"] == "RUNNING" for r in results))
        self.assertEqual(self.driver.starts, 1)

    def test_stop_before_start_is_a_durable_tombstone(self):
        cmd = self.cmd()
        stop = self.change(cmd, action="STOP", leaseGeneration=2, inputs={})
        cleaned = self.send(stop)
        self.assertEqual(cleaned["state"], "CLEANED")
        self.assertFalse(cleaned["everHealthy"])
        self.restart()
        with self.assertRaises(Rejected):
            self.send(cmd)
        self.assertEqual(self.driver.starts, 0)

    def test_command_id_and_run_identity_conflicts_cannot_mutate_a_run(self):
        cmd = self.cmd()
        self.send(cmd)
        for changed in (dict(cmd, inputs={"message": "different"}), dict(cmd, runId=uid()),
                        self.change(cmd, imageDigest="sha256:" + "f" * 64), self.change(cmd, expiresAt=timestamp(self.clock()+250)),
                        self.change(cmd, memoryMiB=256)):
            with self.assertRaises(Rejected):
                self.send(changed)
        self.assertEqual(self.driver.starts, 1)

    def test_stale_epoch_stale_generation_and_unknown_operations_fail_closed(self):
        cmd = self.cmd()
        self.send(cmd)
        for changed in (self.change(cmd, operatorEpoch=uid()), self.change(cmd, leaseGeneration=2),
                        self.change(cmd, action="REQUEST", operationId="shell"),
                        self.change(cmd, action="REQUEST", operationId="evaluate", inputs={"url": "https://outside.invalid"}),
                        self.change(cmd, action="REQUEST", operationId="evaluate", inputs={"count": "101"})):
            with self.assertRaises(Rejected):
                self.send(changed)
        self.assertEqual(self.driver.requests, 0)

    def test_immutable_assets_are_checked_before_every_routed_request(self):
        cmd = self.cmd()
        self.send(cmd)
        with patch("inventory.asset", side_effect=Rejected("ASSET_DIGEST_MISMATCH", 503)):
            with self.assertRaises(Rejected):
                self.send(self.change(cmd, action="REQUEST", operationId="evaluate"))
        self.assertEqual(self.driver.requests, 0)

    def test_expiry_is_independent_from_application_stop_and_result_replay(self):
        cmd = self.cmd()
        self.send(cmd)
        request = self.change(cmd, action="REQUEST", operationId="evaluate")
        self.send(request)
        self.clock.now += 301
        # Simulates the physical supervisor acting while both app and provider control loops are absent.
        self.driver.reap_supervisor_deadlines()
        self.assertFalse(self.driver.active)
        with self.assertRaises(Rejected):
            self.send(request)
        self.assertEqual(self.runner.row(cmd["runId"])["status"], "CLEANED")
        self.assertEqual(self.driver.requests, 1)

    def test_generation_cannot_extend_the_five_minute_hard_deadline(self):
        for duration in (0, -1, 301, 86400):
            with self.assertRaises(Rejected):
                self.send(self.cmd(expiresAt=timestamp(self.clock() + duration)))
        self.assertFalse(self.driver.active)

    def test_uncertain_cleanup_keeps_capacity_reserved_until_confirmed(self):
        self.runner.max_runs = 1
        self.driver.cleanup_ok = False
        self.driver.raise_after_launch = True
        cmd = self.cmd()
        self.assertEqual(self.send(cmd)["state"], "FAILED")
        self.assertEqual(self.runner.row(cmd["runId"])["status"], "CLEANING")
        with self.assertRaises(Rejected) as rejected:
            self.send(self.cmd())
        self.assertEqual(rejected.exception.code, "CAPACITY_RESERVED")
        self.driver.cleanup_ok, self.driver.raise_after_launch = True, False
        self.runner.sweep()
        self.assertEqual(self.send(self.cmd())["state"], "RUNNING")

    def test_stop_reports_pending_and_never_cleans_without_physical_confirmation(self):
        cmd = self.cmd()
        self.send(cmd)
        self.driver.cleanup_ok = False
        stop = self.change(cmd, action="STOP", leaseGeneration=2, inputs={})
        self.assertEqual(self.send(stop)["state"], "PENDING")
        self.assertIn(cmd["runId"], self.driver.active)
        self.driver.cleanup_ok = True
        self.assertEqual(self.send(stop)["state"], "CLEANED")

    def test_restart_and_epoch_rotation_quarantine_old_routes_but_permit_old_cleanup(self):
        cmd = self.cmd()
        self.send(cmd)
        self.restart(uid())
        self.assertFalse(self.driver.active)
        with self.assertRaises(Rejected):
            self.send(self.change(cmd, action="REQUEST", operationId="evaluate"))
        stop = self.change(cmd, action="STOP", leaseGeneration=2, inputs={})
        self.assertEqual(self.send(stop)["state"], "CLEANED")
        self.assertTrue(self.send(stop)["everHealthy"])

    def test_orphan_recovery_includes_physically_present_run_marked_cleaned_in_db(self):
        cmd = self.cmd()
        self.send(cmd)
        self.send(self.change(cmd, action="STOP", leaseGeneration=2, inputs={}))
        self.driver.active[cmd["runId"]] = (self.clock()+100, 0)
        self.driver.active[uid()] = (self.clock()+100, 1)
        self.restart()
        self.assertFalse(self.driver.active)
        self.assertTrue(self.runner.recovery_complete)

    def test_recovery_failure_blocks_all_new_admissions(self):
        self.driver.active[uid()] = (self.clock()+100, 0)
        self.driver.cleanup_ok = False
        self.restart()
        self.assertFalse(self.runner.recovery_complete)
        with self.assertRaises(Rejected):
            self.send(self.cmd())

    def test_pending_request_intent_is_never_executed_on_recovery(self):
        cmd = self.cmd()
        self.send(cmd)
        request = self.change(cmd, action="REQUEST", operationId="evaluate")
        with self.runner.transaction():
            self.runner.record(request, None)
        self.restart()
        with self.assertRaises(Rejected):
            self.send(request)
        self.assertEqual(self.driver.requests, 0)

    def test_health_loss_and_oversized_guest_output_close_execution(self):
        cmd = self.cmd()
        self.send(cmd)
        self.driver.output = "x" * 4097
        failed = self.send(self.change(cmd, action="REQUEST", operationId="evaluate"))
        self.assertEqual(failed["state"], "FAILED")
        self.assertFalse(failed["healthy"] or failed["isolated"])
        self.assertFalse(self.driver.active)

    def test_private_results_are_erased_without_reusing_command_identity(self):
        cmd = self.cmd()
        self.send(cmd)
        request = self.change(cmd, action="REQUEST", operationId="evaluate")
        self.send(request)
        self.send(self.change(cmd, action="STOP", leaseGeneration=2, inputs={}))
        self.clock.now += 86401
        self.runner.sweep()
        row = self.runner.db.execute("SELECT * FROM commands WHERE id=?", (request["commandId"],)).fetchone()
        self.assertIsNone(row["response"])
        self.assertEqual(row["hash"], digest(request))

    def test_single_writer_lock_rejects_second_runner(self):
        with self.assertRaises(Rejected) as rejected:
            Runner(self.temp.name, self.epoch, self.inventory, self.driver)
        self.assertEqual(rejected.exception.code, "RUNNER_ALREADY_ACTIVE")


class WireTests(ProviderFixture, unittest.TestCase):
    def setUp(self):
        super().setUp()
        self.gateway = Gateway(("127.0.0.1", 0), "s" * 48, lambda raw: (200, self.runner.execute(raw)))
        self.thread = threading.Thread(target=self.gateway.serve_forever, kwargs={"poll_interval": 0.01}, daemon=True)
        self.thread.start()
    def tearDown(self):
        self.gateway.shutdown()
        self.gateway.server_close()
        self.thread.join()
        super().tearDown()
    def http(self, body, headers=None, path="/v1/lab-commands"):
        connection = http.client.HTTPConnection(*self.gateway.server_address, timeout=2)
        headers = headers or {"Authorization": "Bearer " + "s" * 48, "Content-Type": "application/json"}
        connection.request("POST", path, body=body, headers=headers)
        response = connection.getresponse()
        status, content_type, payload = response.status, response.getheader("Content-Type"), response.read()
        connection.close()
        return status, content_type, decode_for_test(payload)
    def test_gateway_authentication_and_exact_path(self):
        self.assertEqual(self.http(encode(self.cmd()))[0:2], (200, "application/json"))
        for auth in ("", "Bearer bad", "Basic " + "s"*48):
            self.assertEqual(self.http(encode(self.cmd()), {"Authorization": auth, "Content-Type": "application/json"})[0], 403)
        self.assertEqual(self.http(encode(self.cmd()), path="/v1/lab-commands?target=other")[0], 404)
        self.assertEqual(self.driver.starts, 1)
    def test_unknown_fields_duplicate_keys_and_oversized_bodies_are_rejected(self):
        cmd = self.cmd()
        for raw in (encode(dict(cmd, argv=["sh", "-c", "bad"])), encode(cmd)[:-1] + b',"action":"STOP"}',
                    b'{"x":NaN}', b'[]', encode(dict(cmd, memoryMiB=True))):
            self.assertEqual(self.http(raw)[0], 400)
        self.assertEqual(self.http(b"x" * 65537)[0], 413)
        self.assertEqual(self.driver.starts, 0)
    def test_duplicate_auth_content_length_and_chunked_framing_cannot_reach_runner(self):
        for headers in ("Authorization: Bearer " + "s"*48 + "\r\nAuthorization: Bearer " + "s"*48 + "\r\nContent-Length: 2\r\n",
                        "Authorization: Bearer " + "s"*48 + "\r\nContent-Length: 2\r\nContent-Length: 2\r\n",
                        "Authorization: Bearer " + "s"*48 + "\r\nTransfer-Encoding: chunked\r\n"):
            with socket.create_connection(self.gateway.server_address, timeout=2) as connection:
                connection.sendall(("POST /v1/lab-commands HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/json\r\n" + headers + "\r\n{}").encode())
                self.assertNotIn(b" 200 ", connection.recv(4096))
        self.assertEqual(self.driver.starts, 0)


def decode_for_test(value):
    return json.loads(value)


class BoundaryTests(unittest.TestCase):
    def test_real_launch_recipe_has_hard_supervision_readonly_drives_and_no_nic(self):
        with tempfile.TemporaryDirectory() as folder:
            folder = Path(folder)
            driver = Firecracker.__new__(Firecracker)
            driver.base, driver.prefix = folder / "jails", "labvm"
            driver.base.mkdir()
            driver.config = {"uidBase": 62000}
            driver.firecracker, driver.jailer = "/reviewed/firecracker", "/reviewed/jailer"
            kernel, rootfs = folder / "kernel", folder / "rootfs"
            kernel.write_bytes(b"kernel")
            rootfs.write_bytes(b"rootfs")
            checks, launches = [], []
            def check_asset(path, expected):
                checks.append(str(path))
                require_digest = hashlib.sha256(Path(path).read_bytes()).hexdigest()
                if require_digest != expected:
                    raise Rejected("ASSET_DIGEST_MISMATCH", 503)
                return str(path)
            entry = {"kernel": str(kernel), "kernelSha256": hashlib.sha256(b"kernel").hexdigest(),
                     "rootfs": str(rootfs), "rootfsSha256": hashlib.sha256(b"rootfs").hexdigest()}
            cmd = {"runId": uid(), "expiresAt": timestamp(time.time()+299), "memoryMiB": 128}
            with patch("firecracker.asset", side_effect=check_asset), patch("firecracker.os.chown"), \
                 patch.object(driver, "call", side_effect=lambda argv, **_: launches.append(argv)), \
                 patch.object(driver, "isolated", return_value=True), patch.object(driver, "exchange", return_value={"state": "READY"}):
                self.assertTrue(driver.start(entry, cmd, 0))
            boot = json.loads((driver.root(cmd["runId"]) / "config.json").read_text())
            self.assertEqual(boot["network-interfaces"], [])
            self.assertTrue(all(d["is_read_only"] for d in boot["drives"]))
            self.assertEqual(boot["machine-config"]["vcpu_count"], 1)
            launched = launches[0]
            self.assertIn("--property=PrivateNetwork=yes", launched)
            self.assertIn("--property=ExitType=cgroup", launched)
            self.assertIn("--property=KillMode=control-group", launched)
            self.assertIn("--property=DevicePolicy=closed", launched)
            self.assertIn("--property=DeviceAllow=/dev/kvm rwm", launched)
            self.assertIn("--property=DeviceAllow=/dev/net/tun m", launched)
            self.assertNotIn("--property=DeviceAllow=/dev/net/tun rw", launched)
            self.assertNotIn("--property=DeviceAllow=/dev/net/tun rwm", launched)
            self.assertIn("--no-api", launched)
            self.assertNotIn("--no-seccomp", launched)
            ttl = int(next(v.split("=",2)[2] for v in launched if v.startswith("--property=RuntimeMaxSec=")))
            self.assertTrue(1 <= ttl <= 300)
            self.assertIn(str(driver.root(cmd["runId"]) / "kernel"), checks)
            self.assertIn(str(driver.root(cmd["runId"]) / "rootfs"), checks)
            self.assertEqual(stat.S_IMODE((driver.root(cmd["runId"])/"config.json").stat().st_mode), 0o400)
            source, output = folder/"source.zip", folder/"output.ext4"
            source.write_bytes(b"reviewed archive")
            output.write_bytes(b"\0"*512)
            import shutil
            copy = shutil.copyfileobj
            def private_copy(src, dst, length):
                self.assertEqual(stat.S_IMODE(os.fstat(dst.fileno()).st_mode), 0o600,
                    "source/output must be private before their first byte is copied")
                return copy(src, dst, length)
            build_cmd = dict(cmd, runId=uid())
            previous_mask = os.umask(0)
            try:
                with patch("firecracker.asset", side_effect=check_asset), patch("firecracker.trusted_path", side_effect=Path), \
                     patch("firecracker.os.chown") as owner, patch("firecracker.shutil.copyfileobj", side_effect=private_copy), \
                     patch.object(driver, "call"), patch.object(driver, "isolated", return_value=True), \
                     patch.object(driver, "exchange", return_value={"state": "READY"}):
                    self.assertTrue(driver.start(entry, build_cmd, 1, (("source",source,False),("output",output,True))))
                    for name, mode in (("source",0o400),("output",0o600),("config.json",0o400)):
                        staged = driver.root(build_cmd["runId"])/name
                        self.assertEqual(stat.S_IMODE(staged.stat().st_mode), mode)
                        owner.assert_any_call(staged, 62001, 62001)
            finally:
                os.umask(previous_mask)

    def test_linked_inode_is_reverified_before_any_vm_can_launch(self):
        with tempfile.TemporaryDirectory() as folder:
            folder = Path(folder)
            driver = Firecracker.__new__(Firecracker)
            driver.base, driver.prefix, driver.config = folder / "jails", "labvm", {"uidBase": 62000}
            driver.base.mkdir()
            original, replaced = folder / "kernel", folder / "replaced"
            original.write_bytes(b"reviewed")
            replaced.write_bytes(b"changed")
            original_hash = hashlib.sha256(b"reviewed").hexdigest()
            real_link = os.link
            def verify(path, expected):
                if hashlib.sha256(Path(path).read_bytes()).hexdigest() != expected:
                    raise Rejected("ASSET_DIGEST_MISMATCH", 503)
                return str(path)
            with patch("firecracker.asset", side_effect=verify), \
                 patch("firecracker.os.link", side_effect=lambda src, dst, **_: real_link(replaced, dst)), \
                 patch.object(driver, "call") as launch, self.assertRaises(Rejected):
                driver.start({"kernel": str(original), "kernelSha256": original_hash},
                             {"runId": uid(), "expiresAt": timestamp(time.time()+299)}, 0)
            launch.assert_not_called()

    def test_hierarchical_cgroup_population_supports_an_empty_parent_procs_file(self):
        with tempfile.TemporaryDirectory() as folder:
            folder = Path(folder)
            driver = Firecracker.__new__(Firecracker)
            driver.prefix = "labvm"
            cmd = {"runId": uid(), "memoryMiB": 128}
            unit = driver.unit(cmd["runId"])
            group = folder / "system.slice" / unit
            group.mkdir(parents=True)
            for name, value in {"cgroup.events": "populated 1\nfrozen 0\n", "cgroup.procs": "",
                                "memory.max": str(256*1048576), "pids.max": "64", "cpu.max": "100000 100000"}.items():
                (group/name).write_text(value)
            state = {"ActiveState": "active", "ControlGroup": "/system.slice/"+unit, "KillMode": "control-group",
                     "ExitType": "cgroup", "PrivateNetwork": "yes", "RuntimeMaxUSec": "4min 59s"}
            with patch.object(driver, "state", return_value=state), patch.object(driver, "verify_process") as process, \
                 patch("firecracker.Path", return_value=folder):
                self.assertTrue(driver.isolated(cmd))
                process.assert_called_once()
                (group/"cgroup.events").write_text("populated 0\nfrozen 0\n")
                with self.assertRaises(Rejected):
                    driver.isolated(cmd)

    def test_actual_process_identity_requires_guest_uid_seccomp_cgroup_and_pinned_executable(self):
        with tempfile.TemporaryDirectory() as folder:
            folder = Path(folder)
            driver = Firecracker.__new__(Firecracker)
            driver.base = folder/"jails"
            run_id = uid()
            root = driver.root(run_id)
            root.mkdir(parents=True)
            (root/"firecracker.pid").write_text("12345")
            proc = folder/"proc"/"12345"
            proc.mkdir(parents=True)
            (proc/"status").write_text("Uid:\t62000\t62000\t62000\t62000\nGid:\t62000\t62000\t62000\t62000\nSeccomp:\t2\n")
            expected = "/system.slice/labvm-"+run_id+".service"
            (proc/"cgroup").write_text("0::"+expected+"/descendant\n")
            (proc/"exe").write_bytes(b"pinned executable")
            driver.config = {"uidBase": 62000, "firecrackerSha256": hashlib.sha256(b"pinned executable").hexdigest()}
            def namespace(path):
                path = str(path)
                if path.endswith("/root"):
                    return str(root)
                return "host" if path.startswith("/proc/self/") else "isolated"
            with patch("firecracker.os.readlink", side_effect=namespace):
                driver.verify_process(run_id, expected, folder/"proc")
                (proc/"cgroup").write_text("0::/system.slice/other.service\n")
                with self.assertRaises(Rejected):
                    driver.verify_process(run_id, expected, folder/"proc")
                (proc/"cgroup").write_text("0::"+expected+"\n")
                (proc/"exe").write_bytes(b"replaced executable")
                with self.assertRaises(Rejected):
                    driver.verify_process(run_id, expected, folder/"proc")

    def test_runner_socket_is_owned_by_exact_gateway_uid_with_private_mode(self):
        from transport import RunnerSocket
        with patch("transport.socketserver.UnixStreamServer.__init__"), patch("transport.os.chown") as owner, patch("transport.os.chmod") as mode:
            RunnerSocket("/run/reviewed/control.sock", None, 12345)
            owner.assert_called_once_with("/run/reviewed/control.sock", 12345, -1)
            mode.assert_called_once_with("/run/reviewed/control.sock", 0o600)
    def test_unix_socket_checks_both_peer_credentials_and_bounds_frames(self):
        # This sandbox disallows socket(AF_UNIX), but permits connected AF_UNIX socketpair.
        # Exercise the production handler, framing and real SO_PEERCRED over those endpoints.
        class EchoRunner:
            calls = 0
            def execute(self, raw):
                self.calls += 1
                return {"state": "CLEANED"}
        runner = EchoRunner()
        for gateway_uid, expected_uid, status in ((os.getuid(), os.getuid(), 200),
                                                   (os.getuid()+1, os.getuid(), 403),
                                                   (os.getuid(), os.getuid()+1, None)):
            left, right = socket.socketpair()
            server = SimpleNamespace(runner=runner, gateway_uid=gateway_uid)
            thread = threading.Thread(target=lambda: RunnerHandler(left, "local", server), daemon=True)
            thread.start()
            try:
                class Connected:
                    def __enter__(self):
                        return self
                    def __exit__(self, *_):
                        right.close()
                    def connect(self, _):
                        pass
                    def __getattr__(self, key):
                        return getattr(right, key)
                with patch("transport.socket.socket", return_value=Connected()):
                    if status:
                        self.assertEqual(forward("/test/socketpair", b"{}", expected_uid=expected_uid)[0], status)
                    else:
                        with self.assertRaises(Rejected):
                            forward("/test/socketpair", b"{}", expected_uid=expected_uid)
            finally:
                right.close()
                thread.join()
                left.close()
        self.assertEqual(runner.calls, 1)
    def test_protocol_has_no_path_or_command_surface(self):
        self.assertEqual(len(FIELDS), 12)
        for bad in (b'{"a":1,"a":2}', b'{"a":NaN}', b'"\xff"'):
            with self.assertRaises(Rejected):
                command(bad)
    def test_real_backend_refuses_unprivileged_or_missing_kvm_without_fallback(self):
        with patch("firecracker.os.geteuid", return_value=1234):
            with self.assertRaises(Rejected):
                Firecracker({})
        with patch("firecracker.os.geteuid", return_value=0), patch("firecracker.trusted_path", return_value=Path("/reviewed")), \
             patch("firecracker.asset", return_value="/reviewed/firecracker"), patch("firecracker.os.access", return_value=True), \
             patch("firecracker.os.stat", side_effect=FileNotFoundError):
            with self.assertRaises((Rejected, FileNotFoundError)):
                Firecracker({"uidBase": 62000, "jailBase": "/reviewed", "firecracker": "/reviewed/firecracker",
                             "firecrackerSha256": "a"*64, "jailer": "/reviewed/jailer", "jailerSha256": "b"*64})
    def test_writable_ancestors_and_symlink_assets_are_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            target = Path(folder) / "image"
            target.write_bytes(b"fixed")
            link = Path(folder) / "link"
            link.symlink_to(target)
            for path in (target, link):
                with self.assertRaises(Rejected):
                    asset(str(path), hashlib.sha256(b"fixed").hexdigest())
    def test_framing_rejects_length_before_allocating_body(self):
        left, right = socket.socketpair()
        try:
            left.sendall(struct.pack("!I", 2**32-1))
            with self.assertRaises(Rejected):
                read_frame(right, 65536)
        finally:
            left.close()
            right.close()
    def test_archive_traversal_symlink_duplicate_and_compression_bomb_are_rejected(self):
        bad_entries = [[("../escape", b"x")], [("/absolute", b"x")], [("ok", b"one"), ("ok", b"two")], [("bomb", b"x"*200000)]]
        link = zipfile.ZipInfo("symlink")
        link.create_system = 3
        link.external_attr = (stat.S_IFLNK | 0o777) << 16
        bad_entries.append([(link, b"/outside")])
        for entries in bad_entries:
            bundle = io.BytesIO()
            with warnings.catch_warnings(), zipfile.ZipFile(bundle, "w", compression=zipfile.ZIP_DEFLATED) as archive:
                warnings.simplefilter("ignore", UserWarning)
                for name, content in entries:
                    archive.writestr(name, content)
            bundle.seek(0)
            with tempfile.TemporaryDirectory() as folder, self.assertRaises(Rejected):
                extract_archive(bundle, Path(folder))
    def test_guest_fixed_argv_uses_json_stdin_and_bounds_output_timeout(self):
        import sys
        program = "import json,sys; x=json.load(sys.stdin); print(json.dumps({'output':x['value'],'summary':'ok'}))"
        hostile = "$(touch /tmp/never); `id`; https://outside.invalid"
        self.assertEqual(invoke([sys.executable, "-c", program], {"value": hostile}, scope_factory=DisposableOperationScope)["output"], hostile)
        with self.assertRaises(Rejected):
            invoke([sys.executable, "-c", "print('x'*20000)"], {}, scope_factory=DisposableOperationScope)
        with self.assertRaises(Rejected):
            invoke([sys.executable, "-c", "import time;time.sleep(2)"], {}, timeout=0.05, scope_factory=DisposableOperationScope)

    def test_guest_operation_scope_kills_detached_descendants_on_success_and_timeout(self):
        import select
        import signal
        import sys
        for hangs in (False, True):
            with tempfile.TemporaryDirectory() as folder:
                marker = Path(folder)/"detached.pid"
                class TrackedScope(DisposableOperationScope):
                    closed = False
                    exited = False
                    def close(self):
                        self.closed = True
                        if marker.exists():
                            # pidfd follows this PID namespace; inherited /proc may expose the host's unrelated PID.
                            descriptor = os.pidfd_open(int(marker.read_text()))
                            try:
                                exited = select.poll()
                                exited.register(descriptor, select.POLLIN)
                                os.kill(int(marker.read_text()), signal.SIGKILL)
                                self.exited = bool(exited.poll(1000))
                            finally:
                                os.close(descriptor)
                scope = TrackedScope()
                program = ("import os,sys,time,json; pid=os.fork(); "
                           "exec(\"if pid==0:\\n os.setsid(); os.close(0); os.close(1); os.close(2); "
                           "open(sys.argv[1],'w').write(str(os.getpid())); time.sleep(30); os._exit(0)\"); "
                           "\nwhile not os.path.exists(sys.argv[1]): time.sleep(.005)\n" +
                           ("time.sleep(30)" if hangs else "print(json.dumps({'output':'done','summary':'ok'}))"))
                script = Path(folder)/"operation.py"
                script.write_text(program)
                try:
                    if hangs:
                        with self.assertRaises(Rejected):
                            invoke([sys.executable, str(script), str(marker)], {}, timeout=0.1, scope_factory=lambda: scope)
                    else:
                        self.assertEqual(invoke([sys.executable, str(script), str(marker)], {}, scope_factory=lambda: scope)["output"], "done")
                    self.assertTrue(scope.closed)
                    self.assertTrue(scope.exited, "detached descendant must actually exit after cleanup")
                finally:
                    if marker.exists():
                        try:
                            os.kill(int(marker.read_text()), signal.SIGKILL)
                        except ProcessLookupError:
                            pass

    def test_production_guest_scope_requires_recursive_kill_and_drops_privileges(self):
        scope = OperationScope.__new__(OperationScope)
        scope.path = Path("/guest/operation")
        scope.libc = SimpleNamespace(prctl=lambda *args: 0)
        with patch("guest_agent.os.open", return_value=9), patch("guest_agent.os.write") as join, \
             patch("guest_agent.os.close"), patch("guest_agent.os.setgroups") as groups, \
             patch("guest_agent.os.setgid") as gid, patch("guest_agent.os.setuid") as user:
            scope.child_setup()
            join.assert_called_once_with(9, b"0")
            groups.assert_called_once_with([])
            gid.assert_called_once_with(1000)
            user.assert_called_once_with(1000)
        with tempfile.TemporaryDirectory() as folder:
            scope.path = Path(folder)/"scope"
            scope.path.mkdir()
            (scope.path/"cgroup.kill").write_text("")
            (scope.path/"cgroup.events").write_text("populated 1\n")
            with patch("guest_agent.time.monotonic", side_effect=[0, 1]), self.assertRaises(Rejected):
                scope.close()
            self.assertEqual((scope.path/"cgroup.kill").read_text(), "1")


if __name__ == "__main__":
    unittest.main()
