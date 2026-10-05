package com.getlancer.products;

import static com.getlancer.products.ProductRepository.*;
import static com.getlancer.shared.Support.*;

import com.getlancer.notifications.Mail;
import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import com.getlancer.shared.Pages;
import com.getlancer.shared.Rules;
import com.getlancer.shared.Support;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
public class ProductService {
  @org.springframework.beans.factory.annotation.Value("${app.reliability-min-sample:10}")
  int reliabilityMinimum = 10;

  final JdbcTemplate db;
  private final ProductRepository repository;
  final Security security;
  final Mail mail;
  private final ShowcaseMeasurements measurements;
  private final com.getlancer.publishing.PublishingCapacity publishing;

  public ProductService(
      JdbcTemplate db, Security security, Mail mail, ProductRepository repository, ShowcaseMeasurements measurements, com.getlancer.publishing.PublishingCapacity publishing) {
    this.db = db;
    this.security = security;
    this.mail = mail;
    this.repository = repository;
    this.measurements = measurements;
    this.publishing = publishing;
  }

  public Map<String, Object> dto(Map<String, Object> p) {
    return dto(p, repository.media(p.get("id")));
  }

  public List<Map<String, Object>> dtos(List<Map<String, Object>> rows) {
    if (rows.isEmpty()) return List.of();
    var ids = rows.stream().map(p -> p.get("id")).toArray();
    var media = repository.mediaForProducts(ids);
    Map<Object, List<Map<String, Object>>> grouped = new HashMap<>();
    for (var m : media) grouped.computeIfAbsent(m.get("product_id"), k -> new ArrayList<>()).add(m);
    return rows.stream().map(p -> dto(p, grouped.getOrDefault(p.get("id"), List.of()))).toList();
  }

  public Map<String, Object> dto(Map<String, Object> p, List<Map<String, Object>> media) {
    Map<String, Object> x = new LinkedHashMap<>();
    for (String k :
        List.of(
            "id",
            "slug",
            "title",
            "summary",
            "description",
            "category",
            "technology",
            "builder",
            "availability",
            "visibility")) x.put(k, p.get(k));
    x.put("bookedUntil", p.get("booked_until"));
    x.put(
        "demoHealth",
        Objects.equals(p.get("live_url"), p.get("demo_checked_url"))
            ? Objects.toString(p.get("demo_health"), "UNKNOWN")
            : "UNKNOWN");
    x.put("demoCheckedAt", p.get("demo_checked_at"));
    String[] old = {
      "builder_slug",
      "project_type",
      "contribution_text",
      "live_url",
      "video_url",
      "approval_status",
      "lifecycle_status",
      "available_for_similar_work",
      "updated_at",
      "repository_url",
      "pricing_note",
      "rights_confirmed"
    };
    String[] keys = {
      "builderSlug",
      "projectType",
      "contribution",
      "liveUrl",
      "videoUrl",
      "approvalStatus",
      "lifecycleStatus",
      "availableForSimilarWork",
      "updatedAt",
      "repositoryUrl",
      "pricingNote",
      "rightsConfirmed"
    };
    for (int n = 0; n < old.length; n++) x.put(keys[n], p.get(old[n]));
    x.put("pricingMode", p.get("pricing_mode"));
    x.put("priceMinMinor", p.get("price_min_minor"));
    x.put("priceMaxMinor", p.get("price_max_minor"));
    x.put("currency", p.get("currency_code"));
    if (!media.isEmpty())
      x.put("imageUrl", "/api/v1/media/" + media.get(0).get("id") + "?variant=thumbnail");
    x.put(
        "media",
        media.stream()
            .map(
                m ->
                    Map.of(
                        "id",
                        m.get("id"),
                        "url",
                        "/api/v1/media/" + m.get("id"),
                        "alt",
                        Objects.toString(m.get("alt_text"), "Project screenshot")))
            .toList());
    return x;
  }

  public Map<String, Object> managementDto(Map<String, Object> row) {
    var result = dto(row);
    result.put("moderationReason", row.get("moderation_reason"));
    return result;
  }

  public List<Map<String, Object>> managementDtos(List<Map<String, Object>> rows) {
    var result = dtos(rows);
    for (int i = 0; i < rows.size(); i++)
      result.get(i).put("moderationReason", rows.get(i).get("moderation_reason"));
    return result;
  }

  public Map<String, Object> reviewSummary(UUID builder) {
    return repository.reviewSummary(builder);
  }

  public Map<String, Object> search(
      String q,
      String category,
      String technology,
      String projectType,
      String availability,
      String builder,
      boolean liveDemo,
      String sort,
      int page,
      int size) {
    if (q.length() > 200 || page < 0 || page > 10000 || size < 1 || size > 100)
      throw new ApiError(400, "VALIDATION_ERROR", "Invalid search or pagination.");
    String sql = " WHERE " + PUBLIC;
    List<Object> a = new ArrayList<>();
    if (!builder.isBlank()) {
      sql += " AND d.slug=?";
      a.add(builder);
    }
    if (!q.isBlank()) {
      sql +=
          " AND (to_tsvector('english',p.title||' '||p.summary||' '||p.description||'"
              + " '||p.category||' '||p.technology) @@ websearch_to_tsquery('english',?) OR p.title"
              + " ILIKE ? OR d.display_name ILIKE ?)";
      a.add(q);
      a.add("%" + q.replace("%", "\\%").replace("_", "\\_") + "%");
      a.add("%" + q.replace("%", "\\%").replace("_", "\\_") + "%");
    }
    for (var e :
        Map.of(
                "p.category",
                category,
                "p.project_type",
                projectType,
                "d.availability_status",
                availability)
            .entrySet())
      if (!e.getValue().isBlank()) {
        sql += " AND " + e.getKey() + "=?";
        a.add(e.getValue());
      }
    if (!technology.isBlank()) {
      sql += " AND ?=ANY(string_to_array(p.technology,','))";
      a.add(technology);
    }
    if (liveDemo) sql += " AND p.live_url IS NOT NULL AND p.live_url<>''";
    Long total =
        db.queryForObject(
            "SELECT count(*) FROM products p JOIN users u ON u.id=p.owner_user_id JOIN"
                + " developer_profiles d ON d.user_id=u.id"
                + sql,
            Long.class,
            a.toArray());
    String order = "p.created_at DESC,p.id";
    if (sort.equals("updated")) order = "p.updated_at DESC,p.id";
    if (sort.equals("relevance") && !q.isBlank()) {
      order = "CASE WHEN p.title ILIKE ? THEN 0 ELSE 1 END,p.updated_at DESC,p.id";
      a.add("%" + q + "%");
    }
    a.add(size);
    a.add(page * size);
    var rows =
        db.queryForList(SELECT + sql + " ORDER BY " + order + " LIMIT ? OFFSET ?", a.toArray());
    return Map.of(
        "items",
        dtos(rows),
        "page",
        page,
        "size",
        size,
        "totalItems",
        total,
        "totalPages",
        (total + size - 1) / size);
  }

  public Map<String, Object> detail(String slug) {
    var rows = repository.findPublic(slug);
    if (rows.isEmpty()) throw new ApiError(404, "NOT_FOUND", "Project not found.");
    var result = dto(rows.get(0));
    result.put("repositoryVerified", repository.matchingVerificationCount(slug) > 0);
    result.put("reviewSummary", reviewSummary((UUID) rows.get(0).get("owner_user_id")));
    return result;
  }

  public Map<String, Object> builder(String slug) {
    var rows =
        db.queryForList(
            "SELECT d.user_id AS id,d.display_name AS"
                + " \"displayName\",d.headline,d.bio,d.technology,d.category,d.availability_status"
                + " AS \"availabilityStatus\",d.booked_until AS \"bookedUntil\",d.github_url AS"
                + " \"githubUrl\",d.linkedin_url AS \"linkedinUrl\",d.website_url AS"
                + " \"websiteUrl\",d.country,d.time_zone AS"
                + " \"timeZone\",d.languages,d.availability_confirmed_at AS"
                + " \"availabilityConfirmedAt\" FROM developer_profiles d JOIN users u ON"
                + " u.id=d.user_id WHERE d.slug=? AND d.approval_status='APPROVED' AND"
                + " u.account_status='ACTIVE'",
            slug);
    if (rows.isEmpty()) throw new ApiError(404, "NOT_FOUND", "Builder not found.");
    var profile = new LinkedHashMap<>(rows.get(0));
    var metric =
        db.queryForMap(
            "SELECT count(*) AS sample,count(first_response) AS responded,percentile_cont(0.5)"
                + " WITHIN GROUP(ORDER BY extract(epoch FROM"
                + " (first_response-email_confirmed_at))/3600) AS median_hours FROM (SELECT"
                + " i.email_confirmed_at,(SELECT min(e.created_at) FROM inquiry_events e WHERE"
                + " e.inquiry_id=i.id AND e.event_type='RESPONDED') AS first_response FROM"
                + " inquiries i WHERE i.developer_user_id=? AND i.moderation_status='CLEAR' AND"
                + " i.email_confirmed_at<now()-interval '48 hours') q",
            profile.get("id"));
    long sample = ((Number) metric.get("sample")).longValue();
    if (reliabilityMinimum > 0 && sample >= reliabilityMinimum) {
      profile.put("responseSample", sample);
      profile.put("responseRate", 100.0 * ((Number) metric.get("responded")).longValue() / sample);
      profile.put("medianResponseHours", metric.get("median_hours"));
    }
    return profile;
  }

  public Map<String, Object> reviews(String slug, HttpServletRequest r) {
    return Pages.query(
        db,
        r,
        "SELECT r.id,r.rating,r.review_text AS \"reviewText\",r.created_at AS \"createdAt\",CASE"
            + " WHEN r.visibility='NAMED' THEN (SELECT i.client_name FROM inquiries i WHERE"
            + " i.id=r.inquiry_id) ELSE 'Verified client' END AS \"clientName\" FROM reviews r JOIN"
            + " developer_profiles d ON d.user_id=r.developer_user_id JOIN users u ON"
            + " u.id=d.user_id WHERE d.slug=? AND d.approval_status='APPROVED' AND"
            + " u.account_status='ACTIVE' AND r.moderation_status='PUBLISHED' AND EXISTS(SELECT 1"
            + " FROM inquiries i WHERE i.id=r.inquiry_id AND i.moderation_status='CLEAR' AND"
            + " i.current_status='COMPLETED' AND i.email_confirmed_at IS NOT NULL) ORDER BY"
            + " r.created_at DESC,r.id",
        slug);
  }

  public Map<String, Object> own(HttpServletRequest r) {
    UUID u = security.developer(r, false);
    String sql = SELECT + " WHERE p.owner_user_id=?";
    List<Object> args = new ArrayList<>();
    args.add(u);
    for (String key : List.of("lifecycleStatus", "approvalStatus")) {
      String value = Objects.toString(r.getParameter(key), "");
      if (value.isBlank()) continue;
      Set<String> values =
          key.equals("lifecycleStatus")
              ? Set.of("DRAFT", "ACTIVE", "ARCHIVED", "SUSPENDED")
              : Set.of(
                  "DRAFT",
                  "PENDING_REVIEW",
                  "CHANGES_REQUESTED",
                  "APPROVED",
                  "REJECTED",
                  "SUSPENDED");
      if (!values.contains(value))
        throw new ApiError(400, "VALIDATION_ERROR", "Choose a valid showcase state.");
      sql +=
          key.equals("lifecycleStatus") ? " AND p.lifecycle_status=?" : " AND p.approval_status=?";
      args.add(value);
    }
    var result = Pages.query(db, r, sql + " ORDER BY p.updated_at DESC,p.id", args.toArray());
    result.put(
        "totalOwned",
        db.queryForObject("SELECT count(*) FROM products WHERE owner_user_id=?", Integer.class, u));
    result.put("items", managementDtos(Pages.items(result)));
    result.put("capacity", publishing.capacity(u,"PROJECT"));
    result.put(
        "activeCount",
        db.queryForObject(
            "SELECT count(*) FROM products WHERE owner_user_id=? AND lifecycle_status='ACTIVE'",
            Integer.class,
            u));
    return result;
  }

  @Transactional
  public Map<String, Object> create(Map<String, Object> b, HttpServletRequest r) {
    UUID u = security.developer(r, true), p = id();
    String title = text(b, "title", 3, 120);
    db.update(
        "INSERT INTO"
            + " products(id,owner_user_id,slug,title,summary,description,project_type,category,technology,contribution_text)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?)",
        p,
        u,
        title.toLowerCase().replaceAll("[^a-z0-9]+", "-") + "-" + p.toString().substring(0, 8),
        title,
        "",
        "",
        "PERSONAL",
        "",
        "",
        "");
    save(p, b, u);
    return Map.of("id", p);
  }

  @Transactional
  public Map<String, Object> edit(UUID id, Map<String, Object> b, HttpServletRequest r) {
    UUID u = security.developer(r, true);
    owned(id, u);
    save(id, b, u);
    return Map.of("ok", true);
  }

  void save(UUID p, Map<String, Object> b, UUID u) {
    String category = text(b, "category", 1, 100);
    if (db.queryForObject(
            "SELECT count(*) FROM categories WHERE name=? AND active=true", Integer.class, category)
        == 0) throw new ApiError(400, "VALIDATION_ERROR", "Choose an active business category.");
    String technology = text(b, "technology", 0, 300);
    Set<String> techs = new LinkedHashSet<>();
    for (String t : technology.split(","))
      if (!t.isBlank()) {
        t = t.trim();
        if (db.queryForObject(
                "SELECT count(*) FROM technologies WHERE name=? AND active=true", Integer.class, t)
            == 0) throw new ApiError(400, "VALIDATION_ERROR", "Choose active technology tags.");
        techs.add(t);
      }
    technology = String.join(",", techs);
    String live = text(b, "liveUrl", 0, 1000), video = text(b, "videoUrl", 0, 1000);
    Rules.safeUrl(live);
    Rules.safeUrl(video);
    String repository = text(b, "repositoryUrl", 0, 1000), pricing = text(b, "pricingNote", 0, 300);
    ProductPricing terms = ProductPricing.parse(b);
    Rules.safeUrl(repository);
    String type = text(b, "projectType", 1, 40), visibility = text(b, "visibility", 1, 30);
    if (!Set.of(
                "COMMERCIAL",
                "CLIENT",
                "SAAS",
                "PERSONAL",
                "OPEN_SOURCE",
                "PROTOTYPE",
                "HACKATHON",
                "LEARNING",
                "PRIVATE_CASE_STUDY",
                "NDA_CONFIDENTIAL")
            .contains(type)
        || !Set.of("PUBLIC", "PRIVATE_CASE_STUDY", "NDA_SAFE").contains(visibility))
      throw new ApiError(400, "VALIDATION_ERROR", "Choose a valid project type and visibility.");
    db.update(
        "UPDATE products SET"
            + " title=?,summary=?,description=?,project_type=?,category=?,technology=?,visibility=?,contribution_text=?,live_url=?,video_url=?,available_for_similar_work=?,rights_confirmed=?,approval_status='DRAFT',lifecycle_status='DRAFT',updated_at=now()"
            + " WHERE id=? AND owner_user_id=?",
        text(b, "title", 3, 120),
        text(b, "summary", 5, 240),
        text(b, "description", 20, 10000),
        type,
        category,
        technology,
        visibility,
        text(b, "contribution", 10, 3000),
        live,
        video,
        Boolean.TRUE.equals(b.get("availableForSimilarWork")),
        Boolean.TRUE.equals(b.get("rightsConfirmed")),
        p,
        u);
    db.update(
        "UPDATE products SET"
            + " repository_url=?,pricing_note=?,pricing_mode=?,price_min_minor=?,price_max_minor=?,currency_code=?,demo_health='UNKNOWN',demo_checked_at=NULL,demo_checked_url=NULL"
            + " WHERE id=?",
        repository,
        pricing,
        terms.mode(),
        terms.min(),
        terms.max(),
        terms.currency(),
        p);
    db.update(
        "DELETE FROM repository_verifications WHERE product_id=? AND repository_url<>?",
        p,
        repository);
    db.update("DELETE FROM product_categories WHERE product_id=?", p);
    db.update(
        "INSERT INTO product_categories SELECT ?,slug FROM categories WHERE name=?", p, category);
    db.update("DELETE FROM product_technologies WHERE product_id=?", p);
    for (String t : techs)
      db.update(
          "INSERT INTO product_technologies SELECT ?,slug FROM technologies WHERE name=?", p, t);
  }

  public Map<String, Object> owned(UUID p, UUID u) {
    var rows = repository.findOwnedForUpdate(p, u);
    if (rows.isEmpty()) throw new ApiError(404, "NOT_FOUND", "Project not found.");
    if (rows.get(0).get("approval_status").equals("SUSPENDED"))
      throw new ApiError(403, "FORBIDDEN", "This project is suspended.");
    return rows.get(0);
  }

  @Transactional
  public Map<String, Object> action(UUID id, String action, HttpServletRequest r) {
    UUID u = security.developer(r, true);
    publishing.lock(u);
    db.queryForMap("SELECT * FROM showcase_entitlements WHERE user_id=? FOR UPDATE", u);
    var p = owned(id, u);
    switch (action) {
      case "submit" -> {
        if (!Set.of("DRAFT", "CHANGES_REQUESTED").contains(p.get("approval_status")))
          throw new ApiError(409, "INVALID_STATE_TRANSITION", "Project cannot be submitted now.");
        if (!Boolean.TRUE.equals(p.get("rights_confirmed")))
          throw new ApiError(400, "VALIDATION_ERROR", "Confirm your right to showcase this work.");
        if (p.get("visibility").equals("PUBLIC")
            && db.queryForObject(
                    "SELECT count(*) FROM product_media WHERE product_id=? AND verified=true",
                    Integer.class,
                    id)
                == 0
            && Objects.toString(p.get("video_url"), "").isBlank()
            && Objects.toString(p.get("live_url"), "").isBlank())
          throw new ApiError(
              400,
              "VALIDATION_ERROR",
              "Add a proof image, demo video or live demo before submitting.");
        db.update(
            "UPDATE products SET approval_status='PENDING_REVIEW',updated_at=now() WHERE id=?", id);
      }
      case "archive" -> {
        if (!Set.of("ACTIVE", "ARCHIVED").contains(p.get("lifecycle_status")))
          throw new ApiError(
              409, "INVALID_STATE_TRANSITION", "Only an active showcase can be archived.");
        if (p.get("lifecycle_status").equals("ACTIVE")) {
          db.update(
              "UPDATE products SET lifecycle_status='ARCHIVED',updated_at=now() WHERE id=?", id);
          db.update(
              "INSERT INTO analytics_events(id,event_name,entity_id,context)"
                  + " VALUES(?,'product_archived',?,jsonb_build_object('builderId',?::text,'activeCount',?::integer))",
              id(), id, u.toString(), db.queryForObject("SELECT count(*) FROM products WHERE owner_user_id=? AND lifecycle_status='ACTIVE'", Integer.class, u));
        }
      }
      case "activate" -> activate(id, u, true);
      default -> throw new ApiError(404, "NOT_FOUND", "Action not found.");
    }
    return Map.of("ok", true);
  }

  public void lockOwner(UUID owner) { publishing.lock(owner); }

  public void activate(UUID p, UUID u, boolean fail) {
    publishing.lock(u);
    db.queryForMap("SELECT * FROM showcase_entitlements WHERE user_id=? FOR UPDATE", u);
    var product = db.queryForMap("SELECT * FROM products WHERE id=? FOR UPDATE", p);
    if (!product.get("approval_status").equals("APPROVED"))
      throw new ApiError(409, "INVALID_STATE_TRANSITION", "Product approval is required.");
    if (product.get("lifecycle_status").equals("ACTIVE")) return;
    boolean approved =
        db.queryForObject(
                "SELECT count(*) FROM developer_profiles d JOIN users u ON u.id=d.user_id WHERE"
                    + " d.user_id=? AND d.approval_status='APPROVED' AND u.account_status='ACTIVE'"
                    + " AND u.email_verified_at IS NOT NULL",
                Integer.class,
                u)
            > 0;
    int count =
        db.queryForObject(
            "SELECT count(*) FROM products WHERE owner_user_id=? AND lifecycle_status='ACTIVE'",
            Integer.class,
            u);
    if (!approved || !publishing.projectFits(u,p,false)) {
      if (approved) measurements.blocked(u, p, count);
      if (fail)
        throw new ApiError(409, "SLOT_LIMIT_REACHED", "All slots for this project category are in use. Archive a project in this category or buy another project slot.");
      return;
    }
    db.update("UPDATE products SET lifecycle_status='ACTIVE',updated_at=now() WHERE id=?", p);
    db.update(
        "INSERT INTO analytics_events(id,event_name,entity_id,context) VALUES(?,'product_activated',?,jsonb_build_object('builderId',?::text,'activeCount',?::integer))",
        id(), p, u.toString(), count + 1);
  }

  @Transactional
  public Map<String, Object> saveProduct(UUID id, HttpServletRequest r) {
    UUID u = security.user(r);
    if (db.queryForList(SELECT + " WHERE p.id=? AND " + PUBLIC, id).isEmpty())
      throw new ApiError(404, "NOT_FOUND", "Project unavailable.");
    if (db.update(
            "INSERT INTO saved_products(user_id,product_id) VALUES(?,?) ON CONFLICT DO NOTHING",
            u,
            id)
        > 0)
      db.update(
          "INSERT INTO analytics_events(id,event_name,entity_id) VALUES(?,'product_saved',?)",
          Support.id(),
          id);
    return Map.of("ok", true);
  }

  @Transactional
  public Map<String, Object> unsave(UUID id, HttpServletRequest r) {
    if (db.update(
            "DELETE FROM saved_products WHERE user_id=? AND product_id=?", security.user(r), id)
        > 0)
      db.update(
          "INSERT INTO analytics_events(id,event_name,entity_id) VALUES(?,'product_unsaved',?)",
          Support.id(),
          id);
    return Map.of("ok", true);
  }

  public Map<String, Object> saved(HttpServletRequest r) {
    UUID u = security.user(r);
    String sql = SELECT + " JOIN saved_products s ON s.product_id=p.id WHERE s.user_id=?";
    List<Object> args = new ArrayList<>();
    args.add(u);
    String ids = Objects.toString(r.getParameter("ids"), "");
    if (!ids.isBlank()) {
      String[] selected = ids.split(",", -1);
      if (selected.length > 50)
        throw new ApiError(400, "VALIDATION_ERROR", "Select at most 50 projects.");
      sql += " AND p.id IN (" + String.join(",", Collections.nCopies(selected.length, "?")) + ")";
      for (String value : selected) args.add(uuid(value));
    }
    var result = Pages.query(db, r, sql + " ORDER BY s.created_at DESC,p.id", args.toArray());
    result.put(
        "items",
        Pages.items(result).stream()
            .map(
                p -> {
                  boolean visible =
                      Rules.publicProduct(
                          (String) p.get("approval_status"),
                          (String) p.get("lifecycle_status"),
                          (String) p.get("visibility"),
                          (String) p.get("owner_status"),
                          (String) p.get("profile_status"));
                  return visible
                      ? Map.of(
                          "id",
                          p.get("id"),
                          "title",
                          p.get("title"),
                          "summary",
                          p.get("summary"),
                          "slug",
                          p.get("slug"),
                          "available",
                          true)
                      : Map.of("id", p.get("id"), "available", false);
                })
            .toList());
    return result;
  }

  public Map<String, Object> categories() {
    return Map.of(
        "items", db.queryForList("SELECT * FROM categories WHERE active=true ORDER BY name"));
  }

  public Map<String, Object> technologies() {
    return Map.of(
        "items", db.queryForList("SELECT * FROM technologies WHERE active=true ORDER BY name"));
  }
}
