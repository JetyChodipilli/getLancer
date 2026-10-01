-- Historical observations stay unknown. New metrics begin at this migration.
CREATE TABLE analytics_instrumentation(name varchar(60) PRIMARY KEY,started_at timestamptz NOT NULL DEFAULT now());
INSERT INTO analytics_instrumentation(name) VALUES('showcase_capacity');
ALTER TABLE analytics_instrumentation ENABLE ROW LEVEL SECURITY;
ALTER TABLE reports ADD COLUMN enforcement_action varchar(20);
ALTER TABLE reports ADD CONSTRAINT report_enforcement_action CHECK(enforcement_action IS NULL OR enforcement_action IN ('NONE','SUSPEND','HIDE','QUARANTINE','BLOCK','RESTORE'));
