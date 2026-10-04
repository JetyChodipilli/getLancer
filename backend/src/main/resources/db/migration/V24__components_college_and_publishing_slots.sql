CREATE TABLE component_entries (
  id uuid PRIMARY KEY, owner_id uuid NOT NULL REFERENCES users(id),
  recipe_slug varchar(100) NOT NULL, slug varchar(140) NOT NULL UNIQUE,
  title varchar(100) NOT NULL, summary varchar(240) NOT NULL,
  contribution varchar(2000) NOT NULL, revision bigint NOT NULL DEFAULT 1 CHECK(revision>0),
  status varchar(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','PENDING','ACTIVE','ARCHIVED','SUSPENDED')),
  review_reason varchar(2000), published_at timestamptz,
  published_source jsonb, published_context jsonb,
  CHECK ((published_at IS NULL AND published_source IS NULL AND published_context IS NULL) OR
    (published_at IS NOT NULL AND published_source IS NOT NULL AND published_context IS NOT NULL AND jsonb_typeof(published_source)='object' AND jsonb_typeof(published_context)='object')),
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX components_owner_status ON component_entries(owner_id,status);
CREATE TABLE component_slot_pricing (
  id boolean PRIMARY KEY DEFAULT true CHECK(id),
  amount_minor bigint CHECK(amount_minor BETWEEN 100 AND 1000000000),
  enabled boolean NOT NULL DEFAULT false,
  updated_at timestamptz NOT NULL DEFAULT now(),
  CHECK(NOT enabled OR amount_minor IS NOT NULL)
);
INSERT INTO component_slot_pricing(id) VALUES(true);
CREATE TABLE component_slot_purchases (
  id uuid PRIMARY KEY, owner_id uuid NOT NULL REFERENCES users(id),
  idempotency_key uuid NOT NULL, amount_minor bigint NOT NULL CHECK(amount_minor BETWEEN 100 AND 1000000000),
  currency varchar(3) NOT NULL DEFAULT 'INR' CHECK(currency='INR'),
  mode varchar(4) NOT NULL CHECK(mode IN ('test','live')),
  status varchar(20) NOT NULL CHECK(status IN ('CREATING','UNKNOWN','REJECTED','ORDER_CREATED','CAPTURED','REFUNDED','DISPUTED')),
  order_id varchar(40) UNIQUE, payment_id varchar(40) UNIQUE,
  refunded_minor bigint NOT NULL DEFAULT 0 CHECK(refunded_minor BETWEEN 0 AND amount_minor),
  dispute_id varchar(40), dispute_status varchar(20),
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(owner_id,idempotency_key)
);
CREATE UNIQUE INDEX component_slot_unresolved ON component_slot_purchases(owner_id,mode)
  WHERE status IN ('CREATING','UNKNOWN','ORDER_CREATED');
CREATE TABLE component_audit (
  id uuid PRIMARY KEY, actor_id uuid REFERENCES users(id), target_id uuid,
  kind varchar(60) NOT NULL, detail varchar(2000) NOT NULL, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE component_slot_ledger (
  id uuid PRIMARY KEY, purchase_id uuid NOT NULL REFERENCES component_slot_purchases(id),
  entry_key varchar(100) NOT NULL, kind varchar(10) NOT NULL CHECK(kind IN ('CAPTURE','REFUND')),
  amount_minor bigint NOT NULL CHECK(amount_minor>0), created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(purchase_id,entry_key)
);
CREATE TABLE component_slot_events (
  event_id varchar(100) PRIMARY KEY, payload_hash varchar(64) NOT NULL, received_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE college_project_metadata (
  product_id uuid PRIMARY KEY REFERENCES products(id),
  category varchar(20) NOT NULL CHECK(category IN ('FULL_STACK','DATA_ANALYTICS','AI_ML','IOT')),
  language varchar(100) NOT NULL, problem varchar(2000) NOT NULL,
  outcome varchar(2000) NOT NULL, prerequisites varchar(2000) NOT NULL,
  contribution varchar(2000) NOT NULL,
  institution varchar(160) NOT NULL DEFAULT '', academic_year varchar(40) NOT NULL DEFAULT '', branch varchar(100) NOT NULL DEFAULT '',
  share_academic_details boolean NOT NULL DEFAULT false,
  revision bigint NOT NULL DEFAULT 1 CHECK(revision>0),
  status varchar(20) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','APPROVED','CHANGES_REQUESTED','SUSPENDED')),
  review_reason varchar(2000), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX college_project_category ON college_project_metadata(category,status);
CREATE FUNCTION component_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Component audit records are append-only'; END; $$;
CREATE TRIGGER component_audit_immutable BEFORE UPDATE OR DELETE ON component_audit
  FOR EACH ROW EXECUTE FUNCTION component_append_only();
CREATE TRIGGER component_slot_events_immutable BEFORE UPDATE OR DELETE ON component_slot_events
  FOR EACH ROW EXECUTE FUNCTION component_append_only();
CREATE TRIGGER component_slot_ledger_immutable BEFORE UPDATE OR DELETE ON component_slot_ledger
  FOR EACH ROW EXECUTE FUNCTION component_append_only();
CREATE FUNCTION component_frozen_purchase() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP='DELETE' OR ROW(NEW.id,NEW.owner_id,NEW.idempotency_key,NEW.amount_minor,NEW.currency,NEW.mode,NEW.created_at)
    IS DISTINCT FROM ROW(OLD.id,OLD.owner_id,OLD.idempotency_key,OLD.amount_minor,OLD.currency,OLD.mode,OLD.created_at)
    OR NEW.refunded_minor<OLD.refunded_minor
    OR (OLD.order_id IS NOT NULL AND NEW.order_id IS DISTINCT FROM OLD.order_id)
    OR (OLD.payment_id IS NOT NULL AND NEW.payment_id IS DISTINCT FROM OLD.payment_id)
  THEN RAISE EXCEPTION 'Frozen purchase facts cannot change'; END IF;
  RETURN NEW;
END; $$;
CREATE TRIGGER component_slot_purchase_immutable BEFORE UPDATE OR DELETE ON component_slot_purchases
  FOR EACH ROW EXECUTE FUNCTION component_frozen_purchase();
ALTER TABLE component_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE component_slot_pricing ENABLE ROW LEVEL SECURITY;
ALTER TABLE component_slot_purchases ENABLE ROW LEVEL SECURITY;
ALTER TABLE component_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE component_slot_ledger ENABLE ROW LEVEL SECURITY;
ALTER TABLE component_slot_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE college_project_metadata ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON component_entries,component_slot_pricing,component_slot_purchases,component_audit,component_slot_ledger,component_slot_events,college_project_metadata FROM PUBLIC;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='anon') THEN REVOKE ALL ON component_entries,component_slot_pricing,component_slot_purchases,component_audit,component_slot_ledger,component_slot_events,college_project_metadata FROM anon; END IF;
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN REVOKE ALL ON component_entries,component_slot_pricing,component_slot_purchases,component_audit,component_slot_ledger,component_slot_events,college_project_metadata FROM authenticated; END IF;
END $$;

CREATE INDEX component_review_queue ON component_entries(updated_at,id) WHERE status IN ('PENDING','ACTIVE','SUSPENDED');
CREATE INDEX college_review_queue ON college_project_metadata(status,updated_at,product_id);
CREATE INDEX component_purchase_owner_history ON component_slot_purchases(owner_id,created_at DESC,id);

CREATE FUNCTION freeze_component_source() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF OLD.published_source IS NOT NULL AND NEW.published_source IS DISTINCT FROM OLD.published_source THEN
    RAISE EXCEPTION 'Published component source is immutable';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER component_source_immutable BEFORE UPDATE ON component_entries
  FOR EACH ROW EXECUTE FUNCTION freeze_component_source();
