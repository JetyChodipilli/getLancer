-- Additive V4.6 contribution and private-bookmark records.
ALTER TABLE component_entries ADD COLUMN submitted_source jsonb;
ALTER TABLE component_entries ADD COLUMN withdrawn_at timestamptz;
CREATE TABLE component_releases (
  component_id uuid NOT NULL REFERENCES component_entries(id),
  revision bigint NOT NULL CHECK(revision>0),
  source jsonb NOT NULL CHECK(jsonb_typeof(source)='object'),
  context jsonb NOT NULL CHECK(jsonb_typeof(context)='object'),
  source_sha256 varchar(64) NOT NULL CHECK(source_sha256 ~ '^[a-f0-9]{64}$'),
  published_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(component_id,revision)
);
CREATE TRIGGER component_release_immutable BEFORE UPDATE OR DELETE ON component_releases
  FOR EACH ROW EXECUTE FUNCTION component_append_only();
CREATE FUNCTION freeze_submitted_component() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF OLD.status='PENDING' AND
    ROW(NEW.recipe_slug,NEW.title,NEW.summary,NEW.contribution,NEW.submitted_source)
    IS DISTINCT FROM ROW(OLD.recipe_slug,OLD.title,OLD.summary,OLD.contribution,OLD.submitted_source)
  THEN RAISE EXCEPTION 'Submitted component content is immutable'; END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER component_submission_immutable BEFORE UPDATE ON component_entries
  FOR EACH ROW EXECUTE FUNCTION freeze_submitted_component();
CREATE TABLE saved_components (
  user_id uuid NOT NULL REFERENCES users(id),
  slug varchar(140) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(user_id,slug)
);
ALTER TABLE component_releases ENABLE ROW LEVEL SECURITY;
ALTER TABLE saved_components ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON component_releases,saved_components FROM PUBLIC;
DO $$ BEGIN
  IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='anon') THEN REVOKE ALL ON component_releases,saved_components FROM anon; END IF;
  IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN REVOKE ALL ON component_releases,saved_components FROM authenticated; END IF;
END $$;
