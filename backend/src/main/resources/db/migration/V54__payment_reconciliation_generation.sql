-- Provider observations may finish out of order; only the current pass may commit.
ALTER TABLE payment_attempts ADD COLUMN reconciliation_generation bigint NOT NULL DEFAULT 0 CHECK(reconciliation_generation>=0);
