# V4.0 — maintenance and support, milestone 1

V4 starts with recurring maintenance for a completed delivery engagement. Hosted demos and arbitrary source execution are a later milestone. This release preserves Java/PostgreSQL as the authority and publishes only an isolated frontend exercise.

## Agreement and authority

A current seller drafts monthly INR terms: title (3–100), scope (20–4000), exclusions/terms (40–8000), price (100–1,000,000,000 paise), 1–50 requests, 1–720 elapsed response hours, and 1–12 billing cycles. Sending requires seller consent; accepting requires separate buyer consent to the immutable digest. Acceptance does not authorize recurring debit. Both parties must remain eligible under delivery business/team rules; the original engagement must be completed, fully paid live, unrefunded and free of disputes or financial attention. Historical consent does not grant current account permissions.

Offer lifecycle: DRAFT → OFFERED → ACCEPTED; seller may WITHDRAW before acceptance, buyer may REJECT an offered revision. Sent fields, consent actors/times and agreement digest cannot be rewritten. New terms require a new revision and consent. Only one outstanding offer and one unresolved/running enrollment per engagement; prepaid coverage prevents overlapping re-enrollment.

## Provider boundary

Collection is disabled by default. Existing policies, Razorpay Route and credentials plus explicit maintenance/subscriptions approval are required. Merchant approval for routing subscription-generated captured payments must be verified before enabling live mode. No simulated billing exists in production Java.

Plan creation and subscription creation have separate persisted reservations and correlation notes. Requests commit before HTTP; timeout, crash, malformed success or mismatched facts become UNKNOWN. No automatic repeat of an ambiguous POST. MFA operators bind a matching plan/subscription after verifying provider facts. Frozen amount, INR, quantity 1, monthly interval 1, cycle count and linked account are authoritative.

Checkout signature is HMAC(payment_id + "|" + stored_subscription_id), not the order-payment signature. Checkout and active subscription status never establish support access. Refresh fetches the exact subscription, plan and complete bounded invoice collection. A period requires a paid, fully settled invoice with exact amount/currency, one monthly plan item, no addon/partial charge, authoritative billing_start/end and an exact captured payment/order. Refunds and disputes are fully paged and retained; pending refunds, unresolved/deducted disputes, stale/incomplete reconciliation and routing uncertainty hold service. Test-mode payments never grant production support entitlement.

Each captured invoice has one independently reserved payment-linked Route transfer. That endpoint is not provider-idempotent. UNKNOWN/CREATING blocks retries; an MFA operator can bind exactly one matching transfer with frozen account, source payment, amount, currency and notes. Transfer creation does not assert bank settlement. Financial snapshots, IDs, monotonic refunds, ledger and audits are retained.

Raw webhook bytes are bounded and signed before JSON parsing. Event ID plus SHA-256 deduplicates exact deliveries and rejects conflicting payloads. A durable inbox retains unmatched/pre-binding events for scheduled reconciliation. Events trigger authoritative fetching; they do not overwrite payment authority. Reconciliation failure removes fresh-service availability. Scheduled work is bounded, with explicit refresh and operator recovery.

Buyer cancellation persists its own obligation before requesting immediate provider cancellation. A lost response is reconciled by GET before any retry. Only a matching terminal provider status confirms stopped billing. Eligible, undisputed prepaid access continues until the invoice's exclusive end. Cancellation remains possible when the seller is ineligible. Self-service account deletion is blocked while a personal payer/delivery-party or represented-owner subscription has an unconfirmed billing obligation; the account must cancel it or obtain operator recovery first.

## Support and interface

A new buyer request needs current live paid-period entitlement and fresh financial reconciliation. Quota is locked per period; every request, including cancelled/resolved requests, consumes one slot. The due time is creation + agreed elapsed hours; no business-hours guarantee is implied. Existing requests remain actionable after expiry, subject to current authority and holds.

OPEN → IN_PROGRESS → SUBMITTED → RESOLVED; a buyer may request revision of SUBMITTED, then seller restarts it. Buyer may cancel OPEN or REVISION_REQUESTED. Seller delivery note is required; optional URLs must be HTTPS. Every transition is audited and checked under locks.

`/workspace/maintenance` uses the real Java API; `/preview/maintenance` is explicitly local sample data and redirects to connected workspace when a backend is configured. The sample actor switch and simulated period never contact Razorpay or the API. Current-plan cards, quota Progress, Requests/Billing/Terms tabs, responsive lists, explicit consent and actionable failure states reuse installed shadcn primitives and Spectral Studio. Operator MFA controls stay in AdminTools.

## API and release acceptance

Authenticated routes: GET maintenance/config, maintenance/sources, maintenance, maintenance/{id}; POST maintenance, maintenance/{id}/{send|accept|reject|withdraw}, maintenance/{id}/billing, maintenance/{id}/billing/confirm, maintenance/{id}/billing/refresh, maintenance/{id}/billing/cancel, maintenance/{id}/requests and maintenance/{id}/requests/{requestId}/{action}. MFA routes expose attention, bind and hold/reconcile/cancel. Exact POST maintenance/razorpay/webhook alone bypasses browser CSRF headers.

Acceptance includes negative current-party/consent checks, immutable SQL protection, concurrent quota/idempotency, provider mismatch/unknown recovery, live-vs-test entitlement, refunds/disputes/cancellation, event deduplication, scoped exports, retained restore data, connected API journeys and desktop/tablet/phone UI. CI must pass at the final PR head. The preview is not evidence of merchant approval or a real live payment. This milestone is complete only when those implementation and verification gates pass.
