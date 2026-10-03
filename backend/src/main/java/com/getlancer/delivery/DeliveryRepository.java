package com.getlancer.delivery;

import static com.getlancer.shared.Support.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DeliveryRepository {
  private final JdbcTemplate db;
  private final Security security;
  private final ObjectMapper json;
  private static final String ENGAGEMENT =
      "SELECT e.id,e.title,e.status,e.source_inquiry_id AS \"sourceInquiryId\",e.business_request_id AS \"businessRequestId\",e.buyer_user_id AS \"buyerUserId\",e.business_id AS \"businessId\",e.builder_user_id AS \"builderUserId\",e.team_id AS \"teamId\",e.created_at AS \"createdAt\" FROM delivery_engagements e ";
  private static final String PROPOSAL =
      "SELECT id,revision,status,scope,terms,amount_minor AS \"amountMinor\",currency,milestones::text AS milestones,sent_at AS \"sentAt\",accepted_at AS \"acceptedAt\" FROM delivery_proposals ";
  private static final String MILESTONE =
      "SELECT id,engagement_id AS \"engagementId\",title,description,amount_minor AS \"amountMinor\",currency,due_date AS \"dueDate\",status,delivery_note AS \"deliveryNote\",delivery_url AS \"deliveryUrl\",accepted_at AS \"acceptedAt\" FROM delivery_milestones ";

  public DeliveryRepository(JdbcTemplate db, Security security, ObjectMapper json) {
    this.db = db;
    this.security = security;
    this.json = json;
  }

  public UUID verified(HttpServletRequest request) {
    var p = security.principal(request);
    if (p.get("email_verified_at") == null)
      throw new ApiError(403, "EMAIL_NOT_VERIFIED", "Confirm your email to use delivery.");
    return (UUID) p.get("id");
  }

  UUID admin(HttpServletRequest request) {
    return security.admin(request);
  }

  private Map<String, Object> one(String sql, Object... args) {
    var rows = db.queryForList(sql, args);
    if (rows.isEmpty()) throw new ApiError(404, "NOT_FOUND", "Delivery record not found.");
    return rows.get(0);
  }

  private boolean approvedBuilder(UUID user) {
    return user != null && db.queryForObject(
        "SELECT count(*) FROM users u JOIN developer_profiles d ON d.user_id=u.id JOIN user_roles r ON r.user_id=u.id AND r.role='DEVELOPER' WHERE u.id=? AND u.account_status='ACTIVE' AND u.email_verified_at IS NOT NULL AND d.approval_status='APPROVED'",
        Integer.class, user) > 0;
  }

  private boolean businessBuyer(UUID business, UUID actor, boolean lock) {
    if (business == null) return false;
    if (lock) one("SELECT id FROM businesses WHERE id=? FOR SHARE", business);
    return db.queryForObject(
        "SELECT count(*) FROM business_members m JOIN users u ON u.id=m.user_id WHERE m.business_id=? AND m.user_id=? AND m.role IN ('OWNER','HIRING_MANAGER') AND u.account_status='ACTIVE' AND u.email_verified_at IS NOT NULL",
        Integer.class, business, actor) > 0;
  }

  private boolean teamSeller(UUID team, UUID actor, boolean lock) {
    if (team == null || !approvedBuilder(actor)) return false;
    if (lock) one("SELECT id FROM teams WHERE id=? FOR SHARE", team);
    return db.queryForObject(
        "SELECT count(*) FROM team_members m JOIN teams t ON t.id=m.team_id WHERE m.team_id=? AND m.user_id=? AND m.role IN ('OWNER','BUSINESS_MANAGER') AND (m.expires_at IS NULL OR m.expires_at>now()) AND t.status='ACTIVE'",
        Integer.class, team, actor) > 0;
  }

  String side(Map<String, Object> engagement, UUID actor, boolean lock) {
    boolean buyer = actor.equals(engagement.get("buyerUserId")) && security.role(actor, "CLIENT")
        || businessBuyer((UUID) engagement.get("businessId"), actor, lock);
    boolean seller = actor.equals(engagement.get("builderUserId")) && approvedBuilder(actor)
        || teamSeller((UUID) engagement.get("teamId"), actor, lock);
    if (buyer && seller)
      throw new ApiError(409, "CONFLICT", "The same account cannot represent both agreement parties.");
    return buyer ? "BUYER" : seller ? "SELLER" : null;
  }

  public Map<String, Object> lockEngagement(UUID engagement, HttpServletRequest request, String requiredSide) {
    UUID actor = verified(request);
    var row = one(ENGAGEMENT + "WHERE e.id=? FOR UPDATE", engagement);
    String actual = side(row, actor, true);
    if (actual == null) throw new ApiError(404, "NOT_FOUND", "Engagement not found.");
    if (!"ANY".equals(requiredSide) && !requiredSide.equals(actual))
      throw new ApiError(403, "FORBIDDEN", "This action belongs to the other agreement party.");
    row.put("side", actual);
    return row;
  }

  public Map<String,Object> lockEngagementSystem(UUID engagement) {
    return one(ENGAGEMENT + "WHERE e.id=? FOR UPDATE", engagement);
  }

  public Map<String, Object> lockMilestone(UUID milestone, HttpServletRequest request, String side) {
    UUID engagement = (UUID) one("SELECT engagement_id FROM delivery_milestones WHERE id=?", milestone).get("engagement_id");
    return milestoneWithParties(milestone, lockEngagement(engagement, request, side));
  }

  public Map<String, Object> lockMilestoneSystem(UUID milestone) {
    UUID engagement = (UUID) one("SELECT engagement_id FROM delivery_milestones WHERE id=?", milestone).get("engagement_id");
    return milestoneWithParties(milestone, one(ENGAGEMENT + "WHERE e.id=? FOR UPDATE", engagement));
  }

  void requireMilestone(UUID engagement, UUID milestone) {
    one("SELECT id FROM delivery_milestones WHERE id=? AND engagement_id=?", milestone, engagement);
  }

  private Map<String, Object> milestoneWithParties(UUID milestone, Map<String, Object> engagement) {
    var row = one(MILESTONE + "WHERE id=? AND engagement_id=? FOR UPDATE", milestone, engagement.get("id"));
    row.put("engagementStatus", engagement.get("status"));
    for (String key : List.of("buyerUserId", "businessId", "builderUserId", "teamId")) row.put(key, engagement.get(key));
    return row;
  }

  List<Map<String, Object>> list(UUID actor) {
    var rows = db.queryForList(ENGAGEMENT
        + "WHERE e.buyer_user_id=? OR e.builder_user_id=? OR EXISTS(SELECT 1 FROM business_members m WHERE m.business_id=e.business_id AND m.user_id=?) OR EXISTS(SELECT 1 FROM team_members m WHERE m.team_id=e.team_id AND m.user_id=? AND m.role IN ('OWNER','BUSINESS_MANAGER') AND (m.expires_at IS NULL OR m.expires_at>now())) ORDER BY e.updated_at DESC,e.id LIMIT 200",
        actor, actor, actor, actor);
    var result = new ArrayList<Map<String, Object>>();
    for (var row : rows) {
      String side = side(row, actor, false);
      if (side != null) { row.put("side", side); result.add(row); }
    }
    return result;
  }

  Map<String, Object> inquiry(UUID source) {
    return one("SELECT i.id,i.developer_user_id AS \"builderUserId\",u.id AS \"buyerUserId\",p.title FROM inquiries i JOIN users u ON u.email=i.client_email AND u.account_status='ACTIVE' AND u.email_verified_at IS NOT NULL JOIN user_roles r ON r.user_id=u.id AND r.role='CLIENT' JOIN products p ON p.id=i.reference_product_id WHERE i.id=? AND i.email_confirmed_at IS NOT NULL AND i.moderation_status='CLEAR' AND i.current_status IN ('INQUIRY_RECEIVED','RESPONDED','DISCUSSION','PROPOSAL_SENT','HIRE_PENDING_CONFIRMATION','HIRED','IN_PROGRESS') FOR UPDATE OF i", source);
  }

  Map<String, Object> businessSource(UUID request) {
    UUID business = (UUID) one("SELECT business_id FROM business_requests WHERE id=?", request).get("business_id");
    one("SELECT id FROM businesses WHERE id=? FOR SHARE", business);
    return one("SELECT id,business_id AS \"businessId\",title,category,technology,available_only AS \"availableOnly\",repository_verified_only AS \"repositoryVerifiedOnly\",status FROM business_requests WHERE id=? FOR UPDATE", request);
  }

  boolean buyerForBusiness(UUID business, UUID actor) { return businessBuyer(business, actor, true); }
  boolean sellerForCandidate(String kind, UUID candidate, UUID actor) {
    return "BUILDER".equals(kind) ? candidate.equals(actor) && approvedBuilder(actor) : teamSeller(candidate, actor, true);
  }
  boolean shortlisted(UUID request, String kind, UUID candidate) {
    return db.queryForObject("SELECT count(*) FROM request_shortlist WHERE request_id=? AND kind=? AND coalesce(builder_id,team_id)=?", Integer.class, request, kind, candidate) > 0;
  }

  UUID existing(UUID inquiry, UUID request, UUID builder, UUID team) {
    var rows = db.queryForList("SELECT id FROM delivery_engagements WHERE source_inquiry_id=? OR (business_request_id=? AND (builder_user_id=? OR team_id=?))", inquiry, request, builder, team);
    return rows.isEmpty() ? null : (UUID) rows.get(0).get("id");
  }

  void create(UUID engagement, UUID inquiry, UUID request, UUID buyer, UUID business, UUID builder, UUID team, String title, UUID actor) {
    db.update("INSERT INTO delivery_engagements(id,source_inquiry_id,business_request_id,buyer_user_id,business_id,builder_user_id,team_id,title,created_by) VALUES(?,?,?,?,?,?,?,?,?)", engagement, inquiry, request, buyer, business, builder, team, title, actor);
  }

  Map<String, Object> detail(Map<String, Object> row) {
    UUID id = (UUID) row.get("id");
    var proposals = db.queryForList(PROPOSAL + "WHERE engagement_id=? ORDER BY revision DESC", id);
    for (var proposal : proposals) proposal.put("milestones", milestonesJson((String) proposal.get("milestones")));
    row.put("proposals", proposals);
    row.put("milestones", db.queryForList(MILESTONE + "WHERE engagement_id=? ORDER BY ordinal", id));
    row.put("activity", db.queryForList("SELECT id,kind,detail,actor_id AS \"actorId\",created_at AS \"createdAt\" FROM delivery_activity WHERE engagement_id=? ORDER BY created_at DESC,id LIMIT 100", id));
    row.put("disputes", db.queryForList("SELECT id,reason,status,resolution,resolution_reason AS \"resolutionReason\",created_at AS \"createdAt\",resolved_at AS \"resolvedAt\" FROM delivery_disputes WHERE engagement_id=? ORDER BY created_at DESC,id", id));
    var agreement = db.queryForList("SELECT proposal_id AS \"proposalId\",scope,terms,amount_minor AS \"amountMinor\",currency,digest,seller_consented_at AS \"sellerConsentedAt\",buyer_consented_at AS \"buyerConsentedAt\" FROM delivery_agreements WHERE engagement_id=?", id);
    row.put("agreement", agreement.isEmpty() ? null : agreement.get(0));
    return row;
  }

  List<Map<String, Object>> milestonesJson(String value) {
    try { return json.readValue(value, new TypeReference<List<Map<String, Object>>>() {}); }
    catch (Exception e) { throw new IllegalStateException("Stored milestone terms are invalid", e); }
  }
  String json(Object value) {
    try { return json.writeValueAsString(value); }
    catch (Exception e) { throw new IllegalArgumentException("Invalid delivery terms", e); }
  }

  int nextRevision(UUID engagement) {
    return db.queryForObject("SELECT coalesce(max(revision),0)+1 FROM delivery_proposals WHERE engagement_id=?", Integer.class, engagement);
  }
  String latestProposalStatus(UUID engagement) {
    var rows = db.queryForList("SELECT status FROM delivery_proposals WHERE engagement_id=? ORDER BY revision DESC LIMIT 1", engagement);
    return rows.isEmpty() ? null : (String) rows.get(0).get("status");
  }
  void draft(UUID engagement, UUID proposal, int revision, String scope, String terms, long amount, String milestones) {
    db.update("UPDATE delivery_proposals SET status='WITHDRAWN' WHERE engagement_id=? AND status='DRAFT'", engagement);
    db.update("INSERT INTO delivery_proposals(id,engagement_id,revision,scope,terms,amount_minor,milestones) VALUES(?,?,?,?,?,?,?::jsonb)", proposal, engagement, revision, scope, terms, amount, milestones);
  }
  Map<String, Object> proposal(UUID engagement, UUID proposal) {
    return one("SELECT *,milestones::text AS \"milestonesJson\" FROM delivery_proposals WHERE engagement_id=? AND id=? FOR UPDATE", engagement, proposal);
  }
  void proposalState(UUID proposal, String status, UUID actor) {
    if (status.equals("SENT")) db.update("UPDATE delivery_proposals SET status='SENT',seller_consented_by=?,sent_at=now() WHERE id=?", actor, proposal);
    else db.update("UPDATE delivery_proposals SET status=? WHERE id=?", status, proposal);
  }
  void accept(UUID engagement, UUID proposal, UUID actor, String digest, List<Map<String, Object>> milestones) {
    db.update("UPDATE delivery_proposals SET status='ACCEPTED',buyer_consented_by=?,accepted_at=now() WHERE id=?", actor, proposal);
    db.update("INSERT INTO delivery_agreements(engagement_id,proposal_id,scope,terms,amount_minor,currency,milestones,digest,seller_consented_by,seller_consented_at,buyer_consented_by,buyer_consented_at) SELECT engagement_id,id,scope,terms,amount_minor,currency,milestones,?,seller_consented_by,sent_at,buyer_consented_by,accepted_at FROM delivery_proposals WHERE id=?", digest, proposal);
    int ordinal = 0;
    for (var milestone : milestones) db.update("INSERT INTO delivery_milestones(id,engagement_id,ordinal,title,description,amount_minor,due_date) VALUES(?,?,?,?,?,?,?::date)", id(), engagement, ++ordinal, milestone.get("title"), milestone.get("description"), milestone.get("amountMinor"), milestone.get("dueDate"));
    state(engagement, "ACTIVE");
  }
  void milestoneState(UUID milestone, String status, String note, String url, UUID actor) {
    if (status.equals("SUBMITTED")) db.update("UPDATE delivery_milestones SET status=?,delivery_note=?,delivery_url=?,updated_at=now() WHERE id=?", status, note, url, milestone);
    else if (status.equals("ACCEPTED")) db.update("UPDATE delivery_milestones SET status=?,accepted_at=now(),accepted_by=?,updated_at=now() WHERE id=?", status, actor, milestone);
    else db.update("UPDATE delivery_milestones SET status=?,updated_at=now() WHERE id=?", status, milestone);
  }
  void state(UUID engagement, String status) { db.update("UPDATE delivery_engagements SET status=?,updated_at=now() WHERE id=?", status, engagement); }

  public boolean allPaid(UUID engagement) {
    Integer total = db.queryForObject("SELECT count(*) FROM delivery_milestones WHERE engagement_id=?", Integer.class, engagement);
    Integer paid = db.queryForObject("SELECT count(*) FROM delivery_milestones m WHERE m.engagement_id=? AND m.status='ACCEPTED' AND EXISTS(SELECT 1 FROM payment_attempts p WHERE p.milestone_id=m.id AND p.status='CAPTURED' AND p.mode='live' AND p.amount_minor=m.amount_minor AND p.refunded_minor=0)", Integer.class, engagement);
    return total > 0 && total.equals(paid);
  }
  public void event(UUID engagement, UUID actor, String kind, String detail) {
    db.update("INSERT INTO delivery_activity(id,engagement_id,actor_id,kind,detail) VALUES(?,?,?,?,?)", id(), engagement, actor, kind, detail);
  }
  UUID dispute(UUID engagement, UUID actor, String reason, String previous) {
    UUID dispute = id();
    db.update("INSERT INTO delivery_disputes(id,engagement_id,opened_by,reason,previous_status) VALUES(?,?,?,?,?)", dispute, engagement, actor, reason, previous);
    state(engagement, "DISPUTED");
    return dispute;
  }
  List<Map<String, Object>> disputes() {
    return db.queryForList("SELECT d.id,d.engagement_id AS \"engagementId\",e.title,d.reason,d.status,d.previous_status AS \"previousStatus\",d.resolution,d.resolution_reason AS \"resolutionReason\",d.created_at AS \"createdAt\" FROM delivery_disputes d JOIN delivery_engagements e ON e.id=d.engagement_id ORDER BY (d.status='OPEN') DESC,d.created_at DESC LIMIT 200");
  }
  Map<String, Object> lockDispute(UUID dispute) {
    UUID engagement = (UUID) one("SELECT engagement_id FROM delivery_disputes WHERE id=?", dispute).get("engagement_id");
    one(ENGAGEMENT + "WHERE e.id=? FOR UPDATE", engagement);
    return one("SELECT * FROM delivery_disputes WHERE id=? FOR UPDATE", dispute);
  }
  void resolve(UUID dispute, UUID actor, String resolution, String reason) {
    db.update("UPDATE delivery_disputes SET status='RESOLVED',resolution=?,resolution_reason=?,resolved_by=?,resolved_at=now() WHERE id=?", resolution, reason, actor, dispute);
  }
}
