package com.getlancer.admin;

import static com.getlancer.shared.Support.id;
import static com.getlancer.shared.Support.text;

import com.getlancer.notifications.Mail;
import com.getlancer.products.ProductRepository;
import com.getlancer.products.ProductService;
import com.getlancer.responses.AdminResponses;
import com.getlancer.responses.PageResponse;
import com.getlancer.responses.ProjectResponses;
import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import com.getlancer.shared.Pages;
import com.getlancer.shared.Support;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
  final JdbcTemplate db;
  final Security security;
  final ProductService products;
  final Mail mail;
  final MarketplaceMetrics marketplaceMetrics;

  public AdminService(
      JdbcTemplate db,
      Security security,
      ProductService products,
      Mail mail,
      MarketplaceMetrics marketplaceMetrics) {
    this.db = db;
    this.security = security;
    this.products = products;
    this.mail = mail;
    this.marketplaceMetrics = marketplaceMetrics;
  }

  public PageResponse<ProjectResponses.ManagementProject> pending(HttpServletRequest r) {
    security.admin(r);
    var result =
        Pages.query(
            db,
            r,
            ProductRepository.SELECT
                + " WHERE p.approval_status IN ('PENDING_REVIEW','SUSPENDED') ORDER BY"
                + " p.updated_at,p.id");
    result.put("items", products.managementDtos(Pages.items(result)));
    return PageResponse.from(result,ProjectResponses.ManagementProject::from);
  }

  public PageResponse<AdminResponses.Profile> profiles(HttpServletRequest r) {
    security.admin(r);
    return PageResponse.from(Pages.query(
        db,
        r,
        "SELECT user_id AS id,display_name AS"
            + " \"displayName\",headline,bio,technology,category,github_url,linkedin_url,website_url,country,time_zone,languages,approval_status"
            + " FROM developer_profiles WHERE approval_status IN ('PROFILE_PENDING','SUSPENDED')"
            + " ORDER BY updated_at,user_id"), AdminResponses.Profile::from);
  }

  public PageResponse<AdminResponses.Report> reports(HttpServletRequest r) {
    security.admin(r);
    String sql = "SELECT id,reporter_id,target_type,target_id,reason,detail,status,resolution,created_at,severity,triage_note,updated_at,enforcement_action FROM reports WHERE 1=1";
    List<Object> values = new ArrayList<>();
    for (String key : List.of("status", "reason", "severity")) {
      String value = r.getParameter(key);
      if (value != null && !value.isBlank()) {
        if (value.length() > 40) throw new ApiError(400, "VALIDATION_ERROR", "Filter is too long.");
        sql += " AND " + key + "=?";
        values.add(value);
      }
    }
    return PageResponse.from(Pages.query(
        db,
        r,
        sql
            + " ORDER BY CASE severity WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN"
            + " 2 ELSE 3 END,created_at,id",
        values.toArray()), AdminResponses.Report::from);
  }

  public PageResponse<AdminResponses.Review> reviews(HttpServletRequest r) {
    security.admin(r);
    return PageResponse.from(Pages.query(
        db,
        r,
        "SELECT id,rating,review_text FROM reviews WHERE moderation_status='HELD_FOR_REVIEW' ORDER"
            + " BY created_at,id"), AdminResponses.Review::from);
  }

  @Transactional
  public Map<String, Object> productAction(
      UUID id, String action, Map<String, Object> b, HttpServletRequest r) {
    UUID admin = security.admin(r);
    var owners = db.queryForList("SELECT owner_user_id FROM products WHERE id=?", id);
    if (owners.isEmpty()) throw new ApiError(404, "NOT_FOUND", "Project not found.");
    UUID owner = (UUID) owners.get(0).get("owner_user_id");
    products.lockOwner(owner);
    db.queryForMap("SELECT user_id,active_slot_limit,source FROM showcase_entitlements WHERE user_id=? FOR UPDATE", owner);
    var p = db.queryForMap("SELECT id,owner_user_id,slug,title,summary,description,project_type,category,technology,visibility,contribution_text,available_for_similar_work,approval_status,lifecycle_status,live_url,video_url,moderation_reason,rights_confirmed,created_at,updated_at,repository_url,pricing_note,demo_health,demo_checked_at,demo_checked_url,pricing_mode,price_min_minor,price_max_minor,currency_code FROM products WHERE id=? FOR UPDATE", id);
    String reason = text(b, "reason", 3, 2000);
    String status =
        switch (action) {
          case "approve" -> "APPROVED";
          case "request-changes" -> "CHANGES_REQUESTED";
          case "reject" -> "REJECTED";
          case "suspend" -> "SUSPENDED";
          default -> throw new ApiError(400, "VALIDATION_ERROR", "Unknown moderation action.");
        };
    if (!action.equals("suspend")
        && !Set.of("PENDING_REVIEW", "SUSPENDED").contains(p.get("approval_status")))
      throw new ApiError(409, "INVALID_STATE_TRANSITION", "This project is not pending review.");
    db.update(
        "UPDATE products SET approval_status=?,lifecycle_status=CASE WHEN ? THEN 'SUSPENDED' WHEN"
            + " lifecycle_status='SUSPENDED' THEN 'DRAFT' ELSE lifecycle_status"
            + " END,moderation_reason=?,updated_at=now() WHERE id=?",
        status,
        action.equals("suspend"),
        reason,
        id);
    if (action.equals("approve") && !p.get("lifecycle_status").equals("ARCHIVED"))
      products.activate(id, owner, false);
    audit(admin, "PRODUCT", id, action, reason);
    mail.notify(owner, "Your project review has an update: " + status + ". " + reason);
    return Map.of("ok", true);
  }

  @Transactional
  public Map<String, Object> profileAction(
      UUID id, String action, Map<String, Object> b, HttpServletRequest r) {
    UUID admin = security.admin(r);
    String reason = text(b, "reason", 3, 2000);
    var p = db.queryForMap("SELECT approval_status FROM developer_profiles WHERE user_id=? FOR UPDATE", id);
    String status =
        switch (action) {
          case "approve" -> "APPROVED";
          case "request-changes" -> "CHANGES_REQUESTED";
          case "suspend" -> "SUSPENDED";
          default -> throw new ApiError(400, "VALIDATION_ERROR", "Unknown action.");
        };
    if (!action.equals("suspend")
        && !Set.of("PROFILE_PENDING", "SUSPENDED").contains(p.get("approval_status")))
      throw new ApiError(409, "INVALID_STATE_TRANSITION", "Profile not pending review.");
    if (action.equals("approve")
        && db.queryForObject(
                "SELECT count(*) FROM users WHERE id=? AND email_verified_at IS NOT NULL AND"
                    + " account_status='ACTIVE'",
                Integer.class,
                id)
            == 0)
      throw new ApiError(409, "EMAIL_NOT_VERIFIED", "Account must be active and email verified.");
    db.update(
        "UPDATE developer_profiles SET approval_status=?,moderation_reason=? WHERE user_id=?",
        status,
        reason,
        id);
    audit(admin, "USER", id, action, reason);
    mail.notify(id, "Your builder profile review has an update: " + status + ". " + reason);
    return Map.of("ok", true);
  }

  public Map<String, Object> reportDetail(UUID id, HttpServletRequest request) {
    UUID actor = security.admin(request);
    var rows = db.queryForList("SELECT id,reporter_id,target_type,target_id,reason,detail,status,resolution,created_at,severity,triage_note,updated_at,enforcement_action FROM reports WHERE id=?", id);
    if (rows.isEmpty()) throw new ApiError(404, "NOT_FOUND", "Report not found.");
    var report = rows.get(0);
    if (report.get("target_type").equals("INQUIRY"))
      audit(
          actor,
          "REPORT",
          id,
          "VIEW_EVIDENCE",
          "Inquiry evidence accessed for report investigation");
    List<Map<String, Object>> targets;
    switch ((String) report.get("target_type")) {
      case "PRODUCT" -> {
        targets =
            db.queryForList(ProductRepository.SELECT + " WHERE p.id=?", report.get("target_id"));
        if (!targets.isEmpty()) targets = List.of(products.managementDto(targets.get(0)));
      }
      case "USER" ->
          targets =
              db.queryForList(
                  "SELECT display_name,headline,bio,approval_status FROM developer_profiles WHERE"
                      + " user_id=?",
                  report.get("target_id"));
      case "REVIEW" ->
          targets =
              db.queryForList(
                  "SELECT rating,review_text,moderation_status FROM reviews WHERE id=?",
                  report.get("target_id"));
      case "INQUIRY" ->
          targets =
              db.queryForList(
                  "SELECT description,current_status FROM inquiries WHERE id=?",
                  report.get("target_id"));
      default -> targets = List.of();
    }
    return Map.of("report", report, "target", targets.isEmpty() ? Map.of() : targets.get(0));
  }

  @Transactional
  public Map<String, Object> resolve(
      UUID id, Map<String, Object> body, HttpServletRequest request) {
    UUID admin = security.admin(request);
    String reason = text(body, "reason", 3, 2000), action = text(body, "targetAction", 0, 20);
    var reports = db.queryForList("SELECT id,reporter_id,target_type,target_id,reason,detail,status,resolution,created_at,severity,triage_note,updated_at,enforcement_action FROM reports WHERE id=? FOR UPDATE", id);
    if (reports.isEmpty()) throw new ApiError(404, "NOT_FOUND", "Report not found.");
    var report = reports.get(0);
    if (report.get("status").equals("RESOLVED")) return Map.of("ok", true);
    UUID target = (UUID) report.get("target_id");
    String type = (String) report.get("target_type");
    if (action.equals("SUSPEND") && type.equals("PRODUCT"))
      productAction(target, "suspend", Map.of("reason", reason), request);
    else if (action.equals("SUSPEND") && type.equals("USER"))
      accountAction(target, "suspend", Map.of("reason", reason), request);
    else if (action.equals("HIDE") && type.equals("REVIEW"))
      reviewAction(target, "hide", Map.of("reason", reason), request);
    else if (Set.of("QUARANTINE", "BLOCK", "RESTORE").contains(action) && type.equals("INQUIRY"))
      inquiryAction(target, action.toLowerCase(Locale.ROOT), Map.of("reason", reason), request);
    else if (!Set.of("", "NONE").contains(action))
      throw new ApiError(400, "VALIDATION_ERROR", "Choose an action supported by this report.");
    db.update(
        "UPDATE reports SET status='RESOLVED',resolution=?,enforcement_action=?,updated_at=now() WHERE id=?",
        reason, action.isBlank() ? "NONE" : action,
        id);
    if (report.get("reporter_id") != null)
      mail.notify((UUID) report.get("reporter_id"), "Your report was reviewed. " + reason);
    audit(admin, "REPORT", id, "RESOLVE", reason);
    return Map.of("ok", true);
  }

  @Transactional
  public Map<String, Object> reviewAction(
      UUID id, String action, Map<String, Object> b, HttpServletRequest r) {
    UUID admin = security.admin(r);
    String reason = text(b, "reason", 3, 2000);
    if (!Set.of("publish", "hide").contains(action))
      throw new ApiError(400, "VALIDATION_ERROR", "Unknown action.");
    var rows =
        db.queryForList(
            "SELECT r.developer_user_id,i.client_email FROM reviews r JOIN inquiries i ON"
                + " i.id=r.inquiry_id WHERE r.id=? FOR UPDATE OF r",
            id);
    if (rows.isEmpty()) throw new ApiError(404, "NOT_FOUND", "Review not found.");
    db.update(
        "UPDATE reviews SET moderation_status=? WHERE id=?",
        action.equals("publish") ? "PUBLISHED" : "HIDDEN",
        id);
    mail.notify(
        (UUID) rows.get(0).get("developer_user_id"),
        "An engagement review was " + action + "ed. " + reason);
    mail.enqueue((String) rows.get(0).get("client_email"), "Your review moderation result", reason);
    audit(admin, "REVIEW", id, action, reason);
    return Map.of("ok", true);
  }

  @Transactional
  public Map<String, Object> taxonomy(String kind, Map<String, Object> b, HttpServletRequest r) {
    UUID admin = security.admin(r);
    if (!Set.of("categories", "technologies").contains(kind))
      throw new ApiError(400, "VALIDATION_ERROR", "Choose a supported taxonomy.");
    String name = text(b, "name", 2, 100), slug = text(b, "slug", 2, 100);
    if (!slug.matches("[a-z0-9-]+"))
      throw new ApiError(400, "VALIDATION_ERROR", "Use a lowercase slug.");
    var previous = db.queryForList("SELECT name FROM " + kind + " WHERE slug=? FOR UPDATE", slug);
    if (!previous.isEmpty()) {
      String old = (String) previous.get(0).get("name");
      if (kind.equals("categories"))
        db.update("UPDATE products SET category=? WHERE category=?", name, old);
      else
        db.update(
            "UPDATE products SET"
                + " technology=array_to_string(array_replace(string_to_array(technology,','),?,?),',')"
                + " WHERE ?=ANY(string_to_array(technology,','))",
            old,
            name,
            old);
    }
    db.update(
        "INSERT INTO "
            + kind
            + "(slug,name,active) VALUES(?,?,?) ON CONFLICT(slug) DO UPDATE SET"
            + " name=excluded.name,active=excluded.active",
        slug,
        name,
        !Boolean.FALSE.equals(b.get("active")));
    audit(admin, "TAXONOMY", admin, "UPSERT", kind + ":" + slug);
    return Map.of("ok", true);
  }

  public PageResponse<AdminResponses.History> history(UUID targetId, HttpServletRequest r) {
    security.admin(r);
    return PageResponse.from(targetId == null
        ? Pages.query(db, r, "SELECT id,admin_id,target_type,target_id,action,reason,created_at FROM moderation_actions ORDER BY created_at DESC,id")
        : Pages.query(
            db,
            r,
            "SELECT id,admin_id,target_type,target_id,action,reason,created_at FROM moderation_actions WHERE target_id=? ORDER BY created_at DESC,id",
            targetId), AdminResponses.History::from);
  }

  public PageResponse<AdminResponses.Account> accounts(String q, HttpServletRequest r) {
    security.admin(r);
    if (q.length() > 254) throw new ApiError(400, "VALIDATION_ERROR", "Search is too long.");
    return PageResponse.from(Pages.query(
        db,
        r,
        "SELECT id,email,account_status,moderation_reason FROM users WHERE email ILIKE ? ORDER BY"
            + " created_at DESC,id",
        "%" + q.replace("%", "\\%").replace("_", "\\_") + "%"), AdminResponses.Account::from);
  }

  @Transactional
  public Map<String, Object> accountAction(
      UUID id, String action, Map<String, Object> b, HttpServletRequest r) {
    UUID admin = security.admin(r);
    String reason = text(b, "reason", 3, 2000);
    if (security.role(id, "ADMIN"))
      throw new ApiError(
          409, "ADMIN_ACCOUNT_PROTECTED", "The sole administrator cannot be suspended.");
    if (!Set.of("suspend", "restore").contains(action))
      throw new ApiError(400, "VALIDATION_ERROR", "Unknown account action.");
    var rows = db.queryForList("SELECT account_status FROM users WHERE id=? FOR UPDATE", id);
    if (rows.isEmpty()) throw new ApiError(404, "NOT_FOUND", "Account not found.");
    if (rows.get(0).get("account_status").equals("DELETED"))
      throw new ApiError(
          409, "INVALID_STATE_TRANSITION", "Deleted accounts require operational recovery.");
    db.update(
        "UPDATE users SET account_status=?,moderation_reason=? WHERE id=?",
        action.equals("suspend") ? "SUSPENDED" : "ACTIVE",
        reason,
        id);
    db.update("DELETE FROM sessions WHERE user_id=?", id);
    db.update("DELETE FROM login_challenges WHERE user_id=?", id);
    db.update("UPDATE account_tokens SET used_at=now() WHERE user_id=? AND used_at IS NULL", id);
    audit(admin, "ACCOUNT", id, action, reason);
    mail.notify(id, "Your account status changed: " + action + ". " + reason);
    return Map.of("ok", true);
  }

  @Transactional
  public Map<String, Object> inquiryAction(
      UUID id, String action, Map<String, Object> b, HttpServletRequest r) {
    UUID admin = security.admin(r);
    String reason = text(b, "reason", 3, 2000);
    String state =
        switch (action) {
          case "quarantine" -> "QUARANTINED";
          case "block" -> "BLOCKED";
          case "restore" -> "CLEAR";
          default -> throw new ApiError(400, "VALIDATION_ERROR", "Unknown inquiry action.");
        };
    var rows = db.queryForList("SELECT developer_user_id FROM inquiries WHERE id=? FOR UPDATE", id);
    if (rows.isEmpty()) throw new ApiError(404, "NOT_FOUND", "Inquiry not found.");
    db.update(
        "UPDATE inquiries SET moderation_status=?,moderation_reason=?,updated_at=now() WHERE id=?",
        state,
        reason,
        id);
    db.update(
        "INSERT INTO inquiry_events(id,inquiry_id,event_type,actor_type,actor_id)"
            + " VALUES(?,?,?,'ADMIN',?)",
        Support.id(),
        id,
        "MODERATION_" + state,
        admin);
    if (!state.equals("CLEAR"))
      db.update(
          "UPDATE account_tokens SET used_at=now() WHERE inquiry_id=? AND used_at IS NULL", id);
    audit(admin, "INQUIRY", id, action, reason);
    mail.notify(
        (UUID) rows.get(0).get("developer_user_id"),
        "An inquiry moderation decision is available: " + state + ". " + reason);
    return Map.of("ok", true);
  }

  public AdminResponses.TaxonomyItems allTaxonomy(String kind, HttpServletRequest r) {
    security.admin(r);
    if (!Set.of("categories", "technologies").contains(kind))
      throw new ApiError(400, "VALIDATION_ERROR", "Choose a supported taxonomy.");
    return new AdminResponses.TaxonomyItems(db.queryForList("SELECT slug,name,active FROM " + kind + " ORDER BY name").stream().map(AdminResponses.Taxonomy::from).toList());
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Map<String, Object> metrics(HttpServletRequest r) {
    security.admin(r);
    return Map.of(
        "weekly",
        db.queryForList(
            "SELECT date_trunc('week',i.email_confirmed_at) AS week,count(DISTINCT i.id) AS"
                + " qualified,count(DISTINCT i.id) FILTER(WHERE EXISTS(SELECT 1 FROM inquiry_events"
                + " e WHERE e.inquiry_id=i.id AND e.event_type='HIRED')) AS hires,count(DISTINCT"
                + " i.id) FILTER(WHERE i.current_status='COMPLETED') AS completed FROM inquiries i"
                + " WHERE i.moderation_status='CLEAR' AND i.email_confirmed_at>now()-interval '12"
                + " weeks' GROUP BY 1 ORDER BY 1 DESC"),
        "failedEmails",
        db.queryForObject(
            "SELECT count(*) FROM email_outbox WHERE sent_at IS NULL AND attempts>=8",
            Integer.class),
        "sources",
        db.queryForList(
            "SELECT COALESCE(acquisition_source,'unattributed') AS source,count(*) AS"
                + " qualified,count(*) FILTER(WHERE EXISTS(SELECT 1 FROM inquiry_events e WHERE"
                + " e.inquiry_id=i.id AND e.event_type='HIRED')) AS hires FROM inquiries i WHERE"
                + " email_confirmed_at IS NOT NULL AND moderation_status='CLEAR' GROUP BY 1 ORDER"
                + " BY qualified DESC"),
        "reportedValues",
        db.queryForList(
            "SELECT reported_currency AS currency,sum(reported_value) AS total,count(*) AS"
                + " proposals FROM inquiries WHERE reported_value IS NOT NULL AND"
                + " moderation_status='CLEAR' GROUP BY reported_currency ORDER BY"
                + " reported_currency"),
        "marketplace",
        marketplaceMetrics.snapshot());
  }

  public void audit(UUID admin, String type, UUID target, String action, String reason) {
    db.update(
        "INSERT INTO moderation_actions(id,admin_id,target_type,target_id,action,reason)"
            + " VALUES(?,?,?,?,?,?)",
        Support.id(),
        admin,
        type,
        target,
        action,
        reason);
  }
}
