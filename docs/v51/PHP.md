# V5.1 PHP adapter — S07-B06

The three native PHP labs use `labs/php/server.php`, a persistent CLI stream
socket process. This deliberately avoids PHP's per-request web lifecycle: role
revocation, payment idempotency and sequence persist across HTTP requests and
reset only within the process's UUID namespace. Original MIT source and setup
are in `labs/php/LICENSE` and `labs/php/README.md`.

The cache adapter speaks the bounded RESP subset to real Redis 7.2.16: actual
GET/SET PX/PTTL, DEL and PEXPIRE, one 250 ms operation deadline, refused/timeout
fallback and per-run key isolation. Security verifies synthetic HMAC identities,
fixed startup expiry and current role/tenant policy. Payment verifies exact raw
event bodies before mutation, retains at most 100 events, preserves confirmed
facts on timeout, rejects conflicting IDs and reconciles refund-before-payment
without resurrecting an entitlement.

The server binds loopback, hashes its actual primary bytes, validates strict
form fields and UUID identity before effects, returns bounded redacted typed
events and monotonically increasing sequence, and accepts no remote destination,
code, file path, account token or arbitrary Redis command. HTTP header/body,
socket deadline, memory, connection count, lifetime and ledger limits are
explicit. The source uses only PHP core features; Composer and application
dependencies are unchanged.

Validation entry points:

```sh
php -l labs/php/server.php
php -l labs/php/tests.php
php labs/php/tests.php
REDIS_SERVER=/absolute/path/to/redis-7.2.16/src/redis-server \
  node scripts/verify-lab-scenarios.mjs --language php
```

The native tests exercise malformed UTF-8/forms, signature/body exactness,
current-role revocation, tenant/expiry, refund ordering, duplicate/capacity
invariants, persisted state and real HTTP rejection/connection deadlines. The
shared matrix additionally requires exact real Redis and independent processes
to verify per-run namespace isolation. Actual current-source execution and
clean extracted-source reproduction must be observed before phase acceptance.
Observed local acceptance on 2026-10-13 with native PHP 8.3.6 CLI and real Redis
7.2.16: both source files passed native `php -n -l`; `php -n labs/php/tests.php`
reported `PHP_NATIVE_TESTS_OK checks=951 actual_cli_http=true`; the shared
matrix reported `SCENARIO_MATRIX_OK language=php labs=3 requests=156
redis=7.2.16 mode=local`. The tested primary SHA-256 was
`175670dc09dddfb82129040d20f5a6a13c17573d772d88ebf5e883809e84acbf`.
These observations include actual native HTTP processes and Redis wire traffic.
Current-head CI and clean extracted-source reproduction remain separate root
integration checks. Hosted PHP is disabled unless existing signed evidence,
source/image identity, budget and operator admission gates pass.
