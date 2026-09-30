package com.getlancer.analytics;

import static com.getlancer.shared.Support.*;

import com.getlancer.products.ProductRepository;
import com.getlancer.shared.ApiError;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

@org.springframework.stereotype.Service
public class AnalyticsService {
  @org.springframework.beans.factory.annotation.Value("${app.analytics-enabled:false}")
  boolean enabled;

  @org.springframework.beans.factory.annotation.Value("${app.analytics-salt:}")
  String salt = "";

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

  public AnalyticsService(JdbcTemplate db) {
    this.db = db;
  }

  public Map<String, Object> record(Map<String, Object> body) {
    if (!enabled) return Map.of("ok", true, "recorded", false);
    String event = text(body, "eventName", 1, 60);
    if (!Set.of(
            "home_view",
            "search_performed",
            "builder_profile_view",
            "product_impression",
            "product_view",
            "demo_video_click",
            "live_demo_click",
            "build_similar_click",
            "inquiry_started")
        .contains(event)) throw new ApiError(400, "VALIDATION_ERROR", "Unsupported public event.");
    UUID entity = null;
    if (!Set.of("home_view", "search_performed").contains(event)) {
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
    UUID eventId = body.get("eventId") == null ? null : uuid(body.get("eventId"));
    Map<String, Object> context = cleanContext(body.get("properties"));
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
    db.update(
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
    return Map.of("ok", true);
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
