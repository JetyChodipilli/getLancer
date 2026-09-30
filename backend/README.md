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

Run `mvn -B -f backend/pom.xml verify` with the guarded disposable `getlancer_test` configuration described in the root README. BackendArchitectureTest checks feature placement, controller/service boundaries and the 145-route HTTP contract (121 existing routes plus 24 V2.5 routes). V13 adds private business hiring tables without changing existing V1–V2 routes.

The cloud Site publishes the demo frontend with `DEMO_MODE=true` while BACKEND_URL is absent. The real frontend, this Java service, migrations, tests and container manifests are maintained together in GitHub. Configuring BACKEND_URL always selects the real frontend and disables the sample workspace routes; use `DEMO_MODE=false` to also reject a missing backend URL.

Java has no demo mode or fake data adapter. Its runtime reads and writes PostgreSQL, delivers mail through SMTP and stores proof files through S3. Test fixtures and mocks live only under `src/test` and test dependencies do not enter the production JAR. Fresh deployments contain taxonomy values and the configured administrator only, with no fabricated users, products, teams or reviews. `DemoHealth` monitors actual builders' live project URLs.
