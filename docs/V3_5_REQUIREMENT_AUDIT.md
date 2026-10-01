# V3.5 requirement audit

All **17 numbered documents** were read fully and compared with the Java/PostgreSQL/frontend source at baseline `24c2d046207878b6d12a106a2444dcdce60a789f`. This is a source audit, not live payment, production readiness, legal or WCAG certification. The baseline mapping remains immutable evidence. Root integration dispositions below are recorded separately; acceptance results are in `V3_5_ACCEPTANCE.md`.

The machine-readable companion retains **479 requirement groups**, **50 existing FR/NFR/E2E identifiers**, and a disposition for every one of **5592 nonblank source lines** across **5628 lines**. Repeated requirements across documents are traceability duplicates; these counts are not a percentage of completed features.

| Baseline disposition | Groups |
|---|---:|
| implemented | 262 |
| partial | 133 |
| missing | 3 |
| deferred-owner | 74 |
| deferred-future | 7 |

`implemented` means inspected code behavior exists, with source test references. `partial` includes mixed or adapted requirements. `missing` means absent current-phase code. `deferred-owner` requires actual operator/provider/measurement acceptance. `deferred-future` preserves intentional roadmap scope. Test references are not fresh test pass claims.

## Actionable findings

| ID | Severity | Baseline finding | Required disposition |
|---|---|---|---|
| GAP-01 | high | Hosted frontend Content-Security-Policy blocks Razorpay Checkout | Review official gateway CSP and permit the exact lazy Checkout script/frame/connect origins; preserve frame-ancestors and validate Worker-header behavior. |
| GAP-02 | high | Personal workspace retains stale protected records after failed refresh/session revocation | Clear personal/admin state on failed protected refresh; provide labelled login/retry and negative revoked-session regression. |
| GAP-03 | medium | Public policy wording describes only V1 external agreements/payments | Update draft notices for recorded agreements, provider Route payment boundary, source license/version, entitlement and refund/dispute workflow without claiming legal approval. |
| GAP-04 | medium | Legacy inquiry/moderation history has no database append-only guard | Add new migration guarding UPDATE/DELETE of inquiry_events and moderation_actions; preserve prior released migrations and add mutation-rejection test. |
| GAP-05 | medium | V3.5 commerce missing from baseline main | Incoming commerce leaf: reviewed source package, manifest/README/LICENSE, ownership/rights, bounded archive and secret/executable checks, private storage/hash, snapshot/license purchase and live entitlement, provider refund/dispute/MFA recovery, scoped export/retention, connected UI/API/database tests. |
| GAP-06 | medium | Analytics collection exists but required operational dashboards are incomplete | Add real aggregate APIs/UI for current approved builders/showcases/proof coverage, response/hire/completion conversion, repeat clients, slot utilization/events, reports/severity/appeals/reversals/IP/link incidents. Liquidity needs stored search result-count cohort and consent-aware event limits; do not fake statistics. |
| GAP-07 | medium | Public search GET lacks a dedicated configured rate limit | Throttle bounded public discovery reads using trusted client identity and configured generous read limit; preserve per-user mutation limits and tested Retry-After. |
| GAP-08 | medium | Original six journeys are service-smoke verified, not all independently browser driven | Preserve actual browser V2.5/V3 journey; add full browser onboarding/publication/slots/inquiry/outcomes/review/moderation as coverage allows. Never equate service HTTP smoke or mocked preview with all six browser E2E. |
| GAP-09 | medium | Locks exist; whole-repository vulnerability/license/SBOM verification is not configured | Add meaningful dependency/secret/static/license checks or record exact existing checker evidence; operator must decide license compatibility and patches. Do not claim a lockfile is a vulnerability scan. |
| GAP-10 | low | Optional showcase structured price modes are represented only by free-text pricing context | Implement mode/minor min/max/currency and client-readable display if keeping original optional pricing contract, or explicitly approve/document the narrower free-text adaptation. V3 milestone and V3.5 checkout amounts are already distinct and must stay server selected. |
| GAP-11 | low | Teams share individually owned proof but do not own products or structured contributor records | Document individual ownership adaptation. Real team ownership/contributors requires a scoped authorization/consent/data migration; do not invent ownership or weaken existing consent to close a schema label. |
| GAP-12 | low | Public team routes are absent from sitemap generation | Include only genuinely public eligible team pages under indexing gate, with private/suspended exclusions and metadata tests. |
| GAP-13 | owner | Production/nonfunctional acceptance is not code completeness | Owner: real OAuth/email/S3/TLS/alerts, daily/PITR/object backups, measured RPO/RTO and load, Firefox/Safari/Edge and screen-reader/contrast WCAG checks, legal/contacts/retention/vendor/age policies, curated cohort and live provider acceptance. Current user permits demo frontend hosting only. |

## Current implementation disposition

Baseline counts are not a completion percentage. Mixed source sections retain their original clauses. Code resolutions follow; CI and production acceptance are separate evidence.

| Gap | Current disposition | Evidence and limits |
|---|---|---|
| GAP-01 | Implemented; live provider acceptance pending | Connected-only Checkout CSP, lazy script loading and Worker header checks; demo admits no gateway origins. |
| GAP-02 | Implemented | Protected personal/admin refresh failures clear records; revoked-session connected regression. |
| GAP-03 | Implemented draft; owner approval pending | Current agreement, Route, source license, privacy and retention notices. |
| GAP-04 | Implemented | V17 append-only inquiry/moderation history; real database mutation-denial tests. |
| GAP-05 | Implemented; live provider acceptance pending | Java source marketplace, V16 private artifacts, immutable purchases, MFA review/recovery, refunds/disputes, export and connected UI. |
| GAP-06 | Implemented with prospective cohorts | Real aggregate dashboard; V20 capacity observations and structured report decisions; consented recommendation/result-count samples. Legacy unrecorded durations/decisions remain unknown. |
| GAP-07 | Implemented | Configurable trusted-identity discovery throttle and Retry-After negative controls. |
| GAP-08 | Service coverage plus selected browser coverage | All six original real service journeys stay in Docker smoke. Connected hiring/delivery and source/revoked-session browser flows run at three widths. Not every service journey is independently browser driven. |
| GAP-09 | Implemented inventory/scan; operator license review pending | Runtime CycloneDX and OSV CI, npm audit, dependency remediation and JAR test-fixture exclusion. Declared licenses are inventory, not legal approval. |
| GAP-10 | Implemented | V18 optional showcase pricing modes/currency/minor amounts; server/SQL validation; frontend display/forms and precision tests. No showcase checkout. |
| GAP-11 | Explicit model adaptation remains | Teams share consenting members’ individually owned proof. Source sellers are individuals. Organization-owned products and structured contributor entities require an additional ownership/consent design and are not implemented. |
| GAP-12 | Implemented | Indexing-gated eligible public team/template sitemap using real team UUID routes. |
| GAP-13 | Owner acceptance pending | Real provider/OAuth/email/storage credentials, production hosting, observed backups/load/alerts, policies/KYC and broad accessibility/browser acceptance. Only frontend demo hosting is authorized. |


## Full-stack phase check

| Phase | Baseline source evidence | Limit |
|---|---|---|
| V1 | Auth/profile/product/media/inquiry/review/admin APIs; V1–V10 schema; public/workspace pages; MarketplaceIntegrationTest and service smoke | Original six workflows have service/API coverage. All six are not independently browser driven; hosted providers/manual accessibility remain pending. |
| V1.5 | TrustService, DemoHealth, V11; availability/repository challenge/demo health/similar builders/manual earned capacity UI | Narrow fact labels only; no identity/security/runtime warranty. |
| V2 | TeamService/Repository/Policy, V12; recruitment/staffing/multi-team/CRM UI; TeamsIntegrationTest | Shared proof remains builder-owned; organization-owned products/contributor entities are an explicit conceptual-model gap. |
| V2.5 | Business/Matching/Concierge services, V13; business workspace, saved talent and private briefs; BusinessIntegrationTest/connected browser | Real approved/current evidence, tenant isolation, recipient consent and MFA concierge; owner provider staging still pending. |
| V3 | Delivery/Payment services, V14/V15; proposals/consents/agreement/milestones/disputes/payment UI; DeliveryIntegrationTest/PaymentIntegrationTest | Live Razorpay remains owner pending. Hosted CSP must permit legitimate Checkout; test captures never count as live completion. |
| V3.5 | Absent from baseline. Incoming work defined by V3_5_CONTRACT.md | Must inspect final archive/manifest/rights/review/private storage, immutable license purchase, provider captures/refunds/disputes/recovery, buyer entitlement/export, connected UI and test results. |
| V4+ | No arbitrary source execution, hosted developer code, deployment/maintenance subscriptions, paid capacity, AI matching, native apps or microservice fleet | Deliberately future; adding these to make a checklist green would contradict the roadmap and demo-only hosting rule. |

**Connected V3 browser correction:** `tests/connected/business.spec.ts` contains the real delivery flow within its one hiring test, repeated at desktop/phone/tablet. It creates a business request, agreement with explicit seller/buyer consent, submit/revision/acceptance, disabled-payment completion denial, dispute and MFA resolution, and revoked-manager denial. Three test results do not imply V3 browser coverage is absent.

## All document identities

Current text hashes below are freshly computed. The original Word hashes are inherited from `SOURCE_DOCUMENT_VERIFICATION.json`; this audit did not re-download/recompare the original binaries.

| Document | Text SHA-256 | Lines | Requirement groups |
|---|---|---:|---:|
| docs/00_MASTER_PRD.txt | `77f96bc61d5a4e2daaf855b5f534f9831c49c71008df33e4f969ee19f1146e23` | 530 | 60 |
| docs/01_VISION.txt | `e73ddf98e954c9fc1df002a141c4ee900f3bd4a0946be1a9410cae30963121bc` | 227 | 15 |
| docs/02_FUNCTIONAL_REQUIREMENTS.txt | `c246691b1b8fcff6364bd394d2ca89bed5a1b0f0dcc37637298e035c6fe38765` | 457 | 55 |
| docs/03_NON_FUNCTIONAL_REQUIREMENTS.txt | `1eb1dc8aaa0f29a506c766d84f4270eba8cef35461222691ded56e4a6ebc8f86` | 362 | 41 |
| docs/04_BUSINESS_RULES_STATE_MACHINES.txt | `d3fbc63af408e0fb54765c9116b1976180bb74414707e362484c151e450fbfb0` | 258 | 20 |
| docs/05_ARCHITECTURE.txt | `3f7c4803a7a79170834749e2f01589b439f63dadead876d7166934d5bee47fdc` | 442 | 21 |
| docs/06_API_CONTRACT.txt | `1b2eede4a3a8e733ad427dc83ba9f002b4a8aa66676ac9f973746f4164a988ee` | 323 | 24 |
| docs/07_DATA_MODEL.txt | `6620be59bdbaf1ade8aca4783d1bdeab0bd7fd04077b05665c9a2c9cb7aa5468` | 514 | 35 |
| docs/08_UI_UX.txt | `8d4bb6fe5efb2524f1be6239fa342b0bb7c305253cad2ff3bbae0985598af527` | 406 | 21 |
| docs/09_SECURITY_THREAT_MODEL.txt | `9bc70b9c41d93eae633cfb567cacc7e4a0204db60eb324c6e8ce2f5a0f82550b` | 297 | 27 |
| docs/10_TESTING_QA.txt | `3e7805247b265691721aa5711326bbc1f3700e56bb189b70ed235216a532758b` | 263 | 26 |
| docs/11_ANALYTICS_MEASUREMENT.txt | `01909560aae4f753c797fe30c3977a609b2895a05b74afb1315c74a492ab1fc3` | 250 | 17 |
| docs/12_TRUST_SAFETY_MODERATION.txt | `6e2e76ab927b6f897fc2571c85631e2994e5edb74b46375fb6bdcd4ed0cc098b` | 243 | 24 |
| docs/13_OPS_DEPLOYMENT.txt | `f30604a16a66aacf89e518c2942af18fb095ec6a6c9aead371e5933092029447` | 312 | 26 |
| docs/14_LEGAL_COMPLIANCE.txt | `2bbd90f12a5e571405d986674bae4022739ef27b727f4ce0af3a6ccb1e4abbc1` | 314 | 26 |
| docs/15_SEO_METADATA.txt | `17e89ee872af57fd70c3270c66d31ab25b08524e7711084c93e4a2fe9db4fbe8` | 253 | 24 |
| docs/16_ENVIRONMENT_CONFIG.txt | `a9c28e79b02538b9831db393767cfc9d33b234e9e60f7fd15cd7a85d0f15758b` | 177 | 17 |

## Requirement traceability

Each row links a stable source section or existing requirement identifier to a full-stack evidence domain below. Exact source clauses/line spans, path references, statuses and gap IDs are in the JSON; no source section is silently omitted. A partial row must be read with its domain limitation and actionable findings.

| ID | Source requirement | Baseline | Evidence domain |
|---|---|---|---|
| DOC00-1.1 | Purpose | implemented | core |
| DOC00-1.2 | Problem Statement | implemented | core |
| DOC00-1.3 | Target Audience | implemented | core |
| DOC00-1.4 | Product Principles | implemented | core |
| DOC00-1.5 | Success Criteria for V1 | deferred-owner | analytics |
| DOC00-1.6 | North-Star Metric | partial | analytics |
| DOC00-2.1 | User Roles | implemented | core |
| DOC00-2.2 | Authentication and Account Requirements | implemented | auth |
| DOC00-2.3 | Developer Profile | implemented | profile |
| DOC00-2.4 | Product Showcase | partial | product |
| DOC00-2.5 | Active Showcase Slot System | implemented | slots |
| DOC00-2.6 | Product Approval Workflow | implemented | moderation |
| DOC00-2.7 | Search and Discovery | implemented | discovery |
| DOC00-2.8 | Client Inquiry — Build Something Similar | implemented | inquiry |
| DOC00-2.9 | Inquiry Status and Outcome Tracking | implemented | inquiry |
| DOC00-2.10 | Reviews | implemented | review |
| DOC00-2.11 | Availability and Reliability | implemented | trust |
| DOC00-2.12 | Notifications | partial | notification |
| DOC00-2.13 | Error Handling | partial | ui |
| DOC00-2.14 | Admin and Moderation | implemented | moderation |
| DOC00-2.15 | Core User Stories | implemented | core |
| DOC00-3.1 | Performance | partial | media |
| DOC00-3.2 | Scalability | partial | architecture |
| DOC00-3.3 | Availability and Resilience | deferred-owner | ops |
| DOC00-3.4 | Security | partial | security |
| DOC00-3.5 | Privacy and Compliance | deferred-owner | legal |
| DOC00-3.6 | Accessibility | partial | ui |
| DOC00-4.1 | V1 Architecture Decision | implemented | architecture |
| DOC00-4.2 | Recommended Tech Stack | implemented | architecture |
| DOC00-4.3 | Backend Module Boundaries | implemented | architecture |
| DOC00-4.4 | Deferred Architecture | deferred-future | architecture |
| DOC00-4.5 | Integration Points | partial | architecture |
| DOC00-5.1 | Primary V1 Screens | implemented | ui |
| DOC00-5.2 | Product Detail UX | implemented | product |
| DOC00-5.3 | Client Search UX | implemented | discovery |
| DOC00-5.4 | Error and Empty States | implemented | ui |
| DOC00-5.5 | Responsive Design | partial | ui |
| DOC00-6.1 | Core Data Entities | partial | architecture |
| DOC00-6.2 | Data Validation Rules | implemented | security |
| DOC00-6.3 | Data Integrity | partial | inquiry |
| DOC00-6.4 | Backup and Recovery | deferred-owner | ops |
| DOC00-7.1 | Environments | partial | ops |
| DOC00-7.2 | CI/CD Pipeline | partial | ops |
| DOC00-7.3 | Rollback Strategy | deferred-owner | ops |
| DOC00-7.4 | Monitoring and Alerting | deferred-owner | ops |
| DOC00-7.5 | Logging | partial | ops |
| DOC00-7.6 | Developer Demo Deployment Model | implemented | trust |
| DOC00-8.1 | Required Public Policies | deferred-owner | legal |
| DOC00-8.2 | Product Ownership and Confidentiality | implemented | legal |
| DOC00-8.3 | V1 Commercial Boundary | partial | legal |
| DOC00-8.4 | Future Legal Review Triggers | deferred-owner | legal |
| DOC00-9.1 | Public Indexable Pages | partial | seo |
| DOC00-9.2 | Metadata | implemented | seo |
| DOC00-9.3 | Technical SEO | implemented | seo |
| DOC00-9.4 | URL Examples | implemented | seo |
| DOC00-10 | V1 Acceptance Criteria | partial | testing |
| DOC00-11 | Explicitly Deferred Features | partial | core |
| DOC00-12 | Revenue Hooks Preserved by V1 Design | deferred-future | core |
| DOC00-13 | Required Analytics Events | implemented | analytics |
| DOC00-14 | Final V1 Scope Statement | deferred-owner | core |
| DOC01-1 | Product Name | implemented | core |
| DOC01-2 | Purpose | implemented | core |
| DOC01-3 | Product Principles | implemented | core |
| DOC01-4.1 | Supply Side | implemented | core |
| DOC01-4.2 | Demand Side | implemented | core |
| DOC01-5.1 | In Scope — V1 | implemented | core |
| DOC01-5.2 | Out of Scope — V1 | partial | core |
| DOC01-6 | Showcase Slot Model | partial | slots |
| DOC01-7 | Trust Model | partial | trust |
| DOC01-8 | Revenue Vision | partial | core |
| DOC01-9.1 | V1 Validation Targets | deferred-owner | analytics |
| DOC01-9.2 | North-Star Metric | partial | analytics |
| DOC01-10 | Anti-Goals | implemented | core |
| DOC01-11 | Phase Roadmap | partial | core |
| DOC01-12 | Final Product Rule | deferred-owner | core |
| DOC02-1.1 | Developer / Builder | implemented | product |
| DOC02-1.2 | Client / Visitor | implemented | inquiry |
| DOC02-1.3 | Admin / Moderator | implemented | moderation |
| DOC02-1.4 | Team / Studio | partial | teams |
| FR-AUTH-001 | FR-AUTH-001 | implemented | auth |
| FR-AUTH-002 | FR-AUTH-002 | implemented | auth |
| FR-AUTH-003 | FR-AUTH-003 | implemented | auth |
| FR-AUTH-004 | FR-AUTH-004 | implemented | auth |
| FR-AUTH-005 | FR-AUTH-005 | implemented | auth |
| FR-AUTH-006 | FR-AUTH-006 | implemented | auth |
| FR-AUTH-007 | FR-AUTH-007 | implemented | auth |
| FR-AUTH-008 | FR-AUTH-008 | implemented | auth |
| DOC02-3 | Developer Profile | implemented | profile |
| DOC02-4.1 | Required Fields | implemented | product |
| DOC02-4.2 | Optional Proof | implemented | media |
| DOC02-4.3 | Project Types | implemented | product |
| DOC02-4.4 | Visibility | implemented | media |
| DOC02-4.5 | Approval States | implemented | product |
| FR-SLOT-001 | FR-SLOT-001 | implemented | slots |
| FR-SLOT-002 | FR-SLOT-002 | implemented | slots |
| FR-SLOT-003 | FR-SLOT-003 | implemented | slots |
| FR-SLOT-004 | FR-SLOT-004 | implemented | slots |
| FR-SLOT-005 | FR-SLOT-005 | implemented | slots |
| FR-SLOT-006 | FR-SLOT-006 | implemented | slots |
| FR-SLOT-007 | FR-SLOT-007 | implemented | slots |
| FR-SLOT-008 | FR-SLOT-008 | implemented | slots |
| DOC02-6 | Product Review / Moderation | implemented | moderation |
| FR-SEARCH-001 | FR-SEARCH-001 | implemented | discovery |
| FR-SEARCH-002 | FR-SEARCH-002 | implemented | discovery |
| FR-SEARCH-003 | FR-SEARCH-003 | implemented | discovery |
| FR-SEARCH-004 | FR-SEARCH-004 | implemented | discovery |
| FR-SEARCH-005 | FR-SEARCH-005 | implemented | discovery |
| DOC02-8 | Product Detail Page | partial | product |
| FR-SAVE-001 | FR-SAVE-001 | implemented | product |
| FR-SAVE-002 | FR-SAVE-002 | implemented | product |
| FR-SAVE-003 | FR-SAVE-003 | implemented | product |
| FR-SAVE-004 | FR-SAVE-004 | implemented | product |
| FR-SAVE-005 | FR-SAVE-005 | implemented | product |
| DOC02-9.1 | Required Inquiry Data | implemented | inquiry |
| DOC02-9.2 | Request Types | implemented | inquiry |
| DOC02-9.3 | Budget Bands | implemented | inquiry |
| DOC02-9.4 | Inquiry Flow | implemented | inquiry |
| DOC02-10 | Inquiry Status Workflow | implemented | inquiry |
| FR-REV-001 | FR-REV-001 | implemented | review |
| FR-REV-002 | FR-REV-002 | implemented | review |
| FR-REV-003 | FR-REV-003 | implemented | review |
| FR-REV-004 | FR-REV-004 | implemented | review |
| DOC02-12 | Availability | implemented | trust |
| DOC02-13 | Response Reliability | implemented | analytics |
| DOC02-14 | Notifications | implemented | notification |
| DOC02-15 | Reporting / Abuse | partial | moderation |
| DOC02-16 | Error Handling | partial | ui |
| DOC02-17 | Edge Cases | partial | testing |
| DOC02-18 | V2 Functional Extensions | implemented | teams |
| DOC02-19 | Acceptance Criteria Summary | implemented | testing |
| DOC03-1 | Goals | partial | core |
| NFR-PERF-001 | API Latency | deferred-owner | testing |
| NFR-PERF-002 | Public Web Performance | partial | media |
| NFR-PERF-003 | Database | partial | architecture |
| NFR-SCALE-001 | NFR-SCALE-001 | partial | architecture |
| NFR-SCALE-002 | NFR-SCALE-002 | implemented | architecture |
| NFR-SCALE-003 | NFR-SCALE-003 | partial | architecture |
| NFR-REL-001 | NFR-REL-001 | implemented | inquiry |
| NFR-REL-002 | NFR-REL-002 | implemented | inquiry |
| NFR-REL-003 | NFR-REL-003 | implemented | inquiry |
| DOC03-5 | Availability | deferred-owner | ops |
| DOC03-6.1 | Authentication | implemented | auth |
| DOC03-6.2 | Session Strategy | partial | auth |
| DOC03-6.3 | Authorization | implemented | security |
| DOC03-6.4 | Transport | deferred-owner | ops |
| DOC03-6.5 | Input Safety | implemented | security |
| DOC03-6.6 | File Upload Security | partial | media |
| DOC03-6.7 | External URL / SSRF Protection | implemented | trust |
| DOC03-6.8 | Rate Limiting | partial | security |
| DOC03-6.9 | Secrets | deferred-owner | config |
| DOC03-6.10 | Audit | partial | moderation |
| DOC03-6.11 | Security Headers | partial | security |
| DOC03-6.12 | Encryption at Rest and Key Handling | deferred-owner | ops |
| DOC03-6.13 | Bot and Abuse Resistance | partial | security |
| NFR-PRIV-001 | NFR-PRIV-001 | implemented | privacy |
| NFR-PRIV-002 | NFR-PRIV-002 | implemented | privacy |
| NFR-PRIV-003 | NFR-PRIV-003 | implemented | privacy |
| NFR-PRIV-004 | NFR-PRIV-004 | partial | privacy |
| NFR-PRIV-005 | NFR-PRIV-005 | implemented | privacy |
| DOC03-8 | Compliance | deferred-owner | legal |
| DOC03-9 | Accessibility | partial | ui |
| DOC03-10 | Maintainability | partial | architecture |
| DOC03-11 | Testability | partial | testing |
| DOC03-11.1 | Load and Performance Testing | deferred-owner | testing |
| DOC03-12 | Observability | partial | analytics |
| DOC03-13 | Backup / Recovery | deferred-owner | ops |
| DOC03-13.1 | Recovery Targets | deferred-owner | ops |
| DOC03-14 | Browser / Device Support | partial | testing |
| DOC03-15 | Internationalization Readiness | partial | ui |
| DOC03-16 | Data Retention | deferred-owner | privacy |
| DOC03-17 | Security Review Triggers | partial | security |
| DOC04-1 | Purpose | implemented | core |
| DOC04-2 | Global Rules | partial | core |
| DOC04-3 | Developer Account State Machine | implemented | auth |
| DOC04-4 | Product State Machine | implemented | product |
| DOC04-5 | Showcase Entitlement Rules | implemented | slots |
| DOC04-6 | Client Inquiry State Machine | partial | inquiry |
| DOC04-7 | Inquiry Qualification | implemented | inquiry |
| DOC04-8 | Duplicate Inquiry Rule | implemented | inquiry |
| DOC04-9 | Availability Rules | implemented | trust |
| DOC04-10 | Review State Machine | implemented | review |
| DOC04-11 | Saved Product Rules | implemented | product |
| DOC04-12 | Moderation State Machine | partial | moderation |
| DOC04-13 | Trust Label Rules | implemented | trust |
| DOC04-14 | Search/Ranking Guardrails | implemented | discovery |
| DOC04-15 | Team/Studio Rules — V2 | implemented | teams |
| DOC04-16 | Client → Team Rules — V2 | implemented | teams |
| DOC04-17 | External Contract Boundary — V1 | implemented | inquiry |
| DOC04-18 | Idempotency Rules | implemented | inquiry |
| DOC04-19 | Time Rules | implemented | ui |
| DOC04-20 | Rule Precedence | implemented | security |
| DOC05-1 | Architecture Decision | implemented | architecture |
| DOC05-3 | Backend Module Boundaries | implemented | architecture |
| DOC05-6 | Request/Data Flow — Hire Confirmation | implemented | inquiry |
| DOC05-7 | Frontend Architecture | implemented | ui |
| DOC05-8 | API Style | implemented | api |
| DOC05-9 | Authentication / Session Design | implemented | auth |
| DOC05-10 | PostgreSQL | implemented | architecture |
| DOC05-11 | Object Storage | implemented | media |
| DOC05-12 | External Demo Strategy | implemented | trust |
| DOC05-13 | Video Strategy | implemented | media |
| DOC05-14 | Email | implemented | notification |
| DOC05-15 | Background Jobs | partial | architecture |
| DOC05-16 | Search Architecture | implemented | discovery |
| DOC05-17 | Caching | implemented | architecture |
| DOC05-18 | Deployment Topology | deferred-owner | ops |
| DOC05-19 | Environment Separation | deferred-owner | config |
| DOC05-20 | Observability | deferred-owner | ops |
| DOC05-21 | Microservices Roadmap | deferred-future | architecture |
| DOC05-22 | V2 Team Architecture Readiness | partial | teams |
| DOC05-23 | V3 Payments Architecture Trigger | partial | delivery |
| DOC05-24 | Source-Code Marketplace Trigger | missing | security |
| DOC06-1 | Purpose | implemented | api |
| DOC06-2 | Conventions | implemented | api |
| DOC06-3 | Standard Error Envelope | partial | api |
| DOC06-4 | Authentication Endpoints | implemented | auth |
| DOC06-5 | Current User | implemented | privacy |
| DOC06-6 | Developer Profile | implemented | profile |
| DOC06-7 | Product Search | implemented | discovery |
| DOC06-8 | Product Management | implemented | product |
| DOC06-9 | Product Media | implemented | media |
| DOC06-10 | Saved Products | implemented | product |
| DOC06-11 | Client Inquiry | implemented | inquiry |
| DOC06-12 | Developer Inquiry Dashboard | implemented | inquiry |
| DOC06-13 | Inquiry Status | implemented | inquiry |
| DOC06-14 | Reviews | implemented | review |
| DOC06-15 | Reports | implemented | moderation |
| DOC06-16 | Admin Product Review | implemented | moderation |
| DOC06-17 | Admin Reports | implemented | moderation |
| DOC06-18 | Categories / Technologies | implemented | moderation |
| DOC06-19 | Analytics Events | implemented | analytics |
| DOC06-20 | Rate Limits — Initial Policy | partial | security |
| DOC06-21 | Authorization Matrix | implemented | security |
| DOC06-22 | API Compatibility | implemented | api |
| DOC06-ADDENDUM-14-September-2026 | Implementation addendum — 14 September 2026 | implemented | api |
| DOC06-ADDENDUM-15-September-2026 | Implementation addendum — 15 September 2026 | implemented | api |
| DOC07-1 | Modeling Principles | implemented | architecture |
| DOC07-3.1 | users | implemented | auth |
| DOC07-3.2 | user_roles | implemented | auth |
| DOC07-3.3 | developer_profiles | implemented | profile |
| DOC07-3.4 | client_profiles | partial | inquiry |
| DOC07-3.5 | products | partial | product |
| DOC07-3.6 | product_media | implemented | media |
| DOC07-3.7 | product_links | partial | product |
| DOC07-3.8 | categories | implemented | discovery |
| DOC07-3.9 | technologies | implemented | discovery |
| DOC07-3.10 | saved_products | implemented | product |
| DOC07-3.11 | product_categories | implemented | product |
| DOC07-3.12 | product_technologies | implemented | product |
| DOC07-3.13 | showcase_entitlements | implemented | slots |
| DOC07-3.14 | inquiries | implemented | inquiry |
| DOC07-3.15 | inquiry_events | partial | inquiry |
| DOC07-3.16 | reviews | implemented | review |
| DOC07-3.17 | notifications | implemented | notification |
| DOC07-3.18 | reports | implemented | moderation |
| DOC07-3.19 | moderation_actions | partial | moderation |
| DOC07-3.20 | oauth_accounts | implemented | auth |
| DOC07-3.21 | account_tokens | implemented | auth |
| DOC07-3.22 | legal_acceptances | partial | legal |
| DOC07-3.23 | consent_records | partial | privacy |
| DOC07-3.24 | notification_preferences | deferred-future | notification |
| DOC07-4 | Future Team Tables — V2 | partial | teams |
| DOC07-5 | Validation Rules | implemented | security |
| DOC07-6 | Uniqueness / Constraints | implemented | architecture |
| DOC07-7 | Indexing | implemented | architecture |
| DOC07-8 | Soft Delete | implemented | privacy |
| DOC07-9 | Auditability | partial | moderation |
| DOC07-10 | Sample Data | implemented | core |
| DOC07-11 | Backup Strategy | deferred-owner | ops |
| DOC07-12 | Data Recovery Priorities | deferred-owner | ops |
| DOC07-13 | Data Migration Rules | implemented | ops |
| DOC08-1 | UX Strategy | implemented | core |
| DOC08-2 | Information Architecture | implemented | ui |
| DOC08-3 | Homepage | implemented | ui |
| DOC08-4 | Explore/Search | implemented | discovery |
| DOC08-5 | Product Detail | partial | product |
| DOC08-6 | Build Something Similar | implemented | inquiry |
| DOC08-7 | Developer Profile | implemented | profile |
| DOC08-8 | Developer Dashboard | partial | analytics |
| DOC08-9 | Inquiry Dashboard | partial | inquiry |
| DOC08-10 | Admin Review UX | implemented | moderation |
| DOC08-11 | Loading States | partial | ui |
| DOC08-12 | Error States | partial | ui |
| DOC08-13 | Demo Offline State | implemented | trust |
| DOC08-14 | Accessibility | partial | ui |
| DOC08-15 | Branding Tokens | partial | ui |
| DOC08-16 | Responsive Breakpoints | implemented | ui |
| DOC08-17 | Content Style | implemented | ui |
| DOC08-18 | Community UX — Later | deferred-future | ui |
| DOC08-19 | Team UX — V2 | implemented | teams |
| DOC08-20 | Wireframe Guidance | implemented | ui |
| DOC08-21 | UX Anti-Patterns | implemented | ui |
| DOC09-1 | Scope | partial | security |
| DOC09-2 | Security Objectives | implemented | security |
| DOC09-3 | Trust Boundaries | partial | architecture |
| DOC09-4.1 | Broken Object-Level Authorization | implemented | security |
| DOC09-4.2 | Broken Authentication / Account Takeover | implemented | auth |
| DOC09-4.3 | SSRF Through Demo Validation | implemented | trust |
| DOC09-4.4 | Stored XSS | implemented | security |
| DOC09-4.5 | File Upload Abuse | partial | media |
| DOC09-4.6 | CSRF | implemented | security |
| DOC09-4.7 | CORS Misconfiguration | implemented | security |
| DOC09-4.8 | Clickjacking | implemented | security |
| DOC09-4.9 | Security Headers | partial | security |
| DOC09-4.10 | Mass Inquiry Spam / Business-Flow Abuse | partial | inquiry |
| DOC09-4.11 | Fake Developer / Portfolio Fraud | implemented | trust |
| DOC09-4.12 | Review Manipulation | implemented | review |
| DOC09-4.13 | Resource Exhaustion | partial | security |
| DOC09-4.14 | Injection | implemented | security |
| DOC09-4.15 | Sensitive Data Exposure | partial | privacy |
| DOC09-4.16 | Admin Abuse / Privilege Escalation | implemented | moderation |
| DOC09-4.17 | Third-Party Dependency / Supply Chain | partial | architecture |
| DOC09-4.18 | Email Security | deferred-owner | notification |
| DOC09-5 | Data Classification | implemented | security |
| DOC09-6 | Threat Severity | implemented | moderation |
| DOC09-7 | Security Test Cases | partial | testing |
| DOC09-8 | Security Review Triggers | partial | security |
| DOC09-9 | Incident Response Minimum | deferred-owner | ops |
| DOC09-10 | Security Acceptance Gate | deferred-owner | ops |
| DOC10-1 | Goal | implemented | testing |
| DOC10-2 | Test Pyramid | implemented | testing |
| E2E-01 | Developer Onboarding | partial | testing |
| E2E-02 | Product Publication | partial | testing |
| E2E-03 | Showcase Slot Limit | partial | testing |
| E2E-04 | Client Conversion | partial | testing |
| E2E-05 | Marketplace Outcome | partial | testing |
| E2E-06 | Moderation | partial | testing |
| DOC10-4 | Authorization Test Matrix | implemented | security |
| DOC10-5 | State-Machine Tests | partial | testing |
| DOC10-6 | Concurrency Tests | implemented | slots |
| DOC10-7 | Validation Tests | implemented | testing |
| DOC10-8 | External URL Security Tests | implemented | trust |
| DOC10-9 | UI Tests | partial | ui |
| DOC10-10 | Accessibility QA | partial | ui |
| DOC10-11 | Cross-Browser | partial | testing |
| DOC10-12 | Performance Testing | deferred-owner | testing |
| DOC10-13 | SEO QA | partial | seo |
| DOC10-14 | Email QA | partial | notification |
| DOC10-15 | Data Migration QA | partial | testing |
| DOC10-16 | Backup/Restore QA | partial | ops |
| DOC10-17 | Regression Suite | implemented | testing |
| DOC10-18 | Test Data | implemented | testing |
| DOC10-19 | Defect Severity | partial | testing |
| DOC10-20 | Definition of Done | partial | testing |
| DOC10-21 | Pre-Launch QA Gate | deferred-owner | testing |
| DOC11-1 | Purpose | implemented | analytics |
| DOC11-2 | North-Star Metrics | partial | analytics |
| DOC11-3 | Funnel | implemented | analytics |
| DOC11-4 | Event Envelope | implemented | analytics |
| DOC11-5 | Core Events | implemented | analytics |
| DOC11-6 | Conversion Definitions | partial | analytics |
| DOC11-7 | Marketplace Metrics | partial | analytics |
| DOC11-8 | Marketplace Liquidity Indicators | missing | analytics |
| DOC11-9 | Showcase Slot Analytics | missing | analytics |
| DOC11-10 | Acquisition Attribution | partial | analytics |
| DOC11-11 | SEO Metrics | partial | analytics |
| DOC11-12 | Privacy Rules | implemented | analytics |
| DOC11-13 | Experimentation | deferred-future | analytics |
| DOC11-14 | Dashboard Set | partial | analytics |
| DOC11-15 | Data Quality Rules | partial | analytics |
| DOC11-16 | Early Validation Scorecard | deferred-owner | analytics |
| DOC11-17 | What Not to Optimize | implemented | analytics |
| DOC12-1 | Purpose | implemented | moderation |
| DOC12-2 | Safety Principles | implemented | moderation |
| DOC12-3 | Content Categories | implemented | moderation |
| DOC12-4 | Product Review Checklist | implemented | moderation |
| DOC12-5 | Report Reasons | implemented | moderation |
| DOC12-6 | Severity Levels | implemented | moderation |
| DOC12-7 | Enforcement Actions | partial | moderation |
| DOC12-8 | Moderation Workflow | implemented | moderation |
| DOC12-9 | Appeals | implemented | moderation |
| DOC12-10 | Intellectual Property / Copyright | partial | legal |
| DOC12-11 | Client Confidentiality | implemented | moderation |
| DOC12-12 | Malicious External Links | partial | trust |
| DOC12-13 | Fake Developer Prevention | partial | trust |
| DOC12-14 | Fake Client / Inquiry Abuse | partial | inquiry |
| DOC12-15 | Review Integrity | implemented | review |
| DOC12-16 | Trust Labels | implemented | trust |
| DOC12-17 | Ranking Safety | implemented | discovery |
| DOC12-18 | New Builder Fairness | implemented | discovery |
| DOC12-19 | Team/Studio Safety — V2 | implemented | teams |
| DOC12-20 | Moderation SLAs | deferred-owner | ops |
| DOC12-21 | Transparency | partial | analytics |
| DOC12-22 | Moderator Access | implemented | moderation |
| DOC12-23 | Evidence Retention | deferred-owner | privacy |
| DOC12-24 | Launch Gate | deferred-owner | legal |
| DOC13-1 | Operational Goals | partial | ops |
| DOC13-2 | Environments | deferred-owner | config |
| DOC13-3 | Containerization | implemented | ops |
| DOC13-4 | CI Pipeline | partial | testing |
| DOC13-5 | CD Pipeline | deferred-owner | ops |
| DOC13-6 | Artifact Versioning | partial | ops |
| DOC13-7 | Database Migrations | implemented | ops |
| DOC13-8 | Rollback | deferred-owner | ops |
| DOC13-9 | Health Checks | implemented | ops |
| DOC13-10 | Monitoring | deferred-owner | ops |
| DOC13-11 | Logging | partial | ops |
| DOC13-12 | Alerting | deferred-owner | ops |
| DOC13-13 | Error Tracking | deferred-owner | ops |
| DOC13-14 | Secrets Management | deferred-owner | config |
| DOC13-14.1 | Email Deliverability | deferred-owner | notification |
| DOC13-15 | Backup | deferred-owner | ops |
| DOC13-15.1 | Encryption and Recovery Objectives | deferred-owner | ops |
| DOC13-16 | Deployment Topology | deferred-owner | ops |
| DOC13-17 | Hosting Choices | deferred-owner | ops |
| DOC13-18 | URL Health Worker — V1.5 | implemented | trust |
| DOC13-19 | Incident Response | deferred-owner | ops |
| DOC13-20 | Security Incident Examples | deferred-owner | ops |
| DOC13-21 | Cost Controls | deferred-owner | ops |
| DOC13-22 | Release Strategy | partial | ops |
| DOC13-23 | SLO Evolution | deferred-owner | ops |
| DOC13-24 | Pre-Launch Checklist | deferred-owner | ops |
| DOC14-1 | V1 Legal Position | partial | legal |
| DOC14-2 | Required Public Documents | deferred-owner | legal |
| DOC14-3 | Terms of Service Requirements | deferred-owner | legal |
| DOC14-4 | Intellectual Property | partial | legal |
| DOC14-5 | Copyright / Takedown | deferred-owner | legal |
| DOC14-6 | Privacy Policy Requirements | deferred-owner | legal |
| DOC14-7 | GDPR Readiness | deferred-owner | legal |
| DOC14-8 | CCPA / CPRA Readiness | deferred-owner | legal |
| DOC14-8.1 | Additional Global Marketplace Review | deferred-owner | legal |
| DOC14-9 | Cookies / Tracking | implemented | privacy |
| DOC14-10 | Reviews and Reputation | implemented | review |
| DOC14-11 | Verification Labels | implemented | trust |
| DOC14-12 | External Demo Links | partial | legal |
| DOC14-13 | Client Inquiry Privacy | implemented | inquiry |
| DOC14-14 | Marketing Communications | deferred-future | notification |
| DOC14-15 | Accessibility | partial | ui |
| DOC14-16 | Licensing of Dependencies | partial | architecture |
| DOC14-17 | Open-Source Policy | partial | architecture |
| DOC14-18 | Future Team / Recruitment Legal Review | deferred-owner | legal |
| DOC14-19 | Future Payments Legal Review | deferred-owner | legal |
| DOC14-20 | Future Source-Code Sales Legal Review | deferred-owner | legal |
| DOC14-21 | Age / Eligibility | deferred-owner | legal |
| DOC14-22 | Content Moderation | partial | moderation |
| DOC14-23 | Security / Breach Response | deferred-owner | ops |
| DOC14-24 | Data Processing Vendors | deferred-owner | legal |
| DOC14-25 | Legal Launch Gates | deferred-owner | legal |
| DOC15-1 | SEO Strategy | implemented | seo |
| DOC15-2 | Indexable Public Pages | partial | seo |
| DOC15-3 | URL Structure | implemented | seo |
| DOC15-4 | Title Templates | implemented | seo |
| DOC15-5 | Meta Description Templates | implemented | seo |
| DOC15-6 | Open Graph | implemented | seo |
| DOC15-7 | Twitter / X Cards | implemented | seo |
| DOC15-8 | Canonical URLs | implemented | seo |
| DOC15-9 | robots.txt | implemented | seo |
| DOC15-10 | Sitemap | partial | seo |
| DOC15-11 | Structured Data | implemented | seo |
| DOC15-12 | Product Structured Data Example | implemented | seo |
| DOC15-13 | On-Page SEO | implemented | seo |
| DOC15-14 | Content Quality Rules | partial | moderation |
| DOC15-15 | Programmatic SEO — Later | implemented | seo |
| DOC15-16 | Technology SEO | implemented | seo |
| DOC15-17 | Core Web Vitals | deferred-owner | seo |
| DOC15-18 | Social Sharing | partial | seo |
| DOC15-19 | Pagination / Infinite Scroll | implemented | seo |
| DOC15-20 | Search Page Indexing | implemented | seo |
| DOC15-21 | Image SEO | implemented | media |
| DOC15-22 | Duplicate Content | implemented | seo |
| DOC15-23 | Metadata Validation | partial | seo |
| DOC15-24 | SEO Analytics | partial | analytics |
| DOC16-1 | Purpose | implemented | config |
| DOC16-2 | Environments | deferred-owner | config |
| DOC16-3 | Configuration Principles | implemented | config |
| DOC16-4 | Backend Configuration Categories | partial | config |
| DOC16-5 | Frontend Public Configuration | implemented | config |
| DOC16-6 | Local Development | implemented | config |
| DOC16-7 | Staging | deferred-owner | config |
| DOC16-8 | Production | deferred-owner | config |
| DOC16-9 | Feature Flags | partial | config |
| DOC16-10 | Configuration Validation | partial | config |
| DOC16-11 | Secret Rotation | deferred-owner | config |
| DOC16-12 | Environment-Specific Data Rules | implemented | config |
| DOC16-13 | Logging Levels | deferred-owner | config |
| DOC16-14 | Configuration Ownership | deferred-owner | config |
| DOC16-15 | Example .env.example | implemented | config |
| DOC16-16 | CI/CD Environment Separation | deferred-owner | config |
| DOC16-17 | Drift Control | implemented | config |

## Evidence domains

All paths below were checked for existence at review time. Backend/frontend/database/test references indicate inspected source; owner configuration and independent execution are listed separately.

### core

Proof-first product discovery, account workspaces and the qualified inquiry/outcome loop exist. Historical V1 boundaries do not prohibit later V3/V3.5 work. Actual commercial adoption is an owner acceptance outcome.

- frontend: `app/page.tsx`, `app/products/[slug]/page.tsx`, `app/workspace/page.tsx`
- backend: `backend/src/main/java/com/getlancer/products/ProductService.java`, `backend/src/main/java/com/getlancer/inquiries/InquiryService.java`
- database: `backend/src/main/resources/db/migration/V1__marketplace.sql`, `backend/src/main/resources/db/migration/V2__guardrails.sql`
- tests: `backend/src/test/java/com/getlancer/integration/MarketplaceIntegrationTest.java`, `scripts/smoke-docker.mjs`

### auth

Email/password, Google/GitHub provider code, hashed expiring sessions/tokens, admin MFA and closure confirmation exist. External OAuth consent and hosted TLS are provider acceptance, not demonstrated by source.

- frontend: `app/components/auth-form.tsx`, `app/components/confirmation.tsx`, `app/components/account-settings.tsx`
- backend: `backend/src/main/java/com/getlancer/auth/AuthService.java`, `backend/src/main/java/com/getlancer/auth/GoogleAuthService.java`, `backend/src/main/java/com/getlancer/auth/GitHubAuthService.java`, `backend/src/main/java/com/getlancer/security/Security.java`
- database: `backend/src/main/resources/db/migration/V1__marketplace.sql`, `backend/src/main/resources/db/migration/V4__single_administrator.sql`, `backend/src/main/resources/db/migration/V5__google_and_login_challenges.sql`, `backend/src/main/resources/db/migration/V7__github_oauth.sql`
- tests: `backend/src/test/java/com/getlancer/auth/AuthFlowTest.java`, `backend/src/test/java/com/getlancer/auth/GitHubAuthTest.java`, `backend/src/test/java/com/getlancer/integration/MarketplaceIntegrationTest.java`

### profile

Required profile content, optional links/country/time zone/languages, approval, availability and sample-gated response metrics are implemented.

- frontend: `app/components/profile-form.tsx`, `app/builders/[slug]/page.tsx`
- backend: `backend/src/main/java/com/getlancer/profiles/ProfileService.java`, `backend/src/main/java/com/getlancer/profiles/ProfileDetails.java`, `backend/src/main/java/com/getlancer/products/ProductService.java`
- database: `backend/src/main/resources/db/migration/V1__marketplace.sql`, `backend/src/main/resources/db/migration/V10__profile_details_and_review_identity.sql`
- tests: `backend/src/test/java/com/getlancer/profiles/ProfileAndBootstrapTest.java`, `backend/src/test/java/com/getlancer/integration/MarketplaceIntegrationTest.java`

### product

Public and owner lifecycle flows exist. Optional showcase commercial pricing is free-text pricingNote rather than the original structured pricingMode/min/max/currency model; commerce prices are a separate V3.5 concern.

- frontend: `app/workspace/page.tsx`, `app/components/proof-editor.tsx`, `app/products/[slug]/page.tsx`
- backend: `backend/src/main/java/com/getlancer/products/ProductService.java`, `backend/src/main/java/com/getlancer/products/ProductRepository.java`, `backend/src/main/java/com/getlancer/media/MediaService.java`
- database: `backend/src/main/resources/db/migration/V1__marketplace.sql`, `backend/src/main/resources/db/migration/V2__guardrails.sql`, `backend/src/main/resources/db/migration/V3__product_taxonomy.sql`, `backend/src/main/resources/db/migration/V6__moderation_and_proof.sql`, `backend/src/main/resources/db/migration/V8__release_workflows.sql`
- tests: `backend/src/test/java/com/getlancer/integration/MarketplaceIntegrationTest.java`, `scripts/smoke-docker.mjs`

### slots

Entitlement rows and transactional locks enforce three initial slots; drafts/archives do not consume slots; earned awards are manual, unique per verified completion. Paid capacity is deliberately future.

- frontend: `app/workspace/page.tsx`, `app/components/workspace-overview.tsx`, `app/components/trust-workspace.tsx`
- backend: `backend/src/main/java/com/getlancer/products/ProductService.java`, `backend/src/main/java/com/getlancer/trust/TrustService.java`
- database: `backend/src/main/resources/db/migration/V1__marketplace.sql`, `backend/src/main/resources/db/migration/V11__trust_and_reliability.sql`
- tests: `backend/src/test/java/com/getlancer/integration/MarketplaceIntegrationTest.java#atomicSlotLimit`, `backend/src/test/java/com/getlancer/integration/MarketplaceIntegrationTest.java#v15RepositoryProofIsScopedAndEarnedAwardsAreIdempotent`, `scripts/smoke-docker.mjs`

### discovery

PostgreSQL full-text/filtered search, deterministic newest/updated/relevance ordering, business-first controls, proof cards and similar available builders exist. No paid/popularity-only rank.

- frontend: `app/page.tsx`, `app/components/explore.tsx`, `app/components/browse-toolbar.tsx`, `app/components/similar-builders.tsx`, `lib/server.ts`
- backend: `backend/src/main/java/com/getlancer/products/ProductService.java#search`, `backend/src/main/java/com/getlancer/trust/TrustService.java`
- database: `backend/src/main/resources/db/migration/V1__marketplace.sql`, `backend/src/main/resources/db/migration/V3__product_taxonomy.sql`, `backend/src/main/resources/db/migration/V11__trust_and_reliability.sql`
- tests: `backend/src/test/java/com/getlancer/integration/MarketplaceIntegrationTest.java#publicSearchUsesFiltersAndPagination`, `tests/connected-mode.test.mjs`, `tests/marketplace-ui.test.mjs`

### inquiry

Guest inquiries persist before confirmation/notification; email-qualified party queries, immutable-in-application status events, confirmed hire/completion, retries, duplicate prevention, expiry and abuse quarantine exist. Legacy history lacks a database mutation-rejection trigger in baseline.

- frontend: `app/components/forms.tsx`, `app/inquiry/page.tsx`, `app/components/client-requests.tsx`, `app/components/confirmation.tsx`, `app/workspace/page.tsx`
- backend: `backend/src/main/java/com/getlancer/inquiries/InquiryService.java`, `backend/src/main/java/com/getlancer/inquiries/InquiryPolicy.java`, `backend/src/main/java/com/getlancer/inquiries/InquiryOutcomeService.java`, `backend/src/main/java/com/getlancer/inquiries/InquiryContractService.java`, `backend/src/main/java/com/getlancer/inquiries/ClientInquiryService.java`
- database: `backend/src/main/resources/db/migration/V1__marketplace.sql`, `backend/src/main/resources/db/migration/V2__guardrails.sql`, `backend/src/main/resources/db/migration/V6__moderation_and_proof.sql`, `backend/src/main/resources/db/migration/V8__release_workflows.sql`
- tests: `backend/src/test/java/com/getlancer/integration/MarketplaceIntegrationTest.java`, `scripts/smoke-docker.mjs`

### review

Confirmed legacy inquiry completion is mandatory for a unique review; anonymous/named visibility is explicit; published eligible reviews, reporting and reasoned moderation exist. V3 completion intentionally never invents legacy review eligibility.

- frontend: `app/components/review-fields.tsx`, `app/components/public-reviews.tsx`, `app/components/client-requests.tsx`, `app/workspace/page.tsx`
- backend: `backend/src/main/java/com/getlancer/inquiries/InquiryOutcomeService.java`, `backend/src/main/java/com/getlancer/inquiries/ClientInquiryService.java`, `backend/src/main/java/com/getlancer/admin/AdminService.java#reviewAction`
- database: `backend/src/main/resources/db/migration/V1__marketplace.sql`, `backend/src/main/resources/db/migration/V10__profile_details_and_review_identity.sql`
- tests: `backend/src/test/java/com/getlancer/integration/MarketplaceIntegrationTest.java#confirmedCompletionMakesOneModeratedReviewEligible`, `backend/src/test/java/com/getlancer/integration/MarketplaceIntegrationTest.java#reviewIdentityDefaultsPrivateAndNamedChoiceIsExplicit`, `scripts/smoke-docker.mjs`

### notification

Database outbox retries, transactional notifications, in-app notification records/read action and admin retry tooling exist. Notification preferences and marketing subscriptions are not implemented; baseline allows them later as volume grows.

- frontend: `app/components/account-settings.tsx`, `app/components/moderation-workflow.tsx`
- backend: `backend/src/main/java/com/getlancer/notifications/Mail.java`, `backend/src/main/java/com/getlancer/accounts/AccountService.java#read`, `backend/src/main/java/com/getlancer/moderation/ModerationService.java`
- database: `backend/src/main/resources/db/migration/V1__marketplace.sql`, `backend/src/main/resources/db/migration/V8__release_workflows.sql`
- tests: `backend/src/test/java/com/getlancer/security/ReleaseWorkflowTest.java`, `scripts/smoke-docker.mjs`

### moderation

Reasoned profile/product/account actions, reports with severity/triage, logged private evidence, audit history and structured appeals exist. Some named actions (warning, link-only removal, permanent-ban semantics) are represented by existing reasoned restriction/suspension rather than exact distinct states; full policy/staffing requires operator approval.

- frontend: `app/workspace/page.tsx`, `app/components/admin-tools.tsx`, `app/components/report-triage.tsx`, `app/components/moderation-workflow.tsx`, `app/report/page.tsx`
- backend: `backend/src/main/java/com/getlancer/admin/AdminService.java`, `backend/src/main/java/com/getlancer/moderation/ModerationService.java`, `backend/src/main/java/com/getlancer/inquiries/InquiryService.java#report`
- database: `backend/src/main/resources/db/migration/V1__marketplace.sql`, `backend/src/main/resources/db/migration/V6__moderation_and_proof.sql`, `backend/src/main/resources/db/migration/V8__release_workflows.sql`
- tests: `backend/src/test/java/com/getlancer/inquiries/PublicReportTest.java`, `backend/src/test/java/com/getlancer/integration/MarketplaceIntegrationTest.java#reportTriageRequiresAdminAndRespectsResolvedState`, `scripts/smoke-docker.mjs`

### privacy

Scoped export, verified claims, confirmation-based closure, session revocation/public hiding and policy-gated profile anonymization exist. Blanket historical purge/retention, lawful bases and durable analytics-consent records are not certified; financial/IP history remains policy retained.

- frontend: `app/components/account-settings.tsx`, `app/components/privacy-operations.tsx`, `app/components/analytics-preference.tsx`, `app/policies/page.tsx`
- backend: `backend/src/main/java/com/getlancer/accounts/AccountService.java#export`, `backend/src/main/java/com/getlancer/accounts/PrivacyService.java`, `backend/src/main/java/com/getlancer/auth/AuthService.java`
- database: `backend/src/main/resources/db/migration/V1__marketplace.sql`, `backend/src/main/resources/db/migration/V8__release_workflows.sql`, `backend/src/main/resources/db/migration/V9__private_data_api_boundary.sql`
- tests: `backend/src/test/java/com/getlancer/integration/MarketplaceIntegrationTest.java#exportEnforcesQualificationForRecipientAndPreservesClientOwnership`, `backend/src/test/java/com/getlancer/integration/BusinessIntegrationTest.java#exportIncludesTrustAndStaffingWithoutChallengesOrOtherUsersData`, `backend/src/test/java/com/getlancer/integration/DeliveryIntegrationTest.java#exportIncludesOnlyTheActorsCommercialConsentsDisputesAndPayments`

### security

Ownership/MFA, SameSite/Origin protections, parameterized queries, hashed tokens, bounded input/media, safe URL/DNS/redirect handling and private schema grants exist. Hosted CSP initially excludes Razorpay, public GET search lacks dedicated throttling, scanning/deployed keys/alerts need explicit coverage.

- frontend: `app/api/v1/[...path]/route.ts`, `worker/index.ts`, `lib/api.ts`
- backend: `backend/src/main/java/com/getlancer/security/Security.java`, `backend/src/main/java/com/getlancer/security/SessionCookies.java`, `backend/src/main/java/com/getlancer/security/RateLimits.java`, `backend/src/main/java/com/getlancer/shared/Rules.java`, `backend/src/main/java/com/getlancer/trust/DemoHealth.java`, `backend/src/main/java/com/getlancer/config/ProductionConfiguration.java`
- database: `backend/src/main/resources/db/migration/V2__guardrails.sql`, `backend/src/main/resources/db/migration/V9__private_data_api_boundary.sql`
- tests: `backend/src/test/java/com/getlancer/security/V1SafeguardsTest.java`, `backend/src/test/java/com/getlancer/security/ReleaseWorkflowTest.java`, `backend/src/test/java/com/getlancer/trust/TrustTest.java`, `backend/src/test/java/com/getlancer/integration/MarketplaceIntegrationTest.java`

### media

Signed upload plus completion, bounded PNG/JPEG decode/re-encode, metadata stripping, immutable private copies/thumbnail, alt/order/removal, approved external video and grant-based private access exist. No general malware guarantee; CDN/provider CORS and performance remain hosted acceptance.

- frontend: `app/components/proof-editor.tsx`, `app/components/demo-video.tsx`, `app/components/private-access.tsx`
- backend: `backend/src/main/java/com/getlancer/media/MediaService.java`, `backend/src/main/java/com/getlancer/products/PrivateProjectService.java`
- database: `backend/src/main/resources/db/migration/V6__moderation_and_proof.sql`, `backend/src/main/resources/db/migration/V8__release_workflows.sql`
- tests: `backend/src/test/java/com/getlancer/integration/MarketplaceIntegrationTest.java#proofUploadRequiresOwnerAndConfiguredStorage`, `scripts/smoke-docker.mjs`

### trust

Repository challenge/manual review is scoped to exact URL, health does bounded safe HTTPS HEAD without running JavaScript, available-builder fallback and sample-gated response reliability exist. Identity/contribution/runtime verification beyond these narrow checks is future.

- frontend: `app/components/trust-workspace.tsx`, `app/components/similar-builders.tsx`, `app/builders/[slug]/page.tsx`
- backend: `backend/src/main/java/com/getlancer/trust/TrustService.java`, `backend/src/main/java/com/getlancer/trust/DemoHealth.java`, `backend/src/main/java/com/getlancer/products/ProductService.java#builder`
- database: `backend/src/main/resources/db/migration/V11__trust_and_reliability.sql`
- tests: `backend/src/test/java/com/getlancer/trust/TrustTest.java`, `backend/src/test/java/com/getlancer/integration/MarketplaceIntegrationTest.java#v15AvailabilityAndSimilarBuildersRespectPublicScope`, `scripts/smoke-docker.mjs`

### teams

Five server-enforced roles, invite/recipient consent, multi-team membership, recruitment, temporary staffing/expiry, role-filtered CRM and public consented proof exist. Products remain individually owned; true organization-owned products and formal contributor entities are not implemented.

- frontend: `app/components/team-workspace.tsx`, `app/components/team-public.tsx`, `app/workspace/teams/page.tsx`
- backend: `backend/src/main/java/com/getlancer/teams/TeamService.java`, `backend/src/main/java/com/getlancer/teams/TeamRepository.java`, `backend/src/main/java/com/getlancer/teams/TeamPolicy.java`
- database: `backend/src/main/resources/db/migration/V12__teams.sql`
- tests: `backend/src/test/java/com/getlancer/integration/TeamsIntegrationTest.java`, `tests/team-demo.test.mjs`, `scripts/smoke-docker.mjs`

### business

Tenant-scoped OWNER/HIRING_MANAGER, consented invitations, private brief lifecycle, saved talent/shortlist, approved-current-proof matching and opted-in MFA concierge exist; no fabricated candidates or AI claim.

- frontend: `app/components/business-workspace.tsx`, `app/workspace/business/page.tsx`
- backend: `backend/src/main/java/com/getlancer/business/BusinessService.java`, `backend/src/main/java/com/getlancer/business/ProjectRequestService.java`, `backend/src/main/java/com/getlancer/business/MatchingService.java`, `backend/src/main/java/com/getlancer/business/TalentListService.java`, `backend/src/main/java/com/getlancer/business/ConciergeService.java`
- database: `backend/src/main/resources/db/migration/V13__business_hiring.sql`
- tests: `backend/src/test/java/com/getlancer/integration/BusinessIntegrationTest.java`, `tests/connected/business.spec.ts`, `scripts/smoke-docker.mjs`

### delivery

V3 proposals/consents/immutable accepted agreement, milestones/revisions/buyer acceptance, Razorpay Route reservations/capture/refund/dispute reconciliation and MFA recovery exist. Gateway disabled in service CI; live provider onboarding/payment/settlement is owner pending. V3.5 was absent from baseline and is separate incoming work.

- frontend: `app/components/delivery-workspace.tsx`, `app/components/delivery-operations.tsx`, `app/workspace/delivery/page.tsx`, `lib/delivery.ts`
- backend: `backend/src/main/java/com/getlancer/delivery/DeliveryService.java`, `backend/src/main/java/com/getlancer/delivery/DeliveryRepository.java`, `backend/src/main/java/com/getlancer/payments/PaymentService.java`, `backend/src/main/java/com/getlancer/payments/RazorpayClient.java`
- database: `backend/src/main/resources/db/migration/V14__delivery.sql`, `backend/src/main/resources/db/migration/V15__payments.sql`
- tests: `backend/src/test/java/com/getlancer/integration/DeliveryIntegrationTest.java`, `backend/src/test/java/com/getlancer/integration/PaymentIntegrationTest.java`, `tests/connected/business.spec.ts`, `tests/browser/delivery.spec.ts`, `scripts/smoke-docker.mjs`

### analytics

Allow-listed opt-in public events with event dedup/session hashing/context minimization, authoritative outcome events and basic builder/founder metrics exist. Full liquidity, concentration, slot cohort, repeat-client and quality/transparency dashboards are incomplete; no production analytics completion claim.

- frontend: `lib/analytics.ts`, `app/components/proof-events.tsx`, `app/components/builder-event.tsx`, `app/components/analytics-preference.tsx`, `app/components/admin-tools.tsx`, `app/workspace/page.tsx`
- backend: `backend/src/main/java/com/getlancer/analytics/AnalyticsService.java`, `backend/src/main/java/com/getlancer/inquiries/InquiryService.java#analytics`, `backend/src/main/java/com/getlancer/admin/AdminService.java#metrics`, `backend/src/main/java/com/getlancer/jobs/Maintenance.java`
- database: `backend/src/main/resources/db/migration/V1__marketplace.sql`, `backend/src/main/resources/db/migration/V8__release_workflows.sql`
- tests: `backend/src/test/java/com/getlancer/security/ReleaseWorkflowTest.java`, `backend/src/test/java/com/getlancer/security/V1SafeguardsTest.java`, `scripts/smoke-docker.mjs`

### ui

Installed shadcn/Radix primitives, readable surfaces, labelled core forms, focus/motion, empty/error/loading patterns and three viewport tests exist. Complete WCAG/screen-reader/cross-browser certification has not been performed; personal workspace retains protected state on refresh failure in baseline.

- frontend: `DESIGN.md`, `app/globals.css`, `app/components/workspace.css`, `app/components/workspace-form.tsx`, `app/components/workspace-frame.tsx`, `app/loading.tsx`, `app/error.tsx`, `app/not-found.tsx`
- backend: `backend/src/main/java/com/getlancer/shared/Errors.java`, `backend/src/main/java/com/getlancer/shared/Support.java`
- database: No persistent schema needed for this presentation layer.
- tests: `tests/browser/workspaces.spec.ts`, `tests/browser/delivery.spec.ts`, `tests/connected/business.spec.ts`, `tests/ui-components.test.mjs`

### seo

Index gating, approved-only public metadata/canonicals/structured data, noindex private/search, gated taxonomy landing pages and sitemap exist. Team pages not included in sitemap and complete shared-card/CWV/structured-data validation remains outstanding.

- frontend: `lib/seo.ts`, `app/page.tsx`, `app/products/[slug]/page.tsx`, `app/builders/[slug]/page.tsx`, `app/components/structured-data.tsx`, `app/sitemap.xml/route.ts`, `app/robots.txt/route.ts`, `app/solutions/[slug]/page.tsx`, `app/technologies/[slug]/page.tsx`
- backend: `backend/src/main/java/com/getlancer/products/ProductService.java#search`
- database: `backend/src/main/resources/db/migration/V1__marketplace.sql`, `backend/src/main/resources/db/migration/V3__product_taxonomy.sql`
- tests: `tests/marketplace-ui.test.mjs`, `tests/rendered-html.test.mjs`, `tests/connected-mode.test.mjs`

### architecture

Real Java 17/Spring modular monolith and PostgreSQL/Flyway/JDBC, feature controllers/services, frontend REST proxy, S3/email exist. JDBC is an explicit implementation adaptation to the suggested JPA; no Kafka/gRPC/Kubernetes/runtime demo seeds. External topology is not provisioned by code.

- frontend: `package.json`, `lib/api.ts`, `lib/server.ts`, `app/api/v1/[...path]/route.ts`
- backend: `backend/pom.xml`, `backend/src/main/java/com/getlancer/Application.java`, `backend/src/main/java/com/getlancer/config/ProductionConfiguration.java`
- database: `backend/src/main/resources/db/migration/V1__marketplace.sql`, `backend/src/main/resources/db/migration/V9__private_data_api_boundary.sql`
- tests: `backend/src/test/java/com/getlancer/architecture/BackendArchitectureTest.java`, `.github/workflows/ci.yml`

### api

Versioned REST, stable structured errors/field errors, bounded pagination, contract aliases, signed uploads and object authorization exist. Not every filter/ref list has a paginated shape; 429 filter-level errors omit correlation ID and explicit OpenAPI generation is not present.

- frontend: `lib/api.ts`, `app/api/v1/[...path]/route.ts`
- backend: `backend/src/main/java/com/getlancer/shared/Pages.java`, `backend/src/main/java/com/getlancer/shared/Errors.java`, `backend/src/main/java/com/getlancer/auth/AuthController.java`, `backend/src/main/java/com/getlancer/products/ProductsController.java`, `backend/src/main/java/com/getlancer/inquiries/InquiryContractController.java`
- database: `backend/src/main/resources/db/migration/V1__marketplace.sql`, `backend/src/main/resources/db/migration/V2__guardrails.sql`
- tests: `backend/src/test/java/com/getlancer/integration/MarketplaceIntegrationTest.java`, `tests/connected-mode.test.mjs`, `scripts/verify-connected-frontend.mjs`

### ops

Docker/staging manifest, readiness/liveness, protected configuration, CI isolated services, restore rehearsal and runbooks exist. Backend deployment is prohibited by current user boundary; daily/PITR/object backup scheduling, real alert routing, production rollback/drills are owner operations pending.

- frontend: `scripts/verify-staging.mjs`
- backend: `backend/Dockerfile`, `backend/src/main/java/com/getlancer/config/ProductionConfiguration.java`, `backend/src/main/resources/application.properties`
- database: `ops/verify-private-schema.sql`, `scripts/verify-restore-ci.mjs`
- tests: `.github/workflows/ci.yml`, `scripts/smoke-docker.mjs`, `tests/staging-check.test.mjs`
- operations: `ops/README.md`, `ops/STAGING.md`, `compose.staging.yaml`, `compose.yaml`

### config

Server-only settings, private env examples/setup, fail-fast hosted configuration and trusted proxy secret exist. TTLs and several maximum limits are fixed constants; deploy-provider encryption/rotation/secrets and protected environments require owner configuration.

- frontend: `lib/deployment-mode.ts`, `scripts/check-environment.mjs`, `scripts/local-config.mjs`, `.env.example`
- backend: `backend/src/main/java/com/getlancer/config/ProductionConfiguration.java`, `backend/src/main/java/com/getlancer/config/Bootstrap.java`, `backend/src/main/resources/application.properties`
- database: `compose.yaml`, `compose.staging.yaml`
- tests: `tests/local-tools.test.mjs`, `tests/deployment-mode.test.mjs`, `backend/src/test/java/com/getlancer/profiles/ProfileAndBootstrapTest.java`

### testing

Real PostgreSQL integration/API/concurrency tests, preview browser checks and connected hiring+embedded V3 delivery tests exist. This audit ran no gates: paths are inspected test source, not fresh pass results. Full original six browser E2E journeys, non-Chromium/manual a11y/load/upgrade migration evidence is incomplete.

- frontend: `playwright.config.ts`, `playwright.connected.config.ts`
- backend: `backend/pom.xml`, `backend/src/test/java/com/getlancer/testing/TestDatabaseGuard.java`
- database: `compose.yaml`, `.github/workflows/ci.yml`
- tests: `backend/src/test/java/com/getlancer/integration/MarketplaceIntegrationTest.java`, `backend/src/test/java/com/getlancer/integration/TeamsIntegrationTest.java`, `backend/src/test/java/com/getlancer/integration/BusinessIntegrationTest.java`, `backend/src/test/java/com/getlancer/integration/DeliveryIntegrationTest.java`, `backend/src/test/java/com/getlancer/integration/PaymentIntegrationTest.java`, `tests/browser/workspaces.spec.ts`, `tests/connected/business.spec.ts`, `scripts/smoke-docker.mjs`, `scripts/verify-restore-ci.mjs`

### legal

Draft policy notices, rights acceptance/version records, consent controls, report/appeal/export/deletion tooling and production approval gate exist. Approved contacts/text/licenses/support/refund/KYC/tax/vendor/age policies require owner legal review. Current policy wording still says all agreements/payments are external, inconsistent with V3.

- frontend: `app/policies/page.tsx`, `app/components/auth-form.tsx`, `app/components/privacy-operations.tsx`
- backend: `backend/src/main/java/com/getlancer/accounts/PrivacyService.java`, `backend/src/main/java/com/getlancer/auth/AuthService.java`, `backend/src/main/java/com/getlancer/config/ProductionConfiguration.java`
- database: `backend/src/main/resources/db/migration/V1__marketplace.sql`, `backend/src/main/resources/db/migration/V8__release_workflows.sql`
- tests: `backend/src/test/java/com/getlancer/auth/AuthFlowTest.java`, `tests/local-tools.test.mjs`
- operations: `ops/STAGING.md`, `docs/09_V3_COMMERCIAL_WORKFLOWS.md`, `docs/V3_5_CONTRACT.md`

## Owner and future boundaries

Owner acceptance includes current-head CI and real provider staging, approved commercial/source licenses/refund/support/tax/KYC policies, actual operator contacts and retention/vendor/eligibility decisions, measured load/CWV/recovery, actual alerts and daily/PITR/object backups, and complete cross-browser/manual accessibility. These cannot be completed with frontend sample data or invented seller accounts. Only the demo frontend may be cloud hosted by this task.

Notification preferences/marketing plans, Pro paid capacity/custom domains/portfolios, trusted auto-publish, identity/contribution/runtime verification, algorithmic fraud rings, A/B infrastructure, native apps and hosted code are volume/roadmap features; they remain explicitly future unless product scope is changed. Public marketplace validation targets (real builders/inquiries/paid engagements) are business outcomes, not seed data.

The conceptual data model is implemented with deliberate adapters: JDBC rather than suggested JPA, direct safe proof links rather than a dedicated product_links table, verified email claiming client requests rather than an additional client_profiles table, and owned products shared with teams by explicit consent rather than organization ownership. Those adaptations are not hidden as literal schema completion. Durable analytics consent history and optional notification-preference tables are not present in baseline.

## Unlazy four-pass evidence

1. Complete: all 17 texts read; source clauses and original IDs retained with current hashes.
2. Expert reread: phase/precedence and commercial/legal boundary checked; no source presence promoted to live acceptance.
3. Defect hunt: actionable full-stack findings sent to root; embedded connected V3 browser flow confirmed, eliminating a false missing-test finding.
4. Polish: source-line/ID/evidence checks complete, with explicit current/mixed/owner/future dispositions.

No tests, migrations, account mutations, external deployment, commits or pushes were performed by this audit leaf. Root must attach final current-head execution evidence and reconcile incoming work before release.
