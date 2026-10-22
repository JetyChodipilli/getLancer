"""Concrete resident bridge for the repository's six native HTTP adapters.

Guest-only: all paths/argv/digests come from the immutable rootfs policy. This
module is never imported by the host gateway, runner or build broker.
"""
import hashlib
import http.client
import json
import os
from pathlib import Path
import signal
import socket
import subprocess
import threading
import time
from urllib.parse import urlencode

from protocol import decode, encode, HASH, identity, instant, require, text


class NativeHTTP:
    PORT = 8100
    def __init__(self, policy):
        require(isinstance(policy, dict) and policy.keys() == {"argv", "cwd", "source", "sourceSha256", "language",
                "pattern", "operations", "redisSha256"}, "INVALID_NATIVE_POLICY", 503)
        require(policy["language"] in ("python", "java", "typescript", "go", "dotnet", "php") and
                policy["pattern"] in ("cache", "security", "payment") and isinstance(policy["operations"], dict)
                and isinstance(policy["sourceSha256"], str) and HASH.fullmatch(policy["sourceSha256"]), "INVALID_NATIVE_POLICY", 503)
        require(isinstance(policy["argv"], list) and 1 <= len(policy["argv"]) <= 20 and
                all(isinstance(v, str) and len(v) <= 256 for v in policy["argv"]) and policy["argv"][0].startswith("/")
                and policy["source"].startswith("/") and policy["cwd"].startswith("/"), "INVALID_NATIVE_POLICY", 503)
        for operation, inputs in policy["operations"].items():
            require(isinstance(operation, str) and isinstance(inputs, list) and len(inputs) <= 20
                    and all(isinstance(v, str) for v in inputs), "INVALID_NATIVE_POLICY", 503)
        self.policy, self.processes = policy, []
        self.binding = None
        self.sequence, self.request_lock = 0, threading.Lock()
        self.blackhole, self.stopped = None, threading.Event()

    def start(self, cmd):
        require(hashlib.sha256(Path(self.policy["source"]).read_bytes()).hexdigest() == self.policy["sourceSha256"],
                "NATIVE_SOURCE_MISMATCH", 502)
        self.binding = cmd
        environment = {"PATH": "/usr/bin:/bin", "LANG": "C.UTF-8", "LAB_RUN_ID": cmd["runId"],
                       "LAB_PORT": str(self.PORT), "LAB_REDIS_PORT": "6379", "LAB_REDIS_FAILURE_PORT": "6380",
                       "LAB_REDIS_TIMEOUT_PORT": "6381", "LAB_SOURCE_PATH": self.policy["source"],
                       "LAB_LIFETIME_SECONDS": str(max(1, min(300, int(instant(cmd["expiresAt"])-time.time())))),
                       "DOTNET_CLI_TELEMETRY_OPTOUT": "1", "DOTNET_SKIP_FIRST_TIME_EXPERIENCE": "1"}
        if self.policy["pattern"] == "cache":
            redis = Path("/usr/bin/redis-server")
            require(self.policy["redisSha256"] and hashlib.sha256(redis.read_bytes()).hexdigest() == self.policy["redisSha256"],
                    "REDIS_NOT_PINNED", 502)
            version = subprocess.run([str(redis), "--version"], stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                                     stderr=subprocess.DEVNULL, timeout=1, env=environment, check=True)
            require(len(version.stdout) <= 1024 and b"v=7.2.16 " in version.stdout, "REDIS_NOT_PINNED", 502)
            self.launch([str(redis), "--bind", "127.0.0.1", "--port", "6379", "--protected-mode", "yes",
                         "--save", "", "--appendonly", "no", "--maxmemory", "16mb", "--maxclients", "16"], environment)
            self.blackhole = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.blackhole.bind(("127.0.0.1", 6381))
            self.blackhole.listen(2)
            self.blackhole.settimeout(0.1)
            def blackhole():
                while not self.stopped.is_set():
                    try:
                        connection, _ = self.blackhole.accept()
                    except socket.timeout:
                        continue
                    except OSError:
                        return
                    with connection:
                        # Real accepted TCP blackhole, never an HTTP/provider response fixture.
                        self.stopped.wait(0.4)
            threading.Thread(target=blackhole, daemon=True).start()
        self.launch(self.policy["argv"], environment)
        deadline = time.monotonic() + 1.5
        while time.monotonic() < deadline:
            try:
                if self.healthy():
                    return
            except (OSError, http.client.HTTPException):
                pass
            time.sleep(0.025)
        require(False, "NATIVE_NOT_READY", 502)

    def launch(self, argv, environment):
        self.processes.append(subprocess.Popen(argv, stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL, cwd=self.policy["cwd"], env=environment, close_fds=True, start_new_session=True))

    def call(self, method, path, body=None):
        connection = http.client.HTTPConnection("127.0.0.1", self.PORT, timeout=0.4)
        try:
            connection.request(method, path, body=body, headers={"Content-Type": "application/x-www-form-urlencoded"} if body else {})
            response = connection.getresponse()
            require(response.getheader("Content-Type", "").startswith("application/json"), "NATIVE_UNCONFIRMED", 502)
            raw = response.read(4097)
            require(len(raw) <= 4096, "NATIVE_OUTPUT_LIMIT", 502)
            return response.status, decode(raw, 4096)
        finally:
            connection.close()

    def healthy(self):
        if not self.processes or any(process.poll() is not None for process in self.processes):
            return False
        status, value = self.call("GET", "/health")
        return status == 200 and isinstance(value, dict) and value.get("runId") == self.binding["runId"] and \
            value.get("sourceHash") == self.policy["sourceSha256"] and value.get("language") == self.policy["language"]

    def request(self, cmd):
        with self.request_lock:
            return self.request_serial(cmd)

    def request_serial(self, cmd):
        require(self.binding is not None and cmd["runId"] == self.binding["runId"], "NATIVE_UNCONFIRMED", 502)
        allowed = self.policy["operations"].get(cmd["operationId"])
        require(allowed is not None and cmd["inputs"].keys() <= set(allowed) and self.healthy(), "NATIVE_OPERATION_DENIED", 502)
        fields = dict(cmd["inputs"], runId=cmd["runId"], pattern=self.policy["pattern"], operationId=cmd["operationId"])
        body = urlencode(fields).encode("ascii")
        require(len(body) <= 16384, "NATIVE_INPUT_LIMIT", 400)
        status, value = self.call("POST", "/request", body)
        require(isinstance(value, dict) and value.get("runId") == cmd["runId"] and
                value.get("sourceHash") == self.policy["sourceSha256"] and value.get("pattern") == self.policy["pattern"]
                and value.get("status") == status and isinstance(value.get("events"), list)
                and 1 <= len(value["events"]) <= 4, "NATIVE_UNCONFIRMED", 502)
        request_id = identity(value.get("requestId"))
        allowed_types = {
            "cache": {"CACHE_MISS", "CACHE_HIT", "CACHE_EXPIRED", "CACHE_FALLBACK", "CACHE_RESET", "STORE_READ"},
            "security": {"AUTH_ALLOWED", "AUTH_DENIED", "AUTH_REVOKED", "AUTH_RESET"},
            "payment": {"PAYMENT_APPLIED", "PAYMENT_DUPLICATE", "PAYMENT_SIGNATURE_DENIED", "PAYMENT_CONFLICT",
                        "PAYMENT_PENDING", "PAYMENT_RESET", "REFUND_PENDING", "REFUND_APPLIED"},
        }[self.policy["pattern"]]
        sequence = self.sequence
        for event in value["events"]:
            require(isinstance(event, dict) and event.keys() == {"runId", "sourceHash", "requestId", "sequence",
                    "type", "edge", "recordedAt", "summary"} and event["runId"] == cmd["runId"]
                    and event["sourceHash"] == self.policy["sourceSha256"] and event["requestId"] == request_id
                    and type(event["sequence"]) is int and sequence < event["sequence"] <= 1000000
                    and isinstance(event["type"], str) and event["type"] in allowed_types
                    and text(event["summary"], 240), "NATIVE_UNCONFIRMED", 502)
            kind = event["type"]
            edges = ("service-store",) if kind == "STORE_READ" else ("service-redis",) if kind.startswith("CACHE_") else \
                ("policy-store",) if kind in ("AUTH_RESET", "AUTH_REVOKED") else ("service-policy", "policy-store") if kind.startswith("AUTH_") else \
                ("service-emulator",) if kind in ("PAYMENT_PENDING", "PAYMENT_SIGNATURE_DENIED") else ("emulator-ledger",)
            require(event["edge"] in edges, "NATIVE_UNCONFIRMED", 502)
            instant(event["recordedAt"])
            sequence = event["sequence"]
        self.sequence = sequence
        return {"output": json.dumps(value, ensure_ascii=False, separators=(",", ":")),
                "summary": f"Native {self.policy['language']} {self.policy['pattern']} completed (HTTP {status})."}

    def close(self):
        self.stopped.set()
        if self.blackhole:
            self.blackhole.close()
        for process in self.processes:
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
            process.wait(timeout=1)
