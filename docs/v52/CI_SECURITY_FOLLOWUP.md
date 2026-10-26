# CI and dependency security follow-up

25 October 2026. This records the inspected merged-main baseline and the scoped repair; it does not certify production readiness.

## Completed merged-main workflow

Main `16e45399478c630c819b0f82726c6ede42f5ec60` completed [workflow 40186745829](https://github.com/JetyChodipilli/getLancer/actions/runs/40186745829) successfully at `2026-10-25T11:39:20Z`. All eight jobs passed. The earlier frontend/Docker pending status is superseded by this completed run.

| Job | Inspected result |
| --- | --- |
| [Backend](https://github.com/JetyChodipilli/getLancer/actions/runs/40186745829/job/121149047572) | 747 tests, zero failures/errors/skips; runtime Java dependency audit passed |
| [Frontend](https://github.com/JetyChodipilli/getLancer/actions/runs/40186745829/job/121149047463) | 450 Node tests passed; 668 browser cases passed and four skipped; public asset and dependency checks passed |
| [Native labs](https://github.com/JetyChodipilli/getLancer/actions/runs/40186745829/job/121149047637) | Eighteen core scenarios, source reproduction, data/AI, IoT and local Docker checks passed; Angular audit reported the repairable advisory below |
| [Java SAST/secrets](https://github.com/JetyChodipilli/getLancer/actions/runs/40186745829/job/121150757549) | Three findings with current-source test-bound not-applicable assessments; zero blocking findings; both secret scans passed |
| [JavaScript/TypeScript SAST/secrets](https://github.com/JetyChodipilli/getLancer/actions/runs/40186745829/job/121150757560) | Eight findings below the high-severity threshold, reviewed below; both secret scans passed |
| [Python SAST/secrets](https://github.com/JetyChodipilli/getLancer/actions/runs/40186745829/job/121150757562) | Zero findings; both secret scans passed |
| [Container security](https://github.com/JetyChodipilli/getLancer/actions/runs/40186745829/job/121150757569) | Zero blocking high/critical image findings; two backend findings independently assessed not applicable; publisher had no such assessments |
| [Docker startup/connected/recovery](https://github.com/JetyChodipilli/getLancer/actions/runs/40186745829/job/121153659744) | 36 connected browser cases passed; disposable encrypted database restore passed in 2302 ms; external object recovery and production RPO/RTO remain unaccepted |

The raw scanner outputs remain in the run artifacts. Passing severity gates is not a claim of zero findings, and skipped browser cases are not passes.

## Angular source-distribution repair

The native lab job reported `GHSA-jfgx-762w-99vr` in `@angular/common` 20.3.33 and dependent `@angular/platform-browser`. The [Angular maintainer advisory](https://github.com/angular/angular/security/advisories/GHSA-jfgx-762w-99vr) identifies 20.3.34 as the patched Angular 20 release. Its upstream severity is High; the npm advisory data used by this run classified it Moderate, so the former `--audit-level=high` did not fail this job.

The HTTP-client flaw can attach an XSRF token to certain cross-origin URLs because its old regular-expression check disagrees with browser URL parsing. This original checklist preview does not use HttpClient, and its opaque-origin sandbox denies network access. No exploit of the getLancer application was demonstrated. Distributing the affected framework source still warranted a patch.

All five Angular runtime/compiler packages now use coordinated exact 20.3.34 pins. The npm lock, redistributed license version labels, catalogue version and native preview/source archives were rebuilt. The isolated Vue, Svelte and Angular `npm audit --audit-level=moderate` commands each reported zero vulnerabilities after the repair. CI now applies that threshold to these three source examples, and a regression checks the coordinated patched version, lock and distributed metadata. No finding suppression or scanner weakening was introduced.

Local native compilation was independently repeated with `node scripts/verify-framework-previews.mjs --native`, which verified the rebuilt bytes against checked-in material. The nine focused CI/framework regression tests passed. Candidate and eventual merged-main workflows must be inspected separately for their exact SHA.

## JavaScript/TypeScript SAST review

The full SARIF was downloaded from artifact `12596319821`; ZIP SHA-256 `bd499dafe45578b782f3d129c48820cdc367dd934d48b54ba338d1a5d1e27a99` matched the GitHub artifact digest. All eight results were read with source and data-flow context. No SARIF or applicability rule was changed by this review.

| Finding group | Source review and boundary |
| --- | --- |
| Two `js/bad-code-sanitization` results, score 6.1 | Test-only esbuild/mock source construction in `tests/browser/requirements-regressions.spec.ts` and `tests/data-ai-runtime.test.mjs`; callers supply repository literals and JSON-encoded synthetic values. No user-source execution path was established. |
| One `js/client-side-request-forgery`, score 5.0 | Trace from delivery URL parameters to `lib/api.ts`. The current engagement loader rejects non-UUID identifiers before issuing detail/payment requests and encodes the identifier. The API helper prefixes the same-origin `/api/v1` path. Existing malformed-ID browser regression covers path alteration. No attacker-selected origin or unauthorized endpoint request was established. |
| Two `js/file-access-to-http` results, score 6.5 | `tests/data-ai-qa.test.mjs` and `tests/data-ai-security.test.mjs` deliberately send synthetic local fixture bytes to a test-owned loopback runtime. The fixed destination is `127.0.0.1` with a locally allocated port and bounded request deadlines; these files do not ship as application runtime code. |
| Three `js/http-to-file-access` results, score 6.3 | Disposable CI fixture writers and native lab Replay recording in `scripts/ci-v1-fixture.mjs`, `scripts/smoke-docker.mjs` and `scripts/verify-lab-scenarios.mjs`. The destination paths are fixed local fixture/recording locations; the data comes from test-owned loopback services. This is intended test-evidence persistence, not an application file-writing API. |

These are scoped source-review conclusions, not proof against all future caller changes. The existing scan continues to retain the raw results and block high/critical findings.

## Workerd broken-pipe observation

The completed main logs contain five matching native `workerd` broken-pipe messages during frontend browser testing and seven during connected Docker browser testing. Both jobs and the subsequent database restore passed. `[WebServer]` identifies Playwright's frontend child, whose `createBuiltWorker` implementation runs Miniflare/workerd; this message does not by itself establish a Docker-daemon or Java-service failure.

The previously inspected native stack identifies a socket write after peer disconnection. Request/navigation cancellation remains a hypothesis for the exact trigger, not a reproduced diagnosis. This change does not filter the log, suppress errors or claim to fix the native runtime. A future failed request or child-process exit must be investigated using that failing run's full logs.

## Limits

Hosted KVM certification, seven actual elapsed days of admitted sessions, launch-environment load/mobile measurements, live merchant transactions, production object/database recovery, human screen-reader review and named sign-off require their own real evidence. Passing this CI baseline and native dependency repair does not close those gates.
