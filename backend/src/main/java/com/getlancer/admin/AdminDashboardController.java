package com.getlancer.admin;

import com.getlancer.responses.AdminDashboardResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@PreAuthorize("@authorization.routeAllowed(authentication)")
@RestController
@RequestMapping("/api/v1/admin")
public class AdminDashboardController {
  private final AdminDashboard dashboard;

  public AdminDashboardController(AdminDashboard dashboard) {
    this.dashboard = dashboard;
  }

  @GetMapping("/dashboard")
  public AdminDashboardResponse dashboard(@RequestParam(defaultValue = "4") String weeks,
      HttpServletRequest request) {
    return dashboard.snapshot(weeks, request);
  }
}
