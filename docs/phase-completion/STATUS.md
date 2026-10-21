# Phase completion repairs and remaining acceptance

This work closes specific implementation gaps found while auditing V1 through V5.2. Code presence, automated verification and production acceptance are separate observations. Use the pull request's exact head checks and artifacts for the current result; historical phase documents describe earlier checkpoints.

| Gap | Implementation | Verification |
|---|---|---|
| Combined component filters | Language, framework, licence and execution mode compose with search/category; filters survive pagination and reset. Counts exclude private/draft records and use current creator authority. | Four named PostgreSQL regressions, demo/server filter tests and responsive browser checks. |
| Business lists stop at 100 | Retained workspace, member, invitation, brief, shortlist, saved-list, entry, proof and activity collections expose bounded continuation. Current tenant/role checks run on every request. | Large-collection PostgreSQL tests and continuation regressions. |
| Concierge work disappears behind completed requests | Actionable queue orders requested work first; fulfilled requests have separate paginated history, including closed briefs. Fulfilment tests the full shortlist. | More than 100 old fulfilled requests cannot hide a fresh request. |
| Team collections and lead notes grow without bounds | Public/private/moderation collections expose pagination; each lead has five note previews and a parent-authorized continuation route. One batched preview query replaces per-lead full-note hydration. | Large collections, note query count, tenant binding, demotion and expiry regressions. |
| Shared continuation feedback | Existing pager retains page/filter identity, retries failed pages, ignores stale requests after navigation, and announces loading/results/errors. Final completion preserves keyboard focus. | Browser component checks at desktop, phone and tablet widths. |
| Missing hosted provider implementation | Separate authenticated gateway, durable fenced runner, concrete Firecracker/jailer backend, bounded guest execution and offline build broker. Production admission stays disabled. | Python boundary/recovery tests and Java protocol compatibility. See [provider implementation and acceptance](PROVIDER.md). Real KVM validation remains required. |
| Original V1 browser coverage | Six original chains use the real Java/PostgreSQL/SMTP/object-storage stack, at three widths. | [Journey contract](V1_E2E.md); exact head connected CI is authoritative. |
| Stale requirement status | CI emits source/run/attempt-bound repair evidence and hashes the primary/supplementary artifacts of all 68 requirement rows. Named checks reject missing, skipped, duplicate and retried results. | Backend/connected evidence artifacts. File observations retain `clauseReview: REQUIRED`; they do not promote all 68 requirements. |

## Connected recovery candidate

The `8e8d1d8` connected run passed 23 cases and failed 13; encrypted restore was skipped after that failure. This recovery adds an exact configured storage origin to connected CSP, consumes subsequent confirmation links without stale responses, initializes report targets after hydration, preserves intrinsic dialog form rows, bounds phone workspace rows and opens fulfilled concierge history in its test. New navigation and CSP regressions accompany the repairs. These changes still require a successful full CI run on their own commit. Local Chromium crashes before launch in the recovery environment; local browser acceptance is not claimed.

## Preservation checks before merge

The merge review found and repaired three pagination regressions: public role application status now uses one authenticated lookup bounded to each displayed role page; repeated builder searches receive a fresh continuation identity; catalogue counts and filters read stored compact publication metadata rather than full source/archive JSON. Demo, PostgreSQL and browser regressions cover the repaired triggers. The full latest-head workflow must pass before merging.

Migration V46 adds stored generated metadata to `component_entries` and `component_releases`. It projects existing snapshots once, which rewrites those tables and takes migration locks. Before a future production rollout, measure this migration on a restored backup and plan the migration window. Reapply `ops/database/10_permissions.sql` after migration to grant the runtime only the pure projection function needed for source writes. This code merge does not activate a production rollout.

## Complete the remaining work

1. Run all candidate CI jobs. Review PostgreSQL regressions, six V1 journeys, responsive browser checks, SAST, dependency reports and restore evidence on the same head. Fix failures before merging. Backend evidence is `backend/target/phase-completion-evidence.json` in `phase-backend-evidence-<head>`; browser evidence is `test-results/connected/phase-completion-evidence.json` inside the connected artifact.
2. On a disposable Linux KVM machine, follow [PROVIDER.md](PROVIDER.md). Build and review immutable runtime/builder images, configure gateway identity and host resources, then exercise real launch, operation, timeout, cancellation, crash recovery and cleanup. Sign and admit only the reviewed artifact/environment combination through the existing manifest controls. Unit-test hypervisors do not supply that evidence.
3. Have the named owners review each FR/NFR clause in [the requirement register](../v52/requirements.json), using the exact head evidence and plan acceptance text. Record clause-specific findings and decisions. The register now uses `PENDING_CLAUSE_REVIEW`, separating semantic acceptance from CI execution status; a green file-existence check cannot close it.
4. Execute and record the seven independent launch gates below. The existing [V5.2 runbooks](../../ops/v52) and [launch-gate register](../v52/LAUNCH_GATES.md) remain authoritative.

| Gate | Required real evidence | Owner |
|---|---|---|
| L1 | Seven-day admitted-runtime soak, at least 99% infrastructure provisioning, including outages | Infra |
| L2 | Catalogue/API/startup/load and mobile web performance on the declared launch environment | Infra/QA |
| L3 | Isolated provider account, reviewed images, domain/TLS, egress denial, cleanup, resource limits and budget | Infra/Security |
| L4 | Merchant LIVE activation, provider reconciliation and lawful source agreements | Payments |
| L5 | Encrypted production restore plus withdrawal/rollback drill with measured RPO/RTO | Infra |
| L6 | Manual keyboard/screen-reader/200% zoom and real task-comprehension sessions | QA/Product |
| L7 | Clause-specific review of all 46 FRs and 22 NFRs, limited cohort signoff and named handover | Product/QA |

No provider deployment, live merchant activation, production admission signature or launch signoff is produced by this repair branch.
