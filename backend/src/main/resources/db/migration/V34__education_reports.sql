-- Artifact identity/evidence is independent of mutable moderator notes and actions.
ALTER TABLE reports ADD COLUMN education_release_id uuid REFERENCES education_releases(id);
ALTER TABLE reports ADD COLUMN source_hash varchar(64) CHECK(source_hash ~ '^[a-f0-9]{64}$');
ALTER TABLE reports ADD COLUMN education_snapshot jsonb CHECK(jsonb_typeof(education_snapshot)='object');
ALTER TABLE reports ADD CONSTRAINT education_report_evidence CHECK(
 (education_release_id IS NULL AND source_hash IS NULL AND education_snapshot IS NULL)
 OR (education_release_id IS NOT NULL AND source_hash IS NOT NULL AND education_snapshot IS NOT NULL AND target_type='PRODUCT'));
CREATE FUNCTION education_report_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF ROW(NEW.education_release_id,NEW.source_hash,NEW.education_snapshot) IS DISTINCT FROM ROW(OLD.education_release_id,OLD.source_hash,OLD.education_snapshot)
 OR (OLD.education_release_id IS NOT NULL AND ROW(NEW.target_type,NEW.target_id) IS DISTINCT FROM ROW(OLD.target_type,OLD.target_id))
 THEN RAISE EXCEPTION 'Reported education artifact identity and evidence are immutable'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER education_report_identity BEFORE UPDATE ON reports FOR EACH ROW EXECUTE FUNCTION education_report_identity();
