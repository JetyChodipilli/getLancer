# V1 verification — 15 September 2026

The core V1 implementation is in source and passes the automated suite. **Connected cloud operation and public-launch acceptance remain pending.** The private Sites frontend has no hosted Java API configured. A frontend deployment does not start Spring Boot, migrate PostgreSQL, seed the owner's administrator, or activate email/OAuth/storage.

This report supersedes the outstanding-code and unexecuted-database-test lists in the historical 7–14 September checkpoints. It is not a claim that every nonfunctional or operational requirement has passed.

## Requirements provenance and release boundary

All 17 supplied Word documents, `00_MASTER_PRD(1).docx` through `16_ENVIRONMENT_CONFIG(1).docx`, were compared with the repository's numbered text specifications. Their baseline text matches after removing extraction whitespace and table separators. The API text has separately labelled implementation addenda. [SOURCE_DOCUMENT_VERIFICATION.json](SOURCE_DOCUMENT_VERIFICATION.json) records each original file's SHA-256 and comparison result.

V1 is public software discovery, builder approval, three active showcases, qualified inquiries, client-confirmed outcomes and eligible moderated reviews. One account can be both a builder and a client. The exact supplied transparent logo and the Slate Atelier/Icy Wind design are preserved.

The roadmap in `01_VISION`, section 11, includes V1.5, V2.5 and V3.5 as well as V1–V4. Teams/recruitment belong to V2; platform contracts/payments to V3; source-code commerce to V3.5; hosted demos/deployment/maintenance to V4. Template checkout, subscriptions and paid trust/ranking are not added to V1.

## Document-to-implementation check

| Document | Implementation and evidence | Remaining acceptance |
|---|---|---|
| 00 Master PRD | All six phases have executable paths; the proof → inquiry → confirmed outcome loop is covered by PostgreSQL tests. | Connected browser journeys and operational release gates. |
| 01 Vision | Project-first public discovery; one account for browsing and building; external agreements/payments. | Marketplace validation with real approved builders and clients. |
| 02 Functional requirements | Auth, OAuth, profiles, discovery/filtering, saves, drafts/review/archive, private grants, inquiries, reviews, notifications, moderation and account tools. | Live provider consent, delivery and image storage. |
| 03 Nonfunctional requirements | Bounded collections/uploads, request limits, timeouts, private media checks, responsive layouts, health endpoints and transactional writes. | Load/p95 measurements, uptime monitoring, WCAG and cross-browser acceptance. |
| 04 Business rules/state machines | Concurrent three-slot activation, qualification, confirmation/replay, review eligibility, suspension and archive rules tested. | Real-client acceptance of wording and workflow. |
| 05 Architecture | React/Vinext frontend; Java 17/Spring modular monolith; PostgreSQL/Flyway; S3 and email outbox. | Reachable Java/container host and configured providers. |
| 06 API contract | Public/private collections, status aliases, signed uploads, confirmation/review routes, optional inquiry idempotency key and public review summary. Owner filtering occurs before pagination. | Validate reverse proxy, HTTPS cookies and provider callbacks in staging. |
| 07 Data model | Migrations V1–V10, foreign keys, unique review and administrator constraints, entitlement locks, historical events, schema isolation. | Staging migration and restore drill. The schema adapts the conceptual model: verified email claims client inquiries; provider tables and direct proof-link columns implement the V1 relationships. |
| 08 UI/UX | Compact glass navigation, project search/dock, builder/client workspace, original artwork and labelled auth forms. Login → signup navigation and failure feedback observed in the browser. | Full mobile, keyboard, screen-reader and six connected journeys. |
| 09 Security | Ownership, CSRF, URL validation, hashed tokens, MFA, account status, private exports and report/appeal checks; regression cases pass. Closure now requires email re-confirmation. | Deployed header/proxy checks, provider OAuth tests, secret rotation and alert evidence. |
| 10 Testing/QA | 106 passing backend tests including 38 PostgreSQL scenarios; 19 frontend checks; typecheck and production build. | Complete browser E2E, load, accessibility and backup recovery checks. |
| 11 Analytics | Server-authoritative qualification/outcomes; opt-in usage events; deduplication, minimization, response sample threshold and founder metrics. | Production monitoring, retention policy and acquisition measurement validation. |
| 12 Trust/safety | Approval, report triage/evidence, restrictions, audit reasons, structured appeals and notifications. Guest-engagement ownership and verified client claims tested. | Staffed moderation, copyright/escalation handling and response targets. |
| 13 Operations/deployment | Docker, isolated CI database, local startup/doctor tools, health/readiness, graceful shutdown and runbooks. | Backend deployment, real service connections, encrypted backups/restore and alert delivery. |
| 14 Legal/compliance | Rights attestation, versioned acceptance records, consent controls, private inquiries, export and policy-gated deletion processing. | Actual operator/contact details, approved policies and retention/legal review. Current policy notices are drafts. |
| 15 SEO/metadata | Canonicals, genuine structured data, filtered-page noindex, gated sitemap/taxonomy pages, original branding and public image crawl allowance. | Production share-card and Core Web Vitals checks. Indexing stays disabled. |
| 16 Environment configuration | Ignored private local configuration, server-only credentials, startup validation, separate TEST_DB settings and setup that preserves existing secrets. | Host-injected production settings and environment-specific credentials. |

## Repairs in this verification

- Public report submission accepts absent, revoked, expired or suspended sessions as anonymous; protected routes still reject those sessions. Database failures are not treated as anonymous authorization.
- Account closure first sends a 15-minute confirmation link. Explicit confirmation closes the account, revokes sessions/tokens and hides public content. The sole administrator remains protected. Final erasure remains policy-driven.
- Builders may appeal decisions on guest-client engagements. An unverified account cannot claim another client's email to view or appeal their private decisions.
- Workspace status filters run in PostgreSQL before pagination, so many drafts cannot hide active showcases. Saves on the visible gallery page are resolved even when the saved collection exceeds 50 entries.
- Client inquiry closure calls the controller's method instead of reading a field on a Spring transactional proxy, fixing the observed server error.
- Public and granted-client project responses omit moderation reasons; owner/admin management responses retain them. Product detail includes the builder's published eligible review count and average, with no fabricated zero-star rating.
- Inquiry creation accepts a missing idempotency header as documented, while duplicate and malformed submissions remain rejected. Client-supplied keys still make retries idempotent.
- Authentication checks service availability before submitting, distinguishes a disconnected preview from a temporary outage, and offers a retry action. The signup route, original art and automatic reduced-motion support remain intact.
- PostgreSQL CI health-check quoting was corrected. The full suite now actually runs instead of failing during test-container startup.

## Executed validation

[GitHub Actions run 34981803941](https://github.com/JetyChodipilli/getLancer/actions/runs/34981803941) passed both jobs for source commit `116fd6d2b9571efb460d6714b1d5e4ddedaadfd8`:

- Backend: **106 tests**, zero failures, errors or skips, including **38 PostgreSQL integration scenarios**. The embedded rules scenario also runs 59 business-rule/URL assertions; these are not counted as 59 extra JUnit tests.
- Frontend: **19 tests**, TypeScript check and production build passed.
- PostgreSQL runs only in the disposable `getlancer_test` database. No test was pointed at the owner's application database.
- All original document baselines matched. Source whitespace validation passed.
- Browser: observed login, unavailable-service feedback and navigation to the complete signup form; reviewed the rendered signup layout. No credentials were entered and no live signup was submitted. The HTTP-only internal preview reports a Vinext Web Crypto navigation fallback; the links still open through document navigation. This is not a clean connected E2E certification. The configured local API is unavailable in that preview, and discovery correctly shows an error instead of fabricating live results.

Review used the requested gstack checklists, Ponytail's preference for direct fixes, and the UI UX Pro Max/shadcn guidance while retaining the chosen design. The native gstack helper was absent; specialist review was partial and completed by the primary reviewer. No claim of a fully executed gstack CLI or external-model signoff is made.

## What is needed for connected cloud V1

1. A running HTTPS **Spring Boot backend** with network access to PostgreSQL. The current Sites host runs the frontend Worker and cannot run a JVM or reach PostgreSQL on the owner's computer.
2. Configure the host's database, private S3-compatible storage, transactional SMTP, bootstrap administrator password/TOTP and trusted-proxy values. Keep secrets out of GitHub. Follow [ops/LOCAL_POSTGRESQL.md](../ops/LOCAL_POSTGRESQL.md) locally and [ops/README.md](../ops/README.md) for hosting.
3. Set the frontend's server-only `BACKEND_URL` and matching proxy secret/origin. Register Google/GitHub credentials and exact callbacks to activate provider sign-in.
4. Run the six acceptance journeys from `10_TESTING_QA` against staging, including real email confirmation, uploads, approval, hiring/completion and review publication. Then complete accessibility, load, alerts, policy and restore gates.

Administrator bootstrap and its single-account invariant are implemented and tested with synthetic fixtures. **The administrator has not been seeded or verified in the owner's database by this session.**
