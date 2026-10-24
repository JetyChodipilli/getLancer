-- Current-MFA operator controls can tighten, never exceed reviewed hard ceilings.
ALTER TABLE lab_runtime_settings
 ADD COLUMN daily_run_limit integer NOT NULL DEFAULT 5 CHECK(daily_run_limit BETWEEN 1 AND 5),
 ADD COLUMN max_active_runs integer NOT NULL DEFAULT 10 CHECK(max_active_runs BETWEEN 1 AND 10),
 ADD COLUMN max_memory_mib integer NOT NULL DEFAULT 5120 CHECK(max_memory_mib BETWEEN 64 AND 5120),
 ADD COLUMN daily_budget_micros bigint NOT NULL DEFAULT 50000000 CHECK(daily_budget_micros BETWEEN 1 AND 50000000),
 ADD COLUMN monthly_budget_micros bigint NOT NULL DEFAULT 1000000000 CHECK(monthly_budget_micros BETWEEN 1 AND 1000000000),
 ADD CONSTRAINT lab_budget_order CHECK(monthly_budget_micros>=daily_budget_micros);
ALTER TABLE lab_runs ADD COLUMN observation_sequence bigint NOT NULL DEFAULT 0 CHECK(observation_sequence BETWEEN 0 AND 1000000);
ALTER TABLE lab_manifests ADD COLUMN execution_approved boolean NOT NULL DEFAULT true;
-- Private observations remain in redactable request responses, never immutable events.
ALTER TABLE lab_events ADD COLUMN command_id uuid REFERENCES lab_requests(command_id);
CREATE INDEX lab_events_command ON lab_events(command_id) WHERE command_id IS NOT NULL;
ALTER TABLE lab_operator_audit ADD COLUMN details jsonb NOT NULL DEFAULT '{}'::jsonb
 CHECK(jsonb_typeof(details)='object' AND octet_length(details::text)<=16384);
CREATE OR REPLACE FUNCTION lab_manifest_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' OR ROW(NEW.id,NEW.payload_text,NEW.payload_sha256,NEW.signature,NEW.execution_approved) IS DISTINCT FROM ROW(OLD.id,OLD.payload_text,OLD.payload_sha256,OLD.signature,OLD.execution_approved)
 OR (OLD.revoked_at IS NOT NULL AND NEW.revoked_at IS DISTINCT FROM OLD.revoked_at) THEN RAISE EXCEPTION 'Lab manifests are immutable; source-only and revocation are permanent'; END IF;
 RETURN NEW;
END $$;
CREATE FUNCTION lab_observation_cursor_fence() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.observation_sequence<OLD.observation_sequence THEN RAISE EXCEPTION 'Observed activity cursor is monotonic'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER lab_observation_cursor_fence BEFORE UPDATE ON lab_runs FOR EACH ROW EXECUTE FUNCTION lab_observation_cursor_fence();
