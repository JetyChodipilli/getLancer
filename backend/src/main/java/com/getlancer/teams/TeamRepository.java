package com.getlancer.teams;

import com.getlancer.shared.ApiError;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TeamRepository {
  private final JdbcTemplate db;

  public TeamRepository(JdbcTemplate db) {
    this.db = db;
  }

  static final String ACTIVE =
      "(m.expires_at IS NULL OR m.expires_at>now()) AND u.account_status='ACTIVE' AND"
          + " d.approval_status='APPROVED' AND u.email_verified_at IS NOT NULL";
  static final String MEMBER_FROM =
      " FROM team_members m JOIN users u ON u.id=m.user_id JOIN developer_profiles d ON"
          + " d.user_id=u.id ";
  static final String TEAM =
      "SELECT t.id,t.slug,t.name,t.summary,t.availability,t.project_range AS"
          + " \"projectRange\",t.owner_id AS \"ownerId\",t.status,(SELECT count(*)"
          + MEMBER_FROM
          + "WHERE m.team_id=t.id AND "
          + ACTIVE
          + ") AS \"memberCount\",(SELECT count(*) FROM team_roles r WHERE r.team_id=t.id AND"
          + " r.status='OPEN') AS \"roleCount\" FROM teams t ";
  static final String INVITE =
      "SELECT i.id,i.team_id AS \"teamId\",t.name AS \"teamName\",i.user_id AS"
          + " \"userId\",d.display_name AS name,i.role,i.membership_type AS"
          + " \"membershipType\",i.expires_at AS \"expiresAt\",i.project_label AS"
          + " \"projectLabel\",CASE WHEN i.status='PENDING' AND (i.respond_by<=now() OR"
          + " i.expires_at<=now()) THEN 'EXPIRED' ELSE i.status END AS status FROM team_invitations"
          + " i JOIN teams t ON t.id=i.team_id JOIN developer_profiles d ON d.user_id=i.user_id ";
  static final String ROLE =
      "SELECT id,team_id AS \"teamId\",title,description,skills,contract_type AS"
          + " \"contractType\",compensation_band AS \"compensationBand\",status FROM team_roles ";
  static final String APPLICATION =
      "SELECT a.id,a.team_id AS \"teamId\",a.role_id AS \"roleId\",r.title AS"
          + " \"roleTitle\",a.user_id AS \"userId\",d.display_name AS name,a.message,a.status FROM"
          + " team_applications a JOIN team_roles r ON r.id=a.role_id JOIN developer_profiles d ON"
          + " d.user_id=a.user_id ";
  static final String LEAD =
      "SELECT l.id,l.team_id AS \"teamId\",t.name AS \"teamName\",l.client_id AS"
          + " \"clientId\",coalesce(d.display_name,'Client') AS"
          + " \"clientName\",l.title,l.description,l.budget,l.timeline,l.status,l.assignee_id AS"
          + " \"assigneeId\",l.follow_up_at AS \"followUpAt\" FROM team_leads l JOIN teams t ON"
          + " t.id=l.team_id LEFT JOIN developer_profiles d ON d.user_id=l.client_id ";

  Map<String, Object> one(String sql, Object... args) {
    var rows = db.queryForList(sql, args);
    if (rows.isEmpty()) throw new ApiError(404, "NOT_FOUND", "Team record not found.");
    return rows.get(0);
  }

  Map<String, Object> team(UUID t) {
    return one(TEAM + "WHERE t.id=?", t);
  }

  void lock(UUID t) {
    one("SELECT id FROM teams WHERE id=? FOR UPDATE", t);
  }

  String role(UUID t, UUID u) {
    var rows =
        db.queryForList(
            "SELECT m.role" + MEMBER_FROM + "WHERE m.team_id=? AND m.user_id=? AND " + ACTIVE,
            t,
            u);
    return rows.isEmpty() ? null : (String) rows.get(0).get("role");
  }

  List<Map<String, Object>> members(UUID t) {
    return db.queryForList(
        "SELECT m.user_id AS \"userId\",d.display_name AS name,m.role,m.membership_type AS"
            + " \"membershipType\",m.expires_at AS \"expiresAt\",m.project_label AS"
            + " \"projectLabel\""
            + MEMBER_FROM
            + "WHERE m.team_id=? AND "
            + ACTIVE
            + " ORDER BY d.display_name",
        t);
  }
}
