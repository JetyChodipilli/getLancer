package com.getlancer.auth;

import jakarta.servlet.http.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class GoogleAuthController {
  private final GoogleAuthService service;

  public GoogleAuthController(GoogleAuthService service) {
    this.service = service;
  }

  @PostMapping("/google/start")
  public Map<String, Object> start(
      @RequestBody Map<String, Object> body, HttpServletResponse response) throws Exception {
    return service.start(body, response);
  }

  @GetMapping("/google/callback")
  public ResponseEntity<Void> callback(
      @RequestParam(defaultValue = "") String state,
      @RequestParam(defaultValue = "") String code,
      @RequestParam(defaultValue = "") String error,
      HttpServletRequest req,
      HttpServletResponse res) {
    return service.callback(state, code, error, req, res);
  }
}
