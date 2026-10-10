package com.getlancer.responses;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Fixed administrator review projections; authentication and MFA stay in the service. */
public final class AdminResponses {
  private AdminResponses() {}
  private static final ObjectMapper ARTIFACT_JSON=new ObjectMapper();

  private static JsonNode artifact(Map<String,Object> row) {
    Object value=row.get("education_snapshot");
    if(value==null)return null;
    try {return value instanceof Map || value instanceof JsonNode ? ARTIFACT_JSON.valueToTree(value):ARTIFACT_JSON.readTree(value.toString());}
    catch(java.io.IOException invalid){throw new IllegalStateException("Invalid reported education evidence",invalid);}
  }

  public record TaxonomyItems(List<Taxonomy> items) {}

  public record Profile(UUID id,
      String displayName,
      String headline,
      String bio,
      String technology,
      String category,
      @JsonProperty("github_url") String github_url,
      @JsonProperty("linkedin_url") String linkedin_url,
      @JsonProperty("website_url") String website_url,
      String country,
      @JsonProperty("time_zone") String time_zone,
      String languages,
      @JsonProperty("approval_status") String approval_status) {
    public static Profile from(Map<String, Object> row) {
      return new Profile(ResponseRows.uuid(row,"id"),
          ResponseRows.string(row,"displayName"),
          ResponseRows.string(row,"headline"),
          ResponseRows.string(row,"bio"),
          ResponseRows.string(row,"technology"),
          ResponseRows.string(row,"category"),
          ResponseRows.string(row,"github_url"),
          ResponseRows.string(row,"linkedin_url"),
          ResponseRows.string(row,"website_url"),
          ResponseRows.string(row,"country"),
          ResponseRows.string(row,"time_zone"),
          ResponseRows.string(row,"languages"),
          ResponseRows.string(row,"approval_status"));
    }
  }

  public record Report(UUID id,
      @JsonProperty("reporter_id") UUID reporter_id,
      @JsonProperty("target_type") String target_type,
      @JsonProperty("target_id") UUID target_id,
      String reason,
      String detail,
      String status,
      String resolution,
      @JsonProperty("created_at") Timestamp created_at,
      String severity,
      @JsonProperty("triage_note") String triage_note,
      @JsonProperty("updated_at") Timestamp updated_at,
      @JsonProperty("enforcement_action") String enforcement_action,
      UUID education_release_id,String source_hash,JsonNode education_snapshot) {
    public static Report from(Map<String, Object> row) {
      return new Report(ResponseRows.uuid(row,"id"),
          ResponseRows.uuid(row,"reporter_id"),
          ResponseRows.string(row,"target_type"),
          ResponseRows.uuid(row,"target_id"),
          ResponseRows.string(row,"reason"),
          ResponseRows.string(row,"detail"),
          ResponseRows.string(row,"status"),
          ResponseRows.string(row,"resolution"),
          ResponseRows.timestamp(row,"created_at"),
          ResponseRows.string(row,"severity"),
          ResponseRows.string(row,"triage_note"),
          ResponseRows.timestamp(row,"updated_at"),
          ResponseRows.string(row,"enforcement_action"),ResponseRows.uuid(row,"education_release_id"),ResponseRows.string(row,"source_hash"),artifact(row));
    }
  }

  public record Review(UUID id,
      Integer rating,
      @JsonProperty("review_text") String review_text) {
    public static Review from(Map<String, Object> row) {
      return new Review(ResponseRows.uuid(row,"id"),
          ResponseRows.integer32(row,"rating"),
          ResponseRows.string(row,"review_text"));
    }
  }

  public record History(UUID id,
      @JsonProperty("admin_id") UUID admin_id,
      @JsonProperty("target_type") String target_type,
      @JsonProperty("target_id") UUID target_id,
      String action,
      String reason,
      @JsonProperty("created_at") Timestamp created_at) {
    public static History from(Map<String, Object> row) {
      return new History(ResponseRows.uuid(row,"id"),
          ResponseRows.uuid(row,"admin_id"),
          ResponseRows.string(row,"target_type"),
          ResponseRows.uuid(row,"target_id"),
          ResponseRows.string(row,"action"),
          ResponseRows.string(row,"reason"),
          ResponseRows.timestamp(row,"created_at"));
    }
  }

  public record Account(UUID id,
      String email,
      @JsonProperty("account_status") String account_status,
      @JsonProperty("moderation_reason") String moderation_reason) {
    public static Account from(Map<String, Object> row) {
      return new Account(ResponseRows.uuid(row,"id"),
          ResponseRows.string(row,"email"),
          ResponseRows.string(row,"account_status"),
          ResponseRows.string(row,"moderation_reason"));
    }
  }

  public record Taxonomy(String slug,
      String name,
      Boolean active) {
    public static Taxonomy from(Map<String, Object> row) {
      return new Taxonomy(ResponseRows.string(row,"slug"),
          ResponseRows.string(row,"name"),
          ResponseRows.bool(row,"active"));
    }
  }
}
