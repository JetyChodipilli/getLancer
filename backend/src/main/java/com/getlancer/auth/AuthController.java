package com.getlancer.auth;

import com.getlancer.responses.UserResponse;
import com.getlancer.shared.TypedInputs;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("@authorization.routeAllowed(authentication)")
public class AuthController {
  private final AuthService service;

  public AuthController(AuthService service) { this.service = service; }

  @PostMapping("/auth/signup")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> signup(@Valid @RequestBody AuthRequests.Signup body) {
    return service.signup(TypedInputs.map(body));
  }

  @PostMapping("/auth/login")
  public Map<String, Object> login(@Valid @RequestBody AuthRequests.Login body,
      HttpServletRequest req, HttpServletResponse res) {
    return service.login(TypedInputs.map(body), req, res);
  }

  @PostMapping("/auth/login/mfa")
  public Map<String, Object> mfa(@Valid @RequestBody AuthRequests.Mfa body,
      HttpServletRequest req, HttpServletResponse res) {
    return service.mfa(TypedInputs.map(body), req, res);
  }

  @PostMapping("/auth/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public Map<String, Object> logout(HttpServletRequest req, HttpServletResponse res) {
    return service.logout(req, res);
  }

  @GetMapping("/me")
  public UserResponse me(HttpServletRequest req) { return service.me(req); }

  @PostMapping("/auth/password-reset/request")
  public Map<String, Object> reset(@Valid @RequestBody AuthRequests.Reset body) {
    return service.reset(TypedInputs.map(body));
  }

  @PostMapping("/auth/confirmation")
  public Map<String, Object> confirmationContext(@Valid @RequestBody AuthRequests.Token body) {
    return service.confirmationContext(TypedInputs.map(body));
  }

  @PostMapping({"/auth/confirm", "/auth/verify-email", "/inquiries/confirm-email"})
  public Map<String, Object> confirm(@Valid @RequestBody AuthRequests.Confirmation body,
      HttpServletRequest request) {
    return service.confirm(TypedInputs.map(body), request);
  }

  @PostMapping("/auth/password-reset/confirm")
  public Map<String, Object> confirmPasswordReset(
      @Valid @RequestBody AuthRequests.PasswordResetConfirmation body, HttpServletRequest request) {
    return service.confirm(TypedInputs.map(body), request);
  }

  @PostMapping("/auth/resend-verification")
  public Map<String, Object> resend(HttpServletRequest request) { return service.resend(request); }

  @DeleteMapping("/me")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Map<String, Object> deletion(HttpServletRequest request) { return service.deletion(request); }
}
