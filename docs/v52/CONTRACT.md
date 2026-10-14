# V5.2 implementation contract

Authoritative scope: GetLancer_V4_5_to_V5_2_Full_Plan.pdf, pages 25–30, 52–55 and 71 (4 October 2026). Base: c1185c137227d2a55ec902e940a1c1b0fa2c64e6 (includes the subsequent evidence rebinding). Preserve V5.1 and subsequent security fixes.

| S08 item | Software outcome | Release evidence |
|---|---|---|
| B01 | Current 24 components, 18 core labs and 12 distinct projects, category/mode/licence/source/reproduction inventory | Clean package reproduction and hash checks |
| B02 | Consent-controlled discovery/source/profile events, authoritative run/inquiry/purchase aggregates; mode/version and explicit denominators | Schema/privacy/duplicate/empty cohort regressions |
| B03 | Preserve auth, private bytes, paid authority and isolation | Full backend/security/connected/native CI |
| B04 | Accessible responsive operator view, clear sample/unavailable states | Browser widths/keyboard/reduced motion; manual assessment tracked |
| B05 | Repeatable bounded load and real soak recorder/verifier | Actual declared environment measurements and >=7 elapsed days admitted-runtime evidence |
| B06 | Admission budget, restore/revocation/rollback drill harness/runbook | Actual restore/negative budget/capability tests and operator drill records |
| B07 | Independently fail-closed runtime and commerce activation checklist | Owner-controlled domain/TLS/runtime isolation and merchant LIVE evidence |
| B08 | Requirement traceability, ownership, handover and evidence validator | All required launch gates met before limited cohort approval |

API integration: backend leaf defines GET /api/v1/admin/operations (fresh MFA) returning a typed aggregate response; coordinate exact fields with frontend. Browser observation helper stays optional and consented; authoritative facts come from existing backend records. No arbitrary input or success event accepted. Evidence tooling uses bounded JSON, UTC times, explicit source revision/environment and negative controls. Restore never re-enables old execution capabilities. Pending deployment lanes remain disabled.

Software merge is permitted after final-head CI and review. The full V5.2 launch scope remains partial until real seven-day soak, production/provider prerequisites and any manual/research gates pass. No fabricated user research, availability or load claims.

Revision 2: runtime observations include the non-secret current operator epoch. A changed or missing provider epoch cannot certify a real soak across restored execution authority, even if cumulative counters happen to increase.
