-- Preserve every existing component quote, receipt, capture and audit record.
ALTER TABLE component_slot_purchases ADD COLUMN pool varchar(12) NOT NULL DEFAULT 'COMPONENT'
  CHECK(pool IN ('PROJECT','TEMPLATE','COMPONENT'));
ALTER TABLE component_slot_pricing ADD COLUMN pool varchar(12) NOT NULL DEFAULT 'COMPONENT'
  CHECK(pool IN ('PROJECT','TEMPLATE','COMPONENT'));
ALTER TABLE component_slot_pricing DROP CONSTRAINT component_slot_pricing_pkey;
ALTER TABLE component_slot_pricing ADD PRIMARY KEY(pool);
INSERT INTO component_slot_pricing(pool) VALUES('PROJECT'),('TEMPLATE');
DROP INDEX component_slot_unresolved;
CREATE UNIQUE INDEX publishing_slot_unresolved ON component_slot_purchases(owner_id,pool,mode)
  WHERE status IN ('CREATING','UNKNOWN','ORDER_CREATED');
CREATE INDEX publishing_paid_capacity ON component_slot_purchases(owner_id,pool)
  WHERE mode='live' AND status='CAPTURED' AND refunded_minor=0;
CREATE INDEX publishing_active_templates ON source_templates(seller_id,created_at,id) WHERE status='ACTIVE';

-- Prior template publishing had no quota. Preserve existing active capacity once.
CREATE TABLE publishing_capacity_grants (
  owner_id uuid NOT NULL REFERENCES users(id), pool varchar(12) NOT NULL CHECK(pool='TEMPLATE'),
  slots bigint NOT NULL CHECK(slots>0), reason varchar(100) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(owner_id,pool)
);
INSERT INTO publishing_capacity_grants(owner_id,pool,slots,reason)
  SELECT seller_id,'TEMPLATE',count(*)-3,'Existing active template capacity before V25'
  FROM source_templates WHERE status='ACTIVE' GROUP BY seller_id HAVING count(*)>3;
CREATE TRIGGER publishing_grants_immutable BEFORE UPDATE OR DELETE ON publishing_capacity_grants
  FOR EACH ROW EXECUTE FUNCTION component_append_only();
ALTER TABLE publishing_capacity_grants ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON publishing_capacity_grants FROM PUBLIC;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='anon') THEN REVOKE ALL ON publishing_capacity_grants FROM anon; END IF;
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN REVOKE ALL ON publishing_capacity_grants FROM authenticated; END IF;
END $$;

CREATE OR REPLACE FUNCTION component_frozen_purchase() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP='DELETE' OR ROW(NEW.id,NEW.owner_id,NEW.pool,NEW.idempotency_key,NEW.amount_minor,NEW.currency,NEW.mode,NEW.created_at)
    IS DISTINCT FROM ROW(OLD.id,OLD.owner_id,OLD.pool,OLD.idempotency_key,OLD.amount_minor,OLD.currency,OLD.mode,OLD.created_at)
    OR NEW.refunded_minor<OLD.refunded_minor
    OR (OLD.order_id IS NOT NULL AND NEW.order_id IS DISTINCT FROM OLD.order_id)
    OR (OLD.payment_id IS NOT NULL AND NEW.payment_id IS DISTINCT FROM OLD.payment_id)
  THEN RAISE EXCEPTION 'Frozen purchase facts cannot change'; END IF;
  RETURN NEW;
END; $$;
