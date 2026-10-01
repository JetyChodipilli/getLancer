# V3 commercial workflows — implementation contract

Revision 2 · 30 September 2026. V3 extends the existing Java/PostgreSQL modular monolith. Razorpay is the only gateway; its checkout offers methods supported by the merchant account and customer's device. No payment SDK is loaded until a client chooses to pay. Preview data exists only in the frontend. Connected failures never fall back to preview data.

## Commercial model

A qualified inquiry or an open business request can become an engagement with its eligible builder or team. A seller drafts a proposal with scope, commercial terms and INR milestones, explicitly consents to its terms when sending it, and the buyer explicitly accepts the immutable proposal. The accepted agreement preserves both consents and a digest of the scope, terms and milestones. This is a recorded commercial agreement, not a claim of certified electronic signature or legal advice.

The seller starts and submits a milestone. The buyer requests revision or accepts the deliverable. Payment follows acceptance. There is no advance-payment or escrow balance. Completion requires accepted, provider-confirmed paid milestones and both parties' completion acknowledgement. Either party may raise a dispute; it blocks new checkout and completion until an MFA-authenticated administrator records a resolution. Refunds and transfer reversals are operated through the provider dashboard, with the application reconciling authoritative provider payment totals. Never imply that recording a dispute refunds money.

Initial currency is INR, stored in integer paise, with a product minimum of ₹1.00 per milestone and total maximum of ₹1,00,00,000; reject decimals, negatives, unsafe bounds and mismatched totals. No commission is introduced. Before enabling collection, the operator must approve the commercial policies, activate Razorpay Route and onboard each seller as a linked account. Route transfers are attached to the order so the marketplace never represents its own wallet or escrow. Provider processing, transfer and bank settlement are separate statuses. A captured payment does not prove seller settlement.

## Shared backend contract

New packages `com.getlancer.delivery` and `com.getlancer.payments` own their tables. Controllers delegate; repositories own SQL and services own transactions. No speculative multi-provider interface or new runtime SDK.

DeliveryRepository exposes these concrete methods to PaymentsService:

- `Map<String,Object> lockEngagement(UUID engagement, HttpServletRequest request, String side)` — same current verified party check and engagement lock; list authorization also works before milestones exist.
- `Map<String,Object> lockMilestone(UUID milestone, HttpServletRequest request, String side)` — current verified member access; side `BUYER`, `SELLER` or `ANY`; row-locks the engagement then milestone. Returns `id`, `engagementId`, `amountMinor` (Number), `currency`, `status`, `engagementStatus`, `buyerUserId`, `businessId`, `builderUserId`, `teamId` (nullable IDs).
- `Map<String,Object> lockMilestoneSystem(UUID milestone)` — same row locks and shape, for authenticated provider processing only.
- `void event(UUID engagement, UUID actor, String kind, String detail)` — append activity; null actor for provider events.
- `boolean allPaid(UUID engagement)` is implemented by querying authoritative CAPTURED payment attempts; completion can query the payments table directly from the delivery repository, but never write it.

Migration V14 owns delivery tables. Migration V15 owns payment tables and runs before runtime delivery queries. Payments expose no database object to delivery services.

Delivery JSON uses camelCase. Engagement list/detail fields: `id,title,status,side,sourceInquiryId,businessRequestId,buyerUserId,businessId,builderUserId,teamId,createdAt`; detail adds `proposals,milestones,activity,disputes,agreement`. Proposal fields: `id,revision,status,scope,terms,amountMinor,currency,milestones,sentAt,acceptedAt`; milestones in proposal have `title,description,amountMinor,dueDate` (ISO date). Accepted milestones add `id,status,deliveryNote,deliveryUrl,acceptedAt`. Agreement exposes immutable `proposalId,scope,terms,amountMinor,currency,digest,sellerConsentedAt,buyerConsentedAt`. Payment summary by milestone exposes `id,milestoneId,status,amountMinor,currency,orderId,paymentId,refundedMinor,transferStatus,settlementStatus`; never keys or secrets. Missing rows are empty lists/null.

Routes:

- GET/POST `/api/v1/engagements`; POST body `{inquiryId}` or `{businessRequestId,candidateKind,candidateId}` with exactly one source. Current eligible seller or business buyer may create, with no duplicate source/payee engagement.
- GET `/api/v1/engagements/{id}`
- POST `/api/v1/engagements/{id}/proposals`; draft body `{scope,terms,milestones:[{title,description,amountMinor,dueDate}]}`. A new revision replaces only a draft/withdrawn/rejected offer, never an accepted agreement.
- POST `/api/v1/engagements/{id}/proposals/{proposalId}/{action}`; actions `send` (`{consent:true}`), `withdraw`, `accept` (`{consent:true}`), `reject`.
- POST `/api/v1/engagements/{id}/milestones/{milestoneId}/{action}`; `start`, `submit` (`{deliveryNote,deliveryUrl}`), `request-revision` (`{reason}`), `accept`.
- POST `/api/v1/engagements/{id}/completion`; seller requests then buyer confirms; only when every milestone is accepted and paid.
- POST `/api/v1/engagements/{id}/disputes` (`{reason}`); GET `/api/v1/admin/delivery/disputes`; POST `/api/v1/admin/delivery/disputes/{id}/resolve` (`{resolution:'RESUME'|'CANCEL',reason}`). MFA admin required.
- GET `/api/v1/payments/config` → `{enabled,keyId,mode,reason}`; keyId only when enabled, never secret. Mode `test|live|disabled`.
- GET `/api/v1/engagements/{id}/payments` → list of milestone payment summaries, party access.
- POST `/api/v1/milestones/{id}/payment-order`; `Idempotency-Key` required, random UUID. Returns `{id,orderId,amountMinor,currency,keyId,mode,status}` for Checkout. Server selects frozen amount and payee, no client amount.
- POST `/api/v1/payments/{id}/verify` (`{razorpay_payment_id,razorpay_signature}`); server stored order ID is used for HMAC; fetch payment from Razorpay and verify captured status, amount, currency and order before updating.
- POST `/api/v1/payments/{id}/reconcile`; buyer can fetch authoritative payment/order state after an interrupted checkout. Unknown order creation must never create another order blindly; owner dashboard reconciliation is the explicit recovery path.
- POST `/api/v1/payments/razorpay/webhook`; exact external webhook exception, bounded raw body, HMAC before JSON parsing, event-ID deduplication, no browser session authority.
- GET/POST `/api/v1/admin/payments/accounts`; MFA admin maps exactly one builderUserId or teamId to a real `accountId`, verifying account existence through provider API and requiring `activationConfirmed:true` as explicit operator confirmation of completed KYC and activated Route configuration in the provider dashboard. No bank/PAN credentials stored. GET `/api/v1/admin/payments/attention` lists uncertain orders and disputes; POST `/api/v1/admin/payments/{id}/reconcile` performs MFA operator reconciliation; POST `/api/v1/admin/payments/{id}/bind-order` binds an operator-provided orderId only after provider receipt/amount/currency/payee verification.

## Payment integrity

Payments use a committed CREATING reservation before external order creation and one active reservation per milestone. Stored request keys allow repeat requests without duplicate charges. Network timeouts or ambiguous provider responses become UNKNOWN, with no automatic duplicate create. Route transfer account is frozen with the order. HMAC uses constant-time comparison and raw bytes; the external webhook has no broad origin/CSRF exemption. Duplicate/out-of-order callbacks are harmless; provider fetch verifies current state. Payment ledger entries are append-only and uniquely keyed by attempt/event. Captured and refunded amounts must match the frozen milestone. Authorization alone never marks paid. Provider test-mode captures are visibly labelled and never satisfy real commercial completion. Use a separate disposable database for provider sandbox acceptance; do not mix test and live agreements or seller mappings. Refund reconciliation can only increase confirmed refundedMinor and cannot exceed the captured amount. Any confirmed refund blocks completion until the commercial dispute is resolved; refunded money cannot be counted as fully paid. No live credential, provider sandbox, fake API or sample seller mapping is included in production code.

## Frontend and acceptance

Add Delivery to the existing workspace rail. `/workspace/delivery` uses Java API data exclusively; `/preview/delivery` has explicitly labelled local sample interactions with no gateway requests. The workspace includes source creation, proposal authoring and consent, delivery/revision/acceptance, payment status, disputes and final completion. Existing inquiries and business request/match surfaces link into the same delivery flow. Admin adds disputes, seller account mappings and uncertain order reconciliation. Preserve DESIGN.md, reuse installed Radix/shadcn controls, keyboard labels, 44px controls, reduced motion and mobile layout. Errors clear stale protected data and surface recoverable actions.

Acceptance: meaningful Java/PostgreSQL lifecycle and isolation tests; provider HTTP fixture only in test sources for HMAC, tampering, repeats, concurrency/uncertainty, refund and reconciliation; frontend build/type/unit checks; preview browsers at three viewport sizes; connected real-service delivery journey in CI. Owner acceptance is still required for Route approval, commercial policy/legal review, live credentials, public webhook and an actual provider test/live transaction. Code/CI completion must not be described as completed live payment acceptance.
