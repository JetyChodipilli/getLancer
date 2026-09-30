package com.getlancer.auth;

import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class GitHubAuthController {
  private final GitHubAuthService service;

  public GitHubAuthController(GitHubAuthService service) {
    this.service = service;
  }

  @PostMapping("/github/start")
  public Map<String, Object> start(
      @RequestBody Map<String, Object> body, HttpServletResponse response) throws Exception {
    return service.start(body, response);
  }

  @GetMapping("/github/callback")
  public ResponseEntity<Void> callback(
      @RequestParam(defaultValue = "") String state,
      @RequestParam(defaultValue = "") String code,
      @RequestParam(defaultValue = "") String error,
      HttpServletRequest request,
      HttpServletResponse response) {
    return service.callback(state, code, error, request, response);
  }
}
