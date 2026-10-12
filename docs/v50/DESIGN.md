# V5.0 frontend design and evidence

Recovered 2026-10-12 from the published V5.0 implementation at `78f39ab`, using the NEW `CONTRACT.md`. This document describes the current interface and the recovery review. It does not imply final browser or visual acceptance; that remains root:G4.

## Architecture and authority

The React/Vinext page `/labs/data-ai` is a source and evidence inspector. It consumes the runtime-owned catalogue, compiled `evidence-manifest.json`, generated material index and original LOCAL result recordings. It never starts inference, training or a hosted request. Runtime execution and recording generation belong to the original Python source and material builder. Hosted execution belongs to the existing shared workbench, signed operator manifests, current provider certification, independent activation flags, quotas and cleanup authority.

| View | User action | Observable authority |
| --- | --- | --- |
| Source | Read bounds, setup, rights and limitations; download free source | Original source archive; its version is confirmed only when the pinned index loads |
| Recorded execution | Inspect default or changed recorded case | Two original LOCAL envelopes presented as REPLAY, with actual UUID, input, timestamps, elapsed time and hashes |
| Hosted runtime | Open `/labs` for current admission | Existing authentication/provider/operator/admission authority; a recording grants no hosted run |

Changing a recorded case selects an original response. Charts scale returned values for display and tables retain the exact returned cells. Computation occurs when the source is reproduced locally. Setup uses `LAB_RUN_ID`, `LAB_PORT` and the allowlisted `execute` operation. Checksums bind evidence to the compiled source/archive and dataset/model/evaluation identities; they establish consistency, not independent authenticity of a local claim or real-world accuracy.

## Visual decisions and skill workflow

The visual authority remains `design-system/getlancer/MASTER.md` (Spectral Studio). The existing scenario inspector, shadcn Card/Badge primitives and education creator/detail/reviewer/purchase patterns informed the page. The original page workflow used UI/UX Pro Max design-system, chart and detected React-stack searches: data analytics evidence dashboard; labelled category bar chart; async evidence fetch errors. The useful guidance was a productive data-dense dashboard, direct labels with equivalent tables, and explicit async failures. Unrelated Enterprise Gateway marketing structure, blue palette and Fira fonts were not adopted.

For this targeted accessibility recovery, the full UI/UX Pro Max skill and its pre-delivery guidance were read again. `text color contrast animation --domain ux` returned the normal-text 4.5:1 contrast/readability rule. `async UI animation state --stack react` returned async error-handling guidance alongside generic concurrency recommendations. React/Vinext is detected from package.json. No framework or animation dependency was added, and no unrelated concurrency refactor was needed. The master and approved tokens remain unchanged; this file records page decisions within the leaf's writable scope.

The page uses pearl/silver surfaces, vermillion actions, self-hosted Inter and current semantic color/control/focus tokens. Dense records have readable backplates. Desktop catalogue/inspector columns use 24px gaps and 20–24px card padding. Below 1000px the catalogue sits above the inspector with two cards per row; below 640px everything stacks with 16px gutters. Setup, hashes and disclosures wrap. Wide tables scroll inside their labelled region, preserving document width.

## Output and interaction semantics

Each view uses a native button with its pressed state. Category and case selects have labels. One H1 and nested section headings organize the page. Counts disclose four curated sources and build-listed recordings without fabricating runtime status. The case status announces the ordinal, default/changed case, original LOCAL mode and measured duration.

Each returned metric names its unit. Direct chart labels accompany semantic tables with captions, column headers and row headers. Bars are hidden from assistive technology so the equivalent table supplies one complete reading. Model prediction/confidence remains adjacent to the frozen-model disclosure. Eight held-out synthetic samples, correct count, accuracy ratio, split, protocol and limitations stay visible; tiny fixture metrics do not establish real-world performance.

Controls use 16px text, at least 44px action height and the existing 2px focus outline. Recording errors receive focus, show no output/prediction and offer retry. Source remains available when the index fails. Bounded timed fetches are aborted and guarded against late writes when selection/view changes. The 480ms opacity/8px output entrance follows the master; reduced motion disables it. No continuous record-value animation or animation library is introduced.

Existing education UI consumes the exact top-level `dataAiEvidence` contract through creator editing, draft review, source/detail, private operator review and accepted purchase snapshots. Partial drafts remain saveable. New analytics/AI submissions require separate rights/provenance and true safe declarations; AI additionally requires frozen JSON model and held-out evaluation context. Historical absence is stated explicitly. No historical snapshot is backfilled. Education backend/type/commerce authority remains outside this recovery leaf.

## Parser and security boundary

The strict parser rejects unknown/duplicate raw JSON fields (including escaped duplicate member names), excessive bytes/depth, invalid UTF-8/content type, foreign schema/mode/scenario/UUID, stale source/data/model/evaluation hashes, malformed hashes, future/inconsistent timing, foreign URLs and unsupported input fields. The index matches the compiled source/archive hashes and exact catalogue items. Each recording contains matching default/changed LOCAL envelopes with distinct input and result hashes. Text inputs must equal the fixed synthetic examples, preventing foreign private text from being echoed through a rehashed recording.

Fixed tables/columns/metrics/classes pin labels, types and units. Values are finite, bounded and limited to six decimal places. Analytics totals/counts/rounded money averages and sensor accepted/rejected/percentage totals reconcile. Inference probabilities sum to one; prediction and confidence agree with the maximum; held-out accuracy agrees with correct/sample counts and its metric. Split/protocol/limitations match the pinned context. Exact catalogue identity comparison retains numeric precision; normalization is used for canonical hashing only.

## Recovery defect and correction

The parent reported the published 51-case browser suite had only two failures: the malformed-unit rejection/retry accessibility scan on desktop and phone; tablet passed. The reported targets were metric labels and unit spans under `.data-ai-output`. That test scanned immediately after the valid retry mounted the output's finite opacity entrance. Sampling halfway through the parent fade changes the composed text contrast even though the final semantic colors are unchanged.

The test now waits for the actual finite output animations through the Web Animations API's `finished` promises, then asserts the rendered output opacity is `1` before executing the unchanged axe audit. There is no arbitrary sleep, disabled axe rule, increased threshold, injected contrast color or reduced-motion override in normal audit tests. The malformed-unit/rehashed-output denial, focused alert, empty output and successful retry assertions remain intact. A settled retry screenshot accompanies the audit. The approved interface motion and reduced-motion behavior remain unchanged.

Passing-state screenshots use `testInfo.attach` with full-page PNGs. Each project captures source, replay and hosted disclosure; default and changed output for both AI scenarios; partial and complete creator evidence; private reviewer evidence and accepted purchase disclosures. The existing reviewer/purchase component fixtures show their frozen disclosure state; they do not substitute for server-side MFA, payment or entitlement proof. Attachments are test artifacts for parent review, not fabricated browser observations. Screenshot capture also waits for finite output motion and uses Playwright's standard animation stabilization.

## Four review passes and evidence

1. **Context:** read the new system/result/material/education contract, runtime catalogue and parser, current inspector, approved Spectral tokens and the CI failure supplied by the parent. Rechecked the targeted UI/UX skill guidance.
2. **Implementation:** restored this design record and added finite-animation settling plus passing-state screenshot attachments. Kept the original interface and full axe rules.
3. **Defect hunt:** checked cancellation/late-response guards, index/source pins, byte/depth/duplicate/hash/numeric/context denials and exact input privacy. Checked that animation waiting excludes infinite effects, handles reduced-motion through the opacity assertion and introduces no fixed delay.
4. **Polish:** reviewed labels/headings, table equivalence, focus/error/loading/fallback behavior, wrappers, touch sizes, keyboard controls, text enlargement and responsive/reduced-motion coverage. Distinguished current source evidence from parent-owned final rendered acceptance.

Executed after the restored materials and dependencies became available:

- `node --experimental-strip-types --test tests/data-ai-evidence.test.mjs tests/education.test.mjs`: **27 pass, 0 fail**, including actual recordings and rehashed adversarial controls.
- `npx tsc --noEmit --incremental false`: **pass**.
- Focused ESLint for inspector/parser and owned evidence/browser tests: **pass**.
- `git diff --check`: **pass**.
- `npx playwright test tests/browser/data-ai.spec.ts --list`: **51 discovered checks** across desktop, phone and tablet; discovery does not execute assertions.

The leaf has not executed Chromium or inspected rendered screenshots in this recovery. Root owns the current production build, actual Chromium execution, review of attached passing screenshots, fixes arising from that review and final visual/security acceptance in root:G4. The earlier two CI failures are the reported baseline; no claim that this patch passes them is made before re-execution. No dependency, deployment or hosted activation was added.
