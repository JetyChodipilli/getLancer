# V4.9 review

The correctness review covers the new education domain and its integration with existing account/product/contribution/capacity, source commerce, reports, privacy and hosting. This is separate from Ponytail's complexity review. Verification is recorded in [VALIDATION.md](VALIDATION.md).

## Consequential review outcomes

- Education submissions freeze exact canonical content, source/file/license bindings and package/support terms. Changes need a new release; database triggers also reject changes to accepted purchase disclosures, source file manifests and reported artifact evidence.
- FREE source uses existing private bounded archive inspection and storage. The operator inspection is a POST protected by CSRF and recent MFA; approval requires evidence for that exact reviewer/release/hash. Public source cannot use the operator path or return PAID source bytes. Immutable package records do not receive broad UPDATE privileges merely to support row locking.
- PAID requests must identify the current reviewed release for the same project/seller/source. Generic college template checkout cannot bypass it. Existing capture/refund/dispute/provider reconciliation/current entitlement rules remain authoritative; test mode never grants real bytes. Numeric price comparison uses stable numeric values across PostgreSQL and JSON representations.
- Current product/profile/account/team/component/demo eligibility overlays frozen terms. Withdrawn component links disappear without changing retained source licenses. Source-only and unavailable hosted demos are stated plainly.
- Academic annotations are private and mutable separately from the source agreement. Account export whitelists nested fields and excludes private locators/provider fields, including future-field canary tests. Approved anonymization removes both new and legacy academic details while retaining the documented financial/source/audit history.
- Reports pin canonical reviewed evidence server-side, reject cross-project identity, and reveal moderator notes only through existing administrator controls. Existing product suspension revokes controlled free, paid and lab routes.
- The report detail projection includes the pinned release/hash/object, rather than returning an incomplete raw JDBC row. Concurrent approvals timestamp the locked decision with the actual clock; a real `pg_locks` regression verifies that the last serialized approval is the current public release.
- Clearing a paid source selection removes the prior binding while preserving entered terms. Empty SHOWCASE source bindings render the documented absence of distributed source; they do not attempt to render missing ZIP proof fields.
- The full existing component regression caught an omitted legacy `executionMode` field. The new public adapter preserves its existing “Source only” value and supplies current-mode disclosures for reviewed releases; the previous privacy/identity/review assertion remains unchanged.
- The existing optional-profile request test now supplies a scoped deterministic public DNS response. The real URL syntax/address policy and profile service still execute, and production DNS handling is unchanged. This removes its external DNS dependency without changing the behavior assertions.
- Typed bounded request records, explicit SQL/imports, deny-by-default routes and the real runtime-role permission guard remain enforced. Migrations V32–V34 are additive. Existing security-disposition findings, mandatory tests and expiry are unchanged; only reviewed input hashes are refreshed for the actual V4.9 source.
- GitHub CodeQL identified a fourth GET/CSRF finding through the shared public-download/operator-inspection helper. Public downloads now call a read-only package reader; administrator inspection records evidence only through its separate CSRF-protected POST path. The regression confirms anonymous and administrator GET downloads leave audit history unchanged. No new finding exemption or scanner suppression was added.

## Ponytail diff review

Lean already. Ship.

The separate education domain and small binding helper have direct callers and tested authorization/financial responsibilities. Existing archive, storage, payment ledger, Razorpay integration, publishing capacity, account and moderation primitives are reused; no dependency or lockfile was added. Their meaningful behavioral tests are retained.

## Ponytail whole-repository audit

1. `delete:` Dead legacy public-college read methods and four query constants now replaced by EducationService; preserve legacy owner/save/review methods. Nothing replaces these old reads. [backend/src/main/java/com/getlancer/components/ComponentService.java]
2. `delete:` Default empty SQLite/Drizzle configuration and its two dependencies are used only by the opt-in D1 example, while the application uses Java/PostgreSQL. Move tooling into the example package if that example is retained. [drizzle.config.ts, db/schema.ts, package.json, examples/d1]

net: -37 lines, -2 deps possible.

These optional repository-wide removals are reported and not applied as unrelated changes. The measured line opportunity is 22 dead public-read lines, four constants and 11 empty/default tooling lines; it does not count necessary compatibility or tests as bloat.

## Skill and operational boundaries

The requested gstack Work Mode workflow supplies correctness/security review, Unlazy supplies acceptance ledgers and independent re-verification, and Ponytail supplies the separate complexity report. UI UX Pro Max guides accessible controls, responsive layout and error/focus handling while preserving Spectral Studio and the installed shadcn system. No active freeze boundary needed removal. The available Alpha is a fixture dispatcher with unavailable referenced policy files; it supplies no production-security certification.

The production merchant/provider gate remains pending and `APP_EDUCATION_PAID_ENABLED=false`. Local tests do not claim new hosted deployment, production checkout, field performance, whole-application accessibility certification or a fresh remote CodeQL scan.
