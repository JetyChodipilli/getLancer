# Application audit for PR #56

Expand the upload/form repair into a review of the application’s public, account,
builder, buyer and operator journeys. Keep changes in PR #56 and retain existing
authorization, archive limits, immutable source releases and payment safeguards.

## Work order and acceptance

1. Map each domain's reads, writes and selection changes. Review validation,
   loading, empty, failure, retry, stale responses and loss of authorization.
2. Reproduce supported defects with focused behavioral regressions. Repair the
   cause and preserve successful state when a subsequent refresh fails.
3. Review shared form rhythm, text wrapping, mobile width, focus, disabled and
   submission states using the established design tokens.
4. Check performance instrumentation and bounded continuation. Make performance
   improvement claims only from actual measurements.
5. Run local supported checks and the complete GitHub workflow on the final PR
   revision. Resolve failures before merging, then inspect all main workflow jobs.

## Coverage allocation

| Area | Review focus | Owner |
| --- | --- | --- |
| Authentication, account, profile | Initial load, account changes, save/submit recovery | Auth agent |
| Publishing, media, templates, source commerce | Draft retention, filters, uploads, frozen source authority | Publishing agent |
| Teams and business hiring | Search races, lead updates, continuation, membership authority | Teams agent |
| Delivery, payments, maintenance | Selection changes, uncertain mutations, retry and financial state | Payments agent |
| Admin, moderation, hosting, runtime | Queue continuation, privileged actions, deployment recovery | Admin agent |
| Cross-app design and performance | Overflow, form hierarchy, component states, measured claims | Design agent and integrator |
| Components, education, discovery, API proxy | ZIP diagnostics, same-file retry, stale history, timing headers | Integrator |

## Evidence and boundaries

Record confirmed findings, rejected hypotheses and regression results in
`APPLICATION_AUDIT_RESULTS.md`. Source review and mocked browser tests establish
specific contracts; they do not certify every production integration. GitHub CI
provides PostgreSQL, browser and Docker verification unavailable in this local
environment. No LIVE payment, production data restore, KVM qualification,
seven-day soak or human acceptance result is inferred from these coding checks.
The configured local environment file remains ignored and outside the PR.
