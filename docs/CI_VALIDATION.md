# CI validation and backend time budget

The backend job runs the complete Maven verification suite once against its disposable PostgreSQL 16 database. Round 3 finance, marketplace, operations and runtime/storage tests, plus `DatabaseRoleGuardIntegrationTest`, run as part of that suite. A post-suite check requires each named class to have nonzero test cases with no failures, errors or skips. Missing, malformed or mislabelled XML fails the job.

The same five XML reports are copied unchanged from the complete suite into `backend-round3-smoke-<source SHA>`. This artifact name is retained for existing evidence review; it is now a subset of the full run, rather than a second execution. Available reports are retained on failure. `backend-verification-<source SHA>` still contains the full Surefire reports and the generated SAST evidence. Reports are cleared before verification, and both security scan jobs still depend on a successful backend job and download evidence from the current workflow. SAST evidence binds the reports, workflow and relevant source to the current source SHA; no previous-run evidence is reused.

The backend job limit is **30 minutes**, while the existing 60-second JUnit test timeout, 20-second JDBC socket timeout, 15-second database statement timeout and pool size of four remain unchanged. The database suite remains serial.

| Baseline observation | Evidence | Implication |
| --- | --- | --- |
| Main backend job was cancelled at exactly its 20-minute limit at `2026-11-03T06:16:56Z`; the full suite was still progressing with zero reported failures | [Run 41586810447, backend job 125754111399](https://github.com/JetyChodipilli/getLancer/actions/runs/41586810447/job/125754111399), source `4413a026fe8787c98ab2ff04f5c78ae4db9c6189` | Cancellation was the immediate CI failure; downstream scans could not run |
| The targeted five-class run repeated 97 cases already included in the full suite and took about four minutes | Same main run's fixture and full-suite logs | Removing this duplicate execution saves that work without removing any test |
| The preceding PR full suite passed 880 tests in about 12m20s; main's full run had reached about 15m50s when cancelled, with classes still remaining | Reviewed PR/main backend logs | A 20-minute total budget did not cover runner variability plus repeated tests and remaining packaging/audit work |

Thirty minutes leaves finite headroom for the single full suite, packaging, dependency inventory and OSV audit. The expected saving is the removed four-minute duplicate run; a new branch/main run must establish the actual total and test count. This change does not claim a measured post-change runtime before that run completes. If the new job still approaches its limit, inspect per-class and step timings before changing the bound again.

The final `validation-status` job always inspects lab, backend, frontend, connected Docker, SAST matrix and container scan results. It writes a results table and fails for any failure, cancellation, skip or missing required result. Matrix job success represents all matrix entries passing. The security jobs keep their backend dependency, so incomplete test evidence cannot permit a scan assessment. A workflow-wide cancellation may also cancel the final job; treat the run's cancelled result as incomplete validation.

To check the workflow guards locally, run:

```sh
node --test tests/ci-workflow.test.mjs
```

These tests execute the actual embedded XML validator and final-status command with passing and failing fixtures. They also check one full Maven test execution, retained timeout/database bounds, artifact names, scan dependencies and inclusion of every workflow job in the final status. PostgreSQL integration tests, container tests and security scanners must still pass in GitHub Actions before publication.

The same PR run passed the backend and security jobs, then found two interaction-budget failures in the full browser suite: `/components` produced a 234.4 ms maximum page sample in the desktop project and 220.8 ms in the tablet project. The suite ran a 4x CPU-throttled benchmark alongside another browser worker. Run the unchanged performance spec separately with one worker, then run the remaining browser tests with two workers. All three profiles, four routes, three cache-disabled samples per route, 4G conditions, and the 2500 ms LCP / 0.1 CLS / 200 ms interaction-proxy limits remain required. No readiness delay, retry, or threshold change is introduced. This isolates runner contention; it does not claim an application speed improvement or prove that any future budget failure is noise.

`tests/ci-workflow.test.mjs` invokes Playwright's real collection mode for the unfiltered suite and both actual workflow commands, then verifies disjointness and exact set equality. The isolated command must retain desktop, phone and tablet. Successful measurements and failure diagnostics are retained in `performance-browser-evidence-<workflow SHA>` before the general suite starts. The new current-source run must establish whether the unchanged budgets pass with isolated scheduling.

The next run exposed WebKit page errors when discovery navigated away with category/technology reads still pending. The shared `useTaxonomy` hook now cancels both reads on unmount and document `pagehide`, ignores cancelled continuations, and restarts with a fresh controller on back/forward-cache `pageshow`. Normal options, the explicit preview fallback and real error messages remain. Eight tests execute the actual transpiled hook and cover cancellation, late success/failure, cached-document restoration and normal recovery; mutations restoring the original hook or removing cancellation/restoration fail. The browser suite still rejects every page error without filtering or added navigation waits. Current-source WebKit acceptance must confirm the observed unload failure is resolved.
