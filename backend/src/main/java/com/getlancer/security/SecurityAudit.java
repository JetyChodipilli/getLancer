package com.getlancer.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Bounded events only: no request bodies, credentials, tokens, email addresses, or IP addresses. */
@Service
public class SecurityAudit {
  private static final String PENDING = SecurityAudit.class.getName() + ".pending";
  public record Pending(UUID actor, String event, String target, String result, String requestId) {}
  private static final Set<String> EVENTS = Set.of(
      "LOGIN_SUCCESS", "LOGIN_FAILURE", "MFA_CHALLENGE", "MFA_SUCCESS", "MFA_FAILURE", "MFA_LOCKED", "PASSWORD_RESET", "ACCOUNT_DELETION",
      "SESSION_REVOKED", "AUTHORIZATION_DENIED", "ADMIN_ACTION", "PRIVATE_EXPORT", "WEBHOOK_FAILURE",
      "ACCOUNT_SUSPENDED", "ACCOUNT_DELETED", "ROLE_CHANGED", "BUSINESS_MEMBER_REMOVED",
      "PAYMENT_RECONCILIATION", "PAYMENT_MANUAL_BIND", "WEBHOOK_SIGNATURE_FAILURE", "SECURITY_SETTING_CHANGE");
  private static final Set<String> RESULTS = Set.of("SUCCESS", "FAILURE", "LOCKED");
  private final JdbcTemplate db;
  private final RateLimits rateLimits;

  @Autowired
  public SecurityAudit(JdbcTemplate db, RateLimits rateLimits) { this.db = db; this.rateLimits = rateLimits; }

  /** Queue only bounded event metadata; the transport filter persists it after the domain transaction. */
  public void defer(HttpServletRequest request, String event, UUID actor, String target, String result) {
    Pending pending = new Pending(actor, event, target == null ? "" : target, result, RequestIds.get(request));
    validate(pending.event(), pending.target(), pending.result(), pending.requestId());
    List<Pending> queued = drain(request);
    if (queued.size() >= 4) throw new IllegalStateException("Too many authentication audit events");
    queued = new ArrayList<>(queued);
    queued.add(pending);
    request.setAttribute(PENDING, List.copyOf(queued));
  }

  @SuppressWarnings("unchecked")
  public List<Pending> drain(HttpServletRequest request) {
    Object queued = request.getAttribute(PENDING);
    request.removeAttribute(PENDING);
    return queued == null ? List.of() : (List<Pending>) queued;
  }

  private static void validate(String event, String target, String result, String requestId) {
    if (!EVENTS.contains(event) || !RESULTS.contains(result))
      throw new IllegalArgumentException("Invalid security audit event");
    if (!target.matches("[A-Za-z0-9_{}:/|.-]{0,180}"))
      throw new IllegalArgumentException("Invalid security audit target");
    UUID.fromString(requestId);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(UUID actor, String event, String target, String result) {
    record(actor, event, target, result, RequestIds.current());
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(UUID actor, String event, String target, String result, String requestId) {
    String safeTarget = target == null ? "" : target;
    validate(event, safeTarget, result, requestId);
    // Anonymous denial floods are sampled after a bounded shared budget. Identified MFA,
    // authenticated administration, exports, and successful session events are never sampled.
    if (actor == null && (event.equals("AUTHORIZATION_DENIED") || event.equals("LOGIN_FAILURE"))
        && !rateLimits.allowInCurrentTransaction("security-audit-anonymous-denials", 1000)) return;
    db.update("INSERT INTO security_audit_events(id,actor_id,event,target,result,request_id)"
        + " VALUES(?,?,?,?,?,?)", UUID.randomUUID(), actor, event, safeTarget, result, requestId);
  }
}
