CREATE TABLE payment_accounts (
  id uuid PRIMARY KEY,
  builder_user_id uuid REFERENCES users(id),
  team_id uuid REFERENCES teams(id),
  account_id varchar(40) NOT NULL UNIQUE,
  mode varchar(4) NOT NULL CHECK(mode IN ('test','live')),
  provider_status varchar(32) NOT NULL,
  activation_confirmed boolean NOT NULL CHECK(activation_confirmed),
  verified_by uuid NOT NULL REFERENCES users(id),
  verified_at timestamptz NOT NULL DEFAULT now(),
  CHECK ((builder_user_id IS NOT NULL)::int + (team_id IS NOT NULL)::int = 1)
);
CREATE UNIQUE INDEX payment_accounts_builder ON payment_accounts(builder_user_id) WHERE builder_user_id IS NOT NULL;
CREATE UNIQUE INDEX payment_accounts_team ON payment_accounts(team_id) WHERE team_id IS NOT NULL;
CREATE TABLE payment_attempts (
  id uuid PRIMARY KEY,
  milestone_id uuid NOT NULL REFERENCES delivery_milestones(id),
  payer_user_id uuid NOT NULL REFERENCES users(id),
  idempotency_key uuid NOT NULL,
  amount_minor bigint NOT NULL CHECK(amount_minor BETWEEN 100 AND 1000000000),
  currency varchar(3) NOT NULL DEFAULT 'INR' CHECK(currency='INR'),
  account_id varchar(40) NOT NULL,
  mode varchar(4) NOT NULL CHECK(mode IN ('test','live')),
  status varchar(24) NOT NULL CHECK(status IN ('CREATING','ORDER_CREATED','UNKNOWN','REJECTED','CAPTURED','PARTIALLY_REFUNDED','REFUNDED','DISPUTED')),
  order_id varchar(40) UNIQUE,
  payment_id varchar(40) UNIQUE,
  refunded_minor bigint NOT NULL DEFAULT 0 CHECK(refunded_minor>=0 AND refunded_minor<=amount_minor),
  transfer_status varchar(32) NOT NULL DEFAULT 'NOT_CREATED',
  settlement_status varchar(32) NOT NULL DEFAULT 'UNKNOWN',
  attention_reason varchar(200),
  dispute_id varchar(40),
  dispute_status varchar(32),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(payer_user_id,idempotency_key)
);
CREATE UNIQUE INDEX payment_active_milestone ON payment_attempts(milestone_id) WHERE status<>'REJECTED';
CREATE TABLE payment_provider_disputes (
  id varchar(40) PRIMARY KEY,
  attempt_id uuid NOT NULL REFERENCES payment_attempts(id),
  status varchar(32) NOT NULL,
  deducted_minor bigint NOT NULL CHECK(deducted_minor>=0),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE payment_webhook_events (
  event_id varchar(100) PRIMARY KEY,
  payload_hash char(64) NOT NULL,
  event_kind varchar(100) NOT NULL,
  received_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE payment_ledger (
  id uuid PRIMARY KEY,
  attempt_id uuid NOT NULL REFERENCES payment_attempts(id),
  entry_key varchar(100) NOT NULL,
  kind varchar(24) NOT NULL CHECK(kind IN ('CAPTURE','REFUND')),
  amount_minor bigint NOT NULL CHECK(amount_minor>0),
  currency varchar(3) NOT NULL CHECK(currency='INR'),
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(attempt_id,entry_key)
);
CREATE TABLE payment_account_audit (
  id uuid PRIMARY KEY,
  account_mapping_id uuid NOT NULL REFERENCES payment_accounts(id),
  actor_id uuid NOT NULL REFERENCES users(id),
  previous_account_id varchar(40),
  account_id varchar(40) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE FUNCTION reject_payment_ledger_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Payment ledger entries are append-only'; END $$;
CREATE TRIGGER payment_ledger_immutable BEFORE UPDATE OR DELETE ON payment_ledger FOR EACH ROW EXECUTE FUNCTION reject_payment_ledger_change();
CREATE TRIGGER payment_account_audit_immutable BEFORE UPDATE OR DELETE ON payment_account_audit FOR EACH ROW EXECUTE FUNCTION reject_payment_ledger_change();

-- Public clients use the authenticated Java API. No direct PostgREST access to financial records.
ALTER TABLE payment_accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE payment_attempts ENABLE ROW LEVEL SECURITY;
ALTER TABLE payment_provider_disputes ENABLE ROW LEVEL SECURITY;
ALTER TABLE payment_webhook_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE payment_ledger ENABLE ROW LEVEL SECURITY;
ALTER TABLE payment_account_audit ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON payment_accounts,payment_attempts,payment_webhook_events,payment_provider_disputes,payment_ledger,payment_account_audit FROM PUBLIC;
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='anon') THEN
    REVOKE ALL ON payment_accounts,payment_attempts,payment_webhook_events,payment_provider_disputes,payment_ledger,payment_account_audit FROM anon;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN
    REVOKE ALL ON payment_accounts,payment_attempts,payment_webhook_events,payment_provider_disputes,payment_ledger,payment_account_audit FROM authenticated;
  END IF;
END $$;
