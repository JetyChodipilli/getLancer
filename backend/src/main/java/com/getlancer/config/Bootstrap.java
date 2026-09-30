package com.getlancer.config;

import static com.getlancer.shared.Support.*;

import java.util.*;
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

  public Bootstrap(
      JdbcTemplate db,
      @Value("${app.admin-email}") String email,
      @Value("${app.admin-password}") String password,
      @Value("${app.admin-totp}") String totp,
      @Value("${app.environment}") String environment,
      @Value("${app.origin}") String origin,
      @Value("${app.secure-cookie}") boolean secure) {
    this.db = db;
    this.email = email.trim().toLowerCase(Locale.ROOT);
    this.password = password;
    this.totp = totp;
    this.environment = environment;
    this.origin = origin;
    this.secure = secure;
  }

  @Transactional
  public void run(ApplicationArguments args) {
    if (environment.equals("production") && (!secure || !origin.startsWith("https://")))
      throw new IllegalStateException("Production requires HTTPS and secure cookies");
    db.execute("SELECT pg_advisory_xact_lock(714203061)");
    var admins =
        db.queryForList(
            "SELECT u.email FROM users u JOIN user_roles r ON r.user_id=u.id WHERE r.role='ADMIN'",
            String.class);
    if (!admins.isEmpty()) {
      if (admins.size() != 1 || !admins.get(0).equals(email))
        throw new IllegalStateException(
            "Existing administrator differs from configured email; explicit recovery is required");
      org.slf4j.LoggerFactory.getLogger(Bootstrap.class)
          .info("Administrator already exists; credentials unchanged.");
      return; // Never reset an existing administrator's password on startup.
    }
    if (password.isBlank() && totp.isBlank() && !environment.equals("production")) return;
    if (email.isBlank()
        || password.length() < 16
        || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
        || !totp.matches("[A-Z2-7]{32,}"))
      throw new IllegalStateException(
          "Admin bootstrap requires email, a strong password and Base32 TOTP secret");
    if (db.queryForObject("SELECT count(*) FROM users WHERE email=?", Integer.class, email) > 0)
      throw new IllegalStateException(
          "Admin email belongs to a non-admin account; automatic privilege elevation is forbidden");
    UUID u = id();
    db.update(
        "INSERT INTO users(id,email,password_hash,email_verified_at,admin_totp)"
            + " VALUES(?,?,?,now(),?)",
        u,
        email.toLowerCase(),
        new BCryptPasswordEncoder(12).encode(password),
        totp);
    db.update(
        "INSERT INTO user_roles(user_id,role) VALUES(?,'CLIENT'),(?,'DEVELOPER'),(?,'ADMIN')",
        u,
        u,
        u);
    db.update(
        "INSERT INTO developer_profiles(user_id,slug,display_name,approval_status)"
            + " VALUES(?,?,'Jety','APPROVED')",
        u,
        "admin-" + u);
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
