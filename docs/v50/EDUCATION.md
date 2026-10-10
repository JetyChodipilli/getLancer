# V5.0 education evidence and agreement compatibility

The existing typed education draft accepts optional `dataAiEvidence`. No controller, table, payment system or uploaded-code execution is added. Submission still uses the current product/profile eligibility, revision lock, contribution consent, immutable source binding and canonical agreement digest. Review retains recent administrator MFA, CSRF protection, exact package inspection and the separate paid-collection authority.

## Shared draft schema

The only allowed fields are `codeLicense`, `dataLicense`, `dataProvenance`, `dataSha256`, `modelLicense`, `modelProvenance`, `modelSha256`, `modelFormat`, `evaluationSplit`, `evaluationProtocol`, `outputSchema`, `limitations`, `redistributionAllowed`, `syntheticData`, `noRemoteCode`.

| Fields | Draft | New submission |
|---|---|---|
| Code/data licenses, data provenance, output schema, limitations | Optional text, maximum 4,000 characters | At least 3 trimmed characters each |
| `dataSha256` | Empty or 64 lowercase hexadecimal characters | Required SHA-256 |
| Model license/provenance | Optional text, maximum 4,000 characters | Required for AI, at least 3 trimmed characters |
| `modelSha256`, `modelFormat` | Empty or lowercase SHA-256 / exactly `JSON` | Required for AI |
| Evaluation split/protocol | Optional text, maximum 4,000 characters | Required for AI, at least 3 trimmed characters |
| Redistribution, synthetic-data and no-remote-code flags | Optional actual booleans | All three must be true |

Unfinished drafts may omit or partially fill this object. A blank category can retain a partial draft; `FULL_STACK` and `IOT` reject the object. Analytics may leave model and evaluation fields empty. AI accepts only frozen JSON model evidence. The existing strict request mapper and nested Bean Validation reject unknown keys, nontext disclosure values, coerced booleans, malformed hashes, oversized text and executable/remote loader formats.

## Immutable and safe projections

Every new analytics/AI submission requires complete relevant evidence. Approval rechecks present evidence. An already submitted historical snapshot with no object retains its original review contract, and no historical row or accepted purchase is backfilled. Frozen evidence contributes to `sourceHash`; submitted content and accepted price/license/source terms retain the existing database immutability triggers.

The existing account-export education projection is shared by public release snapshots, source offers, paid offers and accepted agreements. Its top-level and nested allowlists exclude academic annotations, internal storage keys, unexpected fields and source bytes. Data/AI projection additionally checks each field's type, bounds, digest shape and JSON format. Historical buyer reads project stored agreements without rewriting them; current component visibility remains a separate overlay. Owner academic annotations remain in their existing authorized export record, outside the source agreement, and sharing remains separately consented and retractable.

Rights for original code, synthetic data and frozen models are separately declared. A declaration is not a general accuracy guarantee or permission to execute seller uploads. Existing checked source-package bindings identify actual archive bytes; evidence identifies the separately disclosed dataset/model bytes, and reviewers must inspect those claims under the existing authority.

## Original seed material

The analytics example uses the actual original `synthetic.csv` digest and separate original MIT data disclosure. The AI example now reads a fixed original JSON likelihood model, four synthetic reference rows and a separate two-row evaluation fixture; it never trains or loads executable models. `MODEL_LICENSE` records separate original MIT model rights. Its two-row accuracy carries explicit limitations and implies no population performance.

The existing material builder accepts only four known seed folders and fixed file inventories, rejects symlinks or foreign files, derives dataset/model digests from actual bytes, and packages deterministic ZIP headers. Seed metadata is illustrative source evidence, never live review or payment authority. Rebuilding these examples does not modify any stored historical release or accepted agreement.

## Verification

The leaf gate runs six `EducationDataAiTest` cases and three `CommerceEducationEvidenceTest` cases. They exercise strict application-mapper/DTO validation, missing/false publishing declarations, category separation, analytics/AI requirements, historical absence, canonical hash changes, safe nested public/export/offer projections and exact accepted evidence. Privacy tests include polluted positive controls so a missing canary assertion cannot pass accidentally.

The existing PostgreSQL education and commerce suites add real HTTP freeze/review/export and polluted historical purchase-read cases. Full integration and concurrency acceptance requires the guarded disposable `getlancer_test` database with PostgreSQL 16; results must distinguish checks actually executed from authored CI coverage. The leaf ledger and integrated validation report record the measured outcomes and any environment blockers.
