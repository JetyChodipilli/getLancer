# getLancer local backend labs

Nine labs share three patterns across Java, TypeScript and Python. Each language is its own small native HTTP program. These are local execution examples, not a hosted runtime or a certification of isolation. Payment uses a synthetic emulator; authorization uses synthetic identities. No account token or merchant credential is needed.

## Prepare actual Redis

For laptop testing without installing Java, Python or Redis separately, run `npm run labs:docker` from the full repository. Docker executes the real nine-lab matrix in a disposable local container. See [laptop setup](../ops/LAPTOP_TESTING.md) for the application and browser replay dashboard. Native setup below remains available for editing and inspecting each example.

Use Redis **7.2.16**, commit `335554f18caf7bbf6b0ac2b3548133d750f00a1b`. It is labelled Redis, not Valkey. Source: https://github.com/redis/redis/tree/7.2.16. Redis 7.2 is BSD-3-Clause; see its COPYING and REDISCONTRIBUTIONS.txt. This package does not distribute Redis. Later Redis versions use different licensing; this pin does not approve hosted deployment.

```bash
git clone --depth 1 --branch 7.2.16 https://github.com/redis/redis.git redis-source
make -C redis-source -j4 MALLOC=libc redis-server
redis-source/src/redis-server --bind 127.0.0.1 --protected-mode yes --port 6379 --save '' --appendonly no
```

Use a disposable local instance without valuable data. Labs use only the key `getlancer:<run UUID>:item`; reset never flushes a database. The verifier starts and stops its own Redis process in a temporary directory, with persistence off.

## Start and inspect

From the extracted package root, choose the language README. Set `LAB_RUN_ID` to a fresh UUID and `LAB_PORT` to a local port. JDK 17, Node 22.18+ or Python 3.12 supplies the HTTP and cryptography standard library. No npm, pip or Maven install is needed. Request input is bounded form data, not a destination or command shell. This protocol is for local source reproduction; a future isolated provider must translate the certified gateway contract without exposing these ports publicly.

```bash
LAB_RUN_ID=00000000-0000-4000-8000-000000000001 LAB_PORT=8100 python3 labs/python/server.py
curl -sS http://127.0.0.1:8100/health
curl -sS http://127.0.0.1:8100/request -H 'Content-Type: application/x-www-form-urlencoded' --data 'runId=00000000-0000-4000-8000-000000000001&pattern=cache&operationId=read'
```

Repeat `read` to see the real Redis hit. Send `expire`, then `read` to observe a miss. `read-unavailable` and `read-timeout` require operator-set failure fixture ports; the verifier supplies a refused connection and a real TCP blackhole. Both fall back to the synthetic store within a bounded socket deadline.

Security: `pattern=security&operationId=authorize&identity=editor` succeeds. `viewer`, `other-tenant`, `expired`, and `tampered` deny. Send `revoke`, then retry editor to see current authority checked; `reset` restores only this process's policy.

Payment: HMAC-SHA256 the exact UTF-8 `eventBody` with the public demonstration key in `labs/fixtures.json`; this is not a production secret. `evt-1|order-1|payment.succeeded` grants one synthetic entitlement; identical retry adds none. A changed raw body with the old signature denies. `evt-2|order-1|refund.succeeded` revokes it. Refund first stays pending, then converges to refunded when success arrives, without granting access. `timeout` stays pending until a signed event resolves it. No commerce SDK or external endpoint exists.

## Verify all scenarios

Node 22.18+ drives the real HTTP matrix, including foreign run, malformed inputs, bounded outputs, event order, redaction and two-run reset isolation. It requires the relevant language runtime and the exact Redis binary. The suite uses no app database, session, email, object storage, browser fixture, or platform credentials.

```bash
REDIS_SERVER="$PWD/redis-source/src/redis-server" node scripts/verify-lab-scenarios.mjs --language python
```

Replace `python` with `java` or `typescript`. In the full repository, omit `--language` to verify all nine labs. `--record` writes only successful, actual local recordings after the complete matrix passes. These recordings retain the original time and primary-source SHA-256 and are labelled Replay in the dashboard. They cannot establish KVM/network/provider isolation or the hosted startup target.

The repository also runs `REDIS_SERVER=/path/to/redis-server node scripts/verify-lab-packages.mjs` on Linux with GNU tar. It verifies the generated archives, extracts each into a fresh temporary directory, and runs the included HTTP verifier there without repository dependencies or package installation.

References: https://redis.io/docs/latest/commands/set/ and https://redis.io/docs/latest/commands/pexpire/ for real expiry; https://docs.stripe.com/webhooks for the raw-body verification, duplicate and ordering concerns being illustrated. The emulator's compact payload and fixture protocol are getLancer teaching examples, not Stripe-compatible events.

Full contract and release boundaries: `docs/v48/CONTRACT.md`.
