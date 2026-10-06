package com.getlancer.auth;

import static com.getlancer.shared.Support.email;
import static com.getlancer.shared.Support.id;
import static com.getlancer.shared.Support.randomToken;

import com.getlancer.notifications.Mail;
import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleAccounts {
  final String legalVersion;

  final JdbcTemplate db;
  final Security security;
  final Mail mail;
  final String adminEmail;

  public GoogleAccounts(JdbcTemplate db, Security security, Mail mail, String adminEmail) {
    this(db, security, mail, adminEmail, "v1-draft");
  }

  @Autowired
  public GoogleAccounts(JdbcTemplate db, Security security, Mail mail, @Value("${app.admin-email}") String adminEmail,
      @Value("${app.legal-version:v1-draft}") String legalVersion) {
    this.db = db;
    this.legalVersion = legalVersion;
    this.security = security;
    this.mail = mail;
    this.adminEmail = adminEmail.toLowerCase(Locale.ROOT).trim();
  }

  @Transactional
  UUID resolve(Jwt jwt, String intent) {
    String subject = jwt.getSubject(),
        email = jwt.getClaimAsString("email").toLowerCase(Locale.ROOT);
    if (email.equals(adminEmail))
      throw new ApiError(403, "admin_password", "Use administrator password sign-in.");
    // Serialize concurrent callbacks for the same Google identity.
    db.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", "google:" + subject);
    var linked =
        db.queryForList(
            "SELECT u.id,u.email,u.account_status FROM users u JOIN oauth_identities i ON i.user_id=u.id WHERE"
                + " i.provider='google' AND i.subject=? FOR UPDATE OF u",
            subject);
    if (!linked.isEmpty()) {
      var user = linked.get(0);
      UUID id = (UUID) user.get("id");
      if (security.role(id, "ADMIN"))
        throw new ApiError(403, "admin_password", "Use administrator password sign-in.");
      if (!user.get("account_status").equals("ACTIVE") || !user.get("email").equals(email))
        throw new ApiError(403, "account_unavailable", "Account unavailable.");
      return id;
    }
    if (db.queryForObject("SELECT count(*) FROM users WHERE email=?", Integer.class, email) > 0)
      throw new ApiError(409, "email_account", "Use your existing sign-in method.");
    if (!intent.equals("signup"))
      throw new ApiError(409, "signup_required", "Create an account first.");
    UUID user = id();
    boolean verified = GoogleTokens.authoritativeEmail(jwt);
    // A random, unknown password preserves the existing schema; reset email can establish a
    // password later.
    db.update(
        "INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,?,CASE WHEN ? THEN"
            + " now() ELSE NULL END)",
        user,
        email,
        new BCryptPasswordEncoder(12).encode(randomToken()),
        verified);
    db.update(
        "INSERT INTO user_roles(user_id,role) VALUES(?,'CLIENT'),(?,'DEVELOPER')", user, user);
    String name = Objects.toString(jwt.getClaimAsString("name"), "");
    if (name.length() > 100) name = name.substring(0, 100);
    db.update(
        "INSERT INTO developer_profiles(user_id,slug,display_name) VALUES(?,?,?)",
        user,
        "builder-" + user,
        name);
    db.update("INSERT INTO showcase_entitlements(user_id) VALUES(?)", user);
    db.update(
        "INSERT INTO legal_acceptances(user_id,document_version) VALUES(?,?)", user, legalVersion);
    db.update(
        "INSERT INTO oauth_identities(provider,subject,user_id) VALUES('google',?,?)",
        subject,
        user);
    if (!verified) mail.token("EMAIL_VERIFICATION", user, null, email);
    return user;
  }
}
