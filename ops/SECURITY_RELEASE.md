# V1–V5.2 security release and operator handoff

This procedure activates the repository controls on a hosted installation. A green CI run uses disposable services and does not certify a production host, provider credentials, backups, alerts or policy approval. The earlier security-remediation record is in [SECURITY_REMEDIATION.md](../docs/SECURITY_REMEDIATION.md); current release coverage and production qualification must be assessed against the reviewed checkout.

## Review and repository protection

Require all nine jobs in `getLancer validation (V1-V5.2)` to succeed:

| Required job | Scope |
|---|---|
| `lab-scenarios` | Native lab/runtime and downloadable-source verification |
| `backend` | Complete PostgreSQL/Maven verification and dependency evidence |
| `frontend` | Types, changed-source lint, content/security contracts and browser acceptance |
| `docker-startup` | Connected services/browser journeys and encrypted synthetic restore |
| `secrets-and-sast (java-kotlin, manual)` | Java CodeQL plus tree/history secrets |
| `secrets-and-sast (javascript-typescript, none)` | JavaScript/TypeScript CodeQL plus tree/history secrets |
| `secrets-and-sast (python, none)` | Python CodeQL plus tree/history secrets |
| `container-security` | Image advisories and exact current applicability evidence |
| `validation-status` | Fail-closed aggregate of all required job groups and matrix entries |

Record the actual tested full commit SHA and run URL/attempt. PR checks test the merge checkout; retain the reviewed PR head/base separately and review the merge result rather than requiring it to equal the head. Require the current main revision to pass before release. Retain JUnit, browser, raw/assessed image scans, source/history scans, SARIF, dependency inventories and applicability evidence in an access-controlled release archive; CI artifacts expire after seven days, and the intermediate frontend distribution after one day. Review skipped browser tests and dependency dispositions. An application-specific `not_affected` remains visible; it is not a zero-advisory claim. A failed run's public regeneration-review artifact is input for a reviewed follow-up commit, not release approval.

The repository owner must install a main-branch ruleset requiring these checks, an independent approving review, dismissal of stale reviews, resolution of review threads and blocking force pushes/deletion. Require review of CODEOWNERS paths and restrict bypass to a documented emergency process. The managed repository connection has no administration capability; adding CODEOWNERS and workflows does not enable a ruleset. Verify the actual ruleset before allowing releases. Pin deployable images to the reviewed immutable digest and keep a software inventory with the release.

## Prepare the existing installation

1. Stop writes and take an encrypted database and private-object-storage backup. Restore into an isolated target and record counts, access checks, recovery point and elapsed recovery time. Preserve historical MFA key versions separately from the database. Use [DATABASE_RUNTIME_ROLES.md](../docs/DATABASE_RUNTIME_ROLES.md) for backup-role details.
2. Inventory actual provider, bootstrap, proxy, database, storage, payment/webhook and OAuth credentials. Rotate any real credential exposed in Git, logs or insecure configuration at its issuing provider, revoke old credentials and invalidate affected sessions. A redacted secret scan does not revoke a credential. Scan full history and verify exposure scope; do not rewrite shared history without a separately reviewed recovery plan. The narrow scanner allowances cover exact public synthetic fixtures only.
3. Prepare a versioned external 32-byte AES keyring and administrator recovery procedure using [MFA_KEY_ROTATION.md](../docs/MFA_KEY_ROTATION.md). Supply historical versions needed by existing ciphertext and backups. Keep staging and production credentials distinct. Record secret-manager access and rotation ownership without putting secret values into release evidence.
4. Provision the dedicated database's separate migration, runtime, backup and optional read-only roles. Use the reviewed SQL in `ops/database/00_roles.sql` and `10_permissions.sql`; follow their lock/time limits and ownership-transfer procedure. Run all migrations through the migration role. V26 adds encrypted MFA storage, V27 adds append-only security events, and V28 adds session issuance time and revokes existing MFA-verified sessions. Run permissions again after migration. Never put the migration password in the API process.
5. Start only the new application version while traffic remains stopped. The startup MFA upgrade encrypts any legacy seed and revokes affected sessions/challenges in one transaction; missing keys, corrupt envelopes or more than 1,000 credential rows stop readiness. Plan a reviewed offline migration for larger installations. Verify the plaintext credential count is zero and version counts match the intended keyring; print no ciphertext. Every administrator must complete a fresh password-plus-TOTP sign-in. Do not run old plaintext-reading instances beside this version.

The database runtime is a trusted server role with service RLS policies and explicit operation grants. Tenant isolation is enforced by current-user and owner/membership checks in Java. No browser, reporting integration or publisher may receive runtime credentials. The startup role guard rejects elevated flags, owner/migration credentials, DDL/TEMP privileges, unexpected memberships and unreviewed grants. Do not bypass it to restore readiness.

## Configure the host and providers

Inject the matching `SPRING_PROFILES_ACTIVE` and `APP_ENV` (`staging` or `production`), secure cookies, canonical HTTPS frontend/backend/storage URLs, verified TLS JDBC and the private schema. Hosted Spring does not import `.env` and does not run Flyway. Use your secret manager for the runtime password, MFA keyring, proxy secret, SMTP, storage, OAuth and payment values. Retire the bootstrap password and raw TOTP seed after first successful provisioning. `ADMIN_EMAIL` has no personal default.

Use [STAGING.md](STAGING.md) for provider acceptance, with the role separation above. For injected production configuration, validate without displaying expanded secrets:

```sh
node scripts/check-environment.mjs --from-env --existing-admin
docker compose --env-file /dev/null -f compose.staging.yaml config --quiet
```

Use `--existing-admin` only after verifying that prerequisite. The staging manifest deliberately selects staging; create and review the corresponding production host configuration before production promotion. Keep API port 8080 private behind TLS ingress. Pass only authenticated app-proxy identity headers; generic forwarded headers must not establish client identity. Rate-limit abusive traffic at ingress as well as in the application. Set request/time/concurrency budgets compatible with 6 MiB ordinary JSON, 64 KiB signed webhooks and the existing upload bounds; do not globally raise limits for a future feature.

Keep the demo publisher on its isolated origin with separate gateway/publisher secrets, no application cookies and no database credentials. Verify its routing, sandbox/CSP, fixed recipe hashes, health checks and absence of private network access. Restrict private storage CORS to the exact frontend origin and necessary signed PUT headers; retain a lifecycle rule for abandoned temporary uploads. Verify real SMTP delivery, OAuth callbacks, payment signatures and webhook retry/idempotency with provider sandbox accounts before enabling them. Keep real payments disabled until provider and policy acceptance is recorded.

## Acceptance, monitoring and retention

Record real-provider browser acceptance for account verification, secure cookies, password-plus-MFA, reset/session revocation, all four publishing capacities, private uploads/downloads, team and business membership removal, owner/cross-account denial, signed payment callbacks, maintenance and hosted demos. Check stale administrator MFA is refused for writes and private package reads after fifteen minutes. Verify CSP nonces change across HTML responses and no shared cache stores authenticated responses. Readiness includes the database; email, storage, payments and publisher need independent checks.

Install external alerts and assign a responding operator for readiness loss, 5xx/latency, database-pool exhaustion, rate-limit saturation, MFA lockouts, repeated signed-webhook failures, administrator/role changes, audit insertion failures, outbox/job backlog, storage cleanup failures and backup/restore failures. Preserve request IDs and event metadata without logging tokens, cookies, passwords, TOTP, bodies or personal contact data. Anonymous login/authorization denial events have a shared 1,000-per-minute audit budget; identified security events remain unsampled. Monitor ingress denial metrics as well as the bounded audit stream.

| Data | Repository behavior | Operator decision |
|---|---|---|
| Sessions, MFA challenges, OAuth state and access grants | Delete after expiry | Verify the scheduled job runs and expiry indexes stay effective. |
| Rate buckets | Delete one day after expiry | Monitor growth and denial volume. |
| Account tokens and terminal failed-email bodies | Cleanup seven days after token expiry or terminal-email creation | Confirm delivery and expiry evidence is retained without usable token bodies. |
| Pending image uploads | Queue deletion five minutes after expiry | Set private-bucket lifecycle and confirm deletion queue processing. |
| Optional web analytics | Raw consented events retained at most 24 hours; unlinked daily counts expire after 90 days | Verify actual cleanup and approve consent, aggregate retention and salt custody. |
| Security audit events | Append-only; runtime can insert/read, cannot update/delete | Approve archive/retention and legal holds; use a separately reviewed owner maintenance process. No automatic audit retention is configured. |
| Financial, contract, delivery, moderation and account-closure evidence | Preserved subject to domain state and approved holds/anonymization | Approve actual retention periods and deletion/hold processing; no blanket erasure claim. |
| CI and release evidence | CI expires after seven days | Export required evidence under the approved release retention/access policy. |

Do not invent legal retention periods from this table. Record approved durations, responsible operators and verification dates in the deployment's policy register. Repository mechanisms do not establish jurisdictional compliance.

## Incident response and rollback

For suspected account or provider compromise, restrict the affected operation, revoke relevant sessions/challenges and exposed provider credentials, preserve append-only events/request IDs and coordinate recovery through the approved operator process. For MFA key loss or corrupt envelopes, follow the fail-closed recovery procedure in `MFA_KEY_ROTATION.md`; never enable plaintext fallback or grant a new administrator to bypass verification. Confirm recovery with current credentials and cross-account checks before restoring traffic.

Application rollback must preserve compatibility with every migration applied to the reviewed release and its encrypted MFA/session fields. Do not deploy a pre-hardening image that requires plaintext secrets, and do not reverse Flyway automatically. Use a separately rehearsed database/object restore only when necessary, with the corresponding historical keys, verified permissions and provider-state reconciliation. Record the decision, approved image digest, recovery evidence and follow-up actions.

For later integrations, inspect the actual migration inventory and request/authorization contracts in the reviewed checkout. Do not reuse the historical V4.6 hold or migration-renumbering instructions as current release evidence. Live production admission, signed qualification, provider custody and recovery remain separate operator gates.
