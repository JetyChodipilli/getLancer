# Paper & Pixel security binding review

Reviewed 2026-10-29. The Paper & Pixel workflow adds changed-source ESLint, complete checkout history for that comparison, Chromium/Firefox/WebKit coverage, and retained design evidence. The existing backend verification, exact named GET regression requirements, evidence creation and same-run artifact consumption remain unchanged. No security gate, permission boundary, finding, rule descriptor, handler location or severity pin was relaxed.

The applicability inventory contains 235 production inputs, 100 test inputs and five build inputs. Independent byte comparison found exactly one stale entry: `.github/workflows/ci.yml`. Its reviewed SHA-256 is now `302c26707d34252c179febb3dd9062e7cd2d543198128db4fd5c6f8fb48537d6`. Every production/test digest, the other four build digests, and the complete input sets still match. The two OAuth callback handlers and administrator report-evidence handler retain their existing authority checks and nine mandatory named regressions.

Only that workflow digest and the review date changed in `docs/SAST_APPLICABILITY.json`. The existing 2026-11-05 expiry remains. Validator code, the three narrow findings and mandatory tests remain unchanged. The dependency applicability checker computes fresh input/report hashes during CI and has no stale frontend digest to refresh.

The prior candidate CI completed Maven verification before correctly refusing to bind the changed workflow. Final CI must rerun the real PostgreSQL regressions and bind their actual reports to the final commit, workflow run and attempt. Local fixture-based gate tests and static digest review do not substitute for that runtime evidence or certify live OAuth provider registration.
