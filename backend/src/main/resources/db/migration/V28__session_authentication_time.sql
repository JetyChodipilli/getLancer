-- Existing MFA sessions have no reliable issuance time. Require administrator sign-in
-- again rather than treating a migration timestamp as fresh MFA.
DELETE FROM sessions WHERE mfa_verified;
ALTER TABLE sessions ADD COLUMN issued_at timestamptz NOT NULL DEFAULT now();
