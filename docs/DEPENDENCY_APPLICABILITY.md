# Backend dependency applicability

Reviewed 2026-10-06; reassessment required before 2026-11-05.

The OSV scan retains every advisory match in its report. Unassessed matches fail CI. The single conditional assessment below is restricted to the exact advisory revision and package version, and requires a passing application test report from the last hour, newer than production, dependency and test inputs, plus hashes of those inputs. Future report timestamps are rejected. It is not a patched-dependency or zero-advisory claim.

## CVE-2026-47884 / GHSA-pc63-qcmh-9cmg

Package: `org.springframework:spring-webmvc:6.2.19`, OSV revision `2026-10-05T23:30:04.884745Z`.

[Spring's advisory](https://spring.io/security/cve-2026-47884/) requires XSLT view rendering, a catch-all mapping and an implicit view name. This backend does not use XSLT. Its application handlers return REST response bodies. A real Spring application-context test inspects every registered application handler, XSLT view/resolver beans, and rejects an empty registry. The scan also rejects XSLT references or resources anywhere in production sources, stale test evidence, advisory revision changes and expired assessments. All other advisory/package combinations continue to block.

The configured application is assessed as **not affected because the required rendering feature is absent**. The underlying Spring coordinate remains in the affected-version range. Spring lists 6.2.20 as enterprise-only and 7.0.9 as the OSS fix. Adopting XSLT rendering or changing the dependency/advisory requires a fresh assessment or framework upgrade; this assessment cannot transfer to another application. The time bound requires another review even if source remains unchanged.

Evidence: `ComponentsIntegrationTest.mvcHandlersDoNotExposeXsltViewRendering`, `scripts/sbom-dispositions.mjs`, the retained Java dependency report and the CI negative controls in `tests/sbom-dispositions.test.mjs`.

The runtime image gate retains the complete Trivy report and appends the same explicit application disposition only to this exact Spring coordinate/CVE. It waits for passing backend verification and downloads the MVC and dependency reports from the same workflow. The checker revalidates the current source hashes, report hash, freshness, advisory revision and expiry. Every other HIGH/CRITICAL image finding blocks, including findings with no fix; the publisher image has no applicability exception. A passing gate is not a zero-advisory or patched-framework claim.
