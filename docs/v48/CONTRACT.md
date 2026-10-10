# V4.8 backend scenarios — implementation contract

Baseline: main `b78cd25a926b40771c4597ee49bc4140db881f53`. Source: GetLancer_V4_5_to_V5_2_Full_Plan.pdf, S04-B01–B08, pp.21–22, 50–51, 67. Revision 1, 2026-10-09.

## Delivery boundary

Implement nine locally reproducible labs (cache/Redis, synthetic authorization, payment emulator × Java, TypeScript, Python), free source packages, and an evidence dashboard at `/labs/scenarios`. Preserve Spectral Studio tokens and existing shadcn components. Adapt the pinned ShadcnStore dashboard grid/header, retaining its MIT license. No new frontend dependencies or global theme replacement.

These programs execute real HTTP requests locally. Redis cases use an actual pinned Redis 7.2.16 process, not a map or Valkey. Security and payment use synthetic fixtures; payment is an emulator and never moves money. Recordings come from those programs, with original timestamps, run IDs and source hashes. Replay controls only inspect recorded evidence. No client animation, source sample or local test may confer hosted-runtime certification.

Public runtime remains off. The V4.7 isolated provider/build/network/cleanup/restore/budget gates, immutable image certification, >=100 fresh hosted starts per adapter with queue-separated p95, and recruited-user comprehension research remain required for full S04 completion. Do not call this phase complete while these gates are open.

## Local protocol

One process is one disposable local fixture run, fixed by `LAB_RUN_ID` (UUID). Bind HTTP to `127.0.0.1`, port `LAB_PORT` (default 8100). Redis host is fixed `127.0.0.1`, operator-configured `LAB_REDIS_PORT` (default 6379), `LAB_REDIS_FAILURE_PORT` (refused connection), and `LAB_REDIS_TIMEOUT_PORT` (test blackhole). No browser or request field can supply destinations, Redis keys, raw account tokens, commands or file paths. No CORS. Reject query strings, wrong paths/methods/content types, unknown or duplicate fields, malformed UTF-8, oversized requests (16 KiB), invalid IDs and foreign run IDs before any effect.

`GET /health` → JSON `{mode:"Local execution",language,runId,sourceHash}`. `sourceHash` is SHA-256 of that language's primary source file, computed from actual bytes at startup. It is version evidence, never an isolation or production-readiness badge.

`POST /request`, `Content-Type: application/x-www-form-urlencoded`, fields `runId`, `pattern` (`cache|security|payment`), `operationId`, plus the explicitly allowed operation inputs. Form encoding avoids a custom Java JSON parser. Response status equals `status` in JSON:

```json
{"runId":"uuid","sourceHash":"64 lowercase hex","requestId":"uuid","pattern":"cache","status":200,"durationMs":2,"state":{"cache":"MISS","storeReads":1,"ttlMs":5000},"events":[{"runId":"uuid","sourceHash":"64 lowercase hex","requestId":"uuid","sequence":1,"type":"CACHE_MISS","edge":"service-redis","recordedAt":"UTC ISO timestamp","summary":"Redis missed the current run's fixture key."}]}
```

Responses stay <=4096 UTF-8 bytes. Events are emitted by completed observed actions, ordered monotonically across the whole process, max four per response, with an exact fixed `edge` allowlist: `client-service`, `service-redis`, `service-store`, `service-policy`, `policy-store`, `service-emulator`, `emulator-ledger`. No tokens, HMAC secrets, signatures, raw event payloads, endpoints or arbitrary errors enter projections. Protocol denials use `{error:{code,message}}` without execution evidence. Reset clears only this run's pattern fixtures, never another run or the process event sequence. Bound all retained state and event identifiers.

### Cache

Allowed operations: `read`, `expire`, `read-unavailable`, `read-timeout`, `reset`; no extra input. Fixed key `getlancer:<runId>:item`, synthetic store value `fixture-item`, TTL 5000 ms. Real Redis GET/SET PX/PTTL produces MISS then HIT without another store read. `expire` uses real PEXPIRE 0, then the next read misses. Refused/timeout operations use the configured fixture ports and fall back to the store within a 250 ms socket budget (no pretend disconnect flag). Reset DELs only the current key. State: `{cache:MISS|HIT|FALLBACK|EXPIRED|RESET,storeReads:integer,ttlMs:integer}`. Emit CACHE_MISS/HIT/EXPIRED/FALLBACK/RESET and STORE_READ only after the corresponding action.

### Security

Allowed operations: `authorize` with exactly `identity=editor|viewer|other-tenant|expired|tampered`; `revoke`, `reset` without extra inputs. Issue and verify a synthetic HMAC identity internally; reject tampered signatures, expiry, another tenant and wrong role. Read the current role policy on every request so a formerly valid editor identity denies after revoke. Never accept or emit a platform token. State: `{decision:ALLOW|DENY|RESET,reason:ALLOW|ROLE|TENANT|EXPIRED|SIGNATURE|REVOKED|RESET,currentRole:editor|viewer}`. Emit AUTH_ALLOWED/DENIED/REVOKED/RESET with safe decision explanations.

### Payment

Allowed operations: `deliver` with `eventBody` and `signature` (64 lowercase hex), `timeout`, `reset`. Synthetic HMAC-SHA256 fixture key is `getlancer-payment-fixture-only` (public demonstration key, never a merchant credential). Verify constant-time HMAC of the exact UTF-8 eventBody before interpreting it. Payload grammar: `evt-[1-9][0-9]{0,3}|order-1|payment.succeeded` or `...|refund.succeeded`. Any altered body with the old signature denies (401). Identical event-ID/body retry adds no effect; same event ID with a different body conflicts (409). Max 100 distinct events, deny overflow. Timeout stays PENDING without entitlement; signed success reconciles. Refund-before-payment stays pending, then converges to REFUNDED without granting an entitlement; payment-before-refund grants once then revokes once. State: `{payment:PENDING|PAID|REFUNDED,entitlements:0|1,processedEvents:integer}`. Emit PAYMENT_APPLIED/DUPLICATE/SIGNATURE_DENIED/CONFLICT/PENDING/RESET and REFUND_PENDING/APPLIED; ledger edges require actual ledger observation.

Status rules: authorization signature/expiry denial is 401, tenant/role/revoked denial 403; authorize success, revoke and reset are 200. A payment timeout is 202 only while PENDING, and 200 with preserved state when payment or refund is already confirmed. Refund before payment is 202; later convergence and normal/duplicate delivery are 200. Payment signature denial is 401; changed event identity and retention overflow are 409. A correctly signed unsupported event is a 400 protocol denial. Foreign run is 403; malformed fields are 400; unsupported path/method/content type are 404/405/415; request overflow is 413. An actual failed Redis reset/expiry is a 503 protocol denial without invented action evidence.

## Material and evidence interface

`labs/catalogue.json` declares nine IDs `<language>-<pattern>`, titles, summaries, scenarios and fixed architecture descriptions. Build creates `/labs/material-index.json` with items `{id,language,pattern,title,summary,sourceHash,archiveHash,sourceUrl,recordingUrl,setup,scenarios,edges}`; sources at `/labs/sources/<language>.tar.gz`. `recordingUrl` is null when no matching checked recording exists. Source packages include primary source, README, shared protocol, fixtures and verification scripts; no app credentials, build output or unrelated private files.

Recordings: `{mode:"REPLAY",labId,language,pattern,runId,sourceHash,recordedAt,responses:[local protocol response...]}`. Persist actual verifier recordings under `labs/recordings`; reject stale source, foreign identities, non-monotonic event sequence, unsupported fields/types/edges/status, oversized values and malformed recordings. No fabricated recording on failure. The UI distinguishes Source only / Recorded local execution / Hosted runtime unavailable, exposes original timestamp/hash, and highlights only an edge backed by the displayed event. Equivalent text lists every transition. Native selects/buttons, 16px fields, >=44px controls, visible keyboard focus, responsive reflow, reduced motion and inline retry/errors are required.

## Ownership and verification

- Java leaf: `labs/java/**`; primary `LabServer.java`, standard-library JDK 17 HttpServer; launch `java labs/java/LabServer.java`.
- TypeScript leaf: `labs/typescript/**`; primary `server.ts`, Node >=22.18 standard library; launch `node --experimental-strip-types labs/typescript/server.ts`.
- Python leaf: `labs/python/**`; primary `server.py`, Python 3.12 standard library; launch `python3 labs/python/server.py`.
- UI leaf: `app/labs/scenarios/**`, `components/labs/scenario-dashboard.tsx`, `components/labs/scenarios.css`, `lib/scenario-evidence.ts`, `tests/scenario-evidence.test.mjs`, `tests/browser/v48.spec.ts`, `docs/v48/License.md`; consume this fixed contract and catalogue, do not alter shared contract/build/server work.
- Driver: all shared `labs` files excluding the language leaves, verifier/material scripts, navigation link, docs/gates, build and CI integration. Reverify each leaf and integrate before final reporting.

Each language uses the same HTTP success/failure/reset/foreign-run/redaction matrix. Four passes: implement, expert reread, defect hunt, low-cost polish. Runnable checks are inspected commands executed directly within this authorized build; Unlazy parser/lint track the ledgers without installing hooks or invoking unreviewed inherited shell commands.
