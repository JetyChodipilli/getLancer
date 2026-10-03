# Maintenance operations

The V4 first milestone implements monthly care for completed work. Hosted demos remain later work. Billing is off by default; the local `/preview/maintenance` exercise moves no money and creates no mandate.

## Owner setup and live acceptance

Keep `MAINTENANCE_ENABLED=false` until commercial policies, Razorpay Subscriptions, seller KYC/Route and routing of subscription-generated captured payments have been confirmed for this merchant. Existing PAYMENTS_ENABLED / RAZORPAY_ROUTE_APPROVED / policy approval and original-mode credentials still apply. Then configure RAZORPAY_SUBSCRIPTIONS_APPROVED and a separate RAZORPAY_MAINTENANCE_WEBHOOK_SECRET of at least 16 characters for `/api/v1/maintenance/razorpay/webhook`; subscribe to invoice/payment/subscription/refund/dispute/transfer events in the matching mode. Keep secrets outside frontend variables and logs. No code or CI test establishes this merchant's live approval.

Before live activation, exercise signed checkout, invoice paging, complete dispute discovery, captured-payment Route, cancellation, refunds and webhook retries on the actual approved merchant. Missing/incomplete facts hold new support requests. Test-mode authorizations never provide real support. Monitor webhook delivery and pending inbox/financial attention; provider retries are finite and disabled webhooks need operator recovery.

## Attention and recovery

Use Workspace → Moderation → Maintenance billing & support operations with an administrator MFA session. Recover with the original provider mode and credentials. The dashboard exposes the frozen account, local reservation, provider IDs and local invoice-period IDs. A written evidence reason is mandatory.

- Unknown plan: locate its maintenance_attempt and terms_hash notes, verify monthly/1/INR/exact amount, then bind the matching plan. Recovery can create the independently reserved subscription only with collection approval and no cancellation intent. A late original plan reply cannot reset that reservation.
- Unknown subscription: locate the exact plan, quantity 1, total_count and correlation notes. Bind once, then reconcile. Do not issue another non-idempotent POST after a lost reply.
- Unknown invoice routing: payment-linked transfers do not support provider idempotency. Fetch/find exactly one transfer with the original source payment, frozen recipient, amount, currency and maintenance_route/invoice notes. Bind using the local period ID. An empty lookup does not prove an ambiguous POST failed. Resolve externally with Razorpay support if no matching transfer can be established; never create another blind transfer.
- Cancellation intent commits before provider reads. If creation is unbound, late binding must fulfill cancellation. A confirmed no-subscription cancellation has no recurring debit obligation. An attempted uncertain cancellation is not repeated; use the provider dashboard/support to stop it, then reconcile the exact terminal state. A failed preflight GET has not issued a cancel POST and may retry its first attempt.
- Local holds pause support. Releasing one cannot clear provider refunds, pending refunds, deducted disputes, reversal facts, incomplete discovery or uncertain routing. A won dispute with a deduction remains held.

A cancellation stops future billing without an automatic refund/proration. Paid-period access depends on current authority, original engagement validity and current financial facts, independently of subscription status. New support and existing request transitions refresh financial authority. Expiry alone does not strand an existing request; quota never returns when one is cancelled/resolved.

## Privacy, retention and recovery

Self-service closure blocks unresolved recurring obligations for personal/payer and represented owner accounts. Definitively rejected creation with no subscription does not block closure. Current delegated payer records are retained even if membership changes; an MFA operator can stop/reconcile billing when the original payer loses workspace access. Financial snapshots, offer consents, invoice periods, individual refunds/disputes, consumed requests, signed event identity, append-only ledger and audits are retained and scoped in account export. No raw webhook bodies, keys or other tenants' requests are exported.

Include all nine maintenance tables in backups. The guarded encrypted CI restore checks table counts plus a retained bound test period, a cancelled request that still consumes quota and its capture ledger entry. The synthetic prior-live delivery and test-period data are CI fixtures only, not real money evidence or production seeds.

## Verification boundaries

Provider integration tests use the concrete bounded HTTP client against test-source localhost servers and a disposable PostgreSQL schema. Browser tests check separately labelled frontend samples at 375/768/1440 widths and real API consent/disabled collection/current-session behavior. CI financial fixtures are labelled and guarded. Actual live mandate, collection, routing and settlement acceptance remains owner setup work.
