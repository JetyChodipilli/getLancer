Describe the concrete problem, resulting behavior and any deployment prerequisites.

Validation:

- [ ] Required backend, frontend, connected browser, secret/history, SAST, dependency and image checks pass for the current commit.
- [ ] Authorization, ownership and validation changes include meaningful positive and hostile-request coverage.
- [ ] New routes have an explicit authorization policy; new tables have reviewed grants and permission tests.
- [ ] Sensitive response fields and browser payloads follow the explicit DTO contract.
- [ ] Migration, key rotation, provider configuration and rollback requirements are documented when applicable.
- [ ] Any scanner applicability disposition retains the original finding and current reproducible evidence.
- [ ] External deployment acceptance is identified separately from disposable CI results.

For a security release, complete [the operator handoff](../ops/SECURITY_RELEASE.md) and attach the reviewed commit/run and evidence archive references without credentials.
