-- Education adds accepted disclosures to the existing source-commerce agreement and ledger.
ALTER TABLE source_versions ADD COLUMN safe_source_files jsonb
 CHECK(safe_source_files IS NULL OR jsonb_typeof(safe_source_files)='array');
ALTER TABLE template_purchases
 ADD COLUMN education_release_id uuid REFERENCES education_releases(id),
 ADD COLUMN education_snapshot jsonb,
 ADD CONSTRAINT education_purchase_snapshot CHECK (
   (education_release_id IS NULL AND education_snapshot IS NULL) OR
   (education_release_id IS NOT NULL AND education_snapshot IS NOT NULL AND jsonb_typeof(education_snapshot)='object'));
CREATE INDEX template_purchase_education_release ON template_purchases(education_release_id)
 WHERE education_release_id IS NOT NULL;
CREATE FUNCTION commerce_immutable_education() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.education_release_id IS DISTINCT FROM OLD.education_release_id
    OR NEW.education_snapshot IS DISTINCT FROM OLD.education_snapshot THEN
   RAISE EXCEPTION 'Accepted education package disclosures are immutable';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER template_purchase_education_immutable BEFORE UPDATE ON template_purchases
 FOR EACH ROW EXECUTE FUNCTION commerce_immutable_education();
CREATE FUNCTION commerce_immutable_file_manifest() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.safe_source_files IS DISTINCT FROM OLD.safe_source_files THEN
   RAISE EXCEPTION 'Inspected source file paths are immutable';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER source_file_manifest_immutable BEFORE UPDATE ON source_versions
 FOR EACH ROW EXECUTE FUNCTION commerce_immutable_file_manifest();
