# getLancer with Docker PostgreSQL

Docker Compose runs PostgreSQL 16, Spring Boot, Mailpit and private MinIO storage locally. It creates database **getLancer**, user **postgres**, and uses `DB_PASSWORD` from your private `.env`. The requested local password is `postgres`. The database listens on **localhost:5433**, leaving port 5432 free for the PostgreSQL already installed on your computer.

Install Docker Desktop (Compose v2) and Node 22.13+, then run from the repository root:

```sh
npm ci
npm run setup:docker
npm run services:docker
npm run dev:local
```

Open `http://localhost:3000`. The backend runs inside Docker, so a separate local Java/Maven process is unnecessary. Stop a previous `backend:local` process before starting the containers. If the old mail/storage stack is running, stop it with `docker compose -f compose.local-services.yaml stop` first to free ports 1025/8025/9000/9001; its data volumes are preserved.

The first startup also compiles the local storage server and client from pinned official MinIO releases, so it takes longer than subsequent cached starts. `ops/minio/Dockerfile` replaces the unavailable Docker Hub images. The server release includes the October 2025 session-policy security fix; licenses remain in each image. See the [official server release](https://github.com/minio/minio/releases/tag/RELEASE.2025-10-15T17-29-55Z). This local-only setup is not a production storage recommendation.

`setup:docker` changes the database URL to `jdbc:postgresql://localhost:5433/getLancer`, username to `postgres`, and the frontend API URL to `http://localhost:8080`. It preserves existing passwords and authenticator keys; missing local database passwords default to `postgres`. Compose overrides the backend's internal database URL to `jdbc:postgresql://db:5432/getLancer`. Credentials stay out of source control; application properties read them from the environment.

The setup script does not create database records. Compose waits for database readiness and private bucket initialization before starting Spring. Spring applies Flyway migrations and seeds the single administrator only when absent. Configure the private administrator password and add `ADMIN_TOTP_SECRET` to an authenticator as a time-based setup key before signing in. Existing database administrator credentials are never overwritten by bootstrap.

- Application/API readiness: `http://localhost:8080/actuator/health/readiness`
- Local email inbox: `http://localhost:8025` — verification, reset and inquiry messages stay here.
- Private storage console: `http://localhost:9001` — use the storage keys from `.env`.
- Google/GitHub OAuth remain optional and unavailable until configured.

The named `docker-database` volume is separate from the older `database` volume, preserving prior data. PostgreSQL initialization variables apply to a new empty volume; changing `.env` will not change an existing database role password. Do not delete volumes to troubleshoot a login. Use `docker compose logs --tail=80 api` and `docker compose ps` for startup errors. `npm run stop:docker` stops containers without deleting data.

The disposable Docker CI job verifies the exact database name, ten migrations, one synthetic administrator, password + MFA login, protected admin access and private bucket. It uses its own isolated project and removes only those CI volumes afterward. It does not seed or access the owner's local database.

The hosted frontend cannot reach Docker on your computer through `localhost`. Use the [interactive demo](../docs/UAT_DEMO.md) for cloud flow UAT until a reachable Spring API is deployed and its URL configured. Demo success does not verify database connectivity.
