# PR56 second audit: repairs and verification boundaries

This pass starts from `11fab4f675883050c59a69d0ffde567e1006d195`, after the first upload/form audit. The plan was reviewed before implementation in sequential product, design, engineering and developer-experience passes. The six domain audits then examined current source, reproduced supported failures, applied bounded repairs and reviewed the resulting behavior. PR56 remains a draft and must remain unmerged for this round.

## Additional repairs

| Area | Reproduced failure | Resulting behavior |
| --- | --- | --- |
| Client requests | Old list/mutation/history responses could survive an identity or verification change; continuation could start before page zero or retain an obsolete cursor after a mutation. | Requests are scoped to the current account and verification context. Old continuations cannot publish into a new context. Continuation starts from the confirmed first page and resets when that page is replaced. Denials clear private rows. |
| Client request recovery | A successful mutation followed by a failed list refresh could leave ambiguous feedback and an actionable stale context. | Acceptance and refresh failure are distinguished. A read-only retry restores the current requests before another write; definite rejection remains visible. |
| Confirmation links | A transient context lookup failure stranded a page after the bearer token had correctly been removed from its URL. | Retry uses the token retained in memory, keeps it out of the URL and ignores an obsolete link's response. Expired/invalid links do not offer the transient retry. |
| Component saves | Alpha's delayed save acknowledgement could mark newly selected beta saved; denied deletion retained prior account rows. | Save state is tied to the selected component/mode. Current 401/403 clears private saves and offers sign-in. A transient failure retains recoverable rows and offers read-only reload. |
| Private project preview | A previous resource's content/error could remain or arrive after selecting another project. | Resource replacement resets the preview and invalidates the previous read's continuation. |
| Template checkout | Consent, order state and provider callbacks could outlive the selected release/terms. Delayed or duplicate SDK callbacks could start obsolete verification. | Consent and idempotency belong to the selected release and frozen terms. Old orders cannot open checkout for a new selection; obsolete/dismissed callbacks are ignored, verification is single-flight, and closing during verification does not overwrite its result. Existing backend verification remains authoritative. |
| Proof images | Publication, grant, session, media deletion or storage-key changes during S3 I/O were not rechecked before returning fetched bytes. | The current media row, access and exact selected storage key are checked again after I/O. Database locks are not held across the S3 read. |
| Hosting operations | Administrator role/MFA/session revocation during publisher I/O could still be followed by a private detail response. | Verified publisher facts remain committed; returning the private projection requires fresh actor/access checks and locks. |
| Delivery and maintenance operations | An accepted operator write could show misleading success or lose its accepted-write context after a failed/obsolete refresh. | Feedback distinguishes acceptance from unavailable current state, provides read-only recovery and ignores a closed panel's late refresh. |
| Team interest and applications | An accepted alpha submission could announce success or set applied state while beta was displayed. | Acknowledgements apply only to the team/request generation that initiated the write. The accepted original write is preserved. |
| Shared Picker | A legitimate category or technology named `ALL` collided with the empty-selection sentinel. | Internal option encoding distinguishes every nonempty business value from clearing the control, including prefix-like names. Display and API values are preserved. |

These changes introduce no database migration, dependency, provider activation, new business feature or replacement visual system. V51 refund holds and immutable provider/source facts remain enforced.

## Review and executable coverage

New actual-production-routine checks are in `tests/round2-*.test.mjs`. They exercise delayed/current responses, denials, rejected/accepted/unknown outcomes and positive controls at the relevant state/transport boundaries. The runtime checks compile actual extracted Java methods with in-process boundaries. These are executable behavior checks, not React, Spring or SQL acceptance results.

Five new browser files exercise the actual client-request, confirmation, saved/private/checkout, team, operator and Picker components. Their fixtures use production styles, viewport metadata, valid actors/input and controlled response timing. The Picker additionally checks keyboard/focus and 320px/enlarged text. Fourteen real PostgreSQL tests in `Round2RuntimeStorageIntegrationTest` cover public/private/granted image reads, revocation/deletion/key replacement and hosting projection authority while preserving provider facts. External storage/publisher I/O is deterministic in-process test I/O, not a real provider qualification.

The parent independently reviewed the repairs and reruns the focused checks. A second reviewer inspected the security-sensitive runtime changes and payment-adjacent checkout/operator changes. Review caught and corrected loading-state invalidation, obsolete callback verification, duplicate SDK delivery and an initial browser fixture CSP/hydration assumption before integration.

CI adds an early smoke run of the new browser files after Chromium installation, followed by the entire existing responsive suite. All eight original jobs, type/build checks, connected journeys, security scanners and disposable restore remain required. Smoke and full-suite totals must not be added together. The SAST assessment retains its existing findings, required security tests and expiry; only exact reviewed source/test/workflow input hashes are rebound.

This checked-in report describes the implementation and its verification contract. Exact final commit/tree, immutable workflow URLs, observed suite counts and qualification status are recorded in PR56's description after execution. A previous green commit is not evidence for this commit. Local Node checks and Java grammar parsing do not count as local browser/type/build/PostgreSQL execution; those toolchains are absent in this workspace.

## Examined hypotheses that did not justify patches

- Team/template sitemap endpoints already return `totalPages` through the real pagination helper. No alleged pagination-request optimization was made.
- Source/static archives are capped at 5MiB; the 6MiB proxy cap allows normal multipart overhead. Archive trust limits were retained.
- Shared API error/header/multipart handling and the proxy's response-header boundary passed their existing executable controls. No speculative shared transport rewrite was added.
- Authentication/inquiry checks, invitation expiry after lock waits, backend payment/maintenance authority and durable reservations were inspected without supporting a further backend change in those domains.
- An unknown operator POST outcome does not automatically replay a write. The examined repeated backend decisions are constrained by current state, immutable identity and durable reservation/idempotency checks. This pass does not claim a general unknown-POST UI redesign or an exhaustive proof over every financial path.
- No measured new performance bottleneck was established. No speculative cache, index, pool, dependency or memoization change was introduced; no speed-improvement or field-INP claim is made.

## Limits

This is a bounded additional audit, not a claim that all unknown bugs are absent. Live financial/provider acceptance, real KVM isolation, production infrastructure, actual seven-day soak/load, production restore/rollback and human operational acceptance remain separate gates. Passing these checks does not authorize a merge or deployment.
