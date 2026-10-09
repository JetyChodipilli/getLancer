package com.getlancer.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.shared.Support;
import com.getlancer.shared.Errors;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Transport controls run before authentication, including the documented browser CSRF equivalent. */
public final class BrowserSecurityFilter extends OncePerRequestFilter {
  private static final Set<String> SAFE = Set.of("GET", "HEAD", "OPTIONS");
  private static final Set<String> CORS_HEADERS = Set.of("content-type", "x-requested-with", "idempotency-key", "last-event-id");
  private static final ObjectMapper JSON = new ObjectMapper();
  private final Security security;
  private final AuthorizationService authorization;
  private final SecurityAudit audit;

  public BrowserSecurityFilter(Security security, AuthorizationService authorization, SecurityAudit audit) {
    this.security = security; this.authorization = authorization; this.audit = audit;
  }

  public boolean hasBrowserMutationProtection(HttpServletRequest request) {
    return !SAFE.contains(request.getMethod())
        && authorization.route(request.getMethod(), request.getRequestURI()) != null
        && !authorization.signedWebhook(request)
        && security.origin.equals(request.getHeader("Origin"))
        && "getlancer".equals(request.getHeader("X-Requested-With"));
  }

  @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {
    request.setAttribute(SessionCookies.SECURE_ATTRIBUTE, security.secureCookies);
    RequestIds.initialize(request, security);
    response.setHeader("X-Request-ID", RequestIds.get(request));
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("Referrer-Policy", "no-referrer");
    response.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
    response.setHeader("Cache-Control", "no-store");
    response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
    if (security.secureCookies) response.setHeader("Strict-Transport-Security", "max-age=31536000");
    if (security.origin.equals(request.getHeader("Origin"))) {
      response.setHeader("Access-Control-Allow-Origin", security.origin);
      response.setHeader("Access-Control-Allow-Credentials", "true");
      response.addHeader("Vary", "Origin");
    }
    if (request.getMethod().equals("OPTIONS")) {
      String requested = request.getHeader("Access-Control-Request-Method");
      String headers = request.getHeader("Access-Control-Request-Headers");
      boolean valid = security.origin.equals(request.getHeader("Origin")) && requested != null
          && authorization.route(requested, request.getRequestURI()) != null
          && (headers == null || java.util.Arrays.stream(headers.split(","))
              .allMatch(header -> CORS_HEADERS.contains(header.trim().toLowerCase(java.util.Locale.ROOT))));
      if (!valid) {
        writeError(request, response, 403, "FORBIDDEN", "Invalid request origin.");
        audit.record(null, "AUTHORIZATION_DENIED", "PREFLIGHT", "FAILURE", RequestIds.get(request));
        return;
      }
      response.setHeader("Access-Control-Allow-Headers", "Content-Type,X-Requested-With,Idempotency-Key,Last-Event-ID");
      response.setHeader("Access-Control-Allow-Methods", "GET,HEAD,POST,PUT,PATCH,DELETE,OPTIONS");
      response.setStatus(204); return;
    }
    var route = authorization.route(request.getMethod(), request.getRequestURI());
    String target = route == null ? "UNKNOWN_ROUTE" : route.path();
    // Bound private/unknown reads and OAuth callbacks as well as discovery. Denial audit is not an
    // unbounded public write API; the bucket does not contain attacker-selected paths or tokens.
    boolean trustedGateway = route != null && route.policy() == AuthorizationService.Policy.SERVICE
        && authorization.allowed(null, request);
    if (Set.of("GET", "HEAD").contains(request.getMethod()) && request.getRequestURI().startsWith("/api/v1/") && !trustedGateway
        && !request.getRequestURI().matches("/api/v1/(?:products|builders|teams|templates|components|college-projects)(?:/.*)?")
        && !security.rateLimits.allow("security-read:" + security.clientAddress(request), security.discoveryLimit)) {
      response.setHeader("Retry-After", "60");
      writeError(request, response, 429, "RATE_LIMITED", "Too many requests. Try again in a minute."); return;
    }
    if (Set.of("GET", "HEAD").contains(request.getMethod())
        && request.getRequestURI().matches("/api/v1/(?:products|builders|teams|templates|components|college-projects)(?:/.*)?")
        && !security.rateLimits.allow("discovery:" + security.clientAddress(request), security.discoveryLimit)) {
      response.setHeader("Retry-After", "60");
      writeError(request, response, 429, "RATE_LIMITED", "Too many discovery requests. Try again in a minute."); return;
    }
    HttpServletRequest bounded = request;
    if (!SAFE.contains(request.getMethod())) {
      boolean webhook = authorization.signedWebhook(request);
      if (!webhook && (!security.origin.equals(request.getHeader("Origin"))
          || !"getlancer".equals(request.getHeader("X-Requested-With")))) {
        writeError(request, response, 403, "FORBIDDEN", "Invalid request origin.");
        audit.record(null, "AUTHORIZATION_DENIED", target, "FAILURE", RequestIds.get(request));
        return;
      }
      boolean labMutation = request.getRequestURI().matches("/api/v1/lab-runs(?:/.*)?");
      long cap = webhook || labMutation ? 64 * 1024 : 6 * 1024 * 1024;
      if (request.getContentLengthLong() > cap) {
        writeError(request, response, 413, "PAYLOAD_TOO_LARGE", "Request too large."); return;
      }
      if (!security.rateLimits.allow("mutation:" + security.clientAddress(request) + ":" + target, security.limit)) {
        response.setHeader("Retry-After", "60");
        writeError(request, response, 429, "RATE_LIMITED", "Too many requests. Try again in a minute."); return;
      }
      String session = SessionCookies.read(request, "gl_session");
      if (!session.isEmpty() && !security.rateLimits.allow("session:" + Support.hash(session) + ":" + target, security.limit)) {
        response.setHeader("Retry-After", "60");
        writeError(request, response, 429, "RATE_LIMITED", "Too many requests. Try again in a minute."); return;
      }
      bounded = new BoundedRequest(request, cap);
    }
    try { chain.doFilter(bounded, response); }
    catch (BoundedRequest.TooLarge exception) {
      if (!response.isCommitted()) writeError(request, response, 413, "PAYLOAD_TOO_LARGE", "Request too large.");
      else throw exception;
    } finally {
      // Controller transaction interceptors have completed before control returns here.
      // Authentication never allocates an audit connection while holding its user row lock.
      for (var pending : audit.drain(request))
        audit.record(pending.actor(), pending.event(), pending.target(), pending.result(), pending.requestId());
    }
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    var actor = authentication != null && authentication.getPrincipal() instanceof GetLancerPrincipal principal
        ? principal.userId() : null;
    String event = null;
    if (Set.of("/api/v1/auth/google/callback", "/api/v1/auth/github/callback").contains(request.getRequestURI())
        && response.getStatus() == 303
        && java.util.Objects.toString(response.getHeader("Location"), "").contains("auth_error=")) {
      event = "LOGIN_FAILURE";
      target = request.getRequestURI().contains("/google/") ? "OAUTH_GOOGLE" : "OAUTH_GITHUB";
    } else if (response.getStatus() == 401 || response.getStatus() == 403) event = "AUTHORIZATION_DENIED";
    else if (authorization.signedWebhook(request) && response.getStatus() >= 400) event = "WEBHOOK_FAILURE";
    else if (route != null && route.policy() == AuthorizationService.Policy.ADMIN_MFA
        && !SAFE.contains(request.getMethod())) event = "ADMIN_ACTION";
    else if (request.getMethod().equals("GET") && route != null
        && Set.of("/api/v1/me/export", "/api/v1/admin/components/{id}", "/api/v1/me/components/{id}", "/api/v1/admin/templates/{id}/versions/{version}/package",
            "/api/v1/me/templates/{id}/versions/{version}/package",
            "/api/v1/admin/hosting/{id}/package", "/api/v1/me/hosting/{id}/package",
            "/api/v1/template-purchases/{id}/download")
            .contains(route.path())) {
      event = "PRIVATE_EXPORT";
      if (response.getStatus() < 400) target = request.getRequestURI();
    }
    if (event != null) audit.record(actor, event, target,
        event.equals("LOGIN_FAILURE") || response.getStatus() >= 400 ? "FAILURE" : "SUCCESS", RequestIds.get(request));
    if (route != null && response.getStatus() < 400 && !SAFE.contains(request.getMethod())) {
      String path = request.getRequestURI();
      String specific = null;
      if (path.matches("/api/v1/admin/accounts/[0-9a-f-]{36}/suspend")) specific = "ACCOUNT_SUSPENDED";
      else if (request.getMethod().equals("DELETE") && path.matches("/api/v1/businesses/[0-9a-f-]{36}/members/[0-9a-f-]{36}")) specific = "BUSINESS_MEMBER_REMOVED";
      else if (request.getMethod().equals("PATCH") && path.matches("/api/v1/teams/[0-9a-f-]{36}/members/[0-9a-f-]{36}")) specific = "ROLE_CHANGED";
      else if (path.endsWith("/reconcile") && path.matches(".*/(?:payments|publishing-slot-purchases|component-slot-purchases|template-purchases)/[0-9a-f-]{36}/reconcile")) specific = "PAYMENT_RECONCILIATION";
      else if (path.matches("/api/v1/admin/(?:payments|publishing-slot-purchases|component-slot-purchases|template-purchases)/[0-9a-f-]{36}/bind-order")) specific = "PAYMENT_MANUAL_BIND";
      else if (path.matches("/api/v1/admin/(?:component-slot-pricing|publishing-slots/(?:PROJECT|TEMPLATE|COMPONENT)/pricing)")) specific = "SECURITY_SETTING_CHANGE";
      if (specific != null) audit.record(actor, specific, path, "SUCCESS", RequestIds.get(request));
    }
    if (authorization.signedWebhook(request) && response.getStatus() >= 400
        && Set.of("INVALID_WEBHOOK_SIGNATURE", "INVALID_SIGNATURE").contains(
            java.util.Objects.toString(request.getAttribute(Errors.CODE_ATTRIBUTE), "")))
      audit.record(actor, "WEBHOOK_SIGNATURE_FAILURE", target, "FAILURE", RequestIds.get(request));
  }

  public static void writeError(HttpServletRequest request, HttpServletResponse response,
      int status, String code, String message) throws IOException {
    response.setStatus(status); response.setContentType("application/json");
    response.setHeader("X-Request-ID", RequestIds.get(request));
    JSON.writeValue(response.getWriter(), Map.of("error", Map.of("code", code, "message", message,
        "requestId", RequestIds.get(request), "fieldErrors", Map.of())));
  }
}
