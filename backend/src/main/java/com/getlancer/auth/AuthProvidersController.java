package com.getlancer.auth;

import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

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
