package com.getlancer.accounts;

import static com.getlancer.shared.Support.id;
import static com.getlancer.shared.Support.text;

import com.getlancer.admin.AdminService;
import com.getlancer.responses.PageResponse;
import com.getlancer.responses.PrivacyResponses;
import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import com.getlancer.shared.Pages;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrivacyService {
  final JdbcTemplate db;
  final Security security;
  final AdminService admin;

  final String version;

  final boolean approved;

  final String support;

  final String privacy;

  final String copyright;

  public PrivacyService(JdbcTemplate db, Security security, AdminService admin) {
    this(db, security, admin, "v1-draft", false, "", "", "");
  }

  @Autowired
  public PrivacyService(JdbcTemplate db, Security security, AdminService admin,
      @Value("${app.legal-version:v1-draft}") String version,
      @Value("${app.policies-approved:false}") boolean approved,
      @Value("${app.support-email:}") String support,
      @Value("${app.privacy-email:}") String privacy,
      @Value("${app.copyright-email:}") String copyright) {
    this.db = db;
    this.security = security;
    this.admin = admin;
    this.version = version; this.approved = approved; this.support = support;
    this.privacy = privacy; this.copyright = copyright;
  }

  public PrivacyResponses.Configuration config() {
    return new PrivacyResponses.Configuration(version, approved, support, privacy, copyright);
  }

  public PageResponse<PrivacyResponses.DeletionRequest> requests(HttpServletRequest r) {
    security.admin(r);
    return PageResponse.from(Pages.query(
        db,
        r,
        "SELECT d.user_id AS id,d.status,d.created_at,d.resolution,d.processed_at FROM"
            + " deletion_requests d ORDER BY d.created_at,d.user_id"), PrivacyResponses.DeletionRequest::from);
  }

  @Transactional
  public PrivacyResponses.Review review(UUID id, Map<String, Object> body, HttpServletRequest r) {
    UUID actor = security.admin(r);
    String reason = text(body, "reason", 10, 2000), action = text(body, "action", 4, 40);
    if (security.role(id, "ADMIN"))
      throw new ApiError(409, "ADMIN_ACCOUNT_PROTECTED", "The sole administrator is protected.");
    if (!Set.of("HOLD", "ANONYMIZE_PROFILE").contains(action))
      throw new ApiError(400, "VALIDATION_ERROR", "Choose a supported retention action.");
    var rows =
        db.queryForList(
            "SELECT d.status,u.email FROM deletion_requests d JOIN users u ON u.id=d.user_id WHERE"
                + " d.user_id=? AND u.account_status='DELETED' FOR UPDATE OF d,u",
            id);
    if (rows.isEmpty()) throw new ApiError(404, "NOT_FOUND", "Deletion request not found.");
    if (action.equals("ANONYMIZE_PROFILE")) {
      if (!approved)
        throw new ApiError(
            409,
            "RETENTION_POLICY_REQUIRED",
            "Approve a retention policy before processing personal data.");
      db.update(
          "UPDATE developer_profiles SET display_name='Closed"
              + " account',headline='',bio='',technology='',category='',github_url=NULL,linkedin_url=NULL,website_url='',country='',time_zone='',languages='',availability_status='NOT_ACCEPTING',booked_until=NULL,approval_status='SUSPENDED',updated_at=now()"
              + " WHERE user_id=?",
          id);
      db.update("DELETE FROM saved_components WHERE user_id=?", id);
      db.update("DELETE FROM saved_products WHERE user_id=?", id);
      db.update("DELETE FROM notifications WHERE user_id=?", id);
      db.update("DELETE FROM oauth_identities WHERE user_id=?", id);
      db.update("DELETE FROM account_tokens WHERE user_id=?", id);
      db.update("DELETE FROM login_challenges WHERE user_id=?", id);
      db.update("DELETE FROM sessions WHERE user_id=?", id);
      db.update(
          "UPDATE users SET email=?,password_hash='disabled',admin_totp=NULL,admin_totp_key_version=NULL,admin_totp_nonce=NULL,admin_totp_ciphertext=NULL WHERE id=?",
          "closed-" + id + "@example.invalid",
          id);
      db.update(
          "UPDATE email_outbox SET body='[expired]',attempts=8 WHERE recipient=? AND sent_at IS"
              + " NULL",
          rows.get(0).get("email"));
    }
    String state = action.equals("HOLD") ? "ON_HOLD" : "PROFILE_ANONYMIZED";
    db.update(
        "UPDATE deletion_requests SET status=?,resolution=?,processed_at=now(),processed_by=? WHERE"
            + " user_id=?",
        state,
        reason,
        actor,
        id);
    admin.audit(actor, "DELETION", id, action, reason);
    return new PrivacyResponses.Review(state, true);
  }
}
