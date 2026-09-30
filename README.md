# getLancer V1 — Slate Atelier

Authentication redesign and Google activation steps: [AUTHENTICATION.md](docs/AUTHENTICATION.md). Login now asks for an administrator authenticator code only after the administrator password is verified. Google sign-in requires its backend credentials; it is unavailable in the disconnected preview.


A proof-of-work marketplace connecting clients with the builders behind working software.

Read [the phased implementation status](docs/V1_IMPLEMENTATION.md) for the document analysis, code delivered, actual validation evidence and remaining release gates.

Current implementation and verification: [15 September V1 verification](docs/V1_VERIFICATION_2026-09-15.md). **106 backend tests (38 PostgreSQL scenarios) and 19 frontend checks pass.** The core code is present; connected cloud operation and public-launch acceptance still require the backend/provider setup and staging gates in that report.

The historical [8 September completeness audit](docs/V1_COMPLETENESS_AUDIT.md) records the original gaps. Use the current verification report for repaired defects and remaining work. All 17 original document baselines were verified against the repository text.

## Structure

- `app/`: discovery, project details, builder profiles, accounts, workspace, client requests and moderation.
- `backend/`: Java 17 / Spring Boot, PostgreSQL / Flyway, transactional email and S3, organized into [feature packages with controller/service boundaries](backend/README.md).
- `lib/`: shared search state, server API configuration, analytics and SEO.
- `tests/`: frontend route/component checks. `backend/src/test/`: rule and database integration tests.
- `docs/`: all 17 source specifications and implementation status.
- `design-system/SLATE_ATELIER.md`: the selected visual direction, overriding generic skill recommendations.

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

Without a backend, six fictional projects and labelled workspace previews are available for design review. Account/inquiry actions are unavailable. When BACKEND_URL is configured, all three `/preview/*` routes redirect to their real `/workspace/*` counterparts. API failures and empty databases never trigger sample data or demo invitations. Clear BACKEND_URL only when intentionally returning to the frontend-only preview.

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
- Contracts and payments remain external. There is no template checkout, paid ranking or arbitrary developer-code execution.

## Release preparation

Execute all staging, browser, security, performance, email/storage and backup/restore gates in the source QA document. Configure per-client proxy throttling, delivery monitoring and alerts; preserve the previous application image for rollback. Approve operator details, legal policies, retention periods and staffed privacy/appeal handling before onboarding real users.

Account closure requires a new email confirmation. Confirming the 15-minute link hides public content, revokes sessions and creates a retention-review request. Final purge periods and staff processing remain an operational release gate.

## Slate Atelier refinement

The homepage now pairs search with a rotating project spotlight, manual controls and reduced-motion support. Filtered searches collapse the spotlight so results retain priority. Distinct sample interfaces illustrate inventory, bookings, restaurants, CRM, courses and planning; live projects without screenshots show an honest unavailable state. The workspace uses a blue welcome panel, actionable outcome summaries and a vertical tab navigation. My requests remains available within the same account.

The navigation stays visible in a compact frosted-glass header. Once the hero search scrolls out of view, synchronized search and filter controls appear beside the navigation. The filter panel supports business category, technology, project type, availability, live demo and sorting, with an active-filter count and clear action. Small-screen layouts use a navigation menu and a second search row. The exact supplied logo remains intact at a smaller size.

The supplied administrator password was placed in ignored local backend configuration only. It is not part of the source or the private Site build. Backend hosting must receive its own bootstrap secret and authenticator configuration before real admin sign-in works.

## V1 repair checkpoint
See [current V1 verification](docs/V1_VERIFICATION_2026-09-15.md) for implemented audit fixes, executed checks and remaining release blockers. This checkpoint is not a public-launch certification.

## V2 teams and studios

- `/teams`: public studio discovery, shared products, attributed member reviews, open roles and project interest.
- `/workspace/teams`: authenticated memberships, invitations, recruitment, shared leads and temporary staffing.
- `/preview/teams`: isolated sample workspace with role switching and reset; no live account, email or database writes.
- Flyway `V12__teams.sql` creates the team tables when the connected API starts. Existing V1 rules remain enforced.
- Only owner/business manager can manage commercial leads. Recruitment and staffing have separate roles. Applicants and invitees must consent before joining.
- Client proposal acknowledgment records a reported outcome; terms, payment and contracts remain external.

Start the connected stack with the existing Docker instructions. OAuth is optional and requires provider credentials. A frontend-only preview does not host the Java API or PostgreSQL.

## V2.5 business hiring

`/workspace/business` uses the Java API for business ownership, consent-based hiring-manager invitations, private project requests, shared talent lists and shortlists. Matching reads current approved public builder/team evidence from PostgreSQL. Concierge sourcing requires an explicit request and the existing MFA administrator; it never contacts or hires candidates automatically.

`/preview/business` is a frontend-only in-memory demonstration. Configuring `BACKEND_URL` redirects it to the real workspace and disables every sample fallback, including when Java is unavailable or has no records. Flyway `V13__business_hiring.sql` creates private business tables; it seeds no business or talent records.

See the [V2.5 contract](docs/V2_5_CONTRACT.md) and [acceptance and review record](docs/V2_5_ACCEPTANCE.md). The CI workflow verifies Java/PostgreSQL, connected Docker service journeys, recovery and Chromium layouts at desktop, phone and tablet widths. Backend production deployment remains outside this frontend-only cloud release.
