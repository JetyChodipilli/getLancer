package com.getlancer.hosting;

import com.getlancer.dto.HostingRequests;
import com.getlancer.shared.TypedInputs;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@PreAuthorize("@authorization.routeAllowed(authentication)")
@RestController
@RequestMapping("/api/v1")
public class HostingController {
  private final HostingService service;
  public HostingController(HostingService service){this.service=service;}
  @GetMapping("/hosting/config") public Map<String,Object> configuration(){return service.configuration();}
  @GetMapping("/me/hosting/sources") public Map<String,Object> sources(HttpServletRequest r){return service.sources(r);}
  @GetMapping("/me/hosting") public Map<String,Object> own(HttpServletRequest r){return service.list(r,false);}
  @GetMapping("/me/hosting/{id}") public Map<String,Object> detail(@PathVariable UUID id,HttpServletRequest r){return service.detail(id,r);}
  @PostMapping(value="/me/hosting",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
  public Map<String,Object> create(@RequestPart("file") MultipartFile file,@RequestParam UUID productId,@RequestParam String title,@RequestParam String version,@RequestParam boolean rightsConsent,HttpServletRequest r)throws IOException{return service.create(file,productId,title,version,rightsConsent,r);}
  @PostMapping("/me/hosting/{id}/submit") public Map<String,Object> submit(@PathVariable UUID id,@Valid @RequestBody HostingRequests.Submit b,HttpServletRequest r){return service.submit(id,TypedInputs.map(b),r);}
  @PostMapping("/me/hosting/{id}/deploy") public Map<String,Object> deploy(@PathVariable UUID id,HttpServletRequest r){return service.deploy(id,r);}
  @PostMapping("/me/hosting/{id}/withdraw") public Map<String,Object> withdraw(@PathVariable UUID id,HttpServletRequest r){return service.withdraw(id,Map.of(),r,false);}
  @PostMapping("/me/hosting/{id}/reconcile") public Map<String,Object> reconcile(@PathVariable UUID id,HttpServletRequest r){return service.reconcile(id,Map.of(),r,false);}
  @GetMapping("/me/hosting/{id}/package") public ResponseEntity<byte[]> ownPackage(@PathVariable UUID id,HttpServletRequest r){return service.packageDownload(id,r,false);}
  @GetMapping("/admin/hosting") public Map<String,Object> admin(HttpServletRequest r){return service.list(r,true);}
  @GetMapping("/admin/hosting/{id}/package") public ResponseEntity<byte[]> adminPackage(@PathVariable UUID id,HttpServletRequest r){return service.packageDownload(id,r,true);}
  @PostMapping("/admin/hosting/{id}/review") public Map<String,Object> review(@PathVariable UUID id,@Valid @RequestBody HostingRequests.Review b,HttpServletRequest r){return service.review(id,TypedInputs.map(b),r);}
  @PostMapping("/admin/hosting/{id}/reconcile") public Map<String,Object> adminReconcile(@PathVariable UUID id,@Valid @RequestBody HostingRequests.Reason b,HttpServletRequest r){return service.reconcile(id,TypedInputs.map(b),r,true);}
  @PostMapping("/admin/hosting/{id}/withdraw") public Map<String,Object> adminWithdraw(@PathVariable UUID id,@Valid @RequestBody HostingRequests.Reason b,HttpServletRequest r){return service.withdraw(id,TypedInputs.map(b),r,true);}
  @GetMapping("/hosting/products/{productId}") public Map<String,Object> publicProduct(@PathVariable UUID productId){return service.publicProduct(productId);}
  @GetMapping("/hosting/gateway/{deploymentId}") public Map<String,Object> gateway(@PathVariable UUID deploymentId,@RequestHeader(value="X-GetLancer-Demo-Gateway",required=false)String secret){return service.gateway(deploymentId,secret);}
}
