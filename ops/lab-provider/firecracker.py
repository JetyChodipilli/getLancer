"""Concrete Linux/KVM boundary. Production never imports the test hypervisor."""
import math
import hashlib
import os
from pathlib import Path
import re
import shutil
import socket
import stat
import subprocess
import time

from inventory import asset, trusted_path
from protocol import decode, encode, identity, instant, require, run_binding, text


class Firecracker:
    def __init__(self, config, prefix="labvm"):
        require(os.geteuid() == 0, "ROOT_RUNNER_REQUIRED", 503)
        require(prefix in ("labvm", "labbuild"), "INVALID_PREFIX", 503)
        self.prefix, self.config = prefix, config
        require(type(config["uidBase"]) is int and 62000 <= config["uidBase"] <= 63000, "INVALID_UID_POOL", 503)
        self.base = trusted_path(config["jailBase"], directory=True)
        self.firecracker = asset(config["firecracker"], config["firecrackerSha256"])
        self.jailer = asset(config["jailer"], config["jailerSha256"])
        require(Path(self.firecracker).name == "firecracker", "INVALID_FIRECRACKER_PATH", 503)
        require(os.access(self.firecracker, os.X_OK) and os.access(self.jailer, os.X_OK), "EXECUTABLE_REQUIRED", 503)
        for binary in ("/usr/bin/systemctl", "/usr/bin/systemd-run"):
            trusted_path(binary)
        require(Path("/run/systemd/system").is_dir() and Path("/sys/fs/cgroup/cgroup.controllers").is_file(),
                "SYSTEMD_CGROUP_V2_REQUIRED", 503)
        info = os.stat("/dev/kvm")
        require(stat.S_ISCHR(info.st_mode) and os.major(info.st_rdev) == 10 and os.minor(info.st_rdev) == 232,
                "KVM_REQUIRED", 503)
        os.close(os.open("/dev/kvm", os.O_RDWR | os.O_CLOEXEC))
        tun = os.stat("/dev/net/tun")
        require(stat.S_ISCHR(tun.st_mode) and os.major(tun.st_rdev) == 10 and os.minor(tun.st_rdev) == 200,
                "JAILER_TUN_DEVICE_REQUIRED", 503)

    def unit(self, run_id):
        return f"{self.prefix}-{identity(run_id)}.service"

    def root(self, run_id):
        return self.base / "firecracker" / identity(run_id) / "root"

    @staticmethod
    def call(argv, timeout=3, check=True):
        completed = subprocess.run(argv, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                                   timeout=timeout, env={"PATH": "/usr/sbin:/usr/bin:/sbin:/bin", "LANG": "C"},
                                   close_fds=True, cwd="/")
        require(not check or completed.returncode == 0, "SUPERVISOR_UNCONFIRMED", 503)
        require(len(completed.stdout) <= 65536, "SUPERVISOR_UNCONFIRMED", 503)
        return completed.stdout.decode("utf-8", errors="strict")

    def state(self, run_id):
        output = self.call(["/usr/bin/systemctl", "show", self.unit(run_id),
                            "--property=LoadState,ActiveState,ControlGroup,RuntimeMaxUSec,KillMode,ExitType,PrivateNetwork,MemoryMax,TasksMax,CPUQuotaPerSecUSec"], check=False)
        return dict(line.split("=", 1) for line in output.splitlines() if "=" in line)

    def isolated(self, cmd):
        state = self.state(cmd["runId"])
        # The jailer uses this exact existing systemd cgroup, never a sibling outside its lifetime control.
        expected = "/system.slice/" + self.unit(cmd["runId"])
        require(state.get("ActiveState") == "active" and state.get("ControlGroup") == expected and
                state.get("KillMode") == "control-group" and state.get("ExitType") == "cgroup" and
                state.get("PrivateNetwork") == "yes" and state.get("RuntimeMaxUSec") not in (None, "infinity", "0"),
                "ISOLATION_UNCONFIRMED", 502)
        group = Path("/sys/fs/cgroup") / expected.lstrip("/")
        require(group.is_dir() and "populated 1" in (group / "cgroup.events").read_text(), "ISOLATION_UNCONFIRMED", 502)
        require(int((group / "memory.max").read_text()) <= (cmd["memoryMiB"] + 128) * 1048576
                and int((group / "pids.max").read_text()) <= 64, "ISOLATION_UNCONFIRMED", 502)
        cpu = (group / "cpu.max").read_text().split()
        require(len(cpu) == 2 and cpu[0] != "max" and int(cpu[0]) <= int(cpu[1]), "ISOLATION_UNCONFIRMED", 502)
        self.verify_process(cmd["runId"], expected)
        return True

    def verify_process(self, run_id, expected, proc_base=Path("/proc")):
        # cgroup.procs is not recursive: the jailer may place the VMM in a descendant.
        # The kernel, rather than a guest response, establishes the actual process identity.
        pid_path = self.root(run_id) / "firecracker.pid"
        with os.fdopen(os.open(pid_path, os.O_RDONLY | os.O_NOFOLLOW), "rb") as pid_file:
            raw_pid = pid_file.read(16)
        require(re.fullmatch(rb"[1-9][0-9]{0,9}\n?", raw_pid), "ISOLATION_UNCONFIRMED", 502)
        proc = proc_base / str(int(raw_pid))
        status = dict(line.split(":", 1) for line in (proc / "status").read_text().splitlines() if ":" in line)
        process_uids = [int(value) for value in status.get("Uid", "").split()]
        process_gids = [int(value) for value in status.get("Gid", "").split()]
        require(len(process_uids) == 4 and len(set(process_uids)) == 1 and
                self.config["uidBase"] <= process_uids[0] < self.config["uidBase"] + 10 and
                process_gids == process_uids and status.get("Seccomp", "").strip() == "2",
                "ISOLATION_UNCONFIRMED", 502)
        membership = (proc / "cgroup").read_text().splitlines()
        require(any(line == "0::" + expected or line.startswith("0::" + expected + "/") for line in membership),
                "ISOLATION_UNCONFIRMED", 502)
        require(os.readlink(proc / "root") == str(self.root(run_id)) and
                os.readlink(proc / "ns/pid") != os.readlink("/proc/self/ns/pid") and
                os.readlink(proc / "ns/net") != os.readlink("/proc/self/ns/net"), "ISOLATION_UNCONFIRMED", 502)
        with (proc / "exe").open("rb") as executable:
            require(hashlib.file_digest(executable, "sha256").hexdigest() == self.config["firecrackerSha256"],
                    "ISOLATION_UNCONFIRMED", 502)

    def start(self, entry, cmd, slot, extra_drives=()):
        ttl = math.floor(instant(cmd["expiresAt"]) - time.time())
        require(1 <= ttl <= 300 and type(slot) is int and 0 <= slot < 10, "INVALID_HARD_LEASE", 400)
        root = self.root(cmd["runId"])
        require(not root.parent.exists(), "JAIL_ALREADY_EXISTS", 503)
        root.mkdir(parents=True, mode=0o755)
        uid = self.config["uidBase"] + slot
        # Hard links only reference reviewed immutable regular files on the same dedicated asset filesystem.
        for name, key, sha in (("kernel", "kernel", "kernelSha256"), ("rootfs", "rootfs", "rootfsSha256")):
            os.link(asset(entry[key], entry[sha]), root / name, follow_symlinks=False)
            # Verify the actual linked inode as well as the inventory pathname.
            asset(root / name, entry[sha])
        (root / "run").mkdir(mode=0o700)
        os.chown(root / "run", uid, uid)
        drives = [{"drive_id": "rootfs", "path_on_host": "/rootfs", "is_root_device": True, "is_read_only": True}]
        # The jailer writes its child PID after applying RLIMIT_FSIZE.
        max_write = 1048576
        for name, source, writable in extra_drives:
            require(name in ("source", "output") and type(writable) is bool, "INVALID_BUILD_DRIVE", 503)
            source = trusted_path(source)
            with source.open("rb") as src, os.fdopen(os.open(root / name,
                    os.O_CREAT | os.O_EXCL | os.O_WRONLY | os.O_NOFOLLOW, 0o600), "wb") as dst:
                shutil.copyfileobj(src, dst, 1048576)
                if name == "source":
                    dst.write(b"\0" * ((-dst.tell()) % 512))
                dst.flush()
                os.fsync(dst.fileno())
            os.chmod(root / name, 0o600 if writable else 0o400)
            os.chown(root / name, uid, uid)
            if writable:
                max_write = max(max_write, (root / name).stat().st_size)
            drives.append({"drive_id": name, "path_on_host": "/" + name, "is_root_device": False, "is_read_only": not writable})
        boot = {"boot-source": {"kernel_image_path": "/kernel",
                 "boot_args": "ro root=/dev/vda reboot=k panic=1 pci=off nomodules 8250.nr_uarts=0 quiet loglevel=1"},
                "drives": drives, "machine-config": {"vcpu_count": 1, "mem_size_mib": cmd["memoryMiB"], "smt": False},
                "vsock": {"guest_cid": 3, "uds_path": "/run/vsock"}, "network-interfaces": []}
        with os.fdopen(os.open(root / "config.json",
                os.O_CREAT | os.O_EXCL | os.O_WRONLY | os.O_NOFOLLOW, 0o600), "wb") as stream:
            stream.write(encode(boot))
        os.chmod(root / "config.json", 0o400)
        os.chown(root / "config.json", uid, uid)
        unit = self.unit(cmd["runId"])
        # Recompute after staging. Reserve one second each for activation and forced stop.
        ttl = math.floor(instant(cmd["expiresAt"]) - time.time()) - 2
        require(1 <= ttl <= 298, "INSUFFICIENT_HARD_LEASE", 410)
        argv = ["/usr/bin/systemd-run", "--quiet", "--unit=" + unit, "--service-type=exec",
                "--property=ExitType=cgroup", "--property=KillMode=control-group", "--property=RuntimeMaxSec=" + str(ttl),
                "--property=JobTimeoutSec=1", "--property=TimeoutStartSec=1", "--property=TimeoutStopSec=1",
                "--property=SendSIGKILL=yes", "--property=Restart=no",
                "--property=PrivateNetwork=yes", "--property=CPUQuota=100%", "--property=TasksMax=64",
                "--property=MemoryMax=" + str((cmd["memoryMiB"] + 128) * 1048576),
                "--property=MemorySwapMax=0", "--property=LimitCORE=0", "--property=LimitNOFILE=256",
                "--property=StandardOutput=null", "--property=StandardError=null", "--property=UMask=0077",
                # The jailer creates both nodes even when the VM has no network device.
                "--property=DevicePolicy=closed", "--property=DeviceAllow=/dev/kvm rwm",
                "--property=DeviceAllow=/dev/net/tun m",
                "--property=DeviceAllow=/dev/null rw", "--property=DeviceAllow=/dev/zero rw",
                "--property=DeviceAllow=/dev/urandom r", "--", self.jailer,
                "--id", cmd["runId"], "--exec-file", self.firecracker, "--uid", str(uid), "--gid", str(uid),
                "--cgroup-version", "2", "--parent-cgroup", "system.slice/" + unit,
                "--chroot-base-dir", str(self.base), "--new-pid-ns", "--resource-limit", "no-file=256",
                "--resource-limit", "fsize=" + str(max_write), "--", "--no-api", "--config-file", "/config.json"]
        self.call(argv, timeout=4)
        deadline = min(time.monotonic() + 4, time.monotonic() + ttl)
        while time.monotonic() < deadline:
            try:
                self.isolated(cmd)
                reply = self.exchange(cmd, "START")
                return reply["state"] == "READY"
            except (OSError, ValueError):
                pass
            except Exception:
                # Boot readiness may lag the supervisor; retry only START's idempotent guest handshake.
                pass
            time.sleep(0.05)
        return False

    def exchange(self, cmd, action):
        require(instant(cmd["expiresAt"]) > time.time(), "LEASE_EXPIRED", 410)
        payload = dict(cmd, action=action)
        allowance = 240 if self.prefix == "labbuild" and action == "REQUEST" else 2
        deadline = time.monotonic() + min(allowance, instant(cmd["expiresAt"]) - time.time())
        with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as connection:
            connection.settimeout(max(0.001, deadline - time.monotonic()))
            connection.connect(str(self.root(cmd["runId"]) / "run/vsock"))
            connection.sendall(b"CONNECT 7000\n")
            banner = bytearray()
            while not banner.endswith(b"\n") and len(banner) < 64:
                require(time.monotonic() < deadline, "GUEST_TIMEOUT", 502)
                connection.settimeout(max(0.001, deadline - time.monotonic()))
                part = connection.recv(1)
                require(part, "GUEST_UNCONFIRMED", 502)
                banner.extend(part)
            require(re.fullmatch(rb"OK [0-9]{1,10}\n", banner), "GUEST_UNCONFIRMED", 502)
            connection.sendall(encode(payload) + b"\n")
            raw = bytearray()
            while not raw.endswith(b"\n") and len(raw) <= 16384:
                connection.settimeout(max(0.001, deadline - time.monotonic()))
                require(time.monotonic() < deadline, "GUEST_TIMEOUT", 502)
                part = connection.recv(min(4096, 16385 - len(raw)))
                require(part, "GUEST_UNCONFIRMED", 502)
                raw.extend(part)
            reply = decode(bytes(raw), 16384)
        require(isinstance(reply, dict) and reply.keys() == {"binding", "commandId", "state", "output", "summary"}
                and reply["binding"] == run_binding(cmd) and reply["commandId"] == cmd["commandId"]
                and reply["state"] in ("READY", "RESULT") and text(reply["output"], 4096) and text(reply["summary"], 500),
                "GUEST_UNCONFIRMED", 502)
        return reply

    def healthy(self, cmd):
        try:
            return self.isolated(cmd) and self.exchange(cmd, "HEALTH")["state"] == "READY"
        except Exception:
            return False

    def request(self, cmd):
        self.isolated(cmd)
        reply = self.exchange(cmd, "REQUEST")
        require(reply["state"] == "RESULT", "GUEST_UNCONFIRMED", 502)
        return reply["output"], reply["summary"]

    def discover(self):
        output = self.call(["/usr/bin/systemctl", "list-units", "--all", "--plain", "--no-legend", self.prefix + "-*.service"])
        found = set()
        for line in output.splitlines():
            match = re.search(r"\b" + self.prefix + r"-([a-f0-9-]{36})\.service\b", line)
            if match:
                found.add(identity(match[1]))
        jail_parent = self.base / "firecracker"
        if jail_parent.exists():
            for path in jail_parent.iterdir():
                # Dedicated jailBase per runtime/build service; malformed entries block recovery.
                found.add(identity(path.name))
        return found

    def stop(self, run_id):
        state = self.state(run_id)
        if state.get("LoadState") != "not-found":
            self.call(["/usr/bin/systemctl", "stop", self.unit(run_id)], timeout=3, check=False)
            state = self.state(run_id)
            if state.get("ActiveState") not in ("inactive", "failed"):
                self.call(["/usr/bin/systemctl", "kill", "--signal=KILL", "--kill-whom=all", self.unit(run_id)], check=False)
        state = self.state(run_id)
        require(state.get("LoadState") == "not-found" or state.get("ActiveState") in ("inactive", "failed"),
                "CLEANUP_UNCONFIRMED", 503)
        group = Path("/sys/fs/cgroup/system.slice") / self.unit(run_id)
        if group.exists():
            require("populated 0" in (group / "cgroup.events").read_text(), "CLEANUP_UNCONFIRMED", 503)
        jail = self.root(run_id).parent
        if jail.exists():
            require(shutil.rmtree.avoids_symlink_attacks and not jail.is_symlink(), "UNSAFE_CLEANUP_PATH", 503)
            shutil.rmtree(jail)
        self.call(["/usr/bin/systemctl", "reset-failed", self.unit(run_id)], check=False)
        return not jail.exists()
