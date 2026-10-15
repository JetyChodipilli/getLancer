# C# / .NET local scenario fixtures

Original MIT-licensed ASP.NET Core fixtures for .NET **8 SDK**. The project uses
only the SDK's installed `Microsoft.AspNetCore.App` framework and the BCL; it has
no NuGet package dependencies. `NuGet.Config` clears remote package sources and
the project disables restore auditing, so an installed .NET 8 SDK builds offline.
Cache requires real Redis **7.2.16**, not a compatible substitute. Payment is a
synthetic emulator and moves no money; authorization uses disposable identities.

From the repository or extracted source package root:

```sh
LAB_RUN_ID=84884848-4848-4484-8484-848484848484 \
LAB_PORT=8100 LAB_REDIS_PORT=6379 \
LAB_REDIS_FAILURE_PORT=6380 LAB_REDIS_TIMEOUT_PORT=6381 \
DOTNET_CLI_TELEMETRY_OPTOUT=1 DOTNET_SKIP_FIRST_TIME_EXPERIENCE=1 \
dotnet run --project labs/dotnet/LabServer.csproj --no-launch-profile
```

Use a new lowercase UUID and independent HTTP port for every process. Builds and
restore outputs are scoped to `labs/dotnet/artifacts/<LAB_RUN_ID>/`, so concurrent
disposable processes do not share intermediate files. Without a run ID, offline
build commands use `artifacts/standalone/`. Each process exits after 600 seconds;
operator setting `LAB_LIFETIME_SECONDS` can reduce that lifetime to 1–600 seconds.
Stop it earlier with Ctrl-C. From `labs/dotnet`, the equivalent command is
`dotnet run --project LabServer.csproj --no-launch-profile`.

Start Redis in another terminal after verifying its exact version:

```sh
redis-server --version
redis-server --bind 127.0.0.1 --port 6379 --save '' --appendonly no \
  --protected-mode yes --maxmemory 16mb --maxclients 32
```

The failure port must have no listener. The timeout port is a local TCP listener
that accepts a connection without responding. The shared verifier creates those
fixtures, starts two native processes and checks the complete matrix:

```sh
REDIS_SERVER=/absolute/path/to/redis-7.2.16/redis-server \
node scripts/verify-lab-scenarios.mjs --language dotnet
```

On restricted Unix containers, named mutexes require a writable user-owned runtime directory. The verifiers set `XDG_RUNTIME_DIR` to their private temporary workspace, including for compilation. For manual SDK use, create a private directory with `mktemp -d`, export it as `XDG_RUNTIME_DIR` and remove it after the process exits. This supports the patched runtime without enabling unsafe global mutex scope. See [Microsoft's compatibility notice](https://github.com/dotnet/docs/issues/56396).

Set `LAB_DOTNET` to an absolute .NET 8 executable if it is not on PATH. Run the
extra native resource/concurrency regressions with
`node labs/dotnet/verify.mjs`. They use .NET and actual HTTP/TCP; they require no
Redis because the shared matrix separately verifies cache behavior. The verifier
builds once and directly supervises each assembly, preventing an SDK CLI parent
from leaving an unsupervised child. It reports observed native readiness time and
RSS when the host permits process inspection; these are local measurements.
With a working .NET 8 SDK, run `node labs/dotnet/verify.mjs --sdk` to additionally
check the exact documented `dotnet run` command, UUID-scoped restore/build output,
the Release publish profile, published assembly startup and finite lifetimes.
Every observed event is checked for canonical fractional UTC `Z` timestamps.

`GET http://127.0.0.1:8100/health` returns the language, run UUID and SHA-256 of
actual `Program.cs` bytes read at startup. From another working directory, set
operator-only `LAB_SOURCE_PATH` to the absolute primary `Program.cs`. Do not edit
source during a run. Example request:

```sh
curl http://127.0.0.1:8100/request \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data 'runId=84884848-4848-4484-8484-848484848484&pattern=cache&operationId=read'
```

The exact protocol is `docs/v48/CONTRACT.md` and fixtures are `labs/fixtures.json`.

- Cache performs RESP2 `GET`, `SET PX`, `PTTL`, `PEXPIRE 0` and run-scoped `DEL`
  against loopback Redis. One real 250 ms connect/write/read budget spans each
  operation; unavailable and blackhole ports fall back to the synthetic store.
- Security signs and verifies internal HMAC claims in constant time, checks
  expiry/tenant/role and reads current policy on every request. Revocation takes
  effect immediately; real account tokens are never accepted.
- Payment verifies the exact decoded UTF-8 event body before interpreting its
  grammar. Sign with the public `getlancer-payment-fixture-only` demo key, then
  form-encode. Retries are idempotent, changed event-ID bodies conflict, a maximum
  of 100 events is retained, and refund ordering never resurrects entitlement.
- Typed records and event enums produce bounded projections without signatures,
  raw event bodies, identity keys, endpoints or internal errors. A semaphore
  serializes effects and event sequence; resets do not rewind that sequence.
- HTTP binds only `127.0.0.1` and supports HTTP/1. Bodies are limited to 16 KiB,
  JSON to 4096 bytes, connections/backlog to 16, headers to 32/8 KiB and request
  lines to 2 KiB. Header/body/write timeouts are 2 seconds; effect queue waits are
  limited to 1 second. Malformed UTF-8/escaping, duplicate/unknown fields, wrong
  paths/methods/content types, query strings and foreign runs fail before effects.
  No CORS headers or application credentials are exposed.

An offline Release build is `dotnet build labs/dotnet/LabServer.csproj -c Release`.
The framework-dependent native publish profile is:

```sh
dotnet publish labs/dotnet/LabServer.csproj -c Release -p:PublishProfile=Local
```

Local execution and checked source evidence do not certify hosted isolation,
provider startup, cleanup, production readiness or merchant payment integration.
