package com.getlancer.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Concrete bounded draft fields; incomplete drafts are checked for completeness at submission. */
public final class EducationRequests {
  private EducationRequests() {}
  public record PackageDetails(
      @Size(max=30) List<@Size(max=2000) String> includedAssets,
      @Size(max=30) List<@Size(max=2000) String> excludedAssets,
      @Size(max=30) List<@Size(max=2000) String> setupSteps,
      @Size(max=30) List<@Size(max=2000) String> prerequisites,
      @Size(max=30) List<@Size(max=2000) String> limitations,
      @Size(max=4000) String supportTerms,@Size(max=8000) String licenseTerms) {}
  public record CategoryEvidence(
      @Size(max=4000) String modules,@Size(max=4000) String schemaApi,
      @Size(max=4000) String demoAccounts,@Size(max=4000) String setupMigrations,
      @Size(max=4000) String versions,@Size(max=4000) String tests,@Size(max=4000) String deployment,
      @Size(max=4000) String dataSourceLicense,@Size(max=4000) String sampleSchema,
      @Size(max=4000) String transformations,@Size(max=4000) String notebook,
      @Size(max=4000) String charts,@Size(max=4000) String interpretation,@Size(max=4000) String reproducibility,
      @Size(max=4000) String task,@Size(max=4000) String datasetModelLicense,@Size(max=4000) String splits,
      @Size(max=4000) String evaluation,@Size(max=4000) String metrics,@Size(max=4000) String inference,
      @Size(max=4000) String limitations,@Size(max=4000) String boardFirmware,@Size(max=4000) String billOfMaterials,
      @Size(max=4000) String wiring,@Size(max=4000) String powerConnectivity,@Size(max=4000) String topics,
      @Size(max=4000) String evidence) {}
  public record ComponentLink(@NotNull UUID componentId,
      @NotNull @JsonDeserialize(using=WholeNumber.class) @Min(1) Long revision,
      @NotBlank @Size(max=200) String license,@NotBlank @Size(min=3,max=1000) String attribution) {}
  public record Draft(@JsonDeserialize(using=WholeNumber.class) @Min(1) Long revision,
      @Pattern(regexp="|FULL_STACK|DATA_ANALYTICS|AI_ML|IOT") String category,
      @NotBlank @Pattern(regexp="SHOWCASE|FREE|PAID") String mode,
      @Pattern(regexp="|BEGINNER|INTERMEDIATE|ADVANCED") String difficulty,
      @Pattern(regexp="|SOURCE_ONLY|EXTERNAL|HOSTED") String demoMode,
      @Size(max=1000) String demoUrl,@JsonProperty("package") @Valid PackageDetails packageDetails,
      @Valid CategoryEvidence categoryEvidence,@Size(max=20) List<@Valid ComponentLink> componentLinks,
      UUID versionId,Boolean rightsConsent) {}
  public record Submit(@NotNull @JsonDeserialize(using=WholeNumber.class) @Min(1) Long revision,
      Boolean rightsConsent) {}
  public record Review(@NotNull @JsonDeserialize(using=WholeNumber.class) @Min(1) Long revision,
      @NotBlank @Pattern(regexp="[a-f0-9]{64}") String sourceHash,
      @NotBlank @Pattern(regexp="APPROVE|CHANGES_REQUESTED|SUSPEND") String decision,
      @NotBlank @Size(min=20,max=2000) String reason,Boolean rightsReviewed,Boolean packageReviewed) {}
  public record Academic(@NotNull @JsonDeserialize(using=WholeNumber.class) @Min(1) Long revision,
      @Size(max=160) String institution,@Size(max=40) String academicYear,
      @Size(max=100) String branch,Boolean shareAcademicDetails) {}
}
