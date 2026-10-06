CREATE TABLE security_audit_events (
    id uuid PRIMARY KEY,
    -- Deliberately no FK: failed auth events use REQUIRES_NEW while auth may lock the user row.
    -- A UUID identifier preserves history after privacy/anonymization processing.
    actor_id uuid,
    event varchar(40) NOT NULL,
    target varchar(180) NOT NULL DEFAULT '',
    result varchar(16) NOT NULL CHECK (result IN ('SUCCESS','FAILURE','LOCKED')),
    request_id varchar(36) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX security_audit_events_time_idx ON security_audit_events(created_at);
CREATE INDEX security_audit_events_actor_idx ON security_audit_events(actor_id,created_at);
COMMENT ON TABLE security_audit_events IS 'Append-only security events; runtime grants exclude UPDATE and DELETE. Operator retention uses the maintenance role.';
