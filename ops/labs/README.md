# Native runtime certification artifacts

The existing host-only CLI remains `node ops/labs/certify.mjs admission-envelope.json evidence-inventory.json operator-public.pem restore-epoch`. Pass an optional fifth positional signed manifest envelope for exact native certification. The result token is `NATIVE_MANIFEST_ARTIFACTS_VERIFIED`; it verifies artifacts, not hosted qualification.

The manifest must name a supported exact language/runtime/framework version, ACTIVE adapter, maintainer handle, current review and all licence/reproduction/resource/isolation attestations. Its reproduction SHA-256 identifies the dated build artifact in the signed host inventory. Host and manifest use the same independently trusted Ed25519 key. A manifest cannot outlive host approval.

Programmatic `verifyBundle` accepts expectedAdmissionSha256/expectedInventorySha256; `verifyManifestBundle` additionally accepts manifestPath/expectedManifestSha256. Pins hash the same bounded captured bytes subsequently signature-checked. Evidence uses descriptor-relative reads with no linked path components.

The current-MFA admin labs interface permits bounded limits, immutable source-only registration, signed execution publication and permanent revocation. Runtime SQL privileges permit INSERT and UPDATE(revoked_at) only; immutable triggers prevent changing signed bodies/execution flags or restoring revoked certification. Source-only entries require a new distinct signed identity for live publication.

Real KVM deployment, reviewed images, isolation/network/cleanup drills, startup benchmarks and production soak require independent execution and responsible operator sign-off. No fixture certifies those outcomes.
