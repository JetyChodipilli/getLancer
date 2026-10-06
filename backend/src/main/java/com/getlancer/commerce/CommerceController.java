package com.getlancer.commerce;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class CommerceController {
  private final CommerceService sources;
  public CommerceController(CommerceService sources) {this.sources=sources;}
  @GetMapping("/templates") public Map<String,Object> catalog(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String category,@RequestParam(defaultValue="") String technology,HttpServletRequest r) {return sources.catalog(r,q,category,technology);}
  @GetMapping("/templates/{slug}") public Map<String,Object> detail(@PathVariable String slug) {return sources.detail(slug);}
  @GetMapping("/me/templates") public Map<String,Object> own(HttpServletRequest r) {return sources.own(r);}
  @PostMapping("/me/templates") @ResponseStatus(HttpStatus.CREATED) public Map<String,Object> create(@RequestBody Map<String,Object> b,HttpServletRequest r) {return sources.create(b,r);}
  @PostMapping(value="/me/templates/{id}/versions",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED) public Map<String,Object> upload(@PathVariable UUID id,@RequestParam MultipartFile file,@RequestParam String version,@RequestParam String releaseNotes,@RequestParam boolean rightsConsent,HttpServletRequest r) throws IOException {return sources.upload(id,file,version,releaseNotes,rightsConsent,r);}
  @PostMapping("/me/templates/{id}/versions/{version}/submit") public Map<String,Object> submit(@PathVariable UUID id,@PathVariable UUID version,@RequestBody Map<String,Object> b,HttpServletRequest r) {return sources.submit(id,version,b,r);}
  @PostMapping("/me/templates/{id}/activate") public Map<String,Object> activate(@PathVariable UUID id,HttpServletRequest r) {return sources.activate(id,r);}
  @PostMapping("/me/templates/{id}/archive") public Map<String,Object> archive(@PathVariable UUID id,HttpServletRequest r) {return sources.archive(id,r);}
  @GetMapping("/me/templates/{id}/versions/{version}/package") public ResponseEntity<byte[]> ownerPackage(@PathVariable UUID id,@PathVariable UUID version,HttpServletRequest r) {return sources.packageDownload(id,version,r,false);}
  @GetMapping("/admin/templates") public Map<String,Object> admin(HttpServletRequest r) {return sources.admin(r);}
  @PostMapping("/admin/templates/{id}/versions/{version}/review") public Map<String,Object> review(@PathVariable UUID id,@PathVariable UUID version,@RequestBody Map<String,Object> b,HttpServletRequest r) {return sources.review(id,version,b,r);}
  @GetMapping("/admin/templates/{id}/versions/{version}/package") public ResponseEntity<byte[]> adminPackage(@PathVariable UUID id,@PathVariable UUID version,HttpServletRequest r) {return sources.packageDownload(id,version,r,true);}
}
