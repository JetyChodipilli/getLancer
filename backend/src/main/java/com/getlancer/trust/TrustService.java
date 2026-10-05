package com.getlancer.trust;

import static com.getlancer.shared.Support.*;

import com.getlancer.products.ProductRepository;
import com.getlancer.products.ProductService;
import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import com.getlancer.shared.Support;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
public class TrustService {
  final JdbcTemplate db;
  final Security security;
  final ProductService products;
  final com.getlancer.publishing.PublishingCapacity publishing;

  public TrustService(JdbcTemplate db, Security security, ProductService products, com.getlancer.publishing.PublishingCapacity publishing) {
    this.db = db;
    this.security = security;
    this.products = products;
    this.publishing = publishing;
  }

  static boolean githubRepository(String raw) {
    try {
      var u = URI.create(raw);
      return "https".equals(u.getScheme())
          && "github.com".equalsIgnoreCase(u.getHost())
          && u.getPort() == -1
          && u.getRawUserInfo() == null
          && u.getRawQuery() == null
          && u.getRawFragment() == null
          && u.getPath().matches("/[A-Za-z0-9_-]+/[A-Za-z0-9_.-]+/?");
    } catch (Exception e) {
      return false;
    }
  }

  public Map<String, Object> overview(HttpServletRequest r) {
    UUID u = security.developer(r, false);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put(
        "profile",
        db.queryForMap(
            "SELECT availability_status,booked_until,availability_confirmed_at FROM"
                + " developer_profiles WHERE user_id=?",
            u));
    result.put(
        "activeSlotLimit",
        db.queryForObject(
            "SELECT active_slot_limit FROM showcase_entitlements WHERE user_id=?",
            Integer.class,
            u));
    result.put("publishingCapacity",publishing.capacity(u,"PROJECT"));
    result.put(
        "projects",
        db.queryForList(
            "SELECT p.id,p.title,p.repository_url,p.demo_health,p.demo_checked_at,v.status AS"
                + " verification_status,v.challenge,v.expires_at,v.reason FROM products p LEFT JOIN"
                + " repository_verifications v ON v.product_id=p.id AND"
                + " v.repository_url=p.repository_url WHERE p.owner_user_id=? ORDER BY p.updated_at"
                + " DESC LIMIT 100",
            u));
    result.put(
        "awards",
        db.queryForList(
            "SELECT inquiry_id,reason,created_at FROM earned_capacity_awards WHERE user_id=? ORDER"
                + " BY created_at DESC",
            u));
    return result;
  }

  @Transactional
  public Map<String, Object> availability(Map<String, Object> b, HttpServletRequest r) {
    UUID u = security.developer(r, true);
    String status = text(b, "status", 1, 30);
    if (!Set.of("AVAILABLE_NOW", "ONE_SLOT_LEFT", "LIMITED", "BOOKED_UNTIL", "NOT_ACCEPTING")
        .contains(status))
      throw new ApiError(400, "VALIDATION_ERROR", "Choose an availability status.");
    java.sql.Date until = null;
    if (status.equals("BOOKED_UNTIL")) {
      try {
        var date = java.time.LocalDate.parse(text(b, "bookedUntil", 10, 10));
        if (date.isBefore(java.time.LocalDate.now())
            || date.isAfter(java.time.LocalDate.now().plusYears(2)))
          throw new IllegalArgumentException();
        until = java.sql.Date.valueOf(date);
      } catch (Exception e) {
        throw new ApiError(
            400, "VALIDATION_ERROR", "Choose a future booked-until date within two years.");
      }
    }
    db.update(
        "UPDATE developer_profiles SET"
            + " availability_status=?,booked_until=?,availability_confirmed_at=now(),updated_at=now()"
            + " WHERE user_id=?",
        status,
        until,
        u);
    return Map.of("ok", true);
  }

  @Transactional
  public Map<String, Object> request(UUID id, HttpServletRequest r) {
    UUID u = security.developer(r, true);
    var p = products.owned(id, u);
    String repository = Objects.toString(p.get("repository_url"), "");
    if (!githubRepository(repository))
      throw new ApiError(
          400, "VALIDATION_ERROR", "Add a GitHub repository URL to this showcase first.");
    var existing =
        db.queryForList(
            "SELECT challenge,status,expires_at FROM repository_verifications WHERE product_id=?"
                + " AND repository_url=? AND (status='VERIFIED' OR (status='PENDING' AND"
                + " expires_at>now()))",
            id,
            repository);
    if (!existing.isEmpty()) return existing.get(0);
    String challenge = "getlancer-proof-" + randomToken();
    db.update(
        "INSERT INTO repository_verifications(product_id,repository_url,challenge) VALUES(?,?,?) ON"
            + " CONFLICT(product_id) DO UPDATE SET"
            + " repository_url=excluded.repository_url,challenge=excluded.challenge,status='PENDING',requested_at=now(),expires_at=now()+interval"
            + " '7 days',reviewed_at=NULL,reviewer_id=NULL,reason=NULL",
        id,
        repository,
        challenge);
    return Map.of("challenge", challenge, "status", "PENDING");
  }

  public Map<String, Object> queue(HttpServletRequest r) {
    security.admin(r);
    return Map.of(
        "verified",
        db.queryForList(
            "SELECT v.product_id,v.repository_url,v.reviewed_at,p.title FROM"
                + " repository_verifications v JOIN products p ON p.id=v.product_id WHERE"
                + " v.status='VERIFIED' AND v.repository_url=p.repository_url ORDER BY"
                + " v.reviewed_at DESC LIMIT 100"),
        "verifications",
        db.queryForList(
            "SELECT v.*,p.title FROM repository_verifications v JOIN products p ON"
                + " p.id=v.product_id WHERE v.status='PENDING' AND v.expires_at>now() AND"
                + " v.repository_url=p.repository_url ORDER BY v.requested_at LIMIT 100"),
        "eligibleOutcomes",
        db.queryForList(
            "SELECT i.id,i.developer_user_id,d.display_name,p.title FROM inquiries i JOIN users u"
                + " ON u.id=i.developer_user_id JOIN developer_profiles d ON d.user_id=u.id JOIN"
                + " products p ON p.id=i.reference_product_id WHERE "
                + ELIGIBLE
                + " AND NOT EXISTS(SELECT 1 FROM earned_capacity_awards a WHERE a.inquiry_id=i.id)"
                + " ORDER BY i.updated_at LIMIT 100"));
  }

  static final String ELIGIBLE =
      "i.current_status='COMPLETED' AND i.email_confirmed_at IS NOT NULL AND"
          + " i.moderation_status='CLEAR' AND lower(i.client_email)<>lower(u.email) AND"
          + " u.account_status='ACTIVE' AND d.approval_status='APPROVED' AND EXISTS(SELECT 1 FROM"
          + " inquiry_events e WHERE e.inquiry_id=i.id AND e.event_type='COMPLETED' AND"
          + " e.actor_type IN ('CLIENT','CLIENT_TOKEN'))";

  @Transactional
  public Map<String, Object> review(
      UUID id, String action, Map<String, Object> b, HttpServletRequest r) {
    UUID admin = security.admin(r);
    String reason = text(b, "reason", 10, 2000);
    if (!Set.of("approve", "reject", "revoke").contains(action))
      throw new ApiError(400, "VALIDATION_ERROR", "Invalid decision.");
    var rows =
        db.queryForList(
            "SELECT v.*,p.repository_url AS current_url,p.owner_user_id FROM products p JOIN"
                + " repository_verifications v ON v.product_id=p.id WHERE p.id=? FOR UPDATE OF p,v",
            id);
    if (rows.isEmpty()) throw new ApiError(404, "NOT_FOUND", "Verification not found.");
    var v = rows.get(0);
    if (admin.equals(v.get("owner_user_id")))
      throw new ApiError(403, "FORBIDDEN", "Self verification is not allowed.");
    if (!action.equals("revoke")
        && (!v.get("status").equals("PENDING")
            || !v.get("repository_url").equals(v.get("current_url"))
            || ((java.sql.Timestamp) v.get("expires_at"))
                .toInstant()
                .isBefore(java.time.Instant.now())))
      throw new ApiError(
          409, "INVALID_STATE_TRANSITION", "Request is expired, changed or already reviewed.");
    if (action.equals("revoke") && !v.get("status").equals("VERIFIED"))
      throw new ApiError(409, "INVALID_STATE_TRANSITION", "Only verified evidence can be revoked.");
    db.update(
        "UPDATE repository_verifications SET status=?,reason=?,reviewer_id=?,reviewed_at=now()"
            + " WHERE product_id=?",
        action.equals("approve") ? "VERIFIED" : "REJECTED",
        reason,
        admin,
        id);
    audit(admin, "PRODUCT", id, "REPOSITORY_" + action.toUpperCase(Locale.ROOT), reason);
    return Map.of("ok", true);
  }

  @Transactional
  public Map<String, Object> award(UUID id, Map<String, Object> b, HttpServletRequest r) {
    UUID admin = security.admin(r);
    String reason = text(b, "reason", 10, 2000);
    var rows =
        db.queryForList(
            "SELECT i.developer_user_id FROM inquiries i JOIN users u ON u.id=i.developer_user_id"
                + " JOIN developer_profiles d ON d.user_id=u.id WHERE i.id=? AND "
                + ELIGIBLE
                + " FOR UPDATE OF i",
            id);
    if (rows.isEmpty())
      throw new ApiError(
          409, "OUTCOME_NOT_ELIGIBLE", "A clear, client-confirmed completion is required.");
    UUID owner = (UUID) rows.get(0).get("developer_user_id");
    if (admin.equals(owner)) throw new ApiError(403, "FORBIDDEN", "Self awards are not allowed.");
    publishing.lock(owner);
    db.queryForMap("SELECT * FROM showcase_entitlements WHERE user_id=? FOR UPDATE", owner);
    int added =
        db.update(
            "INSERT INTO earned_capacity_awards(inquiry_id,user_id,admin_id,reason) VALUES(?,?,?,?)"
                + " ON CONFLICT DO NOTHING",
            id,
            owner,
            admin,
            reason);
    if (added > 0) {
      db.update(
          "UPDATE showcase_entitlements SET active_slot_limit=active_slot_limit+1,source='EARNED'"
              + " WHERE user_id=?",
          owner);
      audit(admin, "INQUIRY", id, "EARNED_CAPACITY", reason);
    }
    return Map.of("awarded", added > 0);
  }

  void audit(UUID admin, String type, UUID id, String action, String reason) {
    db.update(
        "INSERT INTO moderation_actions(id,admin_id,target_type,target_id,action,reason)"
            + " VALUES(?,?,?,?,?,?)",
        Support.id(),
        admin,
        type,
        id,
        action,
        reason);
  }

  public Map<String, Object> similar(String slug) {
    var source =
        db.queryForList(
            ProductRepository.SELECT + " WHERE p.slug=? AND " + ProductRepository.PUBLIC, slug);
    if (source.isEmpty()) throw new ApiError(404, "NOT_FOUND", "Project not found.");
    var p = source.get(0);
    var rows =
        db.queryForList(
            "SELECT * FROM (SELECT DISTINCT ON(p.owner_user_id) p.*,d.display_name AS"
                + " builder,d.slug AS builder_slug,d.availability_status AS"
                + " availability,d.booked_until,CASE WHEN p.category=? THEN 1 ELSE 0 END AS"
                + " relevance FROM products p JOIN users u ON u.id=p.owner_user_id JOIN"
                + " developer_profiles d ON d.user_id=u.id WHERE "
                + ProductRepository.PUBLIC
                + " AND p.owner_user_id<>? AND p.available_for_similar_work=true AND"
                + " d.availability_status IN ('AVAILABLE_NOW','ONE_SLOT_LEFT','LIMITED') AND"
                + " (p.category=? OR string_to_array(p.technology,',') && string_to_array(?,','))"
                + " ORDER BY p.owner_user_id,relevance DESC,p.updated_at DESC,p.id) candidates"
                + " ORDER BY relevance DESC,updated_at DESC,id LIMIT 4",
            p.get("category"),
            p.get("owner_user_id"),
            p.get("category"),
            p.get("technology"));
    return Map.of("items", products.dtos(rows));
  }
}
