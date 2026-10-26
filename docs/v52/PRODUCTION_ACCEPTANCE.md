# Production qualification and acceptance

The qualification software is executable. **Actual production qualification remains UNMET.** No production target, dedicated KVM host, reviewed images, production dataset, recovery target, live merchant access or human sign-off is configured here. Disposable test keys and invented observations validate the software only. A CI restore cannot satisfy production recovery.

The [25 October execution record](ACCEPTANCE_EXECUTION.md) lists the read-only capability observations, checks actually run and the precise owner handoff for every remaining production outcome. Its local software-verifier results do not close production gates.

Use the existing application/PostgreSQL deployment and [dedicated KVM provider/offline builder](../../ops/lab-provider/README.md). No new hosting account, Kubernetes platform or payment service is introduced. Runtime and commerce activation remain separate. This harness never deploys, creates a VM, changes activation flags, charges/refunds a customer, changes DNS or restores a database.

## External target and trust

The platform owner completes [target.example.json](../../ops/production/target.example.json), whose blank values intentionally fail. Store real policy outside Git at `/etc/getlancer-qualification/target.json`; install the independently trusted Ed25519 public key at `/etc/getlancer-qualification/operator-public.pem`. Keep signing keys on a protected signing system and never mount them in Actions. Do not put database/merchant passwords, cookies, private keys, customer records or payment payloads in Git, command arguments or workflow logs.

Pin the exact deployed Git revision, provider/current operator restore epoch, SHA256 of raw `/etc/machine-id` bytes, separate public HTTPS app/gateway origins, reviewed Firecracker/jailer/kernel paths and hashes, and exact six runtime image/source pins. Native reviewed families are JDK 17, Node 22.23.4+ in the Node 22 LTS line, Python 3.12, Go 1.24, .NET 8 and PHP 8.3. Existing native manifests additionally bind component revision, archive/build hash, exact runtime/version/image and dated maintainer/licence/reproduction/resource/isolation certification. Policy changes need review and a new bundle signature over the raw policy digest.

Pin both real anonymous source ZIP services in policy `sources`: `{id:"component-source",path:"/api/v1/components/ACTUAL-SLUG/source?sha256=ACTUAL-HASH",sha256:ACTUAL-HASH,sizeBytes:ACTUAL-SIZE}` and `{id:"education-source",path:"/api/v1/education-releases/ACTUAL-UUID/source",sha256:ACTUAL-HASH,sizeBytes:ACTUAL-SIZE}`. These descriptions are not valid production values. The monitor rejects redirects, credentials, foreign routes, non-ZIP and mismatched archive bytes. Recorded source pins must exactly equal the external policy, including order. Catalogue availability cannot substitute for source availability.

Assign platform, security/runtime, database/recovery, merchant/finance and accessibility/product owners. Agree the bounded load budget, recovery target, rollback revision, retained trace store, alert destination and mutation window first. Existing budget rules and the creator/three-free-extras model are preserved.

## Bounded read-only preflight

On the actual provider host, from a clean checkout of the exact selected revision, under its ordinary observer account:

```sh
node scripts/qualify-production.mjs preflight \
  --revision ACTUAL_DEPLOYED_MAIN_SHA \
  --target /etc/getlancer-qualification/target.json \
  --out /var/lib/getlancer-qualification/preflight.json
```

This opens `/dev/kvm` without starting a VM, checks cgroup v2/systemd 250+, host identity and root-owned/read-only binary/kernel pins, and observes both HTTPS hosts. Descriptor-relative traversal rejects linked files and writable parent directories. Metadata and hashes come from the same opened descriptor. TLS uses normal trust/hostname verification, explicit SNI, TLS 1.2+, certificate hash and expiry. HTTP port 80 must redirect to the exact configured HTTPS origin or refuse connections. DNS/socket deadlines are absolute. Failed DNS, timeout or untrusted TLS remains unmet; raw machine identity, response headers and credentials are not printed.

The output is exclusively created with mode 0600. Retain failed attempts. `HOST_PREFLIGHT_OBSERVED` proves prerequisites only; device access does not certify KVM boot, guest isolation, jailer behavior or production readiness. No privileged/destructive operation is bundled with discovery.

## Actual exercises required

Keep real dated raw traces in protected immutable storage and attach their digests to the operator-reviewed bundle. Every lane must refer to the same deployed source/provider/epoch. A hash alone cannot establish what an operator's trace proves.

| Lane | Actual work and evidence |
| --- | --- |
| KVM/runtime | Follow the existing provider runbook; boot all reviewed images on the real host. Observe separate-account isolation, denied network paths, cgroup memory/CPU/process limits, hard lease, process/crash cleanup, denied orphan admission and quarantined capacity. Retain host/distribution, source/image, kernel/Firecracker/jailer and epoch/timestamps. Never relax controls to obtain a pass. |
| Hosted starts | >=100 fresh unique hosted runs for **each of six adapters**, >=600 total, both warm/cold classes. Retain queued/admitted/ready/cleaned timestamps. Replay is rejected. Warm p95 <=5s, cold p95 <=30s, cleanup <=5min and admitted concurrency <=10. Native maintainer/licence/reproduction artifacts must match the exact signed inventory/images. |
| Production load/plans | Actual >=10,000 eligible rows separately for components and college projects. Declare real server profile/client location; run each catalogue at 20rps, concurrency <=10, >=60s, all scheduled requests observed, server p95 <=300ms. Retain actual `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)` and eligible counts for the public filter/sort queries under read-only access and bounded statement timeout. Seeds, client latency alone and old products traffic do not substitute. |
| Seven-day soak | >=604800000 actual elapsed milliseconds with agreeing UTC/monotonic time, outages preserved, discovery plus both source services and authenticated authoritative runtime counters. Stable operator/counter epoch, observed endpoints, nonzero settled admitted cohort, >=99% infrastructure success, distinct scenario/quota/validation denominators. Each sampled service needs >=99.5% availability; this does not certify monthly SLA. Rotate the protected MFA operator cookie file; expiry remains an observed failure. Restart after epoch changes. CI-shortened/time-shifted/TEST journals do not substitute. |
| Encrypted production recovery | Actual authenticated encrypted production backup restored to the selected separate recovery target. Verify exact source/version/hash/object bytes, outbox and purchase/entitlement/financial counts/bindings/rights. Actual backup->incident RPO <=15min; incident->recovered RTO <=4h. Fresh external epoch; prove old capabilities cannot read protected bytes. Preserve archive hash/recovery transcript without exposing production data. |
| Rollback | In the agreed drill, pause new admissions, deploy reviewed prior immutable revision, verify migration compatibility/user health and financial/entitlement/source retention, then use reviewed forward recovery. Record the different rollback revision and observed state. No production restore/rollback during discovery. |
| LIVE merchant | Merchant/finance owner approves and performs actual intended-account Razorpay LIVE settled payment, signed/idempotent webhook/duplicate callbacks, ledger/entitlement retention, approved refund and reconciliation. Retain sanitized provider references and audit traces. TEST/mocks do not substitute; no charge/refund is issued by this harness. |
| Human acceptance | Named reviewers exercise keyboard, screen reader, actual 200% zoom and widths 320/390/768/1024/1440; >=5 real task sessions, task comprehension/first useful interaction, blocking remediation, alert delivery and owner acknowledgements. Automated browser/a11y testing supplements actual sessions. |

Existing [launch gates](LAUNCH_GATES.md) also govern content/legal, security CI, operational ownership and limited-cohort sign-off. Runtime and commerce approvals remain independent.

## Collect with existing tools

Use `scripts/v52-load.mjs --catalogue components` and a separate `--catalogue college-projects` campaign, with explicit `--origin`, `--environment production`, `--revision`, unused `--out`, real `--profile`, and 20/10/60000 profile. Responses provide eligible row counts and request-local server timing.

Use `scripts/v52-monitor.mjs --catalogue components` with the same origin/environment/revision/profile, `--sources /protected/actual-source-targets.json`, `--admin configured`, `--duration-ms 604860000`, `--cadence-ms 60000` and protected `V52_ADMIN_COOKIE_FILE` path. This is seven days plus one sampling interval so request latency cannot shorten the measured endpoint span below seven full days. Run on a persistent external observer; only contiguous bounded segments with real coverage qualify. Collector output is unsigned measurements requiring independent review/signature. Review separate discovery campaigns with the monitoring owner. A stale cookie, missing lane or changed epoch cannot be hidden by a healthy catalogue.

Use existing `scripts/v52-evidence.mjs` for LOAD, SOAK, DRILL, ACTIVATION and HANDOVER signing/verification. Keep complete hash-chained journals and each referenced control observation alongside the reports. Deployment/manual controls require actual `method:"OPERATOR"` observations, not CI/mocks. The actual observer takes seven days; this repository adds no fabricated acceptance records.

## Bundle contract and verification

The executable exact schema is `ops/production/qualification.mjs`. Top payload has version 1, kind `PRODUCTION_QUALIFICATION`, purpose `MEASUREMENT`, production environment/revision/provider/operatorEpoch/hostIdSha256, current canonical UTC `observedAt`, and `targetSha256` of the **raw policy bytes**. Use the existing modern Ed25519 `{algorithm,payload,signature}` envelope with the separately trusted operator key.

`files` holds nine distinct sibling `{file,sha256}` references: admission, inventory, preflight, startup, loadComponents, loadProjects, soak, handover, operations. `manifests` has six `{language,file,sha256}` signed native manifest references. Admission/native envelopes keep their existing `{payload,signature}` format; inventory needs all seven dated real control artifacts. Certs must match runtime/version/source/image, startup maintainer and actual reproduction hash.

`traces` references isolation, query-components, query-college-projects, recovery, merchant, human, plus licence-LANGUAGE/reproduction-LANGUAGE for all six adapters. Shared aggregate reviewed licence/build traces may be reused when actually applicable. Every reference is bounded, regular and sibling-local; altered bytes, symlinks, traversal, missing lanes, wrong epoch/revision or signature reject.

Startup proof uses common provenance, kind `HOSTED_STARTUP`, six policy adapter rows, maintainer/licenceEvidenceSha256/reproductionEvidenceSha256 and `samplesFile:{file,sha256}` per row. Each bounded newline-terminated NDJSON file has 100–1000 records with exact runId/kind/queuedAtMs/admittedAtMs/readyAtMs/cleanedAtMs fields. Use actual timestamps within31days, globally unique run IDs and both WARM/COLD classes. Operations proof uses kind `PRODUCTION_OPERATIONS` and exact infrastructure/queryPlans/recovery/merchant/human fields in `reviewOperations`; attach independently reviewed real traces.

All report hashes/signatures/parsing share captured bytes through existing expected digest APIs. HANDOVER must select the exact final component LOAD and SOAK; project LOAD is independently required. Origins/source pins/runtime epochs must match target policy. Final observations/endings must be <=24h old; existing admission/native review dates/expiries remain current under <=31-day rules. The actual soak retains its earlier real start.

```sh
node scripts/qualify-production.mjs verify \
  --revision ACTUAL_DEPLOYED_MAIN_SHA \
  --target /etc/getlancer-qualification/target.json \
  --public-key /etc/getlancer-qualification/operator-public.pem \
  --bundle /var/lib/getlancer-qualification/bundle-signed.json
```

Run from a clean exact-revision checkout on an unprivileged observer. Missing evidence exits nonzero with UNMET status. `PRODUCTION_QUALIFICATION_VERIFIED` means supplied signed evidence passes the checks; it does not deploy/activate anything. A dishonest trusted signer can still fabricate assertions; independent review of immutable actual traces and human work is essential. Never sign fabricated host evidence or install disposable test keys in production.

## Manual workflow and remaining inputs

[production-acceptance.yml](../../.github/workflows/production-acceptance.yml) is manually dispatched only. A hosted guard fails non-main/stale requested revisions; the production-acceptance environment then runs the read-only verifier on an isolated getlancer-evidence-observer runner with immutable dispatch SHA and no persisted checkout credentials.

Platform owners must provision this **unprivileged observer separate from the provider host**, mount only public policy/key and reviewed immutable evidence read-only, and configure main-only environment branch/reviewer controls where supported by the repository plan. Give it no root/deploy/database/merchant/session/signing credentials, host devices, privileged containers or Docker socket. Environment review does not isolate a self-hosted runner. No runner/environment/domain/account/paid resource is provisioned by this change.

Still needed: actual app/gateway origins and deployed SHA, selected host/distribution and observer account, reviewed binaries/kernel/images/sources, epoch/trusted key, named owners/drill windows, real eligible datasets/read-only plans, MFA observer cookie mechanism, encrypted production backup/separate recovery target/rollback revision, approved live merchant lifecycle, and actual human participants/signatures. All corresponding production gates remain **UNMET** until these are supplied and actual acceptance runs finish.

Official references: [Node22 TLS](https://nodejs.org/download/release/latest-v22.x/docs/api/tls.html), [Node22 DNS](https://nodejs.org/download/release/latest-v22.x/docs/api/dns.html), [Actions runner security](https://docs.github.com/en/actions/reference/security/secure-use), [environments](https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments). Selected host systemd/Firecracker behavior still needs the existing real provider qualification.
