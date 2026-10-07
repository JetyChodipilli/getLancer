package com.getlancer.moderation;

import static com.getlancer.shared.Support.id;
import static com.getlancer.shared.Support.text;
import static com.getlancer.shared.Support.uuid;

import com.getlancer.admin.AdminService;
import com.getlancer.notifications.Mail;
import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import com.getlancer.shared.Pages;
import com.getlancer.shared.Rules;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModerationService {
  final JdbcTemplate db;
  final Security security;
  final AdminService admin;
  final Mail mail;

  public ModerationService(JdbcTemplate db, Security security, AdminService admin, Mail mail) {
    this.db = db;
    this.security = security;
    this.admin = admin;
    this.mail = mail;
  }

  @Transactional
  public Map<String, Object> triage(UUID id, Map<String, Object> body, HttpServletRequest r) {
    UUID actor = security.admin(r);
    String severity = text(body, "severity", 3, 12),
        status = text(body, "status", 4, 20),
        reason = text(body, "reason", 3, 2000);
    if (!Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW").contains(severity)
        || !Set.of("OPEN", "TRIAGED", "UNDER_REVIEW").contains(status))
      throw new ApiError(400, "VALIDATION_ERROR", "Choose a valid priority and review stage.");
    if (db.update(
            "UPDATE reports SET severity=?,status=?,triage_note=?,updated_at=now() WHERE id=? AND"
                + " status<>'RESOLVED'",
            severity,
            status,
            reason,
            id)
        != 1)
      throw new ApiError(
          409, "INVALID_STATE_TRANSITION", "This report is missing or already resolved.");
    admin.audit(actor, "REPORT", id, "TRIAGE", severity + ": " + reason);
    return Map.of("ok", true);
  }

  public boolean affected(Map<String, Object> decision, UUID user) {
    UUID target = (UUID) decision.get("target_id");
    return switch ((String) decision.get("target_type")) {
      case "USER", "ACCOUNT" -> target.equals(user);
      case "PRODUCT" ->
          db.queryForObject(
                  "SELECT count(*) FROM products WHERE id=? AND owner_user_id=?",
                  Integer.class,
                  target,
                  user)
              > 0;
      case "REVIEW" ->
          db.queryForObject(
                  "SELECT count(*) FROM reviews r JOIN inquiries i ON i.id=r.inquiry_id LEFT JOIN"
                      + " users u ON u.email=i.client_email AND u.email_verified_at IS NOT NULL"
                      + " WHERE r.id=? AND (r.developer_user_id=? OR u.id=?)",
                  Integer.class,
                  target,
                  user,
                  user)
              > 0;
      case "INQUIRY" ->
          db.queryForObject(
                  "SELECT count(*) FROM inquiries i LEFT JOIN users u ON u.email=i.client_email AND"
                      + " u.email_verified_at IS NOT NULL WHERE i.id=? AND (i.developer_user_id=?"
                      + " OR u.id=?)",
                  Integer.class,
                  target,
                  user,
                  user)
              > 0;
      default -> false;
    };
  }

  public Map<String, Object> decisions(HttpServletRequest r) {
    UUID user = security.user(r);
    return Pages.query(
        db,
        r,
        "SELECT m.id,m.target_type,m.target_id,m.action,m.reason,m.created_at FROM"
            + " moderation_actions m WHERE (m.target_type IN ('USER','ACCOUNT') AND m.target_id=?)"
            + " OR (m.target_type='PRODUCT' AND m.target_id IN (SELECT id FROM products WHERE"
            + " owner_user_id=?)) OR (m.target_type='INQUIRY' AND m.target_id IN (SELECT i.id FROM"
            + " inquiries i WHERE i.developer_user_id=? OR i.client_email=(SELECT email FROM users"
            + " WHERE id=? AND email_verified_at IS NOT NULL))) OR (m.target_type='REVIEW' AND"
            + " m.target_id IN (SELECT v.id FROM reviews v JOIN inquiries i ON i.id=v.inquiry_id"
            + " WHERE v.developer_user_id=? OR i.client_email=(SELECT email FROM users WHERE id=?"
            + " AND email_verified_at IS NOT NULL))) ORDER BY m.created_at DESC,m.id",
        user,
        user,
        user,
        user,
        user,
        user);
  }

  @Transactional
  public Map<String, Object> appeal(Map<String, Object> body, HttpServletRequest r) {
    UUID user = security.user(r), decision = uuid(body.get("decisionId"));
    var rows = db.queryForList("SELECT id,admin_id,target_type,target_id,action,reason,created_at FROM moderation_actions WHERE id=?", decision);
    if (rows.isEmpty() || !affected(rows.get(0), user))
      throw new ApiError(404, "NOT_FOUND", "Decision not found.");
    String evidence = text(body, "evidenceUrl", 0, 1000);
    if (!evidence.isBlank()) Rules.safeUrl(evidence);
    UUID id = id();
    db.update(
        "INSERT INTO moderation_appeals(id,decision_id,appellant_id,statement,evidence_url)"
            + " VALUES(?,?,?,?,?)",
        id,
        decision,
        user,
        text(body, "statement", 20, 3000),
        evidence);
    return Map.of("id", id, "status", "OPEN");
  }

  public Map<String, Object> ownAppeals(HttpServletRequest r) {
    return Pages.query(
        db,
        r,
        "SELECT id,decision_id,statement,status,resolution,created_at,resolved_at FROM"
            + " moderation_appeals WHERE appellant_id=? ORDER BY created_at DESC,id",
        security.user(r));
  }

  public Map<String, Object> appeals(HttpServletRequest r) {
    security.admin(r);
    return Pages.query(
        db,
        r,
        "SELECT a.id,a.decision_id,a.appellant_id,a.statement,a.evidence_url,a.status,a.resolution,a.reviewer_id,a.created_at,a.resolved_at,m.target_type,m.target_id,m.action AS original_action,m.reason AS"
            + " original_reason,m.admin_id AS original_moderator FROM moderation_appeals a JOIN"
            + " moderation_actions m ON m.id=a.decision_id ORDER BY CASE WHEN a.status IN"
            + " ('OPEN','UNDER_REVIEW') THEN 0 ELSE 1 END,a.created_at,a.id");
  }

  @Transactional
  public Map<String, Object> appealDecision(
      UUID id, Map<String, Object> body, HttpServletRequest r) {
    UUID actor = security.admin(r);
    String state = text(body, "status", 4, 20), reason = text(body, "reason", 3, 2000);
    if (!Set.of("UNDER_REVIEW", "UPHELD", "OVERTURNED").contains(state))
      throw new ApiError(400, "VALIDATION_ERROR", "Choose a review outcome.");
    var rows = db.queryForList("SELECT id,decision_id,appellant_id,statement,evidence_url,status,resolution,reviewer_id,created_at,resolved_at FROM moderation_appeals WHERE id=? FOR UPDATE", id);
    if (rows.isEmpty()) throw new ApiError(404, "NOT_FOUND", "Appeal not found.");
    var appeal = rows.get(0);
    if (Set.of("UPHELD", "OVERTURNED").contains(appeal.get("status")))
      throw new ApiError(409, "INVALID_STATE_TRANSITION", "This appeal has already been resolved.");
    var original =
        db.queryForMap("SELECT id,admin_id,target_type,target_id,action,reason,created_at FROM moderation_actions WHERE id=?", appeal.get("decision_id"));
    if (state.equals("OVERTURNED")) {
      UUID target = (UUID) original.get("target_id");
      switch ((String) original.get("target_type")) {
        case "ACCOUNT" -> admin.accountAction(target, "restore", Map.of("reason", reason), r);
        case "USER" -> {
          db.update(
              "UPDATE developer_profiles SET approval_status='PROFILE_PENDING' WHERE user_id=? AND"
                  + " approval_status='CHANGES_REQUESTED'",
              target);
          admin.profileAction(target, "approve", Map.of("reason", reason), r);
        }
        case "INQUIRY" -> admin.inquiryAction(target, "restore", Map.of("reason", reason), r);
        case "REVIEW" -> admin.reviewAction(target, "publish", Map.of("reason", reason), r);
        case "PRODUCT" -> {
          var owner = db.queryForList("SELECT owner_user_id FROM products WHERE id=?", target);
          if (owner.isEmpty()) throw new ApiError(404, "NOT_FOUND", "Project not found.");
          db.queryForMap(
              "SELECT user_id,active_slot_limit,source FROM showcase_entitlements WHERE user_id=? FOR UPDATE",
              owner.get(0).get("owner_user_id"));
          db.update(
              "UPDATE products SET"
                  + " approval_status='PENDING_REVIEW',lifecycle_status='DRAFT',updated_at=now()"
                  + " WHERE id=? AND approval_status IN"
                  + " ('REJECTED','SUSPENDED','CHANGES_REQUESTED')",
              target);
          admin.productAction(target, "approve", Map.of("reason", reason), r);
        }
        default ->
            throw new ApiError(
                409, "INVALID_STATE_TRANSITION", "This decision requires manual recovery.");
      }
    }
    db.update(
        "UPDATE moderation_appeals SET status=?,resolution=?,reviewer_id=?,resolved_at=CASE WHEN"
            + " ?='UNDER_REVIEW' THEN NULL ELSE now() END WHERE id=?",
        state,
        reason,
        actor,
        state,
        id);
    admin.audit(actor, "APPEAL", id, state, reason);
    mail.notify((UUID) appeal.get("appellant_id"), "Your appeal is " + state + ". " + reason);
    return Map.of("ok", true);
  }

  public Map<String, Object> failedMail(HttpServletRequest r) {
    security.admin(r);
    return Pages.query(
        db,
        r,
        "SELECT id,subject,attempts,created_at,body='[expired]' AS expired FROM email_outbox WHERE"
            + " sent_at IS NULL AND attempts>=8 ORDER BY created_at,id");
  }

  @Transactional
  public Map<String, Object> retry(UUID id, Map<String, Object> body, HttpServletRequest r) {
    UUID actor = security.admin(r);
    String reason = text(body, "reason", 3, 2000);
    if (db.update(
            "UPDATE email_outbox SET attempts=0,next_attempt_at=now() WHERE id=? AND sent_at IS"
                + " NULL AND attempts>=8 AND body<>'[expired]' AND created_at>now()-interval '24"
                + " hours'",
            id)
        != 1)
      throw new ApiError(
          409,
          "MAIL_RENEWAL_REQUIRED",
          "This job cannot be retried. Ask the user to request a fresh confirmation link.");
    admin.audit(actor, "EMAIL", id, "RETRY", reason);
    return Map.of("ok", true);
  }
}
