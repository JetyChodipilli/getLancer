# Database runtime roles

Hosted startup requires a PostgreSQL connection authenticated as `getlancer_runtime`. It checks the actual `current_user` and `session_user`, role flags, role memberships, database/schema/object ownership, CREATE/TEMP privileges, application table RLS and grants. It rejects superuser, CREATEDB, CREATEROLE, REPLICATION, BYPASSRLS, owner, migration, or unprovisioned connections. Explicit local profiles and disposable integration fixtures may use an owner for migrations; they do not prove hosted permissions.

The application authorizes users and checks resource ownership in Java. The fixed server role can intentionally access every application row through `getlancer_service_*` RLS policies. This is a trusted backend policy, **not database tenant isolation**. Never expose runtime credentials to clients, Supabase anonymous/authenticated roles, browser code, BI tools, or publisher containers. Database grants prohibit runtime DDL, trigger changes, TRUNCATE, and UPDATE/DELETE of immutable audit/financial history; existing triggers also protect frozen business facts. Backup/read-only roles can read sensitive application data and belong only to tightly controlled operator workflows.

## Provision a dedicated database

Use a dedicated database because provisioning removes PUBLIC CREATE/TEMP there and PUBLIC CREATE on its public schema. Review existing role memberships and catalog ownership before changing an existing installation. The scripts only operate on the selected application schema, use safe quoted identifiers, have 5-second lock and 30-second statement timeouts, and cap the reviewed table inventory at 128. They contain no credentials and never call `REASSIGN OWNED` across databases/schemas.

Run using an administrator connection with TLS certificate verification. Supply its password through your approved secret mechanism; keep passwords out of shell arguments/history/logs. In `psql -X`:

```sql
\set ON_ERROR_STOP on
SET getlancer.app_schema = 'getlancer';
\i ops/database/00_roles.sql
```

New roles begin NOLOGIN with no password. Set four **distinct** externally managed credentials with `psql`'s interactive password mechanism (or the provider's role credential API), then enable only the required logins:

```sql
\password getlancer_migration
\password getlancer_runtime
\password getlancer_backup
\password getlancer_readonly
ALTER ROLE getlancer_migration LOGIN;
ALTER ROLE getlancer_runtime LOGIN;
ALTER ROLE getlancer_backup LOGIN;
-- Enable readonly LOGIN only when a reviewed operator needs it.
```

`getlancer_migration` owns the private schema and migration objects. It has no global CREATE database/role, superuser, replication, or BYPASSRLS permissions. Run Flyway through a separate deployment migration job using this role, `classpath:db/migration`, and the private default schema. Do not put migration credentials in the API environment. For an existing owner-created schema, run `10_permissions.sql` once as administrator before switching its migration job to the migration role; it transfers only that schema's reviewed objects. Back up and verify restoration before transferring existing ownership.

After every migration, run as administrator:

```sql
SET getlancer.app_schema = 'getlancer';
\i ops/database/10_permissions.sql
```

Provisioning is idempotent. It revokes legacy PUBLIC/anonymous/authenticated table grants, resets service grants, enables RLS, and installs operation-specific trusted server policies. Runtime receives SELECT and the reviewed INSERT/UPDATE/DELETE operations needed by V1–V4.5. Migration metadata is inaccessible to runtime. UUID identifiers require no runtime sequence mutation. Trigger functions require no direct caller EXECUTE grant. The migration role remains the owner and therefore can migrate retained history only through an explicitly reviewed deployment.

New migration tables receive no default runtime permissions. An unknown table makes provisioning and hosted startup fail. A change adding a table must update the explicit table and operation lists in `10_permissions.sql`, add positive application-operation and negative permission tests, and rerun provisioning before traffic is enabled. This deliberately avoids automatically granting access to future sensitive tables. Updated function/sequence permissions also require review; do not add SECURITY DEFINER functions callable by the runtime.

## Hosted runtime configuration

Select `SPRING_PROFILES_ACTIVE=staging` or `production`, with the matching explicit `APP_ENV`. Hosted profiles disable Flyway and never import `.env`. Inject `DB_USERNAME=getlancer_runtime`, `DB_PASSWORD`, `DB_SCHEMA=getlancer`, and a JDBC URL such as `jdbc:postgresql://provider.example/db?sslmode=verify-full&sslrootcert=/run/secrets/postgres-ca.crt`. A read-only provider CA mount belongs at that path. No migration password belongs in the process. Provide the HTTPS frontend/backend/storage origins, secure cookies, proxy secret, TLS mail configuration, and the external MFA keyring described in `MFA_KEY_ROTATION.md`.

`node scripts/check-environment.mjs --from-env --existing-admin` validates injected values without printing secrets. Use `--existing-admin` only after verifying the existing administrator; Bootstrap independently verifies that state at startup. Retire bootstrap password/TOTP environment values after initial seeding.

The hosted Compose file receives explicit environment injection and limits the API to a non-root user, read-only root filesystem, writable bounded `/tmp`, no capabilities, no privilege escalation, and CPU/memory/process limits. Invoke Compose with `--env-file /dev/null` so its own default `.env` interpolation is disabled. Review `docker compose config --quiet` without printing the expanded configuration, which contains secrets. TLS termination must forward only authenticated app-proxy identity and the API port must remain private; forwarding headers are disabled in the API.

## Backup and verification

The backup role has SELECT only, including migration history, and a SELECT-only RLS policy; it has no mutation or DDL rights. Logical backups must use `pg_dump --enable-row-security --no-owner --no-acl` through the provider's verified TLS connection. Store backups encrypted outside the database and retain the independent MFA keyring securely; a database backup cannot decrypt TOTP without its key. Restore into a separate isolated database, run roles/migrations/permission provisioning, verify row counts and application readiness, and record the restore result before calling backup recovery verified. Credentials, encryption, scheduled backups, production role installation and a restore drill remain operator actions; repository changes do not execute them.

CI `DatabaseRoleGuardIntegrationTest` provisions real roles, applies all migrations, reruns permissions, verifies readable RLS tables and representative bootstrap/session/history operations, and proves elevated role flags, migration/admin identity, ownership, grants, DDL, ledger mutation and unreviewed future tables fail. The fixture only runs with `TEST_DATABASE_RESET=true` and a dedicated `getlancer_test` database. Run it alongside the existing business/authorization regression suite; keep hosted infrastructure approval separate from these fixture results.
