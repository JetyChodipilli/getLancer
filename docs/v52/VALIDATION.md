# V5.2 software verification

Candidate integrates main 6c489296207077f334b9b38ed73391234107b9b2, including its API credential/privacy security fixes. Final immutable CI identity is recorded by the pull request checks.

Local verification on 14 October 2026: production build, TypeScript, security source contract and complete Node suite pass (347 tests, zero failures/skips on the final integrated rerun; 45 focused evidence/content/SAST regressions also pass). The content verifier independently reproduced twelve clean source packages: three each full-stack, analytics, AI/ML and IoT, with 24 frontend components and 18 core lab inventory entries. Native execution is separately gated in CI.

Independent backend, frontend and operations reviews completed. Fixed mixed-origin soak evidence and preserved delayed capacity skips as failed load outcomes. Re-read all three existing GET SAST assessments; only the admin report handler location and inspected source/test/build inventory changed. Rules, expiry, required regressions and same-run evidence binding remain enforced. No new service or dependency was introduced.

Local Java/PostgreSQL, Docker and actual browser execution were unavailable: no Maven/Postgres/Docker installed and Chromium downloads were truncated. Full backend, browsers, connected services, native/container, semantic/secret security and restore checks must pass on the final CI head before merge. The initial Node run overlapped a build and read incomplete output; rerunning after the completed build passed all 347 tests.

Software completion does not certify launch. All seven production/manual gates in LAUNCH_GATES.md remain unmet, including actual seven-day soak, production load, provider isolation/activation, merchant LIVE, restore/drills and user/accessibility research. Runtime and commerce lanes remain separately controlled.

Initial publication was blocked by automatic approval review. The user subsequently confirmed the repository and payload, and PR #46 was published. The PR checks are authoritative for final-head CI; main remains unchanged until all required checks pass.

CI follow-up on 15 October: resolved two backend contract regressions and six stale catalogue browser assertions. A syscall trace identified serviced .NET 8.0.32 user-scoped mutex startup in a scrubbed non-root environment. Verifiers now provide owner-private disposable XDG runtime/data directories; readonly root, noexec generic temporary files, non-root user, network isolation and dropped capabilities remain. Temporary strace diagnostics were removed. Final-head CI is still required.

The analytics historical-row fixture uses a separate committed transaction before HTTP requests and restores its original row in finally; it does not hold an outer transaction across request rate-limit filters.

The JavaScript scan identified two file-path check/read races in the original study-planner and campus-poll local examples. Both now inspect/read a single descriptor with bounded actual bytes, refuse nonregular files, and close reliably; Linux symlink and oversize controls plus persistence/restart tests pass. Regenerated packages and catalogue bindings preserve reviewed rights/source hashes. Operator-supplied data paths must still be private, as documented. Semantic analysis must rerun on the final head.

Main integration on 15 October preserves the historical review timestamp fix and V5.2 analytics fixture restoration. The operations migration is V36, following main's immutable V35 legacy identity migration. All 36 versions are unique. Independent backend/operations review, all 347 Node tests, 12 focused SAST tests and source/traceability checks pass locally; all 292 reviewed production/test/build hashes match the combined tree. The existing three GET assessments, required tests, severity rules and expiry are unchanged. Full combined-head CI remains required.
