# Connect the private frontend to a hosted V1 API

The existing private Site hosts the React frontend. It cannot run the Java API or access PostgreSQL on your laptop. This procedure prepares a separate Docker-capable Linux host with HTTPS ingress. No host, domain, database or paid service is provisioned by these files.

## Prepare a reviewed release

1. Choose a commit whose three `getLancer validation (V1-V2)` jobs have passed. Record the full SHA. Build `backend/Dockerfile` from that checkout and tag it with that SHA: `docker build -t getlancer-api:FULL_COMMIT_SHA backend`. Set `BACKEND_IMAGE` to that exact tag if building on the host, or push to your chosen registry and use its immutable digest. Keep the preceding image for application rollback.
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

After successful first admin login, remove `ADMIN_BOOTSTRAP_PASSWORD` and `ADMIN_TOTP_SECRET` from the backend environment; the existing database record is retained. Recreate the API container to apply the changed environment. Run the configuration checker with `--existing-admin` only after verifying that database has the administrator. Keep protected authenticator recovery material outside Git.

## Promotion and recovery

Before public launch, configure alerts for database failures, HTTP errors/latency, email retries, upload failures and job backlog. Approve actual policy text and operator contact details. Configure encrypted database backups and separately protect object storage. Rehearse restore into a separate target and verify database-to-object consistency; record recovery point and duration. `scripts/verify-restore-ci.mjs` proves only a small synthetic database archive can be encrypted, restored and checked—it is not a backup scheduler, object-storage backup or production recovery guarantee.

To roll back application code, select the preceding verified image and recreate the API. Do not automatically reverse Flyway migrations. Review schema compatibility first; use a separately tested recovery procedure if a database restore is necessary. This staging manifest hardcodes staging safeguards; production promotion requires a reviewed production host configuration with `APP_ENV=production` and the policy gates enabled.
