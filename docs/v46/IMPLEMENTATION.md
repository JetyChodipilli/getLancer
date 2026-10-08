# V4.6 reusable frontend contributions

The implementation completes sprint S02-B01–B08 from the supplied V4.5-to-V5.2 planning documents. It builds on V4.5 main `b672b584c6eee83e33d9de1db7863b8a38fd074b` and preserves the independent component/project/template publishing pools. Three free component slots remain separate from project and template capacity; extra places use existing admin pricing/provider authority. Visitor source and previews remain free.

## Requirement traceability

| Sprint item | Implemented behavior | Acceptance evidence |
|---|---|---|
| B01 | Two-step private draft, saved draft resume, retained back-navigation fields, explicit contribution and rights consent, bounded inputs and recoverable save/upload errors | `ComponentDraftEditor`, `ComponentWorkspace`; V4.6 responsive and connected browser journeys |
| B02 | Creator ZIP upload pinned privately without execution; exact source/setup/licence inspection and hash binding | `ComponentArchiveTest`; ownership/upload integration regressions |
| B03 | Immutable submission source and text; current revision and full snapshot hash; current admin MFA and actor authority held through publication; append-only distinct source versions | PostgreSQL lifecycle, MFA-race, direct concurrent insert and upgrade regressions |
| B04 | Exact reviewed ZIP through the V4 publisher; free preview with fixed identity/expiry; current role/account/profile/release checks; permanent preview revocation on withdrawal | Publisher regressions, controlled preview integration test and connected browser publication/withdrawal |
| B05 | Phone/tablet/desktop layout, 44-pixel controls, labelled inputs, dialog focus return, loading/error feedback and honest synthetic interactions | Responsive browser suite; field performance targets remain deployment measurements |
| B06 | Actor-private idempotent component saves, 200-entry serialized bound, unavailable projection after withdrawal/suspension, export and deletion handling; existing project saves retained | Bookmark integration and connected saved-list journeys |
| B07 | 24 original standalone HTML/CSS/JS examples, four each NAVBAR/SIDEBAR/FORM/CARD/AUTH/DASHBOARD; unique source hashes, README and MIT notice | Independent ZIP/metadata/licence/script checks and browser interactions for all 24 |
| B08 | Visible demo role cards and sample state; real upload/publisher failures never report simulated success; uploaded source runs only at the controlled publisher in an opaque frame | Connected uploaded-variant interaction and browser DOM/storage/network/navigation denial test |

## Creator and moderator workflow

An approved builder creates a private draft, describes their contribution and accepts the original MIT licence. They may choose a supplied recipe or upload their own self-contained frontend source. The form saves context before uploading. If upload fails, the saved draft ID and entered fields remain available for retry; it never creates a second draft merely to retry the file.

Uploads are ZIPs up to 5 MiB containing exactly `index.html`, `README.md` and `LICENSE`, each valid UTF-8 and at most 100,000 bytes. Paths, compression expansion, duplicate entries, archives/executables, credential-like content and malformed packages are checked by the existing V4 inspector. The full canonical MIT notice, copyright attribution, explicit rights consent, a bounded version and a synthetic interaction description are required. External dependencies and server execution are outside this self-contained format. Submitted bytes are never executed during upload or review.

Submission freezes the complete source archive, all three files, source/manifest/archive hashes and publication text. A moderator inspects those exact files and records a reason, the current revision and the hash of the whole reviewed snapshot. Source-changing publication requires a distinct version. The first source and every release remain immutable. Context-only review and restoration of the same latest source reuse its release and preview expiry. At most 100 distinct reviewed releases are retained; unchanged-source restoration remains possible at that boundary.

Published source remains available while a new private draft waits for review, under the existing archival policy. Moderation of an archived record uses its last published source and text, preserving an unsubmitted draft for the owner. Withdrawal denies subsequent public source/history requests and permanently revokes all existing preview identities. A fresh owner submission can renew source publication, but a distinct reviewed source version is needed for a new preview after withdrawal or expiry.

## Preview boundary and operations

Configure the existing V4 publisher, gateway secret, isolated public URL template, DNS/TLS and `HOSTED_DEMOS_ENABLED` as described in [V4 hosting operations](../V4_HOSTING_OPERATIONS.md). Hosting stays disabled by default. An active owner explicitly publishes the reviewed free preview. Its deployment UUID, exact archive/manifest hashes and configured expiry are reserved before the external PUT. Retries reuse them; a timeout cannot grant public authority before exact publisher confirmation. A withdrawal during publication prevents confirmation from reviving the identity.

The publisher rechecks the gateway on every public GET/HEAD. It denies inactive accounts, unapproved profiles, removed developer roles, suspended/withdrawn components, expired previews and obsolete source releases. Component responses use a dedicated CSP with no network, child frames, workers, form navigation or same-origin permission. Ordinary V4 demo headers remain unchanged. The app uses a script-free outer sandbox restricted to the exact publisher origin and an opaque child sandbox. Its frame policy also blocks child navigation to the application. Already-delivered/downloaded MIT source cannot be recalled; revocation controls subsequent requests.

Apply V29 and V30 with migration credentials, then reapply `ops/database/10_permissions.sql` for runtime/reader grants. V30 backfills pending V4.5 recipes and existing first-publication history without replacing existing payment, audit or source records. Release versions serialize at the database boundary. Preview identity, expiry and digest fields are immutable; revoked state cannot revive. Runtime roles may insert releases and update preview state but cannot mutate/delete history or access these tables through browser database roles. Roll back application code as a unit while retaining additive schema and immutable records.

## Verification and deployment limits

See [STATUS.md](STATUS.md) and [COMPLETION_GATES.md](COMPLETION_GATES.md) for observed results. Production frontend, backend, publisher, Docker, real database roles and security scans must pass on the published implementation before release. Existing scanner finding severities, exact advisory assessments and expiry remain intact; the reviewed input hashes track the audited new handlers and tests.

LCP/INP/CLS p75 targets are not field measurements from a local build. Measure them on the configured production deployment; this implementation does not claim a field-performance or universal WCAG certification. Backend live labs, paid college source listings and the V4.7/V4.8 execution platform remain later-phase work. Demo role switching cannot create real connected accounts or moderation authority.

Requested skills applied: gstack Work Mode and independent native review, Ponytail audit/change review, UI/UX Pro Max form/focus guidance, installed shadcn dashboard conventions and Unlazy acceptance gates. The template bundle is absent in this skill installation, so no upstream template assets were copied or dependencies added.
