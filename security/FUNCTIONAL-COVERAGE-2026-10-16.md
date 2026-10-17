# Functional and operational coverage review — 16 October 2026

Reviewed baseline: GitHub main 5daafa505ba3d4c324bcf53504dd3312583a4c42. This records bounded source/requirements review and verification limits; it does not certify every object lifecycle or a production deployment.

## What the application implements

getLancer is a proof-of-work marketplace connecting clients with individual builders, with consented team workspaces, private business hiring, delivery agreements, source commerce, maintenance, static previews and learning material. The seventeen original specifications are supplemented by phase contracts through V5.2. Historical V1 deferrals must be read alongside the later implementations.

| Area | Implementation and source | Evidence boundary |
|---|---|---|
| Accounts and marketplace | auth, products, inquiries, moderation, trust packages; discovery and workspace routes | Route policy and ownership/lifecycle regression suites; actual OAuth/email/provider activation needs environment configuration. |
| Teams and staffing | TeamService and TeamPolicy; accepted membership, five roles, recruitment, lead CRM, temporary access, owner-consented project proof | Current permissions are server-enforced. Expiry, role downgrade and child-object binding received focused review in this pass. |
| Private business hiring | BusinessRepository, ProjectRequestService, MatchingService, TalentListService, ConciergeService | OWNER/HIRING_MANAGER, private briefs, current public proof, saved talent and explicitly requested MFA concierge exist. Duplicate direct saves must not create repeated audit entries. |
| Delivery and payments | delivery, payments and commerce packages | Consented agreements, milestones, disputes, immutable ledgers and provider reconciliation exist. A configured enabled provider and merchant approval are independent launch requirements. |
| Maintenance | maintenance package, V4 contract | Paid-period admission, current parties, bounded requests, operator reconciliation and continued action on existing work are implemented. Current paid-period eligibility is distinct from retained work after expiry. |
| Contributions and education | components/education/hosting packages, V4.6–V5.1 contracts | Reviewed immutable source/releases, controlled downloads and isolated preview contracts exist. Source-only, illustrative, simulation and admitted-hosted modes must remain distinct. |
| Native learning material | labs source, native verifiers, V5.1 content inventory | 24 frontend components, 18 core labs and twelve original projects are the documented targets; CI reproduction evidence is separate from actual hosted isolation and physical hardware. |
| Operations and measurement | operations package, docs/v52/requirements.json, ops/v52 | Consent-aware observations, authoritative denominators, alerts, bounded load tools and soak/incident evidence validators exist. Tests of the validators cannot supply real production soak evidence. |

## Implementation is not complete production acceptance

The V5.2 inventory maps 46 functional and 22 nonfunctional requirements. In the inspected revision, all 68 software-evidence fields still say PENDING_FINAL_HEAD_CI; 28 rows reference an UNMET launch gate and 40 require review. File existence/structural traceability alone is not proof that a requirement is fully implemented. Earlier successful main CI is useful historical evidence and must not be represented as the result of a later patch.

The seven independent launch gates remain: seven elapsed days of admitted-runtime soak; launch-environment load/mobile performance; isolated provider/domain/TLS/network/budget approval; LIVE merchant activation and lawful agreements; actual encrypted restore and rollback drills; manual accessibility/task research; and reviewed limited-cohort handover. No deployment target, IAM credentials, real bucket policy, production secrets or operator approval was supplied for this pass. Millions-of-clients throughput and failover have not been demonstrated.

The original organization-owned product/structured contributor model remains an explicitly documented adaptation: projects stay individually owned and teams attach proof with that owner's consent. Do not silently replace that model while fixing security.

## Runtime modes

A configured BACKEND_URL selects real services before preview mode. Staging/production require a backend; unavailable upstream calls return errors rather than sample records. Preview adapters are isolated frontend exercises. Payment, recurring maintenance, hosting, lab admission, Data/AI, IoT and paid education are independently disabled by default. Default configuration does not establish an actual deployed flag or provider state.

Windows local Vite development now has a Node runtime path; production builds retain Cloudflare Workers. This makes local startup and streamed navigation testable while the native Windows workerd CreateDirectory failure remains unresolved upstream. The Node path refuses configured D1/R2 bindings and keeps missing-backend APIs unavailable.

## Remaining bounded-scope investigation

Business/request/talent collections use fixed latest-100 windows without paging, some team collections and per-lead notes grow without response bounds, and the oldest concierge window includes fulfilled work. These are real collection/functional scaling gaps. Retained financial/audit history must not be deleted or silently capped as a quick fix. Pagination, suitable indexes, bounded batch hydration and explicit UI continuation need implementation and regression evidence.

This review covers the cited domains and source paths. It is not an exhaustive object-level proof for every application route. The full Codex Security provider scan did not start: its snapshot loader could not inspect the V24 migration path. The separate focused tests, current-source review and GitHub security jobs must be reported under their own scope.
