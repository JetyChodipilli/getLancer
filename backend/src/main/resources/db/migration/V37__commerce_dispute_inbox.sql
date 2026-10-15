ALTER TABLE commerce_webhook_events ADD COLUMN order_id varchar(40);
ALTER TABLE commerce_webhook_events ADD COLUMN payment_id varchar(40);
ALTER TABLE commerce_webhook_events ADD COLUMN dispute_id varchar(40);
ALTER TABLE commerce_webhook_events ADD COLUMN processed_at timestamptz;
UPDATE commerce_webhook_events SET processed_at=received_at
WHERE event_kind NOT LIKE 'payment.dispute.%';
-- Legacy disputes without provider references require operator reconciliation;
-- new source entitlements stay held while any such ambiguity remains unresolved.
CREATE INDEX commerce_pending_disputes ON commerce_webhook_events(order_id,dispute_id)
WHERE processed_at IS NULL AND event_kind LIKE 'payment.dispute.%';
