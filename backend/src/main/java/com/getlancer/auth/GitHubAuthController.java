package com.getlancer.auth;

import com.getlancer.dto.OAuthRequests;
import com.getlancer.shared.TypedInputs;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@PreAuthorize("@authorization.routeAllowed(authentication)")
@RestController
@RequestMapping("/api/v1/auth")
public class GitHubAuthController {
  private final GitHubAuthService service;

  public GitHubAuthController(GitHubAuthService service) {
    this.service = service;
  }

  @PostMapping("/github/start")
  public Map<String, Object> start(
      @Valid @RequestBody OAuthRequests.Start body, HttpServletResponse response) throws Exception {
    return service.start(TypedInputs.map(body), response);
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
