# getLancer working context

Updated 21 September 2026. Core status: [V1 verification](docs/V1_VERIFICATION_2026-09-15.md). The later [interactive UAT demo](docs/UAT_DEMO.md) and [Docker setup](ops/DOCKER_LOCAL.md) supplement that baseline. Earlier dated reports are historical.

- Preserve the existing repository history. Deliver changes through the repository without an external ZIP.
- Stack: React/TypeScript with Vinext for the frontend; Java 17/Spring Boot, PostgreSQL and Flyway for the API; S3 proof storage and transactional email. Do not substitute Supabase or Neon: the user now selected Docker PostgreSQL, database getLancer, exposed locally on port 5433.
- Private environment files are ignored. Never commit, display or embed passwords, MFA keys or service credentials in the frontend. Administrator bootstrap is idempotent and protects one admin account; production seeding has not been verified.
- The private frontend is separate from the Java API. No hosted `BACKEND_URL` is configured. The user explicitly authorized an isolated interactive demo at `/preview/workspace` for UAT. Sample people, records and actions live only in browser session storage and are labeled simulations. Never make real login or API routes silently fall back to demo success. OAuth and real service setup remain deferred.
- The source baseline is all 17 numbered requirements documents in `docs/`; provenance is recorded in `SOURCE_DOCUMENT_VERIFICATION.json`. V1 is project discovery and client-confirmed work, with three active builder showcases. One account may act as both builder and client.
- Preserve the exact transparent logo, Slate Atelier navigation/discovery and Icy Wind auth design. The user's later request removed the motion button; retain system reduced-motion support and automatic art behavior. Login/signup are real routes. Admin MFA appears only after valid administrator password verification.
- Source code passes 106 backend tests (38 database scenarios), 19 frontend checks, TypeScript and production build. CI run 34981803941 verifies the functional patch; see the current report for scope and browser limitations.
- Tests reset only the explicitly authorized disposable `getlancer_test` database through TEST_DB variables and the guard. Never use application DB_URL for tests.
- Remaining release work is connected hosting/provider configuration and the documented staging/browser/operational acceptance gates. Do not claim full public-launch readiness from a passing build.
- Teams/recruitment, platform payments, source-code commerce and hosted demos belong to later roadmap versions. Do not add them while closing V1 defects.
