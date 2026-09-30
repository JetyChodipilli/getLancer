# V2.5 service connection and release steps

This runbook applies to the combined V1–V2.5 application. Use [V2.5 acceptance](../docs/V2_5_ACCEPTANCE.md) for current automated evidence and [hosted staging preparation](STAGING.md) for provider-dependent acceptance. The stable `/api/v1` namespace serves all implemented phases.

The active choice is **Docker PostgreSQL**. Follow [Docker setup](DOCKER_LOCAL.md) for the working configuration, service startup, administrator seeding and diagnostics. Supabase is not required. See [hosted staging preparation](STAGING.md) when a backend host and external services are available. Native PostgreSQL remains an alternative in [Local PostgreSQL setup](LOCAL_POSTGRESQL.md). Do not point integration tests at production or a shared database: they truncate test fixtures. The current private Site is a frontend preview; Spring requires a separate Java/container host.

## Fill the private environment file

Copy `.env.example` to `.env` if absent. The existing local file is preserved. Spring loads optional `.env` properties from the checkout root or backend directory, with actual environment variables taking precedence. Use unquoted Java-properties values (escape backslashes as `\\`); `.env` files and certificates must stay out of Git. On a host, inject values through its secret settings instead.

| Service | Values to supply |
|---|---|
| PostgreSQL | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_SCHEMA=getlancer`, `DB_POOL_MAX` |
| Private S3-compatible storage | `OBJECT_STORAGE_ENDPOINT`, `OBJECT_STORAGE_UPLOAD_ENDPOINT`, `OBJECT_STORAGE_REGION`, `OBJECT_STORAGE_BUCKET`, `OBJECT_STORAGE_ACCESS_KEY`, `OBJECT_STORAGE_SECRET_KEY` |
| Java backend | HTTPS `BACKEND_URL`; identical `BACKEND_PROXY_SECRET` in the frontend and backend |
| Site | HTTPS `APP_BASE_URL` matching the actual private Site URL, `APP_ENV=staging`, `SECURE_COOKIES=true` |
| Transactional email | `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH=true`, and TLS/SSL selection; `EMAIL_FROM_ADDRESS` |
| Google OAuth app | `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` |
| GitHub OAuth app | `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET` |
| First administrator | `ADMIN_EMAIL` is already set; supply `ADMIN_BOOTSTRAP_PASSWORD` and a Base32 `ADMIN_TOTP_SECRET` installed in your authenticator |
| Optional analytics | `ANALYTICS_ENABLED=true`, random `ANALYTICS_HASH_SALT`; default 90-day retention for optional usage events only |
| Production policies | Approved policy text and version, `LEGAL_DOCUMENT_VERSION`, `POLICIES_APPROVED`, `SUPPORT_EMAIL`, `PRIVACY_EMAIL`, `COPYRIGHT_EMAIL` |

The frontend only needs `BACKEND_URL`, `BACKEND_PROXY_SECRET`, `APP_BASE_URL`, and `INDEX_PUBLIC_PAGES=false` during staging. Never copy database, S3, SMTP, administrator, or OAuth secrets into frontend public variables. Supabase Auth keys/anon keys are **not** needed for the existing Spring session system.

For hosted JDBC use `jdbc:postgresql://YOUR_HOST:5432/getLancer?sslmode=verify-full&sslrootcert=/run/secrets/postgres-ca.crt`. Use the actual PostgreSQL host, database and username from your provider or database administrator. Mount its CA certificate read-only in the backend container. Use a direct connection or a session pooler compatible with persistent Spring/Hikari/Flyway sessions.

## Private schema and storage

Flyway creates the schema in `DB_SCHEMA` and applies migrations. The database connection must own that dedicated schema and its tables; existing V1 instances must keep their existing schema/history and plan any schema move separately. Do not expose the application schema through any browser-accessible database API. Migration V9 enables RLS and revokes browser-role grants only on explicitly named application tables. Run `ops/verify-private-schema.sql` in the application schema and also verify provider-specific schema exposure settings, if applicable.

Create a **private** bucket. Copy its S3 endpoint and region from your storage provider. The upload endpoint must be reachable by browsers and ordinarily equals the S3 endpoint. Local Compose instead uses `http://storage:9000` internally and `http://localhost:9000` for browser signatures. Configure/verify storage CORS for the exact frontend origin, `PUT`, and signed content headers. Test it with the real browser/provider before release.

Uploads use a 10-minute signed PUT to a random `pending/` key. Completion verifies length, decodes PNG/JPEG, bounds pixels, strips metadata, and stores an immutable clean copy plus a 640px thumbnail. Replay cannot overwrite the verified copy. Expired temporary objects enter the deletion queue after a five-minute margin. Add a bucket lifecycle rule for abandoned `pending/` objects as defense in depth. All proof reads, including thumbnails, recheck current visibility/access and use `private, no-store`.

## OAuth and administrator

Register the exact frontend callback URLs:

- `APP_BASE_URL/api/v1/auth/google/callback`
- `APP_BASE_URL/api/v1/auth/github/callback`

The browser calls the same-origin API proxy; the backend exchanges codes and issues HttpOnly cookies. The registered callback host must be the frontend host. Provider keys enable the respective buttons; disabled buttons do not imply failed account registration.

The one-time bootstrap creates the sole administrator only if absent and never resets it on restart. Test password plus authenticator challenge, then remove the bootstrap password and TOTP seed from host settings. Use `--existing-admin` with the environment checker after this one-time setup. Preserve a protected recovery procedure for the authenticator secret. The source contains no administrator password.

## Validation and promotion

1. `node scripts/check-environment.mjs .env` checks names/format without printing values.
2. Run `docker compose --profile test run --rm verify` against its disposable test database, or the isolated PostgreSQL CI service. Never substitute a live `DB_URL`.
3. Build and run the backend container with staging environment variables and the CA certificate mount. Check `/actuator/health/readiness` and `/actuator/health/liveness`.
4. Configure the frontend runtime settings and verify two different clients are assigned distinct rate-limit identities through the actual proxy. Forwarded client identity is accepted only with the shared proxy secret; untrusted headers fall back to socket identity. Do not trust generic `X-Forwarded-For`.
5. Execute the six core acceptance journeys in `docs/10_TESTING_QA.txt`: onboarding, approved publishing, fourth-slot/archive/reactivation, inquiry confirmation, hire/completion/review, moderation/restriction. Also verify trust/availability/capacity; team invitation consent, role permissions, leads and staffing; business hiring-manager consent, private briefs, saved talent lists, current-proof matching, administrator MFA concierge and revoked access against the configured staging providers, following `docs/V2_5_ACCEPTANCE.md` and `ops/STAGING.md`. Test both OAuth providers when enabled, signed upload/CORS, thumbnail privacy, expired-link renewal, appeal restoration and email retry.
6. Configure monitoring for 5xx, p95 latency, database pool exhaustion, expired pending jobs and failed email delivery. Health probes are implemented; the external alert destination must be configured by the host operator.
7. Restore an encrypted staging backup into a separate staging target and verify accounts, approved proof, qualified inquiry outcomes and media permissions. Record restore duration and recovery point. A provider backup checkbox alone does not prove restore readiness.
8. Review policy text, owner/contact details and retention rules. Account closure hides data immediately; the admin queue supports holds and profile anonymization after policy approval. Engagement, IP and moderation evidence is deliberately retained for an explicit policy-driven review. This is not a claim of blanket data erasure.
9. Keep indexing disabled until all connected acceptance tests and operational/legal gates pass. Indexable taxonomy pages require at least three real approved listings; no empty generated landing pages are published.

Public response reliability appears only at the configured minimum of 10 qualified inquiries (changeable with `PUBLIC_RELIABILITY_MIN_SAMPLE`; set 0 to withhold), each observed for at least 48 hours. Spam/quarantine is excluded. These measurements are not a guarantee of service or a purchased trust badge.

This runbook does not claim that external services have been provisioned, migrations have run, or public-launch gates have passed.
