-- Refund creation is not terminal provider confirmation. Retain its reference for
-- bounded reconciliation before delivery completion can become eligible again.
ALTER TABLE payment_webhook_events ADD COLUMN refund_id varchar(40);
UPDATE payment_webhook_events SET processed_at=NULL WHERE event_kind LIKE 'refund.%';
-- Legacy refund receipts have no persisted reference. Replaying their original
-- authenticated payload recovers it; a local decision cannot clear the hold.
CREATE INDEX payment_pending_refund_order ON payment_webhook_events(order_id,refund_id)
WHERE processed_at IS NULL AND event_kind LIKE 'refund.%';
CREATE INDEX payment_pending_refund_payment ON payment_webhook_events(payment_id,refund_id)
WHERE processed_at IS NULL AND event_kind LIKE 'refund.%';
CREATE TABLE payment_provider_refunds (
  id varchar(40) PRIMARY KEY,
  attempt_id uuid NOT NULL REFERENCES payment_attempts(id),
  amount_minor bigint NOT NULL CHECK(amount_minor BETWEEN 1 AND 1000000000),
  status varchar(16) NOT NULL CHECK(status IN ('pending','processed','failed')),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX payment_provider_refunds_attempt ON payment_provider_refunds(attempt_id);
ALTER TABLE payment_provider_refunds ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON payment_provider_refunds FROM PUBLIC;
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='anon') THEN
    REVOKE ALL ON payment_provider_refunds FROM anon;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN
    REVOKE ALL ON payment_provider_refunds FROM authenticated;
  END IF;
END $$;
