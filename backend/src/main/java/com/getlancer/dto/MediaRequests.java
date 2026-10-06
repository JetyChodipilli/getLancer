package com.getlancer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Validated media JSON commands; semantic and ownership checks stay in services. */
public final class MediaRequests {
  private MediaRequests() {}

  public record Upload(
      @NotBlank @Size(min = 1, max = 255) String filename,
      @NotBlank @Pattern(regexp = "image/png|image/jpeg") String contentType,
      @NotNull @Min(1) @Max(5242880) Long sizeBytes) {}

  public record Complete(
      @NotNull UUID uploadId,
      @NotBlank @Size(min = 1, max = 300) String altText) {}

  public record Order(
      @NotNull @Size(max = 6) List<@NotNull @Valid Image> items) {}

  public record Image(
      @NotNull UUID id,
      @NotBlank @Size(min = 1, max = 300) String alt) {}
}
