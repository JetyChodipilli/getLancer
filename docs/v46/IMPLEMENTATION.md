# V4.6 fresh implementation

Started 8 October 2026 on a new branch from main `b672b584c6eee83e33d9de1db7863b8a38fd074b`. No prior V4.6 branch or completion evidence was resumed. The scope is grounded in the supplied sprint plan, S02-B01 through S02-B08, with the user's latest independent three-free-slot policy retained.

## First implementation slice

- Visible client, builder and administrator demo cards on the personal workspace. Component publishing has separate sample creator/moderator/visitor views.
- Two-step component draft form with bounded input, retained back-navigation values, contribution disclosure, licence consent and pending/error feedback. Existing saved backend drafts can be reopened.
- Twenty-four original standalone HTML/CSS/JavaScript entries: four navigation, four sidebar, four form, four card, four authentication and four dashboard examples. Every source ZIP contains its exact source, README and MIT notice. No remote dependencies or credentials are required.
- Catalogue metadata is separated from source bodies in the client bundle; source inspection is loaded when needed.
- A submitted recipe snapshot is frozen at submission, including at the database boundary. Review accepts a source hash and rejects mismatched hashes; current revision and current administrator MFA remain required.
- Approved release records are append-only. History checks current public authority. Existing first-publication source remains immutable.
- Explicit withdrawal revokes public detail and version-history access immediately on the next request. Archiving retains the already-published MIT source under the existing policy.
- Private component bookmarks use the authenticated actor, idempotent save/delete, a serialized 200-entry limit and an unavailable state after withdrawal/suspension. Project saves retain their existing service. Preview saves are clearly labelled browser-local sample visitor state.
- Compact workspace typography, role cards, dialog copy and spacing preserve 44-pixel controls, focus outlines and phone input text sizes.

## Remaining V4.6 acceptance work

This slice does **not** mark the whole phase complete. The existing curated-recipe workflow remains explicit. Arbitrary creator source-archive upload, source-changing versions of an existing entry, and V4 publisher integration for controlled free previews are still required by S02-B02/B03/B04. Source hashes and immutable recipe-context history alone do not satisfy those requirements.

Owner/account restrictions and withdrawal deny new requests. Already-delivered iframe source cannot be recalled by these request checks. A controlled publisher/gateway with current authority and active revocation is required before claiming the S02-B04 acceptance gate.

The 24 sources have independent ZIP/hash/licence and JavaScript syntax checks. Browser test specifications cover all 24 interactions, associated errors/loading, visible role switching, draft/back/review, saved state and widths 320/390/768/1024/1440. Passing source tests do not establish rendered accessibility or field performance. Browser/connected acceptance requires actual CI or supported browser execution. LCP/INP/CLS field targets remain unmeasured.

## Verification and operations

Use Flyway V29 for the additive submitted snapshot, release history and bookmark tables. Keep application and migration credentials separate as documented in DATABASE_RUNTIME_ROLES.md. Do not erase immutable releases or existing payment/audit records during rollback. Roll back the application together and retain the additive migration.

The frontend preview remains illustrative for account/community actions. It must not imply that the Spring backend or PostgreSQL service has been deployed. Existing paid-slot policy and merchant gates are unchanged.

Requested skills applied: gstack Work Mode, ponytail whole-repository audit and change review, Unlazy gates, UI/UX Pro Max compact-form/focus guidance, shadcn dashboard free conventions and shadcn dashboard template adaptation guidance. The dashboard template's bundled source directory is absent in this installation; no template files or licence-dependent upstream assets were copied.
