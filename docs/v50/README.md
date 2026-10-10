# V5.0 — reproducible analytics and CPU inference

V5.0 implements roadmap service S06 / FR-036, FR-037 and FR-039 on the V4.9 baseline. The review began with the full phase roadmap, V4.7 runtime lifecycle, V4.8 source/evidence pipeline, V4.9 immutable education releases and existing commerce authority. The implementation uses five native agents with separate runtime, education, frontend, security and QA ownership. Root owns integration and verifies their evidence.

## Architecture and system design

The React/Vinext frontend remains an inspector of measured evidence. Four Python standard-library scenarios run from free source on the user's machine. A fixed synthetic dataset or original frozen JSON model produces schema-labelled tables, charts, contextual evaluation and measured hash/timing envelopes. No request selects a filesystem path, external URL, executable loader or dependency.

The material builder executes the real source, checks default and changed-input results, packages a fixed byte-deterministic source inventory and publishes recordings with an explicit REPLAY label. The frontend verifies source/data/model/evaluation identities, canonical result hashes, input identities and contextual metrics before showing results. Changing the example selection selects a recording; source reproduction performs the computation.

Hosted execution keeps the existing Spring/PostgreSQL control plane, signed operator manifest, immutable source binding, private owner routes, reservations, fenced provider commands, expiry, resource accounting and confirmed cleanup. `APP_DATA_AI_ENABLED=false` adds an independent admission/revocation barrier. Enabling it does not replace `APP_LABS_ENABLED`, current provider certification, budgets or operator authority. Source downloads and recorded local evidence remain usable without hosted activation.

Education drafts add typed, allowlisted provenance and separate code/data/model licences. New analytics and AI submissions require synthetic rights-cleared evidence; AI accepts frozen JSON models only. Reviewed snapshots retain that evidence, and accepted paid agreements freeze it alongside the existing exact source licence. Historical releases and accepted agreements retain their prior semantics. Public projection excludes private or unexpected nested fields.

No new application dependency, migration, billing flow, GPU service, training pipeline or arbitrary upload execution is introduced. The local runtime uses bounded forms, loopback-only HTTP, request/output caps, absolute deadlines, limited requests, finite process lifetime and supported OS limits. These local protections do not certify hosted isolation.

## Scope and ownership

| Workstream | Delivered contract | Evidence |
| --- | --- | --- |
| Runtime | Two analytics and two inference scenarios, original MIT assets, measured envelopes and free source | [Runtime](RUNTIME.md) |
| Education | Draft validation, immutable provenance, safe public projection and agreement history | [Education](EDUCATION.md) |
| Design | Accessible evidence inspector and creator/reviewer/purchase disclosures in Spectral Studio | [Design](DESIGN.md) |
| Security | Loader, input, privacy, source identity, runtime and activation review | [Security](SECURITY.md) |
| QA | Independent numerical oracles, changed-input, hostile HTTP and evidence rejection tests | [QA](QA.md) |
| Integration | Build, local Docker, CI, feature revocation and measured acceptance limits | [Validation](VALIDATION.md) |

## Local reproduction

Python 3.12+ and Node 22.13+ are required. The curated runtime requires no pip installation or account. Use:

```sh
npm run data-ai:verify
npm run data-ai:build
npm run data-ai:material
```

Browse `/labs/data-ai` after the application build. Download the source archive and follow its README to change inputs in a fresh local execution. `npm run labs:docker` reproduces the existing nine language scenarios plus the four curated data/AI scenarios in the existing disposable local test environment.

The existing guarded Java integration suite requires `TEST_DATABASE_RESET=true` and a dedicated `getlancer_test` database/schema. CI uses PostgreSQL 16 and includes the new feature-switch lifecycle, education, commerce, numerical/security and responsive browser regressions. [Validation](VALIDATION.md) separates checks actually run in this workspace from authored CI checks and external activation gates.

## Deployment boundary

Hosted activation is pending independent provider/image, isolation, network, budget, cleanup and restore evidence. Keep both hosted activation flags disabled until those gates pass. Existing live education collection has its separate merchant/operator gate. No production deployment or phase-wide hosted acceptance follows from local source execution.

The exact shared contracts are in [CONTRACT.md](CONTRACT.md). Future sensor/IoT expansion belongs to V5.1; operational scale and incident drills belong to V5.2.
