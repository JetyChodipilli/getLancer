package com.getlancer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Validated analytics JSON commands; semantic and ownership checks stay in services. */
public final class AnalyticsRequests {
  private AnalyticsRequests() {}

  public record Event(
      @NotBlank @Pattern(regexp = "home_view|search_performed|unavailable_builder_fallback|builder_profile_view|product_impression|product_view|demo_video_click|live_demo_click|build_similar_click|inquiry_started|component_discovery|component_view|component_source_view|lab_discovery|lab_view") String eventName,
      Boolean consent,
      @Size(max=140) @Pattern(regexp="[a-z0-9][a-z0-9-]{0,139}") String artifactSlug,
      UUID entityId,
      UUID eventId,
      UUID sessionId,
      Instant occurredAt,
      @Pattern(regexp = "web") String source,
      @Valid Properties properties) {}

  public record Properties(
      @Min(0) @Max(100000) Integer resultCount,
      @Min(0) @Max(100000) Integer rankPosition,
      @Min(0) @Max(100000) Integer queryLength,
      @Pattern(regexp = "explore|product|builder|inquiry|other") String sourcePage,
      @Pattern(regexp = "direct|organic|social|referral|builder_share|founder_outreach|agency_referral") String acquisitionSource,
      @Pattern(regexp = "youtube|loom|vimeo|external") String provider,
      @Pattern(regexp="ALL|PROJECT|BROWSER_LOCAL|SOURCE_ONLY|LIVE|TEST|REPLAY|ILLUSTRATIVE") String mode,
      @Size(max=80) @Pattern(regexp="[A-Za-z0-9._-]{1,80}") String version) {}
}
