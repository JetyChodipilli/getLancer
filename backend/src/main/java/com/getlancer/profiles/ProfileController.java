package com.getlancer.profiles;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ProfileController {
  private final ProfileService service;

  public ProfileController(ProfileService service) {
    this.service = service;
  }

  @PutMapping("/developer/profile")
  public Map<String, Object> profile(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    return service.profile(b, r);
  }

  @PostMapping("/developer/profile/submit")
  public Map<String, Object> submitProfile(HttpServletRequest r) {
    return service.submitProfile(r);
  }
}
