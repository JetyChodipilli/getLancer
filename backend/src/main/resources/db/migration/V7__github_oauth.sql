ALTER TABLE oauth_identities DROP CONSTRAINT oauth_identities_provider_check;
ALTER TABLE oauth_identities ADD CONSTRAINT oauth_identities_provider_check CHECK(provider IN ('google','github'));
ALTER TABLE oauth_pending ADD COLUMN provider varchar(20) NOT NULL DEFAULT 'google' CHECK(provider IN ('google','github'));
ALTER TABLE oauth_pending ADD COLUMN remember_me boolean NOT NULL DEFAULT false;
