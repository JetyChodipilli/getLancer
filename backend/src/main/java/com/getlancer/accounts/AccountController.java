package com.getlancer.accounts;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@PreAuthorize("@authorization.routeAllowed(authentication)")
@RestController
@RequestMapping("/api/v1")
public class AccountController {
  private final AccountService service;

  public AccountController(AccountService service) {
    this.service = service;
  }

  @GetMapping("/me/export")
  public Object export(HttpServletRequest request) {
    return service.export(request);
  }

  @PatchMapping("/notifications/{id}/read")
  public Map<String, Object> read(@PathVariable UUID id, HttpServletRequest request) {
    return service.read(id, request);
  }
}
