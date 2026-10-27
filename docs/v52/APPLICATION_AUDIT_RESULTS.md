# PR #56 application audit results

The audit expands the original ZIP/form repairs with six domain reviews and an
independent integration review. Findings below are supported by source inspection
and focused regressions. This is a bounded audit, not a claim that no bugs remain.
The plan is in `APPLICATION_AUDIT_PLAN.md`; the initial repairs and actual sample
ZIP parser check are documented in `UPLOAD_AND_FORM_REPAIRS.md`.

## Repairs

| Area | Confirmed problem and resulting behavior | Regression coverage |
| --- | --- | --- |
| Account notifications | Continuation could run before the first page and errors resembled an empty list. Loading/error/retry and confirmed page metadata now gate continuation; duplicate read requests are serialized. | Auth browser and PostgreSQL regressions |
| Profile and account identity | Uncontrolled drafts and late responses could survive a changed account. Identity-scoped forms and lifecycle guards prevent old inputs, downloads and errors from reaching the next account. | Auth browser and executable async-routine tests |
| Profile save and submission | A caught refresh failure could be presented as success; unsaved edits could be omitted from submission. Await explicit refresh results, distinguish accepted writes from failed reads, and provide refresh-only retry. Saving availability also records its confirmation timestamp. | Auth browser, Node and PostgreSQL regressions |
| Publishing workspace | Transient refresh errors cleared authentication and editors; late filter responses replaced newer selections. Apply complete loads atomically, reject obsolete responses and preserve edits on temporary failures. | Publishing browser and executable async-routine tests |
| Proof media | Stale proof lists and lost completion IDs made recovery ambiguous. Load current images on open, retain idempotent completion IDs, block stale writes during resync, and stop continuations after editor closure. | Publishing browser, Node and PostgreSQL regressions |
| Source templates | Uploads bypassed shared response validation and temporary refresh failures unmounted forms. Use the shared multipart/error contract, retain forms during temporary outages and isolate each template's inputs. Preserve field names while validation errors are announced separately through their descriptions. | Publishing browser and Node regressions; existing commerce integration suite |
| Teams and hiring | Late initial candidate searches survived refresh/actor changes. Invalidate both search and workspace generations; clear protected caches on lost authority. | Team/business browser and PostgreSQL regressions |
| Lead edits | Resending unchanged overdue dates or expired assignees blocked unrelated note/status edits. Omit unchanged constrained values while preserving explicit clear/reschedule semantics. | Actual payload helper, browser and PostgreSQL regressions |
| Team moderation and sourcing | Accepted writes followed by failed reads announced success. Report accepted-write/failed-refresh separately and retry reads without repeating mutations. | Team/business browser regressions |
| Delivery and checkout | Routine mutation failures discarded forms; closed or competing gateway callbacks could alter the wrong state. Retain retryable forms, reconcile uncertain effects, scope callbacks to checkout/selection and retain uncertain idempotency keys. Only definitive order rejection permits a fresh key. | Payment lifecycle Node/browser regressions |
| Delivery refund reconciliation | Refund creation receipts could be marked processed while the provider still reported zero refunded. Persist refund references and immutable provider membership/amount, process only verified terminal receipts, and hold completion while evidence remains pending. | PostgreSQL signed-webhook/provider-fixture regressions, including late receipts and replay |
| Moderation and privacy | Collection replacement left old continuation cursors; failed reads lacked recovery; denied authority left cached records. Reset continuation only with a confirmed replacement, scope reads, expose retries and clear protected state on denial. | Admin browser and PostgreSQL regressions |
| Admin taxonomy and reports | Post-save refresh could overwrite a newly selected taxonomy; triage continuation failure discarded earlier rows. Scope responses to the current kind and retain confirmed report pages for retry. | Admin browser regressions |
| Email recovery and appeals | Failed-mail expiry projection disagreed with retry's 24-hour limit; own appeal history omitted the submitted evidence URL. Align eligibility and return evidence only in the actor-owned projection. | Admin PostgreSQL regressions |
| Confirmation forms | Fields remained editable while an earlier password/review value was submitting. Freeze the submitted fieldset, retain values after failure, and enable retry. | Navigation recovery browser regressions |
| Education source selection | Delayed purchase callbacks from an earlier release could reopen checkout or replace current state. Bind all continuations to the current release generation. | Delayed-order education browser regression |
| Catalogue timing | The frontend proxy removed the backend's application timing header, preventing the load collector from reading it. Preserve `Server-Timing` through the existing restricted response allowlist. | Actual transformed proxy handler test, including denied internal/CORS header forwarding |
| Long metadata and mobile forms | Unbroken accepted titles, summaries and names could clip or expand cards/menus. Add scoped wrapping and retain independent source-code scrolling. Correct the new error-layout fixture's missing mobile viewport declaration. | Adversarial real-page browser checks, 320px route matrix and enlarged-text form check |

## Refund migration and database privileges

`V51__payment_refund_inbox.sql` adds refund references and a provider-refund facts
table. Existing refund receipts are reopened conservatively: legacy rows without
a reference need their exact original authenticated webhook replay to recover
association. A local decision cannot clear uncertain refund evidence. Inspect and
plan that replay before deploying into an environment with historical refunds.
Pending associated receipts are reconciled in finite batches; repeated calls drain
larger terminal queues without deleting receipt history. Verified processed amounts
and the existing ledger remain monotone and replay-safe.

Runtime grants allow only `refund_id` association on payment receipts and
`status,updated_at` updates on provider-refund facts. Identity and amount remain
immutable after insertion. The startup role guard and real-role integration tests
check these restrictions and reject excessive grants. Source permission coverage
classifies all 112 migration tables. Run permission provisioning after migrations.

Provider semantics were checked against the primary Razorpay documentation:
https://razorpay.com/docs/webhooks/refunds and
https://razorpay.com/docs/payments/refunds/faqs. Local provider fixtures are not
merchant transactions or LIVE lifecycle qualification.

## Verification record and limits

The initial candidate's backend, native-language scenarios and security jobs
passed. Its frontend job exposed one phone-only failure in the new standalone
error-layout fixture: without viewport metadata, Chromium used a 980px layout.
The fixture now declares the same mobile viewport as the application. The final
expanded revision must rerun the complete workflow before merge; Docker startup
was skipped behind that earlier frontend failure.

Local focused Node regressions and source-contract checks run on Node 24.22.1.
The attempted full local suite could not pass without React, Vite, esbuild,
Miniflare, built assets and other application dependencies. This environment also
lacks Docker, Maven and PostgreSQL; Java parser execution alone does not substitute
for HTTP/database acceptance. Final CI results must identify the tested revision
and include frontend types/build, full Node tests, PostgreSQL integration,
desktop/phone/tablet journeys, connected Docker startup, dependency review,
semantic security and container scans.

The design suite records additional throttled browser lab samples for three
workspace shells. LCP/CLS and an input-to-paint proxy are evidence for that CI
profile; they are not field INP, production traffic results or a measured
before/after speed improvement. No speculative caching or pool changes were made.

KVM isolation qualification, an actual seven-day soak, launch-environment load,
production infrastructure, LIVE payments, encrypted production restore/rollback,
real screen-reader/user acceptance and named operational sign-off remain separate
production gates. The private local environment file is ignored and excluded from
the PR. No secrets or live financial actions are part of this change.
