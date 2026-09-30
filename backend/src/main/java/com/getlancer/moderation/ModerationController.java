package com.getlancer.moderation;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ModerationController {
  private final ModerationService service;

  public ModerationController(ModerationService service) {
    this.service = service;
  }

  @PostMapping("/admin/reports/{id}/triage")
  public Map<String, Object> triage(
      @PathVariable UUID id, @RequestBody Map<String, Object> body, HttpServletRequest r) {
    return service.triage(id, body, r);
  }

  @GetMapping("/me/moderation-decisions")
  public Map<String, Object> decisions(HttpServletRequest r) {
    return service.decisions(r);
  }

  @PostMapping("/appeals")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Map<String, Object> appeal(@RequestBody Map<String, Object> body, HttpServletRequest r) {
    return service.appeal(body, r);
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
      @PathVariable UUID id, @RequestBody Map<String, Object> body, HttpServletRequest r) {
    return service.appealDecision(id, body, r);
  }

  @GetMapping("/admin/email-jobs")
  public Map<String, Object> failedMail(HttpServletRequest r) {
    return service.failedMail(r);
  }

  @PostMapping("/admin/email-jobs/{id}/retry")
  public Map<String, Object> retry(
      @PathVariable UUID id, @RequestBody Map<String, Object> body, HttpServletRequest r) {
    return service.retry(id, body, r);
  }
}
