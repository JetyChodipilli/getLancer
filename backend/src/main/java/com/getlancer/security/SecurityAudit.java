package com.getlancer.security;

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

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(UUID actor, String event, String target, String result) {
    record(actor, event, target, result, RequestIds.current());
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(UUID actor, String event, String target, String result, String requestId) {
    if (!EVENTS.contains(event) || !RESULTS.contains(result))
      throw new IllegalArgumentException("Invalid security audit event");
    String safeTarget = target == null ? "" : target;
    if (!safeTarget.matches("[A-Za-z0-9_{}:/|.-]{0,180}"))
      throw new IllegalArgumentException("Invalid security audit target");
    UUID.fromString(requestId);
    // Anonymous denial floods are sampled after a bounded shared budget. Identified MFA,
    // authenticated administration, exports, and successful session events are never sampled.
    if ((event.equals("AUTHORIZATION_DENIED") || actor == null && event.equals("LOGIN_FAILURE"))
        && !rateLimits.allow("security-audit-anonymous-denials", 1000)) return;
    db.update("INSERT INTO security_audit_events(id,actor_id,event,target,result,request_id)"
        + " VALUES(?,?,?,?,?,?)", UUID.randomUUID(), actor, event, safeTarget, result, requestId);
  }
}
