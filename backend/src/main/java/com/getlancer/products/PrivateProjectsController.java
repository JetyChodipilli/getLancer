package com.getlancer.products;

import com.getlancer.dto.ProductRequests;
import com.getlancer.shared.TypedInputs;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@PreAuthorize("@authorization.routeAllowed(authentication)")
@RestController
@RequestMapping("/api/v1")
public class PrivateProjectsController {
  private final PrivateProjectService service;

  public PrivateProjectsController(PrivateProjectService service) {
    this.service = service;
  }

  @GetMapping("/developer/products/{id}/access")
  public Object list(@PathVariable UUID id, HttpServletRequest r) {
    return service.list(id, r);
  }

  @PostMapping("/developer/products/{id}/access")
  public Object grant(
      @PathVariable UUID id, @Valid @RequestBody ProductRequests.Access b, HttpServletRequest r) {
    return service.grant(id, TypedInputs.map(b), r);
  }

  @DeleteMapping("/developer/products/{id}/access")
  public Object revoke(
      @PathVariable UUID id, @Valid @RequestBody ProductRequests.Access b, HttpServletRequest r) {
    return service.revoke(id, TypedInputs.map(b), r);
  }

  @GetMapping("/private/products/{id}")
  public Map<String, Object> preview(@PathVariable UUID id, HttpServletRequest r) {
    return service.preview(id, r);
  }
}
