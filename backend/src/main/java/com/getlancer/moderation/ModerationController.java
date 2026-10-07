package com.getlancer.moderation;

import com.getlancer.dto.ModerationRequests;
import com.getlancer.shared.TypedInputs;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@PreAuthorize("@authorization.routeAllowed(authentication)")
@RestController
@RequestMapping("/api/v1")
public class ModerationController {
  private final ModerationService service;

  public ModerationController(ModerationService service) {
    this.service = service;
  }

  @PostMapping("/admin/reports/{id}/triage")
  public Map<String, Object> triage(
      @PathVariable UUID id, @Valid @RequestBody ModerationRequests.Triage body, HttpServletRequest r) {
    return service.triage(id, TypedInputs.map(body), r);
  }

  @GetMapping("/me/moderation-decisions")
  public Map<String, Object> decisions(HttpServletRequest r) {
    return service.decisions(r);
  }

  @PostMapping("/appeals")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> appeal(@Valid @RequestBody ModerationRequests.Appeal body, HttpServletRequest r) {
    return service.appeal(TypedInputs.map(body), r);
  }

  @GetMapping("/me/appeals")
  public Map<String, Object> ownAppeals(HttpServletRequest r) {
    return service.ownAppeals(r);
  }

  @GetMapping("/admin/appeals")
  public Map<String, Object> appeals(HttpServletRequest r) {
    return service.appeals(r);
  }

  @PostMapping("/admin/appeals/{id}/decision")
  public Map<String, Object> appealDecision(
      @PathVariable UUID id, @Valid @RequestBody ModerationRequests.Decision body, HttpServletRequest r) {
    return service.appealDecision(id, TypedInputs.map(body), r);
  }

  @GetMapping("/admin/email-jobs")
  public Map<String, Object> failedMail(HttpServletRequest r) {
    return service.failedMail(r);
  }

  @PostMapping("/admin/email-jobs/{id}/retry")
  public Map<String, Object> retry(
      @PathVariable UUID id, @Valid @RequestBody ModerationRequests.Retry body, HttpServletRequest r) {
    return service.retry(id, TypedInputs.map(body), r);
  }
}
