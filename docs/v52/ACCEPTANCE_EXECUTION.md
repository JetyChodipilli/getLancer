# Production acceptance execution — 25 October 2026

**Status: local verifier checks executed; actual production acceptance remains UNMET.** The inspected source baseline is `16e45399478c630c819b0f82726c6ede42f5ec60`. Software acceptance changes are being developed separately; use the eventual deployed, clean `main` revision for all production measurements. This document is an execution record and handoff, not a signed release certificate.

## Observed environment

Read-only capability checks found the cgroup v2 controller file. `/dev/kvm`, Docker, Firecracker, jailer, `psql` and `pg_restore` are absent from this workspace. The externally configured policy, trusted public key and signed production bundle are also absent at the paths required by the existing qualification workflow:

- `/etc/getlancer-qualification/target.json`
- `/etc/getlancer-qualification/operator-public.pem`
- `/var/lib/getlancer-qualification/bundle-signed.json`

No actual app/gateway origins, production execution account, reviewed host images, production backup/recovery target, intended LIVE merchant account or named human acceptance participants were supplied for this execution. The example policy has blank production values and cannot substitute for those inputs. The qualification CLI was invoked with the documented external paths and current checkout revision; it exited `1` with `Production qualification is UNMET`, no standard output and no activation change. The in-progress checkout is uncommitted and is independently ineligible for exact-revision production qualification. The local inventory is not remote discovery and does not establish whether a production deployment exists elsewhere.

## Checks actually executed

```sh
node --test --test-reporter=tap \
  tests/production-acceptance.test.mjs \
  tests/v52-operations.test.mjs \
  tests/acceptance-verification.test.mjs \
  tests/acceptance-restore.test.mjs
```

These tests exercise the existing evidence verifier, bounded loopback collectors, forged/missing evidence rejection, source/hash/revision/epoch binding, actual-versus-compressed time checks, outage/denominator accounting and restore-binding validation. DNS is mocked for the sole accepted TLS timeout-test origin; invalid origins reject before network I/O. Restore tests validate synthetic records and immutable ZIP bindings without a PostgreSQL restore. None of these results is a hosted execution, elapsed seven-day observation, production load result, LIVE payment, production recovery or human sign-off. No additional preflight tool is needed: `scripts/qualify-production.mjs` already implements the required read-only host preflight and immutable-evidence verification.

## Exact remaining handoffs

| Requested outcome | Observed blocker | Required owner action and evidence |
|---|---|---|
| Hosted runtime qualification | No KVM device, Firecracker/jailer, selected production host or reviewed production inventory in this environment | Runtime/Security owner selects the dedicated host/account and installs the reviewed provider as described in [the provider runbook](../../ops/lab-provider/README.md). Boot and qualify all six pinned runtime images; retain isolation, network-denial, cgroup, hard-lease, orphan-denial and cleanup traces plus at least 100 fresh starts per adapter. |
| Seven-day soak | No configured deployed origin, admitted runtime cohort, MFA observer session, provider epoch or persistent observer | Infra owner runs the existing monitor for **604860000 actual milliseconds**, including a full cadence beyond seven days, with both immutable source lanes and authoritative admitted-run counters. Preserve failed samples. A changed revision/profile/epoch requires a new cohort. No clock manipulation or short CI journal closes this item. |
| Load and performance | No launch deployment, declared server profile, real eligible catalogue dataset or read-only query plans | Infra/QA owner runs separate component and college-project campaigns against the actual deployed revision: at least 10,000 eligible rows per catalogue, 20 requests/second, concurrency at most 10 and at least 60 seconds; retain request-local server p95 and bounded `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)` traces. Record actual mobile performance separately. |
| Production infrastructure | External policy/key absent; actual execution account, separate domains, TLS and budget approval unknown | Platform/Security owner supplies reviewed target policy and independent trusted public key, assigns owners/budgets and runs host preflight on the actual provider host. Device/TLS prerequisites alone do not certify isolation. Use the separate unprivileged evidence observer for final verification. |
| LIVE payments | No intended LIVE merchant account/approved transaction or finance operator supplied | Merchant/Finance owner executes the approved intended-account LIVE settled-payment, signed webhook/duplicate handling, refund and reconciliation lifecycle. Retain sanitized provider references and ledger/entitlement evidence; TEST callbacks remain software evidence only. |
| Recovery and rollback | No authenticated production backup, private object inventory, separate recovery target or reviewed rollback build supplied; local database tools absent | Database/Infra owner schedules the existing [restore/rollback drill](../../ops/v52/INCIDENTS.md), restores real encrypted database and object backups to the separate target, verifies financial/entitlement/source/outbox bindings, measures RPO at most 15 minutes and RTO at most 4 hours, rotates the independent provider epoch and proves old capabilities denied. |
| Human acceptance and handover | No actual screen-reader reviewer, real-user sessions, acknowledged operational owners or signed handover supplied | QA/Product owner performs keyboard, screen-reader, actual 200% zoom and five-width testing, at least five real task sessions, remediation, alert delivery and named operational acknowledgement. Jety records the limited-cohort release decision after the signed evidence and final-head CI pass. |

## Execute on the selected environment

Follow [the existing production acceptance contract](PRODUCTION_ACCEPTANCE.md) for exact policy, signatures, schemas and thresholds; follow [the collection runbook](../../ops/v52/README.md) for load, soak and signing commands. Complete its intentionally blank policy from actual deployed facts outside Git. Keep account credentials, session material, production data and signing keys out of source, command arguments and workflow logs.

On a **clean checkout of the exact deployed main revision**, first run the existing bounded read-only host preflight on the selected provider host. `ACTUAL_DEPLOYED_MAIN_SHA` below is an operator-supplied value, not the development checkout's SHA:

```sh
node scripts/qualify-production.mjs preflight \
  --revision ACTUAL_DEPLOYED_MAIN_SHA \
  --target /etc/getlancer-qualification/target.json \
  --out /var/lib/getlancer-qualification/preflight.json
```

Use an unused output path for each attempt; retain failures. After the actual hosted, load, soak, restore, merchant and human work is complete, the independently trusted operator reviews the immutable traces and signs the existing bundle. On the separate observer:

```sh
node scripts/qualify-production.mjs verify \
  --revision ACTUAL_DEPLOYED_MAIN_SHA \
  --target /etc/getlancer-qualification/target.json \
  --public-key /etc/getlancer-qualification/operator-public.pem \
  --bundle /var/lib/getlancer-qualification/bundle-signed.json
```

The manually dispatched [production acceptance workflow](../../.github/workflows/production-acceptance.yml) can run the same read-only verifier once the protected observer environment is configured. Do not dispatch it against this unconfigured workspace or substitute fixture keys/evidence. All seven [launch gates](LAUNCH_GATES.md) remain open. Runtime, commerce and hosting activation remain independent and unchanged.
