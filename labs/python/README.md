# Python local scenario labs

Run `server.py` with Python 3.12. It uses the standard library and binds HTTP only
to `127.0.0.1`. One process owns one disposable UUID run, three fixture patterns,
and one monotonically increasing event sequence. If `LAB_RUN_ID` is omitted, the
server creates a UUID at startup. `/health` reports it with the SHA-256 of the
actual `server.py` bytes.

From the source package root, start a local **Redis 7.2.16** fixture on loopback.
Use an isolated Redis process with persistence disabled:

```sh
redis-server --bind 127.0.0.1 --port 6379 --save '' --appendonly no
```

In another terminal:

```sh
export LAB_RUN_ID=11111111-1111-4111-8111-111111111111
export LAB_PORT=8100
export LAB_REDIS_PORT=6379
export LAB_REDIS_FAILURE_PORT=6380
export LAB_REDIS_TIMEOUT_PORT=6381
python3 labs/python/server.py
```

Keep the failure port unused. The timeout fixture is a TCP listener that accepts
connections without replying; the shared verifier creates it. The Redis socket
budget is 250 ms for a complete operation. Ports are operator configuration at
startup; HTTP requests cannot supply destinations, keys, commands or paths.

```sh
curl http://127.0.0.1:8100/health
curl -H 'Content-Type: application/x-www-form-urlencoded' \
  --data 'runId=11111111-1111-4111-8111-111111111111&pattern=cache&operationId=read' \
  http://127.0.0.1:8100/request
```

Run `read` twice to observe a real Redis miss then hit. `expire` uses `PEXPIRE 0`;
the next read misses. `read-unavailable` and `read-timeout` use the configured
failure fixtures and fall back to the synthetic store. Cache `reset` deletes
only this run's Redis key and resets its store read counter.

Security `authorize` requires exactly one `identity` field: `editor`, `viewer`,
`other-tenant`, `expired` or `tampered`. The server issues and verifies synthetic
HMAC identities internally. `revoke` updates the current role policy so a signed
editor is denied on the next request. `reset` restores the current run's editor
policy. No platform identity or account token is accepted.

Payment `deliver` requires `eventBody` and `signature`. Sign the exact UTF-8 body
with HMAC-SHA256 and public demonstration key `getlancer-payment-fixture-only`:

```sh
python3 -c 'import hmac; print(hmac.digest(b"getlancer-payment-fixture-only", b"evt-1|order-1|payment.succeeded", "sha256").hex())'
```

Allowed bodies use `evt-1` through `evt-9999`, `order-1` and either
`payment.succeeded` or `refund.succeeded`, separated by `|`. Signatures must be
64 lowercase hexadecimal characters. Altering the body invalidates its old
signature. An identical retry adds no effect; reusing its ID with a different
signed body conflicts. The ledger retains at most 100 events. `timeout` leaves a
fresh payment pending, and signed success reconciles it. A refund delivered
first stays pending and later converges to refunded without granting an
entitlement. `reset` clears only this run's payment ledger.

All requests are limited to 16 KiB. Unknown or duplicate fields, malformed
encoding, foreign run IDs, query strings and unsupported paths/methods/content
types are rejected before effects. Responses contain at most four ordered,
redacted events and 4096 UTF-8 bytes. Resets preserve the process event sequence.
The process retains no event history or account secrets.

Run the shared actual-HTTP matrix from the repository or source package root:

```sh
REDIS_SERVER=/absolute/path/to/redis-7.2.16/src/redis-server \
  node scripts/verify-lab-scenarios.mjs --language python
```

The verifier supplies Redis, refused/timeout ports and fresh run IDs. Its checked
recordings describe local execution of this exact source hash. Payments are a
synthetic emulator and never move money. Source and local recordings do not
certify hosted isolation or production readiness; hosted runtime remains off.
