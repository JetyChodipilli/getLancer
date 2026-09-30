package com.getlancer.teams;

import static com.getlancer.shared.Support.*;
import static com.getlancer.teams.TeamRepository.*;

import com.getlancer.products.ProductRepository;
import com.getlancer.products.ProductService;
import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
public class TeamService {
  final JdbcTemplate db;
  private final TeamRepository repository;
  final Security security;
  final ProductService products;

  public TeamService(
      JdbcTemplate db, Security security, ProductService products, TeamRepository repository) {
    this.db = db;
    this.security = security;
    this.products = products;
    this.repository = repository;
  }

  Map<String, Object> one(String sql, Object... args) {
    var rows = db.queryForList(sql, args);
    if (rows.isEmpty()) throw new ApiError(404, "NOT_FOUND", "Team record not found.");
    return rows.get(0);
  }

  Map<String, Object> team(UUID t) {
    return repository.team(t);
  }

  void lock(UUID t) {
    repository.lock(t);
  }

  void active(UUID t) {
    if (!"ACTIVE".equals(team(t).get("status")))
      throw new ApiError(409, "TEAM_RESTRICTED", "This team is suspended.");
  }

  String role(UUID t, UUID u) {
    return repository.role(t, u);
  }

  String member(UUID t, UUID u) {
    String role = role(t, u);
    if (role == null) throw new ApiError(403, "FORBIDDEN", "Active team membership is required.");
    return role;
  }

  String access(UUID t, UUID u, String capability) {
    lock(t);
    String role = member(t, u);
    TeamPolicy.require(role, capability);
    return role;
  }

  void builder(UUID u) {
    if (db.queryForObject(
            "SELECT count(*) FROM developer_profiles d JOIN users u ON u.id=d.user_id WHERE u.id=?"
                + " AND u.account_status='ACTIVE' AND u.email_verified_at IS NOT NULL AND"
                + " d.approval_status='APPROVED'",
            Integer.class,
            u)
        == 0) throw new ApiError(403, "FORBIDDEN", "An approved, verified builder is required.");
  }

  void audit(UUID t, UUID actor, String action, String detail, String scope) {
    db.update(
        "INSERT INTO team_activity(id,team_id,actor_id,action,detail,scope) VALUES(?,?,?,?,?,?)",
        id(),
        t,
        actor,
        action,
        detail,
        scope);
  }

  List<Map<String, Object>> members(UUID t) {
    return repository.members(t);
  }

  List<Map<String, Object>> publicMembers(UUID t) {
    return members(t).stream()
        .map(
            m -> {
              m.put("expiresAt", null);
              m.put("projectLabel", null);
              return m;
            })
        .toList();
  }

  List<Map<String, Object>> projects(UUID t) {
    return products.dtos(
        db.queryForList(
            ProductRepository.SELECT
                + " JOIN team_projects tp ON tp.product_id=p.id WHERE tp.team_id=? AND"
                + " tp.consented_by=p.owner_user_id AND "
                + ProductRepository.PUBLIC
                + " AND EXISTS(SELECT 1"
                + MEMBER_FROM
                + "WHERE m.team_id=tp.team_id AND m.user_id=p.owner_user_id AND "
                + ACTIVE
                + ") ORDER BY p.created_at DESC",
            t));
  }

  List<Map<String, Object>> leads(String where, boolean privateNotes, Object... args) {
    var rows = db.queryForList(LEAD + where, args);
    for (var l : rows) {
      if (!privateNotes) {
        l.put("assigneeId", null);
        l.put("followUpAt", null);
      }
      l.put(
          "notes",
          privateNotes
              ? db.queryForList(
                  "SELECT n.body AS text,n.created_at AS"
                      + " \"createdAt\",coalesce(d.display_name,'Team member') AS \"actorName\""
                      + " FROM team_lead_notes n LEFT JOIN developer_profiles d ON"
                      + " d.user_id=n.actor_id WHERE n.lead_id=? ORDER BY n.created_at,n.id",
                  l.get("id"))
              : List.of());
    }
    return rows;
  }

  Map<String, Object> invitation(UUID i) {
    return one(INVITE + "WHERE i.id=?", i);
  }

  static Timestamp timestamp(Instant i) {
    return i == null ? null : Timestamp.from(i);
  }

  public Map<String, Object> directory(String q) {
    if (q.length() > 200) throw new ApiError(400, "VALIDATION_ERROR", "Search is too long.");
    return Map.of(
        "items",
        db.queryForList(
            TEAM
                + "WHERE t.status='ACTIVE' AND (t.name ILIKE ? OR t.summary ILIKE ?) ORDER BY"
                + " t.created_at DESC LIMIT 100",
            "%" + q + "%",
            "%" + q + "%"));
  }

  @Transactional
  public Map<String, Object> create(Map<String, Object> b, HttpServletRequest r) {
    UUID u = security.developer(r, true);
    if (db.queryForObject(
            "SELECT count(*) FROM products WHERE owner_user_id=? AND approval_status='APPROVED' AND"
                + " lifecycle_status<>'SUSPENDED'",
            Integer.class,
            u)
        == 0)
      throw new ApiError(403, "FORBIDDEN", "An approved showcase is required to create a team.");
    UUID t = id();
    String name = text(b, "name", 2, 120);
    db.update(
        "INSERT INTO teams(id,slug,name,summary,availability,project_range,owner_id)"
            + " VALUES(?,?,?,?,?,?,?)",
        t,
        name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-") + "-" + t,
        name,
        text(b, "summary", 10, 3000),
        text(b, "availability", 1, 60),
        text(b, "projectRange", 0, 200),
        u);
    db.update(
        "INSERT INTO team_members(team_id,user_id,role,membership_type)"
            + " VALUES(?,?,'OWNER','PERMANENT')",
        t,
        u);
    audit(t, u, "TEAM_CREATED", name, "member");
    return team(t);
  }

  public Map<String, Object> detail(UUID id) {
    var t = team(id);
    if (!"ACTIVE".equals(t.get("status"))) throw new ApiError(404, "NOT_FOUND", "Team not found.");
    return Map.of(
        "team",
        t,
        "members",
        publicMembers(id),
        "projects",
        projects(id),
        "roles",
        db.queryForList(ROLE + "WHERE team_id=? AND status='OPEN' ORDER BY created_at DESC", id),
        "reviews",
        db.queryForList(
            "SELECT r.id,r.rating,r.review_text AS \"reviewText\",r.created_at AS"
                + " \"createdAt\",d.user_id AS \"memberId\",d.display_name AS"
                + " \"memberName\",'Verified member review' AS attribution FROM reviews r JOIN"
                + " inquiries i ON i.id=r.inquiry_id JOIN team_members m ON"
                + " m.user_id=r.developer_user_id JOIN users u ON u.id=m.user_id JOIN"
                + " developer_profiles d ON d.user_id=m.user_id WHERE m.team_id=? AND "
                + ACTIVE
                + " AND r.moderation_status='PUBLISHED' AND i.moderation_status='CLEAR' AND"
                + " i.current_status='COMPLETED' AND i.email_confirmed_at IS NOT NULL ORDER BY"
                + " r.created_at DESC LIMIT 100",
            id));
  }

  public Map<String, Object> mine(HttpServletRequest r) {
    UUID u = security.user(r);
    return Map.of(
        "items",
        db
            .queryForList(
                TEAM
                    + "WHERE EXISTS(SELECT 1"
                    + MEMBER_FROM
                    + "WHERE m.team_id=t.id AND m.user_id=? AND "
                    + ACTIVE
                    + ")",
                u)
            .stream()
            .map(
                t -> {
                  t.put("myRole", role((UUID) t.get("id"), u));
                  return t;
                })
            .toList(),
        "invitations",
        db.queryForList(INVITE + "WHERE i.user_id=? ORDER BY i.created_at DESC", u),
        "applications",
        db.queryForList(APPLICATION + "WHERE a.user_id=? ORDER BY a.created_at DESC", u),
        "requests",
        leads("WHERE l.client_id=? ORDER BY l.created_at DESC", false, u));
  }

  public Map<String, Object> workspace(UUID id, HttpServletRequest r) {
    String role = member(id, security.user(r));
    Map<String, Object> x = new LinkedHashMap<>();
    x.put("team", team(id));
    x.put("myRole", role);
    x.put("members", members(id));
    x.put("projects", projects(id));
    boolean recruit = TeamPolicy.permits(role, "recruit"),
        commercial = TeamPolicy.permits(role, "commercial"),
        staff = TeamPolicy.permits(role, "staff");
    x.put("invitations", recruit ? db.queryForList(INVITE + "WHERE i.team_id=?", id) : List.of());
    x.put("roles", db.queryForList(ROLE + "WHERE team_id=?", id));
    x.put(
        "applications",
        recruit ? db.queryForList(APPLICATION + "WHERE a.team_id=?", id) : List.of());
    x.put(
        "leads",
        commercial ? leads("WHERE l.team_id=? ORDER BY l.created_at DESC", true, id) : List.of());
    x.put("staffing", staff ? staffing(id) : List.of());
    x.put(
        "activity",
        db
            .queryForList(
                "SELECT a.id,a.action,coalesce(d.display_name,'Team member') AS"
                    + " \"actorName\",a.detail,a.created_at AS \"createdAt\",a.scope FROM"
                    + " team_activity a LEFT JOIN developer_profiles d ON d.user_id=a.actor_id"
                    + " WHERE a.team_id=? ORDER BY a.created_at DESC LIMIT 200",
                id)
            .stream()
            .filter(
                a ->
                    "member".equals(a.get("scope"))
                        || TeamPolicy.permits(role, (String) a.get("scope")))
            .map(
                a -> {
                  a.remove("scope");
                  return a;
                })
            .toList());
    return x;
  }

  @Transactional
  public Map<String, Object> edit(UUID id, Map<String, Object> b, HttpServletRequest r) {
    UUID u = security.user(r);
    access(id, u, "owner");
    active(id);
    db.update(
        "UPDATE teams SET name=?,summary=?,availability=?,project_range=? WHERE id=?",
        text(b, "name", 2, 120),
        text(b, "summary", 10, 3000),
        text(b, "availability", 1, 60),
        text(b, "projectRange", 0, 200),
        id);
    audit(id, u, "TEAM_UPDATED", "Team profile updated", "member");
    return team(id);
  }

  public Map<String, Object> candidates(UUID id, String q, HttpServletRequest r) {
    TeamPolicy.require(member(id, security.user(r)), "recruit");
    active(id);
    if (q.length() > 200) throw new ApiError(400, "VALIDATION_ERROR", "Search is too long.");
    return Map.of(
        "items",
        db.queryForList(
            "SELECT d.user_id AS id,d.display_name AS name,d.headline,d.technology AS skills FROM"
                + " developer_profiles d JOIN users u ON u.id=d.user_id WHERE"
                + " d.approval_status='APPROVED' AND u.account_status='ACTIVE' AND"
                + " u.email_verified_at IS NOT NULL AND (d.display_name ILIKE ? OR d.technology"
                + " ILIKE ?) ORDER BY d.display_name LIMIT 50",
            "%" + q + "%",
            "%" + q + "%"));
  }

  public UUID invite(UUID t, UUID user, String role, String type, Instant expiry, String label) {
    builder(user);
    if (role(t, user) != null)
      throw new ApiError(409, "CONFLICT", "This builder is already a member.");
    db.update(
        "UPDATE team_invitations SET status='EXPIRED' WHERE team_id=? AND user_id=? AND"
            + " status='PENDING' AND (respond_by<=now() OR expires_at<=now())",
        t,
        user);
    UUID i = id();
    db.update(
        "INSERT INTO"
            + " team_invitations(id,team_id,user_id,role,membership_type,expires_at,project_label)"
            + " VALUES(?,?,?,?,?,?,?)",
        i,
        t,
        user,
        role,
        type,
        timestamp(expiry),
        label);
    return i;
  }

  @Transactional
  public Map<String, Object> invite(UUID id, Map<String, Object> b, HttpServletRequest r) {
    UUID u = security.user(r);
    String actor = access(id, u, "recruit");
    active(id);
    String role = text(b, "role", 1, 30);
    TeamPolicy.invitationRole(actor, role);
    String type = TeamPolicy.choice(b, "membershipType", Set.of("PERMANENT", "CONTRACT"));
    Instant expiry = TeamPolicy.future(b.get("expiresAt"), type.equals("CONTRACT"));
    UUID invitation =
        invite(id, uuid(b.get("userId")), role, type, expiry, text(b, "projectLabel", 0, 200));
    audit(id, u, "INVITATION_SENT", "Builder invited with consent required", "recruit");
    return invitation(invitation);
  }

  @Transactional
  public Map<String, Object> respond(UUID id, Map<String, Object> b, HttpServletRequest r) {
    UUID u = security.user(r);
    var initial = one("SELECT team_id FROM team_invitations WHERE id=? AND user_id=?", id, u);
    UUID t = (UUID) initial.get("team_id");
    lock(t);
    var i =
        one(
            "SELECT *,respond_by>now() AND (expires_at IS NULL OR expires_at>now()) AS valid FROM"
                + " team_invitations WHERE id=? AND user_id=? FOR UPDATE",
            id,
            u);
    if (!"PENDING".equals(i.get("status")) || !Boolean.TRUE.equals(i.get("valid")))
      throw new ApiError(
          409, "INVALID_STATE_TRANSITION", "This invitation is no longer pending or has expired.");
    String action = TeamPolicy.choice(b, "action", Set.of("ACCEPT", "DECLINE"));
    if (action.equals("ACCEPT")) {
      active(t);
      builder(u);
      if (role(t, u) != null) throw new ApiError(409, "CONFLICT", "Already a member.");
      db.update(
          "INSERT INTO team_members(team_id,user_id,role,membership_type,expires_at,project_label)"
              + " VALUES(?,?,?,?,?,?) ON CONFLICT(team_id,user_id) DO UPDATE SET"
              + " role=excluded.role,membership_type=excluded.membership_type,expires_at=excluded.expires_at,project_label=excluded.project_label",
          t,
          u,
          i.get("role"),
          i.get("membership_type"),
          i.get("expires_at"),
          i.get("project_label"));
    }
    db.update(
        "UPDATE team_invitations SET status=? WHERE id=?",
        action.equals("ACCEPT") ? "ACCEPTED" : "DECLINED",
        id);
    audit(t, u, "INVITATION_" + action, "Invitation response recorded", "recruit");
    return invitation(id);
  }

  @Transactional
  public Map<String, Object> changeRole(
      UUID id, UUID userId, Map<String, Object> b, HttpServletRequest r) {
    UUID u = security.user(r);
    access(id, u, "owner");
    String current = member(id, userId), next = TeamPolicy.choice(b, "role", TeamPolicy.ROLES);
    if (current.equals("OWNER") || next.equals("OWNER"))
      throw new ApiError(
          409, "OWNER_PROTECTED", "Ownership cannot be changed through member roles.");
    db.update("UPDATE team_members SET role=? WHERE team_id=? AND user_id=?", next, id, userId);
    audit(id, u, "MEMBER_ROLE_CHANGED", next, "owner");
    return Map.of("ok", true);
  }

  @Transactional
  public Map<String, Object> remove(UUID id, UUID userId, HttpServletRequest r) {
    UUID u = security.user(r);
    lock(id);
    String actor = member(id, u);
    var target = one("SELECT role FROM team_members WHERE team_id=? AND user_id=?", id, userId);
    if (!u.equals(userId)) TeamPolicy.require(actor, "owner");
    if ("OWNER".equals(target.get("role"))) {
      if (!u.equals(userId))
        throw new ApiError(409, "OWNER_PROTECTED", "Another owner cannot be removed.");
      var owners =
          db.queryForList(
              "SELECT m.user_id"
                  + MEMBER_FROM
                  + "WHERE m.team_id=? AND m.user_id<>? AND m.role='OWNER' AND "
                  + ACTIVE
                  + " ORDER BY m.user_id",
              id,
              u);
      if (owners.isEmpty())
        throw new ApiError(409, "OWNER_PROTECTED", "The last active owner cannot leave.");
      db.update(
          "UPDATE teams SET owner_id=? WHERE id=? AND owner_id=?",
          owners.get(0).get("user_id"),
          id,
          u);
    }
    db.update("DELETE FROM team_projects WHERE team_id=? AND consented_by=?", id, userId);
    db.update(
        "UPDATE team_staffing SET status='COMPLETED' WHERE team_id=? AND user_id=?", id, userId);
    db.update(
        "UPDATE team_leads SET assignee_id=NULL WHERE team_id=? AND assignee_id=?", id, userId);
    db.update("DELETE FROM team_members WHERE team_id=? AND user_id=?", id, userId);
    audit(id, u, "MEMBER_LEFT", "Membership ended", "member");
    return Map.of("ok", true);
  }

  @Transactional
  public Map<String, Object> createRole(UUID id, Map<String, Object> b, HttpServletRequest r) {
    UUID u = security.user(r);
    access(id, u, "recruit");
    active(id);
    UUID role = id();
    db.update(
        "INSERT INTO"
            + " team_roles(id,team_id,title,description,skills,contract_type,compensation_band)"
            + " VALUES(?,?,?,?,?,?,?)",
        role,
        id,
        text(b, "title", 2, 120),
        text(b, "description", 10, 5000),
        text(b, "skills", 0, 500),
        TeamPolicy.choice(b, "contractType", Set.of("PERMANENT", "CONTRACT")),
        text(b, "compensationBand", 0, 200));
    audit(id, u, "ROLE_CREATED", "Open role created", "recruit");
    return one(ROLE + "WHERE id=?", role);
  }

  @Transactional
  public Map<String, Object> roleStatus(
      UUID id, UUID roleId, Map<String, Object> b, HttpServletRequest r) {
    UUID u = security.user(r);
    access(id, u, "recruit");
    active(id);
    one(ROLE + "WHERE team_id=? AND id=?", id, roleId);
    db.update(
        "UPDATE team_roles SET status=? WHERE id=?",
        TeamPolicy.choice(b, "status", Set.of("OPEN", "CLOSED")),
        roleId);
    audit(id, u, "ROLE_UPDATED", "Open role status changed", "recruit");
    return one(ROLE + "WHERE id=?", roleId);
  }

  @Transactional
  public Map<String, Object> apply(
      UUID id, UUID roleId, Map<String, Object> b, HttpServletRequest r) {
    UUID u = security.developer(r, true);
    lock(id);
    active(id);
    if (role(id, u) != null) throw new ApiError(409, "CONFLICT", "Team members cannot apply.");
    one(ROLE + "WHERE team_id=? AND id=? AND status='OPEN'", id, roleId);
    UUID a = id();
    db.update(
        "INSERT INTO team_applications(id,team_id,role_id,user_id,message) VALUES(?,?,?,?,?)",
        a,
        id,
        roleId,
        u,
        text(b, "message", 1, 3000));
    audit(id, u, "APPLICATION_SUBMITTED", "Application submitted", "recruit");
    return one(APPLICATION + "WHERE a.id=?", a);
  }

  @Transactional
  public Map<String, Object> decision(
      UUID id, UUID applicationId, Map<String, Object> b, HttpServletRequest r) {
    UUID u = security.user(r);
    access(id, u, "recruit");
    active(id);
    var a =
        one(
            "SELECT a.*,r.contract_type FROM team_applications a JOIN team_roles r ON"
                + " r.id=a.role_id WHERE a.team_id=? AND a.id=? AND r.status='OPEN' FOR UPDATE OF"
                + " a",
            id,
            applicationId);
    String status = TeamPolicy.choice(b, "status", Set.of("SHORTLISTED", "REJECTED", "INVITED"));
    TeamPolicy.applicationTransition((String) a.get("status"), status);
    if (status.equals(
        "INVITED")) { // Contract applications receive a bounded offer; acceptance still requires
      // applicant consent.
      String type = (String) a.get("contract_type");
      invite(
          id,
          (UUID) a.get("user_id"),
          "MEMBER",
          type,
          type.equals("CONTRACT") ? Instant.now().plus(Duration.ofDays(30)) : null,
          "");
    }
    db.update("UPDATE team_applications SET status=? WHERE id=?", status, applicationId);
    audit(id, u, "APPLICATION_" + status, "Application decision recorded", "recruit");
    return one(APPLICATION + "WHERE a.id=?", applicationId);
  }

  @Transactional
  public Map<String, Object> lead(UUID id, Map<String, Object> b, HttpServletRequest r) {
    var p = security.principal(r);
    UUID u = (UUID) p.get("id");
    if (p.get("email_verified_at") == null)
      throw new ApiError(403, "EMAIL_NOT_VERIFIED", "Verify your email first.");
    lock(id);
    active(id);
    if (db.queryForObject(
            "SELECT count(*) FROM team_members WHERE team_id=? AND user_id=? AND (expires_at IS"
                + " NULL OR expires_at>now())",
            Integer.class,
            id,
            u)
        > 0) throw new ApiError(403, "FORBIDDEN", "Members cannot inquire about their own team.");
    UUID lead = id();
    db.update(
        "INSERT INTO team_leads(id,team_id,client_id,title,description,budget,timeline)"
            + " VALUES(?,?,?,?,?,?,?)",
        lead,
        id,
        u,
        text(b, "title", 3, 120),
        text(b, "description", 10, 5000),
        text(b, "budget", 0, 200),
        text(b, "timeline", 0, 200));
    audit(id, u, "LEAD_CREATED", "Client interest received", "commercial");
    return leads("WHERE l.id=?", false, lead).get(0);
  }

  @Transactional
  public Map<String, Object> clientDecision(
      UUID id, UUID leadId, Map<String, Object> b, HttpServletRequest r) {
    var principal = security.principal(r);
    UUID u = (UUID) principal.get("id");
    if (principal.get("email_verified_at") == null)
      throw new ApiError(403, "EMAIL_NOT_VERIFIED", "Verify your email first.");
    lock(id);
    active(id);
    var lead =
        one(
            "SELECT status FROM team_leads WHERE team_id=? AND id=? AND client_id=? FOR UPDATE",
            id,
            leadId,
            u);
    String action = TeamPolicy.choice(b, "action", Set.of("ACCEPT", "DECLINE"));
    if (!"PROPOSAL_SENT".equals(lead.get("status")))
      throw new ApiError(
          409,
          "INVALID_STATE_TRANSITION",
          "Only a proposal awaiting your response can be acknowledged.");
    String status = action.equals("ACCEPT") ? "WON" : "LOST";
    db.update("UPDATE team_leads SET status=? WHERE id=?", status, leadId);
    audit(
        id,
        u,
        "CLIENT_" + action,
        "Client reported a proposal decision; external agreement required",
        "commercial");
    return leads("WHERE l.id=?", false, leadId).get(0);
  }

  @Transactional
  public Map<String, Object> updateLead(
      UUID id, UUID leadId, Map<String, Object> b, HttpServletRequest r) {
    UUID u = security.user(r);
    access(id, u, "commercial");
    active(id);
    var l = one("SELECT * FROM team_leads WHERE team_id=? AND id=? FOR UPDATE", id, leadId);
    String status =
        b.containsKey("status")
            ? TeamPolicy.choice(
                b,
                "status",
                Set.of(
                    "NEW",
                    "INTERESTED",
                    "NEEDS_INFORMATION",
                    "DECLINED",
                    "PROPOSAL_SENT",
                    "WON",
                    "LOST"))
            : (String) l.get("status");
    TeamPolicy.leadTransition((String) l.get("status"), status);
    Object assignee = l.get("assignee_id"), follow = l.get("follow_up_at");
    if (b.containsKey("assigneeId")) {
      assignee =
          b.get("assigneeId") == null || b.get("assigneeId").toString().isBlank()
              ? null
              : uuid(b.get("assigneeId"));
      if (assignee != null) member(id, (UUID) assignee);
    }
    if (b.containsKey("followUpAt"))
      follow = timestamp(TeamPolicy.future(b.get("followUpAt"), false));
    db.update(
        "UPDATE team_leads SET status=?,assignee_id=?,follow_up_at=? WHERE id=?",
        status,
        assignee,
        follow,
        leadId);
    String note = text(b, "note", 0, 3000);
    if (!note.isBlank()) {
      db.update(
          "INSERT INTO team_lead_notes(id,lead_id,actor_id,body) VALUES(?,?,?,?)",
          id(),
          leadId,
          u,
          note);
      audit(id, u, "LEAD_NOTE_ADDED", note, "commercial");
    }
    audit(id, u, "LEAD_UPDATED", status, "commercial");
    return leads("WHERE l.id=?", true, leadId).get(0);
  }

  @Transactional
  public Map<String, Object> addProject(UUID id, Map<String, Object> b, HttpServletRequest r) {
    UUID u = security.developer(r, true), p = uuid(b.get("productId"));
    lock(id);
    member(id, u);
    active(id);
    one(
        ProductRepository.SELECT
            + " WHERE p.id=? AND p.owner_user_id=? AND "
            + ProductRepository.PUBLIC,
        p,
        u);
    db.update(
        "INSERT INTO team_projects(team_id,product_id,consented_by) VALUES(?,?,?) ON CONFLICT DO"
            + " NOTHING",
        id,
        p,
        u);
    audit(id, u, "PROJECT_SHARED", "Product owner consent recorded", "member");
    return Map.of("ok", true);
  }

  @Transactional
  public Map<String, Object> removeProject(UUID id, UUID productId, HttpServletRequest r) {
    UUID u = security.user(r);
    lock(id);
    var p =
        one(
            "SELECT consented_by FROM team_projects WHERE team_id=? AND product_id=?",
            id,
            productId);
    if (!u.equals(p.get("consented_by"))) TeamPolicy.require(member(id, u), "owner");
    db.update("DELETE FROM team_projects WHERE team_id=? AND product_id=?", id, productId);
    audit(id, u, "PROJECT_REMOVED", "Showcase consent removed", "member");
    return Map.of("ok", true);
  }

  List<Map<String, Object>> staffing(UUID t) {
    return db.queryForList(
        "SELECT s.id,s.user_id AS \"userId\",d.display_name AS name,s.project_label AS"
            + " \"projectLabel\",s.skills,s.ends_at AS \"endsAt\",CASE WHEN s.ends_at<=now() OR NOT"
            + " EXISTS(SELECT 1"
            + MEMBER_FROM
            + "WHERE m.team_id=s.team_id AND m.user_id=s.user_id AND "
            + ACTIVE
            + ") THEN 'COMPLETED' ELSE s.status END AS status FROM team_staffing s JOIN"
            + " developer_profiles d ON d.user_id=s.user_id WHERE s.team_id=? ORDER BY s.created_at"
            + " DESC",
        t);
  }

  @Transactional
  public Map<String, Object> addStaffing(UUID id, Map<String, Object> b, HttpServletRequest r) {
    UUID u = security.user(r);
    access(id, u, "staff");
    active(id);
    UUID target = uuid(b.get("userId"));
    member(id, target);
    Instant end = TeamPolicy.future(b.get("endsAt"), true);
    var m = one("SELECT expires_at FROM team_members WHERE team_id=? AND user_id=?", id, target);
    if (m.get("expires_at") instanceof Timestamp expiry && end.isAfter(expiry.toInstant()))
      throw new ApiError(400, "VALIDATION_ERROR", "Staffing cannot outlast membership.");
    UUID s = id();
    db.update(
        "INSERT INTO team_staffing(id,team_id,user_id,project_label,skills,ends_at)"
            + " VALUES(?,?,?,?,?,?)",
        s,
        id,
        target,
        text(b, "projectLabel", 1, 200),
        text(b, "skills", 0, 500),
        timestamp(end));
    audit(id, u, "STAFFING_CREATED", "Temporary project assignment created", "staff");
    return staffing(id).stream().filter(x -> s.equals(x.get("id"))).findFirst().orElseThrow();
  }

  @Transactional
  public Map<String, Object> updateStaffing(
      UUID id, UUID staffingId, Map<String, Object> b, HttpServletRequest r) {
    UUID u = security.user(r);
    access(id, u, "staff");
    active(id);
    var s =
        one(
            "SELECT *,ends_at>now() AS valid FROM team_staffing WHERE team_id=? AND id=?",
            id,
            staffingId);
    String status = TeamPolicy.choice(b, "status", Set.of("ACTIVE", "COMPLETED"));
    if (status.equals("ACTIVE")) {
      member(id, (UUID) s.get("user_id"));
      if (!Boolean.TRUE.equals(s.get("valid")) || !"ACTIVE".equals(s.get("status")))
        throw new ApiError(
            409,
            "INVALID_STATE_TRANSITION",
            "Completed or expired staffing cannot be reactivated.");
    }
    db.update("UPDATE team_staffing SET status=? WHERE id=?", status, staffingId);
    audit(id, u, "STAFFING_UPDATED", status, "staff");
    return staffing(id).stream()
        .filter(x -> staffingId.equals(x.get("id")))
        .findFirst()
        .orElseThrow();
  }

  public Map<String, Object> admin(HttpServletRequest r) {
    security.admin(r);
    return Map.of("items", db.queryForList(TEAM + "ORDER BY t.created_at DESC LIMIT 500"));
  }

  @Transactional
  public Map<String, Object> moderate(UUID id, Map<String, Object> b, HttpServletRequest r) {
    UUID u = security.admin(r);
    lock(id);
    String status = TeamPolicy.choice(b, "status", Set.of("ACTIVE", "SUSPENDED")),
        reason = text(b, "reason", 10, 3000);
    db.update("UPDATE teams SET status=? WHERE id=?", status, id);
    db.update(
        "INSERT INTO moderation_actions(id,admin_id,target_type,target_id,action,reason)"
            + " VALUES(?,?,'TEAM',?,?,?)",
        id(),
        u,
        id,
        status,
        reason);
    audit(id, u, "TEAM_MODERATED", reason, "owner");
    return team(id);
  }
}
