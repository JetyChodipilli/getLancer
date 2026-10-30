# Paper & Pixel validation

This report records checks for the approved P4 rollout. It distinguishes real browser interaction from mocked service responses. It does not certify the absence of every vulnerability.

## Baseline

On 28 October 2026 the main branch was `2f1ea580336ce2cf26c655a25d51b0aed080cc5f`. [Full validation run 40664475173](https://github.com/JetyChodipilli/getLancer/actions/runs/40664475173) and its [frontend job](https://github.com/JetyChodipilli/getLancer/actions/runs/40664475173/job/122735386699) were successful before this rollout. The new commit requires fresh CI results.

## Added regression coverage

`playwright.paper.config.ts` runs twelve behavioral scenarios at 1440px, 768px and 375px in Chromium. Four public/navigation/layout/reduced-motion scenarios also run in Firefox at 1440px and WebKit at 375px. The narrow navigation scenario explicitly checks 320px. Test discovery currently enumerates 44 cases.

| Scenario | Evidence being tested | Service boundary |
|---|---|---|
| Public exhibition | Project links, real preview props, four discovery destinations and editorial chapters | Production Worker in explicitly labelled sample mode |
| Navigation intent | No speculative other-route RSC request before a choice; Components navigation requests its route and preserves the document | Actual client navigation; strict page-error assertion remains |
| Search/filter/history | Search results, URL query, category removal, browser Back and reset | Real local sample catalogue filtering |
| Component examples | Selection controls and truthful local success/reset feedback | Browser-local specimen; no network mutation |
| Empty search | No manufactured results, recovery returns catalogue | Real local sample catalogue filtering |
| Narrow navigation | Menu Escape/focus restoration and 320px route reflow | Actual public routes |
| Reduced motion | Content remains visible, no running infinite animations | Actual homepage, auth and preview workspace |
| Workspace surfaces | Eleven preview areas, honest sample controls, maintenance dialog containment and focus return | Actual preview routes; no payment is made |
| Auth failure | Visible validation, focusable field links, password controls, remembered session payload, retained input | Mocked providers and rejected login response |
| Reset lifecycle | Pending lock, no premature success, retained email, explicit retry and non-enumerating acknowledgement | Mocked reset responses |
| OAuth redirect guard | An untrusted lookalike origin is rejected; no external navigation occurs | Mocked provider-start response |
| Sign-out failure | Account remains visible, error can be dismissed, explicit retry stays available | Standalone Header with mocked session/logout responses |

Commands:

```sh
npx playwright test --config playwright.paper.config.ts
# Only for a deliberately running local preview, never CI:
PAPER_REUSE_SERVER=true npx playwright test --config playwright.paper.config.ts
```

The suite emits screenshots/traces in `test-results/paper-pixel` and an HTML report in `playwright-report/paper-pixel`. Screenshot reviews supplement behavioral assertions; a passing reflow check alone is not a visual design review.

## CI changes

The existing frontend job keeps its full historical responsive/browser suites, TypeScript, Node tests, source contracts, production build, secret/source-map scanner, npm advisory checks and SBOM/OSV inventory. It now also enforces ESLint on every changed JavaScript/TypeScript source and test file using the existing rules, installs Chromium/Firefox/WebKit, runs the P4 suite and retains its artifacts. The checkout includes history so PR base / push-before SHA comparisons are available. Invalid SHAs and Git inspection failures reject the lint run. No ESLint rule was disabled. Existing functional checks have not been removed or skipped.

Repository-wide lint debt predates this rollout; the former CI did not run ESLint. A new whole-repository lint command would fail that unrelated baseline. The new gate checks the full contents of each changed source/test file, rather than only new lines, and leaves the existing global lint command available for debt remediation.

The historical `liquid-design.spec.ts` and `spectral.spec.ts` have been migrated from glass, orange accents, image-only logo markup, rotating auth cards and perpetual hero motion to the approved solid P4 material, inline outlined logo, static project exhibit and reduced-motion contracts. Navigation, pointer/keyboard activation, stable field geometry, account authority and dialog recovery checks remain. Package validation now tests the intended summary-first focus journey and its explicit invalid-field link rather than expecting immediate field focus.

## Security verification boundaries

The existing `scripts/check-frontend-security.mjs` scans built text assets for credential signatures, supplied server secrets and public source maps; it rejects published symlinks. Browser `frontend-security.spec.ts` checks real nonce-bound CSP, blocked inline scripts/handlers, trusted preview hashes and opaque sandbox boundaries. Connected Java/PostgreSQL tests remain separate in the Docker CI job. Browser route mocks do not replace those connected tests and do not prove external OAuth availability or payment-provider health.

## Local verification record

- Current `npx playwright test --config playwright.paper.config.ts --list`: passed; 44 cases discovered (36 Chromium cases and four each in Firefox and WebKit). The newly added navigation-intent case requires the final production build; the earlier execution records below cover the original eleven scenarios.
- Owned browser sources, configuration and lint script pass ESLint; full integrated TypeScript checking passes.
- Changed-file lint passed with zero errors; the existing fixed-size provider-icon image warning remains. Invalid SHA negative control rejected with exit 1.
- Chromium: all 33 discovery, form, auth, response-state and sign-out scenarios passed at 1440px, 768px and 375px. The narrow navigation scenario also exercises 320px.
- Firefox: all three critical public discovery, narrow-navigation and reduced-motion scenarios passed using the official Firefox 146 binary. This container cannot start a Firefox content sandbox (`/proc/self/uid_map: EPERM`), so the local run explicitly used `MOZ_DISABLE_CONTENT_SANDBOX=1`. CI keeps its normal browser sandbox and official pinned installation.
- After the final material/header build, both public form/detail/template/team material cases passed and all three exhibition/header geometry cases passed. The header check asserts that the navigation menu does not intersect visible account actions.
- The retained visual/keyboard suites remain in CI and preserve their functional checks. Local investigation confirmed that a failed package mutation focuses the error summary, its field link then focuses the invalid input, and the user's `../private` value remains available for correction. Mixed-build and resource-pressure runs are not counted as passing evidence.

The local Chromium executable was version 153 because the standard CDN download was truncated in this environment. `PAPER_CHROMIUM_EXECUTABLE` and `PAPER_FIREFOX_EXECUTABLE` are local review overrides, disabled when `CI` is set. The product does not bundle these review executables. CI uses the official Playwright-pinned Chromium, Firefox and WebKit and runs the complete retained suites. Local WebKit execution was unavailable because this container lacks its system libraries; CI installs them with `--with-deps`.

The full remote workflow result belongs to the final commit; the previous baseline's successful run is not evidence for this rollout. API mocks in these tests never stand in for Java/PostgreSQL, SMTP, storage, external OAuth, or payment-provider verification.

## CI follow-up, 29 October 2026

Candidate run [40758023441](https://github.com/JetyChodipilli/getLancer/actions/runs/40758023441) stopped after 611 passing Node checks and one failing test file. `tests/round3-ui.test.mjs` used obsolete whitespace boundaries to extract the real catalogue save routine, so its six behavioral cases could not start. The repair selects the actual save declaration and saved-list effect with the existing TypeScript parser, and supplies the current component closure bindings. All six original acknowledgement, duplicate-submit, parallel-save and delayed-read cases now execute and pass locally. No behavior assertion was removed.

The same audit replaced the retired `.project-save` browser selector with the Stockroom save button's accessible name. Historical hosting, maintenance, template and shared workspace form cases now assert the implemented summary-first error focus, follow its invalid-field action, then verify focus, retained input and retry. Their prior direct-focus expectation predated the accessible error-summary rollout. Sample catalogue and trust assertions track the revised plain-language disclosure. The targeted Node repair and ESLint checks pass; fresh-build rendering and remote results must be recorded for the final commit.

The next CI pass exposed the same formatting-dependent extraction in two publishing refresh cases. `publishing-audit-regression.test.mjs` now selects unique actual function declarations with the TypeScript AST; all eleven publishing authority, outage, race and transport cases pass locally. Missing and duplicate declarations are rejected rather than silently selecting a different routine.

The enlarged-text catalogue browser check found a real 7px horizontal overflow at 320px. Component specimen tabs now wrap and retain their intrinsic label width instead of shrinking into equal columns. The original overflow assertion remains unchanged. The previous broad local run was stopped at the project boundary after 79 passing desktop cases and that one reproduced failure; its unexecuted phone cases are not counted as passing evidence.

## Complete browser audit follow-up

The broad desktop diagnostic reached the component accessibility case (332 of the original 391 cases) before session continuation stopped it. It found a stale inquiry-region name and a real contrast dip during staggered metric entrance. The inquiry check now names the actual descriptive region and retains its action, long-address, native-filter and viewport assertions. Workspace entrance opacity is repaired in product CSS; the unchanged WCAG case must pass on the next build.

Five browser fixtures previously expected raw messages from unstructured 503 responses. They now assert the API helper’s safe read/mutation recovery text and the absence of the raw diagnostic, preserving request identity, retained data, retries and access controls. Coded domain-error messages and the separate source-download transport retain their existing checks.

The same diagnostic used Chromium 153 on port 3018 in Asia/Calcutta. The source-package exact-browser guard, an exact-origin sandbox check and a local datetime assertion consequently differed from CI’s pinned Chromium 145, port 3000 and UTC profile. Those checks are retained. An interrupted diagnostic is not counted as a full passing run.

Current-commit remote execution must remain the final release evidence. On the earlier `044467e1` candidate, the push workflow passed all 39 original Paper cases across the official three engines and all 90 Round 3 cases. Its PR workflow reported a WebKit page error during abandoned speculative prefetch at document unload; the explicit navigation-intent regression now guards the site-owned opt-out policy without filtering page errors. No browser scenario, WCAG threshold, security assertion or historical suite is skipped.

## Rebuilt navigation and contrast verification

The rebuilt Worker passed 39 local critical cases: 36 core Chromium cases across desktop/tablet/phone and the unchanged automated component WCAG case at all three widths. The strengthened navigation-intent case also passed in three separate viewport runs. This checks real Components client navigation and strict page-error capture after the speculative-prefetch opt-out.

The remaining eight desktop files were executed on port 3000 with UTC Node/browser timezone. The first pass completed 57 of 58 cases; its only failure was a 240-second wait for a preview iframe’s first paint while that iframe was entirely below the viewport. The test now scrolls the real iframe into view and asserts viewport intersection before the original first-contentful-paint wait. Its CPU/network profile, three samples per route, LCP/CLS/input proxy thresholds and source-interaction checks are unchanged. The single corrected performance case then passed, completing the remaining 58-case coverage across those two runs. This is not a claim of a single 58/58 run or of a complete passing 391-case local run.

The measured 390×844, 4× CPU, 150ms latency, 1600/750Kbps, disabled-cache samples were:

| Route | Samples | LCP p75 (ms) | CLS p75 | Input-to-next-paint proxy p75 (ms) |
|---|---:|---:|---:|---:|
| `/components` | 3 | 1768 | 0 | 33.7 |
| `/components/feedback-form` | 3 | 2036 | 0 | 23.0 |
| `/college-projects` | 3 | 1992 | 0 | 31.3 |
| `/college-projects/stockroom` | 3 | 1668 | 0 | 21.0 |

All retain the existing limits of 2500ms LCP p75, 0.1 CLS p75 and 200ms input proxy p75. These are bounded browser-lab samples, not field INP or live production load acceptance. The corrected inquiry region/action, long client address, native filters and reflow case passed separately at desktop, tablet and phone. Full exact-commit CI, including the official three engines and connected service journeys, remains the release gate.

## Client request fixture host correction

Candidate `2c07e5b8662d8cc6fd769b8cf72d25c48fa7a1d1` passed all 617 Node checks, 44 Paper cases across the official three engines and 90 Round 3 cases in both its push and PR workflows. Both workflows then exposed the same phone-only Round 2 continuation failure: an accepted row remained in the DOM but its heading was hidden. The standalone fixture mounted `ClientRequests` in the retired bare `.wrap` host, so the legacy horizontal row layout collapsed the text beside its actions. The real page mounts it in `WorkspaceFrame`, whose existing mobile layout stacks those rows.

The identity and request-history fixtures now use that actual frame and production stylesheet; their mock Link consumes the framework-only `prefetch` prop. Existing API, identity, retry, history-race and visibility assertions are unchanged. No product CSS or API behavior changed. Both whole files passed in separate desktop, tablet and phone runs on port 3000 with UTC Node/browser timezones: 10 cases per profile, 30 total. The request-history fixture uses asynchronous esbuild because its Link boundary is implemented as a build plugin. ESLint and full TypeScript checking pass. The corrected commit still requires a fresh complete remote workflow, including connected Docker checks, before release.


## Native navigation and full-contrast motion contracts

The complete responsive suite in [candidate run 40851934114](https://github.com/JetyChodipilli/getLancer/actions/runs/40851934114) exposed three stale visual assertions after 175 passing cases. Two used a shared entrance helper that still required the old opacity fade. The approved contrast repair uses position alone. The helper now requires opacity 1 at the initial, progressed and settled states and on every sampled animation frame; it retains the existing duration, stagger, elapsed progress, 8px entrance, visible position change, final geometry and reduced-motion checks.

The 320px shell matrix tried to focus a desktop rail link hidden by the mobile layout. It now opens the actual native mobile navigation with Enter, checks the current route, tabs into its first area, focuses a final destination within its bounded scroller and activates that real route with Enter. The same 18 public/workspace routes, viewport metadata and document-bound checks remain. No product source, browser error guard, test skip or acceptance threshold changed in this correction. ESLint and full TypeScript checking pass; production browser re-execution and fresh exact-commit CI remain required.

Parent verification of the final follow-up completed both changed browser files: 13 desktop cases passed; 11 phone and 11 tablet cases passed. The two existing desktop-only matrices remain exercised on desktop and are still excluded on phone/tablet by their original profile guard. This includes actual 320px route activation, original workspace paint sampling, fully opaque moving surfaces, keyboard focus and reduced motion. Separate built-app QA passed two storage cases per profile (six total): switching account/area survives refresh, and denied browser storage leaves save actions usable with a truthful limitation message and no page errors. Local Chromium remains version153; exact pinned-engine/full-suite CI remains required.

The copy audit removed the obsolete “Templates and checkout belong to a later release” sentence from the preview because Templates & purchases already exists. Touching that previously unlinted file exposed two synchronous-state-in-effect errors. Hydration now runs in a cancellable microtask after mount; persistence still writes only after readiness, and only its failure-state update is deferred and cancelled on cleanup. Existing five demo-flow Node cases, source lint and TypeScript pass. No provider, endpoint, payload, dependency or live capability changed.
