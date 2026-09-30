package com.getlancer.products;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PrivateProjectsController {
  private final PrivateProjectService service;

  public PrivateProjectsController(PrivateProjectService service) {
    this.service = service;
  }

  @GetMapping("/developer/products/{id}/access")
  public Map<String, Object> list(@PathVariable UUID id, HttpServletRequest r) {
    return service.list(id, r);
  }

  @PostMapping("/developer/products/{id}/access")
  public Map<String, Object> grant(
      @PathVariable UUID id, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    return service.grant(id, b, r);
  }

  @DeleteMapping("/developer/products/{id}/access")
  public Map<String, Object> revoke(
      @PathVariable UUID id, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    return service.revoke(id, b, r);
  }

  @GetMapping("/private/products/{id}")
  public Map<String, Object> preview(@PathVariable UUID id, HttpServletRequest r) {
    return service.preview(id, r);
  }
}
