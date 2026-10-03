CREATE TABLE maintenance_offers (
 id uuid PRIMARY KEY, engagement_id uuid NOT NULL REFERENCES delivery_engagements,
 revision integer NOT NULL CHECK(revision>0), status varchar(16) NOT NULL DEFAULT 'DRAFT' CHECK(status IN ('DRAFT','OFFERED','ACCEPTED','WITHDRAWN','REJECTED')),
 title varchar(100) NOT NULL CHECK(length(title)>=3), scope text NOT NULL CHECK(length(scope) BETWEEN 20 AND 4000),
 terms text NOT NULL CHECK(length(terms) BETWEEN 40 AND 8000), amount_minor bigint NOT NULL CHECK(amount_minor BETWEEN 100 AND 1000000000),
 currency varchar(3) NOT NULL DEFAULT 'INR' CHECK(currency='INR'), requests_per_cycle integer NOT NULL CHECK(requests_per_cycle BETWEEN 1 AND 50),
 response_hours integer NOT NULL CHECK(response_hours BETWEEN 1 AND 720), total_cycles integer NOT NULL CHECK(total_cycles BETWEEN 1 AND 12),
 seller_id uuid REFERENCES users, seller_consented_at timestamptz, buyer_id uuid REFERENCES users, buyer_consented_at timestamptz,
 digest char(64), created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(engagement_id,revision), UNIQUE(engagement_id,id),
 CHECK(status NOT IN ('OFFERED','ACCEPTED') OR (seller_id IS NOT NULL AND seller_consented_at IS NOT NULL AND digest IS NOT NULL)),
 CHECK(status<>'ACCEPTED' OR (buyer_id IS NOT NULL AND buyer_consented_at IS NOT NULL))
);
CREATE UNIQUE INDEX maintenance_open_offer ON maintenance_offers(engagement_id) WHERE status IN ('DRAFT','OFFERED');
CREATE TABLE maintenance_subscriptions (
 id uuid PRIMARY KEY, engagement_id uuid NOT NULL, offer_id uuid NOT NULL UNIQUE,
 payer_id uuid NOT NULL REFERENCES users, request_key uuid NOT NULL, amount_minor bigint NOT NULL CHECK(amount_minor BETWEEN 100 AND 1000000000),
 currency varchar(3) NOT NULL CHECK(currency='INR'), requests_per_cycle integer NOT NULL CHECK(requests_per_cycle BETWEEN 1 AND 50),
 response_hours integer NOT NULL CHECK(response_hours BETWEEN 1 AND 720), total_cycles integer NOT NULL CHECK(total_cycles BETWEEN 1 AND 12),
 digest char(64) NOT NULL, account_id varchar(40) NOT NULL, mode varchar(4) NOT NULL CHECK(mode IN ('test','live')),
 status varchar(32) NOT NULL DEFAULT 'CREATING', creation_step varchar(16) NOT NULL DEFAULT 'PLAN' CHECK(creation_step IN ('PLAN','SUBSCRIPTION','COMPLETE')),
 plan_state varchar(16) NOT NULL DEFAULT 'CREATING' CHECK(plan_state IN ('CREATING','UNKNOWN','REJECTED','CONFIRMED')),
 subscription_state varchar(16) CHECK(subscription_state IN ('NOT_CREATED','CREATING','UNKNOWN','REJECTED','CONFIRMED')),
 provider_plan_id varchar(40) UNIQUE, provider_subscription_id varchar(40) UNIQUE,
 attention_reason varchar(240), local_hold boolean NOT NULL DEFAULT false, reconcile_generation bigint NOT NULL DEFAULT 0, reconciled_at timestamptz,
 cancel_requested boolean NOT NULL DEFAULT false, cancel_confirmed boolean NOT NULL DEFAULT false,
 cancel_state varchar(16) CHECK(cancel_state IN ('CREATING','UNKNOWN','REJECTED','CONFIRMED')),
 billing_consented_at timestamptz NOT NULL DEFAULT now(), created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(payer_id,request_key), FOREIGN KEY(engagement_id,offer_id) REFERENCES maintenance_offers(engagement_id,id)
);
CREATE INDEX maintenance_subscriptions_engagement ON maintenance_subscriptions(engagement_id);
CREATE TABLE maintenance_periods (
 id uuid PRIMARY KEY, subscription_id uuid NOT NULL REFERENCES maintenance_subscriptions,
 provider_invoice_id varchar(40) NOT NULL UNIQUE, payment_id varchar(40) NOT NULL UNIQUE, order_id varchar(40) NOT NULL,
 amount_minor bigint NOT NULL CHECK(amount_minor BETWEEN 100 AND 1000000000), currency varchar(3) NOT NULL CHECK(currency='INR'),
 period_start timestamptz NOT NULL, period_end timestamptz NOT NULL CHECK(period_end>period_start),
 status varchar(16) NOT NULL DEFAULT 'PAID' CHECK(status IN ('PAID','HELD','REFUNDED')),
 refunded_minor bigint NOT NULL DEFAULT 0 CHECK(refunded_minor BETWEEN 0 AND amount_minor), pending_refund boolean NOT NULL DEFAULT false,
 transfer_key uuid NOT NULL UNIQUE, transfer_state varchar(16) NOT NULL DEFAULT 'NOT_CREATED' CHECK(transfer_state IN ('NOT_CREATED','CREATING','UNKNOWN','REJECTED','CONFIRMED')),
 transfer_id varchar(40) UNIQUE, reversed_minor bigint NOT NULL DEFAULT 0 CHECK(reversed_minor BETWEEN 0 AND amount_minor), transfer_status varchar(32), dispute_status varchar(32), attention_reason varchar(240),
 reconciled_at timestamptz, created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(subscription_id,period_start), UNIQUE(subscription_id,id)
);
CREATE TABLE maintenance_requests (
 id uuid PRIMARY KEY, subscription_id uuid NOT NULL, period_id uuid NOT NULL,
 actor_id uuid NOT NULL REFERENCES users, request_key uuid NOT NULL, title varchar(100) NOT NULL CHECK(length(title)>=3),
 description text NOT NULL CHECK(length(description) BETWEEN 20 AND 4000), status varchar(24) NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','IN_PROGRESS','SUBMITTED','RESOLVED','REVISION_REQUESTED','CANCELLED')),
 delivery_note text, delivery_url varchar(500), first_response_at timestamptz, response_due_at timestamptz NOT NULL,
 resolved_at timestamptz, created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(actor_id,request_key),
 FOREIGN KEY(subscription_id,period_id) REFERENCES maintenance_periods(subscription_id,id)
);
CREATE INDEX maintenance_requests_period ON maintenance_requests(period_id);
CREATE TABLE maintenance_provider_disputes (
 id varchar(40) PRIMARY KEY, period_id uuid NOT NULL REFERENCES maintenance_periods,
 status varchar(32) NOT NULL, deducted_minor bigint NOT NULL CHECK(deducted_minor>=0), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE maintenance_refunds (
 id varchar(40) PRIMARY KEY, period_id uuid NOT NULL REFERENCES maintenance_periods,
 amount_minor bigint NOT NULL CHECK(amount_minor>0), status varchar(16) NOT NULL CHECK(status IN ('pending','processed','failed')),
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE maintenance_ledger (
 id uuid PRIMARY KEY, period_id uuid NOT NULL REFERENCES maintenance_periods, entry_key varchar(100) NOT NULL,
 kind varchar(16) NOT NULL CHECK(kind IN ('CAPTURE','REFUND','TRANSFER')), amount_minor bigint NOT NULL CHECK(amount_minor>0),
 currency varchar(3) NOT NULL DEFAULT 'INR' CHECK(currency='INR'), created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(period_id,entry_key)
);
CREATE TABLE maintenance_webhook_events (
 event_id varchar(100) PRIMARY KEY, payload_hash char(64) NOT NULL, event_kind varchar(100) NOT NULL,
 subscription_id varchar(40), payment_id varchar(40), dispute_id varchar(40), processed_at timestamptz,
 received_at timestamptz NOT NULL DEFAULT now(), attempts integer NOT NULL DEFAULT 0 CHECK(attempts>=0)
);
CREATE TABLE maintenance_audit (
 id uuid PRIMARY KEY, offer_id uuid NOT NULL REFERENCES maintenance_offers, actor_id uuid REFERENCES users,
 kind varchar(50) NOT NULL, detail text NOT NULL CHECK(length(detail)<=2000), created_at timestamptz NOT NULL DEFAULT now()
);
CREATE FUNCTION maintenance_retained_offer() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Maintenance agreements are retained'; END IF;
 IF (NEW.id,NEW.engagement_id,NEW.revision,NEW.title,NEW.scope,NEW.terms,NEW.amount_minor,NEW.currency,NEW.requests_per_cycle,NEW.response_hours,NEW.total_cycles,NEW.created_at) IS DISTINCT FROM (OLD.id,OLD.engagement_id,OLD.revision,OLD.title,OLD.scope,OLD.terms,OLD.amount_minor,OLD.currency,OLD.requests_per_cycle,OLD.response_hours,OLD.total_cycles,OLD.created_at) THEN RAISE EXCEPTION 'Create a new revision for changed maintenance terms'; END IF;
 IF OLD.status='ACCEPTED' AND NEW IS DISTINCT FROM OLD THEN RAISE EXCEPTION 'Accepted maintenance terms are immutable'; END IF;
 IF OLD.status<>'DRAFT' AND (NEW.seller_id,NEW.seller_consented_at,NEW.digest) IS DISTINCT FROM (OLD.seller_id,OLD.seller_consented_at,OLD.digest) THEN RAISE EXCEPTION 'Sent seller consent is immutable'; END IF;
 IF NEW.status='DRAFT' AND OLD.status<>'DRAFT' THEN RAISE EXCEPTION 'Sent terms cannot revert to draft'; END IF;
 RETURN NEW; END $$;
CREATE TRIGGER maintenance_offers_retained BEFORE UPDATE OR DELETE ON maintenance_offers FOR EACH ROW EXECUTE FUNCTION maintenance_retained_offer();
CREATE FUNCTION maintenance_retained_subscription() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Recurring commercial records are retained'; END IF;
 IF (NEW.id,NEW.engagement_id,NEW.offer_id,NEW.payer_id,NEW.request_key,NEW.amount_minor,NEW.currency,NEW.requests_per_cycle,NEW.response_hours,NEW.total_cycles,NEW.digest,NEW.account_id,NEW.mode,NEW.billing_consented_at,NEW.created_at) IS DISTINCT FROM (OLD.id,OLD.engagement_id,OLD.offer_id,OLD.payer_id,OLD.request_key,OLD.amount_minor,OLD.currency,OLD.requests_per_cycle,OLD.response_hours,OLD.total_cycles,OLD.digest,OLD.account_id,OLD.mode,OLD.billing_consented_at,OLD.created_at) THEN RAISE EXCEPTION 'Recurring financial snapshots are immutable'; END IF;
 IF (OLD.provider_plan_id IS NOT NULL AND NEW.provider_plan_id IS DISTINCT FROM OLD.provider_plan_id) OR (OLD.provider_subscription_id IS NOT NULL AND NEW.provider_subscription_id IS DISTINCT FROM OLD.provider_subscription_id) OR (OLD.cancel_confirmed AND NOT NEW.cancel_confirmed) THEN RAISE EXCEPTION 'Provider bindings and confirmed cancellation are immutable'; END IF;
 RETURN NEW; END $$;
CREATE TRIGGER maintenance_subscriptions_retained BEFORE UPDATE OR DELETE ON maintenance_subscriptions FOR EACH ROW EXECUTE FUNCTION maintenance_retained_subscription();
CREATE FUNCTION maintenance_retained_period() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Paid periods are retained'; END IF;
 IF (NEW.id,NEW.subscription_id,NEW.provider_invoice_id,NEW.payment_id,NEW.order_id,NEW.amount_minor,NEW.currency,NEW.period_start,NEW.period_end,NEW.transfer_key,NEW.created_at) IS DISTINCT FROM (OLD.id,OLD.subscription_id,OLD.provider_invoice_id,OLD.payment_id,OLD.order_id,OLD.amount_minor,OLD.currency,OLD.period_start,OLD.period_end,OLD.transfer_key,OLD.created_at) OR NEW.refunded_minor<OLD.refunded_minor OR NEW.reversed_minor<OLD.reversed_minor OR (OLD.transfer_id IS NOT NULL AND NEW.transfer_id IS DISTINCT FROM OLD.transfer_id) THEN RAISE EXCEPTION 'Paid period snapshots and monotonic financial facts are immutable'; END IF;
 RETURN NEW; END $$;
CREATE TRIGGER maintenance_periods_retained BEFORE UPDATE OR DELETE ON maintenance_periods FOR EACH ROW EXECUTE FUNCTION maintenance_retained_period();
CREATE FUNCTION maintenance_retained_request() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF TG_OP='DELETE' OR (NEW.id,NEW.subscription_id,NEW.period_id,NEW.actor_id,NEW.request_key,NEW.title,NEW.description,NEW.response_due_at,NEW.created_at) IS DISTINCT FROM (OLD.id,OLD.subscription_id,OLD.period_id,OLD.actor_id,OLD.request_key,OLD.title,OLD.description,OLD.response_due_at,OLD.created_at) THEN RAISE EXCEPTION 'Request quota consumption and identity are immutable'; END IF;
 RETURN NEW; END $$;
CREATE TRIGGER maintenance_requests_retained BEFORE UPDATE OR DELETE ON maintenance_requests FOR EACH ROW EXECUTE FUNCTION maintenance_retained_request();
CREATE TRIGGER maintenance_ledger_append BEFORE UPDATE OR DELETE ON maintenance_ledger FOR EACH ROW EXECUTE FUNCTION reject_payment_ledger_change();
CREATE TRIGGER maintenance_audit_append BEFORE UPDATE OR DELETE ON maintenance_audit FOR EACH ROW EXECUTE FUNCTION reject_payment_ledger_change();
DO $$ DECLARE t text; BEGIN
 FOREACH t IN ARRAY ARRAY['maintenance_offers','maintenance_subscriptions','maintenance_periods','maintenance_requests','maintenance_provider_disputes','maintenance_refunds','maintenance_ledger','maintenance_webhook_events','maintenance_audit'] LOOP
  EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t);
  EXECUTE format('REVOKE ALL ON %I FROM PUBLIC',t);
  IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='anon') THEN EXECUTE format('REVOKE ALL ON %I FROM anon',t); END IF;
  IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN EXECUTE format('REVOKE ALL ON %I FROM authenticated',t); END IF;
 END LOOP;
END $$;
CREATE FUNCTION maintenance_retained_dispute() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF TG_OP='DELETE' OR (NEW.id,NEW.period_id) IS DISTINCT FROM (OLD.id,OLD.period_id) THEN RAISE EXCEPTION 'Provider dispute identity and history are retained'; END IF;
 RETURN NEW; END $$;
CREATE TRIGGER maintenance_disputes_retained BEFORE UPDATE OR DELETE ON maintenance_provider_disputes FOR EACH ROW EXECUTE FUNCTION maintenance_retained_dispute();
CREATE FUNCTION maintenance_retained_event() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF TG_OP='DELETE' OR (NEW.event_id,NEW.payload_hash,NEW.event_kind,NEW.subscription_id,NEW.payment_id,NEW.dispute_id,NEW.received_at) IS DISTINCT FROM (OLD.event_id,OLD.payload_hash,OLD.event_kind,OLD.subscription_id,OLD.payment_id,OLD.dispute_id,OLD.received_at) THEN RAISE EXCEPTION 'Signed event identity and evidence are retained'; END IF;
 IF NEW.attempts<OLD.attempts OR (OLD.processed_at IS NOT NULL AND NEW.processed_at IS DISTINCT FROM OLD.processed_at) THEN RAISE EXCEPTION 'Processed event evidence cannot be reset'; END IF;
 RETURN NEW; END $$;
CREATE TRIGGER maintenance_events_retained BEFORE UPDATE OR DELETE ON maintenance_webhook_events FOR EACH ROW EXECUTE FUNCTION maintenance_retained_event();

CREATE FUNCTION maintenance_retained_refund() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF TG_OP='DELETE' OR (NEW.id,NEW.period_id,NEW.amount_minor,NEW.created_at) IS DISTINCT FROM (OLD.id,OLD.period_id,OLD.amount_minor,OLD.created_at) OR (OLD.status='processed' AND NEW.status<>'processed') THEN RAISE EXCEPTION 'Provider refund identity and processed facts are retained'; END IF;
 RETURN NEW; END $$;
CREATE TRIGGER maintenance_refunds_retained BEFORE UPDATE OR DELETE ON maintenance_refunds FOR EACH ROW EXECUTE FUNCTION maintenance_retained_refund();
