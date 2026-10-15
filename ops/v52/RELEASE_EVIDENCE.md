# Release proof schema and independent launch gates

Every report is tied to the expected deployment environment and exact reviewed Git revision supplied by the verifier. Reports have version `1`, collector `getlancer-v52-ops-1`, kind `LOAD`, `SOAK`, `DRILL`, `ACTIVATION` or `HANDOVER`, purpose `MEASUREMENT` or `TEST`, canonical UTC millisecond `startedAt`/`endedAt`, and either hashed sibling `logs` or fixed `controls`. Unknown fields, duplicate JSON keys and arbitrary payload text are refused. `TEST` is always excluded from release verification.

LOAD/SOAK manifests are produced from actual collectors. The first journal line contains environment, commit, origin, declared deployment profile, segment UUID, actual start timestamp and finite collector configuration. Each observation contains sequence, actual timestamp, monotonic elapsed, fixed sanitized probe/count fields, previous hash and its own SHA-256. The manifest binds the complete file hash; an Ed25519 envelope binds the manifest to the independently trusted operator key.

DRILL and ACTIVATION reports contain exactly their required controls. HANDOVER contains its own fixed controls plus four `reports` entries referencing one signed LOAD, SOAK, DRILL and ACTIVATION report by kind, sibling filename and file SHA-256. Release verification recursively verifies those independent reports against the same already trusted key, environment, revision and current time. Passing manual handover labels cannot bypass a failed load, soak, drill or merchant/provider gate.

## Fixed control attachments

Each control entry contains exactly `control`, `outcome` (`PASS` or `UNMET`), `owner` (accountable operator handle), `observedAt`, `file` and `sha256`. The sibling file is a bounded sanitized JSON proof, hashed exactly as stored:

```json
{
  "version": 1,
  "kind": "CONTROL_OBSERVATION",
  "purpose": "MEASUREMENT",
  "environment": "staging",
  "revision": "REPLACE_WITH_ACTUAL_DEPLOYED_40_CHARACTER_GIT_SHA",
  "control": "ADMISSIONS_PAUSE",
  "observedAt": "REPLACE_WITH_ACTUAL_CANONICAL_UTC_TIMESTAMP",
  "outcome": "UNMET",
  "method": "OPERATOR",
  "evidenceSha256": "REPLACE_WITH_RESTRICTED_ACTUAL_TRACE_SHA256",
  "facts": {"newAdmissionDenied": false, "cleanupContinues": false}
}
```

This is an intentionally invalid, unmet template. Replace placeholders only after performing the real procedure. The proof's environment/revision/purpose/control/time/outcome must match the signed report and control entry. `method` is `CI` or `OPERATOR`; production provider/manual claims require independently reviewed actual operator traces. Detailed logs are stored privately and reconciled by the operator using `evidenceSha256`; they are not exported into source or public evidence. Signing a fabricated trace cannot establish production readiness.

The executable fact inventory is `CONTROL_FACTS` in `ops/v52/evidence.mjs`. It defines exact field names, boolean targets and inclusive numeric targets. PASS is refused if any attached fact contradicts its target. UNMET entries remain honest and fail release verification.

| Report | Required controls and passing facts |
|---|---|
| DRILL | `BUDGET_STOP`: reservation denied, budget not exceeded, financial records retained; `ADMISSIONS_PAUSE`: new admission denied and cleanup continues |
| DRILL | `WITHDRAWAL`: new sales denied, listing hidden, historical entitlements retained; `RESTORE`: authenticated archive, counts match, RPO ≤900,000 ms, RTO ≤14,400,000 ms, old capabilities denied |
| DRILL | `CAPABILITY_REVOCATION`: old capability denied, fresh epoch and protected byte denial; `ROLLBACK`: pinned compatible build, paused admissions and retained financial/source records |
| DRILL | `CLEANUP`: zero stale workers/orphan reservations and confirmed cleanup; `FINANCIAL_SOURCE_RETENTION`: immutable ledger, retained source versions and correct entitlements |
| ACTIVATION | `DOMAIN`: verified DNS; `TLS`: valid certificate and HTTPS-only ingress |
| ACTIVATION | `RUNTIME_ISOLATION`: separate execution account, no host privileges, restricted network and hard lease; `RUNTIME_PROVIDER`: actual live provider, signed admission and pinned source hashes |
| ACTIVATION | `MERCHANT_LIVE`: approved LIVE merchant and LIVE provider configuration; `PAYMENT_CALLBACKS`: signature verification, idempotency and retained ledger |
| HANDOVER | `CONTENT_RIGHTS_REPRODUCTION`: 24 components, 18 core labs, 12 projects, reviewed rights and clean reproduction; `SECURITY_CI`: no blocking findings and passing final-head CI |
| HANDOVER | `MANUAL_ACCESSIBILITY`: viewport, keyboard and contrast assessment with no blockers; `TASK_COMPREHENSION`: at least five real task sessions, median first useful interaction ≤120,000 ms and completed remediation |
| HANDOVER | `ALERT_DELIVERY`: actual quota/stale-worker/orphan alerts received; `RUNBOOK_OWNERSHIP`: current runbook and acknowledged owners; `LIMITED_COHORT_APPROVAL`: signed-in cohort of1–10, separately approved runtime and commerce |

The task session count is the declared minimum study profile for this evidence format, not a fabricated result or statistical guarantee. Retain actual consented research records privately and summarize only aggregate timing/remediation facts. The assessment covers new V5.2 flows; it does not certify every legacy route.

## Activation and rollout

Runtime and commerce are independently fail closed. `APP_LABS_ENABLED=false`, `PAYMENTS_ENABLED=false` and `HOSTED_DEMOS_ENABLED=false` remain disabled until the respective existing provider/admission/merchant prerequisites are actually configured and verified. A readiness boolean or a green CI test is not production certification. These evidence commands never change those flags. The backend operator view presents configuration readiness separately from enabled state and production certification.

The runtime operator verifies real domain/TLS, isolated provider account, network/cleanup/restore/budget proof, current signed admission bundle, source/image pins and seven real elapsed days of an admitted cohort. The payments owner independently verifies LIVE merchant credentials/capabilities, HTTPS callback delivery/signature handling, test/live separation and lawful money reconciliation. Do not record a Razorpay TEST payment as LIVE merchant evidence. A pending lane remains disabled while free discovery/source browsing may launch separately as a clearly partial scope.

Before the limited cohort starts, assign actual operator handles for deployment/runtime, backups/on-call, merchant/payment reconciliation, security, accessibility/research and release approval. Require their acknowledgement, signed evidence, all gates met and current immutable CI. Start with at most ten signed-in runtime users under current global resource budgets; observe startup, cleanup, quota, budget and reconciliation before widening. Increasing cohort/capacity or changing a source/epoch/profile requires a new reviewed profile and evidence decision.

The initial implementation has no production seven-day soak, 10,000-row deployed load result, live provider/merchant proof, manual accessibility or real task study attached. Those gates are **UNMET**, with the actual owner assignment and next action tracked in the release ledger. Software merge is not a claim that the complete V5.2 launch scope has passed.
