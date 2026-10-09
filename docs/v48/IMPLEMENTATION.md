# V4.8 implementation and review

Status: **implementation prepared for CI; full phase acceptance remains open**.
Branch: `codex/v4.8-learning-labs-20261009`, based on main `b78cd25a926b40771c4597ee49bc4140db881f53`.
The user authorized publication and merge on 2026-10-09. Draft PR [#42](https://github.com/JetyChodipilli/getLancer/pull/42) publishes the implementation for real CI; full phase acceptance is required before merge or hosted activation.

The `/labs/scenarios` route provides nine free backend labs: Redis cache, synthetic authorization and a payment emulator, each implemented independently in Java, TypeScript and Python. Every language runs native HTTP and cryptography standard libraries without Maven, npm or pip dependencies. Cache cases use an actual disposable Redis 7.2.16 process. Synthetic payment events never move money or touch account entitlements.

| Plan item | Delivered locally | Remaining acceptance |
|---|---|---|
| S04-B01 workbench | Source catalogue, filters, request/state/event inspector and source downloads | Hosted gateway integration and browser/visual acceptance |
| S04-B02 Java patterns | Three native JDK 17 patterns with genuine HTTP evidence | Immutable isolated image and admitted provider |
| S04-B03 TypeScript patterns | Three native Node patterns with genuine HTTP evidence | Immutable isolated image and admitted provider |
| S04-B04 Python patterns | Three native Python patterns with genuine HTTP evidence | Immutable isolated image and admitted provider |
| S04-B05 observed transitions | Safe ordered events, run-scoped resets, equivalent text and replay controls | Actual desktop/phone/tablet browser verification |
| S04-B06 source and replay | Three deterministic archives covering nine labs; nine genuine recordings bound to primary-source SHA-256 | Public publication and hosted replay integration |
| S04-B07 bounded projections | Input/output limits, fixed loopback destinations, redaction, duplicate protection and negative HTTP regressions | Provider network/isolation/privacy audit |
| S04-B08 nine-lab catalogue | Free source/setup catalogue, explicit Source only / Replay / Hosted unavailable states | Recruited comprehension study and public release |

The dashboard adapts the pinned ShadcnStore dashboard grid/header from commit `65fc11224e96d56a62e224a58f7ed590aea5ac24`, preserving the existing Spectral Studio theme and Card/Badge components. Its exact MIT attribution is in `License.md`. No frontend dependencies were added. Native controls, focus styles, 44px targets, 16px fields, narrow-screen reflow and reduced-motion rules are implemented; source inspection is not a completed visual audit.

## Verified outcomes

- All nine lab behaviors pass the shared actual-HTTP matrix: **156 requests per language, 468 total**. Cases include real Redis miss/hit/expiry, refused and timed-out sockets, two-run reset isolation, current-role revocation, exact-body signatures, idempotent retries, refund ordering and the 100-event ledger bound.
- All three generated source archives pass the same matrix from fresh temporary extraction directories, without repository dependencies or package installation. Archive bytes, inventories and hashes are checked before extraction.
- Nine recordings were generated only after actual lab execution passed. Build checks reject stale source hashes, foreign run/request IDs, invalid event order, unsupported fields, inconsistent state/status/action projections and malformed or oversized JSON.
- **22 evidence-parser regressions pass.** Full frontend type checking, production build and the existing Node regression suite pass. Scoped ESLint, frontend asset security, typed route contracts and database permission source coverage pass.
- GitHub run `37919626084` passed **311 browser checks, including all 39 V4.8 cases**, across desktop, phone and tablet. The genuine-material journey downloads and checks the archive hash and renders original recorded metadata. Visual inspection of its screenshots found an inherited grid-column collision in the inspector heading; this is fixed with a scoped one-column header and a browser geometry regression. The updated exact head must pass CI again.
- The local production build and **209 Node regression tests** pass, including startup-report and captured signing-key regressions. All three freshly generated source archives reproduce the actual 468-request matrix independently.
- Backend, native lab scenarios, runtime container security and JavaScript/TypeScript semantic scanning passed in that GitHub run. Java semantic scanning found a bounded Redis reply loop comparing `int` to `long`; the validated length is now narrowed before iteration and all 156 actual Java requests were reverified. No scan disposition or blocking policy was relaxed. Docker and final exact-head checks remain required.
- `ops/labs/check-v48-benchmark.mjs` validates actual signed operator reports, current source/image pins, raw fresh-run counts, global overlapping reservations, queue wait and nearest-rank startup p95. Its protocol fixtures cannot establish hosted execution. `ACCEPTANCE_HANDOFF.md` specifies the missing provider inputs, report format and recruited comprehension-session procedure.

## Bugs found and fixed during review

- Payment timeout now preserves previously confirmed paid/refunded facts and returns 200; only unresolved pending payments return 202.
- TypeScript rejects trailing newline variants in signed event bodies, signatures and other exact-match fields before effects.
- Verifier cleanup recognizes signal-terminated child processes and does not wait indefinitely for an already-finished process.
- Dashboard selection/loading cannot apply stale or foreign evidence. Reopening the same evidence view no longer clears a recording without refetching it. Card titles use valid heading/button markup.
- Evidence observations must agree with authorization status and final cache/payment facts, while preserving legitimate intermediate miss/fallback and refund-reconciliation observations.
- Inspector headings and view controls occupy separate rows despite the application's shared `.grid` rule; the hosted-availability badge cannot overlap the title.
- Admission and benchmark envelopes use one captured operator public key so a replaced key file cannot switch trust between checks. Individual raw benchmark samples must also be current, even when the report timestamp is older.

Correctness/privacy review covered native servers, shared protocol, verifier, archive inventory, raw JSON validation, recordings, UI effects and CI integration. The separate Ponytail complexity review found no unnecessary dependencies, speculative service abstractions or dead extension mechanisms to remove.

Ponytail complexity-only verdict: `Lean already. Ship.` Full release acceptance follows the open gates below.

## Reproduction

```sh
REDIS_SERVER=/absolute/path/to/redis-7.2.16/src/redis-server node scripts/verify-lab-scenarios.mjs
node --experimental-strip-types scripts/build-lab-material.mjs
REDIS_SERVER=/absolute/path/to/redis-7.2.16/src/redis-server node scripts/verify-lab-packages.mjs
node scripts/verify-v48-frontend.mjs
```

For browser acceptance, install the repository's pinned Playwright Chromium and run `node scripts/verify-v48-frontend.mjs --browser`. CI includes this through its existing all-browser suite and adds a pinned Redis/native-language/archive reproduction job. The Java lab is also compiled during semantic security analysis. Existing security dispositions remain unchanged; only the changed CI workflow's build-input fingerprint was refreshed.

## Open phase gates

The local Unlazy ledger has 17 gates: **13 met, 3 unmet, 1 abandoned for environment handoff**. The abandoned gate is browser acceptance N4; the implementation does not claim it passed. The three unmet gates are:

1. P1: inherited V4.7 provider admission, certified immutable images, KVM/network isolation, quotas/budget, cleanup, restore and operational evidence.
2. P2: at least 100 fresh hosted starts per adapter, queue-separated startup measurements, p95 warm ≤5s and cold ≤30s.
3. P3: recruited-user evidence that replay, the payment emulator and actual execution are understood correctly.

These require actual operator/provider and research evidence. Local fixture timings, recordings and parser/browser fixtures cannot substitute for them. Public runtime remains disabled. V4.8 is not certified complete and is not ready to merge solely on these local results.
