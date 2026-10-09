-- Isolated lab control plane only. This migration installs no executable manifests or provider evidence.
CREATE TABLE lab_manifests (
 id varchar(80) PRIMARY KEY,payload_text text NOT NULL CHECK(octet_length(payload_text)<=65536),
 payload_sha256 varchar(64) NOT NULL CHECK(payload_sha256~'^[a-f0-9]{64}$'),signature text NOT NULL CHECK(length(signature)<=128),
 revoked_at timestamptz,created_at timestamptz NOT NULL DEFAULT now()
);
CREATE FUNCTION lab_manifest_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' OR ROW(NEW.id,NEW.payload_text,NEW.payload_sha256,NEW.signature) IS DISTINCT FROM ROW(OLD.id,OLD.payload_text,OLD.payload_sha256,OLD.signature)
 OR (OLD.revoked_at IS NOT NULL AND NEW.revoked_at IS DISTINCT FROM OLD.revoked_at) THEN RAISE EXCEPTION 'Lab manifests are immutable; revocation is permanent'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER lab_manifest_immutable BEFORE UPDATE OR DELETE ON lab_manifests FOR EACH ROW EXECUTE FUNCTION lab_manifest_immutable();
CREATE TABLE lab_runtime_settings (
 id integer PRIMARY KEY CHECK(id=1),paused boolean NOT NULL DEFAULT false,pause_reason varchar(500) NOT NULL DEFAULT '',
 observed_epoch uuid,updated_at timestamptz NOT NULL DEFAULT now()
);
INSERT INTO lab_runtime_settings(id) VALUES(1);
CREATE TABLE lab_runs (
 id uuid PRIMARY KEY,owner_id uuid NOT NULL REFERENCES users(id),manifest_id varchar(80) NOT NULL REFERENCES lab_manifests(id),
 scenario_id varchar(60) NOT NULL,request_hash varchar(64) NOT NULL,idempotency_key varchar(100) NOT NULL,
 inputs jsonb NOT NULL CHECK(jsonb_typeof(inputs)='object' AND octet_length(inputs::text)<=65536),
 manifest_sha256 varchar(64) NOT NULL,image_digest varchar(71) NOT NULL,operator_epoch uuid NOT NULL,
 lease_generation bigint NOT NULL DEFAULT 1 CHECK(lease_generation>0),
 status varchar(12) NOT NULL CHECK(status IN('QUEUED','STARTING','RUNNING','CANCELLING','CANCELLED','SUCCEEDED','FAILED','EXPIRED')),
 requested_at timestamptz NOT NULL DEFAULT now(),expires_at timestamptz NOT NULL,last_activity_at timestamptz NOT NULL DEFAULT now(),
 healthy_at timestamptz,cleanup_confirmed_at timestamptz,reason varchar(500) NOT NULL DEFAULT '',attention boolean NOT NULL DEFAULT false,
 memory_mib integer NOT NULL CHECK(memory_mib BETWEEN 64 AND 512),cost_micros bigint NOT NULL CHECK(cost_micros BETWEEN 1 AND 1000000),
 quota_counted boolean NOT NULL DEFAULT true,UNIQUE(owner_id,idempotency_key)
);
CREATE UNIQUE INDEX lab_one_active_account ON lab_runs(owner_id) WHERE cleanup_confirmed_at IS NULL;
CREATE INDEX lab_runs_watchdog ON lab_runs(expires_at) WHERE cleanup_confirmed_at IS NULL;
CREATE FUNCTION lab_run_fences() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF ROW(NEW.id,NEW.owner_id,NEW.manifest_id,NEW.scenario_id,NEW.request_hash,NEW.idempotency_key,NEW.manifest_sha256,NEW.image_digest,NEW.operator_epoch,NEW.requested_at,NEW.expires_at,NEW.memory_mib,NEW.cost_micros)
 IS DISTINCT FROM ROW(OLD.id,OLD.owner_id,OLD.manifest_id,OLD.scenario_id,OLD.request_hash,OLD.idempotency_key,OLD.manifest_sha256,OLD.image_digest,OLD.operator_epoch,OLD.requested_at,OLD.expires_at,OLD.memory_mib,OLD.cost_micros)
 OR (NEW.inputs IS DISTINCT FROM OLD.inputs AND NOT (OLD.cleanup_confirmed_at IS NOT NULL AND NEW.inputs='{}'::jsonb))
 OR NEW.lease_generation<OLD.lease_generation OR (OLD.healthy_at IS NOT NULL AND NEW.healthy_at IS DISTINCT FROM OLD.healthy_at)
 OR (OLD.cleanup_confirmed_at IS NOT NULL AND ROW(NEW.cleanup_confirmed_at,NEW.status,NEW.quota_counted) IS DISTINCT FROM ROW(OLD.cleanup_confirmed_at,OLD.status,OLD.quota_counted))
 OR (OLD.status IN('CANCELLING','CANCELLED','SUCCEEDED','FAILED','EXPIRED') AND NEW.status IN('QUEUED','STARTING','RUNNING'))
 THEN RAISE EXCEPTION 'Lab identity, lifetime and terminal fences are immutable'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER lab_run_fences BEFORE UPDATE ON lab_runs FOR EACH ROW EXECUTE FUNCTION lab_run_fences();
CREATE TABLE lab_outbox (
 command_id uuid PRIMARY KEY,run_id uuid NOT NULL REFERENCES lab_runs(id),lease_generation bigint NOT NULL,
 action varchar(10) NOT NULL CHECK(action IN('START','STOP')),delivered_at timestamptz,
 next_attempt_at timestamptz NOT NULL DEFAULT now(),attempts integer NOT NULL DEFAULT 0 CHECK(attempts>=0),UNIQUE(run_id,lease_generation,action)
);
CREATE FUNCTION lab_outbox_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF ROW(NEW.command_id,NEW.run_id,NEW.lease_generation,NEW.action) IS DISTINCT FROM ROW(OLD.command_id,OLD.run_id,OLD.lease_generation,OLD.action)
 OR (OLD.delivered_at IS NOT NULL AND NEW.delivered_at IS DISTINCT FROM OLD.delivered_at) OR NEW.attempts<OLD.attempts
 THEN RAISE EXCEPTION 'Lab outbox command identity and acknowledged delivery are immutable'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER lab_outbox_identity BEFORE UPDATE ON lab_outbox FOR EACH ROW EXECUTE FUNCTION lab_outbox_identity();
CREATE TABLE lab_requests (
 command_id uuid PRIMARY KEY,run_id uuid NOT NULL REFERENCES lab_runs(id),idempotency_key varchar(100) NOT NULL,
 request_hash varchar(64) NOT NULL,lease_generation bigint NOT NULL,operation_id varchar(60) NOT NULL,
 inputs jsonb NOT NULL CHECK(jsonb_typeof(inputs)='object' AND octet_length(inputs::text)<=65536),response jsonb,
 created_at timestamptz NOT NULL DEFAULT now(),UNIQUE(run_id,idempotency_key)
);
CREATE FUNCTION lab_request_fences() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF ROW(NEW.command_id,NEW.run_id,NEW.idempotency_key,NEW.request_hash,NEW.lease_generation,NEW.operation_id,NEW.created_at)
 IS DISTINCT FROM ROW(OLD.command_id,OLD.run_id,OLD.idempotency_key,OLD.request_hash,OLD.lease_generation,OLD.operation_id,OLD.created_at)
 OR (NEW.inputs IS DISTINCT FROM OLD.inputs AND NOT (NEW.inputs='{}'::jsonb AND EXISTS(SELECT 1 FROM lab_runs WHERE id=OLD.run_id AND cleanup_confirmed_at IS NOT NULL)))
 OR (OLD.response IS NOT NULL AND NEW.response IS DISTINCT FROM OLD.response AND NOT (NEW.response IS NULL AND EXISTS(SELECT 1 FROM lab_runs WHERE id=OLD.run_id AND cleanup_confirmed_at IS NOT NULL)))
 THEN RAISE EXCEPTION 'Lab request command identities and confirmed results are immutable'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER lab_request_fences BEFORE UPDATE ON lab_requests FOR EACH ROW EXECUTE FUNCTION lab_request_fences();
CREATE TABLE lab_events (
 run_id uuid NOT NULL REFERENCES lab_runs(id),sequence bigint NOT NULL CHECK(sequence>0),event_type varchar(30) NOT NULL,
 status varchar(12) NOT NULL,reason varchar(500) NOT NULL,recorded_at timestamptz NOT NULL DEFAULT now(),PRIMARY KEY(run_id,sequence)
);
CREATE TABLE lab_operator_audit (
 id uuid PRIMARY KEY,actor_id uuid REFERENCES users(id),action varchar(30) NOT NULL,reason varchar(500) NOT NULL,recorded_at timestamptz NOT NULL DEFAULT now()
);
CREATE TRIGGER lab_events_immutable BEFORE UPDATE OR DELETE ON lab_events FOR EACH ROW EXECUTE FUNCTION component_append_only();
CREATE TRIGGER lab_operator_audit_immutable BEFORE UPDATE OR DELETE ON lab_operator_audit FOR EACH ROW EXECUTE FUNCTION component_append_only();
ALTER TABLE lab_manifests ENABLE ROW LEVEL SECURITY;
ALTER TABLE lab_runtime_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE lab_runs ENABLE ROW LEVEL SECURITY;
ALTER TABLE lab_outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE lab_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE lab_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE lab_operator_audit ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON lab_manifests,lab_runtime_settings,lab_runs,lab_outbox,lab_requests,lab_events,lab_operator_audit FROM PUBLIC;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='anon') THEN REVOKE ALL ON lab_manifests,lab_runtime_settings,lab_runs,lab_outbox,lab_requests,lab_events,lab_operator_audit FROM anon; END IF;
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN REVOKE ALL ON lab_manifests,lab_runtime_settings,lab_runs,lab_outbox,lab_requests,lab_events,lab_operator_audit FROM authenticated; END IF;
END $$;
