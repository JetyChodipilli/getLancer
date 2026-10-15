CREATE INDEX payment_pending_dispute_payment ON payment_webhook_events(payment_id,dispute_id)
WHERE processed_at IS NULL AND event_kind LIKE 'payment.dispute.%';
CREATE INDEX commerce_pending_dispute_payment ON commerce_webhook_events(payment_id,dispute_id)
WHERE processed_at IS NULL AND event_kind LIKE 'payment.dispute.%';
