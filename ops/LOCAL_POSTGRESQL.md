# Run getLancer V1 with your local PostgreSQL

This is the active setup for this project. Supabase is not required. Run the commands on the same computer as PostgreSQL, from the getLancer checkout root, in PowerShell, Terminal or a shell. The cloud preview cannot reach your computer through `localhost`.

## 1. Prerequisites

- PostgreSQL running on port 5432 and an existing database named exactly `getLancer`.
- Java 17+ and Maven 3.9+ on PATH.
- Node.js 22.13+ and npm.
- Docker Desktop / Compose for the local email inbox and private proof-image storage below. PostgreSQL itself stays on your computer.

If the database is absent, create it in pgAdmin with the name `getLancer`, or run `CREATE DATABASE "getLancer";` once while connected to the maintenance database `postgres`. PostgreSQL folds unquoted SQL identifiers to lowercase, so the quotes matter for this database name. Do not recreate or drop an existing database.

## 2. Prepare private configuration

Run `npm ci`, then `npm run setup:local`. The setup command creates `.env` from `.env.example` only if absent. It preserves existing values and generates only missing administrator/authenticator and local storage secrets. It does not create users or alter database records.

Check these local values in `.env`:

```properties
APP_ENV=local
APP_BASE_URL=http://localhost:3000
BACKEND_URL=http://localhost:8080
DB_URL=jdbc:postgresql://localhost:5432/getLancer
DB_USERNAME=postgres
DB_PASSWORD=postgres
DB_SCHEMA=getlancer
SECURE_COOKIES=false
```

The database password above is the local value requested for this project. It must already match the password of your PostgreSQL `postgres` role; editing `.env` does not change the database role's password. If upgrading an existing getLancer database, preserve its existing `DB_SCHEMA` and Flyway history.

Keep `.env` on your computer and out of Git. Use unquoted values, including passwords with `@` or `#`; escape backslashes as `\\` for Spring's Java-properties import. OS environment variables override this file. The generic scripts reject duplicate keys instead of silently picking a different password than Spring.

Add the `ADMIN_TOTP_SECRET` from `.env` to an authenticator using **Enter setup key → Time based**. The account name is `jetychodipilli@gmail.com`. Keep the setup key and the configured `ADMIN_BOOTSTRAP_PASSWORD` private. Do not regenerate a secret to recover an existing admin; startup deliberately preserves its stored credentials.

## 3. Start email and proof storage

```sh
npm run services:local
```

This starts Mailpit and MinIO only, and creates a private `getlancer` bucket. It does not start or replace your PostgreSQL server. Check initialization with `docker compose -f compose.local-services.yaml logs storage-init`; successful initialization must finish before uploads work.

- SMTP: `localhost:1025`; test inbox: `http://localhost:8025`.
- Storage API: `http://localhost:9000`; console: `http://localhost:9001`.
- MinIO credentials are `OBJECT_STORAGE_ACCESS_KEY` and `OBJECT_STORAGE_SECRET_KEY` in `.env`.
- Account verification and inquiry emails appear in Mailpit. They are not delivered to real inboxes in this local configuration.

If these services are already running elsewhere, configure their actual values manually; `setup:local` will not replace remote service credentials.

## 4. Check and start the backend

```sh
npm run doctor:local
npm run backend:local
```

The doctor performs a read-only database login and checks the local email/storage TCP listeners. It never applies migrations or seeds accounts. If `psql` is not on PATH, add PostgreSQL's `bin` directory or set `PSQL_PATH` to the full `psql.exe` / `psql` path. A TCP-only success is not password verification or upload verification.

The backend applies Flyway migrations, then seeds the single admin if absent. It logs **Administrator created. Password and authenticator sign-in are required.** only after the seed transaction commits. On subsequent starts it reports **Administrator already exists; credentials unchanged.** It will never promote an unrelated existing user or silently reset an existing admin.

Confirm `http://localhost:8080/actuator/health/readiness` reports `UP`. Run `npm run doctor:local` again: it should find exactly one active administrator with authenticator configured. This check does not replace the actual MFA login below.

## 5. Start the website and sign in

In a second terminal:

```sh
npm run dev:local
```

Open `http://localhost:3000/login`. Use the configured admin email/password, then enter the current six-digit authenticator code. The local launcher loads the frontend's backend URL and uses port 3000 consistently with email links, cookies and OAuth callbacks.

Google and GitHub OAuth remain optional until their provider credentials and exact localhost callback URLs are registered. Email/password login, local email confirmation and MFA do not require them.

## 6. Verify the V1 journeys

Use synthetic accounts and data. Complete all six journeys in `docs/10_TESTING_QA.txt`: builder signup/approval; project proof/review/publication; fourth-slot blocking and archive/reactivation; client inquiry confirmation; hire/completion/review; moderation and removal from discovery. Verify the new named/anonymous review choices and profile fields through their approval cycle.

For repeatable database tests use the **separate** disposable Compose test service:

```sh
docker compose --profile test run --rm verify
```

For Maven without Docker, first create an empty database named `getlancer_test`. Set these environment variables in that terminal (not the app `.env`): `TEST_DB_URL=jdbc:postgresql://localhost:5432/getlancer_test`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`, and `TEST_DATABASE_RESET=true`. Then run `mvn -B -f backend/pom.xml verify`. Tests use only the `getlancer_test` schema and reject any other database name before Spring/Flyway initialization. The fixture records in that test database are deliberately truncated; never use it to store real work.

## Cloud publication boundary

The private cloud preview hosts the JavaScript frontend. Working cloud accounts require a reachable hosted Spring Boot service and a PostgreSQL database reachable by that service. Updating local credentials or publishing the frontend does not seed the database on your computer. Local success does not certify production email, provider OAuth, browser accessibility, load handling, backups, monitoring or approved legal policies.
