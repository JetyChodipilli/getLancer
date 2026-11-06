# Commercial financial inboxes

Source-commerce licenses and all three publishing-slot pools retain authenticated webhook evidence before contacting the payment provider. A provider outage, malformed provider response, or unfinished refund leaves its receipt pending. A pending adverse receipt holds source entitlement and paid slot capacity even when an aggregate payment response still reports zero refunded funds. Local administrator resolution cannot override the source-commerce hold.

A refund receipt is resolved only after its exact refund ID, payment membership, currency, amount and terminal provider status are verified. A processed refund increases the retained refunded total even if the payment aggregate lags. A failed refund can release its pending hold; a pending refund cannot. Confirmed refunded totals never decrease. Independent slot disputes retain both status and deducted minor units separately. Capacity requires every retained dispute to be won with an explicitly observed zero deduction; a positive or unknown deduction remains held even if aggregate payment refunds are zero. Winning one dispute does not remove another dispute's hold. Legacy dispute backfill retains an unknown deduction until that exact provider dispute is reconciled; it never invents a zero from the old status.

Provider calls occur outside purchase/owner locks. Each reconciliation claims a generation before those reads. An older overlapping observation cannot overwrite a newer reconciliation or resolve its receipts; it returns the current stored summary. A pass reads at most eight distinct refund IDs and eight dispute IDs. Unobserved or later-arriving receipts stay pending for subsequent reconciliation.

## Deployment

Quiesce old application writers and webhook consumers before V53, apply the migration, then run `ops/database/10_permissions.sql` with the migration operator before starting the new runtime. Do not run mixed old/new financial writers: old slot writers do not populate the new receipt metadata, and old commerce writers do not enforce the exact-refund lifecycle. The runtime role guard rejects missing or excessive financial evidence grants.

Rehearse the migration and PostgreSQL regressions on a disposable restored copy before a hosted rollout. Source changes and CI do not certify a live merchant account, webhook delivery history, applied grants or production reconciliation.

## Existing receipts

- Commerce already retained event kinds and payment/order references. V53 reopens known historical refund receipts. If a receipt lacks a refund ID, ordinary reconciliation reports `REFUND_RECONCILIATION_REQUIRED`; recover it through the provider's original authenticated delivery with the same event ID and byte-identical payload. A hash-conflicting replay is rejected. An aggregate payment total or local administrator choice cannot supply the missing identity. If the original authenticated delivery cannot be recovered, that purchase remains held and needs separately reviewed provider-evidence recovery; this patch does not invent an ID or expose a bypass.
- Old slot receipts retained only a payload hash after the old processing transaction succeeded. V53 preserves that historical completion time and leaves the unknown kind/references empty. It does not freeze every seller's capacity merely because those old references are unavailable. Existing stored refund/dispute facts remain in force. Matching authenticated redelivery can fill a legacy receipt's metadata once and reopen the new adverse-event verification. This preservation is not proof that every historical refund was individually resolved; reconcile merchant history independently when qualifying a hosted rollout.

## Recovery behavior

Retry the original pending webhook through provider delivery, or use the existing owner/operator purchase reconciliation action. The hash-bound receipt remains durable across failures. Newly associated payment/order references are checked rather than replaced. Source-commerce attention lists and purchase summaries expose held download availability; slot history and capacity use the same paid-capacity predicate.

Pending slot evidence can archive excess active publications under the existing capacity-trimming policy. A subsequently failed refund restores capacity but does not automatically republish those archived items. Their immutable reviewed source and prior buyer rights remain retained; the owner can use the existing activation flow when eligible.

Use synthetic identities and a mocked/local provider for regressions. Do not put webhook signing keys, credentials or real private package contents into test output or recovery reports.
