CREATE TABLE login_challenges (
 token_hash varchar(64) PRIMARY KEY,
 user_id uuid NOT NULL REFERENCES users,
 attempts int NOT NULL DEFAULT 0 CHECK(attempts >= 0),
 expires_at timestamptz NOT NULL
);
CREATE INDEX login_challenges_expiry ON login_challenges(expires_at);
CREATE TABLE oauth_pending (
 state_hash varchar(64) PRIMARY KEY,
 browser_hash varchar(64) NOT NULL,
 nonce varchar(100) NOT NULL,
 code_verifier varchar(100) NOT NULL,
 intent varchar(10) NOT NULL CHECK(intent IN ('login','signup')),
 expires_at timestamptz NOT NULL
);
CREATE INDEX oauth_pending_expiry ON oauth_pending(expires_at);
CREATE TABLE oauth_identities (
 provider varchar(20) NOT NULL CHECK(provider='google'),
 subject varchar(255) NOT NULL,
 user_id uuid NOT NULL REFERENCES users,
 created_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(provider,subject),
 UNIQUE(user_id,provider)
);
