package com.getlancer.labs;

import com.getlancer.dto.LabRequests;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("@authorization.routeAllowed(authentication)")
public class LabController {
  private final LabService service;
  public LabController(LabService service){this.service=service;}
  @GetMapping("/lab-manifests") public LabResponses.Catalogue catalogue(){return service.catalogue();}
  @GetMapping("/lab-quota") public LabResponses.Quota quota(HttpServletRequest request){return service.quota(request);}
  @PostMapping("/lab-runs") @ResponseStatus(HttpStatus.ACCEPTED)
  public LabResponses.Run start(@Valid @RequestBody LabRequests.Start input,@RequestHeader("Idempotency-Key") String key,HttpServletRequest request){return service.start(input,key,request);}
  @GetMapping("/lab-runs/{id}") public LabResponses.Run detail(@PathVariable UUID id,HttpServletRequest request){return service.detail(id,request);}
  @PostMapping("/lab-runs/{id}/stop") public LabResponses.Run stop(@PathVariable UUID id,HttpServletRequest request){return service.stop(id,request);}
  @PostMapping("/lab-runs/{id}/requests") public LabResponses.RequestResult request(@PathVariable UUID id,@Valid @RequestBody LabRequests.Request input,@RequestHeader("Idempotency-Key") String key,HttpServletRequest request){return service.request(id,input,key,request);}
  // Finite replay is deliberately bounded. No view fragments, asynchronous emitter or bearer URL exists.
  @GetMapping("/lab-runs/{id}/events") public ResponseEntity<String> events(@PathVariable UUID id,@RequestHeader(value="Last-Event-ID",required=false) String cursor,HttpServletRequest request){return ResponseEntity.ok().header("Content-Type","text/event-stream; charset=UTF-8").header("Cache-Control","private, no-store").header("X-Accel-Buffering","no").body(service.events(id,cursor,request));}
  @GetMapping("/admin/labs") public LabResponses.Admin admin(HttpServletRequest request){return service.admin(request);}
  @PostMapping("/admin/labs/pause-admissions") public LabResponses.Admin pause(@Valid @RequestBody LabRequests.Pause input,HttpServletRequest request){return service.pause(input,request);}
}
