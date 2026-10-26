-- Private, append-only review evidence. Public source/release projections never join this table.
CREATE TABLE component_review_assessments (
 id uuid PRIMARY KEY,
 component_id uuid NOT NULL REFERENCES component_entries(id),
 revision bigint NOT NULL CHECK(revision>0),
 source_hash varchar(64) NOT NULL CHECK(source_hash ~ '^[a-f0-9]{64}$'),
 admin_id uuid NOT NULL REFERENCES users(id),
 decision varchar(30) NOT NULL CHECK(decision IN ('APPROVE','CHANGES_REQUESTED','SUSPEND','APPEAL_RESTORE')),
 reason varchar(2000) NOT NULL CHECK(length(reason)>=20),
 assessments jsonb,
 restored_decision_id uuid REFERENCES moderation_actions(id),
 created_at timestamptz NOT NULL DEFAULT now(),
 CHECK (((decision='APPEAL_RESTORE' AND assessments IS NULL AND restored_decision_id IS NOT NULL)
   OR (decision<>'APPEAL_RESTORE' AND restored_decision_id IS NULL
     AND jsonb_typeof(assessments)='object'
     AND assessments ?& ARRAY['originality','quality','license','execution']
     AND assessments - ARRAY['originality','quality','license','execution']='{}'::jsonb)) IS TRUE)
);
CREATE INDEX component_review_history ON component_review_assessments(component_id,created_at DESC,id);
CREATE TRIGGER component_review_assessments_immutable BEFORE UPDATE OR DELETE ON component_review_assessments
 FOR EACH ROW EXECUTE FUNCTION component_append_only();
ALTER TABLE component_review_assessments ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON component_review_assessments FROM PUBLIC;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='anon') THEN REVOKE ALL ON component_review_assessments FROM anon; END IF;
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN REVOKE ALL ON component_review_assessments FROM authenticated; END IF;
END $$;
