-- Keep server-derived component evidence immutable and separate from moderator notes.
ALTER TABLE reports ADD COLUMN component_revision bigint CHECK(component_revision>0);
ALTER TABLE reports ADD COLUMN component_version varchar(80);
ALTER TABLE reports ADD COLUMN component_source_hash varchar(64) CHECK(component_source_hash ~ '^[a-f0-9]{64}$');
ALTER TABLE reports ADD COLUMN component_snapshot jsonb;
ALTER TABLE reports ADD CONSTRAINT component_report_evidence CHECK(
 (target_type<>'COMPONENT' AND component_revision IS NULL AND component_version IS NULL AND component_source_hash IS NULL AND component_snapshot IS NULL)
 OR (target_type='COMPONENT' AND component_revision IS NOT NULL AND component_version IS NOT NULL AND length(component_version)>0
   AND component_source_hash IS NOT NULL AND component_snapshot IS NOT NULL AND jsonb_typeof(component_snapshot)='object'
   AND education_release_id IS NULL AND component_snapshot->>'componentId'=target_id::text
   AND component_snapshot->>'revision'=component_revision::text AND component_snapshot->>'version'=component_version
   AND component_snapshot->>'sourceHash'=component_source_hash AND component_snapshot->'source'->>'sha256'=component_source_hash) IS TRUE);
CREATE FUNCTION component_report_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF ROW(NEW.component_revision,NEW.component_version,NEW.component_source_hash,NEW.component_snapshot)
    IS DISTINCT FROM ROW(OLD.component_revision,OLD.component_version,OLD.component_source_hash,OLD.component_snapshot)
 OR (OLD.component_revision IS NOT NULL AND ROW(NEW.target_type,NEW.target_id) IS DISTINCT FROM ROW(OLD.target_type,OLD.target_id))
 THEN RAISE EXCEPTION 'Reported component artifact identity and evidence are immutable'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER component_report_identity BEFORE UPDATE ON reports FOR EACH ROW EXECUTE FUNCTION component_report_identity();

-- Existing append-only action protection freezes this exact report and enforcement generation.
ALTER TABLE moderation_actions ADD COLUMN component_report_id uuid REFERENCES reports(id);
ALTER TABLE moderation_actions ADD COLUMN component_enforcement_revision bigint CHECK(component_enforcement_revision>0);
ALTER TABLE moderation_actions ADD CONSTRAINT component_report_action_evidence CHECK(
 (component_report_id IS NULL AND component_enforcement_revision IS NULL)
 OR (component_report_id IS NOT NULL AND component_enforcement_revision IS NOT NULL AND target_type='COMPONENT' AND action='suspend'));

-- File-backed curated recipes are moderated by exact hash. Independent remixes keep their own authority.
CREATE TABLE component_recipe_enforcement (
 recipe_slug varchar(140) NOT NULL,
 source_sha256 varchar(64) NOT NULL CHECK(source_sha256 ~ '^[a-f0-9]{64}$'),
 suspended boolean NOT NULL DEFAULT true,
 actor_id uuid NOT NULL REFERENCES users(id),
 reason varchar(2000) NOT NULL CHECK(length(reason)>=3),
 updated_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(recipe_slug,source_sha256)
);
ALTER TABLE component_recipe_enforcement ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON component_recipe_enforcement FROM PUBLIC;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='anon') THEN REVOKE ALL ON component_recipe_enforcement FROM anon; END IF;
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN REVOKE ALL ON component_recipe_enforcement FROM authenticated; END IF;
END $$;
