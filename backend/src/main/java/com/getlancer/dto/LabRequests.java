package com.getlancer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

/** Browser commands contain bounded scenario data, never process, path or network instructions. */
public final class LabRequests {
  private LabRequests() {}
  public record Start(@NotBlank @Pattern(regexp="[a-z0-9][a-z0-9-]{0,79}") String manifestId,
      @NotBlank @Pattern(regexp="[a-z0-9][a-z0-9-]{0,59}") String scenarioId,
      @NotNull @Size(max=20) Map<@Pattern(regexp="[a-z][a-zA-Z0-9]{0,39}") String,@Size(max=4096) String> inputs) {}
  public record Request(@NotBlank @Pattern(regexp="[a-z0-9][a-z0-9-]{0,59}") String operationId,
      @NotNull @Size(max=20) Map<@Pattern(regexp="[a-z][a-zA-Z0-9]{0,39}") String,@Size(max=4096) String> inputs) {}
  public record Pause(@NotNull Boolean paused,@NotBlank @Size(min=10,max=500) String reason) {}
}
