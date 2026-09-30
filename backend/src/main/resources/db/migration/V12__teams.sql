CREATE TABLE teams(id uuid PRIMARY KEY,slug varchar(180) NOT NULL UNIQUE,name varchar(120) NOT NULL,summary varchar(3000) NOT NULL,availability varchar(60) NOT NULL,project_range varchar(200) NOT NULL,owner_id uuid NOT NULL REFERENCES users,status varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','SUSPENDED')),created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE team_members(team_id uuid REFERENCES teams,user_id uuid REFERENCES users,role varchar(30) NOT NULL CHECK(role IN ('OWNER','BUSINESS_MANAGER','RECRUITER','PROJECT_MANAGER','MEMBER')),membership_type varchar(20) NOT NULL CHECK(membership_type IN ('PERMANENT','CONTRACT')),expires_at timestamptz,project_label varchar(200),PRIMARY KEY(team_id,user_id),CHECK(membership_type<>'CONTRACT' OR expires_at IS NOT NULL),CHECK(role<>'OWNER' OR (membership_type='PERMANENT' AND expires_at IS NULL)));
CREATE INDEX team_members_user ON team_members(user_id);
CREATE TABLE team_invitations(id uuid PRIMARY KEY,team_id uuid NOT NULL REFERENCES teams,user_id uuid NOT NULL REFERENCES users,role varchar(30) NOT NULL CHECK(role IN ('BUSINESS_MANAGER','RECRUITER','PROJECT_MANAGER','MEMBER')),membership_type varchar(20) NOT NULL CHECK(membership_type IN ('PERMANENT','CONTRACT')),expires_at timestamptz,respond_by timestamptz NOT NULL DEFAULT now()+interval '14 days',project_label varchar(200),status varchar(20) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','ACCEPTED','DECLINED','EXPIRED')),created_at timestamptz NOT NULL DEFAULT now(),CHECK(membership_type<>'CONTRACT' OR expires_at IS NOT NULL));
CREATE UNIQUE INDEX team_pending_invitation ON team_invitations(team_id,user_id) WHERE status='PENDING';
CREATE TABLE team_roles(id uuid PRIMARY KEY,team_id uuid NOT NULL REFERENCES teams,title varchar(120) NOT NULL,description varchar(5000) NOT NULL,skills varchar(500) NOT NULL,contract_type varchar(30) NOT NULL CHECK(contract_type IN ('PERMANENT','CONTRACT')),compensation_band varchar(200) NOT NULL,status varchar(20) NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','CLOSED')),created_at timestamptz NOT NULL DEFAULT now(),UNIQUE(team_id,id));
CREATE TABLE team_applications(id uuid PRIMARY KEY,team_id uuid NOT NULL REFERENCES teams,role_id uuid NOT NULL,user_id uuid NOT NULL REFERENCES users,message varchar(3000) NOT NULL,status varchar(20) NOT NULL DEFAULT 'APPLIED' CHECK(status IN ('APPLIED','SHORTLISTED','REJECTED','INVITED')),created_at timestamptz NOT NULL DEFAULT now(),FOREIGN KEY(team_id,role_id) REFERENCES team_roles(team_id,id),UNIQUE(role_id,user_id));
CREATE TABLE team_leads(id uuid PRIMARY KEY,team_id uuid NOT NULL REFERENCES teams,client_id uuid NOT NULL REFERENCES users,title varchar(120) NOT NULL,description varchar(5000) NOT NULL,budget varchar(200) NOT NULL,timeline varchar(200) NOT NULL,status varchar(30) NOT NULL DEFAULT 'NEW' CHECK(status IN ('NEW','INTERESTED','NEEDS_INFORMATION','DECLINED','PROPOSAL_SENT','WON','LOST')),assignee_id uuid REFERENCES users,follow_up_at timestamptz,created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE team_lead_notes(id uuid PRIMARY KEY,lead_id uuid NOT NULL REFERENCES team_leads,actor_id uuid NOT NULL REFERENCES users,body varchar(3000) NOT NULL,created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE team_projects(team_id uuid REFERENCES teams,product_id uuid REFERENCES products,consented_by uuid NOT NULL REFERENCES users,created_at timestamptz NOT NULL DEFAULT now(),PRIMARY KEY(team_id,product_id));
CREATE TABLE team_staffing(id uuid PRIMARY KEY,team_id uuid NOT NULL REFERENCES teams,user_id uuid NOT NULL REFERENCES users,project_label varchar(200) NOT NULL,skills varchar(500) NOT NULL,ends_at timestamptz NOT NULL,status varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','COMPLETED')),created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE team_activity(id uuid PRIMARY KEY,team_id uuid NOT NULL REFERENCES teams,actor_id uuid NOT NULL REFERENCES users,action varchar(60) NOT NULL,detail varchar(3000) NOT NULL,scope varchar(20) NOT NULL DEFAULT 'owner' CHECK(scope IN ('owner','recruit','commercial','staff','member')),created_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX team_leads_team ON team_leads(team_id,created_at DESC);
CREATE INDEX team_activity_team ON team_activity(team_id,created_at DESC);
CREATE INDEX team_invitations_user ON team_invitations(user_id);
CREATE INDEX team_applications_user ON team_applications(user_id);

-- Team data is served only through the authenticated application boundary.
-- Supabase Data API roles must never expose private CRM/recruitment records.
DO $$
DECLARE t text; r text;
BEGIN
 FOREACH t IN ARRAY ARRAY['teams','team_members','team_invitations','team_roles','team_applications','team_leads','team_lead_notes','team_projects','team_staffing','team_activity'] LOOP
  EXECUTE format('ALTER TABLE %I.%I ENABLE ROW LEVEL SECURITY',current_schema(),t);
  EXECUTE format('REVOKE ALL ON TABLE %I.%I FROM PUBLIC',current_schema(),t);
  FOREACH r IN ARRAY ARRAY['anon','authenticated'] LOOP
   IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname=r) THEN
    EXECUTE format('REVOKE ALL ON TABLE %I.%I FROM %I',current_schema(),t,r);
   END IF;
  END LOOP;
 END LOOP;
END $$;
