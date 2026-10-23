-- Mutable runtime observations remain separate from immutable source and licensing snapshots.
ALTER TABLE education_releases
 ADD COLUMN demo_health varchar(20) NOT NULL DEFAULT 'UNKNOWN'
   CHECK (demo_health IN ('UNKNOWN','REACHABLE','UNREACHABLE','BLOCKED')),
 ADD COLUMN demo_checked_at timestamptz,
 ADD COLUMN demo_checked_url text,
 ADD COLUMN demo_checked_source_hash varchar(64)
   CHECK (demo_checked_source_hash ~ '^[a-f0-9]{64}$');
CREATE INDEX education_demo_health_due ON education_releases(demo_checked_at,id)
 WHERE status='APPROVED' AND snapshot->>'demoMode'='EXTERNAL';
