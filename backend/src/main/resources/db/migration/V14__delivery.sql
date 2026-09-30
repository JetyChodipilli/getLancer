CREATE TABLE delivery_engagements(
 id uuid PRIMARY KEY,source_inquiry_id uuid REFERENCES inquiries,business_request_id uuid REFERENCES business_requests,
 buyer_user_id uuid REFERENCES users,business_id uuid REFERENCES businesses,builder_user_id uuid REFERENCES users,team_id uuid REFERENCES teams,
 title varchar(120) NOT NULL,created_by uuid NOT NULL REFERENCES users,
 status varchar(30) NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','ACTIVE','COMPLETION_PENDING','COMPLETED','DISPUTED','CANCELLED')),
 created_at timestamptz NOT NULL DEFAULT now(),updated_at timestamptz NOT NULL DEFAULT now(),
 CHECK((source_inquiry_id IS NOT NULL AND business_request_id IS NULL AND buyer_user_id IS NOT NULL AND business_id IS NULL AND builder_user_id IS NOT NULL AND team_id IS NULL)
 OR (source_inquiry_id IS NULL AND business_request_id IS NOT NULL AND buyer_user_id IS NULL AND business_id IS NOT NULL)),
 CHECK((builder_user_id IS NOT NULL)::int+(team_id IS NOT NULL)::int=1),
 CHECK(buyer_user_id IS NULL OR buyer_user_id<>builder_user_id),
 UNIQUE(source_inquiry_id),UNIQUE(business_request_id,builder_user_id),UNIQUE(business_request_id,team_id)
);
CREATE INDEX delivery_engagements_business ON delivery_engagements(business_id,created_at DESC);
CREATE INDEX delivery_engagements_buyer ON delivery_engagements(buyer_user_id,created_at DESC);
CREATE INDEX delivery_engagements_builder ON delivery_engagements(builder_user_id,created_at DESC);
CREATE INDEX delivery_engagements_team ON delivery_engagements(team_id,created_at DESC);
CREATE TABLE delivery_proposals(
 id uuid PRIMARY KEY,engagement_id uuid NOT NULL REFERENCES delivery_engagements,revision integer NOT NULL CHECK(revision>0),
 status varchar(20) NOT NULL DEFAULT 'DRAFT' CHECK(status IN ('DRAFT','SENT','WITHDRAWN','REJECTED','ACCEPTED')),
 scope varchar(10000) NOT NULL,terms varchar(5000) NOT NULL,amount_minor bigint NOT NULL CHECK(amount_minor>=100 AND amount_minor<=1000000000),
 currency varchar(3) NOT NULL DEFAULT 'INR' CHECK(currency='INR'),milestones jsonb NOT NULL CHECK(jsonb_typeof(milestones)='array'),
 seller_consented_by uuid REFERENCES users,sent_at timestamptz,buyer_consented_by uuid REFERENCES users,accepted_at timestamptz,created_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(engagement_id,revision),UNIQUE(engagement_id,id),CHECK((sent_at IS NULL)=(seller_consented_by IS NULL)),CHECK((accepted_at IS NULL)=(buyer_consented_by IS NULL))
);
CREATE UNIQUE INDEX delivery_active_offer ON delivery_proposals(engagement_id) WHERE status IN ('DRAFT','SENT','ACCEPTED');
CREATE TABLE delivery_agreements(
 engagement_id uuid PRIMARY KEY REFERENCES delivery_engagements,proposal_id uuid NOT NULL UNIQUE REFERENCES delivery_proposals,
 scope varchar(10000) NOT NULL,terms varchar(5000) NOT NULL,amount_minor bigint NOT NULL CHECK(amount_minor>=100 AND amount_minor<=1000000000),
 currency varchar(3) NOT NULL CHECK(currency='INR'),milestones jsonb NOT NULL,digest varchar(64) NOT NULL,
 seller_consented_by uuid NOT NULL REFERENCES users,seller_consented_at timestamptz NOT NULL,buyer_consented_by uuid NOT NULL REFERENCES users,buyer_consented_at timestamptz NOT NULL,
 FOREIGN KEY(engagement_id,proposal_id) REFERENCES delivery_proposals(engagement_id,id)
);
CREATE TABLE delivery_milestones(
 id uuid PRIMARY KEY,engagement_id uuid NOT NULL REFERENCES delivery_engagements,ordinal integer NOT NULL CHECK(ordinal>0),
 title varchar(120) NOT NULL,description varchar(3000) NOT NULL,amount_minor bigint NOT NULL CHECK(amount_minor>=100 AND amount_minor<=1000000000),
 currency varchar(3) NOT NULL DEFAULT 'INR' CHECK(currency='INR'),due_date date NOT NULL,
 status varchar(30) NOT NULL DEFAULT 'PLANNED' CHECK(status IN ('PLANNED','IN_PROGRESS','SUBMITTED','REVISION_REQUESTED','ACCEPTED')),
 delivery_note varchar(5000),delivery_url varchar(2000),accepted_by uuid REFERENCES users,accepted_at timestamptz,updated_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(engagement_id,ordinal),UNIQUE(engagement_id,id),CHECK((accepted_at IS NULL)=(accepted_by IS NULL))
);
CREATE TABLE delivery_activity(id uuid PRIMARY KEY,engagement_id uuid NOT NULL REFERENCES delivery_engagements,actor_id uuid REFERENCES users,kind varchar(60) NOT NULL,detail varchar(3000) NOT NULL,created_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX delivery_activity_engagement ON delivery_activity(engagement_id,created_at DESC);
CREATE TABLE delivery_disputes(
 id uuid PRIMARY KEY,engagement_id uuid NOT NULL REFERENCES delivery_engagements,opened_by uuid NOT NULL REFERENCES users,reason varchar(3000) NOT NULL,
 status varchar(20) NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','RESOLVED')),previous_status varchar(30) NOT NULL CHECK(previous_status IN ('OPEN','ACTIVE','COMPLETION_PENDING','COMPLETED')),
 resolution varchar(10) CHECK(resolution IN ('RESUME','CANCEL')),resolved_by uuid REFERENCES users,resolution_reason varchar(3000),created_at timestamptz NOT NULL DEFAULT now(),resolved_at timestamptz,
 CHECK((status='OPEN' AND resolution IS NULL AND resolved_at IS NULL AND resolved_by IS NULL) OR (status='RESOLVED' AND resolution IS NOT NULL AND resolved_at IS NOT NULL AND resolved_by IS NOT NULL))
);
CREATE UNIQUE INDEX delivery_open_dispute ON delivery_disputes(engagement_id) WHERE status='OPEN';
CREATE FUNCTION delivery_immutable_agreement() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Accepted delivery agreements are immutable'; END $$;
CREATE TRIGGER delivery_agreement_immutable BEFORE UPDATE OR DELETE ON delivery_agreements FOR EACH ROW EXECUTE FUNCTION delivery_immutable_agreement();
CREATE FUNCTION delivery_immutable_offer() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF OLD.status='ACCEPTED' AND NEW IS DISTINCT FROM OLD THEN RAISE EXCEPTION 'Accepted delivery proposals are immutable'; END IF;
 IF OLD.status<>'DRAFT' AND NEW.status='DRAFT' THEN RAISE EXCEPTION 'Proposal revisions cannot be reset to draft'; END IF;
 IF OLD.status<>'DRAFT' AND (NEW.scope,NEW.terms,NEW.amount_minor,NEW.currency,NEW.milestones,NEW.revision,NEW.engagement_id,NEW.seller_consented_by,NEW.sent_at) IS DISTINCT FROM (OLD.scope,OLD.terms,OLD.amount_minor,OLD.currency,OLD.milestones,OLD.revision,OLD.engagement_id,OLD.seller_consented_by,OLD.sent_at) THEN RAISE EXCEPTION 'Sent delivery offers are immutable'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER delivery_offer_immutable BEFORE UPDATE ON delivery_proposals FOR EACH ROW EXECUTE FUNCTION delivery_immutable_offer();
CREATE FUNCTION delivery_immutable_activity() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Delivery activity is append-only'; END $$;
CREATE TRIGGER delivery_activity_immutable BEFORE UPDATE OR DELETE ON delivery_activity FOR EACH ROW EXECUTE FUNCTION delivery_immutable_activity();
DO $$ DECLARE t text; r text; BEGIN
 FOREACH t IN ARRAY ARRAY['delivery_engagements','delivery_proposals','delivery_agreements','delivery_milestones','delivery_activity','delivery_disputes'] LOOP
  EXECUTE format('ALTER TABLE %I.%I ENABLE ROW LEVEL SECURITY',current_schema(),t);
  EXECUTE format('REVOKE ALL ON TABLE %I.%I FROM PUBLIC',current_schema(),t);
  FOREACH r IN ARRAY ARRAY['anon','authenticated'] LOOP
   IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname=r) THEN EXECUTE format('REVOKE ALL ON TABLE %I.%I FROM %I',current_schema(),t,r); END IF;
  END LOOP;
 END LOOP;
END $$;
