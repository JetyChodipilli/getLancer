package com.getlancer.config;

import static com.getlancer.shared.Support.id;

import com.getlancer.auth.MfaSecrets;

import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class Bootstrap implements ApplicationRunner {
  final JdbcTemplate db;
  final String email, password, totp, environment, origin;
  final boolean secure;
  final MfaSecrets secrets;
  final String displayName;

  public Bootstrap(JdbcTemplate db, String email, String password, String totp,
      String environment, String origin, boolean secure) {
    this(db, email, password, totp, environment, origin, secure,
        new MfaSecrets("", "", "local"), "Administrator");
  }

  @Autowired
  public Bootstrap(
      JdbcTemplate db,
      @Value("${app.admin-email}") String email,
      @Value("${app.admin-password}") String password,
      @Value("${app.admin-totp}") String totp,
      @Value("${app.environment}") String environment,
      @Value("${app.origin}") String origin,
      @Value("${app.secure-cookie}") boolean secure,
      MfaSecrets secrets,
      @Value("${app.admin-display-name:Administrator}") String displayName) {
    this.db = db;
    this.email = email.trim().toLowerCase(Locale.ROOT);
    this.password = password;
    this.totp = totp;
    this.environment = environment;
    this.origin = origin;
    this.secure = secure;
    this.secrets = secrets;
    this.displayName = displayName;
  }

  @Transactional
  public void run(ApplicationArguments args) {
    if (environment.equals("production") && (!secure || !origin.startsWith("https://")))
      throw new IllegalStateException("Production requires HTTPS and secure cookies");
    if (!environment.equals("local")) secrets.requireConfigured();
    db.execute("SELECT pg_advisory_xact_lock(714203061)");
    var admins =
        db.queryForList(
            "SELECT u.email FROM users u JOIN user_roles r ON r.user_id=u.id WHERE r.role='ADMIN'",
            String.class);
    if (!admins.isEmpty()) {
      if (admins.size() != 1 || (!email.isBlank() && !admins.get(0).equals(email)))
        throw new IllegalStateException(
            "Existing administrator differs from configured email; explicit recovery is required");
      org.slf4j.LoggerFactory.getLogger(Bootstrap.class)
          .info("Administrator already exists; credentials unchanged.");
      return; // Never reset an existing administrator's password on startup.
    }
    if (password.isBlank() && totp.isBlank() && !environment.equals("production")) return;
    if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
        || email.length() > 254
        || displayName == null || displayName.isBlank() || displayName.length() > 100
        || password.length() < 16
        || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
        || !totp.matches("[A-Z2-7]{32,128}"))
      throw new IllegalStateException(
          "Admin bootstrap requires email, a strong password and Base32 TOTP secret");
    if (db.queryForObject("SELECT count(*) FROM users WHERE email=?", Integer.class, email) > 0)
      throw new IllegalStateException(
          "Admin email belongs to a non-admin account; automatic privilege elevation is forbidden");
    secrets.requireConfigured();
    UUID u = id();
    var encrypted = secrets.encrypt(u, totp);
    db.update(
        "INSERT INTO users(id,email,password_hash,email_verified_at,admin_totp_key_version,"
            + "admin_totp_nonce,admin_totp_ciphertext) VALUES(?,?,?,now(),?,?,?)",
        u,
        email.toLowerCase(),
        new BCryptPasswordEncoder(12).encode(password),
        encrypted.keyId(),
        encrypted.nonce(),
        encrypted.ciphertext());
    db.update(
        "INSERT INTO user_roles(user_id,role) VALUES(?,'CLIENT'),(?,'DEVELOPER'),(?,'ADMIN')",
        u,
        u,
        u);
    db.update(
        "INSERT INTO developer_profiles(user_id,slug,display_name,approval_status)"
            + " VALUES(?,?,?,'APPROVED')",
        u,
        "admin-" + u,
        displayName.trim());
    db.update("INSERT INTO showcase_entitlements(user_id) VALUES(?)", u);
    org.springframework.transaction.support.TransactionSynchronizationManager
        .registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
              public void afterCommit() {
                org.slf4j.LoggerFactory.getLogger(Bootstrap.class)
                    .info(
                        "Administrator created. Password and authenticator sign-in are required.");
              }
            });
  }
}
