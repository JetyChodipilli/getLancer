package com.getlancer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Validated admin JSON commands; semantic and ownership checks stay in services. */
public final class AdminRequests {
  private AdminRequests() {}

  public record Reason(
      @NotBlank @Size(min = 3, max = 2000) String reason) {}

  public record Resolve(
      @NotBlank @Size(min = 3, max = 2000) String reason,
      @Pattern(regexp = "|NONE|SUSPEND|HIDE|QUARANTINE|BLOCK|RESTORE") String targetAction) {}

  public record Taxonomy(
      @NotBlank @Size(min = 2, max = 100) String name,
      @NotBlank @Size(min = 2, max = 100) @Pattern(regexp = "[a-z0-9-]+") String slug,
      Boolean active) {}
}
