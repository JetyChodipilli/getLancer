# Java local scenario fixtures

Requires JDK 17 or newer and a real Redis **7.2.16** fixture listening on loopback.
The JDK source launcher compiles this single file in memory: no Maven, dependencies,
separate `javac` command, app server, account token or merchant credential is needed.
All three labs share one disposable process and one run UUID.
Choose a fresh UUID for each process; the fixed UUID below is an example.

For a manual Redis fixture, verify `redis-server --version` reports 7.2.16, then
start it in a separate terminal:

```sh
redis-server --bind 127.0.0.1 --port 6379 --save '' --appendonly no \
  --protected-mode yes --maxmemory 16mb --maxclients 32
```

From the repository or extracted source package root:

```sh
LAB_RUN_ID=84884848-4848-4484-8484-848484848484 \
LAB_PORT=8100 LAB_REDIS_PORT=6379 \
LAB_REDIS_FAILURE_PORT=6380 LAB_REDIS_TIMEOUT_PORT=6381 \
java labs/java/LabServer.java
```

The failure port must have no listener. The timeout port must be a local TCP
listener that accepts connections without answering Redis commands. The shared
verifier creates both fixtures, checks Redis's exact version, runs real HTTP
requests and captures their evidence:

```sh
node scripts/verify-lab-scenarios.mjs --language java
```

Set `REDIS_SERVER` to the absolute Redis 7.2.16 executable path when needed by the
verifier. Stop the process after a run. Its payment ledger and synthetic identity
key live only in memory; Redis keys are scoped to that run. `reset` affects only
the requested pattern and never rewinds the process event sequence.

`GET http://127.0.0.1:8100/health` exposes the run UUID and the SHA-256 of the
actual `labs/java/LabServer.java` bytes read at startup. Launch from the repository
root, the extracted package root, or the `labs/java` directory. For another working
directory, set the operator-only `LAB_SOURCE_PATH` to the absolute path of the
same `LabServer.java` being launched. Do not change source bytes during a run.

Requests use `POST /request` and UTF-8 `application/x-www-form-urlencoded` bodies.
For example, with the process above:

```sh
curl http://127.0.0.1:8100/request \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data 'runId=84884848-4848-4484-8484-848484848484&pattern=cache&operationId=read'
```

The full field and response contract is in `docs/v48/CONTRACT.md`; fixture values
are in `labs/fixtures.json`.

- Cache uses real Redis RESP `GET`, `SET PX`, `PTTL`, `PEXPIRE 0` and run-scoped
  `DEL`. A cache miss reads the fixed synthetic store once; a hit does not. Refused
  and blackhole sockets fall back within one 250 ms Redis connection/read budget.
- Authorization issues and verifies an internal HMAC identity, checks expiry and
  tenant, and reads the current role for each request. `revoke` removes editor
  authority immediately. Inputs select fixtures; they never carry a real token.
- Payment verifies HMAC-SHA256 over the exact decoded UTF-8 `eventBody` using the
  public fixture key `getlancer-payment-fixture-only`. Signed fixture bodies follow
  `evt-1|order-1|payment.succeeded` or `evt-2|order-1|refund.succeeded`. Sign the body
  before form encoding. Identical retries add no effect; changed-body event-ID
  reuse conflicts. The ledger retains at most 100 distinct events. An initial
  timeout stays pending; a later signed success reconciles it. Refund before
  payment converges to refunded without granting an entitlement. Known paid or
  refunded facts survive a later ambiguous timeout and return 200; a pending
  timeout returns 202.

Only loopback destinations from operator configuration are used. Requests cannot
choose Redis keys, commands, endpoints, secrets or source paths. Bodies are limited
to 16 KiB; forms, JSON responses, worker queues, ledger entries and per-response
events are bounded. Projections contain fixed safe explanations, without raw event
bodies, signatures, identity keys or internal errors. HTTP responses include no
CORS headers.

These are locally executed synthetic fixtures. Payment is an emulator that moves
no money. Local requests and recordings do not certify a hosted runtime, isolation,
provider readiness or production security.
