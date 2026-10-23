package com.getlancer.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.List;
import java.util.TreeMap;

/** Browser commands contain bounded scenario data, never process, path or network instructions. */
public final class LabRequests {
  private LabRequests() {}
  public record Input(@NotBlank @Pattern(regexp="[a-z][a-zA-Z0-9]{0,39}") String name,
      @Size(max=4096) String value) {}
  /** Concrete validated fields retain the existing JSON object wire format. */
  public record Inputs(@NotNull @Size(max=20) List<@NotNull @Valid Input> fields) {
    public Inputs {if(fields!=null)fields=List.copyOf(fields);}
    @JsonCreator(mode=JsonCreator.Mode.DELEGATING)
    public static Inputs from(Map<String,String> values) {
      return values==null?null:new Inputs(values.entrySet().stream().map(entry->new Input(entry.getKey(),entry.getValue())).toList());
    }
    @JsonValue public Map<String,String> values() {
      var values=new TreeMap<String,String>();for(var field:fields)values.put(field.name(),field.value());
      return java.util.Collections.unmodifiableMap(values);
    }
  }
  public record Start(@NotBlank @Pattern(regexp="[a-z0-9][a-z0-9-]{0,79}") String manifestId,
      @NotBlank @Pattern(regexp="[a-z0-9][a-z0-9-]{0,59}") String scenarioId,
      @NotNull @Valid Inputs inputs) {}
  public record Request(@NotBlank @Pattern(regexp="[a-z0-9][a-z0-9-]{0,59}") String operationId,
      @NotNull @Valid Inputs inputs) {}
  public record Limits(@NotNull @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(5) Integer dailyRunLimit,
      @NotNull @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(10) Integer maxActiveRuns,
      @NotNull @jakarta.validation.constraints.Min(64) @jakarta.validation.constraints.Max(5120) Integer maxMemoryMiB,
      @NotNull @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(50000000) Long dailyBudgetMicros,
      @NotNull @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(1000000000) Long monthlyBudgetMicros,
      @NotBlank @Size(min=10,max=500) String reason) {}
  public record Certification(@NotBlank @Pattern(regexp="REGISTER_SOURCE|PUBLISH|REVOKE") String action,
      @NotBlank @Pattern(regexp="[a-z0-9][a-z0-9-]{0,79}") String manifestId,
      @Size(max=65536) String payload,@Size(max=128) String signature,@NotBlank @Size(min=10,max=500) String reason) {}
  public record Pause(@NotNull Boolean paused,@NotBlank @Size(min=10,max=500) String reason) {}
}
