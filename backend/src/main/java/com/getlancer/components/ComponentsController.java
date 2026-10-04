package com.getlancer.components;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;
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
  @GetMapping("/me/components") public Object own(HttpServletRequest r){
    return service.own(r);
  }
  @PostMapping("/me/components") public Object create(@RequestBody Map<String,Object>b,HttpServletRequest r){
    return service.create(b,r);
  }
  @PatchMapping("/me/components/{id}") public Object edit(@PathVariable UUID id,@RequestBody Map<String,Object>b,HttpServletRequest r){
    return service.edit(id,b,r);
  }
  @PostMapping("/me/components/{id}/{action}") public Object action(@PathVariable UUID id,@PathVariable String action,HttpServletRequest r){
    return service.action(id,action,r);
  }
  @GetMapping("/admin/components/{id}") public Object adminDetail(@PathVariable UUID id,HttpServletRequest r){return service.adminDetail(id,r);}
  @GetMapping("/admin/components/review") public Object queue(HttpServletRequest r){
    return service.queue(r);
  }
  @PostMapping("/admin/components/{id}/review") public Object review(@PathVariable UUID id,@RequestBody Map<String,Object>b,HttpServletRequest r){
    return service.review(id,b,r);
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
  @PutMapping("/me/college-projects/{id}") public Object saveCollege(@PathVariable UUID id,@RequestBody Map<String,Object>b,HttpServletRequest r){
    return service.saveCollege(id,b,r);
  }
  @PostMapping("/admin/college-projects/{id}/review") public Object reviewCollege(@PathVariable UUID id,@RequestBody Map<String,Object>b,HttpServletRequest r){
    return service.reviewCollege(id,b,r);
  }
}
