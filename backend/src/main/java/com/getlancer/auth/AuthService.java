package com.getlancer.auth;

import static com.getlancer.shared.Support.email;
import static com.getlancer.shared.Support.hash;
import static com.getlancer.shared.Support.id;
import static com.getlancer.shared.Support.randomToken;
import static com.getlancer.shared.Support.text;
import static com.getlancer.shared.Support.totp;

import com.getlancer.hosting.HostingRepository;
import com.getlancer.inquiries.InquiryOutcomeService;
import com.getlancer.inquiries.InquiryPolicy;
import com.getlancer.notifications.Mail;
import com.getlancer.products.ProductRepository;
import com.getlancer.responses.AccountExportResponse;
import com.getlancer.responses.UserResponse;
import com.getlancer.security.Security;
import com.getlancer.security.SecurityAudit;
import com.getlancer.security.SessionCookies;
import com.getlancer.shared.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuthService {
  final String legalVersion;

  final JdbcTemplate db;
  final Security security;
  final Mail mail;
  private final InquiryOutcomeService outcomes;
  private final HostingRepository hosting;
  private final SecurityAudit audit;

  private void audit(UUID actor, String event, String target, String result) {
    if (audit != null) audit.record(actor, event, target, result);
  }

  final boolean secure;
  final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);
  final String reservedAdminEmail;
  final MfaSecrets mfaSecrets;

  public AuthService(JdbcTemplate db, Security security, Mail mail, boolean secure,
      String reservedAdminEmail, InquiryOutcomeService outcomes) {
    this(db, security, mail, secure, reservedAdminEmail, outcomes,
        new MfaSecrets("", "", "local"));
  }

  public AuthService(JdbcTemplate db, Security security, Mail mail, boolean secure,
      String reservedAdminEmail, InquiryOutcomeService outcomes, MfaSecrets mfaSecrets) {
    this(db, security, mail, secure, reservedAdminEmail, outcomes, mfaSecrets,
        null, null, "v1-draft");
  }

  @Autowired
  public AuthService(
      JdbcTemplate db,
      Security security,
      Mail mail,
      @Value("${app.secure-cookie}") boolean secure,
      @Value("${app.admin-email}") String reservedAdminEmail,
      InquiryOutcomeService outcomes,
      MfaSecrets mfaSecrets,
      SecurityAudit audit,
      HostingRepository hosting,
      @Value("${app.legal-version:v1-draft}") String legalVersion) {
    this.reservedAdminEmail = reservedAdminEmail.trim().toLowerCase(Locale.ROOT);
    this.db = db;
    this.security = security;
    this.mail = mail;
    this.outcomes = outcomes;
    this.secure = secure;
    this.mfaSecrets = mfaSecrets;
    this.audit = audit;
    this.hosting = hosting;
    this.legalVersion = legalVersion;
  }

  @Transactional
  public Map<String, Object> signup(Map<String, Object> b) {
    String email = email(b, "email"), password = password(b);
    if (email.equals(reservedAdminEmail))
      throw new ApiError(
          409, "EMAIL_UNAVAILABLE", "This email cannot be used for public registration.");
    if (!Boolean.TRUE.equals(b.get("acceptedTerms")))
      throw new ApiError(400, "TERMS_REQUIRED", "Accept the terms and privacy notice to continue.");
    if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new ApiError(400, "VALIDATION_ERROR", "Password must be at most 72 UTF-8 bytes.");
    UUID u = id();
    db.update(
        "INSERT INTO users(id,email,password_hash) VALUES(?,?,?)",
        u,
        email,
        passwords.encode(password));
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'CLIENT'),(?,'DEVELOPER')", u, u);
    db.update(
        "INSERT INTO developer_profiles(user_id,slug,display_name) VALUES(?,?,?)",
        u,
        "builder-" + u,
        text(b, "displayName", 0, 100));
    db.update("INSERT INTO showcase_entitlements(user_id) VALUES(?)", u);
    db.update(
        "INSERT INTO legal_acceptances(user_id,document_version) VALUES(?,?)", u, legalVersion);
    mail.token("EMAIL_VERIFICATION", u, null, email);
    return Map.of("message", "Confirm your email to continue.");
  }

  @Transactional
  public Map<String, Object> login(Map<String, Object> b, HttpServletResponse res) {
    return login(b, null, res);
  }

  @Transactional
  public Map<String, Object> login(Map<String, Object> b, HttpServletRequest req, HttpServletResponse res) {
    String email = email(b, "email"), password = Objects.toString(b.get("password"), "");
    security.limitIdentity(email, "login");
    var users =
        db.queryForList("SELECT id,password_hash FROM users WHERE email=? AND account_status='ACTIVE' FOR UPDATE", email);
    String dummy = "$2a$12$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW";
    String stored = users.isEmpty() ? dummy : (String) users.get(0).get("password_hash");
    if (password.length() > 72
        || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
        || !passwords.matches(password, stored) || users.isEmpty()) {
      audit(null, "LOGIN_FAILURE", "AUTH", "FAILURE");
      throw new ApiError(401, "UNAUTHENTICATED", "Email or password is incorrect.");
    }
    UUID user = (UUID) users.get(0).get("id");
    revokeBrowserSession(req);
    if (security.role(user, "ADMIN")) {
      db.update("DELETE FROM login_challenges WHERE user_id=? OR expires_at<now()", user);
      String challenge = randomToken();
      db.update(
          "INSERT INTO login_challenges(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '5"
              + " minutes')",
          hash(challenge),
          user);
      authCookie(res, "gl_session", "", 0);
      authCookie(res, "gl_mfa", challenge, 300);
      audit(user, "MFA_CHALLENGE", user.toString(), "SUCCESS");
      return Map.of("mfaRequired", true);
    }
    db.update("DELETE FROM login_challenges WHERE token_hash=?",
        hash(req == null ? "" : SessionCookies.read(req, "gl_mfa")));
    authCookie(res, "gl_mfa", "", 0);
    issueSession(user, false, Boolean.TRUE.equals(b.get("rememberMe")), res);
    return Map.of("id", user);
  }

  @Transactional(noRollbackFor = ApiError.class)
  public Map<String, Object> mfa(
      Map<String, Object> body, HttpServletRequest req, HttpServletResponse res) {
    String challenge = SessionCookies.read(req, "gl_mfa");
    var rows =
        db.queryForList(
            "SELECT c.user_id,c.expires_at,c.attempts,u.admin_totp_key_version,u.admin_totp_nonce,"
                + "u.admin_totp_ciphertext,u.account_status FROM login_challenges c JOIN users u ON"
                + " u.id=c.user_id WHERE c.token_hash=? FOR UPDATE OF u",
            hash(challenge));
    if (rows.isEmpty()) {
      audit(null, "MFA_FAILURE", "AUTH", "FAILURE");
      throw new ApiError(401, "MFA_EXPIRED", "Please sign in again.");
    }
    // The first statement acquires the user lock. Re-read and lock the challenge in a
    // new statement so callers queued on that lock cannot use stale attempt counters.
    rows = db.queryForList(
        "SELECT c.user_id,c.expires_at,c.attempts,u.admin_totp_key_version,u.admin_totp_nonce,"
            + "u.admin_totp_ciphertext,u.account_status FROM login_challenges c JOIN users u ON"
            + " u.id=c.user_id WHERE c.token_hash=? FOR UPDATE OF c",
        hash(challenge));
    if (rows.isEmpty()) {
      audit(null, "MFA_FAILURE", "AUTH", "FAILURE");
      throw new ApiError(401, "MFA_EXPIRED", "Please sign in again.");
    }
    var row = rows.get(0);
    UUID user = (UUID) row.get("user_id");
    boolean attemptsLocked = ((Number) row.get("attempts")).intValue() >= 5;
    if (((Timestamp) row.get("expires_at")).toInstant().isBefore(Instant.now())
        || attemptsLocked
        || !row.get("account_status").equals("ACTIVE")
        || !security.role(user, "ADMIN")) {
      db.update("DELETE FROM login_challenges WHERE token_hash=?", hash(challenge));
      authCookie(res, "gl_mfa", "", 0);
      audit(user, attemptsLocked ? "MFA_LOCKED" : "MFA_FAILURE", user.toString(),
          attemptsLocked ? "LOCKED" : "FAILURE");
      throw new ApiError(401, "MFA_EXPIRED", "Please sign in again.");
    }
    security.limitIdentity(user.toString(), "mfa");
    String secret;
    try {
      secret = mfaSecrets.decrypt(user, (String) row.get("admin_totp_key_version"),
          (byte[]) row.get("admin_totp_nonce"), (byte[]) row.get("admin_totp_ciphertext"));
    } catch (MfaSecrets.SecretUnavailable unavailable) {
      db.update("DELETE FROM login_challenges WHERE token_hash=?", hash(challenge));
      authCookie(res, "gl_mfa", "", 0);
      audit(user, "MFA_LOCKED", user.toString(), "LOCKED");
      throw new ApiError(401, "MFA_EXPIRED", "Please sign in again or contact the operator.");
    }
    if (!totp(secret, Objects.toString(body.get("totp"), ""))) {
      db.update(
          "UPDATE login_challenges SET attempts=attempts+1 WHERE token_hash=?", hash(challenge));
      boolean locked = ((Number) row.get("attempts")).intValue() + 1 >= 5;
      if (locked) {
        db.update("DELETE FROM login_challenges WHERE token_hash=?", hash(challenge));
        authCookie(res, "gl_mfa", "", 0);
      }
      audit(user, locked ? "MFA_LOCKED" : "MFA_FAILURE", user.toString(),
          locked ? "LOCKED" : "FAILURE");
      throw new ApiError(
          401,
          "MFA_INVALID",
          "That code is incorrect or has expired. Try the current code from your authenticator.");
    }
    if (db.update("DELETE FROM login_challenges WHERE token_hash=?", hash(challenge)) != 1) {
      authCookie(res, "gl_mfa", "", 0);
      audit(user, "MFA_FAILURE", user.toString(), "FAILURE");
      throw new ApiError(401, "MFA_EXPIRED", "Please sign in again.");
    }
    authCookie(res, "gl_mfa", "", 0);
    revokeBrowserSession(req);
    issueSession(user, true, res);
    audit(user, "MFA_SUCCESS", user.toString(), "SUCCESS");
    return Map.of("id", user);
  }

  public void issueSession(UUID user, boolean admin, HttpServletResponse res) {
    issueSession(user, admin, false, res);
  }

  public void issueSession(UUID user, boolean admin, boolean remember, HttpServletResponse res) {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)
      revokeBrowserSession(attributes.getRequest());
    String token = randomToken();
    long ttl = admin ? 3600 : 86400;
    db.update(
        "INSERT INTO sessions(token_hash,user_id,expires_at,mfa_verified) VALUES(?,?,?,?)",
        hash(token),
        user,
        Timestamp.from(Instant.now().plusSeconds(ttl)),
        admin);
    writeCookie(res, "gl_session", token, admin || remember ? ttl : null);
    audit(user, "LOGIN_SUCCESS", user.toString(), "SUCCESS");
  }

  public void authCookie(HttpServletResponse res, String name, String value, long seconds) {
    writeCookie(res, name, value, seconds);
  }

  private void writeCookie(HttpServletResponse res, String logicalName, String value, Long seconds) {
    String name = SessionCookies.name(logicalName, secure);
    var cookie = ResponseCookie.from(name, value)
        .httpOnly(true).secure(secure).sameSite("Lax").path("/");
    if (seconds != null) cookie.maxAge(seconds);
    res.addHeader("Set-Cookie", cookie.build().toString());
    if (!name.equals(logicalName))
      res.addHeader("Set-Cookie", ResponseCookie.from(logicalName, "")
          .httpOnly(true).secure(secure).sameSite("Lax").path("/").maxAge(0).build().toString());
  }

  @Transactional
  public Map<String, Object> logout(HttpServletRequest req, HttpServletResponse res) {
    String session = SessionCookies.read(req, "gl_session");
    String challenge = SessionCookies.read(req, "gl_mfa");
    UUID actor = null;
    if (!session.isEmpty()) {
      var owners = db.queryForList("SELECT user_id FROM sessions WHERE token_hash=?",
          UUID.class, hash(session));
      if (!owners.isEmpty()) actor = owners.get(0);
    }
    if (actor == null && !challenge.isEmpty()) {
      var owners = db.queryForList("SELECT user_id FROM login_challenges WHERE token_hash=?",
          UUID.class, hash(challenge));
      if (!owners.isEmpty()) actor = owners.get(0);
    }
    int revoked = 0;
    if (!challenge.isEmpty())
      revoked += db.update("DELETE FROM login_challenges WHERE token_hash=?", hash(challenge));
    if (!session.isEmpty())
      revoked += db.update("DELETE FROM sessions WHERE token_hash=?", hash(session));
    authCookie(res, "gl_mfa", "", 0);
    authCookie(res, "gl_session", "", 0);
    if (revoked > 0)
      audit(actor, "SESSION_REVOKED", actor == null ? "AUTH" : actor.toString(), "SUCCESS");
    return Map.of("ok", true);
  }

  private void revokeBrowserSession(HttpServletRequest request) {
    if (request == null) return;
    String token = SessionCookies.read(request, "gl_session");
    if (!token.isEmpty()) db.update("DELETE FROM sessions WHERE token_hash=?", hash(token));
  }

  public UserResponse me(HttpServletRequest r) {
    var p = security.principal(r);
    UUID u = (UUID) p.get("id");
    var profile = db.queryForMap(
        "SELECT user_id,slug,display_name,headline,bio,technology,category,availability_status,"
            + "booked_until,github_url,linkedin_url,website_url,country,time_zone,languages,"
            + "approval_status,moderation_reason,updated_at,availability_confirmed_at"
            + " FROM developer_profiles WHERE user_id=?", u);
    return new UserResponse(u, (String) p.get("email"), (String) profile.get("display_name"),
        AccountExportResponse.Profile.from(profile),
        db.queryForList("SELECT role FROM user_roles WHERE user_id=?", String.class, u),
        p.get("email_verified_at") != null,
        db.queryForObject("SELECT active_slot_limit FROM showcase_entitlements WHERE user_id=?",
            Integer.class, u));
  }

  @Transactional
  public Map<String, Object> reset(Map<String, Object> b) {
    String e = email(b, "email");
    security.limitIdentity(e, "reset");
    var rows = db.queryForList("SELECT id FROM users WHERE email=? AND account_status='ACTIVE'", e);
    if (!rows.isEmpty()) mail.token("PASSWORD_RESET", (UUID) rows.get(0).get("id"), null, e);
    return Map.of("message", "If an account exists, a reset email will be sent.");
  }

  public Map<String, Object> confirmationContext(Map<String, Object> body) {
    var token = confirmationToken(text(body, "token", 20, 200), false);
    if (((Timestamp) token.get("expires_at")).toInstant().isBefore(Instant.now()))
      throw new ApiError(410, "TOKEN_EXPIRED", "This confirmation link has expired.");
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("kind", token.get("kind"));
    result.put("used", token.get("used_at") != null);
    if (token.get("inquiry_id") != null) {
      var inquiry =
          db.queryForMap(
              "SELECT i.description,i.current_status AS status,p.title AS"
                  + " \"projectTitle\",d.display_name AS builder FROM inquiries i JOIN products p"
                  + " ON p.id=i.reference_product_id JOIN developer_profiles d ON"
                  + " d.user_id=i.developer_user_id WHERE i.id=?",
              token.get("inquiry_id"));
      result.put("inquiry", inquiry);
    }
    return result;
  }

  public Map<String, Object> confirmationToken(String raw, boolean lock) {
    var tokens =
        db.queryForList(
            "SELECT id,kind,user_id,inquiry_id,expires_at,used_at FROM account_tokens WHERE token_hash=?" + (lock ? " FOR UPDATE" : ""),
            hash(raw));
    if (tokens.isEmpty())
      throw new ApiError(400, "INVALID_TOKEN", "This confirmation link is invalid.");
    var token = tokens.get(0);
    if (token.get("used_at") == null
        && ((Timestamp) token.get("expires_at")).toInstant().isBefore(Instant.now()))
      throw new ApiError(
          410,
          "TOKEN_EXPIRED",
          "This confirmation link has expired. Request a fresh link from your workspace.");
    return token;
  }

  @Transactional
  public Map<String, Object> confirm(Map<String, Object> body, HttpServletRequest request) {
    String raw = text(body, "token", 20, 200);
    var token = confirmationToken(raw, false);
    if ("ACCOUNT_DELETION".equals(token.get("kind"))
        || "PASSWORD_RESET".equals(token.get("kind")))
      db.queryForMap("SELECT id FROM users WHERE id=? FOR UPDATE", token.get("user_id"));
    if (token.get("inquiry_id") != null)
      db.queryForMap("SELECT id FROM inquiries WHERE id=? FOR UPDATE", token.get("inquiry_id"));
    token = confirmationToken(raw, true);
    String kind = (String) token.get("kind");
    String required =
        switch (request.getRequestURI()) {
          case "/api/v1/auth/verify-email" -> "EMAIL_VERIFICATION";
          case "/api/v1/auth/password-reset/confirm" -> "PASSWORD_RESET";
          case "/api/v1/inquiries/confirm-email" -> "CLIENT_INQUIRY_CONFIRMATION";
          default -> kind;
        };
    if (!required.equals(kind))
      throw new ApiError(400, "INVALID_TOKEN", "This link is for a different action.");
    if (token.get("used_at") != null) {
      if ("PASSWORD_RESET".equals(kind))
        throw new ApiError(400, "INVALID_TOKEN", "This confirmation link is invalid.");
      return Map.of("ok", true, "alreadyRecorded", true);
    }
    UUID user = (UUID) token.get("user_id"), inquiryId = (UUID) token.get("inquiry_id");
    switch (kind) {
      case "EMAIL_VERIFICATION" -> {
        if (db.update(
                "UPDATE users SET email_verified_at=COALESCE(email_verified_at,now()) WHERE id=?"
                    + " AND account_status='ACTIVE'",
                user)
            != 1) throw new ApiError(409, "ACCOUNT_UNAVAILABLE", "This account is unavailable.");
      }
      case "PASSWORD_RESET" -> {
        String password = password(body);
        if (db.update(
                "UPDATE users SET password_hash=? WHERE id=? AND account_status='ACTIVE'",
                passwords.encode(password),
                user)
            != 1) throw new ApiError(409, "ACCOUNT_UNAVAILABLE", "This account is unavailable.");
        db.update("DELETE FROM sessions WHERE user_id=?", user);
        db.update("DELETE FROM login_challenges WHERE user_id=?", user);
        audit(user, "PASSWORD_RESET", user.toString(), "SUCCESS");
        db.update(
            "UPDATE account_tokens SET used_at=now() WHERE user_id=? AND kind='PASSWORD_RESET'",
            user);
      }
      case "ACCOUNT_DELETION" -> {
        if (!"DELETE".equals(body.get("decision")))
          throw new ApiError(400, "CONFIRMATION_REQUIRED", "Confirm account closure explicitly.");
        db.queryForMap("SELECT id FROM users WHERE id=? FOR UPDATE", user);
        if (security.role(user, "ADMIN"))
          throw new ApiError(
              409,
              "ADMIN_ACCOUNT_PROTECTED",
              "The sole administrator cannot close their account through self-service.");
        if (db.queryForObject("SELECT count(*) FROM maintenance_subscriptions s JOIN delivery_engagements e ON e.id=s.engagement_id WHERE NOT s.cancel_confirmed AND NOT(s.status='REJECTED' AND s.provider_subscription_id IS NULL) AND (s.payer_id=? OR e.buyer_user_id=? OR e.builder_user_id=? OR e.business_id IN (SELECT id FROM businesses WHERE owner_id=?) OR e.team_id IN (SELECT id FROM teams WHERE owner_id=?))", Integer.class, user,user,user,user,user)>0)
          throw new ApiError(409,"RECURRING_BILLING_ACTIVE","Cancel future maintenance billing and confirm the provider stopped it before closing this account. Ask the operator to recover uncertain billing.");
        if (hosting != null && hosting.blocksAccountDeletion(user))
          throw new ApiError(409,"HOSTED_DEMOS_ACTIVE","Withdraw hosted demos and confirm removal before closing this account. Ask the operator to recover uncertain deployments.");
        db.update("INSERT INTO deletion_requests(user_id) VALUES(?) ON CONFLICT DO NOTHING", user);
        db.update("UPDATE users SET account_status='DELETED' WHERE id=?", user);
        db.update("DELETE FROM sessions WHERE user_id=?", user);
        db.update("DELETE FROM login_challenges WHERE user_id=?", user);
        db.update("UPDATE account_tokens SET used_at=now() WHERE user_id=?", user);
        event("account_deletion_requested", user);
        audit(user, "ACCOUNT_DELETED", user.toString(), "SUCCESS");
      }
      case "CLIENT_INQUIRY_CONFIRMATION" -> {
        var inquiry = db.queryForMap("SELECT id,developer_user_id,reference_product_id,client_email,current_status,moderation_status FROM inquiries WHERE id=? FOR UPDATE", inquiryId);
        InquiryPolicy.requireClear(inquiry);
        InquiryPolicy.requireSender(db, (String) inquiry.get("client_email"));
        if (!inquiry.get("current_status").equals("CREATED_UNVERIFIED"))
          throw new ApiError(
              409, "INVALID_STATE_TRANSITION", "This inquiry cannot be confirmed now.");
        var products =
            db.queryForList(
                ProductRepository.SELECT
                    + " WHERE p.id=? AND "
                    + ProductRepository.PUBLIC
                    + " AND p.available_for_similar_work=true AND"
                    + " d.availability_status<>'NOT_ACCEPTING' FOR SHARE OF p,u,d",
                inquiry.get("reference_product_id"));
        if (products.isEmpty())
          throw new ApiError(
              409,
              "PRODUCT_NOT_AVAILABLE",
              "The project or builder is no longer accepting new inquiries.");
        db.update(
            "UPDATE inquiries SET"
                + " current_status='INQUIRY_RECEIVED',email_confirmed_at=now(),updated_at=now()"
                + " WHERE id=?",
            inquiryId);
        db.update(
            "INSERT INTO inquiry_events(id,inquiry_id,event_type,actor_type)"
                + " VALUES(?,?,'INQUIRY_RECEIVED','CLIENT_TOKEN')",
            id(),
            inquiryId);
        mail.notify((UUID) inquiry.get("developer_user_id"), "A new qualified inquiry is ready.");
        event("inquiry_email_confirmed", inquiryId);
      }
      case "HIRE_CONFIRMATION", "COMPLETION_CONFIRMATION" -> {
        var inquiry = db.queryForMap("SELECT id,developer_user_id,reference_product_id,client_email,current_status,moderation_status FROM inquiries WHERE id=? FOR UPDATE", inquiryId);
        outcomes.applyDecision(inquiry, kind, text(body, "decision", 0, 10), "CLIENT_TOKEN", null);
      }
      case "REVIEW" -> {
        var inquiry = db.queryForMap("SELECT id,developer_user_id,reference_product_id,client_email,current_status,moderation_status FROM inquiries WHERE id=? FOR UPDATE", inquiryId);
        outcomes.submitReview(inquiry, body);
      }
      default -> throw new ApiError(400, "INVALID_TOKEN", "Unsupported confirmation link.");
    }
    db.update("UPDATE account_tokens SET used_at=now() WHERE id=?", token.get("id"));
    return Map.of("ok", true);
  }

  @Transactional
  public Map<String, Object> resend(HttpServletRequest request) {
    var user = security.principal(request);
    security.limitIdentity((String) user.get("email"), "verification");
    if (user.get("email_verified_at") == null)
      mail.token("EMAIL_VERIFICATION", (UUID) user.get("id"), null, (String) user.get("email"));
    return Map.of("ok", true);
  }

  String password(Map<String, Object> body) {
    String value = Objects.toString(body.get("password"), "");
    if (value.length() < 12 || value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new ApiError(
          400,
          "VALIDATION_ERROR",
          "Use at least 12 characters and at most 72 UTF-8 bytes for your password.");
    return value;
  }

  @Transactional
  public Map<String, Object> deletion(HttpServletRequest r) {
    var principal = security.principal(r);
    UUID user = (UUID) principal.get("id");
    if (security.role(user, "ADMIN"))
      throw new ApiError(
          409,
          "ADMIN_ACCOUNT_PROTECTED",
          "The sole administrator cannot close their account through self-service.");
    security.limitIdentity((String) principal.get("email"), "deletion");
    db.queryForMap("SELECT id FROM users WHERE id=? FOR UPDATE", user);
    db.update(
        "UPDATE account_tokens SET used_at=now() WHERE user_id=? AND kind='ACCOUNT_DELETION' AND"
            + " used_at IS NULL",
        user);
    mail.token("ACCOUNT_DELETION", user, null, (String) principal.get("email"));
    return Map.of("status", "EMAIL_CONFIRMATION_REQUIRED");
  }

  void event(String name, UUID entity) {
    db.update(
        "INSERT INTO analytics_events(id,event_name,entity_id) VALUES(?,?,?)", id(), name, entity);
  }
}
