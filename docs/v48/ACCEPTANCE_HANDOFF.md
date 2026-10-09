# V4.8 hosted acceptance handoff

The application remains disabled until the actual operator passes `../v47/ADMISSION_GATES.md`. Local tests, browser fixtures and signed synthetic reports are not admission evidence.

## Required provider input

Supply the selected KVM provider/host, its fixed HTTPS gateway implementing `POST /v1/lab-commands`, reviewed costs, external restore epoch, current signed admission envelope, seven-report evidence inventory and operator public key. Keep gateway credentials and the signing private key out of Git, lab images, screenshots and reports. The backend needs protected `app.labs` configuration described in `../v47/IMPLEMENTATION.md`; signing and build execution remain outside the application.

Build immutable images from the reviewed Java, TypeScript and Python source archives. Pin actual archive/source/image digests and `getlancer-lab-v1`; publish only manifests linked to eligible reviewed BACKEND source releases. The native local protocol in `CONTRACT.md` is not the hosted provider command protocol. A reviewed provider must translate certified operation/input definitions, bind run/lease/epoch/command/image identities, collect native responses, deny arbitrary destinations and enforce the hard lease independently. Confirm nine hosted normal/failure/reset matrices on those exact images and run real network, tenant, cleanup, orphan and restore drills before signing admission.

## Startup measurements

Use at least 100 fresh disposable runs per adapter with both warm and cold startup cohorts. Keep at most ten admitted runs across the entire campaign. Record queue admission, healthy-ready and confirmed-cleanup times on a consistent provider clock. Publish hardware, image/configuration, per-class sample counts and queue wait separately. The checker calculates nearest-rank p95 ready time from admission, requiring warm ≤5000 ms and cold ≤30000 ms; it derives peak concurrency from reservation intervals, not a supplied count.

Sign the exact JSON report bytes with the admission operator's Ed25519 key and wrap them in the existing `{payload,signature}` envelope. The report schema is:

```json
{
  "version": 1,
  "mode": "HOSTED_EXECUTION",
  "provider": "actual admitted provider name",
  "operatorEpoch": "actual UUID",
  "evidenceSha256": "actual admission inventory SHA-256",
  "measuredAt": "actual UTC timestamp",
  "hardware": "actual hardware and benchmark configuration",
  "adapters": [
    {"language": "java|typescript|python", "sourceSha256": "actual primary-source SHA-256", "imageDigest": "sha256:actual immutable image digest", "samples": [
      {"runId": "fresh actual UUID", "kind": "WARM|COLD", "queuedAtMs": 0, "admittedAtMs": 0, "readyAtMs": 0, "cleanedAtMs": 0}
    ]}
  ]
}
```

This is a schema illustration, not a valid report. Replace every placeholder and zero with actual observations; supply all three adapters and the full raw sample cohorts. Each run must clean within the five-minute lease. Report files are limited to 1 MiB and 1000 samples per adapter; split larger campaigns into separately reviewed reports without inventing observations.

```sh
node ops/labs/check-v48-benchmark.mjs signed-startup-report.json admission-envelope.json evidence-inventory.json operator-public.pem actual-restore-epoch
```

This verifies current signed admission artifacts, report binding, source versions and measurement consistency. Independent review still needs to establish that the records came from the actual admitted provider and exact images. Passing protocol tests cannot establish that provenance.

## Recruited comprehension sessions

Recruit actual developers/students from the target audience. Record anonymized participant IDs, consent, task results and their own explanations; retain no account or merchant secrets. Start each participant without explaining the badges or giving the answers. Ask them to:

1. Distinguish Source only, recorded local execution/Replay and hosted execution availability, and explain whether clicking replay sends a new request.
2. Find the original recording time and source hash, select a Redis miss followed by a hit, and explain the store-read count and highlighted edge.
3. Inspect another-tenant/expired-identity denial and explain why a valid synthetic identity still needs current authority.
4. Inspect payment timeout, duplicate success and refund-before-payment, and explain pending state, idempotency, entitlement count and the emulator's lack of real money movement.
5. Download source/setup and describe how to reproduce behavior locally when hosted admission or quota is unavailable.

Document misconceptions, independent completion and corrections for every session. Any participant mistaking Replay for Live or the emulator for a real payment needs a design fix and another session. The product owner must record the study's recruitment, sample-size rationale and acceptance decision. Agent reasoning, automated browser tests and invented participant quotes cannot replace this evidence.

Merge full V4.8 only after exact-head CI/browser/visual review and actual provider, benchmark and comprehension acceptance are complete. This handoff does not approve the provider or activate the runtime.
