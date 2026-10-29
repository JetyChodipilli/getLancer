# Paper & Pixel validation

This report records checks for the approved P4 rollout. It distinguishes real browser interaction from mocked service responses. It does not certify the absence of every vulnerability.

## Baseline

On 28 October 2026 the main branch was `2f1ea580336ce2cf26c655a25d51b0aed080cc5f`. [Full validation run 40664475173](https://github.com/JetyChodipilli/getLancer/actions/runs/40664475173) and its [frontend job](https://github.com/JetyChodipilli/getLancer/actions/runs/40664475173/job/122735386699) were successful before this rollout. The new commit requires fresh CI results.

## Added regression coverage

`playwright.paper.config.ts` runs eleven behavioral scenarios at 1440px, 768px and 375px in Chromium. Three public/layout/reduced-motion scenarios also run in Firefox at 1440px and WebKit at 375px. The narrow navigation scenario explicitly checks 320px. Test discovery currently enumerates 39 cases.

| Scenario | Evidence being tested | Service boundary |
|---|---|---|
| Public exhibition | Project links, real preview props, four discovery destinations and editorial chapters | Production Worker in explicitly labelled sample mode |
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

- `npx playwright test --config playwright.paper.config.ts --list`: passed; 39 cases discovered (33 Chromium cases and three each in Firefox and WebKit).
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
