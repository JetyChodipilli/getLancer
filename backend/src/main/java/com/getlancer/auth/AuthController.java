package com.getlancer.auth;

import jakarta.servlet.http.*;
import java.time.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
  private final AuthService service;

  public AuthController(AuthService service) {
    this.service = service;
  }

  @PostMapping("/auth/signup")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Map<String, Object> signup(@RequestBody Map<String, Object> b) {
    return service.signup(b);
  }

  @PostMapping("/auth/login")
  public Map<String, Object> login(@RequestBody Map<String, Object> b, HttpServletResponse res) {
    return service.login(b, res);
  }

  @PostMapping("/auth/login/mfa")
  public Map<String, Object> mfa(
      @RequestBody Map<String, Object> body, HttpServletRequest req, HttpServletResponse res) {
    return service.mfa(body, req, res);
  }

  @PostMapping("/auth/logout")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public Map<String, Object> logout(HttpServletRequest req, HttpServletResponse res) {
    return service.logout(req, res);
  }

  @GetMapping("/me")
  public Map<String, Object> me(HttpServletRequest r) {
    return service.me(r);
  }

  @PostMapping("/auth/password-reset/request")
  public Map<String, Object> reset(@RequestBody Map<String, Object> b) {
    return service.reset(b);
  }

  @PostMapping("/auth/confirmation")
  public Map<String, Object> confirmationContext(@RequestBody Map<String, Object> body) {
    return service.confirmationContext(body);
  }

  @PostMapping({
    "/auth/confirm",
    "/auth/verify-email",
    "/auth/password-reset/confirm",
    "/inquiries/confirm-email"
  })
  public Map<String, Object> confirm(
      @RequestBody Map<String, Object> body, HttpServletRequest request) {
    return service.confirm(body, request);
  }

  @PostMapping("/auth/resend-verification")
  public Map<String, Object> resend(HttpServletRequest request) {
    return service.resend(request);
  }

  @DeleteMapping("/me")
  @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
  public Map<String, Object> deletion(HttpServletRequest r) {
    return service.deletion(r);
  }
}
