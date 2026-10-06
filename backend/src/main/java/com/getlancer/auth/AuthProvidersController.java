package com.getlancer.auth;

import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@PreAuthorize("@authorization.routeAllowed(authentication)")
@RestController
public class AuthProvidersController {
  private final AuthProviderService service;

  public AuthProvidersController(AuthProviderService service) {
    this.service = service;
  }

  @GetMapping("/api/v1/auth/providers")
  public Map<String, Object> providers() {
    return service.providers();
  }
}
