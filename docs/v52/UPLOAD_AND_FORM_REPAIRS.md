# Component upload and form recovery repairs

This follow-up repairs supported frontend/backend defects found against main
`b634a35624e951435b4eead763ae7b947afe42ed`. It preserves archive limits,
publication review, current authorization and separate production activation gates.

## Findings and resulting behavior

| Trigger | Previous behavior | Repair and observing checks |
|---|---|---|
| Component source upload rejects a file or version | UI replaced the status with 400 and discarded field diagnostics and the support reference | Retain server status, code, reference and field errors. A saved draft is reused on retry; 401/403 clears protected UI. Backend archive, version, scenario and rights validation identify the failing field. `component-upload-regression.spec.ts` and `ComponentsIntegrationTest` cover recovery and frozen-source preservation. |
| Upload succeeds but the following list refresh fails | Editor closed and displayed a successful save | Keep the editor and existing draft identity; show the refresh failure without a success message. College-context saves use the same confirmation rule. |
| Education upload fails before or during a request | Selecting the same ZIP again did not reliably trigger the file change event | Capture the file and reset the picker before handling it. Reject empty, oversized and non-ZIP selections locally; retain unsaved draft fields. `education.spec.ts` covers rights rejection, server failure and same-file retry. |
| Inquiry A's history responds after the user opens B | A's events could appear under B's title | Bind history, loading and errors to the selected-request generation. Closing/unmounting invalidates earlier responses. `request-history-regression.spec.ts` uses deliberately reversed responses and a separate failure-recovery case. |
| More than 100 retained college contexts | The hard-limited collection hid records | Return the existing bounded page contract, newest updated first with a product-ID tie-breaker. Independent continuation retains records on retry and clears protected data on authority denial. Backend regression traverses 110 owner records and excludes another owner's context. |
| A successful HTTP response contains invalid JSON | API helper silently substituted an empty object, allowing false confirmation | Reject an unconfirmed response explicitly. Preserve 401/403 for malformed error responses, support 204/205 and retain idempotency headers supplied as Headers or tuples. `api-response.test.mjs` exercises the actual transformed TypeScript module. |
| Wizard and consent controls use shared form styles | Fieldsets lacked internal rhythm and consent text could stack below its checkbox | Apply spacing to visible fieldsets, keep wrapping consent inline with a 44px target, compact field errors and give the focused error summary an accessible name. `form-layout-regression.spec.ts` checks actual wizard layout, overflow and enlarged text. |
| Local doctor inspects an encrypted admin MFA account | Retired plaintext TOTP column caused a false failure | Require exactly one active administrator with a complete encrypted MFA envelope. Subprocess regressions cover current, legacy, incomplete, suspended and duplicate configurations. |

## ZIP reproduction

The actual `getLancer_Sample_Component_Upload.zip` (6,574 bytes; SHA-256
`41c75e307c0d102e84d8868c473575faca49e75e6185a2be6246b0b5226562fa`)
passed the production Java `StaticArchive` and `ComponentArchive` inspectors.
Only the multipart transport was stubbed for that parser check; it does not prove
authenticated HTTP upload or publication. The larger test kit was correctly
rejected because its wrapper, reports and nested archives are not a component source.

Component uploads require one individual ZIP with only root `index.html`,
`README.md` and `LICENSE`, each at most 100 kB, with a full MIT notice. The
compressed archive remains limited to 5 MiB. The editor explains this and the
connected-account requirement before submission. A frontend-only preview cannot
store or moderate an uploaded archive.

## Verification and boundaries

Run the full existing workflow, including PostgreSQL integration, frontend build,
Node checks, desktop/phone/tablet browser journeys, connected Docker journeys,
dependency review, semantic security and container checks. The added browser
tests run in the existing three viewport projects; they do not replace connected
HTTP/database tests. Final PR and merged-main workflow results identify the tested
revision. Preserve raw failures and fix them before merging.

Local verification used Node 24.22.1 and Java 17.0.20. This workspace lacks the
Docker daemon, PostgreSQL binaries, Maven and application dependencies; direct
package-registry access timed out at the network proxy. The private ignored
environment was prepared, but no local database or owner administrator was created.
Real browser/DB verification therefore relies on the actual GitHub Actions jobs.

The audit examined representative account, showcase, request, team, business,
delivery, source-commerce, maintenance, hosting, education, component and runtime
boundaries. It found no measured basis for a speculative cache, pool increase or
capacity claim. These repairs do not certify zero remaining bugs, arbitrary traffic
capacity, hosted KVM operation, LIVE payments or production launch acceptance.
