# V2.5 acceptance and review record

Release scope: business workspaces, hiring-manager consent, private project requests, shared talent lists, current-evidence matching and manual concierge sourcing. The Java API is real PostgreSQL-backed application code. The cloud release is exclusively the frontend preview. V2.5 is merged through [PR #4](https://github.com/JetyChodipilli/getLancer/pull/4); the connected acceptance follow-up is [PR #5](https://github.com/JetyChodipilli/getLancer/pull/5).

## Acceptance contract

| Gate | Evidence maintained in the repository |
|---|---|
| Real Java/PostgreSQL | Maven verify includes 157 tests against guarded disposable PostgreSQL; eight business integration scenarios exercise actual HTTP controllers and persistence. |
| V2 gaps | All non-owner team roles are checked for commercial, recruitment, staffing and owner-only permissions. Server-provided creation eligibility accepts archived approved work and rejects suspended proof. Forms enforce contract limits, identify field errors and focus the invalid field or error summary. Account exports include the user's trust, capacity, staffing and team/business records, excluding verification challenges and private team CRM notes. |
| Consent and isolation | Invitations grant no access before recipient acceptance; other recipients, expired invitations, removed members, suspended and unverified accounts are rejected. Other businesses cannot read briefs, saved lists or shortlists. |
| Matching | Approved active public proof, approved verified active builders, active consented team membership, matching category/technology and optional availability/repository filters. Reads immediately reflect visibility, approval, expiry and repository URL changes. |
| Concierge | An opted-in open brief, MFA administrator, explicit sourcing transition and eligible recommendation with a visible reason are required. Already-shortlisted candidates can receive concierge reasons; withdrawn proof cannot fulfil sourcing. No inquiry or hiring event is fabricated. |
| Frontend separation | Node tests check all sample routes redirect when connected, real empty/error responses never show samples, and the business preview never uses network requests. Docker verifies the built production frontend against real Java before and after service journeys. |
| Connected services | Docker smoke covers SMTP verification, approved profiles/proof, V1–V1.5 workflows, team consent/permissions/leads, business manager acceptance, private requests, real builder/team matches, lists, concierge, withdrawn consent, removed access and closed requests. Encrypted database restore runs separately. |
| Responsive UI | Chromium acceptance covers 1440×1000, 375×812 and 768×1024: all four workspace previews, new brief/open/close, invitation consent, concierge and Escape/focus restoration. Screenshots and failure traces are attached to CI artifacts. |
| Runtime artifact | CI checks the production Java JAR excludes JUnit, Mockito, test fixtures and frontend demo modules. V13 contains no sample data seeds. |

Run `npx tsc --noEmit`, `npm run build`, `node --experimental-strip-types --test tests/*.test.mjs`, `npx playwright install --with-deps chromium` and `npx playwright test`. Backend acceptance uses `mvn -B -f backend/pom.xml verify` and the test database guard described in README. Docker service acceptance is CI-only and requires a disposable `getlancer-ci-*` project; do not run its reset/restore operations against application data.

## gstack correctness review

Applied the Work Mode engineering/review and QA checklists: authorization, SQL parameter binding, row-lock ordering, request lifecycle, current proof, data exposure, CSRF/session reuse, migrations, artifact contents and frontend connection boundaries. Controllers are thin; application services own transactions within the feature package. Business mutations and concierge transitions lock the business row before dependent records, so membership revocation and closing a request serialize with those operations. Dynamic table/column names in matching are internal fixed call-site constants; user values are JDBC parameters.

Issues corrected during acceptance: Spring repository proxy fields were replaced with invoked accessors; withdrawal tombstones preserve removal without exposing proof; concierge upserts can replace a prior business selection with a reason; fulfilment rechecks current eligibility; test fixtures use valid private visibility; the preview actor has an explicit accessible name; mobile navigation overrides cover both discovery states after selector minification. Browser overflow checks compare against the device viewport and do not accept an expanded layout viewport as evidence of reflow.

This is a native checklist review, not an independent cross-model or external security certification.

## Ponytail review — changed code

Lean already. Ship.

Shared labelled form controls replace the duplicated team form, matching serves the two requested saved-entry types, and the only added package is a development browser test runner. No runtime mock API or speculative matching engine was added.

## Ponytail audit — whole tree, report only

1. `delete:` 51 starter UI modules are unreachable from application/lib/non-UI component imports, totalling 6,088 source lines. Keep the ten reachable primitives and delete unused modules after checking any intended starter-library use. [components/ui]

net: -6088 lines, -0 deps possible.

The audit also checked Java interfaces/factories, wrappers, frontend package usage and preview/config boundaries. No speculative Java abstraction was identified. Dependency removals need a separate dependency/import pass; none is claimed here. The unused starter modules are pre-existing and are not removed by this report-only audit.

## Connected acceptance follow-up — 30 September 2026

The follow-up adds a second browser suite using the production frontend server connected to disposable Java/PostgreSQL. Accounts are verified through real SMTP in the existing service smoke; its separate browser businesses and invitations are created through HTTP. Ignored, mode-0600 synthetic credentials stay within that CI job. The suite uses real login and administrator MFA, then exercises business consent, owner-only controls, a failed category/technology validation with focus recovery, persisted brief creation, builder/team matching, saved lists, shortlisting, opt-in concierge, membership revocation and closed requests. It checks Java permission responses through the frontend proxy, responsive width, page errors and records screenshots/traces. Workspace and talent-list selectors have explicit accessible names that remain stable as their options change. No new dependency is added. CI transfers the complete production build, including browser assets. No browser network interception or mock backend is used. [Connected CI run 36752780563](https://github.com/JetyChodipilli/getLancer/actions/runs/36752780563) passed on head `7d964402092a0a288c7a06376901dca12d69b1f1`: 157 Java tests, 66 frontend tests, 15 preview browser tests and all three real connected journeys. Actual service journeys, database outage recovery and encrypted restore also passed. Browser cleanup preserves the original assertion failure if a timed-out context is already closed.

`scripts/verify-staging.mjs` adds read-only HTTPS health, proxy, anonymous-authentication and preview-redirect checks with optional private Site access restricted to the frontend origin for a later configured host. It reports a bounded catalog-latency sample without declaring production capacity. The production-provider gate remains unmet until the owner supplies a deployment target and provider configuration. The [private frontend preview](https://getlancer-v1.jety124050.chatgpt.site) was updated successfully on 30 September 2026 with the explicit selector names. It remains frontend-only, with its owner-only audience preserved. GATES.md records six verified code/review/release gates and one provider handoff; full production acceptance is not complete.

## Production limits

CI service acceptance uses disposable real Java/PostgreSQL/SMTP/object-storage services. It does not establish acceptance of the user's eventual production providers or deployment environment. Java/PostgreSQL deployment, real SMTP/object-storage credentials, optional OAuth, operational monitoring, load testing and public-launch acceptance remain pending. No Java backend is deployed to the cloud Site. Mobile checks use Chromium emulation; physical devices and a full screen-reader/WCAG audit remain separate.
