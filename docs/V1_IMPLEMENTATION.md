> Historical checkpoint. Current status: [15 September V1 verification](V1_VERIFICATION_2026-09-15.md). PostgreSQL is the selected database; earlier Supabase references and unexecuted-test counts below are superseded.

# V1 implementation and release status

Current follow-up: [9 September release checkpoint](V1_RELEASE_CHECKPOINT.md) documents the latest code changes, executed checks and credential-dependent launch gates.

Updated 7 September 2026. This is an implementation checkpoint, not a public-launch approval.

**8 September audit:** See [V1_COMPLETENESS_AUDIT.md](V1_COMPLETENESS_AUDIT.md) for the current assessment against all 17 resupplied documents. V1 is partially implemented, with missing functionality and source-confirmed privacy/enforcement defects in addition to unexecuted integration and deployment gates. Earlier “code implemented” labels below describe delivered scaffolding and paths, not full acceptance of each phase.

## Source analysis

All 17 supplied specification documents are retained here, from `00_MASTER_PRD.txt` through `16_ENVIRONMENT_CONFIG.txt`. Document 04 defines the state transitions and entitlement rules; documents 10 and 13 define release gates. The selected visual direction is Slate Atelier.

V1's loop is: approved software showcase → email-confirmed inquiry → developer follow-up → client-confirmed hire → client-confirmed completion → eligible review. A person can act as both builder and client through one account.

## Phases

| Phase | Code delivered | Status |
|---|---|---|
| 1 — Foundation | React/TypeScript; blue glass surfaces; responsive navigation; secure-cookie sessions; verification/reset; dual roles; administrator TOTP; data export/deletion request | Code implemented; authentication integration remains a staging gate |
| 2 — Discovery | Search; category, technology, project type, availability and live-demo filters; URL state; server pagination; details/proof; profiles/reviews; saves | Rendered-route checks cover search, filters, empty results, details and profiles |
| 3 — Builder workspace | Profile submission; draft/edit; proof upload; review submission; active/archive views; three active slots; atomic capacity check; material edits require review | Isolated slot rules passed; PostgreSQL concurrency test awaits execution |
| 4 — Inquiries | Durable guest inquiries; duplicate prevention; email qualification; private inbox/history; developer stages; client My requests; accept/reject hire/completion; renewal | Isolated transition checks passed; complete API integration awaits execution |
| 5 — Trust/admin | Profile/project approval; reasons/audit; report inspection and restriction; review eligibility/moderation; notifications; aggregate analytics | Code implemented; moderation/email/storage integration remains a staging gate |
| 6 — Launch readiness | Docker; migrations; CI; environment example; security headers; noindex controls; SEO routes; setup/runbooks | Private visual preview is supported; production launch is not validated |

## Private preview boundary

Explore, search/filter navigation, details, profiles and the explicitly labelled workspace design preview are available. The catalogue has six fictional projects. The workspace illustrates both builder inquiries and client requests.

Account creation, authentication, inquiries, moderation and storage use the actual Spring API. Without `BACKEND_URL`, these actions return an unavailable response and never pretend to persist data. The fictional workspace route is disabled when the backend is connected.

## Implementation decisions

- The frontend uses Vinext's Next.js-compatible React/TypeScript routing to run in the provided JavaScript Worker host. The Java 17 Spring backend is a separate service; that host cannot execute it.
- Spring JDBC and explicit transactions replace the PRD's recommended JPA. Flyway owns migrations. Category/technology mapping tables coexist with searchable denormalized text.
- Authentication uses email/password, BCrypt, opaque expiring sessions, hashed one-time tokens and administrator TOTP. GitHub OAuth is not implemented in this checkpoint.
- Authenticated multipart image uploads are decoded, re-encoded as PNG and stored in S3. This replaces the proposed presigned-upload sequence. No SVG or arbitrary-code execution is accepted.
- Private/NDA-safe content is excluded from public APIs. Profile material edits require reapproval; availability changes alone do not.
- Contracts/payments remain external. Templates, components, subscription checkout, teams, source sales and hosted demos are deferred.

## Validation evidence

- Frontend production build and TypeScript validation passed.
- All 12 rendered-route and reusable-component checks passed.
- 59 isolated business-rule and external-URL checks passed using Java 17.
- Syntax parsing passed for 16 Java source/test files. This is not a full Spring compilation.
- 14 Spring/PostgreSQL integration tests are written: concurrency, privacy, inquiry idempotency, archival/qualification, dual-use accounts, client rejection, completion/review eligibility and email ownership.
- Maven compilation and PostgreSQL integration were not run: Maven, PostgreSQL and Docker are unavailable here, and dependency retrieval did not complete.
- Browser interaction, assistive-technology, cross-browser, load, restore, delivery and storage tests have not run. Rendered-route tests do not replace them.

## Public-release gates

1. Run the complete backend suite on a disposable PostgreSQL database; resolve failures before image promotion.
2. Deploy Spring, PostgreSQL, private S3 storage and transactional email with separate staging credentials; connect the frontend backend URL.
3. Exercise all six browser journeys in document 10, including rejection/retry, mobile, keyboard and accessibility checks.
4. Validate per-client proxy throttling, administrator MFA, dependencies and storage authorization. The baseline API limiter observes its remote address; a shared proxy needs per-client throttling before public launch.
5. Configure email authentication, bounce/delivery handling and outbox alerts; test actual uploads and access after suspension.
6. Approve operator details, policies, contacts, retention periods and staffed privacy/appeal processing. Existing policy pages are development notices.
7. Configure monitoring; perform backup/restore, migration and rollback drills; run load tests.
8. Enable public indexing only after the release gates pass.

## Confirmed roadmap

V1.5: advanced verification, link health, earned capacity and similar-builder fallback. V2: teams/studios and recruitment. V2.5: business accounts and matching. V3: proposals/contracts/milestones/payments. V3.5: verified source-code commerce. V4: isolated hosted demos and maintenance subscriptions. The documents include intermediate releases as well as V1–V4.

## Slate Atelier refinement and single administrator

Implemented a search-led hero with a rotating product spotlight, pause/previous/next controls, independent hover/focus pauses and reduced-motion handling. Filtered searches collapse the spotlight. Distinct illustrative project interfaces replace repeated generic cards. The workspace now has a navy welcome panel, actionable outcome summaries and vertical navigation for showcases, received inquiries and client requests. Fixed empty filter labels and preserved the supplied brand mark.

Administrator email defaults to `jetychodipilli@gmail.com`. PostgreSQL migration V4 restricts the ADMIN role to one row. Bootstrap serializes startup, verifies an existing administrator matches the configured email, never overwrites existing credentials, and refuses silent privilege elevation. Public signup reserves the configured administrator email. Password remains an environment reference in application.properties; the supplied value exists only in ignored local configuration. Real backend hosting and TOTP provisioning are still required.

Validation: TypeScript and the production frontend build passed; all 12 existing frontend checks passed. Browser review confirmed the homepage, redesigned workspace, client request tab, pause/next controls and React filtering (3 illustrative results). Java sources passed syntax parsing. Two additional PostgreSQL integration checks were authored for single-admin enforcement and reserved-email signup; database integration remains unexecuted. Browser mobile and reduced-motion emulation were not available through the supported interface.

## Compact navigation and glass refinement — 8 September

The original logo is displayed at a smaller size in a sticky 80px navigation bar (81px including the border). After the hero search passes above it, synchronized search and a filter popover appear inline. Intermediate widths use a navigation menu; small screens place search in a second sticky row. Active filters are counted, and the popover shares the original URL-backed filter state. Focused controls and open filters remain mounted when scrolling back. Blue ambient gradients, translucent surfaces, backdrop blur and fine bright borders make the glass effect visible, with readable menus and opaque fallbacks.

Validation: production build, TypeScript and all 12 existing frontend tests passed. Desktop browser review confirmed header position at the top while scrolling, the search handoff and return, filter popover interaction, React + SaaS filtering (3 illustrative projects), a synchronized inventory search (1 result), and clearing filters (6 results). No horizontal overflow was observed at the inspected desktop width. The logo asset is byte-identical to the supplied second image. Direct pointer and keyboard interactions worked; automated locator scrolling could reposition the page. The HTTP-only local preview also falls back to full-page navigation for filter changes because its framework requires secure-context Web Crypto for client navigation. Production navigation over HTTPS and mobile/reduced-motion browser emulation remain unverified in this review.

## Authentication follow-up — 8 September

Login/signup now use the split Slate Atelier product showcase and form layout. Google OpenID Connect, consent, one-time administrator MFA challenges, and 25 focused Java unit tests are implemented. Java compilation now passes. See `AUTHENTICATION.md` for exact configuration and remaining connected-staging checks. These changes do not establish full V1 completion.
