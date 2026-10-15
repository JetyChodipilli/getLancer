# Education source retention budget

Reviewed base: GitHub main `96b45e32dc9664e896c0558f3caa2e8e67108594` (V5.2). This focused continuation supplements the earlier audit; it does not establish full manual repository coverage or production capacity.

## Finding and enforcement

An approved developer could repeatedly replace a FREE draft's source using its current revision. Each accepted upload created a new private object and an immutable `education_free_packages` row, while moving the draft's pointer. The existing 100-release limit counted release identities, so it did not bound accumulated packages. Changing access mode detached the pointer without removing retained history. Request rate limits only limited the pace of this storage growth. This is a cumulative resource-allocation issue (CWE-770); it requires an approved developer account and access to an owned editable draft.

Each builder now has a lifetime budget of **100 retained free source packages across all projects**, including replacements, detached draft packages and historical releases. This is an upload retention policy, independent of active publishing slots. The workspace explains it before upload. Normal replacements below the limit remain available. Further uploads return HTTP 409 `EDUCATION_SOURCE_LIMIT`.

The service counts package ownership directly, rather than joining current release pointers. It checks the budget in both upload transactions under the existing publishing owner advisory lock: before object storage and immediately before committing a package. Storage calls remain outside those transactions. Concurrent uploads to different projects cannot both commit the final slot. A confirmed losing transaction keeps the existing cleanup of its own unlinked object.

V40 adds an owner index. The count examines at most 100 matching rows, including when a historical owner already exceeds the limit. The migration does not delete packages, rewrite submitted agreements, or change private storage credentials or permissions. Existing over-limit owners retain approval, inspection and eligible downloads; additional uploads are denied. Operators must review any exceptional retention increase rather than reset the count by deleting agreement history.

## Validation

Three regression tests fail on the unchanged base: an exhausted owner still uploads, a preexisting over-limit owner uploads, and two concurrent uploads across distinct projects both commit the final slot. Focused checks after remediation cover:

- ordinary replacement, superseded/unlinked historical rows, mode switching and a second product;
- denial before storage once exhausted, with another owner still able to upload;
- concurrent final-slot serialization and cleanup of only the losing upload;
- approval, recent-MFA source inspection and exact public download for an existing over-limit owner;
- existing ownership, stale revisions, rollback cleanup, immutable source/licence snapshots and education commerce behavior.

All 40 focused Java tests passed after remediation (26 education, 12 education commerce, two storage), as did 17 education/security-header Node tests, the 287-route source contract and offline Maven packaging. A fresh independent source review found no concrete remaining bypass or regression within the six-file patch.

The SAST applicability manifest also binds the changed service, regression class and new migration. Its three existing GET assessments and required tests are unchanged. An additional 34 authentication/data-exposure regressions, seven manifest tests and the current-source evidence validator passed locally; GitHub generates its own evidence with the actual commit/run identity.

Commands: `mvn -o -q -Dtest=EducationIntegrationTest,EducationCommerceIntegrationTest,CommerceStorageTest -Djunit.jupiter.execution.timeout.default=60s test`, run from `backend` against the disposable `getlancer_test` database; `mvn -o -q -DskipTests package`; `node --test tests/education.test.mjs tests/security-headers.test.mjs`; `node scripts/check-security-contract.mjs`. GitHub CI runs the owning package and repository checks on the published commit; use that commit's completed checks as the final integration evidence.

The independent pre-patch investigation uses direct source because the graph service returned `Transport closed`. A new Codex Security scan could not start after its initial snapshot setup failed. This source/test remediation and independent review supplement the completed earlier scan; they are not a new provider scan result. The available Code Review connector supplies PR diagnostics, so direct-main publication has no plugin PR-review result.

## Limits

The 100-record limit bounds represented compressed archives to 500 MiB per builder because each package is at most five MiB. It does **not** prove an absolute physical bucket bound: failed best-effort cleanup and ambiguous commit outcomes can leave objects requiring operational reconciliation. Existing data is retained. Multiple approved accounts, database/audit growth in other features, actual bucket lifecycle/IAM, production throughput and failover need separate operational controls and measurement.
