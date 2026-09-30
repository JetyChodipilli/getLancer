CREATE TABLE businesses(id uuid PRIMARY KEY,name varchar(120) NOT NULL,summary varchar(2000) NOT NULL,owner_id uuid NOT NULL REFERENCES users,created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE business_members(business_id uuid REFERENCES businesses,user_id uuid REFERENCES users,role varchar(30) NOT NULL CHECK(role IN ('OWNER','HIRING_MANAGER')),PRIMARY KEY(business_id,user_id));
CREATE INDEX business_members_user ON business_members(user_id);
CREATE TABLE business_invitations(id uuid PRIMARY KEY,business_id uuid NOT NULL REFERENCES businesses,user_id uuid NOT NULL REFERENCES users,status varchar(20) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','ACCEPTED','DECLINED','EXPIRED')),expires_at timestamptz NOT NULL DEFAULT now()+interval '14 days',created_at timestamptz NOT NULL DEFAULT now());
CREATE UNIQUE INDEX business_pending_invite ON business_invitations(business_id,user_id) WHERE status='PENDING';
CREATE TABLE business_requests(id uuid PRIMARY KEY,business_id uuid NOT NULL REFERENCES businesses,created_by uuid NOT NULL REFERENCES users,title varchar(120) NOT NULL,description varchar(5000) NOT NULL,category varchar(100) NOT NULL,technology varchar(300) NOT NULL,budget varchar(200) NOT NULL,timeline varchar(200) NOT NULL,available_only boolean NOT NULL DEFAULT false,repository_verified_only boolean NOT NULL DEFAULT false,status varchar(20) NOT NULL DEFAULT 'DRAFT' CHECK(status IN ('DRAFT','OPEN','CLOSED')),created_at timestamptz NOT NULL DEFAULT now(),updated_at timestamptz NOT NULL DEFAULT now(),UNIQUE(business_id,id));
CREATE INDEX business_requests_business ON business_requests(business_id,created_at DESC);
CREATE TABLE talent_lists(id uuid PRIMARY KEY,business_id uuid NOT NULL REFERENCES businesses,name varchar(120) NOT NULL,created_by uuid NOT NULL REFERENCES users,created_at timestamptz NOT NULL DEFAULT now(),UNIQUE(business_id,id));
CREATE TABLE talent_entries(id uuid PRIMARY KEY,list_id uuid NOT NULL REFERENCES talent_lists,kind varchar(10) NOT NULL CHECK(kind IN ('BUILDER','TEAM')),builder_id uuid REFERENCES users,team_id uuid REFERENCES teams,created_by uuid NOT NULL REFERENCES users,created_at timestamptz NOT NULL DEFAULT now(),CHECK((kind='BUILDER' AND builder_id IS NOT NULL AND team_id IS NULL) OR (kind='TEAM' AND team_id IS NOT NULL AND builder_id IS NULL)),UNIQUE(list_id,builder_id),UNIQUE(list_id,team_id));
CREATE TABLE request_shortlist(id uuid PRIMARY KEY,request_id uuid NOT NULL REFERENCES business_requests,kind varchar(10) NOT NULL CHECK(kind IN ('BUILDER','TEAM')),builder_id uuid REFERENCES users,team_id uuid REFERENCES teams,reason varchar(2000) NOT NULL,source varchar(20) NOT NULL CHECK(source IN ('BUSINESS','CONCIERGE')),created_by uuid NOT NULL REFERENCES users,created_at timestamptz NOT NULL DEFAULT now(),CHECK((kind='BUILDER' AND builder_id IS NOT NULL AND team_id IS NULL) OR (kind='TEAM' AND team_id IS NOT NULL AND builder_id IS NULL)),UNIQUE(request_id,builder_id),UNIQUE(request_id,team_id));
CREATE TABLE concierge_requests(id uuid PRIMARY KEY,request_id uuid NOT NULL UNIQUE REFERENCES business_requests,requested_by uuid NOT NULL REFERENCES users,status varchar(20) NOT NULL DEFAULT 'REQUESTED' CHECK(status IN ('REQUESTED','IN_PROGRESS','FULFILLED','CANCELLED')),created_at timestamptz NOT NULL DEFAULT now(),updated_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE business_activity(id uuid PRIMARY KEY,business_id uuid NOT NULL REFERENCES businesses,actor_id uuid NOT NULL REFERENCES users,action varchar(60) NOT NULL,detail varchar(2000) NOT NULL,created_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX business_activity_business ON business_activity(business_id,created_at DESC);
DO $$
DECLARE t text; r text;
BEGIN
 FOREACH t IN ARRAY ARRAY['businesses','business_members','business_invitations','business_requests','talent_lists','talent_entries','request_shortlist','concierge_requests','business_activity'] LOOP
  EXECUTE format('ALTER TABLE %I.%I ENABLE ROW LEVEL SECURITY',current_schema(),t);
  EXECUTE format('REVOKE ALL ON TABLE %I.%I FROM PUBLIC',current_schema(),t);
  FOREACH r IN ARRAY ARRAY['anon','authenticated'] LOOP
   IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname=r) THEN EXECUTE format('REVOKE ALL ON TABLE %I.%I FROM %I',current_schema(),t,r); END IF;
  END LOOP;
 END LOOP;
END $$;
