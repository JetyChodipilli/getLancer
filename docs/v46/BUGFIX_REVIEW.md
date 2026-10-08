# V4.6 PR #40 bug-fix review

This follow-up uses Unlazy reproduction and completion gates with the gstack investigate/review workflow. A fresh-context native reviewer checked the fixes and owner/publication boundaries and found no further demonstrated blocker. No outside-model review is claimed.

## Corrected defects

- Public catalogue search used the owner's mutable title and summary even when moderation restored an older approved snapshot. Search now uses `published_context`. A PostgreSQL-compatible predicate reproduction failed before the change and passed afterward for the approved title and both private draft terms. The PostgreSQL integration regression also checks that explicitly submitting and approving the private title makes it searchable.
- A selected zero-byte ZIP passed browser required-file validation and skipped the source-upload branch, silently saving the base recipe. The creator form now rejects empty, oversized and incorrectly named ZIPs before any draft mutation. The browser regression checks the error, retained form values and absence of a saved draft.
- Account export omitted uploaded draft/submission source and immutable source releases. The export now includes owner-scoped draft, submission, publication and release snapshots as usable JSON, preserving source files and hashes while omitting duplicate `archiveBase64`. The response regression failed before the fix and passes afterward. PostgreSQL integration assertions cover an unpublished upload, two source versions and another actor's exclusion.

## Local validation

TypeScript and production build pass. All 165 Node tests pass, as do the authorization and database-permission source contracts. All production and test Java sources compile; the targeted Java suite passes 10 tests with zero failures, errors or skips. Local SQL reproduction covers the search predicate; it does not substitute for the real PostgreSQL integration suite.

The empty-ZIP regression passed on desktop, phone and tablet after failing before the fix. The standard local Playwright browser download was unavailable. A temporary alternate Chromium configuration is excluded from the PR; it is used only for the focused form regression, not as browser security evidence. The published revision must pass the standard responsive browser suite, real PostgreSQL regressions and connected Docker/publisher flows in all six CI jobs before the completion gates are closed.

The source-bound SAST input hashes are refreshed. Advisory dispositions, scanner rules, findings, expiry, permissions and dependencies remain unchanged.

## Operational learning

Public discovery must filter the same approved context that its response projects. An owner-only export must retain every persisted source stage and release, and JDBC JSON wrappers must be decoded before serialization. Native file-required validation does not reject a selected empty file; source validation must precede draft creation.

This runtime's proxy endpoint changes between tool invocations. Generate Maven proxy settings in the same invocation that runs Maven. Complete the production build before starting browser checks against its bundle.
