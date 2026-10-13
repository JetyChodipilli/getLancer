# V5.1 validation

Observed local acceptance on 2026-10-13:

| Check | Observed result |
| --- | --- |
| Production Vinext build, TypeScript and source/asset security | Passed; explicit authorization coverage and database permission source classification passed |
| Native core matrix | Six actual language implementations, eighteen labs, 156 requests per language against Redis 7.2.16 |
| Clean source downloads | All six archives extracted into fresh directories and executed independently |
| Go native regressions | Nine race tests and vet passed; real shutdown/Redis cleanup probe passed |
| .NET native regressions | Actual ASP.NET execution passed 38 HTTP/resource/concurrency checks; canonical UTC-Z evidence passed |
| PHP native regressions | Actual CLI process passed 977 checks, including strict HTTP response framing |
| IoT source/runtime | 21 focused tests; direct builder CLI, deterministic verification, real current/extracted MQTT actions and complete cleanup passed |
| Framework source/runtime | Seven focused checks; all three native compilers/linker and clean deterministic reproduction passed; three dependency audits reported zero advisories |
| Backend lab tests | Local Java compilation and 21 lab unit tests passed; initial PR CI passed all 534 Java/PostgreSQL tests |
| Node regression suite | 306 tests passed with zero skips, including actual production Worker hash-CSP responses |

The production browser suite exercises changed telemetry, disconnection/recovery,
reset, unique sessions, foreign-topic denial, bounded histories, actual native
framework controls, free source hashes, opaque sandbox isolation, keyboard/touch
controls and automated WCAG checks at desktop/phone/tablet widths. Required CI
also runs the complete existing browser suite and connected service journeys.

The merge gate is the passing required checks attached to the V5.1 pull request
head in [GitHub Actions](https://github.com/JetyChodipilli/getLancer/actions).
CI verifies the actual .NET SDK build/run/publish path, PostgreSQL integration,
the complete account-free Docker matrix, connected browsers, encrypted restore,
secret/history scans, semantic security analysis, dependencies and runtime images.
Local source checks do not stand in for these checks.

The shared executor could run actual .NET/ASP.NET assemblies compiled by the
official Roslyn compiler but could not run MSBuild's process metadata inspection.
Docker and PostgreSQL service binaries were also unavailable locally. These
acceptance checks use the real SDK/services in CI; no mock output or simulated
passing result is substituted.

Integration review corrected .NET's noncanonical timestamp emitter, exposed and
fixed the IoT builder's direct-entry circular import, and verified that opaque
framework previews preserve their hash-only CSP rather than app nonce rewriting.
Negative cases reject linked/oversized package sources and foreign/encoded
preview destinations. Framework advisory findings in initial pins were patched
before the checked native builds.
The initial CI run also required refreshing the source/test inventory for the
existing three reviewed GET assessments. Their allowlist, proof requirements and
expiry were retained; no new finding was suppressed. PHP's test client was fixed
to validate the declared HTTP response frame rather than await TCP EOF after an
already-complete rejection. Truncated or ambiguous responses still fail.

The requested Codex CLI review was attempted read-only, but its authentication
failed and the bounded command produced no review verdict. Native leaf reviews,
independent parent source review and executable acceptance remain the observed
evidence. Alpha's required CORE/POLICY resources were missing, so that fixture
workflow could not execute. Neither unavailable tool is represented as a pass.

No physical-device execution, dated hardware proof, production-provider
certification, manual screen-reader session or Firefox/Safari evaluation is
claimed. Hosted execution remains independently disabled by default.
