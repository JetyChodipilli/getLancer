package com.getlancer.education;

import com.getlancer.dto.EducationRequests;
import com.getlancer.shared.TypedInputs;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@PreAuthorize("@authorization.routeAllowed(authentication)")
@RestController
@RequestMapping("/api/v1")
public class EducationController {
  private final EducationService service;
  public EducationController(EducationService service){this.service=service;}
  @GetMapping("/college-projects")
  public Object college(@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="")String category,@RequestParam(defaultValue="")String language,@RequestParam(defaultValue="")String builder,@RequestParam(defaultValue="")String mode,@RequestParam(defaultValue="")String difficulty,@RequestParam(defaultValue="0")int page){return service.college(q,category,language,builder,mode,difficulty,page);}
  @GetMapping("/college-projects/{slug}")
  public Object detail(@PathVariable String slug){return service.detail(slug);}
  @GetMapping("/me/education-releases")
  public Object own(HttpServletRequest request){return service.own(request);}
  @GetMapping("/me/education-releases/{id}")
  public Object ownDetail(@PathVariable UUID id,HttpServletRequest request){return service.ownDetail(id,request);}
  @PostMapping("/me/college-projects/{productId}/releases")
  public Object create(@PathVariable UUID productId,@Valid @RequestBody EducationRequests.Draft body,HttpServletRequest request){return service.create(productId,TypedInputs.map(body),request);}
  @PatchMapping("/me/education-releases/{id}")
  public Object save(@PathVariable UUID id,@Valid @RequestBody EducationRequests.Draft body,HttpServletRequest request){return service.save(id,TypedInputs.map(body),request);}
  @PostMapping(value="/me/education-releases/{id}/source",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  public Object upload(@PathVariable UUID id,@RequestPart("file")MultipartFile file,@RequestParam long revision,@RequestParam boolean rightsConsent,HttpServletRequest request)throws IOException{return service.upload(id,file,revision,rightsConsent,request);}
  @PostMapping("/me/education-releases/{id}/submit")
  public Object submit(@PathVariable UUID id,@Valid @RequestBody EducationRequests.Submit body,HttpServletRequest request){return service.submit(id,TypedInputs.map(body),request);}
  @PatchMapping("/me/education-releases/{id}/academic")
  public Object academic(@PathVariable UUID id,@Valid @RequestBody EducationRequests.Academic body,HttpServletRequest request){return service.academic(id,TypedInputs.map(body),request);}
  @GetMapping("/admin/education-releases")
  public Object queue(HttpServletRequest request){return service.queue(request);}
  @GetMapping("/admin/education-releases/{id}")
  public Object adminDetail(@PathVariable UUID id,HttpServletRequest request){return service.adminDetail(id,request);}
  @PostMapping("/admin/education-releases/{id}/package")
  public ResponseEntity<byte[]> inspect(@PathVariable UUID id,HttpServletRequest request){return service.source(id,request,true);}
  @PostMapping("/admin/education-releases/{id}/review")
  public Object review(@PathVariable UUID id,@Valid @RequestBody EducationRequests.Review body,HttpServletRequest request){return service.review(id,TypedInputs.map(body),request);}
  @GetMapping("/education-releases/{id}/source-offer")
  public Object sourceOffer(@PathVariable UUID id){return service.sourceOffer(id);}
  @GetMapping("/education-releases/{id}/source")
  public ResponseEntity<byte[]> source(@PathVariable UUID id,HttpServletRequest request){return service.source(id,request,false);}
}
