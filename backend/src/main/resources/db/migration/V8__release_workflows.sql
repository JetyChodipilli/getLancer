CREATE TABLE media_uploads (
 id uuid PRIMARY KEY, product_id uuid NOT NULL REFERENCES products,
 storage_key text NOT NULL UNIQUE, content_type varchar(50) NOT NULL,
 size_bytes bigint NOT NULL CHECK(size_bytes BETWEEN 1 AND 5242880),
 expires_at timestamptz NOT NULL, completed_media_id uuid REFERENCES product_media ON DELETE SET NULL,
 created_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE inquiries ADD COLUMN reported_value numeric(14,2) CHECK(reported_value >= 0);
ALTER TABLE inquiries ADD COLUMN reported_currency varchar(3);
ALTER TABLE inquiries ADD CONSTRAINT value_currency_pair CHECK((reported_value IS NULL)=(reported_currency IS NULL));
ALTER TABLE reports ADD COLUMN severity varchar(12) NOT NULL DEFAULT 'MEDIUM' CHECK(severity IN ('CRITICAL','HIGH','MEDIUM','LOW'));
ALTER TABLE reports ADD COLUMN triage_note varchar(2000);
ALTER TABLE reports ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now();
CREATE INDEX reports_triage ON reports(status,severity,created_at);
CREATE TABLE moderation_appeals (
 id uuid PRIMARY KEY, decision_id uuid NOT NULL REFERENCES moderation_actions,
 appellant_id uuid NOT NULL REFERENCES users, statement varchar(3000) NOT NULL,
 evidence_url text, status varchar(20) NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','UNDER_REVIEW','UPHELD','OVERTURNED')),
 resolution varchar(2000), reviewer_id uuid REFERENCES users,
 created_at timestamptz NOT NULL DEFAULT now(), resolved_at timestamptz,
 UNIQUE(decision_id,appellant_id)
);
ALTER TABLE analytics_events ADD COLUMN context jsonb NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE analytics_events ADD COLUMN session_hash varchar(64);
ALTER TABLE analytics_events ADD COLUMN occurred_at timestamptz;
ALTER TABLE analytics_events ADD COLUMN source varchar(20) NOT NULL DEFAULT 'server';
ALTER TABLE inquiries ADD COLUMN acquisition_source varchar(30);
ALTER TABLE deletion_requests ADD COLUMN resolution text;
ALTER TABLE deletion_requests ADD COLUMN processed_at timestamptz;
ALTER TABLE deletion_requests ADD COLUMN processed_by uuid REFERENCES users;
ALTER TABLE product_media ADD COLUMN thumbnail_key text;
ALTER TABLE product_media ADD COLUMN width int;
ALTER TABLE product_media ADD COLUMN height int;
