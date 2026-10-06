package com.getlancer.inquiries;

import com.getlancer.dto.InquiryRequests;
import com.getlancer.shared.TypedInputs;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@PreAuthorize("@authorization.routeAllowed(authentication)")
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
      @Valid @RequestBody(required = false) InquiryRequests.Confirmation raw,
      HttpServletRequest request) {
    return service.confirm(id, TypedInputs.map(raw), request);
  }

  @PostMapping("/{id}/not-hired")
  public Map<String, Object> close(@PathVariable UUID id, HttpServletRequest request) {
    return service.close(id, request);
  }

  @PostMapping("/{id}/review")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> review(
      @PathVariable UUID id, @Valid @RequestBody InquiryRequests.Review body, HttpServletRequest request) {
    return service.review(id, TypedInputs.map(body), request);
  }
}
