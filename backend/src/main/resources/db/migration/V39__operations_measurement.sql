-- No provider/runtime is enabled by measurement. Raw web observations remain optional and short lived.
INSERT INTO analytics_instrumentation(name) VALUES('v52_operations') ON CONFLICT DO NOTHING;
CREATE TABLE analytics_daily_counts (
 day date NOT NULL, stage varchar(60) NOT NULL CHECK(stage ~ '^[a-z_]{1,60}$'),
 mode varchar(20) NOT NULL CHECK(mode IN('ALL','PROJECT','BROWSER_LOCAL','SOURCE_ONLY','LIVE','TEST')),
 version varchar(80) NOT NULL CHECK(version ~ '^[A-Za-z0-9._-]{1,80}$'),
 observations bigint NOT NULL CHECK(observations>=0), PRIMARY KEY(day,stage,mode,version)
);
ALTER TABLE analytics_daily_counts ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON analytics_daily_counts FROM PUBLIC;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='anon') THEN REVOKE ALL ON analytics_daily_counts FROM anon; END IF;
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN REVOKE ALL ON analytics_daily_counts FROM authenticated; END IF;
END $$;
CREATE INDEX lab_runs_measurement ON lab_runs(requested_at,manifest_sha256);
CREATE INDEX commerce_measurement_window ON template_purchases(created_at,mode,sha256);
CREATE INDEX analytics_web_retention ON analytics_events(created_at) WHERE source='web';
ALTER TABLE lab_runtime_settings ADD COLUMN worker_observed_at timestamptz;
ALTER TABLE lab_requests ADD COLUMN confirmed_failure boolean NOT NULL DEFAULT false;
ALTER TABLE analytics_instrumentation ADD COLUMN quota_denials bigint NOT NULL DEFAULT 0 CHECK(quota_denials>=0);
ALTER TABLE analytics_instrumentation ADD COLUMN validation_denials bigint NOT NULL DEFAULT 0 CHECK(validation_denials>=0);
CREATE FUNCTION analytics_counter_fences() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF ROW(NEW.name,NEW.started_at) IS DISTINCT FROM ROW(OLD.name,OLD.started_at)
   OR NEW.quota_denials<OLD.quota_denials OR NEW.validation_denials<OLD.validation_denials
   OR (OLD.name<>'v52_operations' AND ROW(NEW.quota_denials,NEW.validation_denials) IS DISTINCT FROM ROW(OLD.quota_denials,OLD.validation_denials))
 THEN RAISE EXCEPTION 'Instrumentation epochs are immutable and operational counters cannot decrease'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER analytics_counter_fences BEFORE UPDATE ON analytics_instrumentation FOR EACH ROW EXECUTE FUNCTION analytics_counter_fences();
CREATE FUNCTION lab_request_failure_fences() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.confirmed_failure AND NOT NEW.confirmed_failure THEN
   RAISE EXCEPTION 'Confirmed scenario failure evidence cannot be withdrawn';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER lab_request_failure_fences BEFORE UPDATE ON lab_requests FOR EACH ROW EXECUTE FUNCTION lab_request_failure_fences();
REVOKE ALL ON FUNCTION analytics_counter_fences() FROM PUBLIC;
REVOKE ALL ON FUNCTION lab_request_failure_fences() FROM PUBLIC;
