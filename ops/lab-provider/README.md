# Separate hosted lab provider

This is executable provider/build service source, not a provider admission or a local Docker alias. Deploy it only on a dedicated Linux KVM execution host/account, separately from the Java application and application database. No cloud resources, credentials, images or admission signatures are supplied. The example inventory and build job list are empty; example binary hashes fail closed. `APP_LABS_ENABLED=false` stays unchanged.

Python 3.11+ is sufficient for the host and guest helpers; no pip/npm dependencies are installed. A qualified host must supply cgroup v2, systemd 250+ (`ExitType=cgroup`), `/dev/kvm`, `/dev/net/tun`, a reviewed guest kernel with virtio block/vsock, and a matching pinned **musl** Firecracker/jailer release. The jailer always creates KVM/TUN nodes inside its jail, including when the VM has no NIC. The closed device policy permits KVM read/write/node creation and TUN node creation only; it grants no TUN read/write access. The selected versions, hardware, patches and systemd/jailer interaction require actual host acceptance. This checkout has not booted KVM.

The unprivileged HTTPS/loopback gateway authenticates the app bearer token and sends bounded frames to a root runner over a private Unix socket. Only the configured gateway UID owns that socket, mode `0600`; both ends check `SO_PEERCRED`. The privileged runner independently validates commands against a root-owned inventory, owns a private SQLite ledger and launches only the fixed Firecracker/jailer recipe. The runner has no HTTP listener and no application database, session, signing or payment credentials. Its app-facing interface has no upload, URL, environment, filesystem or executable parameter.

The guest has a read-only root drive, one vCPU, its admitted 64–512 MiB RAM, no NIC/MMDS/API server, and a bounded host-initiated vsock port. Host guest-initiated vsock listeners are never created. systemd isolates the VMM network namespace, limits CPU/memory/tasks and bounds runtime independently of the gateway, Java worker and runner process. The jailer switches to a reserved run-specific UID/GID and uses its default seccomp behavior. Health requires kernel `/proc` evidence of the jailed child executable hash, UID/GID, seccomp, PID/network namespaces and membership in the exact supervised cgroup, plus bounded guest readiness. These checks enforce source configuration; they cannot certify the host against hypervisor/kernel vulnerabilities.

Run the local protocol/negative checks from repository root:

```sh
python3 -m unittest discover -s ops/lab-provider -p 'test_*.py'
node --test tests/lab-provider.test.mjs
```

The tests use a disposable fake hypervisor injected through Python constructors; production command lines expose no fake backend. Real HTTP authentication/framing is exercised on loopback. Unix credential/framing handlers use actual kernel `SO_PEERCRED` over connected `socketpair` endpoints because this sandbox prohibits new Unix listeners. Supervisor tests inspect the actual fixed launch recipe and simulate kernel evidence; they do not start systemd or KVM.

## Operator installation

Install the reviewed Python files at `/opt/getlancer-provider`, owned by root and unwritable by group/others. Use a dedicated unprivileged `labgateway` user (the example UID is 61999) and reserve runtime UIDs/GIDs 62000–62009 exclusively. Create `/var/lib/getlancer-provider/state` root mode `0700`, `/srv/getlancer-provider/runtime-jails` root mode `0755`, and immutable assets under `/srv/getlancer-provider/assets`. Assets and jails must share the same filesystem/mount for hard links; the runner unit permits the common provider mount. Asset files must be root owned with **no write bits**, directories root owned and unwritable by group/others. Binaries must be executable (typically `0555`), images/kernel/archive `0444`. Symlinks and writable ancestors are refused. Staged hard links are hashed again before launch.

Install the example configuration files at `/etc/getlancer-provider` with actual operator-approved values. The runner configuration and inventory can be root mode `0600`; gateway configuration, token and optional TLS key must be root owned and readable only by `labgateway` (for example root:`labgateway` mode `0640`). Generate a high entropy bearer secret outside Git, between 32 and 256 printable ASCII characters. No example secret is usable. Keep the actual source hashes, independent restore epoch and source/image approval dates in the reviewed inventory. Every configuration/asset ancestor must satisfy the filesystem checks.

The supplied systemd units are templates for this specific dedicated host layout. Install/review them explicitly; no installer is run here. The default gateway binds loopback, with a local TLS reverse proxy providing the app's fixed HTTPS origin. The proxy must limit connections, header/body sizes, total request time, ingress to the application and TLS; it must redact Authorization and request bodies. The service unit permits only localhost IP traffic. Direct HTTPS is supported by supplying certificate/key and `listen=0.0.0.0`; that deliberate operator change also requires a reviewed systemd IP allowlist and host firewall. Never expose cleartext bearer traffic beyond loopback.

The runner's `/run/getlancer-runner` parent is root:`labgateway` `0750`; the root service creates the gateway-owned socket inside it. The socket owner can connect but cannot replace the parent entry. A writer `flock` excludes another runner; only after acquiring it may restart remove a stale root/gateway socket. The runner reconfirms cleanup of every discovered VM/jail before admitting new work, including identities a restored DB calls cleaned. An unconfirmed orphan blocks all admission. Stopping only the gateway does not stop leases. Stopping/crashing the runner leaves independent supervisor deadlines intact; the next runner reconciles every physical unit and closes all previous routes.

All host subprocess argv arrays, executable paths, unit prefixes, devices and boot arguments are fixed by source/root configuration. The only dynamic hypervisor identity is a parsed canonical UUID. The jailer uses `--cgroup-version 2 --parent-cgroup system.slice/labvm-<uuid>.service` **without** `--cgroup`; the documented behavior moves into that pre-existing systemd cgroup. `ExitType=cgroup` tracks the jailer's new-PID-namespace child after its parent exits. Hierarchical `cgroup.events` plus actual child membership are checked; an empty parent `cgroup.procs` is allowed. No delegate to the application or VMM is needed. If the unit cgroup does not exist, permits the wrong process, has enabled subtree controllers that prevent placement, or fails the evidence checks, startup fails and capacity stays quarantined until cleanup. Verify this on the selected distribution; do not weaken it to make a host pass.

## Inventory and guest contract

An inventory entry has exactly these fields (replace every hash/path/value with reviewed real data):

```json
{
  "manifestSha256": "<64 lowercase hex: signed app manifest bytes>",
  "imageDigest": "sha256:<rootfsSha256>",
  "sourceSha256": "<64 lowercase hex: reviewed primary source>",
  "archiveSha256": "<64 lowercase hex: reviewed build source archive>",
  "archive": "/srv/getlancer-provider/assets/source.zip",
  "kernel": "/srv/getlancer-provider/assets/kernel",
  "kernelSha256": "<64 lowercase hex>",
  "rootfs": "/srv/getlancer-provider/assets/runtime.ext4",
  "rootfsSha256": "<64 lowercase hex>",
  "memoryMiB": 128,
  "approvedUntil": "2026-10-22T12:00:00Z",
  "scenarios": {
    "reviewed-scenario": {
      "operations": ["evaluate", "reset"],
      "inputs": {
        "message": {"type":"TEXT","required":false,"maxLength":80,"min":0,"max":100,"choices":[]}
      }
    }
  }
}
```

The inventory envelope is `{"version":1,"entries":[...]}`. The rootfs digest is the provider image digest. The archive hash is the manifest's `buildSourceSha256`/`archiveSha256`; its reviewed primary source has the separate `sourceSha256`. The provider pins this relationship through the root-reviewed entry and build receipt; the application still verifies its own signed manifest/current source/release eligibility. An inventory file is not a signature or a source license approval. Reload requires stopping/restarting the runner, which closes/reconciles old routes; do not mutate active image inodes or replace files under a live process.

The read-only runtime rootfs must include Python, `protocol.py`, `guest_agent.py`, the guest-only runtime unit, and reviewed operation executables. Mount `/tmp` and required runtime directories as bounded tmpfs. Disable unnecessary guest services, package managers, SSH and network configuration. A pinned `/etc/getlancer/runtime-policy.json` maps each scenario to a fixed `start` argv and each operation to a fixed argv:

```json
{"scenarios":{"reviewed-scenario":{"start":["/usr/bin/python3","/opt/lab/check_ready.py"],"operations":{"evaluate":["/usr/bin/python3","/opt/lab/evaluate.py"],"reset":["/usr/bin/python3","/opt/lab/reset.py"]}}}}
```

Handlers receive the complete validated command as JSON on stdin and return exactly `{"output":"...","summary":"..."}`. Each handler joins a fresh guest cgroup before execution, drops supplementary groups and switches to UID/GID 1000 with no-new-privileges. Recursive `cgroup.kill` and hierarchical `populated 0` confirmation run after success and failure, including detached descendants. The guest image must provide writable cgroup v2 to the root agent, reserve UID 1000 for these handlers and supply bounded tmpfs for state. Missing controls fail closed. A readiness script must actually check its certified runtime.

For the six existing native HTTP adapters, include `guest_native.py` and use a scenario with exactly one `nativeHttp` policy instead of `start`/`operations`. That policy fixes `argv`, `cwd`, `source`, `sourceSha256`, `language` (`python`, `java`, `typescript`, `go`, `dotnet`, `php`), `pattern` (`cache`, `security`, `payment`), `operations` (operation IDs mapped to allowed input names) and `redisSha256` (required for cache; null otherwise). The bridge launches the reviewed native executable, pins source and run identities in real health/request responses, and routes only fixed loopback HTTP. Cache scenarios launch pinned Redis 7.2.16 without persistence and a bounded TCP timeout fixture. Native resident processes live for the VM lease; they are separate from short-lived argv handlers. Image policies, offline toolchains and each native scenario matrix still need actual image acceptance. The provider never interprets handler output as a host command or runs uploaded handlers on the host.

## Offline build broker

`trusted_build.py --config /etc/getlancer-builder/build.json --job <reviewed-id>` is a separate root-only, offline build service/CLI on a dedicated builder host/account. It has no HTTP upload/exec endpoint. Its reviewed job inventory chooses every source/image/template path and recipe; a caller supplies only a bounded slug. Use a distinct jail base, UID pool (example 62020–62029), configuration and output directory, never application credentials. Reserve at most one build under the output-directory writer lock.

A job contains exactly `source`, `archiveSha256`, `sourceSha256`, `builderKernel`, `builderKernelSha256`, `builderRootfs`, `builderRootfsSha256`, `outputTemplate`, `outputTemplateSha256`, `recipeId`, `approvedUntil`. Every path refers to a pre-staged immutable regular file with a reviewed hash. The builder boots the pinned read-only builder image, presents source as a read-only block drive and a copied preallocated ext4 template as the only writable drive. The host never mounts or unpacks contributor source/output. The builder image includes `builder_agent.py`, `protocol.py`, the guest builder unit, offline toolchains and `/etc/getlancer/build-recipes.json`.

A guest recipe has exactly `language` (`python`, `java`, `javascript`, `typescript`, `go`, `dotnet`, `php`), `sourceEntry` (primary file), `installPrefix` (a new directory under `opt`), and `runtimePolicy` (the fixed runtime handler policy). The agent bounds ZIP entry count, compressed ratio, per-file/total bytes, duplicate names, encryption, links and traversal; hashes the primary source; then runs a fixed offline compiler/checker recipe. Python uses checked-hash bytecode, Java 17 disables annotation processing, JavaScript uses Node syntax checks, TypeScript uses a pinned local compiler and Node types, Go disables toolchain/module fetching and CGO, .NET restores only from the pinned offline package directory before no-restore publish, and PHP uses syntax checks. The builder then copies into the output template and installs the fixed runtime policy. Toolchain/library/runtime template licenses and versions must already be reviewed. No arbitrary command field or online dependency fetch is accepted. A TypeScript runtime may run the reviewed original source under the pinned Node type-stripper so the native health source hash stays aligned; the image matrix must verify this exact mapping.

The host allows up to 240 seconds for the fixed build operation under the independent five-minute supervisor bound. It freezes the cgroup, confirms `frozen 1`, independently hashes/copies the output without mounting it, stops and confirms cleanup, and only then writes an `UNSIGNED_BUILD_CANDIDATE` receipt. The candidate image/hash is not automatically appended to runtime inventory or signed. A crash leaves no qualifying receipt; reconcile orphans and review/remove incomplete candidates before another job. Images are published with exclusive creation and no symlink following. Builder images/templates and source-policy mappings are bootstrap/operator inputs, not fabricated by tests.

## State and failure behavior

`POST /v1/lab-commands` matches the twelve Java `Command` fields exactly. JSON duplicate/unknown keys, paths/URLs/commands, chunked/ambiguous framing, invalid UUID/hash/instant/types, unsupported actions/operations/inputs, excessive bodies and unauthenticated traffic are denied. Responses echo all seven Java identities and remain below 16 KiB; output/summary have the Java UTF-16 length bounds (4096/500). A whole HTTP connection has a ten-second deadline, runner RPC eight seconds and runtime vsock two seconds; no guest stream can extend a deadline with partial output. HTTP handling is serial with a bounded accept queue. Production reverse-proxy ingress limits remain required.

SQLite uses WAL, `synchronous=FULL`, a private writer lock and committed run/command intents. A command UUID is immutable across runs and bodies. Retries replay an exact result only while its run/lease/image remains live and healthy. No uncertain operation intent is executed again. START is generation 1, cannot extend its original expiry, and cannot revive a tombstone. STOP accepts the recorded epoch's greater/current generation even after a restore epoch change, fences requests before touching the hypervisor, and may arrive before START to establish a durable tombstone. Only physically confirmed stop, empty hierarchical cgroup and removed jail yield `CLEANED`; uncertainty yields `PENDING` and retains the slot. Failed operations close the provider route and attempt cleanup; the app's reservation remains under its own reconciliation rules.

At most ten active/quarantined slots are admitted. The durable identity ledger has finite run/command bounds; reaching them closes admission. Cleanup is still attempted even when a new command cannot be journaled; the response stays unconfirmed rather than manufacturing durable success. Do not truncate tombstones: archive/replace a full ledger only after all physical work is cleaned and both independently held operator epochs are rotated. Private response bytes are erased after 24 hours once cleaned; command hashes and identity tombstones remain. The ledger, private source and credentials require encrypted protected storage/backups, retention approval and monitoring; no user payload/secret is logged by the gateway.

The absolute expiry may be at most five minutes away. The driver recalculates remaining time after file staging, reserves activation/forced-stop time, and sets monotonic `RuntimeMaxSec`, `JobTimeoutSec`, `TimeoutStartSec`, `TimeoutStopSec` and `KillMode=control-group` on a non-notify service. The supervisor therefore remains responsible if the app or provider disappears. The runner also sweeps expiry every half second. Real clock changes, queued supervisor jobs, forked children, hard deadlines under load, daemon death and host reboot/restore must be measured on the chosen host; the protocol fake cannot establish that timing.

The app's default admission, signed evidence, budgets, tenant authorization, idle timer, independent data/AI/IoT policies and seven launch gates are unchanged. See [the completion evidence and remaining gates](../../docs/phase-completion/PROVIDER.md).

## Primary implementation references

Consulted 2026-10-21; these are upstream main/current documentation, not a claim that a specific release has been accepted:

- [Firecracker jailer](https://github.com/firecracker-microvm/firecracker/blob/main/docs/jailer.md): matching static binaries, child PID file, chroot/UID, cgroup v2 placement and caller-owned cleanup.
- [Firecracker vsock](https://github.com/firecracker-microvm/firecracker/blob/main/docs/vsock.md): fixed host-initiated `CONNECT` handshake and absence of guest-initiated listeners.
- [Production host setup](https://github.com/firecracker-microvm/firecracker/blob/main/docs/prod-host-setup.md): patched KVM/kernel/microcode, default seccomp, bounded/discarded serial logs, jailer requirements.
- [systemd service](https://www.freedesktop.org/software/systemd/man/250/systemd.service.html) and [systemctl](https://www.freedesktop.org/software/systemd/man/256/systemctl.html): `ExitType=cgroup`, supervisor runtime limits and freeze/thaw behavior. The exact selected host/version still needs live qualification.
