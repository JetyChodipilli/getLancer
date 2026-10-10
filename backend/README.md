# Backend structure

The API is a Java 17 / Spring Boot modular monolith with PostgreSQL and Flyway. `com.getlancer.Application` is the only class in the root package; Spring scans the feature packages beneath it.

| Package | Responsibility |
|---|---|
| `accounts` | Account export, notifications read state and privacy operations |
| `admin` | Protected administrative review, taxonomy, account and inquiry actions |
| `analytics` | Event intake, context validation and reporting |
| `auth` | Signup/login, verification/reset, MFA, sessions and OAuth providers |
| `business` | Business membership, private briefs, talent lists, evidence matching and opted-in concierge sourcing |
| `config` | Environment validation and sole-administrator bootstrap |
| `inquiries` | Builder/client requests, shared outcomes, reviews and confirmation contracts |
| `jobs` | Scheduled expiry and retention tasks |
| `maintenance` | Consented recurring care, financial reconciliation, support quotas and MFA recovery |
| `hosting` | Built static ZIP consent/review, fixed deployment identities, withdrawal and gateway authority |
| `components` | Private source drafts, bounded MIT ZIPs, immutable reviews/releases, free controlled previews, bookmarks and educational context |
| `publishing` | Independent project/template/component capacity and retained paid-slot authority |
| `commerce` | Source-template releases, purchases and protected delivery |
| `delivery` | Consented agreements, milestones and dispute workflows |
| `payments` | Provider verification, financial reservations, refunds, disputes, transfers and ledgers |
| `media` | Proof uploads, image sanitization, storage and access |
| `moderation` | Report triage, decisions, appeals and mail recovery |
| `notifications` | Transactional mail and notification delivery |
| `products` | Discovery, showcase lifecycle, capacity, private access and product data reads |
| `profiles` | Builder profile validation, updates and submission |
| `security` | Request filtering, authorization, rate limits and cookie reads |
| `shared` | API errors, common validation and pagination |
| `teams` | Team membership, consent, recruitment, CRM, staffing and team data reads |
| `trust` | Availability, repository evidence, demo health and earned capacity |

HTTP controllers own route/parameter/status annotations and delegate to application services. Services enforce permissions, validation and lifecycle rules, and own transaction boundaries. ProductRepository and TeamRepository own the shared product/media and team/member reads. Existing feature-specific JDBC writes remain within transactional services; repositories can be extended as those persistence operations evolve.

Controllers never inject another controller or JdbcTemplate. Cross-feature calls use explicit service or policy methods. Tests follow their owning feature; PostgreSQL scenarios live in `integration`, database reset guards in `testing`, and boundary/route checks in `architecture`.

Run `mvn -B -f backend/pom.xml verify` with the guarded disposable `getlancer_test` configuration described in the root README. BackendArchitectureTest checks feature placement, controller/service boundaries and the published HTTP route contract in `src/test/resources/api-route-contract.txt`, including maintenance and hosting routes. New operator recovery actions use the existing action route; historical route counts are not the current inventory.

The cloud Site publishes the demo frontend with `DEMO_MODE=true` while BACKEND_URL is absent. The real frontend, this Java service, migrations, tests and container manifests are maintained together in GitHub. Configuring BACKEND_URL always selects the real frontend and disables the sample workspace routes; use `DEMO_MODE=false` to also reject a missing backend URL.

Java has no demo mode or fake data adapter. Its runtime reads and writes PostgreSQL, delivers mail through SMTP and stores proof files through S3. Test fixtures and mocks live only under `src/test` and test dependencies do not enter the production JAR. Fresh deployments contain taxonomy values and the configured administrator only, with no fabricated users, products, teams or reviews. `DemoHealth` monitors actual builders' live project URLs.


V3 adds `delivery` (source-authorized agreements and milestones) and `payments` (Razorpay orders, webhook verification, ledger and reconciliation). [The commercial workflow contract](../docs/09_V3_COMMERCIAL_WORKFLOWS.md) defines their shared repository boundary and activation requirements. INR amounts use integer paise. Payment keys and linked seller accounts are never seeded. Backend origin exposes the exact signed webhook route separately from same-origin browser mutations.

V4 includes maintenance/support and reviewed static hosting. V21 creates retained care/financial records, V22 creates hosting records, and V23 retains known refund references. Collection and publisher activation remain separate, disabled deployment choices. See [maintenance operations](../docs/V4_OPERATIONS.md) and [hosting operations](../docs/V4_HOSTING_OPERATIONS.md).

V4.6 adds V29/V30 for pinned submission source and context, retained releases, private bookmarks, uploaded drafts and immutable preview identities. The existing V4 publisher serves reviewed component bytes with an opaque sandbox and no network access. See [V4.6 operations and traceability](../docs/v46/IMPLEMENTATION.md).

## V4.9 education domain

`com.getlancer.education` owns bounded draft/release schemas, exact source/hash review, private free packages, public discovery and current contribution/component/demo projections. Submitted terms are immutable; new content requires a new release. `CommerceEducationBinding` connects an approved paid release to the existing source-purchase ledger; `APP_EDUCATION_PAID_ENABLED=false` keeps live college checkout unavailable pending merchant readiness. Migrations V32–V34 are additive. Run the reviewed database permission provisioning after migration. See [V4.9 contract](../docs/v49/CONTRACT.md) and [validation](../docs/v49/VALIDATION.md).
