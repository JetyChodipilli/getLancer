CREATE TABLE source_templates (
 id uuid PRIMARY KEY, seller_id uuid NOT NULL REFERENCES users, product_id uuid NOT NULL REFERENCES products,
 slug varchar(140) NOT NULL UNIQUE, title varchar(100) NOT NULL, summary varchar(240) NOT NULL,
 description text NOT NULL, price_minor bigint NOT NULL CHECK(price_minor BETWEEN 100 AND 1000000000),
 currency varchar(3) NOT NULL DEFAULT 'INR' CHECK(currency='INR'), license_terms text NOT NULL,
 status varchar(20) NOT NULL DEFAULT 'DRAFT' CHECK(status IN ('DRAFT','ACTIVE','ARCHIVED','SUSPENDED')),
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE source_versions (
 id uuid PRIMARY KEY, template_id uuid NOT NULL REFERENCES source_templates, version varchar(40) NOT NULL,
 release_notes text NOT NULL, storage_key text NOT NULL UNIQUE, sha256 char(64) NOT NULL,
 size_bytes integer NOT NULL CHECK(size_bytes BETWEEN 1 AND 5242880), entry_count integer NOT NULL CHECK(entry_count BETWEEN 2 AND 500),
 license_terms text NOT NULL, manifest_files text NOT NULL, rights_consented_at timestamptz NOT NULL,
 status varchar(24) NOT NULL DEFAULT 'DRAFT' CHECK(status IN ('DRAFT','PENDING','CHANGES_REQUESTED','APPROVED','REJECTED','SUSPENDED')),
 review_reason text, submitted_at timestamptz, reviewed_at timestamptz, reviewed_by uuid REFERENCES users,
 created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(template_id,version)
);
CREATE INDEX source_versions_template ON source_versions(template_id,created_at DESC);
CREATE TABLE template_purchases (
 id uuid PRIMARY KEY, template_id uuid NOT NULL REFERENCES source_templates, version_id uuid NOT NULL REFERENCES source_versions,
 buyer_id uuid NOT NULL REFERENCES users, seller_id uuid NOT NULL REFERENCES users, idempotency_key uuid NOT NULL,
 title varchar(100) NOT NULL, version varchar(40) NOT NULL, license_terms text NOT NULL, sha256 char(64) NOT NULL,
 amount_minor bigint NOT NULL CHECK(amount_minor BETWEEN 100 AND 1000000000), currency varchar(3) NOT NULL CHECK(currency='INR'),
 account_id varchar(40) NOT NULL, mode varchar(4) NOT NULL CHECK(mode IN ('test','live')),
 status varchar(24) NOT NULL CHECK(status IN ('CREATING','UNKNOWN','REJECTED','ORDER_CREATED','CAPTURED','PARTIALLY_REFUNDED','REFUNDED','DISPUTED')),
 order_id varchar(40) UNIQUE, payment_id varchar(40) UNIQUE,
 refunded_minor bigint NOT NULL DEFAULT 0 CHECK(refunded_minor BETWEEN 0 AND amount_minor),
 transfer_status varchar(32) NOT NULL DEFAULT 'NOT_CREATED', entitlement_revoked boolean NOT NULL DEFAULT false,
 attention_reason varchar(200), license_consented_at timestamptz NOT NULL DEFAULT now(),
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(), UNIQUE(buyer_id,idempotency_key)
);
CREATE UNIQUE INDEX commerce_active_purchase ON template_purchases(buyer_id,version_id,mode) WHERE status<>'REJECTED';
CREATE TABLE commerce_ledger (
 id uuid PRIMARY KEY, purchase_id uuid NOT NULL REFERENCES template_purchases, entry_key varchar(100) NOT NULL,
 kind varchar(20) NOT NULL CHECK(kind IN ('CAPTURE','REFUND')), amount_minor bigint NOT NULL CHECK(amount_minor>0),
 currency varchar(3) NOT NULL CHECK(currency='INR'), created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(purchase_id,entry_key)
);
CREATE TABLE commerce_webhook_events (event_id varchar(100) PRIMARY KEY,payload_hash char(64) NOT NULL,event_kind varchar(100) NOT NULL,received_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE commerce_disputes (
 id uuid PRIMARY KEY, purchase_id uuid NOT NULL UNIQUE REFERENCES template_purchases, buyer_id uuid NOT NULL REFERENCES users,
 reason text NOT NULL, status varchar(16) NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','RESUMED','REVOKED')),
 resolution_reason text, resolved_by uuid REFERENCES users, resolved_at timestamptz, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE commerce_provider_disputes (id varchar(40) PRIMARY KEY,purchase_id uuid NOT NULL REFERENCES template_purchases,status varchar(32) NOT NULL,deducted_minor bigint NOT NULL CHECK(deducted_minor>=0),updated_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE commerce_audit (id uuid PRIMARY KEY,template_id uuid REFERENCES source_templates,purchase_id uuid REFERENCES template_purchases,actor_id uuid REFERENCES users,kind varchar(50) NOT NULL,detail text NOT NULL,created_at timestamptz NOT NULL DEFAULT now());
CREATE FUNCTION commerce_immutable_version() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Source releases are retained for immutable buyer entitlements'; END IF;
 IF (NEW.id,NEW.template_id,NEW.version,NEW.release_notes,NEW.storage_key,NEW.sha256,NEW.size_bytes,NEW.entry_count,NEW.license_terms,NEW.manifest_files,NEW.rights_consented_at,NEW.created_at) IS DISTINCT FROM (OLD.id,OLD.template_id,OLD.version,OLD.release_notes,OLD.storage_key,OLD.sha256,OLD.size_bytes,OLD.entry_count,OLD.license_terms,OLD.manifest_files,OLD.rights_consented_at,OLD.created_at) THEN RAISE EXCEPTION 'Source release contents are immutable; create a new release'; END IF;
 RETURN NEW; END $$;
CREATE TRIGGER source_versions_immutable BEFORE UPDATE OR DELETE ON source_versions FOR EACH ROW EXECUTE FUNCTION commerce_immutable_version();
CREATE FUNCTION commerce_immutable_purchase() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Commercial purchases are retained for financial records'; END IF;
 IF (NEW.id,NEW.template_id,NEW.version_id,NEW.buyer_id,NEW.seller_id,NEW.idempotency_key,NEW.title,NEW.version,NEW.license_terms,NEW.sha256,NEW.amount_minor,NEW.currency,NEW.account_id,NEW.mode,NEW.license_consented_at,NEW.created_at) IS DISTINCT FROM (OLD.id,OLD.template_id,OLD.version_id,OLD.buyer_id,OLD.seller_id,OLD.idempotency_key,OLD.title,OLD.version,OLD.license_terms,OLD.sha256,OLD.amount_minor,OLD.currency,OLD.account_id,OLD.mode,OLD.license_consented_at,OLD.created_at) THEN RAISE EXCEPTION 'Purchase agreement snapshots are immutable'; END IF;
 IF (OLD.order_id IS NOT NULL AND NEW.order_id IS DISTINCT FROM OLD.order_id) OR (OLD.payment_id IS NOT NULL AND NEW.payment_id IS DISTINCT FROM OLD.payment_id) OR NEW.refunded_minor<OLD.refunded_minor THEN RAISE EXCEPTION 'Confirmed payment bindings and refund totals cannot be replaced or reduced'; END IF;
 RETURN NEW; END $$;
CREATE TRIGGER template_purchases_immutable BEFORE UPDATE OR DELETE ON template_purchases FOR EACH ROW EXECUTE FUNCTION commerce_immutable_purchase();
CREATE TRIGGER commerce_ledger_immutable BEFORE UPDATE OR DELETE ON commerce_ledger FOR EACH ROW EXECUTE FUNCTION reject_payment_ledger_change();
CREATE TRIGGER commerce_audit_immutable BEFORE UPDATE OR DELETE ON commerce_audit FOR EACH ROW EXECUTE FUNCTION reject_payment_ledger_change();
ALTER TABLE source_templates ENABLE ROW LEVEL SECURITY; ALTER TABLE source_versions ENABLE ROW LEVEL SECURITY;
ALTER TABLE template_purchases ENABLE ROW LEVEL SECURITY; ALTER TABLE commerce_ledger ENABLE ROW LEVEL SECURITY;
ALTER TABLE commerce_webhook_events ENABLE ROW LEVEL SECURITY; ALTER TABLE commerce_disputes ENABLE ROW LEVEL SECURITY;
ALTER TABLE commerce_provider_disputes ENABLE ROW LEVEL SECURITY; ALTER TABLE commerce_audit ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON source_templates,source_versions,template_purchases,commerce_ledger,commerce_webhook_events,commerce_disputes,commerce_provider_disputes,commerce_audit FROM PUBLIC;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='anon') THEN REVOKE ALL ON source_templates,source_versions,template_purchases,commerce_ledger,commerce_webhook_events,commerce_disputes,commerce_provider_disputes,commerce_audit FROM anon; END IF;
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN REVOKE ALL ON source_templates,source_versions,template_purchases,commerce_ledger,commerce_webhook_events,commerce_disputes,commerce_provider_disputes,commerce_audit FROM authenticated; END IF;
END $$;
