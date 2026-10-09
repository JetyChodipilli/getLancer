# V4.7 implementation checkpoint

Status: started from tested main c5bc9eb on `codex/v4.7-runtime-20261009`. Development implementation is under verification. V4.7 is not complete or approved for public runtime. No deployment or public admission has occurred.

## Implemented

- Separate Java/PostgreSQL lab reservations, quotas, pinned signed manifests, durable commands, lease fencing, expiry/idle cleanup and owner-authorized finite SSE.
- Fixed authenticated provider HTTP protocol with exact run/image/epoch/command binding, full-body deadlines and output limits. No shell, arbitrary browser URL, app credentials or contributor build runs inside the app.
- `/labs` workbench with deliberate start, unchanged retry identity, cancel, input preservation, free source/setup and accurate disabled/empty/error states. `/workspace/labs` shows real operator reservations and MFA-protected pause decisions.
- V31 migration plus explicit database provisioning grants; runtime cannot install/modify certified manifests or edit immutable events/audit. Account export retains useful owner-scoped inputs/results with allowlisted projections. Approved account anonymization and completed-run retention redact private inputs/results.
- Offline Ed25519 admission-artifact verifier and seven explicitly open public-runtime prerequisites. Source eligibility uses the latest reviewed BACKEND release; V4.6 FRONTEND uploads cannot qualify. No executable product manifests are seeded.

## Checks and limits

Production build and TypeScript compile passed. Full Node suite passed 177/177; certification tests use synthetic signed reports solely to exercise verifier rejection behavior. After the publication review, Java protocol, request DTO, account export and architecture tests passed 22/22, including the complete request-validation architecture check. These unit checks do not establish database/provider isolation.

The real PostgreSQL integration suite and production-role grants have not passed here: no disposable native PostgreSQL/Docker service is available. Browser journeys are written for desktop 1440px, phone 375px and tablet 768px, but local execution is blocked by missing Chromium and truncated official Playwright downloads. Test discovery/type checking is not a browser pass. Public-provider isolation, licence, pricing/budget, image/build, network, cleanup, restore and soak evidence remains open in ADMISSION_GATES.md.

PR #41 publishes this development slice. Its first CI run found a stale lock-only permission inventory, lab fixtures retaining rate-limit buckets across tests, and dynamic map fields violating the concrete request DTO contract. The inventory now explicitly covers the manifest lock and immutable lab history; test setup resets its rate buckets; concrete validated input fields preserve the existing JSON object format and canonical retry identity. No security regression, permission boundary, SAST finding scope or expiry was relaxed. The reviewed provider request intent also drops an unused cached-response field; response recovery still rechecks authority in the delivery transaction.

Before merging, require the lab integration and role tests on disposable real PostgreSQL, all browser journeys against the current production build, and repository CI/SAST/container checks on the exact updated branch tree. Keep all unmet public-runtime gates open; merging the disabled development slice cannot certify or activate the provider.

## Operator protocol and rollout

1. Deploy schema V31 with the migration role, then rerun `ops/database/10_permissions.sql` with the reviewed administrator procedure. Never serve production with the database owner.
2. Keep `APP_LABS_ENABLED=false`. Inspect the chosen KVM/equivalent provider and secret-free build service against the phase plan; approve actual Redis licence obligations and a dated all-cost worksheet. The app contains only a provider adapter, not a hosted worker/build implementation.
3. Build reviewed source in the separate approved build service, pin archive/file manifest/image digests and protocol `getlancer-lab-v1`, and review bounded input/operation/resource/network rules. Insert an immutable operator-signed manifest using the migration/operator role after that review. The app runtime role cannot certify source.
4. Record the seven real admission reports and verify their hashed inventory offline. Sign exact JSON bytes with the operator Ed25519 private key kept outside this app. Pass X509 DER base64 public key, signed evidence-file path, fixed gateway origin/auth and independently stored epoch through the protected deployment configuration. No private signing key enters the app.
5. The fixed gateway accepts only `/v1/lab-commands`: START, REQUEST and STOP bind runId, leaseGeneration, operatorEpoch, commandId, manifestSha256, imageDigest and expiresAt exactly. Retries use the same command identity. RUNNING and RESULT must report current isolation. CLEANED must explicitly report `everHealthy` so lost healthy startup acknowledgements cannot refund quota or spent-cost reservations. Missing or uncertain cleanup retains reservations; STOP carries no user input bytes. A bound REQUEST FAILED currently returns an unconfirmed-operation error, saves no output, and keeps its reservation until explicit cancellation or watchdog cleanup; it does not certify scenario success. The provider must independently enforce the hard lease expiry even while the bounded app watchdog is awaiting other command responses.
6. After restoring metadata, rotate the external epoch before any routing/admission resumes. Old leases stay quarantined; the provider may accept fenced STOP of an old epoch solely for cleanup, never START/REQUEST. Confirm orphan removal and sign fresh evidence before recent-MFA unpause. A settings flag or signature alone is no proof of provider isolation.

The global admission lock deliberately serializes this initial ten-run control plane. Provider calls have a bounded deadline; commands retained during uncertainty are retried with stable identities. Initial caps are five account runs/UTCday, one active/account, ten global, five-minute lease, 90-second idle, 5120 MiB global and conservative 50,000,000 daily/1,000,000,000 monthly budget-micro-unit ceilings. These internal ceilings are not a price quote or spending approval. Translate actual provider expenses to one reviewed unit and choose stricter caps before public admission.

Private run/request input and result bytes become eligible for redaction 24 hours after confirmed cleanup, processed by bounded watchdog batches; approved anonymization also redacts after eventual cleanup. Failed cleanup retains inputs only where needed for reconciliation and must be an operator incident. Minimal immutable run identities, costs and typed activity/audit have no user-entered operation payload. Define the approved operational/audit retention before production activation.

## Work remaining

S03-B01 provider/licence/budget decision and measurements, S03-B02 real restricted build/image certification, S03-B04 real orphan/status/cleanup adapter behavior, S03-B07 provider metrics/log redaction/retention/backup drills, and S03-B08 real-provider threat/resource/restore/soak checks remain open. The current code is the initial development control plane and workbench, not a replacement for those infrastructure deliverables. Nine educational teaching labs and diagrams belong to V4.8.
