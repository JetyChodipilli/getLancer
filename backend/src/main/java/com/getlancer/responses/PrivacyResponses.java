package com.getlancer.responses;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;

public final class PrivacyResponses {
  private PrivacyResponses() {}

  public record Configuration(String version, boolean approved, String support, String privacy, String copyright) {}
  public record Review(String status, boolean engagementRecordsRetained) {}
  public record DeletionRequest(UUID id, String status, @JsonProperty("created_at") Timestamp createdAt,
      String resolution, @JsonProperty("processed_at") Timestamp processedAt) {
    public static DeletionRequest from(Map<String,Object> row) {
      return new DeletionRequest(ResponseRows.uuid(row,"id"),ResponseRows.string(row,"status"),
          ResponseRows.timestamp(row,"created_at"),ResponseRows.string(row,"resolution"),ResponseRows.timestamp(row,"processed_at"));
    }
  }
}
