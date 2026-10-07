package com.getlancer.profiles;

import com.getlancer.dto.ProfileRequests;
import com.getlancer.shared.TypedInputs;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@PreAuthorize("@authorization.routeAllowed(authentication)")
@RestController
@RequestMapping("/api/v1")
public class ProfileController {
  private final ProfileService service;

  public ProfileController(ProfileService service) {
    this.service = service;
  }

  @PutMapping("/developer/profile")
  public Object profile(@Valid @RequestBody ProfileRequests.Profile b, HttpServletRequest r) {
    return service.profile(TypedInputs.map(b), r);
  }

  @PostMapping("/developer/profile/submit")
  public Object submitProfile(HttpServletRequest r) {
    return service.submitProfile(r);
  }
}
