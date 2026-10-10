# V4.9 measured validation

Validation date: 2026-10-10. Worktree: `codex/v4.9-college-commerce-20261010`, based on V4.8 main `da530f7048c21f48a7c18aedece60b5d158bbc9e`. These are fresh local results for this implementation; unavailable earlier workspace runs are not counted.

## Behavioral verification

| Check | Fresh result |
|---|---|
| Existing source archive + education commerce integration | 20 passed, 0 failed, 0 errors, 0 skipped; independently rerun by parent |
| Account export, request validation, architecture and actual database-role guard | 19 passed, 0 failed, 0 errors, 0 skipped |
| Four original programs, deterministic ZIP material and education interfaces | 9 passed, 0 failed |
| Education releases and report/privacy integration | 18 passed, 0 failed, 0 errors, 0 skipped; independently rerun by parent |
| Full backend `verify` | 498 passed across 46 JUnit reports; 0 failed, 0 errors, 0 skipped |
| Production frontend build / typecheck | Passed, including parent exact build/program re-verification |
| Full Node regression suite | 220 passed; 0 failed, 0 cancelled, 0 skipped |
| Education responsive browser journeys | 45 passed across three viewports, 0 failed, 0 retries, 0 skipped; independently reverified after the final build |
| Full desktop / phone / tablet browser regressions | Full command exit 0; 360 selected = 356 passed + 4 existing desktop-only skips; final result `passed`, no failed tests |
| Frontend asset scan, typed route contract and database permission source coverage | Passed: 34 controllers, 116 typed handlers, 286 classified routes, 107 migration tables |
| Production JAR and exact reviewed SAST input/test evidence | Passed: runtime-only JAR; exact local evidence binds 208 production, 55 test and 5 build inputs |

The Java integration tests use PostgreSQL 16 and the repository's destructive-test database guard against a fresh disposable database. They exercise migrations V1–V34, actual SQL constraints and immutable triggers. Runtime role tests exercise real restricted database grants, including education package/audit history. Commerce tests use the existing concrete provider HTTP fixture; education release tests mock storage/provider boundaries while checking real authorization, database and source-inspection behavior. The optional-profile positive case scopes a deterministic public DNS response through Mockito's per-mock inline maker while retaining the real URL/address validation. This constrained local runner preloads the existing Mockito test agent; production code, dependencies and DNS behavior are unchanged.

The browser configuration reproduces the checked-in desktop (1440×1000), phone (375×812) and tablet (768×1024) projects, one worker and no retries. A freshly built production Worker runs in explicit illustrative mode. API-dependent education journeys use explicit HTTP fixtures; these are distinct from the real Java/PostgreSQL tests. The local runner uses official Chrome headless shell 145.0.7632.117 with default Playwright settings plus `--no-zygote` and `--in-process-gpu`. The local temporary executable path is not committed, and CI retains its standard Playwright Chromium installation.

## Review and evidence boundaries

The focused checks cover immutable source/license/support packages, historical FREE downloads, exact PAID release/source/price binding, stale revisions, current contribution/team/component visibility, source-only/external/hosted availability, artifact reports, private moderator notes, ownership-bound exports, academic consent and approved anonymization. Original examples execute from their supplied files and regenerate byte-identical ZIPs.

The parent inspected fresh [desktop discovery](screenshots/college-desktop.png), [phone package disclosures](screenshots/package-phone.png) and [phone wizard/focus](screenshots/wizard-phone.png) captures. Focused axe checks and browser assertions cover serious/critical issues, keyboard focus, 320 px reduced-motion reflow, readable filters and error/denial states; this is focused acceptance evidence.

The broad browser command used all 19 test files and the three configured projects. Its final process result was exit 0 and its `.last-run.json` recorded `passed` with an empty failure list. The live text log was partial; its stopped progress did not indicate a test failure. The four existing skips are the two Spectral viewport/orbit checks on phone and tablet; the desktop project runs their complete shared matrix.

The existing SAST assessment keeps all three finding definitions, mandatory passing security regressions and its existing expiry. Its source inventories are refreshed for the current implementation. Local evidence is explicitly identified as a worktree run; it is not a new remote CodeQL scan or GitHub Actions result.

No Docker deployment, live hosted provider, field performance result, whole-application accessibility certification or live merchant readiness is inferred from these tests. Paid college collection stays disabled by default. The production merchant/provider gate remains pending and is described in [README.md](README.md).

No dependency or lockfile was added. Optional unrelated Ponytail audit findings remain report-only in [REVIEW.md](REVIEW.md).

## Reproduction

The normal repository checks remain `npm run build`, `node --experimental-strip-types --test tests/*.test.mjs`, `npx tsc --noEmit`, `npx playwright test`, the three frontend/security/database source scanners, and `mvn -B -f backend/pom.xml -Djunit.jupiter.execution.timeout.default=60s verify` with the documented disposable PostgreSQL test settings. This local sandbox uses an external PostgreSQL wrapper and the already-installed Mockito 5.17 test agent to reproduce those Java checks. Temporary runner paths and test database shim are not application changes.

All three child acceptance ledgers were independently reverified, including after the final integration corrections. Their leases and dispatch returns are complete. C1–C14 code acceptance is closed; the separate live merchant/provider gate remains pending. This report records local acceptance before publication; the pull request must also pass its current GitHub Actions checks before merging into `main`.

## GitHub CI follow-up

[PR #43](https://github.com/JetyChodipilli/getLancer/pull/43) publishes the same tested file tree. Its initial CI run confirmed 498 backend tests with no failures, errors or skips, production JAR isolation, same-run reviewed security-test evidence, dependency/container scans, JavaScript semantic security and native/Docker lab reproduction. Java CodeQL identified the shared public download/operator inspection call path as a new GET/CSRF finding. The implementation now separates public read-only package retrieval from the administrator POST audit write; its existing inspection/CSRF/MFA regression also verifies that public downloads create no audit records. The source inventories retain the original three dispositions, mandatory tests and expiry. Current GitHub checks on the PR head remain the merge gate.

After this correction, the parent reran all 18 education integration cases and six security/backend architecture cases against fresh PostgreSQL: 24 passed, no failures, errors or skips. The 286-route source authorization contract and `git diff --check` also passed.
