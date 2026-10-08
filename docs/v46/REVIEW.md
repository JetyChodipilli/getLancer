# V4.6 audit and review

## Ponytail whole-repository audit

`delete: Remove the unused sidebar and six exclusive helpers. Retain meaningful UI smoke coverage using the workspace navigation. [components/ui/sidebar.tsx; components/ui/input.tsx; components/ui/sheet.tsx; components/ui/separator.tsx; components/ui/skeleton.tsx; components/ui/tooltip.tsx; hooks/use-mobile.ts]`

`delete: Remove the unused chart wrapper and recharts. Redirect its test-only consumer to retained theme coverage. [components/ui/chart.tsx; package.json]`

The sidebar has only a test consumer. Its six helpers have only sidebar consumers; the mobile hook is included in the conservative source-line inventory. The chart wrapper has only a test consumer. No unrelated removal was applied during this phase start.

`delete: Remove thirteen unused static Support imports. Nothing replaces them. [accounts/PrivacyService.java; accounts/AccountService.java; hosting/HostingService.java; business/ConciergeService.java; payments/PaymentService.java; auth/GoogleAccounts.java; auth/GitHubAuthService.java; notifications/Mail.java; inquiries/InquiryContractService.java; products/PrivateProjectService.java; maintenance/MaintenanceService.java; commerce/CommercePaymentService.java; commerce/CommerceService.java]`

Every production Java file was checked for these imports and actual calls/method references; thirteen imports have no use. The wrapper/abstraction scan found no unsupported single-implementation interface deletion candidate.

`net: -1398 lines, -1 deps possible.`

## Ponytail change review

The first pass identified eager source bodies in the shared catalogue module. The final change imports source-free metadata for discovery and loads source only for the detail/review path. No new dependencies, form library, generic repository abstraction or unused framework were added.

`Lean already. Ship.` applies to the reviewed contribution slice's complexity, not to completion of the full V4.6 acceptance contract.

## Correctness review

- Saved lists and removals use only the current actor. Save count and insert are serialized under that actor's row lock; authority is rechecked after obtaining the lock.
- Pending content cannot change through the mutation service or direct SQL. Submission captures the current pinned recipe; subsequent catalogue changes cannot silently change a pending review's source.
- Public data continues using the last published snapshot while edited context awaits review. Draft source hashes and moderation reasons remain private.
- Release/history and saved-detail reads recheck public account and publication authority. Withdrawal preserves bookmark identity but excludes title/source in the unavailable projection.
- Explicit API policy and route-contract fixtures include all four new routes. Controllers depend on services and retain declarative authorization. Existing pricing/provider and three-slot calculations remain intact.
- Runtime grants classify both V29 tables: releases permit select/insert, bookmarks permit select/insert/delete. The startup guard rejects mutable release grants. Real PostgreSQL-role regressions verify these operations and direct browser-role denial.
- Reviewed GET finding dispositions, scanner rules and expiry remain unchanged. The review inventory includes every current production/test input, including V29 and the new routes. New GET handlers only read; bookmark mutations use POST/DELETE with the existing browser-origin checks and authenticated actor.

Limitations and required later acceptance are recorded in IMPLEMENTATION.md. No whole-phase security, WCAG, performance or launch certification is claimed.

## Operational learning

Catalogue source bodies should remain outside shared client metadata. Maintain exact metadata/source projections in the independent source test. Existing root GATES.md covers the security release; preserve it and use a task ledger under docs/v46 for this fresh phase. In this runtime Maven requires explicit proxy configuration; environment HTTP_PROXY alone is not consumed by Java artifact resolution.

CI browser evidence exposed a four-tab intrinsic-width overflow on phones and an exact-label selector mismatch for the nested time-range select. The detail tabs now use a bounded two-column layout on narrow screens; the range select has an explicit associated label. Security traces exposed footer hit-test interference with default-size probe iframes and hydration removing probes inside React-owned main. The fixture now waits for a hydrated control and installs sized frames in a dedicated foreground host outside main, preserving the opaque sandbox and positive/negative CSP assertions. Full browser checks remain required after these corrections.

OSV added the existing Spring SSE-fragment advisory to the current dependency report. The required streaming feature is absent in this REST application. The second narrow assessment requires its own real application-context regression, source/report hashes, exact advisory revision and existing expiry; introducing streaming source invalidates it. Container evidence preserves the original severity and findings. See DEPENDENCY_APPLICABILITY.md; the dependency is not claimed patched.

## V4.6 completion review

A fresh-context native reviewer examined the completion diff and the publisher/database boundaries. No outside-model review is claimed. Findings corrected before publication:

- Match the complete canonical MIT notice rather than a few permission markers.
- Require the current developer role for public source and preview authority; include private bookmarks in actor export/deletion.
- Pin archived/suspended moderation source and publication text independently of saved private drafts. Hash exactly the projected source and text, and preserve the owner draft until explicit submission.
- Reuse an unchanged latest source release for context-only review/restoration; never renew fixed preview expiry by allocating an identical release. Lock the component row before checking direct release version uniqueness.
- Permanently retain withdrawn preview tombstones, deny revoked-state revival in SQL and publication confirmation, and prevent moderator restoration from bypassing owner withdrawal.
- Hold current admin/owner account, profile, role and session authority through mutation commit. Concurrent MFA revocation is rechecked before publication.
- Require recent MFA for private admin source exports and record only actor/resource/request metadata in the private-export audit.

Actual Chromium evidence caught `frame-ancestors *` rejecting an opaque outer sandbox. The component-only response policy now permits embedding while preserving script/form sandboxing without same-origin, all outbound network/child-frame restrictions, and the exact-origin outer navigation boundary. Ordinary V4 publisher policy remains unchanged. The positive interaction and negative DOM/storage/network/navigation browser test passed after the correction.

Ponytail completion review: reuse the existing ZIP inspector, publisher client, capacity authority and shadcn Dialog/Tabs; add no runtime dependencies or generic upload framework. Source/base64 stays out of catalogue metadata and public projections. Full final CI evidence is tracked in STATUS.md rather than inferred from test specifications.

Final native recheck found no remaining normal-API blocker. The legacy suspended-context ambiguity is nonblocking with the conservative migration disposition documented in IMPLEMENTATION.md.

The added automated WCAG scan found inadequate contrast in inactive component-detail tabs. The component-scoped foreground colour is corrected while retaining the existing shadcn tab behavior. The mobile lab fixture uses the production Worker, disabled cache, explicit CPU/network throttling and separate shell metrics; results are retained without presenting event-timing interaction samples as field INP.

## PR #40 issue follow-up

Native Chromium reproduced two creator-form navigation failures: entering a short contribution or an invalid upload version, then choosing Back, caused native validation on the hidden second step to block Continue. The contribution minimum length and upload version pattern now apply only while that step is visible. Values remain mounted and preserved, and final-step validation remains enforced.

The new browser regression failed against the original form for the short contribution, then independently failed for the invalid version after correcting only the contribution. The complete fix passed on desktop, phone and tablet. TypeScript, the production build and all 165 Node tests passed; final published-head CI remains required. Earlier backend fixture corrections are verified by run 37817401539: 440 tests passed with zero failures/errors/skips. That preceding run is not evidence for a later commit.
