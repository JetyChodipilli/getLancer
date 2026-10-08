package com.getlancer.components;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@PreAuthorize("@authorization.routeAllowed(authentication)")
@RestController
@RequestMapping("/api/v1")
public class ComponentBookmarksController {
  private final ComponentBookmarkService service;
  public ComponentBookmarksController(ComponentBookmarkService service) { this.service=service; }
  @GetMapping("/me/saved-components") public Object list(HttpServletRequest request) { return service.list(request); }
  @PostMapping("/components/{slug}/save") public Object save(@PathVariable String slug,HttpServletRequest request) { return service.save(slug,request); }
  @DeleteMapping("/components/{slug}/save") public Object remove(@PathVariable String slug,HttpServletRequest request) { return service.remove(slug,request); }
}
