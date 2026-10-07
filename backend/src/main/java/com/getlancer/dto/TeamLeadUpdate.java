package com.getlancer.dto;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Partial lead update: omitted assignments stay unchanged; explicit null clears them. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class TeamLeadUpdate {
  @Pattern(regexp = "NEW|INTERESTED|NEEDS_INFORMATION|DECLINED|PROPOSAL_SENT|WON|LOST")
  private String status;

  private UUID assigneeId;

  @Future private Instant followUpAt;

  @Size(max = 3000) private String note;

  private boolean assigneeSupplied;
  private boolean followUpSupplied;

  @JsonSetter("status")
  public void setStatus(String value) { status = value; }

  @JsonSetter("assigneeId")
  public void setAssigneeId(UUID value) { assigneeId = value; assigneeSupplied = true; }

  @JsonSetter("followUpAt")
  public void setFollowUpAt(Instant value) { followUpAt = value; followUpSupplied = true; }

  @JsonSetter("note")
  public void setNote(String value) { note = value; }

  @JsonGetter("status")
  public String status() { return status; }

  @JsonGetter("assigneeId")
  public String assigneeId() {
    return !assigneeSupplied ? null : assigneeId == null ? "" : assigneeId.toString();
  }

  @JsonGetter("followUpAt")
  public String followUpAt() {
    return !followUpSupplied ? null : followUpAt == null ? "" : followUpAt.toString();
  }

  @JsonGetter("note")
  public String note() { return note; }
}
