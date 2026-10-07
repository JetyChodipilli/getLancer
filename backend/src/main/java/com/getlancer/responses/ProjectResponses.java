package com.getlancer.responses;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ProjectResponses {
  private ProjectResponses() {}

  public record Media(UUID id, String url, String alt) {
    public static Media from(Map<String,Object> row) {
      return new Media(ResponseRows.uuid(row,"id"),ResponseRows.string(row,"url"),ResponseRows.string(row,"alt"));
    }
  }

  public record PoolUsage(int free, int used) {
    public static PoolUsage from(Map<String,Object> row) {
      return new PoolUsage(ResponseRows.integer32(row,"free"),ResponseRows.integer32(row,"used"));
    }
  }

  public record Capacity(String pool, int purchased, int free, int used, int limit, int earned,
      PoolUsage regular, PoolUsage college, int extraUsed, int extraLimit, int availableRegular, int availableCollege) {
    public static Capacity from(Map<String,Object> row) {
      return new Capacity(ResponseRows.string(row,"pool"),ResponseRows.integer32(row,"purchased"),
          ResponseRows.integer32(row,"free"),ResponseRows.integer32(row,"used"),ResponseRows.integer32(row,"limit"),
          ResponseRows.integer32(row,"earned"),PoolUsage.from(ResponseRows.row(row,"regular")),PoolUsage.from(ResponseRows.row(row,"college")),
          ResponseRows.integer32(row,"extraUsed"),ResponseRows.integer32(row,"extraLimit"),
          ResponseRows.integer32(row,"availableRegular"),ResponseRows.integer32(row,"availableCollege"));
    }
  }

  public record Owned(List<ManagementProject> items, int page, int size, boolean hasMore, long totalItems,
      long totalPages, int totalOwned, Capacity capacity, int activeCount) {
    public static Owned from(Map<String,Object> row) {
      return new Owned(ResponseRows.rows(row,"items",ManagementProject::from),ResponseRows.integer32(row,"page"),
          ResponseRows.integer32(row,"size"),ResponseRows.bool(row,"hasMore"),ResponseRows.integer64(row,"totalItems"),
          ResponseRows.integer64(row,"totalPages"),ResponseRows.integer32(row,"totalOwned"),
          Capacity.from(ResponseRows.row(row,"capacity")),ResponseRows.integer32(row,"activeCount"));
    }
  }

  public record Project(UUID id,
      String slug,
      String title,
      String summary,
      String description,
      String category,
      String technology,
      String builder,
      String availability,
      String visibility,
      Date bookedUntil,
      String demoHealth,
      Timestamp demoCheckedAt,
      String builderSlug,
      String projectType,
      String contribution,
      String liveUrl,
      String videoUrl,
      String approvalStatus,
      String lifecycleStatus,
      Boolean availableForSimilarWork,
      Timestamp updatedAt,
      String repositoryUrl,
      String pricingNote,
      Boolean rightsConfirmed,
      String pricingMode,
      Long priceMinMinor,
      Long priceMaxMinor,
      String currency,
      @JsonInclude(JsonInclude.Include.NON_NULL) String imageUrl,
      List<Media> media) {
    public static Project from(Map<String,Object> row) {
      return new Project(ResponseRows.uuid(row,"id"),
          ResponseRows.string(row,"slug"),
          ResponseRows.string(row,"title"),
          ResponseRows.string(row,"summary"),
          ResponseRows.string(row,"description"),
          ResponseRows.string(row,"category"),
          ResponseRows.string(row,"technology"),
          ResponseRows.string(row,"builder"),
          ResponseRows.string(row,"availability"),
          ResponseRows.string(row,"visibility"),
          ResponseRows.date(row,"bookedUntil"),
          ResponseRows.string(row,"demoHealth"),
          ResponseRows.timestamp(row,"demoCheckedAt"),
          ResponseRows.string(row,"builderSlug"),
          ResponseRows.string(row,"projectType"),
          ResponseRows.string(row,"contribution"),
          ResponseRows.string(row,"liveUrl"),
          ResponseRows.string(row,"videoUrl"),
          ResponseRows.string(row,"approvalStatus"),
          ResponseRows.string(row,"lifecycleStatus"),
          ResponseRows.bool(row,"availableForSimilarWork"),
          ResponseRows.timestamp(row,"updatedAt"),
          ResponseRows.string(row,"repositoryUrl"),
          ResponseRows.string(row,"pricingNote"),
          ResponseRows.bool(row,"rightsConfirmed"),
          ResponseRows.string(row,"pricingMode"),
          ResponseRows.integer64(row,"priceMinMinor"),
          ResponseRows.integer64(row,"priceMaxMinor"),
          ResponseRows.string(row,"currency"),
          ResponseRows.string(row,"imageUrl"),
          ResponseRows.rows(row,"media", Media::from));
    }
  }

  public record ManagementProject(UUID id,
      String slug,
      String title,
      String summary,
      String description,
      String category,
      String technology,
      String builder,
      String availability,
      String visibility,
      Date bookedUntil,
      String demoHealth,
      Timestamp demoCheckedAt,
      String builderSlug,
      String projectType,
      String contribution,
      String liveUrl,
      String videoUrl,
      String approvalStatus,
      String lifecycleStatus,
      Boolean availableForSimilarWork,
      Timestamp updatedAt,
      String repositoryUrl,
      String pricingNote,
      Boolean rightsConfirmed,
      String pricingMode,
      Long priceMinMinor,
      Long priceMaxMinor,
      String currency,
      @JsonInclude(JsonInclude.Include.NON_NULL) String imageUrl,
      List<Media> media,
      String moderationReason) {
    public static ManagementProject from(Map<String,Object> row) {
      return new ManagementProject(ResponseRows.uuid(row,"id"),
          ResponseRows.string(row,"slug"),
          ResponseRows.string(row,"title"),
          ResponseRows.string(row,"summary"),
          ResponseRows.string(row,"description"),
          ResponseRows.string(row,"category"),
          ResponseRows.string(row,"technology"),
          ResponseRows.string(row,"builder"),
          ResponseRows.string(row,"availability"),
          ResponseRows.string(row,"visibility"),
          ResponseRows.date(row,"bookedUntil"),
          ResponseRows.string(row,"demoHealth"),
          ResponseRows.timestamp(row,"demoCheckedAt"),
          ResponseRows.string(row,"builderSlug"),
          ResponseRows.string(row,"projectType"),
          ResponseRows.string(row,"contribution"),
          ResponseRows.string(row,"liveUrl"),
          ResponseRows.string(row,"videoUrl"),
          ResponseRows.string(row,"approvalStatus"),
          ResponseRows.string(row,"lifecycleStatus"),
          ResponseRows.bool(row,"availableForSimilarWork"),
          ResponseRows.timestamp(row,"updatedAt"),
          ResponseRows.string(row,"repositoryUrl"),
          ResponseRows.string(row,"pricingNote"),
          ResponseRows.bool(row,"rightsConfirmed"),
          ResponseRows.string(row,"pricingMode"),
          ResponseRows.integer64(row,"priceMinMinor"),
          ResponseRows.integer64(row,"priceMaxMinor"),
          ResponseRows.string(row,"currency"),
          ResponseRows.string(row,"imageUrl"),
          ResponseRows.rows(row,"media", Media::from),
          ResponseRows.string(row,"moderationReason"));
    }
  }
}
