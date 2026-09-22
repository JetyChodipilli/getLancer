# V1 service and release follow-up — 22 September 2026

This supplements the [17-document V1 matrix](V1_VERIFICATION_2026-09-15.md) and [interactive UAT report](UAT_DEMO.md). Core V1 is implemented. Hosted service activation and public-launch acceptance remain separate gates.

## Fixes

- Readiness now checks PostgreSQL as well as Spring readiness state. Liveness remains process-only. Database pool acquisition and validation are bounded so a lost database does not leave readiness waiting for the default long pool timeout.
- Unknown `APP_ENV` values now fail startup instead of silently skipping hosted HTTPS, TLS and secret checks.
- The environment checker uses the same Java-properties parser as local setup, rejects duplicates and quoted values, and accepts removed bootstrap credentials only with explicit `--existing-admin`. That option does not create or verify an administrator.
- Docker CI runs full connected backend journeys using PostgreSQL, Mailpit and private S3-compatible storage. Email links are read from actual SMTP delivery, and proof bytes use signed PUT plus completion/sanitization. Assertions cover approval, three active showcases, discovery/saves, qualification, client-confirmed hire/completion, eligible review publication and report suspension.
- CI deliberately stops only its guarded disposable database to check failed readiness, independent liveness and recovery. It also encrypts a synthetic database dump, verifies tamper rejection, restores into a separate empty database and compares key records before cleanup.
- A standalone [staging manifest](../compose.staging.yaml) and [runbook](../ops/STAGING.md) prepare a Docker API host with HTTPS ingress and external database, email and storage. They provision no infrastructure and alter no running deployment.

## Verification boundary

The backend and frontend suites pass 107 and 25 tests respectively, including the new environment regressions; typecheck and production build also pass. [Service acceptance run 35689297009](https://github.com/JetyChodipilli/getLancer/actions/runs/35689297009) passed all three jobs. Docker logs confirm real SMTP delivery, signed proof upload, all six backend journeys and database outage recovery. [Final validation run 35689544744](https://github.com/JetyChodipilli/getLancer/actions/runs/35689544744) passed all three jobs for code commit `c9f7a991998c921604df0917260d0ff9da998910`, including staging manifest validation and the encrypted restore rehearsal. The restored database preserved application row counts, migrations, the sole administrator, completed inquiry, published anonymous review and suspended showcase. The small synthetic restore took 1,142 ms; this is not a production performance target. The acceptance scripts are backend HTTP/service checks, not browser E2E. All their users, emails and records are synthetic. They cannot access or seed the owner's laptop database.

The encrypted restore rehearsal does not back up object storage, schedule backups, preserve recovery keys, test production data volume or establish a production RPO/RTO. Those remain operational responsibilities on the selected host.

## Remaining release gates

1. Provision an HTTPS Java/container host, PostgreSQL accessible to it, private storage and transactional email. Inject backend secrets and configure the frontend's `BACKEND_URL` and matching proxy secret/origin.
2. Verify the real administrator's password plus authenticator login against that database. Remove one-time bootstrap secrets after verification. OAuth activation remains deferred until Google/GitHub applications are configured.
3. Run the six acceptance journeys through the deployed browser/proxy, including storage CORS, HTTPS cookies, provider callbacks, cross-account access and distinct client rate-limit identities. Complete mobile/keyboard/screen-reader/cross-browser checks and measured load tests.
4. Configure alert delivery, encrypted backups plus object protection and a realistic separate-target restore. Approve policies, operator details and retention/moderation procedures before public indexing or launch.

The existing private cloud frontend remains an explicitly labeled demo while no hosted API is configured. Passing CI does not change it into a connected deployment.
