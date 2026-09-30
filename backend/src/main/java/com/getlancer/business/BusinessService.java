package com.getlancer.business;

import static com.getlancer.business.BusinessRepository.*;
import static com.getlancer.shared.Support.*;

import com.getlancer.shared.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessService {
  final BusinessRepository repo;

  public BusinessService(BusinessRepository repo) {
    this.repo = repo;
  }

  public Map<String, Object> mine(HttpServletRequest r) {
    UUID u = repo.verified(r);
    return Map.of(
        "items",
        repo.jdbc()
            .queryForList(
                BUSINESS
                    + "JOIN business_members m ON m.business_id=b.id WHERE m.user_id=? ORDER BY"
                    + " b.created_at DESC LIMIT 100",
                u),
        "invitations",
        repo.jdbc()
            .queryForList(
                "SELECT i.id,b.name AS \"businessName\",i.business_id AS \"businessId\",CASE WHEN"
                    + " i.status='PENDING' AND i.expires_at<=now() THEN 'EXPIRED' ELSE i.status END"
                    + " AS status FROM business_invitations i JOIN businesses b ON"
                    + " b.id=i.business_id WHERE i.user_id=? ORDER BY i.created_at DESC LIMIT 100",
                u));
  }

  @Transactional
  public Map<String, Object> create(Map<String, Object> b, HttpServletRequest r) {
    UUID u = repo.verified(r), business = id();
    repo.jdbc()
        .update(
            "INSERT INTO businesses(id,name,summary,owner_id) VALUES(?,?,?,?)",
            business,
            text(b, "name", 2, 120),
            text(b, "summary", 10, 2000),
            u);
    repo.jdbc()
        .update(
            "INSERT INTO business_members(business_id,user_id,role) VALUES(?,?,'OWNER')",
            business,
            u);
    repo.audit(business, u, "BUSINESS_CREATED", "Business workspace created");
    return repo.one(BUSINESS + "WHERE b.id=?", business);
  }

  @Transactional
  public Map<String, Object> workspace(UUID id, HttpServletRequest r) {
    UUID u = repo.verified(r);
    String role = repo.access(id, u, false);
    return Map.of(
        "business",
        repo.one(BUSINESS + "WHERE b.id=?", id),
        "myRole",
        role,
        "members",
        repo.jdbc()
            .queryForList(
                "SELECT m.user_id AS \"userId\",coalesce(d.display_name,'Hiring manager') AS"
                    + " name,m.role FROM business_members m JOIN users u ON u.id=m.user_id LEFT"
                    + " JOIN developer_profiles d ON d.user_id=m.user_id WHERE m.business_id=? AND"
                    + " u.account_status='ACTIVE' AND u.email_verified_at IS NOT NULL ORDER BY"
                    + " m.role,u.id",
                id),
        "invitations",
        role.equals("OWNER")
            ? repo.jdbc()
                .queryForList(
                    "SELECT i.id,u.email,CASE WHEN i.status='PENDING' AND i.expires_at<=now() THEN"
                        + " 'EXPIRED' ELSE i.status END AS status FROM business_invitations i JOIN"
                        + " users u ON u.id=i.user_id WHERE i.business_id=? ORDER BY i.created_at"
                        + " DESC LIMIT 100",
                    id)
            : List.of(),
        "activity",
        repo.jdbc()
            .queryForList(
                "SELECT id,action,detail,created_at AS \"createdAt\" FROM business_activity WHERE"
                    + " business_id=? ORDER BY created_at DESC,id LIMIT 100",
                id));
  }

  @Transactional
  public Map<String, Object> edit(UUID id, Map<String, Object> b, HttpServletRequest r) {
    UUID u = repo.verified(r);
    repo.access(id, u, true);
    repo.jdbc()
        .update(
            "UPDATE businesses SET name=?,summary=? WHERE id=?",
            text(b, "name", 2, 120),
            text(b, "summary", 10, 2000),
            id);
    repo.audit(id, u, "BUSINESS_UPDATED", "Business profile updated");
    return repo.one(BUSINESS + "WHERE b.id=?", id);
  }

  @Transactional
  public Map<String, Object> invite(UUID business, Map<String, Object> b, HttpServletRequest r) {
    UUID u = repo.verified(r);
    repo.access(business, u, true);
    var target =
        repo.jdbc()
            .queryForList(
                "SELECT id FROM users WHERE email=? AND account_status='ACTIVE' AND"
                    + " email_verified_at IS NOT NULL",
                email(b, "email"));
    if (target.isEmpty())
      throw new ApiError(
          400,
          "INVITATION_UNAVAILABLE",
          "A verified active account is required for this invitation.");
    UUID recipient = (UUID) target.get(0).get("id");
    if (repo.jdbc()
            .queryForObject(
                "SELECT count(*) FROM business_members WHERE business_id=? AND user_id=?",
                Integer.class,
                business,
                recipient)
        > 0) throw new ApiError(409, "CONFLICT", "This account is already a member.");
    repo.jdbc()
        .update(
            "UPDATE business_invitations SET status='EXPIRED' WHERE business_id=? AND user_id=? AND"
                + " status='PENDING' AND expires_at<=now()",
            business,
            recipient);
    UUID invitation = id();
    if (repo.jdbc()
            .queryForObject(
                "SELECT count(*) FROM business_invitations WHERE business_id=? AND user_id=? AND"
                    + " status='PENDING'",
                Integer.class,
                business,
                recipient)
        > 0) throw new ApiError(409, "CONFLICT", "An invitation is already pending.");
    repo.jdbc()
        .update(
            "INSERT INTO business_invitations(id,business_id,user_id) VALUES(?,?,?)",
            invitation,
            business,
            recipient);
    repo.jdbc()
        .update(
            "INSERT INTO notifications(id,user_id,title) VALUES(?,?,?)",
            id(),
            recipient,
            "You have a business hiring invitation");
    repo.audit(business, u, "MANAGER_INVITED", "Hiring manager invited; acceptance required");
    return Map.of("id", invitation, "status", "PENDING");
  }

  @Transactional
  public Map<String, Object> respond(UUID invitation, Map<String, Object> b, HttpServletRequest r) {
    UUID u = repo.verified(r);
    var initial =
        repo.one(
            "SELECT business_id FROM business_invitations WHERE id=? AND user_id=?", invitation, u);
    UUID business = (UUID) initial.get("business_id");
    repo.one("SELECT id FROM businesses WHERE id=? FOR UPDATE", business);
    var i =
        repo.one(
            "SELECT *,expires_at>now() AS valid FROM business_invitations WHERE id=? AND user_id=?"
                + " FOR UPDATE",
            invitation,
            u);
    if (!i.get("status").equals("PENDING") || !Boolean.TRUE.equals(i.get("valid")))
      throw new ApiError(409, "INVALID_STATE_TRANSITION", "This invitation is no longer pending.");
    String action = choice(b, "action", Set.of("ACCEPT", "DECLINE"));
    if (action.equals("ACCEPT"))
      repo.jdbc()
          .update(
              "INSERT INTO business_members(business_id,user_id,role) VALUES(?,?,'HIRING_MANAGER')"
                  + " ON CONFLICT DO NOTHING",
              business,
              u);
    String status = action.equals("ACCEPT") ? "ACCEPTED" : "DECLINED";
    repo.jdbc().update("UPDATE business_invitations SET status=? WHERE id=?", status, invitation);
    repo.audit(business, u, "INVITATION_" + status, "Hiring invitation response recorded");
    return Map.of("ok", true);
  }

  @Transactional
  public Map<String, Object> remove(UUID business, UUID user, HttpServletRequest r) {
    UUID u = repo.verified(r);
    repo.access(business, u, !u.equals(user));
    var target =
        repo.one(
            "SELECT role FROM business_members WHERE business_id=? AND user_id=?", business, user);
    if (target.get("role").equals("OWNER"))
      throw new ApiError(409, "OWNER_PROTECTED", "The business owner cannot be removed.");
    repo.jdbc()
        .update("DELETE FROM business_members WHERE business_id=? AND user_id=?", business, user);
    repo.audit(business, u, "MANAGER_REMOVED", "Hiring access revoked");
    return Map.of("ok", true);
  }
}
