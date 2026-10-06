package com.getlancer.security;

import com.getlancer.shared.ApiError;
import com.getlancer.shared.Support;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Domain authorization facade. HTTP policy and transport protections live in separate filters. */
@Service
public class Security {
  final JdbcTemplate db;
  final String origin;
  final int limit;
  final RateLimits rateLimits;
  final String proxySecret;
  final int authLimit;
  final int emailLimit;
  final int discoveryLimit;
  final boolean secureCookies;

  public Security(JdbcTemplate db, String origin, int limit) {
    this(db, origin, limit, new RateLimits(db), "", 10, 3, 300, false);
  }

  @Autowired
  public Security(JdbcTemplate db, @Value("${app.origin}") String origin,
      @Value("${app.rate-limit:30}") int limit, RateLimits rateLimits,
      @Value("${app.proxy-secret:}") String proxySecret,
      @Value("${app.auth-rate-limit:10}") int authLimit,
      @Value("${app.email-rate-limit:3}") int emailLimit,
      @Value("${app.discovery-rate-limit:300}") int discoveryLimit,
      @Value("${app.secure-cookie:true}") boolean secureCookies) {
    this.db = db; this.origin = origin; this.limit = limit; this.rateLimits = rateLimits;
    this.proxySecret = proxySecret; this.authLimit = authLimit; this.emailLimit = emailLimit;
    this.discoveryLimit = discoveryLimit; this.secureCookies = secureCookies;
  }

  public void limitIdentity(String identity, String kind) {
    int maximum = Set.of("login", "mfa").contains(kind) ? authLimit : emailLimit;
    if (!rateLimits.allow(kind + ":" + identity, maximum))
      throw new ApiError(429, "RATE_LIMITED", "Too many attempts. Try again in a minute.");
  }

  public boolean trustedProxy(HttpServletRequest request) {
    String supplied = Objects.toString(request.getHeader("X-GetLancer-Proxy"), "");
    return !proxySecret.isBlank() && supplied.length() <= 512
        && MessageDigest.isEqual(proxySecret.getBytes(StandardCharsets.UTF_8),
            supplied.getBytes(StandardCharsets.UTF_8));
  }

  String clientAddress(HttpServletRequest request) {
    String forwarded = request.getHeader("X-GetLancer-Client-IP");
    if (trustedProxy(request) && forwarded != null && forwarded.matches("[0-9a-fA-F:.]{3,45}")) return forwarded;
    return request.getRemoteAddr();
  }

  public GetLancerPrincipal loadPrincipal(HttpServletRequest request) {
    request.setAttribute(SessionCookies.SECURE_ATTRIBUTE, secureCookies);
    String token = SessionCookies.read(request, "gl_session");
    if (token.isEmpty()) return null;
    var rows = db.queryForList("SELECT u.id,u.email,u.email_verified_at,s.mfa_verified"
        + " FROM users u JOIN sessions s ON s.user_id=u.id WHERE s.token_hash=?"
        + " AND s.expires_at>now() AND u.account_status='ACTIVE'", Support.hash(token));
    if (rows.isEmpty()) return null;
    var row = rows.get(0);
    UUID id = (UUID) row.get("id");
    Object verified = row.get("email_verified_at");
    Instant verifiedAt = verified instanceof java.sql.Timestamp timestamp ? timestamp.toInstant()
        : verified instanceof java.time.OffsetDateTime offset ? offset.toInstant() : (Instant) verified;
    return new GetLancerPrincipal(id, (String) row.get("email"), verifiedAt,
        Boolean.TRUE.equals(row.get("mfa_verified")),
        Set.copyOf(db.queryForList("SELECT role FROM user_roles WHERE user_id=?", String.class, id)));
  }

  /** Compatibility for legacy domain rules, restricted to safe identity fields. Rechecks revocation. */
  public Map<String, Object> optionalPrincipal(HttpServletRequest request) {
    var user = loadPrincipal(request);
    if (user == null) return null;
    Map<String, Object> safe = new HashMap<>();
    safe.put("id", user.userId()); safe.put("email", user.email());
    safe.put("email_verified_at", user.emailVerifiedAt()); safe.put("mfa_verified", user.mfaVerified());
    safe.put("account_status", "ACTIVE");
    return Collections.unmodifiableMap(safe);
  }

  public Map<String, Object> principal(HttpServletRequest request) {
    var user = optionalPrincipal(request);
    if (user == null) throw new ApiError(401, "UNAUTHENTICATED", "Please log in to continue.");
    return user;
  }

  public UUID optionalUser(HttpServletRequest request) {
    var user = optionalPrincipal(request);
    return user == null ? null : (UUID) user.get("id");
  }

  public UUID user(HttpServletRequest request) { return (UUID) principal(request).get("id"); }

  public boolean role(UUID user, String role) {
    Integer count = db.queryForObject("SELECT count(*) FROM user_roles r JOIN users u ON u.id=r.user_id"
        + " WHERE r.user_id=? AND r.role=? AND u.account_status='ACTIVE'", Integer.class, user, role);
    return count != null && count > 0;
  }

  public UUID admin(HttpServletRequest request) {
    var principal = principal(request);
    UUID user = (UUID) principal.get("id");
    if (!role(user, "ADMIN") || !Boolean.TRUE.equals(principal.get("mfa_verified")))
      throw new ApiError(403, "FORBIDDEN", "Administrator authentication is required.");
    return user;
  }

  public UUID developer(HttpServletRequest request, boolean approved) {
    var principal = principal(request);
    UUID user = (UUID) principal.get("id");
    if (!role(user, "DEVELOPER")) throw new ApiError(403, "FORBIDDEN", "A builder profile is required.");
    if (approved && (principal.get("email_verified_at") == null
        || db.queryForObject("SELECT count(*) FROM developer_profiles WHERE user_id=?"
            + " AND approval_status='APPROVED'", Integer.class, user) == 0))
      throw new ApiError(403, "FORBIDDEN", "Your email and builder profile must be approved first.");
    return user;
  }
}
