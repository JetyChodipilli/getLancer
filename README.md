# getLancer V1 — Slate Atelier

Authentication redesign and Google activation steps: [AUTHENTICATION.md](docs/AUTHENTICATION.md). Login now asks for an administrator authenticator code only after the administrator password is verified. Google sign-in requires its backend credentials; it is unavailable in the disconnected preview.


A proof-of-work marketplace connecting clients with the builders behind working software.

Read [the phased implementation status](docs/V1_IMPLEMENTATION.md) for the document analysis, code delivered, actual validation evidence and remaining release gates.

Current implementation and verification: [V1 release checkpoint](docs/V1_RELEASE_CHECKPOINT.md).

The historical [8 September completeness audit](docs/V1_COMPLETENESS_AUDIT.md) compares all 17 requirements documents with the code. **V1 is partial and not ready for public launch**; it identifies missing functionality and privacy/enforcement fixes as well as outstanding integration and operational work.

## Structure

- `app/`: discovery, project details, builder profiles, accounts, workspace, client requests and moderation.
- `backend/`: Java 17 / Spring Boot, PostgreSQL / Flyway, transactional email and S3.
- `lib/`: shared search state, server API configuration, analytics and SEO.
- `tests/`: frontend route/component checks. `backend/src/test/`: rule and database integration tests.
- `docs/`: all 17 source specifications and implementation status.
- `design-system/SLATE_ATELIER.md`: the selected visual direction, overriding generic skill recommendations.

## Local setup

Use your existing local PostgreSQL database `getLancer`. The complete [local PostgreSQL guide](ops/LOCAL_POSTGRESQL.md) covers Windows/macOS/Linux startup, environment values, local email/storage, and administrator MFA.

```sh
npm ci
npm run setup:local
npm run services:local
npm run doctor:local
npm run backend:local
```

Keep the backend running and use `npm run dev:local` in a second terminal. Open `http://localhost:3000`. First configure the actual database password and import the private admin authenticator key as described in the guide. Startup creates the admin in the database only after a successful connection and migration.

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

The Docker image build packages without database tests; promotion requires the separate CI test job to pass. Full backend tests were not executed in the authoring workspace. Actual results and limitations are in the implementation status.

## Hosting

The provided private host supports the React frontend through JavaScript Workers. Spring Boot needs its own Java/container host, plus PostgreSQL, S3 and transactional email.

Configure these server-only frontend settings when connecting the backend:

| Variable | Purpose |
|---|---|
| `BACKEND_URL` | HTTPS origin of the Spring API |
| `APP_BASE_URL` | Exact frontend origin; must also match the backend setting |
| `INDEX_PUBLIC_PAGES` | Keep `false` until public-release gates pass |

The browser uses same-origin `/api/v1` requests. Database, email and storage secrets never enter client code. A configured but unavailable API returns an error rather than substituting sample listings.

Without a backend, six fictional projects and a labelled workspace preview are available for design review. Account/inquiry actions are unavailable. The fictional workspace route is disabled when a backend is configured.

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

Account closure hides public content immediately and creates a retention-review request. Final purge periods and staff processing remain an operational release gate.

## Slate Atelier refinement

The homepage now pairs search with a rotating project spotlight, manual controls and reduced-motion support. Filtered searches collapse the spotlight so results retain priority. Distinct sample interfaces illustrate inventory, bookings, restaurants, CRM, courses and planning; live projects without screenshots show an honest unavailable state. The workspace uses a blue welcome panel, actionable outcome summaries and a vertical tab navigation. My requests remains available within the same account.

The navigation stays visible in a compact frosted-glass header. Once the hero search scrolls out of view, synchronized search and filter controls appear beside the navigation. The filter panel supports business category, technology, project type, availability, live demo and sorting, with an active-filter count and clear action. Small-screen layouts use a navigation menu and a second search row. The exact supplied logo remains intact at a smaller size.

The supplied administrator password was placed in ignored local backend configuration only. It is not part of the source or the private Site build. Backend hosting must receive its own bootstrap secret and authenticator configuration before real admin sign-in works.

## V1 repair checkpoint
See [V1 fixes and release gates](docs/V1_FIXES_AND_RELEASE_GATES.md) for implemented audit fixes, verification and remaining release blockers. This checkpoint is not a public-launch certification.
