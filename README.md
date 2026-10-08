# getLancer V4.6 — Spectral Studio

A proof-of-work marketplace connecting clients with the builders behind working software, with team workspaces and private business hiring.

## Current implementation status

**V4.6 implements reusable frontend contributions:** 24 original MIT examples, private resumable drafts, bounded source ZIP uploads, immutable reviewed versions, free isolated previews through the V4 publisher, and private saved components. Review pins source, setup, licence and publication text; every preview request checks current account, role, profile, release and withdrawal authority. See the [V4.6 implementation](docs/v46/IMPLEMENTATION.md), [verification status](docs/v46/STATUS.md) and [completion gates](docs/v46/COMPLETION_GATES.md). Backend labs remain source-only; live backend execution belongs to later phases.

**V4.0 implements maintenance/support and reviewed static frontend demos:** immutable monthly care agreements, separate recurring billing consent, verified paid-period access, bounded support requests, MFA recovery, built ZIP review and isolated publication. See the [V4 contract](docs/V4_CONTRACT.md), [maintenance operations](docs/V4_OPERATIONS.md) and [hosting operations](docs/V4_HOSTING_OPERATIONS.md). Collection and hosting remain disabled by default. Merchant approval, live provider acceptance and separate publisher/DNS/TLS setup remain deployment requirements. The private cloud Site provides labelled frontend exercises at `/preview/maintenance` and `/preview/hosting`.

The active visual system is [Spectral Studio](docs/SPECTRAL_STUDIO_ACCEPTANCE.md), using the installed shadcn components and current authentication artwork.

| Phase | Implemented scope | Source and evidence |
|---|---|---|
| V1 | Accounts, approved project discovery, publishing, inquiries, client-confirmed outcomes and moderation | `app/workspace`, `backend/src/main/java/com/getlancer/{auth,products,inquiries,moderation}`; [historical V1 verification](docs/V1_VERIFICATION_2026-09-15.md) |
| V1.5 | Trust evidence, availability, link health and earned showcase capacity | `backend/src/main/java/com/getlancer/trust`; [V1.5 implementation](docs/V1_5_IMPLEMENTATION.md) |
| V2 | Teams/studios, membership consent, recruitment, commercial leads and temporary staffing | `app/workspace/teams`, `backend/src/main/java/com/getlancer/teams`, migration `V12__teams.sql`; [V2 contract](docs/V2_CONTRACT.md) |
| V2.5 | Business workspaces, hiring-manager consent, private briefs, shared talent lists, evidence matching and opted-in concierge | `app/workspace/business`, `backend/src/main/java/com/getlancer/business`, migration `V13__business_hiring.sql`; [V2.5 acceptance](docs/V2_5_ACCEPTANCE.md) |
| V3 | Source-authorized proposals, immutable consented agreements, milestone delivery/revision/acceptance, disputes, Razorpay orders and signed provider reconciliation | `app/workspace/delivery`, `backend/src/main/java/com/getlancer/{delivery,payments}`, migrations `V14__delivery.sql` and `V15__payments.sql`; [V3 contract](docs/09_V3_COMMERCIAL_WORKFLOWS.md) |
| V3.5 | Versioned source templates, consented purchases and protected delivery | [V3.5 contract](docs/V3_5_CONTRACT.md) and [acceptance](docs/V3_5_ACCEPTANCE.md) |
| V4.0 | Monthly maintenance, recurring invoice authority, bounded requests, operator recovery and reviewed static hosting | `app/workspace/{maintenance,hosting}`, `backend/src/main/java/com/getlancer/{maintenance,hosting}`, `ops/demo-publisher`, migrations V21–V23; [V4 contract](docs/V4_CONTRACT.md) |
| V4.5 | Independent component/project/template publishing pools, component and college discovery, moderation and admin-priced additional slots | [V4.5 baseline](docs/V4_5_COMPONENTS_AND_COLLEGE.md) |
| V4.6 | Original frontend catalogue, resumable source contributions, immutable releases, controlled free previews and private bookmarks | [Implementation and S02 traceability](docs/v46/IMPLEMENTATION.md); [current verification](docs/v46/STATUS.md) |

Merged V4 baseline: [main CI run 37122827837](https://github.com/JetyChodipilli/getLancer/actions/runs/37122827837) passed on `e61316043a057befa2ba4ac8849bec0787ac92df` with 278 Java tests, 105 Node tests, 89 responsive browser checks and 15 connected browser checks, plus encrypted database restore. This is the pre-audit-fix checkpoint; use the current pull request checks for subsequent changes.

Historical V3 verification: [Main CI run 36756252636](https://github.com/JetyChodipilli/getLancer/actions/runs/36756252636) passed on merged commit `31f027f4e9f3603a78b1fa302bb9009c7d0a6771`: **157 backend tests, 66 frontend tests, 15 preview browser checks and 3 connected browser journeys**, plus real service journeys and encrypted database recovery. Connected browser journeys use the production frontend against disposable Java/PostgreSQL/SMTP/object-storage services at desktop, phone and tablet widths. They do not certify the eventual production providers.

Use the V4 contract and operations guides above for the current implementation, plus [hosted staging preparation](ops/STAGING.md) for deployment requirements. The V1 implementation and verification documents below preserve earlier checkpoints.

### Why some files still say V1

GitHub's folder listing shows the last commit that changed each folder. An unchanged folder can still show `Import getLancer V1 implementation...` while newer phase code exists elsewhere in the tree. These historical commit messages do not identify the current product phase.

`/api/v1` is the stable HTTP contract namespace used by V1 through V4. Flyway's `V1__...` through `V23__...` identify ordered database migrations. Package and JAR versions are separate build coordinates. None of these is a feature switch or a reason to rewrite migration history.

Authentication redesign and Google activation steps: [AUTHENTICATION.md](docs/AUTHENTICATION.md). Login now asks for an administrator authenticator code only after the administrator password is verified. Google sign-in requires its backend credentials; it is unavailable in the disconnected preview.


Historical V1 checkpoints: [implementation status](docs/V1_IMPLEMENTATION.md), [15 September verification](docs/V1_VERIFICATION_2026-09-15.md) and [8 September completeness audit](docs/V1_COMPLETENESS_AUDIT.md). These record earlier evidence and gaps; use the V2.5 acceptance record for the baseline and the V3 contract for commercial workflows. All 17 original document baselines were verified against the repository text.

## Structure

- `app/`: discovery, project details, builder profiles, accounts, builder/team/business/delivery/maintenance/hosting workspaces, client requests and moderation.
- `backend/`: Java 17 / Spring Boot, PostgreSQL / Flyway, transactional email and S3, organized into [feature packages with controller/service boundaries](backend/README.md).
- `lib/`: shared search state, server API configuration, analytics and SEO.
- `tests/`: frontend route/component checks; `tests/browser` and `tests/connected`: preview and connected browser journeys. `backend/src/test/`: rule and database integration tests.
- `docs/`: all 17 source specifications, phase contracts and acceptance records.
- `DESIGN.md` and `docs/SPECTRAL_STUDIO_ACCEPTANCE.md`: the current visual authority; older design records remain historical.

## Local setup

The current local setup uses [Docker PostgreSQL](ops/DOCKER_LOCAL.md), database `getLancer`, with the API, local email and private storage. For cloud flow testing without services, open `/preview/workspace`; the [interactive UAT guide](docs/UAT_DEMO.md) lists supported journeys and simulation boundaries.

```sh
npm ci
npm run setup:docker
npm run services:docker
npm run dev:local
```

Open `http://localhost:3000`. First configure the actual database password and import the private admin authenticator key as described in the guide. Docker starts Spring Boot after PostgreSQL is healthy and the private bucket exists. Startup creates the admin only after a successful connection and migration. The [native PostgreSQL guide](ops/LOCAL_POSTGRESQL.md) remains an alternative.

The same person can browse, commission work and publish showcases without switching accounts.

## Validation commands

Run backend integration checks against the separate disposable test service:

```bash
docker compose --profile test run --rm verify
npx tsc --noEmit
npm test
```

The integration tests clear their configured database. The Compose test profile and CI use `getlancer_test`, separate from the application database. Never point these tests at a staging or production database.

If running Maven directly, configure `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`, and `TEST_DATABASE_RESET=true` for an empty `getlancer_test` database before `mvn -B -f backend/pom.xml verify`. The suite refuses other database names before migrations and never uses the application DB_URL.

The Docker image build packages without database tests; promotion requires the separate CI test job to pass. The full backend suite now passes against PostgreSQL 16 in GitHub Actions. Actual results and limitations are in the current verification report.

## Hosting

The provided private host supports the React frontend through JavaScript Workers. Spring Boot needs its own Java/container host, plus PostgreSQL, S3 and transactional email.

Configure these server-only frontend settings when connecting the backend:

| Variable | Purpose |
|---|---|
| `BACKEND_URL` | HTTPS origin of the Spring API |
| `DEMO_MODE` | `true` permits the frontend-only preview when BACKEND_URL is absent. A configured BACKEND_URL always selects real services, even with this flag still true. `false` requires BACKEND_URL and fails if it is missing. |
| `APP_BASE_URL` | Exact frontend origin; must also match the backend setting |
| `INDEX_PUBLIC_PAGES` | Keep `false` until public-release gates pass |

The browser uses same-origin `/api/v1` requests. Database, email and storage secrets never enter client code. A configured but unavailable API returns an error rather than substituting sample listings.

Without a backend, six fictional projects and labelled workspace previews are available for design review. Account/inquiry actions are unavailable. When BACKEND_URL is configured, all `/preview/*` routes redirect to their real `/workspace/*` counterparts. API failures and empty databases never trigger sample data or demo invitations. Clear BACKEND_URL only when intentionally returning to the frontend-only preview.

The Java runtime has no demo mode, sample users, sample projects, fake sessions or in-memory marketplace adapter. Flyway inserts supported taxonomy values only. Bootstrap creates the configured real administrator with a BCrypt password and TOTP; it does not seed listings or teams. Test fixtures and Mockito are confined to `src/test` and are excluded from the production JAR. `DemoHealth` is the real outbound reachability check for builders' live project URLs.

## Core invariants

- Only approved, active, public projects from approved active builders are discoverable.
- Three active showcases are enforced with a transactional entitlement lock.
- Unconfirmed inquiries never appear as qualified leads.
- Hire/completion require client confirmation; rejection returns to discussion/in-progress.
- Reviews require confirmed completion, are unique per engagement and undergo moderation.
- Material edits require re-review; availability is separate from showcase capacity.
- Private inquiries, emails and NDA-safe content are excluded from public APIs.
- One-time tokens are hashed and expiring; new email links keep the token in a URL fragment.
- Images are checked and re-encoded; external URLs must resolve to public HTTPS destinations.
- Legacy inquiry reports describe work arranged externally. V3 Delivery records consented agreements and supports enabled Razorpay milestone payments. V3.5 adds consented source-template checkout and protected delivery. V4 accepts reviewed built static frontend ZIPs. Paid ranking and server execution of submitted code are outside this implementation.

## Release preparation

Execute all staging, browser, security, performance, email/storage and backup/restore gates in the source QA document. Configure per-client proxy throttling, delivery monitoring and alerts; preserve the previous application image for rollback. Approve operator details, legal policies, retention periods and staffed privacy/appeal handling before onboarding real users.

Account closure requires a new email confirmation. Confirming the 15-minute link hides public content, revokes sessions and creates a retention-review request. Final purge periods and staff processing remain an operational release gate.

## Slate Atelier refinement

The homepage now pairs search with a rotating project spotlight, manual controls and reduced-motion support. Filtered searches collapse the spotlight so results retain priority. Distinct sample interfaces illustrate inventory, bookings, restaurants, CRM, courses and planning; live projects without screenshots show an honest unavailable state. The workspace uses a blue welcome panel, actionable outcome summaries and a vertical tab navigation. My requests remains available within the same account.

The navigation stays visible in a compact frosted-glass header. Once the hero search scrolls out of view, synchronized search and filter controls appear beside the navigation. The filter panel supports business category, technology, project type, availability, live demo and sorting, with an active-filter count and clear action. Small-screen layouts use a navigation menu and a second search row. The exact supplied logo remains intact at a smaller size.

The supplied administrator password was placed in ignored local backend configuration only. It is not part of the source or the private Site build. Backend hosting must receive its own bootstrap secret and authenticator configuration before real admin sign-in works.

## V1 repair checkpoint
See [historical V1 verification](docs/V1_VERIFICATION_2026-09-15.md) for the audit fixes and checks recorded at that checkpoint. Current delivered scope and remaining provider gates are in [V2.5 acceptance](docs/V2_5_ACCEPTANCE.md).

## V2 teams and studios

- `/teams`: public studio discovery, shared products, attributed member reviews, open roles and project interest.
- `/workspace/teams`: authenticated memberships, invitations, recruitment, shared leads and temporary staffing.
- `/preview/teams`: isolated sample workspace with role switching and reset; no live account, email or database writes.
- Flyway `V12__teams.sql` creates the team tables when the connected API starts. Existing V1 rules remain enforced.
- Only owner/business manager can manage commercial leads. Recruitment and staffing have separate roles. Applicants and invitees must consent before joining.
- Team lead acknowledgment records an externally arranged outcome. Use V3 Delivery for a recorded milestone agreement and enabled Razorpay checkout.

Start the connected stack with the existing Docker instructions. OAuth is optional and requires provider credentials. A frontend-only preview does not host the Java API or PostgreSQL.

## V2.5 business hiring

`/workspace/business` uses the Java API for business ownership, consent-based hiring-manager invitations, private project requests, shared talent lists and shortlists. Matching reads current approved public builder/team evidence from PostgreSQL. Concierge sourcing requires an explicit request and the existing MFA administrator; it never contacts or hires candidates automatically.

`/preview/business` is a frontend-only in-memory demonstration. Configuring `BACKEND_URL` redirects it to the real workspace and disables every sample fallback, including when Java is unavailable or has no records. Flyway `V13__business_hiring.sql` creates private business tables; it seeds no business or talent records.

See the [V2.5 contract](docs/V2_5_CONTRACT.md) and [acceptance and review record](docs/V2_5_ACCEPTANCE.md). The CI workflow verifies Java/PostgreSQL, connected Docker service journeys, recovery and Chromium layouts at desktop, phone and tablet widths. Backend production deployment remains outside this frontend-only cloud release.


## Razorpay activation

One gateway offers multiple methods through Standard Checkout; the Java service creates every order from the accepted milestone amount. Checkout loads on an explicit Pay action. Test captures are labelled and cannot complete real commercial agreements. Payment capture, Route transfer and bank settlement are recorded separately. Refunds and transfer reversals are performed in Razorpay and reconciled into the append-only ledger; recording an application dispute does not move money.

Set `PAYMENTS_ENABLED=false` until commercial policies, Route and seller KYC are approved. Use separate disposable data for test keys. Configure the key ID, key secret, webhook secret and mode only on Java hosting, configure auto-capture in Razorpay, and point the provider webhook directly to `/api/v1/payments/razorpay/webhook` on the public Java API origin. Map activated seller linked accounts using MFA-protected operator controls. No provider secret or bank/PAN data is sent to the frontend. Unknown order creation is reconciled against the original receipt before another order can be attempted.

### V4 static frontend demos

Use `/workspace/hosting` for built static ZIP upload, operator review and isolated publication; `/preview/hosting` is a labelled local exercise. Hosting is disabled by default and requires a separately deployed publisher. See [V4 hosting operations](docs/V4_HOSTING_OPERATIONS.md) for origin isolation, private ingress, caps, recovery and setup. V4 also includes the maintenance/support workflow in [V4 operations](docs/V4_OPERATIONS.md).
