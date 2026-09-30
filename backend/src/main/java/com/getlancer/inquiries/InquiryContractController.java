package com.getlancer.inquiries;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inquiries")
public class InquiryContractController {
  private final InquiryContractService service;

  public InquiryContractController(InquiryContractService service) {
    this.service = service;
  }

  @PostMapping({"/{id}/confirm-hire", "/{id}/confirm-completion"})
  public Map<String, Object> confirm(
      @PathVariable UUID id,
      @RequestBody(required = false) Map<String, Object> raw,
      HttpServletRequest request) {
    return service.confirm(id, raw, request);
  }

  @PostMapping("/{id}/not-hired")
  public Map<String, Object> close(@PathVariable UUID id, HttpServletRequest request) {
    return service.close(id, request);
  }

  @PostMapping("/{id}/review")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> review(
      @PathVariable UUID id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
    return service.review(id, body, request);
  }
}
