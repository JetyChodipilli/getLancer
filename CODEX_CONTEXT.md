# getLancer working context

Updated 22 September 2026. Core status: [V1 verification](docs/V1_VERIFICATION_2026-09-15.md). The later [interactive UAT demo](docs/UAT_DEMO.md) and [Docker setup](ops/DOCKER_LOCAL.md) supplement that baseline. [Service/release follow-up](docs/V1_SERVICE_VERIFICATION_2026-09-22.md) records the latest work. Earlier dated reports are historical.

- Preserve the existing repository history. Deliver changes through the repository without an external ZIP.
- Stack: React/TypeScript with Vinext for the frontend; Java 17/Spring Boot, PostgreSQL and Flyway for the API; S3 proof storage and transactional email. Do not substitute Supabase or Neon: the user now selected Docker PostgreSQL, database getLancer, exposed locally on port 5433.
- Private environment files are ignored. Never commit, display or embed passwords, MFA keys or service credentials in the frontend. Administrator bootstrap is idempotent and protects one admin account; production seeding has not been verified.
- The private frontend is separate from the Java API. No hosted `BACKEND_URL` is configured. The user explicitly authorized an isolated interactive demo at `/preview/workspace` for UAT. Sample people, records and actions live only in browser session storage and are labeled simulations. Never make real login or API routes silently fall back to demo success. OAuth and real service setup remain deferred.
- The source baseline is all 17 numbered requirements documents in `docs/`; provenance is recorded in `SOURCE_DOCUMENT_VERIFICATION.json`. V1 is project discovery and client-confirmed work, with three active builder showcases. One account may act as both builder and client.
- Preserve the exact transparent logo, Slate Atelier navigation/discovery and Icy Wind auth design. The user's later request removed the motion button; retain system reduced-motion support and automatic art behavior. Login/signup are real routes. Admin MFA appears only after valid administrator password verification.
- Source code passes 107 backend tests (38 database scenarios), 25 frontend checks, TypeScript and production build. CI run 35689544744 also passes connected Docker service journeys, database outage recovery and a synthetic encrypted restore. The current service report distinguishes this HTTP acceptance from browser/provider certification.
- JUnit resets only the explicitly authorized disposable `getlancer_test` database through TEST_DB variables and the guard. Docker acceptance uses a separate guarded `getlancer-ci-<run_id>` Compose project with synthetic credentials, and cleans only its own containers, volumes and restore target. Never point tests at an owner or shared database.
- Remaining release work is connected hosting/provider configuration and the documented staging/browser/operational acceptance gates. Do not claim full public-launch readiness from a passing build.
- Teams/recruitment, platform payments, source-code commerce and hosted demos belong to later roadmap versions. Do not add them while closing V1 defects.

## UI refinement — 2026-09-23
- Selected Awesome Design MD's Linear reference and documented getLancer's deliberate light/blue adaptation in DESIGN.md.
- Shared account menu with avatar identity, role, workspace/profile actions, and explicit demo-only account switching.
- Workspace navigation is horizontal and sticky above the filters. Four columns at desktop; two at tablet and one on narrow phones. Active showcase entitlement remains three.
- Search stays mounted and crossfades in a reserved header cell, with inert hidden controls and reduced-motion support. Filtering no longer collapses the hero.
- Shared responsive footer uses real project, workspace, guidance, and policy routes.
- Demo account switching and sticky navigation manually checked in the managed browser; dock search returned the expected Inventory result. No hosted backend/OAuth activation is implied by this visual release.

## Quiet Craft refinement — 2026-09-23
- Latest user-directed theme replaces Linear as primary: Cursor Quiet Craft atmosphere; Airbnb marketplace search; Figma Community/Canva-inspired hero framing. DESIGN.md contains references and adaptations. Exact logo, blue actions and Icy Wind auth artwork retained.
- Native sticky project stack replaces the rotating homepage spotlight, with progressive scroll-timeline animation, mobile snap strip, reduced-motion fallback and a direct collection anchor. These are original illustrative getLancer demos; no inaccessible Figma Community asset is claimed as imported.
- Shared BrowseToolbar supplies staged filter dialog/mobile sheet, removable chips, sorting, accessible counts and recovery actions. Four-column preview cards have restrained metadata. Demo account switching lives in its own submenu; real account settings and profile have separate actions. Pending inquiry/draft next steps appear before metrics.
- Browser checks passed desktop stacking, four-column layout, filter Apply/removal, no-match recovery, sticky tabs above search and keyboard account switching. Browser preview uses HTTP where Vinext Web Crypto navigation is unavailable; public filtering falls back to anchored navigation. Hosted HTTPS uses client transitions. Mobile CSS and reduced-motion implementation inspected; no mobile browser certification claimed.
- Frontend-only refinement. Hosted backend, OAuth and launch gates remain as above.
