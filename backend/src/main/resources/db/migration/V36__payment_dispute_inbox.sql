-- Retain verified webhook associations even when an uncertain order is not bound yet.
ALTER TABLE payment_webhook_events ADD COLUMN order_id varchar(40);
ALTER TABLE payment_webhook_events ADD COLUMN payment_id varchar(40);
ALTER TABLE payment_webhook_events ADD COLUMN dispute_id varchar(40);
ALTER TABLE payment_webhook_events ADD COLUMN processed_at timestamptz;
UPDATE payment_webhook_events SET processed_at=received_at
WHERE event_kind NOT LIKE 'payment.dispute.%';
-- Old dispute receipts have no recoverable association. Completion stays held until
-- an operator verifies their provider references and reconciles or resolves them.
CREATE INDEX payment_pending_disputes ON payment_webhook_events(order_id,dispute_id)
WHERE processed_at IS NULL AND event_kind LIKE 'payment.dispute.%';
