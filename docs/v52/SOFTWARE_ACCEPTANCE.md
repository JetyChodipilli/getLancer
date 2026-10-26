# Software acceptance follow-up

The follow-up closes the retained admin collection, component setup/state and structured moderation implementation gaps. Actual production acceptance remains governed by [the seven launch gates](LAUNCH_GATES.md) and [the execution handoff](ACCEPTANCE_EXECUTION.md).

| Outcome | Implementation and observing checks |
|---|---|
| Retained admin disputes | Bounded OPEN-first pages with a deterministic ID tie-breaker; independent continuation, retry, focus, stale-response protection and current MFA. `AdminOperationsPaginationIntegrationTest` reaches and resolves a case beyond 200 open disputes; `admin-operations-continuation.spec.ts` exercises all three collection controls and revoked authority. |
| Seller accounts and payment attention | Existing typed `PageResponse` and shared database pagination replace unbounded lists. Each collection advances independently; an authority denial clears all protected records. |
| FR-008 component setup | All 24 ZIPs carry concrete startup instructions and a new immutable `1.0.1` version. `component-setup.spec.ts` independently extracts/CRC-checks downloads, compares every file and opens file/Python HTTP startup paths. Passing CI retains exact browser/Python versions and archive hashes; see [setup reproduction](../v46/COMPONENT_SETUP.md). |
| FR-006 component states | [The per-component matrix](../v46/COMPONENT_STATES.md) decides all eight states. `component-states.spec.ts` asserts actual focus, pointer feedback, applicable validation/loading/disabled states and resulting interactions at desktop, phone and tablet sizes. Inapplicable states have explicit reasons. |
| FR-012 moderation | Four private evidence assessments bind to the reviewed revision, full source/context hash and actual administrator. Failed/stale/unauthorized commands cannot publish. Append-only database history and current role/MFA protect the evidence. [Appeal restoration](../v46/MODERATION_ASSESSMENTS.md) retains exact-artifact provenance without inventing fresh assessments. |
| Dependency security | Angular runtime/compiler packages and redistributed examples move together to `20.3.34`; the original source is rebuilt into hash-bound archives. All three isolated framework audits must now reject moderate or higher findings. [The investigation](CI_SECURITY_FOLLOWUP.md) records the advisory and the earlier main CI evidence. |

## Rollout and verification boundary

Apply Flyway migration V50 before deploying the API. On hosted deployments, reapply `ops/database/10_permissions.sql` with the database administrator after migration and before admitting traffic. The new `component_review_assessments` table receives SELECT/INSERT only, private service RLS policies and immutable-history protection; the production startup guard rejects accidental mutation grants. The real-role regression exercises those privileges and direct browser-role denial.

Previously published database release snapshots are preserved. No historical assessments are backfilled. Existing clients submitting moderation commands must now send all four assessments; reload the current administrator UI before reviewing content. Existing free source policy and separate runtime/commerce/hosting activation gates are preserved.

The candidate requires passing current-head backend, browser, native-scenario, dependency, container and semantic-security checks before merging. Local source/type/package checks do not substitute for those jobs. CI records automated software behavior; it does not establish hosted KVM qualification, elapsed seven-day availability, launch load/performance, production infrastructure, LIVE merchant acceptance, production restore/rollback or human sign-off. No million-client capacity or zero-vulnerability certification is claimed.
