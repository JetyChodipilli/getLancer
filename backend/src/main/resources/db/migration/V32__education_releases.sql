CREATE TABLE education_free_packages (
 id uuid PRIMARY KEY, owner_id uuid NOT NULL REFERENCES users(id), storage_key text NOT NULL,
 sha256 varchar(64) NOT NULL CHECK(sha256 ~ '^[a-f0-9]{64}$'),
 size_bytes integer NOT NULL CHECK(size_bytes BETWEEN 22 AND 5242880),
 entry_count integer NOT NULL CHECK(entry_count BETWEEN 2 AND 500),
 files jsonb NOT NULL CHECK(jsonb_typeof(files)='array'),
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE education_releases (
 id uuid PRIMARY KEY, product_id uuid NOT NULL REFERENCES products(id), owner_id uuid NOT NULL REFERENCES users(id),
 revision bigint NOT NULL DEFAULT 1 CHECK(revision>0),
 status varchar(20) NOT NULL DEFAULT 'DRAFT' CHECK(status IN ('DRAFT','PENDING','APPROVED','CHANGES_REQUESTED','SUSPENDED')),
 draft jsonb NOT NULL CHECK(jsonb_typeof(draft)='object'),
 snapshot jsonb CHECK(jsonb_typeof(snapshot)='object'),
 source_hash varchar(64) CHECK(source_hash ~ '^[a-f0-9]{64}$'),
 free_package_id uuid REFERENCES education_free_packages(id),source_version_id uuid REFERENCES source_versions(id),
 review_reason varchar(2000),submitted_at timestamptz,reviewed_at timestamptz,
 created_at timestamptz NOT NULL DEFAULT now(),updated_at timestamptz NOT NULL DEFAULT now(),
 CHECK(submitted_at IS NULL OR (snapshot IS NOT NULL AND source_hash IS NOT NULL)),
 CHECK(status<>'APPROVED' OR submitted_at IS NOT NULL),
 CHECK(submitted_at IS NULL OR
   (snapshot->>'mode'='SHOWCASE' AND free_package_id IS NULL AND source_version_id IS NULL) OR
   (snapshot->>'mode'='FREE' AND free_package_id IS NOT NULL AND source_version_id IS NULL) OR
   (snapshot->>'mode'='PAID' AND free_package_id IS NULL AND source_version_id IS NOT NULL))
);
CREATE INDEX education_owner_releases ON education_releases(owner_id,created_at DESC,id);
CREATE INDEX education_product_releases ON education_releases(product_id,reviewed_at DESC,id) WHERE status='APPROVED';
CREATE INDEX education_review_queue ON education_releases(status,submitted_at,id);
CREATE TABLE education_private_details (
 release_id uuid PRIMARY KEY REFERENCES education_releases(id), institution varchar(160) NOT NULL DEFAULT '',
 academic_year varchar(40) NOT NULL DEFAULT '',branch varchar(100) NOT NULL DEFAULT '',
 share_academic_details boolean NOT NULL DEFAULT false,revision bigint NOT NULL DEFAULT 1 CHECK(revision>0)
);
CREATE TABLE education_audit (
 id uuid PRIMARY KEY,release_id uuid NOT NULL REFERENCES education_releases(id),actor_id uuid REFERENCES users(id),
 action varchar(50) NOT NULL,reason varchar(2000) NOT NULL,
 source_hash varchar(64) CHECK(source_hash ~ '^[a-f0-9]{64}$'),created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX education_inspection_evidence ON education_audit(release_id,actor_id,action,source_hash);
CREATE FUNCTION education_frozen_release() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Education release identities are retained'; END IF;
 IF ROW(NEW.id,NEW.product_id,NEW.owner_id,NEW.created_at) IS DISTINCT FROM ROW(OLD.id,OLD.product_id,OLD.owner_id,OLD.created_at)
 THEN RAISE EXCEPTION 'Education release identity is immutable'; END IF;
 IF OLD.submitted_at IS NOT NULL AND
  ROW(NEW.draft,NEW.snapshot,NEW.source_hash,NEW.free_package_id,NEW.source_version_id,NEW.submitted_at)
  IS DISTINCT FROM ROW(OLD.draft,OLD.snapshot,OLD.source_hash,OLD.free_package_id,OLD.source_version_id,OLD.submitted_at)
 THEN RAISE EXCEPTION 'Submitted education content and source terms are immutable'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER education_release_frozen BEFORE UPDATE OR DELETE ON education_releases FOR EACH ROW EXECUTE FUNCTION education_frozen_release();
CREATE TRIGGER education_package_immutable BEFORE UPDATE OR DELETE ON education_free_packages FOR EACH ROW EXECUTE FUNCTION reject_payment_ledger_change();
CREATE TRIGGER education_audit_immutable BEFORE UPDATE OR DELETE ON education_audit FOR EACH ROW EXECUTE FUNCTION reject_payment_ledger_change();
DO $$ DECLARE t text; BEGIN
 FOREACH t IN ARRAY ARRAY['education_releases','education_free_packages','education_private_details','education_audit'] LOOP
  EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t); EXECUTE format('REVOKE ALL ON %I FROM PUBLIC',t);
  IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='anon') THEN EXECUTE format('REVOKE ALL ON %I FROM anon',t); END IF;
  IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN EXECUTE format('REVOKE ALL ON %I FROM authenticated',t); END IF;
 END LOOP;
END $$;
