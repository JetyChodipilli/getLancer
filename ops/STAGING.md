# Connect the private frontend to the V2.5 Java API

The existing private Site hosts the React frontend. It cannot run the Java API or access PostgreSQL on your laptop. This procedure prepares a separate Docker-capable Linux host with HTTPS ingress. No host, domain, database or paid service is provisioned by these files.

## Prepare a reviewed release

1. Choose a commit whose three `getLancer validation (V1-V2.5)` jobs have passed, including connected browser acceptance. Record the full SHA. Build `backend/Dockerfile` from that checkout and tag it with that SHA: `docker build -t getlancer-api:FULL_COMMIT_SHA backend`. Set `BACKEND_IMAGE` to that exact tag if building on the host, or push to your chosen registry and use its immutable digest. Keep the preceding image for application rollback.
2. Copy `.env.example` to the ignored `.env.staging`. Set `APP_ENV=staging`, `SECURE_COOKIES=true`, the actual HTTPS frontend `APP_BASE_URL`, HTTPS `BACKEND_URL`, `DEMO_MODE=false`, and a random `BACKEND_PROXY_SECRET` of at least 32 characters. The proxy secret must match the frontend runtime secret.
3. Use a separate staging PostgreSQL database, private `DB_SCHEMA=getlancer`, and backend-only database credentials. Set `DB_URL=jdbc:postgresql://YOUR_HOST:5432/getLancer?sslmode=verify-full&sslrootcert=/run/secrets/postgres-ca.crt`. Download the database provider's CA certificate, set `DB_CA_FILE` to its absolute host path and ensure the non-root container user can read it. The template refuses to create a missing certificate path.
4. Configure private S3-compatible storage and a verified SMTP sender in `.env.staging`. Both storage endpoints must use HTTPS; the upload endpoint must be browser-reachable. Restrict storage CORS to the exact frontend origin, PUT and required signed headers. Use TLS or SSL with SMTP. Supply separate staging administrator bootstrap credentials and authenticator secret. OAuth may remain blank until provider applications are ready.
5. Add `BACKEND_IMAGE` and `DB_CA_FILE` to `.env.staging`. Validate without printing secrets:

```sh
node scripts/check-environment.mjs .env.staging
docker compose --env-file .env.staging -f compose.staging.yaml config --quiet
docker compose --env-file .env.staging -f compose.staging.yaml up -d --wait --wait-timeout 240
```

The manifest explicitly runs staging mode, binds the API to loopback, mounts the CA read-only, uses the image's non-root user, drops capabilities and limits writable files to `/tmp`. It is deliberately separate from local Compose and starts no development database, Mailpit or MinIO service.

## Connect and verify

Configure the host's HTTPS reverse proxy to forward to `127.0.0.1:8080`, including the original cookies and the application's `X-GetLancer-Proxy` / `X-GetLancer-Client-IP` headers. Use the exact header names in `app/api/v1/[...path]/route.ts` and `Security.java`; do not invent replacement trusted headers. Keep ingress limits compatible with the 5 MB image limit. Do not expose port 8080 directly to the Internet.

Set `BACKEND_URL`, `DEMO_MODE=false`, `BACKEND_PROXY_SECRET` and `APP_BASE_URL` in the existing frontend's server runtime. BACKEND_URL always switches to real services, including if an old DEMO_MODE=true value remains; false additionally rejects a missing URL. Preview routes then redirect to the real workspaces. Keep `INDEX_PUBLIC_PAGES=false`. Database, SMTP, S3 and administrator secrets belong only on the backend.

Check both `/actuator/health/readiness` and `/actuator/health/liveness`. Readiness includes PostgreSQL; liveness intentionally does not. A stopped database must remove the instance from ready traffic without triggering a database-dependent restart loop. Email and storage require independent delivery/upload checks and alerts; readiness does not certify those services.

Run all six journeys in `docs/10_TESTING_QA.txt` in the real browser through the actual frontend proxy. Verify provider storage CORS, signed upload and thumbnail access, email delivery and expiry, admin password plus MFA, client confirmation, review moderation, cross-account access and proxy rate-limit identity. The Docker CI suite exercises equivalent backend service journeys, but cannot certify the chosen host, provider or browser configuration.

Run the read-only deployed check after connecting the frontend:

```sh
node scripts/verify-staging.mjs .env.staging
```

It verifies HTTPS origins, Java readiness/liveness, exact frontend-to-Java provider responses, anonymous rejection and redirects for all four preview workspaces. It measures eight catalog reads at concurrency two and reports observed p95 latency. This small availability measurement is not a capacity/load certification; set the launch traffic target and measure that load separately against staging. The script never creates accounts, sends messages, uploads files, resets data or prints credentials. Use `--existing-admin` only with the verified administrator precondition described below.

V2.5 provider acceptance must also record these browser results with owner-controlled staging accounts:

| Journey | Required evidence |
|---|---|
| Registration and administrator login | Actual provider email delivered; email confirmation; password followed by MFA. |
| Publishing | Browser signed upload succeeds under the provider's CORS policy; image/thumbnail can be read only at the correct visibility. |
| Hiring-manager consent | Invitation has no access before acceptance; only the intended verified recipient can accept. |
| Private briefs and talent | Reload preserves saved records; another business cannot read them; closed requests reject editing/matching. |
| Current proof and concierge | Real approved builder/team results; revoked consent disappears; only an MFA administrator can recommend on opted-in briefs. |
| Membership revocation | Removed manager loses access immediately in both API and browser. |

Disposable CI now drives these V2.5 hiring journeys through the built frontend and real Java API at desktop, phone and tablet sizes, with no intercepted or mocked network responses. `tests/connected` refuses to run outside the guarded CI project. It cannot be pointed at owner, shared or production data; provider acceptance is a separate operator exercise.

After successful first admin login, remove `ADMIN_BOOTSTRAP_PASSWORD` and `ADMIN_TOTP_SECRET` from the backend environment; the existing database record is retained. Recreate the API container to apply the changed environment. Run the configuration checker with `--existing-admin` only after verifying that database has the administrator. Keep protected authenticator recovery material outside Git.

## Promotion and recovery

Before public launch, configure alerts for database failures, HTTP errors/latency, email retries, upload failures and job backlog. Approve actual policy text and operator contact details. Configure encrypted database backups and separately protect object storage. Rehearse restore into a separate target and verify database-to-object consistency; record recovery point and duration. `scripts/verify-restore-ci.mjs` proves only a small synthetic database archive can be encrypted, restored and checked—it is not a backup scheduler, object-storage backup or production recovery guarantee.

To roll back application code, select the preceding verified image and recreate the API. Do not automatically reverse Flyway migrations. Review schema compatibility first; use a separately tested recovery procedure if a database restore is necessary. This staging manifest hardcodes staging safeguards; production promotion requires a reviewed production host configuration with `APP_ENV=production` and the policy gates enabled.
