-- Private immutable built packages. The server role is the sole data authority.
CREATE TABLE hosted_demos (
 id uuid PRIMARY KEY,
 owner_id uuid NOT NULL REFERENCES users,
 product_id uuid NOT NULL REFERENCES products,
 title varchar(100) NOT NULL,
 version varchar(40) NOT NULL,
 storage_key text NOT NULL,
 archive_sha256 char(64) NOT NULL CHECK(archive_sha256 ~ '^[a-f0-9]{64}$'),
 manifest_sha256 char(64) NOT NULL CHECK(manifest_sha256 ~ '^[a-f0-9]{64}$'),
 size_bytes integer NOT NULL CHECK(size_bytes BETWEEN 22 AND 5242880),
 expanded_bytes integer NOT NULL CHECK(expanded_bytes BETWEEN 1 AND 10485760),
 file_count integer NOT NULL CHECK(file_count BETWEEN 1 AND 256),
 files jsonb NOT NULL CHECK(jsonb_typeof(files)='array'),
 rights_consent_at timestamptz NOT NULL DEFAULT now(),
 status varchar(30) NOT NULL DEFAULT 'DRAFT' CHECK(status IN ('DRAFT','PENDING','APPROVED','CHANGES_REQUESTED','REJECTED','SUSPENDED')),
 deployment_state varchar(30) NOT NULL DEFAULT 'NOT_CREATED' CHECK(deployment_state IN ('NOT_CREATED','CREATING','UNKNOWN','READY','DELETE_PENDING','DELETED')),
 desired_state varchar(20) NOT NULL DEFAULT 'PUBLISHED' CHECK(desired_state IN ('PUBLISHED','WITHDRAWN')),
 deployment_id uuid UNIQUE,
 expires_at timestamptz,
 submitted_at timestamptz,
 reviewed_at timestamptz,
 reviewed_by uuid REFERENCES users,
 review_reason varchar(2000),
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(owner_id,product_id,version),
 CHECK((deployment_id IS NULL AND expires_at IS NULL AND deployment_state IN ('NOT_CREATED','DELETED')) OR (deployment_id IS NOT NULL AND expires_at IS NOT NULL)),
 CHECK(deployment_state NOT IN ('DELETE_PENDING','DELETED') OR desired_state='WITHDRAWN'),
 CHECK(deployment_state<>'READY' OR desired_state='PUBLISHED')
);
CREATE INDEX hosted_demos_owner ON hosted_demos(owner_id,created_at DESC,id);
CREATE INDEX hosted_demos_public ON hosted_demos(product_id,expires_at) WHERE status='APPROVED' AND deployment_state='READY' AND desired_state='PUBLISHED';
CREATE TABLE hosting_audit (
 id uuid PRIMARY KEY,
 demo_id uuid NOT NULL REFERENCES hosted_demos,
 actor_id uuid REFERENCES users,
 action varchar(60) NOT NULL,
 detail varchar(2400) NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX hosting_audit_demo ON hosting_audit(demo_id,created_at,id);
CREATE FUNCTION hosted_demo_retained() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Hosted package identities and tombstones are retained'; END IF;
 IF (NEW.id,NEW.owner_id,NEW.product_id,NEW.title,NEW.version,NEW.storage_key,NEW.archive_sha256,NEW.manifest_sha256,NEW.size_bytes,NEW.expanded_bytes,NEW.file_count,NEW.files,NEW.rights_consent_at,NEW.created_at) IS DISTINCT FROM (OLD.id,OLD.owner_id,OLD.product_id,OLD.title,OLD.version,OLD.storage_key,OLD.archive_sha256,OLD.manifest_sha256,OLD.size_bytes,OLD.expanded_bytes,OLD.file_count,OLD.files,OLD.rights_consent_at,OLD.created_at) THEN RAISE EXCEPTION 'Hosted static package and rights consent are immutable'; END IF;
 IF OLD.deployment_id IS NOT NULL AND (NEW.deployment_id,NEW.expires_at) IS DISTINCT FROM (OLD.deployment_id,OLD.expires_at) THEN RAISE EXCEPTION 'Deployment identity and fixed expiry are immutable'; END IF;
 IF (OLD.desired_state='WITHDRAWN' AND NEW.desired_state<>'WITHDRAWN') OR (OLD.deployment_state='DELETED' AND NEW.deployment_state<>'DELETED') THEN RAISE EXCEPTION 'Withdrawal and tombstone facts are monotonic'; END IF;
 IF OLD.deployment_id IS NULL AND NEW.deployment_id IS NOT NULL AND (NEW.expires_at<=clock_timestamp() OR NEW.expires_at>clock_timestamp()+interval '30 days' OR NEW.deployment_state<>'CREATING') THEN RAISE EXCEPTION 'Deployment reservation must have bounded future expiry'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER hosted_demo_immutable BEFORE UPDATE OR DELETE ON hosted_demos FOR EACH ROW EXECUTE FUNCTION hosted_demo_retained();
CREATE TRIGGER hosting_audit_append BEFORE UPDATE OR DELETE ON hosting_audit FOR EACH ROW EXECUTE FUNCTION reject_payment_ledger_change();
DO $$ DECLARE t text; BEGIN
 FOREACH t IN ARRAY ARRAY['hosted_demos','hosting_audit'] LOOP
  EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t);
  EXECUTE format('REVOKE ALL ON %I FROM PUBLIC',t);
  IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='anon') THEN EXECUTE format('REVOKE ALL ON %I FROM anon',t); END IF;
  IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN EXECUTE format('REVOKE ALL ON %I FROM authenticated',t); END IF;
 END LOOP;
END $$;
