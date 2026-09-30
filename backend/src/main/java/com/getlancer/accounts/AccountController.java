package com.getlancer.accounts;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AccountController {
  private final AccountService service;

  public AccountController(AccountService service) {
    this.service = service;
  }

  @GetMapping("/me/export")
  public Map<String, Object> export(HttpServletRequest request) {
    return service.export(request);
  }

  @PatchMapping("/notifications/{id}/read")
  public Map<String, Object> read(@PathVariable UUID id, HttpServletRequest request) {
    return service.read(id, request);
  }
}
