"""Install ONLY inside a reviewed read-only runtime rootfs; never launch on the host.

The guest policy maps scenario/operation identifiers to fixed argv arrays. Input is
JSON on stdin, never argument interpolation, shell, URL or dynamic module loading.
"""
import argparse
import ctypes
import os
from pathlib import Path
import sys
import selectors
import signal
import socket
import subprocess
import time
import uuid

sys.path.insert(0, str(Path(__file__).resolve().parent))

from protocol import command, decode, digest, encode, instant, require, run_binding, text


class OperationScope:
    """Required guest cgroup; a detached session cannot escape it as operation UID 1000."""
    def __init__(self):
        require(os.geteuid() == 0, "GUEST_ROOT_SUPERVISOR_REQUIRED", 503)
        base = Path("/sys/fs/cgroup/getlancer-operations")
        base.mkdir(mode=0o700, exist_ok=True)
        info = base.lstat()
        require(not base.is_symlink() and info.st_uid == 0 and not info.st_mode & 0o022,
                "GUEST_OPERATION_CGROUP_REQUIRED", 503)
        self.path = base / str(uuid.uuid4())
        self.path.mkdir(mode=0o700)
        require((self.path / "cgroup.kill").exists(), "GUEST_OPERATION_CGROUP_REQUIRED", 503)
        self.libc = ctypes.CDLL(None, use_errno=True)

    def child_setup(self):
        descriptor = os.open(self.path / "cgroup.procs", os.O_WRONLY | os.O_NOFOLLOW)
        try:
            os.write(descriptor, b"0")
        finally:
            os.close(descriptor)
        os.setgroups([])
        os.setgid(1000)
        os.setuid(1000)
        if self.libc.prctl(38, 1, 0, 0, 0) != 0:  # PR_SET_NO_NEW_PRIVS
            raise OSError(ctypes.get_errno(), "no-new-privileges failed")

    def close(self):
        (self.path / "cgroup.kill").write_text("1")
        deadline = time.monotonic() + 0.5
        while "populated 0" not in (self.path / "cgroup.events").read_text():
            require(time.monotonic() < deadline, "GUEST_DESCENDANTS_UNCONFIRMED", 502)
            time.sleep(0.01)
        self.path.rmdir()


def invoke(argv, payload, timeout=1, scope_factory=OperationScope):
    require(isinstance(argv, list) and 1 <= len(argv) <= 20 and all(isinstance(arg, str) and len(arg) <= 256 for arg in argv)
            and argv[0].startswith("/"), "INVALID_GUEST_POLICY", 503)
    scope = scope_factory()
    try:
        process = subprocess.Popen(argv, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                                   shell=False, cwd="/tmp", env={"PATH": "/usr/bin:/bin", "LANG": "C.UTF-8"},
                                   start_new_session=True, close_fds=True, preexec_fn=scope.child_setup)
    except Exception:
        scope.close()
        raise
    deadline = time.monotonic() + timeout
    output = bytearray()
    try:
        # No pipe can deadlock: the bounded JSON is written with nonblocking I/O alongside output reads.
        pending = memoryview(encode(payload))
        os.set_blocking(process.stdin.fileno(), False)
        os.set_blocking(process.stdout.fileno(), False)
        with selectors.DefaultSelector() as selector:
            selector.register(process.stdin, selectors.EVENT_WRITE)
            selector.register(process.stdout, selectors.EVENT_READ)
            while selector.get_map():
                require(time.monotonic() < deadline, "GUEST_OPERATION_TIMEOUT", 502)
                for key, _ in selector.select(min(0.05, max(0, deadline - time.monotonic()))):
                    if key.fileobj is process.stdin:
                        count = os.write(key.fd, pending)
                        pending = pending[count:]
                        if not pending:
                            selector.unregister(process.stdin)
                            process.stdin.close()
                    else:
                        data = os.read(key.fd, 4096)
                        if data:
                            output.extend(data)
                            require(len(output) <= 12000, "GUEST_OPERATION_OUTPUT_LIMIT", 502)
                        else:
                            selector.unregister(process.stdout)
        require(process.wait(timeout=max(0.001, deadline - time.monotonic())) == 0, "GUEST_OPERATION_FAILED", 502)
        value = decode(bytes(output), 12000)
        require(isinstance(value, dict) and value.keys() == {"output", "summary"} and
                text(value["output"], 4096) and text(value["summary"], 500), "GUEST_OPERATION_FAILED", 502)
        return value
    finally:
        try:
            # Kernel cgroup.kill is recursive and includes setsid/forked descendants.
            scope.close()
        finally:
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
            process.wait(timeout=1)
            for pipe in (process.stdin, process.stdout):
                if not pipe.closed:
                    pipe.close()


class Agent:
    def __init__(self, policy):
        require(isinstance(policy, dict) and policy.keys() == {"scenarios"}, "INVALID_GUEST_POLICY", 503)
        self.policy, self.binding, self.start_hash = policy, None, None
        self.responses = {}
        self.native = None

    def execute(self, raw):
        payload = decode(raw)
        health = isinstance(payload, dict) and payload.get("action") == "HEALTH"
        cmd = command(encode(dict(payload, action="START", operationId=None)) if health else raw)
        require(instant(cmd["expiresAt"]) > time.time(), "LEASE_EXPIRED", 410)
        binding = run_binding(cmd)
        scenario = self.policy["scenarios"].get(cmd["scenarioId"])
        require(scenario is not None, "SCENARIO_NOT_ADMITTED", 403)
        if self.binding is not None:
            require(binding == self.binding and cmd["leaseGeneration"] == 1, "LEASE_CLOSED", 409)
        if health:
            require(self.binding is not None, "NOT_READY", 409)
            require(self.native is None or self.native.healthy(), "NATIVE_NOT_READY", 502)
            value = {"output": "", "summary": ""}
        elif cmd["action"] == "START":
            identity = digest(cmd["inputs"])
            require(self.start_hash is None or self.start_hash == identity, "RUN_ID_CONFLICT", 409)
            if self.binding is None:
                if scenario.keys() == {"nativeHttp"}:
                    from guest_native import NativeHTTP
                    self.native = NativeHTTP(scenario["nativeHttp"])
                    self.native.start(cmd)
                else:
                    require(scenario.keys() == {"start", "operations"}, "INVALID_GUEST_POLICY", 503)
                    invoke(scenario["start"], cmd)
                self.binding, self.start_hash = binding, identity
            value = {"output": "", "summary": ""}
        else:
            require(cmd["action"] == "REQUEST" and self.binding is not None, "NOT_READY", 409)
            argv = scenario.get("operations", {}).get(cmd["operationId"])
            require(self.native is not None or argv is not None, "OPERATION_NOT_ADMITTED", 403)
            previous = self.responses.get(cmd["commandId"])
            if previous:
                require(previous[0] == digest(cmd), "COMMAND_ID_CONFLICT", 409)
                value = previous[1]
            else:
                require(len(self.responses) < 1000, "OPERATION_LIMIT", 409)
                value = self.native.request(cmd) if self.native else invoke(argv, cmd)
                self.responses[cmd["commandId"]] = (digest(cmd), value)
        return dict(binding=binding, commandId=cmd["commandId"], state="RESULT" if cmd["action"] == "REQUEST" else "READY", **value)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--policy", default="/etc/getlancer/runtime-policy.json")
    args = parser.parse_args()
    # Images carry this policy; the host command cannot choose a handler path.
    agent = Agent(decode(Path(args.policy).read_bytes()))
    with socket.socket(socket.AF_VSOCK, socket.SOCK_STREAM) as server:
        server.bind((socket.VMADDR_CID_ANY, 7000))
        server.listen(4)
        while True:
            connection, _ = server.accept()
            with connection:
                connection.settimeout(2)
                try:
                    raw = bytearray()
                    deadline = time.monotonic() + 2
                    while not raw.endswith(b"\n") and len(raw) <= 65536:
                        require(time.monotonic() < deadline, "GUEST_TIMEOUT", 502)
                        data = connection.recv(min(4096, 65537 - len(raw)))
                        require(data, "TRUNCATED_BODY", 400)
                        raw.extend(data)
                    connection.sendall(encode(agent.execute(bytes(raw))) + b"\n")
                except Exception:
                    # A broken operation closes the VM route. Host health checks then force cleanup.
                    return


if __name__ == "__main__":
    main()
