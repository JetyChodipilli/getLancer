# Current follow-up — 14 September 2026

Local PostgreSQL is the selected database; Supabase is not a requirement. See `ops/LOCAL_POSTGRESQL.md` for the executable startup path. The 9 September results below remain historical evidence.

## Added in this follow-up

- Optional website, country, IANA time zone and language fields across profile editing, validation, public display, moderation evidence and privacy anonymization. Material changes return approved/pending profiles to draft for review.
- Booked-until dates validate in the builder's configured time zone and report field-specific errors for malformed/past dates.
- Explicit anonymous/named review choice in both client workspace and email-token flow. Anonymous is the default; public responses expose the selected name only, never the client email. Review retries preserve the selected visibility.
- Additive migration V10 for profile details and review-visibility constraint.
- Local configuration generator preserves existing credentials, fills missing local service secrets privately, and never claims database seeding.
- Local email/private image-storage Compose setup, cross-platform frontend/backend launch commands and read-only connection/admin diagnostics.
- Database tests use TEST_DB_* only, require explicit reset opt-in and reject any database name other than getlancer_test before migrations. CI and disposable Compose use the same guarded path.
- Administrator creation is logged only after transaction commit; restarts keep existing credentials.

## Verification for this follow-up

- Production frontend build and TypeScript checking passed.
- All 19 frontend/rendering/local-setup checks passed, including anonymous review defaults, credential-preserving setup and configuration validation.
- Current Java main sources compiled through Maven. All current Java test sources, including 31 PostgreSQL scenarios, compiled with the installed Java compiler against intact cached compile dependencies.
- An earlier run of the new 64-test unit suite passed. The 14 September full rerun could not finish because cached byte-buddy-1.17.7.jar is damaged and downloads time out. Compile-only verification excludes that damaged runtime dependency; it is not a substitute for rerunning the tests in a healthy environment.
- YAML and whitespace checks passed. No configured private password/storage/MFA values were found in built frontend artifacts.
- The local configuration generator completed; it preserved current database/admin values and filled missing local service keys. Removed wrapper quotes from the existing administrator password so Spring reads the intended password.
- The read-only doctor correctly reports no reachable PostgreSQL/email/storage services and no psql in this authoring workspace. The user's computer has not been accessed.

V1 is **not yet verified for public launch**. The 31 database scenarios, administrator insertion in the user's database, live provider OAuth/email/storage journeys, six browser E2E journeys, accessibility/cross-browser/load, backup restore, operational alerts and production-policy approval remain unverified. The exact local setup steps are in `ops/LOCAL_POSTGRESQL.md`.

---

# V1 release checkpoint — 9 September 2026

**Local implementation has advanced; V1 is not yet verified for public launch.** This checkpoint supersedes the outstanding code list in `V1_FIXES_AND_RELEASE_GATES.md`. It does not claim Supabase, SMTP, OAuth, Java hosting or administrator seeding have been activated.

## Implemented in this checkpoint

| Area | Behavior now in source |
|---|---|
| Supabase configuration | Spring loads local private environment properties or host-injected values. Dedicated schema configuration, certificate-verified hosted JDBC, server-only S3 credentials, public/browser upload endpoint separation, startup checks and a secret-safe configuration checker. |
| Data API boundary | V9 enables RLS and revokes public/anon/authenticated grants on all explicitly named application tables, with a post-migration verification query. Spring retains object-level authorization. |
| Media contract | Signed 10-minute PUT reservation; owner/draft checks; server completion verification; bounded decode/re-encode; distinct immutable proof copy; 640px thumbnail; temporary-object expiry/deletion. The multipart endpoint remains compatible. |
| Media privacy | Full images and thumbnails recheck publication/owner/private-grant/admin access on every read, with no-store caching. No permanent public signed read URLs. |
| API alignment | Added client/token hire/completion aliases, participant not-hired alias, review creation endpoint, proposal amount/currency validation, collection totals, and documented create/no-content statuses. Existing frontend-compatible endpoints remain. |
| Moderation | Severity and stage filters; triage notes; inquiry-evidence access audit; linked original decision/evidence/statement/result for appeals; authorized restorations reuse state/capacity rules; affected users notified. Public report intake remains available if an account is suspended. |
| Delivery and privacy operations | MFA-protected failed-mail retry (fresh actionable jobs only); deletion-review queue with policy-gated profile anonymization and explicit evidence retention; configured support/privacy/copyright contacts. |
| Analytics | Opt-in first-party usage events, allowlisted non-sensitive context, session hashing, bounded timestamp validation, acquisition categories, optional usage-event retention, proposal totals by currency. Business outcomes remain server-authoritative. |
| Reliability | Public response sample, response rate and median response time after configurable minimum sample, with a 48-hour observation boundary and spam exclusions. Default minimum 10; set 0 to withhold. |
| Abuse protection | Shared-secret validation for trusted proxy client address; ignored generic forwarded headers; per-session and per-email/login limits with independent transactions so failed login cannot roll them back. |
| Discovery/SEO | Actual-data Person/SoftwareApplication structured data, business/technology landing routes, crawlable pagination, empty taxonomy pages excluded and indexing gated to three approved projects plus site indexing flag. |
| Forms | Preserved Slate Atelier/Icy Wind, visible labels, busy/error states, focused appeal error feedback, proposal reporting UI, expanded report reason choices, private account/appeal tools and optional privacy preferences. |
| Deployment preparation | Liveness/readiness, graceful shutdown, configuration validation, provider callback instructions, isolated database-test instructions, backup/restore and alert runbook. |

Migrations are additive: `V8__release_workflows.sql` and `V9__private_data_api_boundary.sql`. Apply them only on an isolated staging database first. Do not silently change the schema of an existing installation that already has a Flyway history.

## Executed checks

- 59 Java unit/safeguard tests passed, no failures/errors/skips.
- Main and test Java sources compile, including 28 PostgreSQL integration scenarios.
- TypeScript checking and production frontend build passed.
- 15 frontend rendering/contract tests passed against the disconnected preview.
- Every application table in the migration chain is covered by the explicit V9 protection list.
- No configured private environment values were found in the built frontend/server artifacts.
- Whitespace validation passed.

The 28 PostgreSQL scenarios are **compiled, not executed** in this environment: there is no connected disposable database or available Docker/PostgreSQL runtime. New scenarios cover confirmation aliases, proposal privacy, triage authorization, private-schema grants, upload ownership, participant closure and contract pagination/statuses. Existing CI and Compose service definitions provide isolated PostgreSQL execution. No successful CI run is claimed.

## Still needed to finish verification and launch

1. User-owned **PostgreSQL database and private S3-compatible storage** values; Java backend host; sandbox SMTP; provider-registered Google and GitHub credentials; admin authenticator setup. The configuration check currently identifies missing values. Full key/connection instructions: `ops/README.md`.
2. Run Flyway on staging; verify schema exposure/grants/RLS and bucket privacy/CORS; execute all 28 PostgreSQL tests and the six browser acceptance journeys. Verify the actual Site proxy forwards distinct trusted identities, that cookies work through HTTPS, and that private thumbnails disappear immediately after access is revoked.
3. Exercise email verification/reset/renewal, Google/GitHub consent, sole-admin password + MFA, object uploads/replay/cleanup, permission/state concurrency, appeals and delivery recovery with the real providers. Confirm callback URLs and email deliverability configuration.
4. Keyboard/screen-reader/zoom/contrast, cross-browser, p95/load, backup/restore and operational alert delivery checks need staging evidence. These have not been certified by a code build.
5. Approve the actual operator/legal policies and retention rules. The existing policies page remains a draft notice; contact wiring and consent versioning do not replace approved policy text. Profile anonymization deliberately does not claim blanket erasure of engagement, IP, email-delivery or moderation evidence.
6. A suspended account can lodge a public report or contact support; the structured signed-in appeal workspace is for accounts that can authenticate. Staff must verify account ownership before associating an unauthenticated appeal with private evidence.
7. Analytics currently records first session acquisition category and source page with strict minimization; it does not reconstruct cross-device attribution or store raw search text. Provider delivery/monitoring, large-scale retention processing, fraud-model ranking and public reliability policy still require operational review.

Do not describe the private frontend preview or passing unit suite as a complete connected marketplace. Templates, subscriptions, source-code checkout, teams, automatic trust and platform payments remain outside the documented V1 scope.
