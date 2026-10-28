# PR #56 second audit plan

Baseline: `11fab4f675883050c59a69d0ffde567e1006d195`, tree `664815fd3f07233501ddba26d52eb9676e3be820`; main `b634a35624e951435b4eead763ae7b947afe42ed`. The first pass is historical evidence, not proof that unknown defects are absent. PR56 is open. User now requests more audit/fixes BEFORE merging. Do not attempt merge or bypass the earlier automatic merge rejection.

## Premises and acceptance
The user requests a deeper search for existing functional, backend/frontend, security, form and performance gaps, a plan before fixes, and commits on the SAME PR56. This authorizes bounded investigation and reversible repairs; it does not authorize feature invention, production activation, live payments, destructive data changes or a new visual identity. Preserve the unrelated modified docs/auth/icy-wind-auth-reference.png and ignored environment settings. Node24.22.1/Java17 are available; installed frontend dependencies, Maven/PostgreSQL/Docker are absent locally. Existing GitHub CI supplies actual browser/database/connected verification. Do not claim unexecuted tests.

## Method
1. Review this plan using Autoplan's sequential CEO, Design, Engineering and applicable DX passes. Use available native independent reviewers honestly; no Claude CLI is present and no cross-model consensus is claimed. Existing user scope and code/commit authorization resolve routine choices; no further plan-approval pause is needed. Record actual ambiguity for root resolution without widening scope.
2. Map each domain's reads, writes, trust boundaries, lifecycle and invalidation. Compare the previous audit reports, then focus on cases they did not exercise: account/permission changes during I/O, uncertain mutations, replay/retry, expiry/revocation, background work, bounds and failure recovery. Review normal paths as positive controls.
3. Reproduce a supported defect before repair. Prefer actual extracted/helper behavior, real component browser tests and real PostgreSQL integration. Do not invent findings or demand a fixed number of bugs. Record falsified hypotheses and unchanged safe paths.
4. Make the smallest complete repair using established helpers. No speculative caches, pool changes, dependency updates or database indexes. Any performance optimization needs reproducible before/after measurements and an effect beyond noise. No new migration unless a confirmed repair requires it and root reviews its rollout/recovery implications.
5. Re-read as domain expert, hunt integration/race/authority/data-loss defects, then polish. Parent independently reviews each finding, checks actual changes and re-runs relevant focused tests; financial/security findings receive another independent review before push.
6. Integrate and push only intentional verified files into PR56. Run the complete workflow at the final head and resolve failures. Keep PR56 unmerged for user review; main/production qualification is outside this round's completion claim.

## Domain contracts
- Identity/inquiries: signup/login/reset/OAuth/MFA/session/account/export/deletion; profile, client requests, inquiry outcomes/contracts and review eligibility. Test stale identity, token replay/expiry and denied authority.
- Marketplace: public/private catalogues, component/source/education packages, saved items, slots, immutable releases and download access. Test withdrawn/replaced resources, catalogue/filter changes, duplicate/uncertain operations, rights validation and bounded continuation.
- Runtime/storage: admitted runtime/hosted demos, guest/provider protocol, expiry/cancel/crash, proof/media upload intent/completion, storage cleanup and background jobs. Test idempotency, lease/authority expiry and abandoned work; fake-provider tests do not establish real KVM isolation.
- Finance: delivery proposals/milestones, payment/refund/dispute transitions and maintenance subscription/entitlement/invoice lifecycle. Verify monotone provider facts, locking, immutability, late/duplicate/out-of-order evidence and user recovery. Never perform LIVE transactions.
- Operations: teams/business matching, membership/invitations, saved talent and concierge; moderation/privacy handoffs, taxonomy, reports, analytics and demo-health signaling. Test stale context, revoked authority, role changes, pagination, expiry and projection consistency.
- UI/performance: shared control semantics, keyboard/focus, error/loading/empty/disabled states, real form/row layouts at 320px and 200% text, content extremes and measured rendering/network issues. Preserve DESIGN.md and later explicit motion decisions; no replacement design, remote assets, animation library or generic generated page.
- Root: shared API/proxy/server helpers, DTO/response/config/database privileges, SAST bindings, integration tests/docs, PR writes and complete CI evidence. Workers request ownership amendments before editing shared files.

## Verification and evidence
Existing green baseline:780 backend/507 Node/943 browser/36 connected checks,8 intentional browser duplicate-profile skips, encrypted disposable restore. Re-run the final expanded head, do not reuse baseline counts as results. Each fix gets a relevant behavioral regression and positive/negative controls. New tests must use real component styles, viewport metadata, asynchronous esbuild plugins and complete typed fixtures. Run syntax/source-supported tests before push; no source assertions masquerading as runtime proof. Preserve scanner findings/gates and reviewed-hash binding. Record final exact head/tree/run IDs, exercised suites and limitations in ROUND2_AUDIT_RESULTS.md and PR metadata.

## Not in scope
No merge, deployment, provider/merchant activation, real KVM qualification, seven actual days of soak, launch-scale load certification, production restore/rollback or human operational sign-off. No credential access/output. No unrelated PNG replacement. No added business feature or large architecture rewrite to make an imagined defect disappear.

## Decision audit trail
| ID | Decision | Principle | Rationale |
| --- | --- | --- | --- |
| D1 | Keep the same PR56 and preserve all prior repairs | User scope | Avoid losing already tested work or fragmenting the requested review. |
| D2 | Reproduce findings and keep falsified hypotheses visible | Evidence | A large codebase does not justify speculative patches or zero-bug claims. |
| D3 | Retain the established UI and improve observed usability failures | Explicit design | User requested less clumsy forms, not a replacement brand. |
| D4 | Require measured optimization and current-head CI | Performance and correctness | Structural guesses and old passing runs cannot prove this expanded result. |
| D5 | Keep PR unmerged | Latest instruction | Further audit is requested before merge; earlier automatic rejection also remains binding. |


## Accepted sequential review amendments
CEO -> Design -> Engineering -> applicable DX completed in native independent review, followed by parent source/evidence review. No second-model or Claude consensus is claimed. Existing user scope authorizes these mechanical amendments; no unresolved taste/scope decision remains.

- T1: Each finding records exact source, reproduction and positive control, actor, selected resource, generation, mutation outcome (rejected/unknown/accepted), authority and invalidation, safe retry, affected callers and changed true/false/error branches.
- T2: Before the full responsive suite, CI runs new round2 browser fixtures after Chromium installation with max-failures1. Keep the entire original suite and all eight jobs. Eligible complete DTOs, real styles, viewport, valid required input and actual request initiation are required. Local Node/JDK grammar checks are not JSX/type/DOM/SQL execution.
- T4: Shared API/DTO/config/grants/security evidence stays root-owned. Domain workers coordinate ownership before editing, and financial/security changes receive another independent review before push.
- T7: Final qualification binds exact head/tree/run IDs, actual suite counts/skips and limitations; old green baseline is historical. PR56 stays unmerged.

The root independently reproduced the saved-component selection race from actual production mutation code: old alpha acknowledgement marks visible beta saved. Same-slug control passes; the delayed selection-change assertion fails. Marketplace owns the repair and rendered regression. Suspected sitemap pagination and ZIP proxy-size mismatches were falsified by actual backend contracts and will not cause patches.

