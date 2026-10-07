package com.getlancer.business;

import static com.getlancer.shared.Support.id;
import static com.getlancer.shared.Support.text;

import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BusinessRepository {
  private final JdbcTemplate db;
  private final Security security;
  static final String BUSINESS =
      "SELECT b.id,b.name,b.summary,b.owner_id AS \"ownerId\",b.created_at AS \"createdAt\" FROM"
          + " businesses b ";
  static final String REQUEST =
      "SELECT r.id,r.business_id AS"
          + " \"businessId\",r.title,r.description,r.category,r.technology,r.budget,r.timeline,r.available_only"
          + " AS \"availableOnly\",r.repository_verified_only AS"
          + " \"repositoryVerifiedOnly\",r.status,r.created_by AS \"createdBy\",r.updated_at AS"
          + " \"updatedAt\",c.status AS \"conciergeStatus\" FROM business_requests r LEFT JOIN"
          + " concierge_requests c ON c.request_id=r.id ";
  static final String MEMBER =
      " FROM business_members m JOIN users u ON u.id=m.user_id WHERE m.business_id=? AND"
          + " u.account_status='ACTIVE' AND u.email_verified_at IS NOT NULL ";

  public BusinessRepository(JdbcTemplate db, Security security) {
    this.db = db;
    this.security = security;
  }

  JdbcTemplate jdbc() {
    return db;
  }

  UUID admin(HttpServletRequest r) {
    return security.admin(r);
  }

  Map<String, Object> one(String sql, Object... args) {
    var rows = db.queryForList(sql, args);
    if (rows.isEmpty()) throw new ApiError(404, "NOT_FOUND", "Business record not found.");
    return rows.get(0);
  }

  UUID verified(HttpServletRequest r) {
    var p = security.principal(r);
    if (p.get("email_verified_at") == null)
      throw new ApiError(
          403, "EMAIL_NOT_VERIFIED", "Confirm your email to use a business workspace.");
    return (UUID) p.get("id");
  }

  String access(UUID business, UUID user, boolean owner) {
    one("SELECT id FROM businesses WHERE id=? FOR UPDATE", business);
    var rows = db.queryForList("SELECT m.role" + MEMBER + "AND m.user_id=?", business, user);
    if (rows.isEmpty()) throw new ApiError(404, "NOT_FOUND", "Business workspace not found.");
    String role = (String) rows.get(0).get("role");
    if (owner && !role.equals("OWNER"))
      throw new ApiError(403, "FORBIDDEN", "Only the business owner can do this.");
    return role;
  }

  Map<String, Object> request(UUID business, UUID request) {
    return one(REQUEST + "WHERE r.business_id=? AND r.id=?", business, request);
  }

  void open(Map<String, Object> r) {
    if (!"OPEN".equals(r.get("status")))
      throw new ApiError(409, "INVALID_STATE_TRANSITION", "Open the project request first.");
  }

  void audit(UUID business, UUID actor, String action, String detail) {
    db.update(
        "INSERT INTO business_activity(id,business_id,actor_id,action,detail) VALUES(?,?,?,?,?)",
        id(),
        business,
        actor,
        action,
        detail);
  }

  public static String choice(Map<String, Object> b, String field, Set<String> allowed) {
    String value = text(b, field, 1, 30);
    if (!allowed.contains(value))
      throw new ApiError(400, "VALIDATION_ERROR", "Invalid " + field + ".");
    return value;
  }
}
