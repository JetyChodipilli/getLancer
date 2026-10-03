# Maintenance operations

V4 implements monthly care for completed work and [reviewed static frontend demos](V4_HOSTING_OPERATIONS.md). Billing is off by default; the local `/preview/maintenance` exercise moves no money and creates no mandate.

## Owner setup and live acceptance

Keep `MAINTENANCE_ENABLED=false` until commercial policies, Razorpay Subscriptions, seller KYC/Route and routing of subscription-generated captured payments have been confirmed for this merchant. Existing PAYMENTS_ENABLED / RAZORPAY_ROUTE_APPROVED / policy approval and original-mode credentials still apply. Then configure RAZORPAY_SUBSCRIPTIONS_APPROVED and a separate RAZORPAY_MAINTENANCE_WEBHOOK_SECRET of at least 16 characters for `/api/v1/maintenance/razorpay/webhook`; subscribe to invoice/payment/subscription/refund/dispute/transfer events in the matching mode. Keep secrets outside frontend variables and logs. No code or CI test establishes this merchant's live approval.

Before live activation, exercise signed checkout, invoice paging, complete dispute discovery, captured-payment Route, cancellation, refunds and webhook retries on the actual approved merchant. Missing/incomplete facts hold new support requests. Test-mode authorizations never provide real support. Monitor webhook delivery and pending inbox/financial attention; provider retries are finite and disabled webhooks need operator recovery.

## Attention and recovery

Use Workspace → Moderation → Maintenance billing & support operations with an administrator MFA session. Recover with the original provider mode and credentials. The dashboard exposes the frozen account, local reservation, provider IDs and local invoice-period IDs. A written evidence reason is mandatory.

- Unknown plan: locate its maintenance_attempt and terms_hash notes, verify monthly/1/INR/exact amount, then bind the matching plan. Recovery can create the independently reserved subscription only with collection approval and no cancellation intent. A late original plan reply cannot reset that reservation.
- Confirmed plan with a proven unattempted subscription: use **Resume unattempted subscription** after readiness returns. Recovery verifies the original provider plan, current consent/authority, frozen amount/mode/account and collection eligibility, then reserves the first subscription attempt under the existing locks. CREATING, UNKNOWN and cancelled attempts cannot use this action.
- Unknown subscription: locate the exact plan, quantity 1, total_count and correlation notes. Bind once, then reconcile. Do not issue another non-idempotent POST after a lost reply.
- Unknown invoice routing: payment-linked transfers do not support provider idempotency. Fetch/find exactly one transfer with the original source payment, frozen recipient, amount, currency and maintenance_route/invoice notes. Bind using the local period ID. An empty lookup does not prove an ambiguous POST failed. Resolve externally with Razorpay support if no matching transfer can be established; never create another blind transfer.
- Cancellation intent commits before provider reads. If creation is unbound, late binding must fulfill cancellation. A confirmed no-subscription cancellation has no recurring debit obligation. An attempted uncertain cancellation is not repeated; use the provider dashboard/support to stop it, then reconcile the exact terminal state. A failed preflight GET has not issued a cancel POST and may retry its first attempt.
- Known refund events retain their exact refund identity. When collection reads have not caught up, reconciliation verifies the refund by its exact provider ID, payment, currency and amount. Missing or inconsistent facts leave the signed event pending and service held. Webhook status alone never grants access. For inbox rows received before refund references were retained, replay the original signed event in its original mode with the same event ID and unchanged body. Its stored hash and references must match before the missing refund ID can be filled once. The enriched reference stays immutable; a changed payload is rejected. Legacy rows hold service and new routing until their references are verified.
- Automatic cancellation recovery and financial refresh run independently. Every scheduled attempt rotates its queue position, including failed preflight and unresolved cancellation states, so one failure cannot block later subscriptions.
- Local holds pause support. Releasing one cannot clear provider refunds, pending refunds, deducted disputes, reversal facts, incomplete discovery or uncertain routing. A won dispute with a deduction remains held.

A cancellation stops future billing without an automatic refund/proration. Paid-period access depends on current authority, original engagement validity and current financial facts, independently of subscription status. New support and existing request transitions refresh financial authority. Expiry alone does not strand an existing request; quota never returns when one is cancelled/resolved.

## Privacy, retention and recovery

Self-service closure blocks unresolved recurring obligations for personal/payer and represented owner accounts. Definitively rejected creation with no subscription does not block closure. Current delegated payer records are retained even if membership changes; an MFA operator can stop/reconcile billing when the original payer loses workspace access. Financial snapshots, offer consents, invoice periods, individual refunds/disputes, consumed requests, signed event identity, append-only ledger and audits are retained and scoped in account export. Exports include individually scoped dispute deductions and signed event IDs/hashes/timestamps, without raw webhook bodies, unverified foreign provider references, keys or other tenants' requests.

Include all nine maintenance tables in backups. The guarded encrypted CI restore checks table counts plus a retained bound test period, a cancelled request that still consumes quota and its capture ledger entry. The synthetic prior-live delivery and test-period data are CI fixtures only, not real money evidence or production seeds.

## Verification boundaries

Provider integration tests use the concrete bounded HTTP client against test-source localhost servers and a disposable PostgreSQL schema. Browser tests check separately labelled frontend samples at 375/768/1440 widths and real API consent/disabled collection/current-session behavior. CI financial fixtures are labelled and guarded. Actual live mandate, collection, routing and settlement acceptance remains owner setup work.
