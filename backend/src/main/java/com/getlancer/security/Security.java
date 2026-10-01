package com.getlancer.security;

import static com.getlancer.shared.Support.*;

import com.getlancer.shared.ApiError;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.*;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class Security extends OncePerRequestFilter {
  @org.springframework.beans.factory.annotation.Autowired RateLimits rateLimits;

  @Value("${app.proxy-secret:}")
  String proxySecret = "";

  @Value("${app.auth-rate-limit:10}")
  int authLimit = 10;

  @Value("${app.email-rate-limit:3}")
  int emailLimit = 3;

  public void limitIdentity(String identity, String kind) {
    if (rateLimits != null
        && !rateLimits.allow(kind + ":" + identity, kind.equals("login") ? authLimit : emailLimit))
      throw new ApiError(429, "RATE_LIMITED", "Too many attempts. Try again in a minute.");
  }

  String clientAddress(HttpServletRequest req) {
    String supplied = Objects.toString(req.getHeader("X-GetLancer-Proxy"), "");
    String forwarded = req.getHeader("X-GetLancer-Client-IP");
    if (!proxySecret.isBlank()
        && java.security.MessageDigest.isEqual(
            proxySecret.getBytes(java.nio.charset.StandardCharsets.UTF_8),
            supplied.getBytes(java.nio.charset.StandardCharsets.UTF_8))
        && forwarded != null
        && forwarded.matches("[0-9a-fA-F:.]{3,45}")) return forwarded;
    return req.getRemoteAddr();
  }

  final JdbcTemplate db;
  final String origin;
  final int limit;

  public Security(
      JdbcTemplate db,
      @Value("${app.origin}") String origin,
      @Value("${app.rate-limit}") int limit) {
    this.db = db;
    this.origin = origin;
    this.limit = limit;
  }

  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    res.setHeader("X-Content-Type-Options", "nosniff");
    res.setHeader("Referrer-Policy", "no-referrer");
    res.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
    res.setHeader("Cache-Control", "no-store");
    res.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
    if (req.isSecure()) res.setHeader("Strict-Transport-Security", "max-age=31536000");
    boolean razorpayWebhook = req.getMethod().equals("POST")
        && req.getRequestURI().equals("/api/v1/payments/razorpay/webhook");
    String o = req.getHeader("Origin");
    if (origin.equals(o)) {
      res.setHeader("Access-Control-Allow-Origin", origin);
      res.setHeader("Access-Control-Allow-Credentials", "true");
      res.setHeader("Vary", "Origin");
    }
    if (req.getMethod().equals("OPTIONS")) {
      if (!origin.equals(o)) {
        res.setStatus(403);
        return;
      }
      res.setHeader(
          "Access-Control-Allow-Headers", "Content-Type,X-Requested-With,Idempotency-Key");
      res.setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS");
      res.setStatus(204);
      return;
    }
    if (!Set.of("GET", "HEAD").contains(req.getMethod())) {
      if (!razorpayWebhook
          && (!origin.equals(o) || !"getlancer".equals(req.getHeader("X-Requested-With")))) {
        deny(res, 403, "FORBIDDEN", "Invalid request origin.");
        return;
      }
      if (req.getContentLengthLong() > (razorpayWebhook ? 64 * 1024 : 6 * 1024 * 1024)) {
        deny(res, 413, "PAYLOAD_TOO_LARGE", "Request too large.");
        return;
      }
      String bucket =
          hash(
              clientAddress(req)
                  + ":"
                  + req.getRequestURI().replaceAll("[0-9a-fA-F]{8}-[0-9a-fA-F-]{27,}", "{id}"));
      Integer hits =
          db.queryForObject(
              "INSERT INTO rate_buckets(bucket,hits,expires_at) VALUES(?,1,now()+interval '1"
                  + " minute') ON CONFLICT(bucket) DO UPDATE SET hits=CASE WHEN"
                  + " rate_buckets.expires_at<now() THEN 1 ELSE rate_buckets.hits+1"
                  + " END,expires_at=CASE WHEN rate_buckets.expires_at<now() THEN now()+interval '1"
                  + " minute' ELSE rate_buckets.expires_at END RETURNING hits",
              Integer.class,
              bucket);
      if (hits != null && hits > limit) {
        res.setHeader("Retry-After", "60");
        deny(res, 429, "RATE_LIMITED", "Too many requests. Try again in a minute.");
        return;
      }
    }
    if (!Set.of("GET", "HEAD").contains(req.getMethod())
        && !SessionCookies.read(req, "gl_session").isEmpty()
        && rateLimits != null
        && !rateLimits.allow(
            "session:"
                + hash(SessionCookies.read(req, "gl_session"))
                + ":"
                + req.getRequestURI().replaceAll("[0-9a-fA-F]{8}-[0-9a-fA-F-]{27,}", "{id}"),
            limit)) {
      res.setHeader("Retry-After", "60");
      deny(res, 429, "RATE_LIMITED", "Too many requests. Try again in a minute.");
      return;
    }
    chain.doFilter(req, res);
  }

  void deny(HttpServletResponse r, int status, String code, String message) throws IOException {
    r.setStatus(status);
    r.setContentType("application/json");
    r.getWriter().write("{\"error\":{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}}");
  }

  public Map<String, Object> optionalPrincipal(HttpServletRequest r) {
    String token = SessionCookies.read(r, "gl_session");
    if (token.isEmpty()) return null;
    var rows =
        db.queryForList(
            "SELECT u.*,s.mfa_verified FROM users u JOIN sessions s ON s.user_id=u.id WHERE"
                + " s.token_hash=? AND s.expires_at>now() AND u.account_status='ACTIVE'",
            hash(token));
    return rows.isEmpty() ? null : rows.get(0);
  }

  public Map<String, Object> principal(HttpServletRequest r) {
    var p = optionalPrincipal(r);
    if (p == null) throw new ApiError(401, "UNAUTHENTICATED", "Please log in to continue.");
    return p;
  }

  public UUID optionalUser(HttpServletRequest r) {
    var p = optionalPrincipal(r);
    return p == null ? null : (UUID) p.get("id");
  }

  public UUID user(HttpServletRequest r) {
    return (UUID) principal(r).get("id");
  }

  public boolean role(UUID user, String role) {
    return db.queryForObject(
            "SELECT count(*) FROM user_roles WHERE user_id=? AND role=?", Integer.class, user, role)
        > 0;
  }

  public UUID admin(HttpServletRequest r) {
    var p = principal(r);
    UUID u = (UUID) p.get("id");
    if (!role(u, "ADMIN") || !Boolean.TRUE.equals(p.get("mfa_verified")))
      throw new ApiError(403, "FORBIDDEN", "Administrator authentication is required.");
    return u;
  }

  public UUID developer(HttpServletRequest r, boolean approved) {
    var p = principal(r);
    UUID u = (UUID) p.get("id");
    if (!role(u, "DEVELOPER"))
      throw new ApiError(403, "FORBIDDEN", "A builder profile is required.");
    if (approved
        && (p.get("email_verified_at") == null
            || db.queryForObject(
                    "SELECT count(*) FROM developer_profiles WHERE user_id=? AND"
                        + " approval_status='APPROVED'",
                    Integer.class,
                    u)
                == 0))
      throw new ApiError(
          403, "FORBIDDEN", "Your email and builder profile must be approved first.");
    return u;
  }
}
