# Administrator MFA encryption and recovery

TOTP credentials are encrypted with AES-256-GCM before database insertion. The database stores a key version, a fresh 96-bit nonce and ciphertext including a 128-bit authentication tag. A unique database index rejects nonce reuse within a key version. The authenticated data binds the envelope to the user UUID, purpose and key version. Authentication selects only encrypted fields and never falls back to `admin_totp`.

Provide `MFA_ACTIVE_KEY_ID` and `MFA_KEYRING` from an external secret manager. The keyring format is `version:standardBase64,otherVersion:standardBase64`; each decoded key must contain exactly 32 random bytes, each version must match `[A-Za-z0-9_-]{1,32}`, and at most 16 versions are accepted. Generate a new key with a cryptographically secure secret-manager generator or `openssl rand -base64 32`. Never put keys in Git, database tables, logs, images, ticket attachments or client configuration. Every retained version must identify different key material. Staging and production fail startup when encryption configuration is absent or invalid. Local startup without a key is permitted only without stored MFA credentials or administrator creation.

Administrator bootstrap uses `ADMIN_EMAIL`, `ADMIN_BOOTSTRAP_PASSWORD`, `ADMIN_TOTP_SECRET` and optional `ADMIN_DISPLAY_NAME` (default `Administrator`). Provision the TOTP secret to the authorized operator through an approved secure channel and verify sign-in. Remove bootstrap password and TOTP material from deployment configuration after provisioning; keep the external encryption keyring. The administrator email may be retained to pin bootstrap to that account; a blank email permits the existing sole administrator without changing credentials. Existing administrator credentials are never reset or promoted by bootstrap. No personal operator identity is seeded by default.

## Existing plaintext upgrade

Deploy Flyway V26 with the correct external keyring before allowing traffic. The startup runner acquires the administrator advisory lock and performs one transaction before application readiness. It reads at most 1,001 credential rows, accepts at most 1,000, encrypts legacy valid Base32 secrets, sets `admin_totp=NULL`, and revokes affected sessions and login challenges. It also authenticates every existing ciphertext. Invalid plaintext, a missing historical key, corrupted ciphertext or a bound exceeded aborts startup and rolls back the entire upgrade. Do not serve traffic from old application versions after this migration. Validate with read-only queries:

```sql
SELECT count(*) AS plaintext_remaining FROM users WHERE admin_totp IS NOT NULL;
SELECT admin_totp_key_version,count(*) FROM users
 WHERE admin_totp_key_version IS NOT NULL GROUP BY admin_totp_key_version;
```

The first result must be zero. Do not print ciphertext or keys during validation. V28 adds session issuance timestamps and revokes existing MFA-verified sessions because their historical authentication time cannot be established. Every administrator must sign in again with password and TOTP after that upgrade. For datasets over the startup bound, arrange an offline, reviewed migration using the same encryption implementation before startup. The runtime database role needs only the documented user/session/challenge permissions; Flyway runs with the separate migration role.

## Rotation

1. Create a fresh 32-byte key under a new version. Retain the old version and encrypted backup access in the secret manager.
2. Deploy the combined keyring to every instance while keeping the old active version. Verify startup and an administrator MFA sign-in.
3. During a maintenance window stop traffic and ensure all instances use the same new active version. Restart with the combined keyring. Startup authenticates and re-encrypts every old-version credential with a fresh nonce, clears any legacy plaintext and revokes affected sessions/challenges. A failure aborts the transaction and application readiness.
4. Check that the plaintext count is zero and all credential version counts show the new version. Complete a fresh password-plus-MFA login; confirm old sessions and challenges fail. Restore traffic after readiness and sign-in verification.
5. Retire the old key from the live keyring only after no current rows reference it. Keep backup-required historical keys separately under restricted, audited access until those backups expire. Never reuse a version for different key material. Backups containing ciphertext cannot be restored without their matching historical keys.

Rollback before rotation may restore the old active version with the combined keyring. After rotation, preserve both keys and use the same reviewed maintenance process; never reintroduce plaintext or bypass MFA. Keep mixed active versions out of service because a restarting instance could otherwise rotate a credential back to its own active version.

## Lost key, corruption or operator authenticator loss

Fail closed. A missing or invalid ciphertext cannot authenticate, and plaintext cannot act as a recovery channel. Stop administrator access, revoke all of its sessions and login challenges, and preserve audit evidence. Recover the historical key from the external secret manager and validate the envelope before restart. If recovery is impossible, use an approved offline procedure with dual operator approval to verify operator identity, create a new TOTP secret, encrypt it with `MfaSecrets.encrypt(userId, secret)` and update only the three encrypted fields with `admin_totp=NULL` in one transaction. Do not place recovery key material in SQL history or support tickets. Supply values as bound parameters from a short-lived secure recovery program using the audited implementation. Verify the new password-plus-MFA flow before restoring access. Do not grant ADMIN to a new account or disable MFA as a shortcut.

Key storage, production rotation, backup restoration and operator identity approval are deployment actions. This repository supplies the mechanism and procedure; it does not claim those actions have been performed.
