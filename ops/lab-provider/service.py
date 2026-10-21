"""Production entry points only: gateway or KVM runner. No emulation/fallback mode."""
import argparse
import os
from pathlib import Path
import sys
import ssl
import stat
import threading

# -I excludes the script directory. This directory is part of the root-owned deployment.
sys.path.insert(0, str(Path(__file__).resolve().parent))

from firecracker import Firecracker
from inventory import Inventory, read_trusted, trusted_path
from protocol import decode, identity, require
from runner import Runner
from transport import Gateway, RunnerSocket, forward


def stale_socket(path, gateway_uid):
    path = Path(path)
    if path.exists() or path.is_symlink():
        info = path.lstat()
        require(stat.S_ISSOCK(info.st_mode) and info.st_uid in (0, gateway_uid), "UNTRUSTED_RUNNER_SOCKET", 503)
        path.unlink()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("role", choices=("gateway", "runner"))
    parser.add_argument("--config", required=True)
    args = parser.parse_args()
    config = decode(read_trusted(args.config))
    if args.role == "gateway":
        require(config.keys() == {"listen", "port", "secretFile", "runnerSocket", "tlsCertificate", "tlsKey"}, "INVALID_CONFIG", 503)
        require(os.geteuid() != 0, "UNPRIVILEGED_GATEWAY_REQUIRED", 503)
        require(config["listen"] in ("127.0.0.1", "0.0.0.0") and type(config["port"]) is int and 1024 <= config["port"] <= 65535,
                "INVALID_LISTENER", 503)
        secret = read_trusted(config["secretFile"], 256).decode("ascii").strip()
        trusted_path(Path(config["runnerSocket"]).parent, directory=True)
        server = Gateway((config["listen"], config["port"]), secret, lambda raw: forward(config["runnerSocket"], raw))
        if config["tlsCertificate"] and config["tlsKey"]:
            context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
            context.minimum_version = ssl.TLSVersion.TLSv1_2
            context.load_cert_chain(trusted_path(config["tlsCertificate"]), trusted_path(config["tlsKey"]))
            server.socket = context.wrap_socket(server.socket, server_side=True, do_handshake_on_connect=False)
        else:
            require(config["listen"] == "127.0.0.1" and config["tlsCertificate"] is None and config["tlsKey"] is None,
                    "TLS_REQUIRED", 503)
        server.serve_forever(poll_interval=0.25)
    else:
        require(config.keys() == {"epoch", "inventory", "stateDir", "socket", "gatewayUid", "driver"}, "INVALID_CONFIG", 503)
        identity(config["epoch"])
        require(os.geteuid() == 0 and type(config["gatewayUid"]) is int and config["gatewayUid"] > 0, "INVALID_RUNNER_IDENTITY", 503)
        state_dir = trusted_path(config["stateDir"], directory=True)
        require(state_dir.stat().st_mode & 0o077 == 0, "PRIVATE_STATE_REQUIRED", 503)
        trusted_path(Path(config["socket"]).parent, directory=True)
        driver = Firecracker(config["driver"])
        runner = Runner(state_dir, config["epoch"], Inventory.load(config["inventory"]), driver)
        # The exclusive writer lock has proved that no runner can still own this socket.
        stale_socket(config["socket"], config["gatewayUid"])
        server = RunnerSocket(config["socket"], runner, config["gatewayUid"])
        def sweep():
            while not stopped.wait(0.5):
                runner.sweep()
        stopped = threading.Event()
        worker = threading.Thread(target=sweep, daemon=True)
        worker.start()
        try:
            server.serve_forever(poll_interval=0.25)
        finally:
            stopped.set()
            server.server_close()
            os.unlink(config["socket"])
            runner.close()


if __name__ == "__main__":
    main()
