-- Application startup performs the bounded keyring-backed upgrade before traffic is served.
-- Preserve the nullable legacy column for the upgrade; authentication never reads it.
ALTER TABLE users ADD COLUMN admin_totp_key_version varchar(32);
ALTER TABLE users ADD COLUMN admin_totp_nonce bytea;
ALTER TABLE users ADD COLUMN admin_totp_ciphertext bytea;
ALTER TABLE users ADD CONSTRAINT admin_totp_encrypted_envelope CHECK (
  (admin_totp_key_version IS NULL AND admin_totp_nonce IS NULL AND admin_totp_ciphertext IS NULL)
  OR (admin_totp IS NULL AND admin_totp_key_version IS NOT NULL AND admin_totp_key_version ~ '^[A-Za-z0-9_-]{1,32}$'
      AND admin_totp_nonce IS NOT NULL AND octet_length(admin_totp_nonce)=12
      AND admin_totp_ciphertext IS NOT NULL AND octet_length(admin_totp_ciphertext) BETWEEN 32 AND 144)
);
-- Enforce uniqueness of the 96-bit nonce for each external key version.
CREATE UNIQUE INDEX admin_totp_unique_nonce ON users(admin_totp_key_version,admin_totp_nonce)
  WHERE admin_totp_nonce IS NOT NULL;
