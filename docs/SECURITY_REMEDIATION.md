# Security plan reconciliation: V1–V4.5

This reconciles all 67 numbered sections of the supplied security plan against the security worktree based on `04b621b62144e1973447b56f72e96615326e0857` (the verified V1–V4.5 public-main source). V4.6 contribution work is paused and preserved separately; none of its proposed behavior is evidence for this reconciliation. The section numbers and titles below reproduce the supplied plan, including section 4's umbrella title.

**Repository implementation is not production security completion.** No production deployment, live provider activation, installed branch protection, production role provisioning, secret rotation, monitoring installation, backup restoration, or external penetration test is claimed here. Independent source review is not an external model/runtime installation or an operational penetration test.

## Evidence and status boundary

These execution results were recorded before publishing the residual scanner fixes. Use [PR 23](https://github.com/JetyChodipilli/getLancer/pull/23) for the latest head's checks; every later source revision requires its own complete successful workflow. Status labels in the table describe repository control ownership and do not certify production activation.

- Revision `3336d08d68e8d76dc0a71516474765495b4cdedc`, [run 37574756910](https://github.com/JetyChodipilli/getLancer/actions/runs/37574756910), passed **425 backend tests, 0 failures/errors/skips**, including all OAuth, database-role, typed-request, authorization and data-exposure regressions. Its responsive frontend job also passed. Four browser skips are duplicate phone/tablet instances of two desktop viewport/animation-matrix tests; the full matrices execute in the desktop project.
- That run's Java runtime inventory scanned **113 package versions**, retaining one exact Spring advisory with passing application-specific evidence and zero blocking findings. Runtime npm audit reported zero vulnerabilities. These results apply to that revision and do not certify later inputs or provider infrastructure.
- The original 22 semantic findings reduced to **3 remaining source findings** plus **3 reviewed GET applicability assessments** backed by actual same-run JUnit. The remaining static SQL selection, script-tag recognition and local configuration creation paths have source fixes and negative regressions awaiting a fresh scan. The publisher image's 11 high findings all came from unused bundled npm libraries; the runtime now removes unused package managers and requires a rebuilt-image scan. No image finding allowance was added.
- The secret scanner's 21 current/history matches were public source-file SHA-256 values in the original SAST manifest. Schema 2 uses separate strict path/digest entries; the history allowance binds only those 21 exact reviewed digests, the exact manifest path and generic-key rule. A fresh current/history scan remains required; this does not claim live credential rotation.
- Local source checks passed: `node scripts/check-security-contract.mjs` reports **31 controllers, 108 typed JSON handlers and 256 classified application routes**; `node ops/database/check-permissions.mjs` reports **93 classified migration tables**. These are structural checks, not PostgreSQL permission execution or Java compilation.
- `node --test tests/container-security-gate.test.mjs` passed **6 tests**. This verifies scanner-result rejection and narrow disposition behavior using test reports; it is not an actual image scan. Actual current-image, secret, SAST, dependency, browser and backend results remain CI gates.
- `node --test tests/sast-security-gate.test.mjs` passed **5 tests** after independent review hardened SARIF validation, rule resolution, severity handling and suppression behavior. This verifies the result gate with fixtures, not a clean semantic scan of the application.
- The combined semantic-gate/disposition check, `node --test tests/sast-security-gate.test.mjs tests/sast-dispositions.test.mjs`, passed **12 tests** (the same 5 gate tests plus 7 disposition tests). These fixtures verify exact matching, source/test inventory hashes, fresh same-commit/run/attempt evidence, review expiry, and rejection of failed/skipped/substituted required tests. The eight new real-PostgreSQL OAuth regressions and administrator evidence-read regression are present in source and required by the disposition mechanism; they passed in revision `3336d08d`; later revisions still require exact-source execution. Actual XML parsing rejects test names embedded in logs/CDATA/comments, and Maven/verification build inputs are pinned alongside production and test sources.
- The 7 October continuation local Node suite passed **152 tests, 0 failures and 0 skipped**. TypeScript, production build, the 107-asset public frontend security check, route/DTO inventory and database-grant source checks also passed. This does not substitute for current Java/PostgreSQL, connected-browser or scanner execution.
- Linked regression classes/specifications are present in source. Unless a result is explicitly stated above, the link identifies available coverage, not an executed passing result. Exact-source CI artifacts must be attached before release.

Status describes where the control belongs, not a green completion score: **repository change** means this change supplies or strengthens it; **existing control** means it was already implemented and is retained; **operator prerequisite** means installation, policy approval or execution outside the repository is still needed. A row may also identify a remaining limitation.

### 7 October continuation

The published source tree `4e5105b22046035357093244fa653d2e4dabf963` was matched exactly before editing. Revision `b42f95d8892c8ff2eabcabab3b06f5e3373e8f49`, [run 37576082152](https://github.com/JetyChodipilli/getLancer/actions/runs/37576082152), passed backend, responsive frontend, Java SAST, current/history secrets and container scans. Its JavaScript SAST and connected-service gates failed; those results are retained as failed evidence, not described as a successful release.

The connected smoke check sent a business-request response object, including response-only metadata, to a strict typed update endpoint. It now projects only the nine editable command fields, matching the existing frontend behavior. Unknown-field rejection stays enabled. Local configuration setup now uses one atomic open/create and the same descriptor for validation and mutation; symlinks, hardlinks and non-regular files are refused. A configuration is initialized in memory and written only after provider checks, and existing credential preservation remains tested. The test captures permissions and contents from one held descriptor before and after rerun, retaining first-creation and rerun coverage while eliminating a test-only pathname check/use pattern. No SAST rule or path suppression was added for these changes.

Independent read-only verification found no demonstrated application exploit behind the four previous filesystem race alerts: the existing setup already used no-follow/exclusive flags and descriptor I/O, and two alerts were isolated test fixtures. The simplified implementation removes the retry pattern and adds hardlink refusal as a precaution; it is not reported as proof of a previously exploitable production vulnerability. Final acceptance still requires every job on the revised PR head.

Codex Security Cloud was explicitly attempted but reported `security_cloud_access_denied` and no matching Cloud environment. No Cloud scan, finding closure, credential change or permission change was performed. Repository scans remain mandatory. Alpha's installed fixture package lacks its required `references/CORE.md` and `references/POLICY.md`; no security verification is attributed to it. Guard was applied to the isolated security checkout; V4.6 and production infrastructure remain outside the edit scope.

Ponytail audit/review retains the existing optional dead sidebar/chart cleanup in [PONYTAIL_AUDIT.md](PONYTAIL_AUDIT.md); it does not remove authentication, validation, evidence, or application regression checks. These are independent native source reviews, not a professional penetration test. Production acceptance, external secrets/key rotation, installed repository rules and provider verification remain explicitly separate in [the release handoff](../ops/SECURITY_RELEASE.md).

Revision `c4286f67e6847b07494351a16dff04454d7b0723`, [run 37602818789](https://github.com/JetyChodipilli/getLancer/actions/runs/37602818789), passed 425 backend tests, 152 Node tests, 209 responsive browser cases (four intentional duplicate matrix cases skipped), both SAST/secret jobs and both runtime image gates. The corrected connected service smoke journey also passed. Connected browser acceptance had 14 passes and four failures; the restore step was skipped, so this is not a complete successful workflow. All four traces showed HTTP 429 from the shared protected-read budget. Independent trace review confirmed distinct client/administrator identities and successful private-package denial; the default page screenshot was not evidence of session leakage.

Connected tests now isolate rate counters only between journeys in the verified disposable CI database. The helper requires CI mode, the exact fixture/Compose project, one worker, a concrete container with matching project/service labels, a dedicated project-owned volume and exclusively synthetic accounts before touching `rate_buckets`. Mutation targets the inspected container ID. Production rate limits, actual authorization, test assertions and all counters within each journey remain unchanged. Guard regressions reject non-CI, mismatched, parallel, shared-volume and non-fixture targets. The next published head still requires its own complete workflow.

## All 67 supplied sections

| § | Exact section title | Status | Implementation / source anchors | Regression / evidence and remaining requirement |
|---|---|---|---|---|
| 1 | Purpose | repository change | This reconciliation; [CI] | Implementation, source evidence and operator gates are separated; complete security acceptance remains pending. |
| 2 | Security Principles | repository change | [chain], [authorization], [role guard], [DB permissions] | [authorization tests] and [DB role tests] cover layered denial; deployed TLS/grants remain operator work. |
| 3 | Security Priorities | existing control | [release gates], [operations specification] | Preserve P0 before public, P1 before commercial, P2 before scale and continuous P3 checks; no gate is declared complete here. |
| 4 | Confirmed Security Findings | repository change | [POM], [chain], [session filter], [principal], [authorization] | Spring Security starter, method security and centralized access decision; [security architecture] verifies route inventory, [authorization tests] exercises 401/403. |
| 5 | SEC-AUTH-002 — Fail-Open-by-Omission Authorization | repository change | [chain], [authorization], [route policy], controller `@PreAuthorize` | Unknown routes deny; [request architecture] requires declarative controller policy; domain owner/member/party checks remain necessary. |
| 6 | SEC-AUTH-003 — Missing API Authorization Classification | repository change | [authorization matrix], [route policy], [route contract], [security architecture] | Matrix covers all 259 unique method/route entries (256 API plus 3 health), all 11 required fields, domain gates and delegate sources. It documents selective audits/service quotas; maintenance config now correctly has SESSION policy. Inventory/source verification is not runtime CI. |
| 7 | SEC-AUTH-004 — IDOR / BOLA Prevention | existing control | [team policy], [inquiry policy], [business repository], [delivery repository], [commerce repository] | [business tests], [team tests], [delivery tests], [commerce tests], [authorization tests] exercise scoped access/revocation. The entire required resource/actor matrix must pass in CI. |
| 8 | SEC-AUTH-005 — Authentication Principal Hardening | repository change | [principal], [session filter], [security facade] | Principal contains identity/roles/MFA/time, no credential or session token; current account state is checked. Request-based compatibility facade remains; full `@AuthenticationPrincipal` service migration is not claimed. |
| 9 | SEC-DATA-001 — Sensitive Columns Loaded by `SELECT *` | repository change | [security facade], [response models], [account service], [trust service] | [source contract] and [security architecture] prohibit wildcard SQL projections; [data exposure tests] proves future fields stay out of user/export responses. |
| 10 | SEC-DATA-002 — Raw Database Maps / Response Maps | repository change | [response models], [account service], [privacy service], [payment service] | Sensitive responses use concrete projections/DTOs; [data exposure tests] checks exact keys. Fixed allowlisted maps and bounded legacy snapshots remain; universal elimination of response maps is not claimed. |
| 11 | SEC-VAL-001 — Missing Typed API Validation | repository change | [POM], [request models], [auth requests], [chain], [errors] | 108 JSON handlers use concrete validated DTOs; strict unknown fields/coercions/trailing tokens; [request architecture] and [typed request tests] cover safe malformed 400s. |
| 12 | SEC-MFA-001 — Plaintext TOTP Secret | repository change | [MFA envelopes], [MFA upgrade], [V26], [MFA runbook] | AES-256-GCM, user/purpose/version binding and nonce uniqueness; [MFA tests], [auth hardening tests] cover tampering/upgrade/rotation. External versioned keyring and migration activation remain required. |
| 13 | SEC-MFA-002 — Administrator Authorization | repository change | [authorization], [principal], [V28], [security facade] | Central ADMIN plus current MFA; privileged writes/package exports require the 15-minute issuance window. [authorization tests] includes stale MFA denial; principal rejects future issuance times. Old MFA sessions are revoked by V28. |
| 14 | SEC-SESSION-001 — Session Lifecycle Hardening | repository change | [auth service], [session cookies], [session filter], [V28] | Hashed random tokens, expiry, rotation and revocation, hosted `__Host-` cookies; [auth hardening tests] checks reset/logout/replay and [boundary tests] checks cookie names. |
| 15 | SEC-CSRF-001 — Browser Mutation Protection | repository change | [chain], [browser filter], [proxy] | Documented exact Origin plus non-simple header and SameSite equivalent; standard CSRF remains outside verified exceptions, exact signed webhook exception. [authorization tests], [boundary tests] cover hostile/missing evidence. |
| 16 | SEC-CORS-001 — CORS Policy | repository change | [browser filter], [production config] | Exact configured origin, bounded preflight methods/headers, no wildcard credentials; sibling-origin/preflight cases in [authorization tests]. Verify actual storage/proxy CORS before release. |
| 17 | SEC-ABUSE-001 — Public Endpoint Abuse Controls | repository change | [rate limits], [auth service], [browser filter] | PostgreSQL atomic shared buckets; IP/session/route limits and dedicated identity preflights for login/MFA/reset/resend; [auth hardening tests], [discovery tests]. Hosting gateway uses its authenticated service path. |
| 18 | SEC-WEB-001 — CSP Inline JavaScript | repository change | [frontend headers], [worker] | Per-request script nonce, `script-src-attr 'none'`, explicit providers, fixed trusted-recipe hashes; script `unsafe-inline` removed. [header tests], [browser security tests] cover hydration, nonce positive control and blocked script/handler. Style inline permission remains explicit. |
| 19 | SEC-XSS-001 — XSS Prevention Standard | existing control | [safe URLs], [frontend headers], [worker], [proof editor] | React escaping and bounded trusted previews; uploaded arbitrary scripts are not trusted recipe hashes. [safe URL tests], [browser security tests], [rendered HTML tests] provide hostile URL/inline/preview coverage. |
| 20 | SEC-TLS-001 — Production HTTP Backend | repository change | [production config], [deployment mode], [server client], [environment check] | Hosted frontend/backend/storage HTTPS, JDBC `verify-full`, SMTP TLS identity checks; [production config tests], [deployment tests]. Actual certificates and provider endpoints require operator verification. |
| 21 | SEC-CFG-001 — `.env` Auto-Import | repository change | [common config], [local config], [hosted config], [production config] | Packaged `.env` import exists only in explicit local profile; hosted external config imports fail. [production config tests] loads a poisoned env fixture to distinguish local/hosted behavior. |
| 22 | SEC-CFG-002 — Hard-Coded Administrator Identity | repository change | [bootstrap], [common config], [env example], [MFA runbook] | No personal default email; explicit strong bootstrap or verified existing administrator. [bootstrap tests], [production config tests]. Retire bootstrap password/seed after provisioning. |
| 23 | SEC-SECRETS-001 — Secret Management | repository change | [env example], [CI], [Gitleaks config], [frontend artifact check], [MFA runbook] | Redacted current-tree/full-history scans and client artifact checks are configured. Live key/credential custody, access review and any exposed-secret rotation remain operator requirements. |
| 24 | SEC-DB-001 — PostgreSQL Least Privilege | repository change | [DB role guard], [DB roles], [DB permissions], [DB runbook] | Four distinct migration/runtime/backup/read-only roles; deny elevated identity/ownership/DDL/future tables. [DB role tests] exercises actual PostgreSQL roles and shared locks; provision and verify hosted credentials separately. |
| 25 | SEC-DB-002 — RLS Defense-in-Depth | repository change | [DB permissions], [DB role guard], [DB runbook] | Operation-specific policies/grants constrain the trusted server role. **This is not tenant RLS:** runtime intentionally reads application rows across users; Java authorization remains the tenant boundary. [DB role tests] checks grants and denial. |
| 26 | SEC-SQL-001 — SQL Injection Prevention | existing control | [business repository], [commerce repository], [product repository], [support] | Bound query values and existing allowlists are retained; [CI] semantic SAST/source rules add regression pressure. Parameterization does not replace owner predicates. |
| 27 | SEC-SSRF-001 — Server-Side Request Forgery | existing control | [URL rules], [demo health], [publisher client] | Public-address checks, bounded DNS, pinned validated destination, revalidated redirects, bounded time/response; [trust tests], [publisher tests]. Provider egress policy is an operator defense. |
| 28 | SEC-UPLOAD-001 — File Upload Security | existing control | [media service], [commerce storage], [ops guide] | Private random staging keys; byte/pixel/magic/decode checks, re-encoding and private authorized reads; [marketplace tests], [storage tests]. Activate bucket privacy/CORS/lifecycle and cleanup. |
| 29 | SEC-ARCHIVE-001 — ZIP / Source Archive Security | existing control | [source archive], [static archive], [publisher server] | Bounded entries/compression/expansion, traversal/symlink/encryption/nesting rejection; no uploaded package install/build/execute. [source archive tests], [static archive tests], [publisher node tests]. |
| 30 | SEC-PAY-001 — Payment Trust Boundary | existing control | [payment service], [commerce payment service], [maintenance billing], [payment repository] | Server-priced provider facts, signature/mode/seller/amount checks and immutable ledger/idempotency; [payment tests], [provider tests], [maintenance tests]. Actual merchant approval/settlement verification remains external. |
| 31 | SEC-WEBHOOK-001 — Webhook Security | repository change | [browser filter], [payment service], [commerce payment service], [maintenance billing] | Exact signed POST exceptions; streamed 64 KiB cap; raw-byte signature before JSON, durable identity/replay conflict handling. [request architecture], [payment tests], [provider tests]. |
| 32 | SEC-OAUTH-001 — OAuth Security | repository change | [Google auth], [Google tokens], [GitHub auth], [auth providers] | Retained state/PKCE/Google identity controls; browser cookie is cleared only after atomic valid state consumption. [Auth hardening tests] adds 8 PostgreSQL cases for mismatch, expiry/provider, racing/repeated replay and failed exchange; current CI pending. [Google tests], [Google account tests], [GitHub tests] remain; real providers require acceptance. |
| 33 | SEC-PASS-001 — Password Security | repository change | [auth requests], [auth service], [chain] | BCrypt cost 12, new password minimum 12 characters and maximum 72 UTF-8 bytes; legacy login remains compatible; reset request non-enumerating. [auth hardening tests] covers UTF-8 limits and one-use reset. |
| 34 | SEC-REDIRECT-001 — Open Redirect Prevention | existing control | [Google auth], [GitHub auth], [auth form], [safe URLs] | Callbacks return fixed relative app paths; provider navigation validates exact provider origin. [GitHub tests], [safe URL tests]; arbitrary return URL input is not introduced. |
| 35 | SEC-STORAGE-001 — Object Storage | operator prerequisite | [media service], [commerce storage], [DB runbook], [ops guide] | Authorized private no-store reads and scoped short-lived signatures exist. Operator must verify private bucket/no public ACL or listing, restricted credentials, HTTPS, CORS and lifecycle using real provider. |
| 36 | SEC-DEMO-001 — Hosted Demo Isolation | existing control | [hosting config], [frontend headers], [publisher server], [hosting runbook] | Isolated demo origins, credential-free static runtime and authenticated publisher/gateway boundaries; [hosting tests], [publisher tests], [publisher node tests]. Separate production domain/network/credentials require installation review. |
| 37 | SEC-LOG-001 — Logging | repository change | [request IDs], [errors], [proxy], [audit] | Server-generated request IDs cross proxy/backend/error/audit; bounded errors omit exception messages/SQL/secrets. [boundary tests], [authorization tests]. Host log redaction, access and alert destinations remain external. |
| 38 | SEC-AUDIT-001 — Security Audit Trail | repository change | [audit], [browser filter], [V27], [DB permissions], [authorization matrix] | Finite event/result/target fields; actor UUID and request ID, no body/token/email/IP. Runtime INSERT/SELECT only; anonymous denials share a budget. All 6 classified export/package/download GET routes now record PRIVATE_EXPORT; other success auditing is selective. [auth hardening tests], [authorization tests], [DB role tests]. Operator retention uses reviewed elevated workflow. |
| 39 | SEC-HEADERS-001 — Security Headers | repository change | [frontend headers], [browser filter], [proxy] | CSP, nosniff, referrer/permissions/frame controls, conditional HTTPS HSTS and private no-store; [header tests], [browser security tests], [boundary tests]. Validate deployed TLS termination. |
| 40 | SEC-ACTUATOR-001 — Management Endpoint Protection | repository change | [route policy], [authorization], [common config] | Only health is exposed and permitted for loopback, detailed management is denied; [authorization tests] contains real management endpoint cases. Configure safe internal health probes. |
| 41 | SEC-CODE-001 — Wildcard Imports | repository change | [source contract], [security architecture] | Explicit imports enforced by source/architecture checks in CI. This is an equivalent mandatory gate; Checkstyle itself is not claimed installed. |
| 42 | SEC-CODE-002 — Field Injection | repository change | [source contract], [security architecture], backend services | Constructor injection replaces field injection; CI rejects reintroduction. Constructor annotations are allowed. |
| 43 | SEC-CODE-003 — Oversized Security Class | repository change | [security facade], [authorization], [session filter], [browser filter], [request IDs], [audit] | Authentication transport/route/browser/audit responsibilities are split; compatibility facade retains domain authorization helpers and request arguments. Further service-interface refactoring is optional remaining maintainability work. |
| 44 | SEC-CODE-004 — Annotation Standards | repository change | [source contract], [request architecture], controllers | Imported annotation types and `@Valid`/`@PreAuthorize` conventions are enforced; no fully qualified Spring annotation escape. |
| 45 | Preventing Future Vulnerabilities — Mandatory Development Rules | repository change | Checklist below; [PR template], [source contract], [security architecture], [CI] | New routes/DTOs/tables cannot bypass the enforced inventories; owner/race/payment/upload/audit review still requires meaningful negative tests. |
| 46 | Architecture Tests to Prevent Future Security Regression | repository change | [backend architecture], [security architecture], [request architecture], [source contract] | Controller/service boundaries, route inventory, annotations, explicit SQL, injection and typed bodies are checked. Backend architecture execution is pending exact-source CI. |
| 47 | Static Analysis | repository change | [CI], [SAST gate], [SAST dispositions], [SAST review spec], [SAST gate tests], [SAST disposition tests] | CodeQL security-extended Java/JS; 12 local gate/disposition regressions pass. Only 3 exact reviewed GET alerts may receive visible applicability assessments tied to hashes/fresh same-run JUnit; reviewed 2026-10-07, expiring 2026-11-05. Raw SARIF is retained. Three residual source findings have fixes awaiting a fresh scan; the three GET assessments already passed same-run JUnit verification at `3336d08d`. Semgrep/SpotBugs/PMD are not installed-tool claims. |
| 48 | Dependency Security | repository change | [CI], [Dependabot], [SBOM audit], [dependency applicability] | Java/frontend CycloneDX inventories, OSV/npm runtime advisories and documented exact applicability evidence; no blanket CVE ignore. Current artifacts/scans and override review are release gates. |
| 49 | GitHub Actions Security | repository change | [CI] | Default contents-read, pinned actions/checksummed scanner archives, no persisted checkout credential, disposable fixtures, limited job permission. No deployment job/production-secret activation is claimed. |
| 50 | Branch Protection | operator prerequisite | [CODEOWNERS], [CI], [release handoff] | Owner coverage is repository source. Host administrator must activate required PR/review/status checks and block force push/deletion; no installed rules are claimed. |
| 51 | Container Security | repository change | [API Dockerfile], [publisher Dockerfile], [hosted compose], [CI], [image gate] | Non-root runtime, read-only API filesystem, cap drop, no-new-privileges, bounded tmp/resources; API/publisher Trivy jobs retain evidence. Six gate tests pass; the publisher runtime removes unused package managers. Actual rebuilt scans and deployed restrictions remain required. |
| 52 | Backup Security | operator prerequisite | [DB runbook], [restore rehearsal], [operations specification] | Encrypted disposable CI restore mechanism exists. Install encrypted private backup/PITR and distinct backup role; record isolated real restore, recovery time/point and historical MFA-key availability before declaring recovery verified. |
| 53 | Data Retention | operator prerequisite | [maintenance jobs], [privacy service], [DB runbook], [commercial runbook] | Expired security/upload data and optional usage events have cleanup. Operator must approve numeric periods/legal holds for notifications, audits, financial/moderation/deletion evidence and backups; see retention boundary below. |
| 54 | Production Monitoring | operator prerequisite | [request IDs], [audit], [release handoff], [commercial runbook] | Signals and alert/incident procedures exist; install private dashboards/alerts for auth/MFA/401/403/429/5xx, signature/payment reconciliation, DB/storage and admin anomalies. Alert delivery/redaction must be tested. |
| 55 | Security Regression Test Suite | repository change | [auth hardening tests], [authorization tests], [typed request tests], [data exposure tests], archive/provider/browser suites | Negative actors, OAuth/MFA replay, invalid bodies, chunked oversize, stored-script isolation and financial/archive cases are present. New 8 OAuth atomic-binding cases and administrator GET audit regression are mandatory disposition evidence. Required full suite remains pending CI; 4 skipped frontend cases are not passes. |
| 56 | CI Security Pipeline | repository change | [CI], [source contract], [SAST gate], [image gate], [SBOM audit] | Compile/tests/browser/build, architecture, secrets/history, semantic analysis, advisory/SBOM and image jobs exist. Exact-source green workflow and deployment validation must block release; branch enforcement is external. |
| 57 | Production Fail-Closed Configuration | repository change | [production config], [DB role guard], [MFA envelopes], [bootstrap], [environment check], [deployment mode] | Unsafe HTTPS/TLS/schema/role/cookie/proxy/storage/mail/bootstrap/key/partial OAuth settings reject hosted startup; [production config tests], [DB role tests], [MFA tests]. Operator must supply valid configuration. |
| 58 | Recommended Security Implementation Sprints | repository change | Sections 4–57; [CI], [DB runbook], [MFA runbook] | Foundation, ownership, input/output, authentication, configuration and automation have source controls. Sprint 8 operational exercises remain prerequisites, not completed sprints. |
| 59 | P0 Production Release Gate | operator prerequisite | Sections 4–40 and 47–57; [release gates], [CI], [authorization matrix] | **Not yet 100% PASS:** complete matrix is delivered, but current backend/full CI and scans, live TLS/private storage/DB-role validation and real provider acceptance remain required. |
| 60 | P1 Commercial Release Gate | operator prerequisite | [MFA runbook], [CI], [DB runbook], [commercial runbook] | **Not yet 100% PASS:** current evidence, installed branch rules, restored backup, approved active retention and monitoring remain required. |
| 61 | P2 Scaling Gate | operator prerequisite | [rate limits], [MFA runbook], [operations specification] | PostgreSQL counters are already shared/atomic; load/resilience verification, centralized alerting, key/secret rotation, DR/incident exercises and external penetration testing require operators. Source review is not a penetration test. |
| 62 | Definition of Done for Every Security Ticket | repository change | Evidence boundary and checklist here; [CI] | Attach implementation, meaningful negative/integration tests, docs, exact-source CI/scans and safe deployment evidence. Source-fixed items are not marked verified while those gates remain pending. |
| 63 | Permanent Pull Request Security Checklist | repository change | Checklist below; [PR template], [CODEOWNERS], [CI] | Explicit review answers are required; source rules automate a subset. Repository-host required review/check configuration is still an operator prerequisite. |
| 64 | Final Security Architecture | repository change | [proxy], [chain], [session filter], [principal], [authorization], [request models], [DB permissions], [response models] | The application boundary matches the layered flow, with retained domain owner/member checks. RLS grants guard a trusted server role, not tenant isolation; supporting operational systems are not installed by source changes. |
| 65 | Final Security Completion Criteria | operator prerequisite | Entire table; [CI], [DB runbook], [MFA runbook] | Completion is withheld until both P0/P1 are 100%, exact-source suites/scans pass and live infrastructure/operational evidence exists. |
| 66 | Security Status Tracking Template | repository change | This 67-row table and evidence boundary | Every supplied section is tracked; status records control ownership, and the evidence column records limits rather than substituting a green badge for execution. |
| 67 | Final Rule | repository change | [chain], [route policy], [request architecture], [source contract], [production config], [DB role guard] | Default denial, typed validation, finite audit and fail-closed configuration are centralized/testable. CI enforcement and operator activation complete the boundary. |

## Independent Review Army resolutions

These are source resolutions from independent review, not claims that the pending backend workflow or operational checks passed.

| Finding | Resolution and meaningful regression |
|---|---|
| Authentication pool starvation | Identity throttles run before the domain transaction. Authentication queues at most four bounded audit events and [browser filter] persists them after transaction interceptors finish, avoiding a nested audit connection while user locks consume the pool. [auth hardening tests] adds ten concurrent real HTTP logins with the production-size fixture pool and checks failure audit survives rollback. |
| PostgreSQL `FOR SHARE` requires UPDATE privilege | [DB permissions] supplies only identity-column UPDATE grants on `user_roles`, `sessions` and `business_members`. UPDATE policy permits locking via `USING (true)` but rejects changed rows via `WITH CHECK (false)`; stale column grants are cleared. [DB role guard] checks exact grants/policies; [DB role tests] exercises shared-lock joins and mutation denial. |
| Strict request DTO/client contract mismatch | [auth form] sends `rememberMe` only for password login, so signup/reset match their records. [business workspace] constructs the exact brief update fields for open/close instead of posting response-only IDs/timestamps. [typed request tests] checks strict boundaries; connected frontend journeys remain required for these callers. |
| Trust-card projection lost public fields | [trust service] explicitly includes builder identity, profile slug, availability and booked-until in similar-builder rows. `similarBuilderCardsPreserveIdentityProfileLinksAndAvailability` in [data exposure tests] verifies the user-visible contract alongside secret exclusion. |
| Incomplete container scan could pass | [image gate] validates schema/image/type, nonempty results, operating-system coverage and required vulnerability fields; UNKNOWN/high/critical findings block unless the exact documented assessment applies. [image gate tests] has six passing local regressions, including empty/wrong/incomplete reports and unrelated findings. Full scanner output remains visible; actual scans are pending. |
| Incomplete SARIF, ambiguous rule lookup, malformed severity or accepted scanner suppression could bypass the semantic gate | [SAST gate] requires complete successful analysis evidence, resolves descriptors within their driver/extension component, rejects conflicting identities/invalid security severity and blocks high findings despite scanner suppression. [SAST gate tests] has five passing local regressions. The three residual source findings have fixes awaiting a fresh scan; three reviewed GET findings remain visible with bound evidence. |
| GET alerts need reproducible applicability evidence rather than blanket suppression | [SAST dispositions] matches only the 3 reviewed administrator/Google/GitHub GET alerts by path, line, rule-descriptor hash and score. Full production/test inventories, required passing JUnit hashes, current source SHA/workflow run/attempt and review expiry bind each visible assessment. Seven [SAST disposition tests] pass locally; actual required JUnit and fresh raw/assessed SARIF remain CI prerequisites. |
| OAuth mismatched/cancelled callbacks could erase a valid browser binding | [Google auth] and [GitHub auth] clear the cookie only after provider-specific unexpired state and browser hashes are atomically consumed. Eight new cases in [auth hardening tests] preserve mismatched state/binding and prove expired/cross-provider/racing/replayed/failed-exchange requests cannot obtain sessions. These PostgreSQL tests passed at `3336d08d`; later inputs require fresh CI. |
| File checks and test-host matching needed stronger boundaries | [Local setup], [frontend artifact check] and [dependency dispositions] open with `O_NOFOLLOW`, validate the open descriptor and read/update that same descriptor; local configuration permissions/writes are descriptor-based. [Local tool tests] includes direct and deterministic create-time symlink rejection without target mutation. New files use exclusive no-follow descriptors and mode `0600`. Six browser/connected test files now parse provider hostnames with an exact hostname or dot-delimited subdomain boundary; current full CI remains pending. |
| Private package/download success reads lacked a shared security audit event | [Browser filter] now applies PRIVATE_EXPORT to all 6 classified GET export/package/download routes, with FAILURE for completed error responses and authorization-denial precedence for 401/403. The [authorization matrix] records this alongside retained domain read records; The corresponding commerce and authorization regression classes passed at `3336d08d`; later revisions require fresh CI. |

## Operator activation and retention boundary

Use [release handoff] and [DB runbook] for **hosted** deployment. [Ops guide] and [staging guide] now distinguish local migrations from the hosted role-separated procedure. Create distinct externally managed migration, runtime, backup and read-only credentials, run migrations separately, provision/recheck grants after every migration, and verify the hosted process connects only as `getlancer_runtime`. Never distribute those credentials to browser, anonymous database APIs or demo publisher. The 93-table inventory is bounded and fail-closed for unknown future tables.

Inject the versioned AES-256-GCM MFA keyring and active key ID outside PostgreSQL and source. Apply V26–V28 with the documented upgrade order, verify zero plaintext seeds and require fresh administrator password plus TOTP. Retain historical keys for encrypted backups; execute and record rotation/recovery using [MFA runbook]. Remove bootstrap password/seed after administrator verification. These actions cannot be inferred from migrations or fixture tests.

Activate exact-origin private storage, verified TLS and mail, isolated demo infrastructure, approved merchant configuration, and hosted container restrictions. Install branch protection using actual CI status names, approved reviewers and CODEOWNERS; block force pushes/deletion. Do not enable public/commercial traffic until the exact-source full workflow and the applicable P0/P1 checklist have evidence.

| Data family | Repository retention behavior | Required operator decision/evidence |
|---|---|---|
| Sessions, MFA challenges, OAuth state, access grants | [maintenance jobs] deletes expired rows; expiry is established by the issuing service. V28 invalidates historical MFA sessions. | Validate scheduler execution, configured lifetimes and revocation on the deployed service. |
| Reset/verification tokens and rate buckets | Expired account tokens are removed after 7 days; expired rate buckets after 1 day. Terminal failed unsent mail bodies are scrubbed after 7 days/8 attempts. | Approve retention and verify cleanup/queue health without printing tokens. |
| Temporary uploads | Expired records enter deletion queue after a 5-minute margin; storage deletion/lifecycle is separate. | Install lifecycle for abandoned pending objects; verify queued object deletion and signed-link expiry. |
| Optional usage analytics | Default 90-day retention, bounded configuration; cleanup excludes engagement evidence. | Approve analytics collection, salt custody and configured period; verify actual deletion. |
| Notifications, security audit, financial/moderation/engagement evidence, deleted-account metadata | No blanket erasure or universal numeric retention is claimed. Audit is finite-field, append-only for runtime; financial/business evidence has preservation constraints. | Approve an explicit period/hold register with legal and policy owners; perform reviewed retention with appropriate operator role, never expand API runtime history mutation privileges. |
| Backups and MFA historical keys | Encrypted CI restore rehearsal and backup role procedure exist. | Set encrypted private retention/access, restore into isolation, record recovery point/time and key availability; expire backup-required keys only after their backups expire. |

Monitoring must count authentication/MFA failures, 401/403/429/5xx, signature failures, reconciliation attention, DB/storage failures and unexpected administrator changes. Use request IDs and bounded action metadata; alerts must omit confidential user data and secrets. Exercise the alert destination before activation. During an incident, restrict affected access, preserve private audit/financial evidence, revoke compromised sessions/challenges, rotate affected credentials using versioned recovery procedures, reconcile provider facts before restoring payments, and record the decision and recovery validation. Production monitoring, alert exercises, restore drills and external penetration testing remain operator prerequisites.

## Security-relevant PR checklist

Before merge, record answers for route classification/authentication/role/recent MFA; current ownership/membership/party predicates; concrete DTOs and bounded values/URLs/uploads; parameterized SQL and race/transaction behavior; explicit response fields and minimal PII; hostile browser/SSRF/XSS/redirect inputs; shared abuse limits and finite audit events; payment signatures/provider facts/idempotency/mode; migrations/grants/retention; meaningful negative tests; exact-source CI/scans; and production activation/rollback requirements. A source convention alone is insufficient: add the enforced route/table inventory and regression when the surface changes. Unanswered applicable items block security review.

## Remaining concrete gaps

The detailed [authorization matrix] requested by section 6 is delivered and matches all 259 classified method/route entries. Request-based compatibility helpers and some fixed-map responses remain documented limitations of the preferred interface/model standard; their presence is not evidence of an authorization bypass or raw-row leak. Full latest-head CI and scan evidence, residual scanner fixes, numeric retained-evidence policies and all operator activation records remain outstanding. Neither P0 nor P1 is declared complete.

[POM]: ../backend/pom.xml
[CI]: ../.github/workflows/ci.yml
[chain]: ../backend/src/main/java/com/getlancer/config/SecurityConfiguration.java
[authorization]: ../backend/src/main/java/com/getlancer/security/AuthorizationService.java
[route policy]: ../backend/src/main/resources/api-authorization-policy.txt
[route contract]: ../backend/src/test/resources/api-route-contract.txt
[authorization matrix]: AUTHORIZATION_MATRIX.md
[session filter]: ../backend/src/main/java/com/getlancer/security/SessionAuthenticationFilter.java
[principal]: ../backend/src/main/java/com/getlancer/security/GetLancerPrincipal.java
[security facade]: ../backend/src/main/java/com/getlancer/security/Security.java
[browser filter]: ../backend/src/main/java/com/getlancer/security/BrowserSecurityFilter.java
[request IDs]: ../backend/src/main/java/com/getlancer/security/RequestIds.java
[audit]: ../backend/src/main/java/com/getlancer/security/SecurityAudit.java
[rate limits]: ../backend/src/main/java/com/getlancer/security/RateLimits.java
[session cookies]: ../backend/src/main/java/com/getlancer/security/SessionCookies.java
[auth service]: ../backend/src/main/java/com/getlancer/auth/AuthService.java
[auth requests]: ../backend/src/main/java/com/getlancer/auth/AuthRequests.java
[MFA envelopes]: ../backend/src/main/java/com/getlancer/auth/MfaSecrets.java
[MFA upgrade]: ../backend/src/main/java/com/getlancer/config/MfaStorageUpgrade.java
[MFA runbook]: MFA_KEY_ROTATION.md
[V26]: ../backend/src/main/resources/db/migration/V26__encrypted_admin_mfa.sql
[V27]: ../backend/src/main/resources/db/migration/V27__security_audit_events.sql
[V28]: ../backend/src/main/resources/db/migration/V28__session_authentication_time.sql
[bootstrap]: ../backend/src/main/java/com/getlancer/config/Bootstrap.java
[production config]: ../backend/src/main/java/com/getlancer/config/ProductionConfiguration.java
[common config]: ../backend/src/main/resources/application.properties
[local config]: ../backend/src/main/resources/application-local.properties
[hosted config]: ../backend/src/main/resources/application-production.properties
[DB role guard]: ../backend/src/main/java/com/getlancer/config/DatabaseRoleGuard.java
[role guard]: ../backend/src/main/java/com/getlancer/config/DatabaseRoleGuard.java
[DB roles]: ../ops/database/00_roles.sql
[DB permissions]: ../ops/database/10_permissions.sql
[DB runbook]: DATABASE_RUNTIME_ROLES.md
[request models]: ../backend/src/main/java/com/getlancer/dto/
[response models]: ../backend/src/main/java/com/getlancer/responses/
[errors]: ../backend/src/main/java/com/getlancer/shared/Errors.java
[support]: ../backend/src/main/java/com/getlancer/shared/Support.java
[account service]: ../backend/src/main/java/com/getlancer/accounts/AccountService.java
[privacy service]: ../backend/src/main/java/com/getlancer/accounts/PrivacyService.java
[team policy]: ../backend/src/main/java/com/getlancer/teams/TeamPolicy.java
[inquiry policy]: ../backend/src/main/java/com/getlancer/inquiries/InquiryPolicy.java
[business repository]: ../backend/src/main/java/com/getlancer/business/BusinessRepository.java
[delivery repository]: ../backend/src/main/java/com/getlancer/delivery/DeliveryRepository.java
[commerce repository]: ../backend/src/main/java/com/getlancer/commerce/CommerceRepository.java
[product repository]: ../backend/src/main/java/com/getlancer/products/ProductRepository.java
[trust service]: ../backend/src/main/java/com/getlancer/trust/TrustService.java
[URL rules]: ../backend/src/main/java/com/getlancer/shared/Rules.java
[demo health]: ../backend/src/main/java/com/getlancer/trust/DemoHealth.java
[publisher client]: ../backend/src/main/java/com/getlancer/hosting/DemoPublisherClient.java
[media service]: ../backend/src/main/java/com/getlancer/media/MediaService.java
[commerce storage]: ../backend/src/main/java/com/getlancer/commerce/CommerceStorage.java
[source archive]: ../backend/src/main/java/com/getlancer/commerce/SourceArchive.java
[static archive]: ../backend/src/main/java/com/getlancer/hosting/StaticArchive.java
[publisher server]: ../ops/demo-publisher/server.mjs
[payment service]: ../backend/src/main/java/com/getlancer/payments/PaymentService.java
[payment repository]: ../backend/src/main/java/com/getlancer/payments/PaymentRepository.java
[commerce payment service]: ../backend/src/main/java/com/getlancer/commerce/CommercePaymentService.java
[maintenance billing]: ../backend/src/main/java/com/getlancer/maintenance/MaintenanceBillingService.java
[Google auth]: ../backend/src/main/java/com/getlancer/auth/GoogleAuthService.java
[Google tokens]: ../backend/src/main/java/com/getlancer/auth/GoogleTokens.java
[GitHub auth]: ../backend/src/main/java/com/getlancer/auth/GitHubAuthService.java
[auth providers]: ../backend/src/main/java/com/getlancer/auth/AuthProviderService.java
[hosting config]: ../backend/src/main/java/com/getlancer/hosting/HostingConfiguration.java
[hosting runbook]: V4_HOSTING_OPERATIONS.md
[commercial runbook]: V4_OPERATIONS.md
[maintenance jobs]: ../backend/src/main/java/com/getlancer/jobs/Maintenance.java
[proxy]: ../app/api/v1/[...path]/route.ts
[auth form]: ../app/components/auth-form.tsx
[business workspace]: ../app/components/business-workspace.tsx
[proof editor]: ../app/components/proof-editor.tsx
[frontend headers]: ../lib/security-headers.ts
[safe URLs]: ../lib/safe-url.ts
[worker]: ../worker/index.ts
[deployment mode]: ../lib/deployment-mode.ts
[server client]: ../lib/server.ts
[env example]: ../.env.example
[environment check]: ../scripts/check-environment.mjs
[source contract]: ../scripts/check-security-contract.mjs
[frontend artifact check]: ../scripts/check-frontend-security.mjs
[SAST gate]: ../scripts/check-sast-results.mjs
[SAST gate tests]: ../tests/sast-security-gate.test.mjs
[SAST dispositions]: ../scripts/sast-dispositions.mjs
[SAST review spec]: SAST_APPLICABILITY.json
[SAST disposition tests]: ../tests/sast-dispositions.test.mjs
[dependency dispositions]: ../scripts/sbom-dispositions.mjs
[Local setup]: ../scripts/setup-local.mjs
[Local tool tests]: ../tests/local-tools.test.mjs
[image gate]: ../scripts/check-container-results.mjs
[SBOM audit]: ../scripts/audit-sbom.mjs
[restore rehearsal]: ../scripts/verify-restore-ci.mjs
[Gitleaks config]: ../.gitleaks.toml
[CODEOWNERS]: ../.github/CODEOWNERS
[Dependabot]: ../.github/dependabot.yml
[dependency applicability]: DEPENDENCY_APPLICABILITY.md
[API Dockerfile]: ../backend/Dockerfile
[publisher Dockerfile]: ../ops/demo-publisher/Dockerfile
[hosted compose]: ../compose.staging.yaml
[ops guide]: ../ops/README.md
[staging guide]: ../ops/STAGING.md
[release handoff]: ../ops/SECURITY_RELEASE.md
[PR template]: ../.github/pull_request_template.md
[operations specification]: 13_OPS_DEPLOYMENT.txt
[release gates]: V1_FIXES_AND_RELEASE_GATES.md
[backend architecture]: ../backend/src/test/java/com/getlancer/architecture/BackendArchitectureTest.java
[security architecture]: ../backend/src/test/java/com/getlancer/architecture/SecurityArchitectureTest.java
[request architecture]: ../backend/src/test/java/com/getlancer/architecture/RequestValidationTest.java
[authorization tests]: ../backend/src/test/java/com/getlancer/security/AuthorizationIntegrationTest.java
[boundary tests]: ../backend/src/test/java/com/getlancer/security/SecurityBoundaryTest.java
[auth hardening tests]: ../backend/src/test/java/com/getlancer/auth/AuthenticationHardeningIntegrationTest.java
[MFA tests]: ../backend/src/test/java/com/getlancer/auth/MfaSecretsTest.java
[production config tests]: ../backend/src/test/java/com/getlancer/config/ProductionConfigurationTest.java
[DB role tests]: ../backend/src/test/java/com/getlancer/config/DatabaseRoleGuardIntegrationTest.java
[data exposure tests]: ../backend/src/test/java/com/getlancer/integration/DataExposureIntegrationTest.java
[typed request tests]: ../backend/src/test/java/com/getlancer/integration/TypedRequestsIntegrationTest.java
[business tests]: ../backend/src/test/java/com/getlancer/integration/BusinessIntegrationTest.java
[team tests]: ../backend/src/test/java/com/getlancer/integration/TeamsIntegrationTest.java
[delivery tests]: ../backend/src/test/java/com/getlancer/integration/DeliveryIntegrationTest.java
[commerce tests]: ../backend/src/test/java/com/getlancer/integration/CommerceIntegrationTest.java
[marketplace tests]: ../backend/src/test/java/com/getlancer/integration/MarketplaceIntegrationTest.java
[maintenance tests]: ../backend/src/test/java/com/getlancer/integration/MaintenanceIntegrationTest.java
[payment tests]: ../backend/src/test/java/com/getlancer/integration/PaymentIntegrationTest.java
[provider tests]: ../backend/src/test/java/com/getlancer/payments/CommerceProviderIntegrationTest.java
[storage tests]: ../backend/src/test/java/com/getlancer/commerce/CommerceStorageTest.java
[source archive tests]: ../backend/src/test/java/com/getlancer/commerce/SourceArchiveTest.java
[static archive tests]: ../backend/src/test/java/com/getlancer/hosting/StaticArchiveTest.java
[trust tests]: ../backend/src/test/java/com/getlancer/trust/TrustTest.java
[publisher tests]: ../backend/src/test/java/com/getlancer/hosting/DemoPublisherClientTest.java
[hosting tests]: ../backend/src/test/java/com/getlancer/integration/HostedDemoIntegrationTest.java
[Google tests]: ../backend/src/test/java/com/getlancer/auth/GoogleTokensTest.java
[Google account tests]: ../backend/src/test/java/com/getlancer/auth/GoogleAccountsTest.java
[GitHub tests]: ../backend/src/test/java/com/getlancer/auth/GitHubAuthTest.java
[bootstrap tests]: ../backend/src/test/java/com/getlancer/profiles/ProfileAndBootstrapTest.java
[discovery tests]: ../backend/src/test/java/com/getlancer/security/DiscoveryRateTest.java
[browser security tests]: ../tests/browser/frontend-security.spec.ts
[header tests]: ../tests/security-headers.test.mjs
[safe URL tests]: ../tests/safe-url.test.mjs
[rendered HTML tests]: ../tests/rendered-html.test.mjs
[deployment tests]: ../tests/deployment-mode.test.mjs
[publisher node tests]: ../tests/demo-publisher.test.mjs
[image gate tests]: ../tests/container-security-gate.test.mjs
