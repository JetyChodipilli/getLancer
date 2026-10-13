# Go local scenario labs

These original MIT examples run natively with Go 1.24 or newer using only the
standard library. They bind HTTP and Redis connections to `127.0.0.1`. One
disposable process owns one UUID run, its cache key, current authorization
policy, payment emulator ledger, and a monotonic event sequence.

Install Go from [the official distribution](https://go.dev/dl/) and verify its
published checksum. The V5.1 local proof used Go 1.24.13. No Go modules, downloaded
packages, application accounts, payment provider, or platform credentials are
required. Keep `server.go` available: `/health` reports SHA-256 of its actual
primary source bytes. Normal source launches use `go run`; a compiled executable
also requires its reviewed original source path to remain present.

Start an isolated **real Redis 7.2.16** fixture. Redis is a separate dependency;
this package contains no Redis implementation or third-party source. Its license
and installation are managed separately by the operator.

```sh
redis-server --bind 127.0.0.1 --port 6379 --save '' --appendonly no \
  --protected-mode yes --maxmemory 16mb --maxclients 32
```

From the extracted source package or repository root, in a second terminal:

```sh
export LAB_RUN_ID=11111111-1111-4111-8111-111111111111
export LAB_PORT=8100
export LAB_REDIS_PORT=6379
export LAB_REDIS_FAILURE_PORT=6380
export LAB_REDIS_TIMEOUT_PORT=6381
go run labs/go/server.go
```

`LAB_RUN_ID` must be a canonical lowercase UUID; omission generates a random v4
UUID. All four ports accept only decimal values from 1 through 65535. Keep the
failure port unused. The shared verifier creates the timeout fixture, an actual
TCP peer that accepts a connection without replying. Requests cannot select
destinations, keys, Redis commands, tokens, source paths, or external endpoints.

```sh
curl http://127.0.0.1:8100/health
curl -H 'Content-Type: application/x-www-form-urlencoded' \
  --data 'runId=11111111-1111-4111-8111-111111111111&pattern=cache&operationId=read' \
  http://127.0.0.1:8100/request
```

The exact form fields are `runId`, `pattern`, `operationId`, plus the fields
required below. Unknown/missing/duplicate fields, malformed UTF-8 or escapes,
foreign run IDs, query strings, unsupported methods/paths/content types, and
oversized bodies fail before effects. `/health` accepts GET without a body;
`/request` accepts only POST with `application/x-www-form-urlencoded` and a
supported content length. Chunked request bodies are rejected. Go's HTTP
parser rejects conflicting framing and malformed HTTP before the lab handler.

| Pattern | Operations | Extra fields and effects |
| --- | --- | --- |
| `cache` | `read`, `expire`, `read-unavailable`, `read-timeout`, `reset` | None. Real Redis miss → fixed synthetic store read → SET with 5000 ms TTL; repeat reads hit. `expire` performs PEXPIRE 0. Both failure paths produce bounded fallback. Reset deletes only the current run's key and resets its store counter. |
| `security` | `authorize`, `revoke`, `reset` | `authorize` needs `identity`: `editor`, `viewer`, `other-tenant`, `expired`, or `tampered`. Synthetic HMAC claims, expiry and tenant are checked; current role is checked on every request. Revocation denies the next signed editor; reset restores editor policy. |
| `payment` | `deliver`, `timeout`, `reset` | `deliver` needs `eventBody` and `signature`. Exact-body HMAC is verified before the fixed grammar or ledger changes. Retry is idempotent, conflicting reuse gets 409, and early refunds reconcile without granting access. A synthetic 25 ms timeout preserves confirmed facts. |

Payment bodies are `evt-1` through `evt-9999`, `order-1`, and
`payment.succeeded` or `refund.succeeded`, joined by `|`. Sign the exact UTF-8 body
with HMAC-SHA256 and the public demo key `getlancer-payment-fixture-only`.
Signatures are exactly 64 lowercase hexadecimal characters. This is a learning
emulator: it never moves money, accepts provider credentials, or grants platform
access. Authorization uses the separate public synthetic identity key
`getlancer-identity-fixture-only`; neither key is a secret or a production key.

| Resource | Bound |
| --- | --- |
| Local run lifetime | 15 minutes from runtime initialization; expired requests fail closed and the process shuts down. |
| Accepted connections | 8; HTTP keepalive disabled. Idle/slow readers do not create unbounded application goroutines. |
| HTTP header/read/idle deadline | 1 second each; configured header limit 4096 bytes. Go may buffer bounded parser lookahead in addition to that header limit. |
| HTTP body / JSON response | 16 KiB / 4096 UTF-8 bytes. |
| Redis operation | One absolute 250 ms socket deadline including GET/SET/PTTL; 2048 consumed reply bytes, 1024-byte bulk value cap. |
| Event projection | Up to four typed, redacted events per response; no retained event history. |
| Retained emulator events | At most 100; a retry at capacity remains idempotent. |
| Counters | 10,000,000 event sequence / 1,000,000 store reads; reset never rewinds sequence. |
| Runtime profile | GOMAXPROCS 2; Go GC memory target 64 MiB, an advisory runtime target rather than an OS isolation limit. |
| Shutdown | SIGINT/SIGTERM, run expiry, or launcher-parent loss; up to 3 seconds to drain requests, then forced socket closure. Afterwards, bounded DEL of only this run's key; offline Redis still expires it after 5000 ms. |

No disk ledger or event history is retained. Reset affects only this run. HTTP
results contain UUID run/request IDs, primary source hash, duration, typed state,
and ordered event metadata; they omit raw claims, signatures, endpoints, keys,
and incoming bodies. Lab-handler errors contain only a fixed code and safe
message. Go's HTTP parser may reject invalid HTTP earlier with its own bounded
standard error response.

Run the focused native tests, including Go's race detector:

```sh
go test -race -v labs/go/server.go labs/go/server_test.go
go vet labs/go/server.go labs/go/server_test.go
```

Run the actual HTTP/Redis success and failure matrix:

```sh
REDIS_SERVER=/absolute/path/to/redis-7.2.16/src/redis-server \
  node scripts/verify-lab-scenarios.mjs --language go
```

The verifier creates two independent native runs and a private Go build cache;
it does not substitute fake responses for Go or Redis. For a manual launch in a
sanitized environment, provide an existing writable `GOCACHE` directory, with
`GOENV=off` and `GOTOOLCHAIN=local`. The standard-library source works offline
after installing the Go toolchain. First-launch compilation is additional to
native startup time.

Source availability and local acceptance do not certify a hosted container. The
operator must separately verify the signed image/source binding, admission,
budgets, privacy and cleanup before enabling hosted execution.
