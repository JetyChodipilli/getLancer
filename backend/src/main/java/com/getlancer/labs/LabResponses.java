package com.getlancer.labs;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class LabResponses {
  private LabResponses() {}
  public record Manifest(String id,String title,String summary,String language,String framework,String sourceUrl,String setup,
      String executionMode,List<LabManifest.Scenario> scenarios) {}
  public record Catalogue(List<Manifest> items,LabConfiguration.Runtime runtime) {}
  public record Quota(int dailyLimit,int remaining,Instant resetAt,UUID activeRunId,LabConfiguration.Runtime runtime) {}
  public record Run(UUID id,String manifestId,String scenarioId,String status,String executionMode,boolean verified,
      Instant requestedAt,Instant expiresAt,String reason,String eventsUrl) {}
  public record RequestResult(UUID runId,UUID commandId,String output,String summary) {}
  public record EventData(String status,String reason) {}
  public record Event(UUID runId,long sequence,String type,Instant recordedAt,EventData data) {}
  public record Admin(LabConfiguration.Runtime runtime,boolean paused,String pauseReason,long queued,long starting,long running,
      long cancelling,long attention,long activeReservations,long memoryReservedMiB,long dailyReservedMicros,long monthlyReservedMicros) {}
}
