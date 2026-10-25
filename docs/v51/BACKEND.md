# V5.1 backend admission and source availability

The existing lab control plane now checks explicit signed native adapter coverage and an independent IoT execution policy. Hosted execution remains disabled by default. Local native examples, MQTT reproduction, browser simulations and controlled HTTP fixtures do not establish hosted provider, isolation, merchant or physical hardware certification.

## Signed adapter contract

New manifests carry `adapter: {runtime, version, state}` inside their existing signed JSON payload. `version` is an exact numeric `major.minor.patch`; `state` is `ACTIVE`, `DISABLED` or `RETIRED`. The full payload SHA-256 is persisted on the run and forwarded in every existing provider command, so language, version, state and scenario coverage are covered by the same signature and provider/run/image binding. The client cannot select a runtime or modify signed retirement state.

| Language | Signed runtime | Reviewed family | Scenarios |
| --- | --- | --- | --- |
| Java | `jdk` | 17.x | cache, security, payment |
| TypeScript | `node` | 22.23.4 or later in Node 22 | cache, security, payment |
| Python | `python` | 3.12.x | cache, security, payment |
| Go | `go` | 1.24.x | cache, security, payment |
| C# / .NET | `dotnet` | 8.x | cache, security, payment |
| PHP | `php` | 8.3.x | cache, security, payment |
| JavaScript (IoT runtime) | `node` | 22.23.4 or later in Node 22 | greenhouse-monitor |

Versions outside the reviewed family, language/runtime mismatch, unknown scenarios or language, disabled/retired adapters and a new adapter without an explicit version fail closed. An operator signature alone does not override this allowlist. Historical signed Java/TypeScript/Python manifests retain their three-pattern contract. Existing test-only Protocol/Fixture manifests and historical Python data/CPU manifests preserve their existing signed contracts and independent data/AI gate. A general native pattern cannot be relabelled as data/AI to obtain a different resource class.

Signed, currently source-eligible manifests can remain in `/api/v1/lab-manifests` as `executionMode: "Source only"` with `availability: {enabled: false, reason}` and signed adapter metadata when runtime certification expires, version coverage is unsupported or an adapter is disabled/retired. Read-only source metadata verifies the original payload hash and operator signature; execution still requires the current independently signed admission evidence and manifest expiry. Unsigned/revoked manifests and currently withdrawn, superseded or ineligible component owners/releases are excluded. Independent data/AI catalogue hiding remains compatible with V5.0. Reviewed source remains available through the existing components catalogue regardless of hosted lab activation.

## Curated IoT gate

`app.iot.enabled` / `APP_IOT_ENABLED` defaults to `false`. The policy recognizes the `IOT_SIMULATION` resource class, `iot-` manifest namespace and `greenhouse-monitor` scenario identity. Renaming a manifest, omitting its class or changing one identity does not bypass the gate. Enabled execution accepts only one curated greenhouse scenario, its reviewed operations (`publish`, `connect`, `disconnect`, `reset`) and a versioned Node adapter that matches the actual `labs/iot/runtime.mjs` source family. No simulator upload, physical actuator or arbitrary broker is admitted.

Hosted request inputs use the existing `INTEGER` schema: temperature -20..80 C, humidity 0..100%, threshold 0..60 C. Local/browser simulations additionally permit bounded decimal sensor readings; they are independent local evidence and do not widen the hosted manifest. An optional `topic` must equal `getlancer/<current run UUID>/telemetry`. The service creates the run UUID before input validation, inserts its canonical topic when omitted, and checks foreign topics before durable request persistence or provider forwarding. Users omit the topic on first admission because the server allocates the UUID. Subsequent requests may omit it or supply that exact run's topic.

The signed greenhouse schema itself is checked against the fixed ranges, field types, required inputs and operation allowlist. Requests cannot introduce destinations, source paths, broker URLs or additional resource parameters. The class reserves at most 128 MiB/run, four uncleared runs and 512 MiB independently, while the existing global ten-run/memory/cost ceilings, one active run/account, daily quota and five-minute lifetime remain enforced. Uncertain cleanup retains all reservations. A disabled IoT flag closes queued starts, running requests, observations and event replay before provider confirmation; the existing fenced STOP outbox handles cleanup and only confirmed cleanup refunds an unstarted reservation.

## Verification scope

Unit tests cover all eighteen native pattern/family combinations, version/language mismatch, unsupported scenarios, disabled/retired status, signed adapter modification and expired source versus runtime separation. IoT unit tests cover default denial, renamed/class/namespace bypass attempts, schema/range/operation/memory bounds and current-run topic identity. PostgreSQL integration cases exercise default-denied source visibility, current publisher authority, queued and running flag revocation, foreign topic/input rejection before effects, separate class limits, uncertain cleanup and unsupported/expired/unsigned manifest admission.

These integration tests use the explicitly labelled controlled HTTP protocol fixture. They verify Java admission, database/ownership and cleanup behavior; they do not claim to run a broker, Redis, native language lab or isolated VM. Actual native source and MQTT reproduction, signed hosted provider acceptance and final current-head CI are separate root acceptance gates. Validation results must be recorded from observed execution, not inferred from this test inventory.

## Owned validation, 2026-10-13

Four review passes covered contract completeness, fresh source/authority tracing, adversarial metadata and request combinations, and final compilation/runtime checks. Adversarial review closed two possible classification shortcuts: an IoT manifest cannot reuse the historical Protocol/Fixture exemption, and new backend adapters cannot acquire data/AI classification through an arbitrary class label. The native display label `C# / .NET` matches the shared catalogue.

The final owned source compiled with Maven 3.9.11 and the installed JDK 17.0.20 compiler module. All 21 lab unit tests passed with zero failures, errors or skips; test compilation also includes the 26 PostgreSQL lab integration cases (seven added for V5.1). `git diff --check` passed for the owned files. Local PostgreSQL server/client binaries are absent, so those integration cases have not yet been executed locally. Their actual current-head PostgreSQL result, broader backend suite, security scans and merge acceptance remain root CI gates.
