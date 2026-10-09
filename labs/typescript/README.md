# TypeScript local scenario labs

These three disposable labs execute real HTTP requests and Redis commands. Authorization identities and payment events are synthetic fixtures. The payment emulator never moves money. Local evidence does not certify a hosted runtime.

Use Node **22.18 or newer** and Redis **7.2.16**. No npm packages, app account, platform token, or merchant credentials are needed. The repository source package includes the shared protocol, fixture values, and verifier.

Start pinned Redis locally with persistence disabled:

```sh
redis-server --bind 127.0.0.1 --port 6379 --save '' --appendonly no
```

In another terminal, start the fixture server from the repository root:

```sh
LAB_RUN_ID=3aed1de9-c4a5-4f4c-934d-1e0610ecbbfd \
LAB_PORT=8100 LAB_REDIS_PORT=6379 \
LAB_REDIS_FAILURE_PORT=6380 LAB_REDIS_TIMEOUT_PORT=6381 \
node --experimental-strip-types labs/typescript/server.ts
```

Each process has one fixed UUID. Use a new UUID for a new run. The server binds only to `127.0.0.1`; Redis destinations are fixed to that host and selected only by startup environment. Keep the failure port unused. For `read-timeout`, the verifier creates a TCP fixture that accepts connections without replying on the timeout port. A closed timeout port instead demonstrates refusal. Each Redis connection has a 250 ms absolute deadline.

Check source identity and perform a cache miss, followed by a hit:

```sh
curl http://127.0.0.1:8100/health
curl -X POST http://127.0.0.1:8100/request \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data 'runId=3aed1de9-c4a5-4f4c-934d-1e0610ecbbfd&pattern=cache&operationId=read'
```

All requests use `POST /request` and exactly `runId`, `pattern`, `operationId`, plus the operation inputs below. Responses include bounded redacted events, the startup source SHA-256, and the fixed run UUID. Protocol denials contain only a safe error and have no execution evidence. Query strings, duplicate or unknown fields, invalid UTF-8, foreign run IDs, and bodies over 16 KiB are rejected before fixture effects.

| Pattern | Operations | Extra fields |
| --- | --- | --- |
| `cache` | `read`, `expire`, `read-unavailable`, `read-timeout`, `reset` | None |
| `security` | `authorize` | `identity=editor`, `viewer`, `other-tenant`, `expired`, or `tampered` |
| `security` | `revoke`, `reset` | None |
| `payment` | `deliver` | `eventBody`, `signature` |
| `payment` | `timeout`, `reset` | None |

Cache reads use Redis `GET`, `SET PX 5000`, and `PTTL` for the fixed current-run key. `expire` uses `PEXPIRE 0`. Failed cache reads use the synthetic store; failed expiry or reset reports 503. Cache reset deletes only the current run key and resets its store-read count. It preserves the process event sequence.

Authorization issues and verifies HMAC identities internally. It checks signatures, expiry, tenant, claimed role, and the current role policy on every authorization. After `revoke`, the editor is denied with `REVOKED`; `reset` restores its editor policy. No request accepts an account token or secret.

Payment signatures are lowercase hexadecimal HMAC-SHA256 of the **exact UTF-8 eventBody**, using the public demonstration key `getlancer-payment-fixture-only`. For example, sign `evt-1|order-1|payment.succeeded` and send it using form URL encoding:

```sh
node --input-type=module -e "import {createHmac} from 'node:crypto'; console.log(createHmac('sha256','getlancer-payment-fixture-only').update('evt-1|order-1|payment.succeeded').digest('hex'))"
curl -X POST http://127.0.0.1:8100/request \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'runId=3aed1de9-c4a5-4f4c-934d-1e0610ecbbfd' \
  --data-urlencode 'pattern=payment' --data-urlencode 'operationId=deliver' \
  --data-urlencode 'eventBody=evt-1|order-1|payment.succeeded' \
  --data-urlencode 'signature=PASTE_THE_64_CHARACTER_SIGNATURE'
```

The only accepted signed event grammar is `evt-[1-9][0-9]{0,3}|order-1|payment.succeeded` or the corresponding `refund.succeeded`. Verification happens before interpreting the payload. Altering the body while retaining the signature returns 401. An identical retry returns 200 with no second effect; reusing its ID with different content returns 409. The ledger retains at most 100 distinct events. Refund before payment returns 202 pending, then payment reconciles to refunded with zero entitlement. Payment followed by refund changes one entitlement to zero. An ambiguous timeout adds no ledger event or entitlement; signed delivery reconciles it. Timeout returns 202 while pending and 200 after a confirmed payment or refund, preserving the ledger facts. Settled ledger state persists until `reset`.

Run the shared scenario matrix from the repository root. `REDIS_SERVER` points to the actual pinned Redis 7.2.16 executable; the verifier supplies fresh run IDs, ports, the refusing destination, and timeout TCP fixture:

```sh
REDIS_SERVER=/absolute/path/to/redis-7.2.16/src/redis-server \
node scripts/verify-lab-scenarios.mjs --language typescript
```

Successful verification prints `SCENARIO_MATRIX_OK language=typescript`. Add `--record` to save original local recordings after the complete matrix passes. Stop this server and Redis with Ctrl-C when done. The startup source hash identifies the primary file's actual bytes; it carries no isolation or production-readiness claim.
