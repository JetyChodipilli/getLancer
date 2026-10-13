# Security, functional and capacity audit — 2026-10-13

This report records verified remediations and remaining validation work. It is **not a completed Codex Security scan, a no-vulnerabilities attestation, or certification for millions of concurrent clients**.

Reviewed remote baseline: `0bca656c091a878d907add5839275eb17e9672f6` (V5.1). Earlier broad checks used its V5.0 parent `fb7b1a7a733a30c73d63dbf48356d869dbb3ab45`; their results are separated below. Remediation was verified in an isolated copy of V5.1. GitHub publication preserves the existing tree and uses an expected-head lease, without local Git commits or ref updates.

## Scope and method

Applied gstack investigation/QA/security review, Ponytail Audit, Unlazy acceptance gates, Codex Security remediation review and Code Review CI diagnostics. Used codebase-memory structural queries with coverage checks and direct source reads for excluded or changed files. Local QA used Playwright when the installed gstack browser executable could not locate its server source.

Inspected authentication/MFA, public discovery and moderation visibility, maintenance billing/webhook reconciliation, source-download identities, Worker startup, dependencies, generated-material verification and deployment/test controls. Tests exercised synthetic accounts, captured local email, isolated PostgreSQL and payment fixtures. No real charges, hosted executions, customer data or production load tests were used.

Codex Security Deep Scan failed after three worker attempts with a provider HTTP 401 credential error. The independent Codex CLI review also failed with HTTP 401. Neither produced a successful scan manifest or review. Repair those credentials privately and rerun; this report must not be substituted for their output. The installed Code Review capability supplied CI diagnostics for merged V5.1 PR #45; those successful checks predate this patch. The publication's own GitHub Actions results must be inspected separately.

## Verified findings and fixes

| ID | Finding and consequence | Remediation and evidence |
| --- | --- | --- |
| ISSUE-001 | Windows Worker startup interpreted a file URL pathname as a native path, producing an invalid drive path. Generated backend/audit files also entered the development watch scope. | `fileURLToPath` resolves both server and asset directories. Generated directories are excluded from Vite watching. A real Worker regression loads assets from a path containing spaces, `#` and `%`. |
| ISSUE-002 | Database MFA issuance could be a few milliseconds ahead of the JVM observation; freshly authenticated administrators received false denials. | Production principals carry the database observation with issuance. The strict fifteen-minute window still rejects future, expired, missing and unverified states. Unit regressions plus privileged integration controls passed. |
| ISSUE-003 | Windows gzip OS metadata changed source-archive hashes even when every original source byte was identical, breaking reviewed download identities. | Pin the gzip OS header to the existing Linux release value. The analytics archive reproduces the reviewed SHA-256 `a4555b4211fb3915efff6b07ae1e6d075bec76a04638ceb0fb0789a94ab9a490`; clean extraction retains exact source bytes. Lab archives use the same compatibility rule. |
| ISSUE-004 | Maintenance evidence timestamps came from PostgreSQL but reconciliation compared them with JVM start time. Skew could exclude retained refunds or misclassify arrivals during provider discovery. | Capture an immutable database cutoff; use it for refund/dispute discovery, routing guards, freshness and processed-event updates. Keep the JVM clock only for existing elapsed-work budgets. Regression controls cover both directions of simulated skew. |
| ISSUE-005 | Locked sharp binaries included a vulnerable librsvg dependency. | Override sharp to `0.35.5`; installed binaries report librsvg `2.63.2` and successfully transform a small SVG. Advisory counts fell from twelve to eight high affected packages. This is a dependency fix; a deployed application RCE was not demonstrated. See [the upstream advisory](https://github.com/advisories/GHSA-wq5f-xc86-pv6w). |
| ISSUE-006 | The Python verifier's precise wall clock could lead JavaScript's clock, causing a valid generated recording to fail as predating execution. | Wait, with a two-second monotonic deadline, until the observer actually reaches completed execution time before recording the current timestamp. Strict timestamp validation remains. The complete V5.1 build passed. |
| ISSUE-007 | Webhook insertion used transaction-start `now()`. A transaction begun before cutoff could persist later with a backdated timestamp. Separate finalization statements also allowed an inbox write between evidence checks and bulk acknowledgement. | Event persistence, cutoff capture, transfer reservation and finalization share a short database advisory fence. Insertions receive `clock_timestamp()` after acquiring it. Provider calls remain outside locked transactions. A delayed-transaction regression failed before the fix; the repaired path preserves the event as pending. Another regression observes an actual blocked PostgreSQL advisory waiter during finalization. |
| ISSUE-008 | Financial freshness compared database reconciliation time with JVM time and accepted future observations. | Compare both freshness bounds with database time and continue denying pending inbox evidence. A future observation was accepted by the old implementation and rejected after remediation; fresh and expired controls pass. |
| ISSUE-009 | Public search joined full-text and substring matches with OR, preventing useful independent access paths and repeatedly evaluating full-text expressions. | Union matching product IDs in independent branches, retaining the outer public visibility, moderation and account gates on both count and result queries. Integration tests cover full-text, title substring, builder name, duplicate matches, filters, private/NDA/archive/pending projects and suspended owners/profiles. Measurements below show improvement for a selective query. |

The supplied React Server Components “Network connection lost” stack describes a broken response stream; it alone does not identify its server-side cause. Worker path failure is reproduced and fixed. Watch exclusions reduce interference from generated output. Successful production startup and browser checks do not prove that every possible development-server disconnect has the same cause.

## Verification evidence

Final V5.1 candidate:

- Production build and TypeScript check: passed.
- Focused backend package/integration checks: **89 tests, zero failures, zero errors**, covering MFA, billing clocks, inbox ordering and public search.
- Archive and real Worker portability regressions: **2 passed**.
- Browser suites `v51`, `components`, `education` and `spectral`, across desktop, phone and tablet: **101 passed, 4 skipped, zero failures**, 358 seconds. This rerun used the expected port and Python runtime; it resolved the earlier browser failures.
- Source security contract: **34 controllers, 116 typed JSON handlers, 286 explicitly classified routes**. These are inventory/contract counts, not a claim of exhaustive vulnerability freedom.
- Captured local email/auth journey: signup 201; verify/login/profile 200; ordinary-user admin access 403; logout 204; subsequent profile access 401. Eight checks passed.
- Complete Windows Node suite: **309 tests, 257 passed, 51 failed, 1 skipped**. It is not green. Failures include POSIX filesystem/locking assumptions, unavailable symlink privileges, path-separator expectations and timing/extraction checks. All failures require Linux CI review or individual reproduction before dismissal.

Publication validation for remediation commit `ed1e897a6fbb6b27b9646bb216025da8d7021ac9` ran the full Linux backend: **541 tests, zero failures/errors/skips**. Its native/container lab job passed, as did the complete Linux Node suite, build and typecheck. The backend job then correctly rejected the stale GET-review inventory, including the two added regression files. The three existing GET assessments were re-read against the changed authorization implementation; only reviewed production/test/build hashes and the added test inventory were refreshed. Finding rules, regression requirements and expiry remain unchanged. The inventory helper now emits repository paths consistently on Windows and Linux. **34 OAuth/admin GET backend regressions and seven manifest positive/negative tests passed**, and the binding command accepted the real reports. The follow-up commit still requires its own same-run CI evidence; GitHub Actions for the latest main head is authoritative.

Earlier V5.0 coverage, not certification of V5.1:

- Full browser run: 399 passed, 8 failed, 4 skipped. The failures included an incorrect test-runner origin and missing Python setup; subsequent V5.1 reruns of the affected suites passed.
- Full backend rerun with CI pool/timeouts: 522 tests, one failure and one Windows symlink error. The maintenance failure was addressed; affected suites were rerun on the final candidate. The complete final Linux backend run belongs to publication CI.

No local Docker/native Go/PHP/.NET matrix was executed. V5.1 browser IoT and framework checks ran; complete native and container execution still depends on the repository's Linux CI. Object-storage upload and real merchant activation were not end-to-end certified locally. Preserve existing activation restrictions until those environments are verified.

## Capacity measurements and limits

An isolated PostgreSQL database contained 100,000 synthetic users, 10,000 approved builders, then 100,000 and 1,000,000 synthetic public projects. No customer records were copied. A selective `99999` search used the same data and schema for both query variants, one warmup each and five alternating `EXPLAIN ANALYZE` observations. Raw samples are in [the capacity evidence](2026-10-13-capacity-evidence.json).

| Synthetic projects | Existing count query median | Revised count query median |
| --- | ---: | ---: |
| 100,000 | 975.840 ms | 51.071 ms |
| 1,000,000 | 7,032.032 ms | 344.217 ms |

These are selective SQL execution measurements on one Windows machine, not end-to-end concurrent user throughput. The synthetic schema also contained a separately tested partial created-date index; it was present for both variants and is not added by this patch. The index experiment improved one first-page observation but worsened deep offsets, so it was not promoted into a production migration.

A bounded, separate empty-catalogue HTTP check observed 100 reads at concurrency ten, all 200, p95 173 ms; twenty invalid logins at concurrency ten all returned 401, p95 3,231 ms. These small controls do not establish production traffic capacity.

Before claiming millions of clients, establish an actual concurrency/SLO target and measure representative common searches, private/public ratios, deep pagination, sustained login CPU, uploads, email/queue drain, webhook rates and multi-replica connection usage. Title/builder substring branches still scan; exact counts and OFFSET still become expensive. Evaluate keyset pagination and indexed substring search against real workloads before changing public contracts. The inbox fence serializes short maintenance writes and needs its own contention measurement. Size total connection pools across replicas against PostgreSQL limits and verify backup restoration, tenant isolation and failover in the intended deployment.

## Remaining dependency and audit work

`npm audit` still reports **eight high affected packages from the braces advisory chain**, not eight distinct application exploits. The [braces advisory](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm) affects deeply nested glob patterns; no fixed published braces version was available at inspection. Relevant consumers include lint/build tools and vinext plugin dependencies. A request-to-pattern exploit was not demonstrated. Do not use npm's suggested major downgrades as an unverified fix; rerun upstream checks and replace or upgrade affected tooling when a compatible remediation is available.

Some adjacent auth token/challenge/session expiry paths still combine JVM-created or JVM-observed time with database timestamps. They were not demonstrated as exploitable in this audit. Keep clocks synchronized and review those lifetimes with controlled clock tests as follow-up.

Unlazy retains an explicit failed-scan handoff instead of declaring all required gates complete. Restore the security provider credential privately, rerun the independent scan/review, inspect final GitHub CI, reproduce remaining platform failures, and perform deployment-specific capacity validation before issuing a full security or scale-readiness statement.

## Ponytail Audit

Report-only simplification candidates; no speculative architecture replacement was made:

- `db/schema.ts` and `drizzle.config.ts`: the empty SQLite/D1 starter is separate from the active Spring/PostgreSQL backend; consider removing it and the unused `db:generate` path after checking future D1 intent (four schema lines, seven configuration lines, one script line).
- `package.json`: that removal could also remove `drizzle-orm` and `drizzle-kit` (two dependency declarations).
- `ProductService.search`: keep the existing service/repository boundary and batched media lookup; the measured query change avoids adding a separate search service.
- `MaintenanceRepository`: reuse the existing advisory-lock mechanism for the demonstrated race; avoid holding provider HTTP calls inside database transactions.

Potential starter cleanup: fourteen lines and two direct dependencies. **Actual cleanup in this patch: zero lines/dependencies deleted for Ponytail findings.** The correctness, dependency and portability changes above are remediations, not a complexity-reduction score.
