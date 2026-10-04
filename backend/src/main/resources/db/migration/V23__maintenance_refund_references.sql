ALTER TABLE maintenance_webhook_events ADD COLUMN refund_id varchar(40)
 CHECK(refund_id IS NULL OR refund_id ~ '^rfnd_[A-Za-z0-9]{6,32}$');
ALTER TABLE maintenance_subscriptions ADD COLUMN last_reconcile_attempt_at timestamptz;
CREATE INDEX maintenance_events_refund ON maintenance_webhook_events(payment_id,refund_id) WHERE refund_id IS NOT NULL;
UPDATE maintenance_subscriptions s SET reconciled_at=null,
 attention_reason='A legacy refund event requires replay of its original signed payload to recover the refund reference.'
 WHERE EXISTS(SELECT 1 FROM maintenance_webhook_events w WHERE w.event_kind LIKE 'refund.%'
 AND (w.subscription_id=s.provider_subscription_id OR w.payment_id IN (SELECT payment_id FROM maintenance_periods p WHERE p.subscription_id=s.id)));

CREATE OR REPLACE FUNCTION maintenance_retained_event() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF TG_OP='DELETE' OR (NEW.event_id,NEW.payload_hash,NEW.event_kind,NEW.subscription_id,NEW.payment_id,NEW.dispute_id,NEW.received_at) IS DISTINCT FROM (OLD.event_id,OLD.payload_hash,OLD.event_kind,OLD.subscription_id,OLD.payment_id,OLD.dispute_id,OLD.received_at) THEN RAISE EXCEPTION 'Signed event identity and evidence are retained'; END IF;
 -- A matching signed replay may fill the reference the pre-V23 receiver discarded, once.
 IF NEW.refund_id IS DISTINCT FROM OLD.refund_id AND NOT(OLD.refund_id IS NULL AND NEW.refund_id IS NOT NULL) THEN RAISE EXCEPTION 'Signed refund identity is retained after enrichment'; END IF;
 IF NEW.attempts<OLD.attempts OR (OLD.processed_at IS NOT NULL AND NEW.processed_at IS DISTINCT FROM OLD.processed_at) THEN RAISE EXCEPTION 'Processed event evidence cannot be reset'; END IF;
 RETURN NEW; END $$;
