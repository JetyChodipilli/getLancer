# Backend dependency applicability

Reviewed through 2026-10-08; reassessment required before 2026-11-05.

The OSV scan retains every advisory match in its report. Unassessed matches fail CI. Each conditional assessment below is restricted to its exact advisory revision and package version, and requires its own passing application test report parsed as actual XML testcase elements, excluding logged text, CDATA and comments, from the last hour, newer than production, dependency and test inputs, plus hashes of those inputs. Future report timestamps are rejected. It is not a patched-dependency or zero-advisory claim.

## CVE-2026-47884 / GHSA-pc63-qcmh-9cmg

Package: `org.springframework:spring-webmvc:6.2.19`, OSV revision `2026-10-05T23:30:04.884745Z`.

[Spring's advisory](https://spring.io/security/cve-2026-47884/) requires XSLT view rendering, a catch-all mapping and an implicit view name. This backend does not use XSLT. Its application handlers return REST response bodies. A real Spring application-context test inspects every registered application handler, XSLT view/resolver beans, and rejects an empty registry. The scan also rejects XSLT references or resources anywhere in production sources, stale test evidence, advisory revision changes and expired assessments. All other advisory/package combinations continue to block.

The configured application is assessed as **not affected because the required rendering feature is absent**. The underlying Spring coordinate remains in the affected-version range. Spring lists 6.2.20 as enterprise-only and 7.0.9 as the OSS fix. Adopting XSLT rendering or changing the dependency/advisory requires a fresh assessment or framework upgrade; this assessment cannot transfer to another application. The time bound requires another review even if source remains unchanged.

Evidence: `ComponentsIntegrationTest.mvcHandlersDoNotExposeXsltViewRendering`, `scripts/sbom-dispositions.mjs`, the retained Java dependency report and the CI negative controls in `tests/sbom-dispositions.test.mjs`.

## CVE-2026-47890 / GHSA-j9f9-w8pj-32f8

Reviewed 2026-10-08. Package: `org.springframework:spring-webmvc:6.2.19`, OSV revision `2026-10-07T13:30:04.802772Z`.

[Spring's advisory](https://spring.io/security/cve-2026-47890/) requires view fragments streamed over Server-Sent Events, with attacker-controlled streamed data. The application source contains no SSE emitter, streaming response, fragment renderer, event-stream mapping or WebFlux configuration. A real Spring-context regression must inspect every application handler, its return type, response-body annotation and declared produced media types, plus streaming emitter beans. Adding these features or changing the advisory revision invalidates the assessment. The test must pass in the current CI run before the disposition is accepted.

The conditional assessment is **not affected because the required SSE fragment feature is absent**. The dependency remains in the affected-version range; the vendor lists 6.2.20 as enterprise-only and 7.0.9 as the OSS fix. Maven Central currently ends the 6.2 line at 6.2.19. The scanner's original severity is retained, including GitHub's Critical classification; it is not reduced to the vendor's Low rating. This assessment cannot authorize other SSE applications or versions.

Evidence: `ComponentsIntegrationTest.mvcHandlersDoNotExposeSseFragmentRendering`, exact source/report hashes, and negative controls proving missing/failed/skipped runtime proof, changed advisory metadata and introduced streaming configuration block the scan.

The runtime image gate retains the complete Trivy report and appends the corresponding application disposition only to the two exact Spring coordinate/CVE pairs above. It waits for passing backend verification and downloads the MVC and dependency reports from the same workflow. The checker revalidates the current source hashes, report hash, freshness, advisory revision and expiry. Every other HIGH/CRITICAL image finding blocks, including findings with no fix; the publisher image has no applicability exception. A passing gate is not a zero-advisory or patched-framework claim.
