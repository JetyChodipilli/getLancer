# V5.0 integrated validation

The rebuilt branch is `codex/v5.0-data-ai`, based on V4.9 main `95ba03da3264d09b9efdeea799b813d7ee9f760c`. This is the implementation rebuild; the previously reported unpublished checkout was unavailable. No production deployment or hosted provider activation is performed.

## Measured local checks

The production Vinext build passed with the data/AI materials generated from actual source. The joined Node regression run passed 253 tests with zero failures, skips or cancellations. TypeScript and frontend/security/database source contracts passed. Independent parent runtime verification passed both the actual-source verifier and eleven hostile/source-bound regressions. Education materials were regenerated from original seeds with actual data/model byte digests.

The Linux workspace provides Node 24.19.0, Python 3.12.14 and Java 17.0.20. Repository CI verifies the supported Node 22 and Java 17 environments. A temporary Maven toolchain is isolated outside application source; no application dependency or lockfile changes were introduced. Native PostgreSQL could not run in this restricted container, and the Playwright CDN download returned truncated payloads. Those limitations do not relax the guarded database schema, tests or browser acceptance. CI uses actual PostgreSQL 16 and Chromium with system dependencies.

## Control plane acceptance

A separate default-off data/AI policy is enforced before reservations and on current run operations, detail/activity authorization and watchdog/START delivery. Recognition covers signed `resourceClass`, curated scenario identity and the `data-ai-` namespace. Known identities determine the correct class even if a manifest is renamed. Unclassified data/AI namespaces and mismatched class declarations fail closed. Ordinary signed lab manifests retain their original semantics.

Each DATA_ANALYTICS and AI_ML class permits four uncleaned reservations and 1,024 MiB independently, with a 256 MiB maximum per run. Existing global/user/budget limits also apply under the same database admission lock. Failed, cancelling and uncertain reservations retain class capacity until confirmed cleanup; denied and unhealthy runs do not consume the healthy daily allowance. Disabling execution closes private routes and prevents queued START dispatch while preserving STOP/cleanup. Existing 24-hour input/response redaction and deletion handling remain in place.

Guarded PostgreSQL tests cover default denial without reservation, renamed/signed alias recognition, active and queued revocation, held uncertain cleanup, independent class pools and ordinary-lab compatibility. They use the controlled provider fixture to test control-plane authority; they do not certify VM execution or hosted isolation.

## CI and final release evidence

CI reproduces the real nine native-language scenarios and four data/AI scenarios, deterministic source/recording materials, clean extracted source and the read-only offline local Docker image. It also runs full Java/PostgreSQL regression, immutable education/commerce privacy tests, production artifact/dependency/source scans, joined Node tests and desktop/phone/tablet journeys. The new browser matrix includes downloads, source/replay/hosted distinctions, returned tables, changed cases, hostile evidence/retry, stale-request races, keyboard access, narrow reflow, text enlargement, reduced motion and education disclosures.

Independent QA/security review and final PR/head check identities are recorded after execution. Hosted certification, production provider activation and production deployment remain separate pending operator evidence.
