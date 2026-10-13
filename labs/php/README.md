# PHP local pattern labs

Run with **64-bit PHP CLI 8.3 or newer**, using only core streams, JSON, PCRE,
hash and random functions. No Composer installation, framework, `redis` PHP
extension, web server or application database is needed. Start the CLI program
directly; `php -S` would give this program a different request lifecycle and is
not supported.

```sh
export LAB_RUN_ID=11111111-1111-4111-8111-111111111111
export LAB_PORT=8100
export LAB_REDIS_PORT=6379
export LAB_REDIS_FAILURE_PORT=6380
export LAB_REDIS_TIMEOUT_PORT=6381
php labs/php/server.php
```

An omitted `LAB_RUN_ID` creates a fresh UUID. A supplied UUID must use canonical
lowercase spelling. The process binds only `127.0.0.1`, owns that run's in-memory
role and payment ledger, and retains state between requests. `/health` reports
the actual primary file's SHA-256. Configuration accepts numeric ports from 1
to 65535; request input cannot choose destinations, Redis keys, commands or paths.

Cache scenarios need a separate **Redis 7.2.16** process on loopback with
persistence disabled. Obtain that exact Redis release from the Redis project;
check `redis-server --version` before running:

```sh
redis-server --bind 127.0.0.1 --port 6379 --save '' --appendonly no \
  --protected-mode yes --maxmemory 16mb --maxclients 32
```

Leave the failure port unused. The timeout fixture is a TCP listener that
accepts without replying; the shared verifier supplies that fixture. Redis is
never replaced with an in-memory imitation. Two `read` requests observe a real
miss and hit; `expire` performs real `PEXPIRE 0`; `read-unavailable` and
`read-timeout` select the refused and stalled fixture ports. A complete Redis
action has one absolute 250 ms deadline. `reset` deletes only the current run's
key and resets its store-read count.

```sh
curl http://127.0.0.1:8100/health
curl -H 'Content-Type: application/x-www-form-urlencoded' \
  --data 'runId=11111111-1111-4111-8111-111111111111&pattern=cache&operationId=read' \
  http://127.0.0.1:8100/request
```

Security `authorize` takes exactly one `identity`: `editor`, `viewer`,
`other-tenant`, `expired` or `tampered`. The fixture creates signed identities
with startup-bound expiry, verifies signatures with `hash_equals`, and reads
the current run's role and tenant policy on every authorization. `revoke`
changes the current role; the same signed editor is then denied. `reset`
restores that run's role. This is a demonstration policy, not a platform login;
account tokens and arbitrary identities are rejected.

Payment `deliver` takes `eventBody` and `signature`. Sign the **exact UTF-8 body**
with HMAC-SHA256 and public fixture key `getlancer-payment-fixture-only`:

```sh
php -r 'echo hash_hmac("sha256", "evt-1|order-1|payment.succeeded", "getlancer-payment-fixture-only"), PHP_EOL;'
```

The strict body grammar is `evt-1` through `evt-9999`, `order-1` and
`payment.succeeded` or `refund.succeeded`, separated by `|`. The signature must
be exactly 64 lowercase hexadecimal characters. Signature validation precedes
event parsing and effects. Identical retries are idempotent; a changed signed
body using an existing event ID conflicts. At most 100 distinct events are
retained, and an identical retry still succeeds when the ledger is full.
A refund delivered before payment remains pending, then converges to refunded
without granting access. `timeout` is a 25 ms synthetic emulator deadline,
preserving confirmed payment/refund facts; `reset` clears only this run's ledger.
No payment gateway call or real money movement occurs.

The custom HTTP subset accepts only `GET /health` and form `POST /request`.
Queries, duplicate or unknown form fields, invalid UTF-8 or percent escapes,
foreign runs, transfer encoding, duplicate content lengths, extra/pipelined
body bytes and foreign Host headers fail closed. Headers are limited to 8 KiB
and 32 fields, bodies to 16 KiB, responses to 4096 bytes and four redacted events.
One active connection and a 32-entry socket backlog serialize effects; each
request has a one-second absolute read deadline and 250 ms response deadline.
The process uses a 32 MiB PHP memory ceiling, at most 10,000 connections, a
bounded counter and a default one-hour lifetime. Operators may set
`LAB_LIFETIME_SECONDS` from 1 to 86400 at startup. Lifetime termination closes
the listener; an already active bounded action may finish within its socket
budget. Event sequence remains monotonic through all resets. No request bodies,
account secrets, destination URLs or payment signatures appear in events/logs.

Run the native parser/state/HTTP tests without Redis, then the shared matrix
with exact real Redis. The matrix supplies fresh runs and refused/stalled fixtures:

```sh
php -l labs/php/server.php
php -l labs/php/tests.php
php labs/php/tests.php
REDIS_SERVER=/absolute/path/to/redis-7.2.16/src/redis-server \
  node scripts/verify-lab-scenarios.mjs --language php
```

This original source is MIT-licensed; see `LICENSE`. Local execution and
recordings do not certify hosted isolation or production readiness. The hosted
PHP runtime remains disabled until the separate signed provider/operator gates
and current source/image identity checks succeed.
