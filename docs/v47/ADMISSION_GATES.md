# V4.7 public runtime admission gates

These gates remain open. Development/control-plane tests cannot approve public untrusted-code execution. Keep the runtime disabled and retain free sources/setup while any gate is unmet.

- [ ] A1: Independently verify the selected KVM host/provider and tenant boundary, boot timing, host patch/jailer controls, unprivileged CPU/memory/PID/file/temp/output limits. Shared-kernel containers alone fail this requirement.
  EVIDENCE: pending; no provider selected, `/dev/kvm` unavailable in this development environment.
- [ ] A2: On the selected provider, deny production/private/link-local/metadata/IPv6 destinations, host sockets/mounts and cross-run data. Health, gateway, reset and declared operations have negative network controls.
  EVIDENCE: pending; protocol fixture tests are not provider network tests.
- [ ] A3: Review the actual Redis/image/dependency licence and obligations, pinned source/image/protocol, SBOM, restricted build approval and maintainer. No production database, account or payment secret enters a build/runtime.
  EVIDENCE: pending; no Redis runtime or certified educational lab image is hosted.
- [ ] A4: Approve a dated provider worksheet covering fixed, idle, build, execution, storage and egress costs; configure conservative per-run plus daily/monthly budget caps and demonstrate the admissions-stop drill.
  EVIDENCE: pending; development does not imply spending approval.
- [ ] A5: Run real-provider cancellation, expiry, stale lease, crash, orphan reconciliation and failed-cleanup drills. Revoke routing first; keep capacity reserved until cleanup; quarantine failures.
  EVIDENCE: pending; run/release protocol tests alone cannot prove real resource disposal.
- [ ] A6: Restore a disposable backup with old active runs. Rotate the independently stored epoch, block stale routes and clean old provider resources before signing replacement evidence and resuming admissions.
  EVIDENCE: pending; the epoch must never be restored with application metadata.
- [ ] A7: Record dated independent admission/control review and provider soak with honest admitted-run denominators. Every required runtime threat/resource/race/cleanup check must pass before public activation.
  EVIDENCE: pending; V4.7 implementation is in progress, not release certified.

## Evidence envelope

An operator Ed25519 key signs exact UTF-8 JSON bytes; the backend receives its X509 DER base64 public key, never a private key. Envelope: `{"payload":"<base64 JSON>","signature":"<base64 Ed25519 signature>"}`. The signed payload binds provider, KVM isolation, required licence/budget/network/cleanup/restore approvals, expiry, evidence inventory SHA-256 and external restore epoch. A signature records an accountable review, not an automated proof of isolation.

The offline verifier `node ops/labs/certify.mjs admission-envelope.json evidence-inventory.json operator-public.pem restore-epoch` verifies the envelope and all seven current hashed reports. The inventory records version 1, PRODUCTION mode, provider, operatorEpoch, and one PASS record each for isolation, network, cleanup, restore, licence, cost and build. Each record includes a relative evidencePath, artifactSha256 and recordedAt. Test/replay mode, links/traversal, altered bytes, missing reports, expired approval and a mismatched restore epoch fail.

Review reports before signing. This tool never signs an approval, activates the runtime, clones contributor repositories, downloads dependencies, starts containers or calls a provider. Report authenticity and conclusions require independent operator review. Unit-test synthetic reports are confined to test fixtures and cannot be treated as actual hosting evidence.
