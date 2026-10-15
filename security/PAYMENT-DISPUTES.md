# Payment dispute inbox and recovery

Milestone payments and source commerce use separate signing secrets and receipt tables. Both now commit authenticated event hashes and payment/dispute references before provider HTTP. Order association and reconciliation happen afterwards, without retaining payment, engagement or purchase row locks across those calls.

A provider failure leaves durable pending evidence. A pending dispute blocks live milestone completion, source entitlement and local operator RESUME. An unknown order can later be bound through the existing MFA-protected operator endpoint; reconciliation discovers pending references by order or payment ID. Capture/refund ledger uniqueness, frozen parties, price, currency and mode checks remain in force.

Processed duplicates return `duplicate=true`. Pending duplicates retry reconciliation. A different raw body using the same event ID conflicts. Only receipt IDs observed before a dispute lookup are consumed with its verified result: a later notification remains pending. Pending dispute IDs receive priority in bounded reconciliation batches (100 distinct IDs and 200 receipt IDs per dispute). Repeat reconciliation to drain larger backlogs; monitor their age and provider failures.

## Before deploying V36–V38

Back up the database, inspect existing dispute receipts and rerun `ops/database/10_permissions.sql` after migrations. Runtime UPDATE is restricted to the four reconciliation metadata columns; event IDs, payload hashes, event kinds and received timestamps remain immutable to that role. Startup guard and real-role tests reject broader receipt mutation. Older receipts stored a hash and event kind, with no recoverable order/payment/dispute association. These migrations retain those receipts as pending. **Unmapped historical receipts conservatively hold completion or source access across their respective subsystem until recovered.** Budget that operational work before enabling public traffic.

To recover a historical receipt, arrange an authenticated replay of its exact original event ID and raw body through the corresponding existing webhook endpoint, using the correct subsystem signature. The stored hash must match. Matching replay fills missing references, preserves the pending hold through provider failures and reconciles current provider facts. A verified win releases that dispute's hold; another pending, open, lost, refunded or local dispute still blocks access as applicable. Changed-body replay cannot clear a historical receipt.

If the original event cannot be recovered, its association cannot be reconstructed from the retained hash alone. Keep the hold and use a reviewed operator process with independent provider evidence and an audit record; do not delete receipts, mark every pending row processed or bypass the entitlement/completion checks. No production receipts were altered by repository publication.

For restricted operator monitoring, inspect `event_id`, `received_at`, `order_id`, `payment_id`, `dispute_id` and `processed_at` in `payment_webhook_events` and `commerce_webhook_events`. Retain pending dispute evidence until resolved. Do not expose raw webhook payloads, signing secrets or customer information in public logs.

## Verification

Regressions exercise early dispute delivery before uncertain-order binding, provider failure at the first payment lookup and at dispute lookup, pending/processed/conflicting replay, matching historical replay, a newer receipt arriving during a verified-win lookup, live completion/source entitlement, operator hold enforcement and independent row-lock acquisition during delayed provider HTTP. Local fixtures use loopback HTTP and synthetic data; they are not live merchant acceptance tests.
