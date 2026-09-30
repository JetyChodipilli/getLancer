# Backend structure

The API is a Java 17 / Spring Boot modular monolith with PostgreSQL and Flyway. `com.getlancer.Application` is the only class in the root package; Spring scans the feature packages beneath it.

| Package | Responsibility |
|---|---|
| `accounts` | Account export, notifications read state and privacy operations |
| `admin` | Protected administrative review, taxonomy, account and inquiry actions |
| `analytics` | Event intake, context validation and reporting |
| `auth` | Signup/login, verification/reset, MFA, sessions and OAuth providers |
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

Run `mvn -B -f backend/pom.xml verify` with the guarded disposable `getlancer_test` configuration described in the root README. BackendArchitectureTest checks feature placement, controller/service boundaries and the preserved 121-route HTTP contract. Flyway migrations and request/response schemas are unchanged by this structural refactor.

The cloud Site publishes the demo frontend with `DEMO_MODE=true`. The real frontend, this Java service, migrations, tests and container manifests are maintained together in GitHub. Set `DEMO_MODE=false` and configure BACKEND_URL for a connected frontend deployment.
