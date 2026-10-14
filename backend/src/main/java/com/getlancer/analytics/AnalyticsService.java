package com.getlancer.analytics;

import static com.getlancer.shared.Support.hash;
import static com.getlancer.shared.Support.id;
import static com.getlancer.shared.Support.text;
import static com.getlancer.shared.Support.uuid;

import com.getlancer.products.ProductRepository;
import com.getlancer.components.ComponentService;
import com.getlancer.labs.LabService;
import org.springframework.transaction.annotation.Transactional;
import com.getlancer.shared.ApiError;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {
  final boolean enabled;

  final String salt;

  public static final Set<String> CHANNELS =
      Set.of(
          "direct",
          "organic",
          "social",
          "referral",
          "builder_share",
          "founder_outreach",
          "agency_referral");
  final JdbcTemplate db;
  private final ComponentService components;
  private final LabService labs;
  private final UsageCounters counters;

  public AnalyticsService(JdbcTemplate db) { this(db, false, "", null, null, new UsageCounters(db)); }

  @Autowired
  public AnalyticsService(JdbcTemplate db, @Value("${app.analytics-enabled:false}") boolean enabled,
      @Value("${app.analytics-salt:}") String salt, ComponentService components, LabService labs, UsageCounters counters) {
    this.db = db; this.enabled = enabled; this.salt = salt; this.components=components; this.labs=labs; this.counters=counters;
  }

  @Transactional
  public Map<String, Object> record(Map<String, Object> body) {
    if (!enabled || !Boolean.TRUE.equals(body.get("consent"))) return Map.of("ok", true, "recorded", false);
    String event = text(body, "eventName", 1, 60);
    if (!Set.of(
            "home_view",
            "search_performed",
            "unavailable_builder_fallback",
            "builder_profile_view",
            "product_impression",
            "product_view",
            "demo_video_click",
            "live_demo_click",
            "build_similar_click",
            "inquiry_started", "component_discovery", "component_view", "component_source_view", "lab_discovery", "lab_view")
        .contains(event)) throw new ApiError(400, "VALIDATION_ERROR", "Unsupported public event.");
    UUID entity = null;
    String mode="PROJECT", version="current";
    if (Set.of("home_view","search_performed","component_discovery","lab_discovery").contains(event)) {
      mode="ALL"; version="catalogue";
    } else if (Set.of("component_view","component_source_view").contains(event)) {
      String slug=artifactSlug(body);
      var artifact=components.detail(slug); // Reuse source visibility, review and withdrawal rules.
      mode="FRONTEND".equals(artifact.get("kind"))?"BROWSER_LOCAL":"SOURCE_ONLY";
      version=java.util.Objects.toString(artifact.get("sha256"));
      if(!version.matches("[a-f0-9]{64}")) throw new ApiError(503,"ARTIFACT_UNAVAILABLE","Reviewed source metadata is unavailable.");
    } else if (event.equals("lab_view")) {
      String slug=artifactSlug(body);
      var artifact=labs.catalogue().items().stream().filter(item->item.id().equals(slug)).findFirst()
          .orElseThrow(()->new ApiError(404,"NOT_FOUND","Lab unavailable."));
      mode=artifact.availability().enabled()?"LIVE":"SOURCE_ONLY";
      version=db.queryForObject("SELECT payload_sha256 FROM lab_manifests WHERE id=? AND revoked_at IS NULL",String.class,slug);
    } else if (!Set.of("home_view", "search_performed").contains(event)) {
      entity = uuid(body.get("entityId"));
      if (event.equals("builder_profile_view")) {
        if (db.queryForObject(
                "SELECT count(*) FROM developer_profiles d JOIN users u ON u.id=d.user_id WHERE"
                    + " d.user_id=? AND d.approval_status='APPROVED' AND u.account_status='ACTIVE'",
                Integer.class,
                entity)
            == 0) throw new ApiError(404, "NOT_FOUND", "Builder unavailable.");
      } else if (db.queryForList(
              ProductRepository.SELECT + " WHERE p.id=? AND " + ProductRepository.PUBLIC, entity)
          .isEmpty()) throw new ApiError(404, "NOT_FOUND", "Project unavailable.");
    }
    if(body.get("eventId")==null)throw new ApiError(400,"VALIDATION_ERROR","Supply a stable observation identity.");
    UUID eventId = uuid(body.get("eventId"));
    if(body.get("properties") instanceof Map<?,?> properties && properties.get("mode") instanceof String requestedMode && Set.of("REPLAY","ILLUSTRATIVE").contains(requestedMode))
      throw new ApiError(400,"VALIDATION_ERROR","Replay and illustrative activity cannot be submitted as real observations.");
    Map<String, Object> context = cleanContext(body.get("properties"));
    context.put("mode",mode); context.put("version",version);
    String session = null;
    if (!salt.isBlank() && body.get("sessionId") != null)
      session = hash(salt + ":" + uuid(body.get("sessionId")));
    java.time.Instant at = java.time.Instant.now();
    if (body.get("occurredAt") != null) {
      try {
        var supplied = java.time.Instant.parse(body.get("occurredAt").toString());
        if (supplied.isAfter(at.plusSeconds(60)) || supplied.isBefore(at.minusSeconds(86400)))
          throw new IllegalArgumentException();
        at = supplied;
      } catch (Exception e) {
        throw new ApiError(
            400, "VALIDATION_ERROR", "Event timestamp is outside the supported window.");
      }
    }
    String json;
    try {
      json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(context);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    int recorded=db.update(
        "INSERT INTO"
            + " analytics_events(id,event_name,entity_id,client_event_id,context,session_hash,occurred_at,source)"
            + " VALUES(?,?,?,?,?::jsonb,?,?,'web') ON CONFLICT(client_event_id) DO NOTHING",
        id(),
        event,
        entity,
        eventId,
        json,
        session,
        java.sql.Timestamp.from(at));
    if(recorded==1)counters.observation(event,mode,version);
    return Map.of("ok", true,"recorded",recorded==1);
  }

  private static String artifactSlug(Map<String,Object> body) {
    String slug=text(body,"artifactSlug",1,140);
    if(!slug.matches("[a-z0-9][a-z0-9-]{0,139}"))throw new ApiError(400,"VALIDATION_ERROR","Choose a valid artifact slug.");
    return slug;
  }

  public static Map<String, Object> cleanContext(Object raw) {
    Map<String, Object> out = new LinkedHashMap<>();
    if (!(raw instanceof Map<?, ?> values)) return out;
    for (String key : List.of("resultCount", "rankPosition", "queryLength")) {
      Object value = values.get(key);
      if (value instanceof Number n
          && n.doubleValue() == n.intValue()
          && n.intValue() >= 0
          && n.intValue() <= 100000) out.put(key, n.intValue());
    }
    Map<String, Set<String>> enums =
        Map.of(
            "sourcePage",
            Set.of("explore", "product", "builder", "inquiry", "other"),
            "acquisitionSource",
            CHANNELS,
            "provider",
            Set.of("youtube", "loom", "vimeo", "external"));
    enums.forEach(
        (key, allowed) -> {
          Object value = values.get(key);
          if (value instanceof String && allowed.contains(value)) out.put(key, value);
        });
    // Never accept raw search text, URLs, emails, tokens, or arbitrary property keys.
    return out;
  }
}
