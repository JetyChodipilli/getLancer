package com.getlancer.accounts;

import static com.getlancer.shared.Support.*;

import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
public class AccountService {
  final JdbcTemplate db;
  final Security security;

  public AccountService(JdbcTemplate db, Security security) {
    this.db = db;
    this.security = security;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> export(HttpServletRequest request) {
    var user = security.principal(request);
    UUID id = (UUID) user.get("id");
    if (user.get("email_verified_at") == null)
      throw new ApiError(
          403, "EMAIL_NOT_VERIFIED", "Confirm your email before exporting account data.");
    Map<String, Object> result = new LinkedHashMap<>();
    result.put(
        "account",
        db.queryForMap("SELECT id,email,email_verified_at,created_at FROM users WHERE id=?", id));
    result.put("profile", db.queryForMap("SELECT * FROM developer_profiles WHERE user_id=?", id));
    result.put(
        "products",
        db.queryForList(
            "SELECT"
                + " id,slug,title,summary,description,contribution_text,approval_status,lifecycle_status,created_at"
                + " FROM products WHERE owner_user_id=?",
            id));
    result.put(
        "requests",
        db.queryForList(
            "SELECT"
                + " id,client_email,client_name,description,budget_band,timeline_band,current_status,created_at"
                + " FROM inquiries WHERE client_email=? OR (developer_user_id=? AND"
                + " email_confirmed_at IS NOT NULL AND moderation_status='CLEAR')",
            user.get("email"),
            id));
    result.put(
        "savedProducts",
        db.queryForList("SELECT product_id,created_at FROM saved_products WHERE user_id=?", id));
    result.put(
        "legalAcceptances",
        db.queryForList(
            "SELECT document_version,accepted_at FROM legal_acceptances WHERE user_id=?", id));
    result.put(
        "teamMemberships",
        db.queryForList(
            "SELECT m.team_id,m.role,m.membership_type,m.expires_at,m.project_label,t.name FROM"
                + " team_members m JOIN teams t ON t.id=m.team_id WHERE m.user_id=?",
            id));
    result.put(
        "teamInvitations",
        db.queryForList(
            "SELECT id,team_id,role,membership_type,status,expires_at,project_label FROM"
                + " team_invitations WHERE user_id=?",
            id));
    result.put(
        "teamApplications",
        db.queryForList(
            "SELECT id,team_id,role_id,message,status FROM team_applications WHERE user_id=?", id));
    result.put(
        "teamRequests",
        db.queryForList(
            "SELECT id,team_id,title,description,budget,timeline,status FROM team_leads WHERE"
                + " client_id=?",
            id));
    result.put(
        "teamProductConsent",
        db.queryForList(
            "SELECT team_id,product_id,created_at FROM team_projects WHERE consented_by=?", id));
    result.put(
        "businessMemberships",
        db.queryForList(
            "SELECT m.business_id,m.role,b.name FROM business_members m JOIN businesses b ON"
                + " b.id=m.business_id WHERE m.user_id=?",
            id));
    result.put(
        "businessInvitations",
        db.queryForList(
            "SELECT id,business_id,status,expires_at FROM business_invitations WHERE user_id=?",
            id));
    result.put(
        "businessRequests",
        db.queryForList(
            "SELECT"
                + " r.id,r.business_id,r.title,r.description,r.category,r.technology,r.budget,r.timeline,r.status"
                + " FROM business_requests r WHERE r.created_by=? AND EXISTS(SELECT 1 FROM"
                + " business_members m WHERE m.business_id=r.business_id AND m.user_id=?)",
            id,
            id));
    result.put(
        "savedTalent",
        db.queryForList(
            "SELECT e.id,e.list_id,e.kind,e.builder_id,e.team_id FROM talent_entries e JOIN"
                + " talent_lists l ON l.id=e.list_id WHERE e.created_by=? AND EXISTS(SELECT 1 FROM"
                + " business_members m WHERE m.business_id=l.business_id AND m.user_id=?)",
            id,
            id));
    result.put(
        "requestShortlist",
        db.queryForList(
            "SELECT s.id,s.request_id,s.kind,s.builder_id,s.team_id,s.reason,s.source FROM"
                + " request_shortlist s JOIN business_requests r ON r.id=s.request_id WHERE"
                + " s.created_by=? AND EXISTS(SELECT 1 FROM business_members m WHERE"
                + " m.business_id=r.business_id AND m.user_id=?)",
            id,
            id));
    return result;
  }

  public Map<String, Object> read(UUID id, HttpServletRequest request) {
    if (db.update(
            "UPDATE notifications SET read_at=COALESCE(read_at,now()) WHERE id=? AND user_id=?",
            id,
            security.user(request))
        == 0) throw new ApiError(404, "NOT_FOUND", "Notification not found.");
    return Map.of("ok", true);
  }
}
