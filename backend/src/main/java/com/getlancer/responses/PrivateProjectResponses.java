package com.getlancer.responses;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.sql.Timestamp;
import java.util.Map;

public final class PrivateProjectResponses {
  private PrivateProjectResponses() {}

  public record AccessGrant(@JsonProperty("client_email") String clientEmail,
      @JsonProperty("expires_at") Timestamp expiresAt) {
    public static AccessGrant from(Map<String, Object> row) {
      return new AccessGrant(ResponseRows.string(row,"client_email"), ResponseRows.timestamp(row,"expires_at"));
    }
  }
  public record Granted(String path, int expiresInDays) {}
}
