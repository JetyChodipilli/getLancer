# Deployment security requirements

Use the existing [release checklist](../ops/SECURITY_RELEASE.md) and [runtime database roles](../docs/DATABASE_RUNTIME_ROLES.md) alongside this document.

1. Inject private environment settings into the backend or named Worker secret bindings. Never use public build prefixes for private keys. Keep local `.env*`, database passwords, keyrings, logs and audit scratch directories out of published artifacts.
2. Set the correct hosted environment and canonical HTTPS origins. Keep generic forwarded headers disabled; trust asserted client IPs only through the configured secret-bearing platform proxy. Restrict direct backend and publisher administration ingress.
3. Apply reviewed migrations with separate operator credentials. The application must use `getlancer_runtime`, a private schema and verified PostgreSQL TLS. **Review [V35's legacy access effects](LEGACY-IDENTITY.md) and [V36–V38's historical dispute holds and replay recovery](PAYMENT-DISPUTES.md) before rollout.**
4. Keep private object buckets private. Configure narrow browser PUT CORS, credentials, pending-upload lifecycle and verified-copy retention. A signed upload URL grants only its specific temporary operation.
5. Use distinct proxy, publisher and gateway secrets. Configure provider webhook secrets independently. Rotate keys after suspected exposure; source deletion does not revoke a credential.
6. Protect MFA keyring versions and recovery material independently of database backups. Preserve UUID-based ownership, financial records and publisher identity history during restore.
7. Serve only built assets and application routes. Keep Vite/debug servers, Mailpit, PostgreSQL, storage administration and filesystem checkouts private. Verify security headers and cache policy at the public edge.
8. Configure alerts, retention and restore drills. Audit metadata must not contain raw credentials, request bodies or capability URLs. Restrict access to pending email bodies and legacy identity review contact data.
9. Admit production traffic based on measured latency, error rates, DB lock/pool behavior and provider concurrency. Rate defaults are not a capacity guarantee. Keep optional payment, publishing and hosted lab features disabled until their independent operational requirements pass.

Repository publication is not production deployment. Actual cloud IAM, secret stores, ingress, TLS, edge limits, backups and merchant/provider approvals were not changed by this audit.
