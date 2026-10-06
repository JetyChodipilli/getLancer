package com.getlancer.config;

import com.getlancer.auth.MfaSecrets;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Upgrade and rotate a bounded number of MFA credentials before application readiness. */
@Component
@Order(-20)
public class MfaStorageUpgrade implements ApplicationRunner {
  public static final int MAX_CREDENTIALS = 1000;
  private final JdbcTemplate db;
  private final MfaSecrets secrets;

  public MfaStorageUpgrade(JdbcTemplate db, MfaSecrets secrets) {
    this.db = db;
    this.secrets = secrets;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    db.execute("SELECT pg_advisory_xact_lock(714203061)");
    Integer count = db.queryForObject(
        "SELECT count(*) FROM users WHERE admin_totp IS NOT NULL OR admin_totp_key_version IS NOT NULL",
        Integer.class);
    if (count == null || count == 0) return;
    if (count > MAX_CREDENTIALS)
      throw new IllegalStateException("MFA upgrade exceeds startup bound; offline recovery is required.");
    secrets.requireConfigured();
    var rows = db.queryForList(
        "SELECT id,admin_totp,admin_totp_key_version,admin_totp_nonce,admin_totp_ciphertext FROM users"
            + " WHERE admin_totp IS NOT NULL OR admin_totp_key_version IS NOT NULL ORDER BY id LIMIT ? FOR UPDATE",
        MAX_CREDENTIALS + 1);
    if (rows.size() > MAX_CREDENTIALS)
      throw new IllegalStateException("MFA upgrade exceeds startup bound; offline recovery is required.");
    for (var row : rows) {
      UUID user = (UUID) row.get("id");
      String version = (String) row.get("admin_totp_key_version");
      String secret = row.get("admin_totp") == null
          ? secrets.decrypt(user, version, (byte[]) row.get("admin_totp_nonce"),
              (byte[]) row.get("admin_totp_ciphertext"))
          : (String) row.get("admin_totp");
      if (row.get("admin_totp") != null || !secrets.activeKeyId().equals(version)) {
        var encrypted = secrets.encrypt(user, secret);
        db.update("UPDATE users SET admin_totp=NULL,admin_totp_key_version=?,admin_totp_nonce=?,"
                + "admin_totp_ciphertext=? WHERE id=?", encrypted.keyId(), encrypted.nonce(),
            encrypted.ciphertext(), user);
        db.update("DELETE FROM sessions WHERE user_id=?", user);
        db.update("DELETE FROM login_challenges WHERE user_id=?", user);
      }
    }
  }
}
