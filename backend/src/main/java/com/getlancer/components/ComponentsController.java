package com.getlancer.components;

import com.getlancer.dto.ComponentRequests;
import com.getlancer.shared.TypedInputs;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@PreAuthorize("@authorization.routeAllowed(authentication)")
@RestController @RequestMapping("/api/v1")
public class ComponentsController {
  final ComponentService service;
  public ComponentsController(ComponentService service){
    this.service=service;
  }
  @GetMapping("/components") public Object catalog(@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="")String category,@RequestParam(defaultValue="FRONTEND")String kind,@RequestParam(defaultValue="")String builder,@RequestParam(defaultValue="0")int page){
    return service.catalog(q,category,kind,builder,page);
  }
  @GetMapping("/components/{slug}") public Object detail(@PathVariable String slug){
    return service.detail(slug);
  }
  @GetMapping("/components/{slug}/versions") public Object history(@PathVariable String slug){return service.history(slug);}
  @GetMapping("/me/components") public Object own(HttpServletRequest r){
    return service.own(r);
  }
  @PostMapping("/me/components") public Object create(@Valid @RequestBody ComponentRequests.Mutation b,HttpServletRequest r){
    return service.create(TypedInputs.map(b),r);
  }
  @PatchMapping("/me/components/{id}") public Object edit(@PathVariable UUID id,@Valid @RequestBody ComponentRequests.Mutation b,HttpServletRequest r){
    return service.edit(id,TypedInputs.map(b),r);
  }
  @PostMapping("/me/components/{id}/{action}") public Object action(@PathVariable UUID id,@PathVariable String action,HttpServletRequest r){
    return service.action(id,action,r);
  }
  @GetMapping("/admin/components/{id}") public Object adminDetail(@PathVariable UUID id,HttpServletRequest r){return service.adminDetail(id,r);}
  @GetMapping("/admin/components/review") public Object queue(HttpServletRequest r){
    return service.queue(r);
  }
  @PostMapping("/admin/components/{id}/review") public Object review(@PathVariable UUID id,@Valid @RequestBody ComponentRequests.Review b,HttpServletRequest r){
    return service.review(id,TypedInputs.map(b),r);
  }
  @GetMapping("/college-projects") public Object college(@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="")String category,@RequestParam(defaultValue="")String language,@RequestParam(defaultValue="")String builder,@RequestParam(defaultValue="0")int page){
    return service.college(q,category,language,builder,page);
  }
  @GetMapping("/college-projects/{slug}") public Object collegeDetail(@PathVariable String slug){
    return service.collegeDetail(slug);
  }
  @GetMapping("/me/college-projects") public Object ownCollege(HttpServletRequest r){
    return service.ownCollege(r);
  }
  @PutMapping("/me/college-projects/{id}") public Object saveCollege(@PathVariable UUID id,@Valid @RequestBody ComponentRequests.College b,HttpServletRequest r){
    return service.saveCollege(id,TypedInputs.map(b),r);
  }
  @PostMapping("/admin/college-projects/{id}/review") public Object reviewCollege(@PathVariable UUID id,@Valid @RequestBody ComponentRequests.CollegeReview b,HttpServletRequest r){
    return service.reviewCollege(id,TypedInputs.map(b),r);
  }
}
