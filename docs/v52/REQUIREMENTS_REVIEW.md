# Requirements source audit — 22 October 2026

All 68 requirements were reviewed against the original roadmap acceptance text: 246 independently recorded obligations. This completes the source-review pass, not acceptance certification. Nine requirements have source support for every reviewed obligation; 59 retain implementation, verification or external evidence items. See [clause-review.json](clause-review.json) for exact clauses, code/test references, historical findings and subsequent fixes.

Authority: `GetLancer_V4_5_to_V5_2_Full_Plan.pdf`, pages 18–30. The audit records its SHA-256 and baseline commit `25491d459c03e4496373cb9e55e31c08677b719e`. `SUPPORTED` means code and specific authored assertions were inspected. It does not mean those tests ran in this review, that a content owner approved rights, or that production behavior was observed. Baseline findings stay visible after fixes to preserve the review trail.

## Corrections delivered by this audit

- Preserve the exact reviewed uploaded component ZIP and verify its archive checksum; current eligibility and expected archive hash gate anonymous download.
- Clear stale clipboard success before retrying a copy.
- Remove ineligible component links from public nested education snapshots as well as their outer aliases, preserving stored source rights.
- Use reviewed execution mode for component analytics cohorts.
- Reject foreign, replayed, unordered or misbound native lab events before publishing output; cursor changes are atomic and serialized.
- Associate component draft API errors with controls and reveal the correct step before focusing an error.
- Add bounded `--catalogue products|components|college-projects` collection and persist the target in evidence. The existing products default is preserved. Legacy product-only load logs cannot pass the new-catalogue gate.

Existing paid creator publishing slots remain separate from free consumer source and standard demo policy; no paid publishing feature was removed.

## Baseline implementation and verification gaps

These are the gaps recorded at the audit baseline. The five coding areas now have implementations and regression coverage in [the coding follow-up](IMPLEMENTATION_COMPLETION.md); the table and per-clause source record remain historical evidence. Exact revised-head CI and external acceptance are tracked separately.

| Area | Required next evidence or implementation |
|---|---|
| FR-023 | Active-run architecture/state/event presentation; the existing graph is Replay-only. The native event trust fix does not supply that UI. |
| FR-031 | Reuse safe exact-URL health facts to label broken external demos. Unchecked links are currently honestly unverified, but never labelled broken. |
| FR-042 | Component-specific report binding to immutable revision/hash and report-driven new/current run revocation. Existing education reporting and direct component revocation are distinct. |
| FR-017 / FR-043 / FR-046 | Exact framework/runtime certification, maintainer/reproduction attestations and current-MFA limits/certification workflow require implementation or an explicit reviewed contract amendment. Historical adapters and existing publishing behavior are preserved. |
| NFR-006 / NFR-008 / NFR-009 | Importer redirect/rebinding fault cases; multi-actor lab export, deletion and automatic retention checks; exact 50-way PostgreSQL quota/memory/budget contention. |
| NFR-014 / NFR-022 | New-domain package/hash/outbox/purchase restore assertions and combined shutdown/withdrawal/restore/rollback coherence; current disposable CI recovery covers legacy counts and retained maintenance/hosting outcomes. |
| NFR-001 / NFR-002 / NFR-016 | Manual screen-reader, keyboard, actual 200% zoom and timed-lab assessment; agreed mobile performance thresholds across new routes; complete five-width/state/focus matrix. |
| NFR-012 / NFR-013 | Actual 10,000-row new-catalogue production load/query plans and separate source-service availability probes. Catalogue selector tests are collector behavior checks, not performance or availability results. |
| Other specific assertions | Unknown-language onboarding, second-run security reset isolation, college-to-inquiry current eligibility, pause-to-reservation, stale/orphan alert generation and route-family outage cases remain documented per clause. |

No static or unit check closes real KVM/image/network/isolation/cleanup qualification, hosted startup benchmarks, seven-day provisioning soak, field p75, operator budget approval, live payments/legal activation, production domain/TLS, production recovery/RPO/RTO/rollback or human owner sign-off. Those remain under [LAUNCH_GATES.md](LAUNCH_GATES.md).

The requested Codex CLI review could not run because the binary is absent. Native source reviewers were used; no independent CLI or cross-model verdict is claimed.

## Subsequent software acceptance work

The component state matrix (FR-006), clean ZIP setup reproduction (FR-008), and separate originality/quality/licence/execution moderation assessments (FR-012) now have implementations and focused observing tests in [the software acceptance follow-up](SOFTWARE_ACCEPTANCE.md). The baseline clause counts below remain historical; current-head CI and the seven external launch gates remain separate evidence.

## Per-requirement disposition

Every row below is reviewed and remains uncertified. A source-supported row can still need execution of its authored tests and responsible-owner acceptance.

| Requirement | Topic | Baseline obligation review | Subsequent correction |
|---|---|---|---|
| FR-001 | Discovery | 3 supported | — |
| FR-002 | Free policy | 2 supported, 1 partial | — |
| FR-003 | Search | 3 supported | — |
| FR-004 | Frontend scope | 2 supported, 1 partial | — |
| FR-005 | Preview | 2 supported, 1 partial | — |
| FR-006 | Component states | 1 partial, 2 supported | — |
| FR-007 | Source access | 2 supported, 2 gap | F1, F2 |
| FR-008 | Setup | 1 partial, 1 external, 1 supported | — |
| FR-009 | Licensing | 2 supported, 1 partial | — |
| FR-010 | Versioning | 3 supported | — |
| FR-011 | Contributions | 3 supported | — |
| FR-012 | Moderation | 2 supported, 2 partial | — |
| FR-013 | Talent proof | 2 partial | — |
| FR-014 | Capacity | 3 supported | — |
| FR-015 | Saved items | 3 supported | — |
| FR-016 | Backend scope | 2 supported, 2 external | — |
| FR-017 | Language coverage | 1 partial, 2 supported, 1 external | — |
| FR-018 | Real execution | 2 partial, 2 supported, 1 external | — |
| FR-019 | Request console | 4 supported | — |
| FR-020 | Redis | 1 partial, 3 supported, 1 external | — |
| FR-021 | Security modules | 4 supported, 1 external | — |
| FR-022 | Payment modules | 4 supported, 1 partial, 1 external | — |
| FR-023 | Execution evidence | 1 partial, 2 supported, 1 gap, 1 external | F5 |
| FR-024 | Scenarios | 2 supported, 1 partial, 1 external | — |
| FR-025 | Lifecycle | 4 supported, 1 external | — |
| FR-026 | Free compute limits | 3 supported, 1 partial, 1 external | — |
| FR-027 | Local reproduction | 2 supported, 2 partial | — |
| FR-028 | College categories | 2 supported, 1 partial | — |
| FR-029 | College modes | 4 supported | — |
| FR-030 | Project metadata | 4 supported | — |
| FR-031 | Source/demo disclosure | 2 supported, 1 partial, 1 gap | — |
| FR-032 | Student privacy/team | 4 supported | — |
| FR-033 | Showcase contribution | 3 supported, 1 partial | — |
| FR-034 | Paid integration | 4 supported | — |
| FR-035 | Purchase support | 4 supported | — |
| FR-036 | Analytics evidence | 2 supported, 1 partial | — |
| FR-037 | AI evidence | 2 supported, 1 partial | — |
| FR-038 | IoT evidence | 2 supported, 1 partial | — |
| FR-039 | Data/AI hosting | 2 partial, 2 supported | — |
| FR-040 | IoT simulation | 4 supported | — |
| FR-041 | Reuse relationships | 2 supported, 1 gap | F3 |
| FR-042 | Reports | 2 partial, 1 supported | — |
| FR-043 | Operations | 3 partial, 1 external | — |
| FR-044 | Measurement | 2 supported, 1 gap, 1 partial, 1 external | F4 |
| FR-045 | Demo honesty | 2 partial, 1 supported | — |
| FR-046 | Extension workflow | 3 partial | — |
| NFR-019 | Maintainability | 3 supported, 1 partial | — |
| NFR-020 | Test quality | 2 supported, 1 partial, 1 external | — |
| NFR-022 | Release observability | 1 supported, 3 partial, 1 external | F4 |
| NFR-003 | Isolation | 1 supported, 1 partial, 1 external | — |
| NFR-004 | Authorization | 3 supported | F3 |
| NFR-005 | Financial integrity | 3 supported | — |
| NFR-006 | Network security | 2 partial, 1 external | — |
| NFR-007 | Resource limits | 2 supported, 1 external | — |
| NFR-008 | Privacy | 1 supported, 3 partial | — |
| NFR-009 | Concurrency | 2 supported, 1 gap | — |
| NFR-010 | Cost | 1 supported, 2 external | — |
| NFR-014 | Recovery | 1 external, 2 partial | — |
| NFR-017 | Licence provenance | 2 partial, 1 supported | — |
| NFR-018 | Data consistency | 2 supported, 1 external | — |
| NFR-021 | Data/model safety | 2 partial, 2 supported | — |
| NFR-001 | Usability/accessibility | 2 partial, 2 external | F6 |
| NFR-002 | Web performance | 3 partial, 1 external | — |
| NFR-011 | Runtime latency | 1 supported, 1 external | — |
| NFR-012 | Catalogue API | 1 gap, 1 supported, 1 external | F7 |
| NFR-013 | Availability | 1 external, 1 supported, 1 gap | F7 |
| NFR-015 | Reproducibility | 2 partial, 2 supported | — |
| NFR-016 | Responsive quality | 4 partial | — |

## Verification record

The local build and TypeScript check passed. The provider suite passed 39 tests; the full Node run passed 377 tests before the final positive CLI regression was added, and the operations collector suite then passed 34 tests including both CLI entry points. Scoped lint for changed JavaScript/TypeScript files passed; repository-wide lint still reports pre-existing issues outside this patch. Local Chromium installation failed, so no local browser pass is claimed. Exact-head CI, including PostgreSQL and browser regressions, remains the publishing gate.
