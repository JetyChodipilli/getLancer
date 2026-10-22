
## Exact reviewed upload download

`GET /api/v1/components/{slug}/source?sha256={archiveSha256}` returns the original approved uploaded ZIP as `application/zip` with an attachment filename and `Cache-Control: no-store`. It is anonymous and free. Current publication, withdrawal and creator eligibility are checked using public detail authority; private, withdrawn or ineligible artifacts return 404. A checksum that no longer identifies the current released archive returns 409 `SOURCE_VERSION_CHANGED`. The client verifies the received archive checksum before offering the download. Curated built-in files retain their existing browser-generated ZIP path. Raw archive base64 remains excluded from public detail, catalogue and account export JSON.
