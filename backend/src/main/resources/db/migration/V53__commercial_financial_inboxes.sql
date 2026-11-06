-- An aggregate payment refresh cannot resolve an individual refund receipt.
ALTER TABLE template_purchases ADD COLUMN reconciliation_generation bigint NOT NULL DEFAULT 0 CHECK(reconciliation_generation>=0);
ALTER TABLE component_slot_purchases ADD COLUMN reconciliation_generation bigint NOT NULL DEFAULT 0 CHECK(reconciliation_generation>=0);
ALTER TABLE commerce_webhook_events ADD COLUMN refund_id varchar(40);
UPDATE commerce_webhook_events SET processed_at=NULL
  WHERE event_kind IN ('refund.created','refund.processed','refund.failed')
    AND (nullif(btrim(order_id),'') IS NOT NULL OR nullif(btrim(payment_id),'') IS NOT NULL);
-- The old handler completed unrecognized kinds and known kinds without usable
-- purchase references as ignored receipts. Preserve those completions: there
-- is no purchase to hold or supported exact replay for malformed known kinds.
-- Existing pending receipts are not cleared by this migration.
-- Original authenticated payload redelivery is the only way to recover missing
-- legacy refund IDs. Do not invent references or locally clear these holds.
CREATE INDEX commerce_pending_refund_order ON commerce_webhook_events(order_id,refund_id)
  WHERE processed_at IS NULL AND event_kind LIKE 'refund.%';
CREATE INDEX commerce_pending_refund_payment ON commerce_webhook_events(payment_id,refund_id)
  WHERE processed_at IS NULL AND event_kind LIKE 'refund.%';
CREATE TABLE commerce_provider_refunds (
  id varchar(40) PRIMARY KEY,
  purchase_id uuid NOT NULL REFERENCES template_purchases(id),
  amount_minor bigint NOT NULL CHECK(amount_minor BETWEEN 1 AND 1000000000),
  status varchar(16) NOT NULL CHECK(status IN ('pending','processed','failed')),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX commerce_provider_refunds_purchase ON commerce_provider_refunds(purchase_id);

-- Slot receipts previously retained only a hash after successful processing.
-- Historical event kinds/references cannot be reconstructed from that hash.
-- These rows were committed only after the old processing transaction succeeded.
-- Preserve that historical completion without inventing event kinds/references;
-- stored purchase refund/dispute facts remain authoritative. This does not certify
-- that old aggregate observations resolved every historical refund lifecycle.
ALTER TABLE component_slot_events ADD COLUMN event_kind varchar(100);
ALTER TABLE component_slot_events ADD COLUMN order_id varchar(40);
ALTER TABLE component_slot_events ADD COLUMN payment_id varchar(40);
ALTER TABLE component_slot_events ADD COLUMN dispute_id varchar(40);
ALTER TABLE component_slot_events ADD COLUMN refund_id varchar(40);
ALTER TABLE component_slot_events ADD COLUMN processed_at timestamptz;
DROP TRIGGER component_slot_events_immutable ON component_slot_events;
UPDATE component_slot_events SET processed_at=received_at;
CREATE FUNCTION component_slot_receipt_immutable() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
  IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Financial webhook receipts are retained'; END IF;
  IF (NEW.event_id,NEW.payload_hash,NEW.received_at) IS DISTINCT FROM (OLD.event_id,OLD.payload_hash,OLD.received_at)
    OR (OLD.event_kind IS NOT NULL AND NEW.event_kind IS DISTINCT FROM OLD.event_kind)
    OR (OLD.order_id IS NOT NULL AND NEW.order_id IS DISTINCT FROM OLD.order_id)
    OR (OLD.payment_id IS NOT NULL AND NEW.payment_id IS DISTINCT FROM OLD.payment_id)
    OR (OLD.dispute_id IS NOT NULL AND NEW.dispute_id IS DISTINCT FROM OLD.dispute_id)
    OR (OLD.refund_id IS NOT NULL AND NEW.refund_id IS DISTINCT FROM OLD.refund_id)
    OR (OLD.event_kind IS NOT NULL AND OLD.processed_at IS NOT NULL AND NEW.processed_at IS DISTINCT FROM OLD.processed_at)
  THEN RAISE EXCEPTION 'Authenticated webhook identity and resolved evidence are immutable'; END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER component_slot_events_immutable BEFORE UPDATE OR DELETE ON component_slot_events
  FOR EACH ROW EXECUTE FUNCTION component_slot_receipt_immutable();
CREATE INDEX component_slot_pending_order ON component_slot_events(order_id)
  WHERE processed_at IS NULL;
CREATE INDEX component_slot_pending_payment ON component_slot_events(payment_id)
  WHERE processed_at IS NULL;
CREATE TABLE component_slot_refunds (
  id varchar(40) PRIMARY KEY,
  purchase_id uuid NOT NULL REFERENCES component_slot_purchases(id),
  amount_minor bigint NOT NULL CHECK(amount_minor BETWEEN 1 AND 1000000000),
  status varchar(16) NOT NULL CHECK(status IN ('pending','processed','failed')),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX component_slot_refunds_purchase ON component_slot_refunds(purchase_id);
CREATE TABLE component_slot_disputes (
  id varchar(40) PRIMARY KEY,
  purchase_id uuid NOT NULL REFERENCES component_slot_purchases(id),
  status varchar(32) NOT NULL CHECK(status IN ('open','under_review','action_required','lost','closed','won')),
  -- A legacy status alone does not prove that the provider deducted zero funds.
  deducted_minor bigint CHECK(deducted_minor BETWEEN 0 AND 1000000000),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX component_slot_disputes_purchase ON component_slot_disputes(purchase_id);
-- The prior schema retained only the latest dispute. Preserve it as a held fact;
-- no unknown historic dispute ID/status or zero deduction is synthesized.
-- NULL deduction holds capacity until this exact dispute is observed again.
INSERT INTO component_slot_disputes(id,purchase_id,status)
  SELECT dispute_id,id,dispute_status FROM component_slot_purchases
  WHERE dispute_id IS NOT NULL AND dispute_status IN ('open','under_review','action_required','lost','closed','won');
ALTER TABLE commerce_provider_refunds ENABLE ROW LEVEL SECURITY;
ALTER TABLE component_slot_refunds ENABLE ROW LEVEL SECURITY;
ALTER TABLE component_slot_disputes ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON commerce_provider_refunds,component_slot_refunds,component_slot_disputes FROM PUBLIC;
DO $$ BEGIN
  IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='anon') THEN
    REVOKE ALL ON commerce_provider_refunds,component_slot_refunds,component_slot_disputes FROM anon;
  END IF;
  IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN
    REVOKE ALL ON commerce_provider_refunds,component_slot_refunds,component_slot_disputes FROM authenticated;
  END IF;
END $$;
