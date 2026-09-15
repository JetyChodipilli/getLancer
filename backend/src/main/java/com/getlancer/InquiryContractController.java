package com.getlancer;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import static com.getlancer.Support.*;

/** Documented endpoints share the existing authorization and state machines. */
@RestController @RequestMapping("/api/v1/inquiries") class InquiryContractController {
 final ClientController clients;final AuthController auth;final InquiriesController inquiries;
 InquiryContractController(ClientController clients,AuthController auth,InquiriesController inquiries){this.clients=clients;this.auth=auth;this.inquiries=inquiries;}
 @PostMapping({"/{id}/confirm-hire","/{id}/confirm-completion"}) @Transactional
 Map<String,Object> confirm(@PathVariable UUID id,@RequestBody(required=false) Map<String,Object> raw,HttpServletRequest request){
  Map<String,Object> body=raw==null?new HashMap<>():new HashMap<>(raw);
  String kind=request.getRequestURI().endsWith("confirm-hire")?"HIRE_CONFIRMATION":"COMPLETION_CONFIRMATION";
  body.put("kind",kind);body.putIfAbsent("decision","ACCEPT");
  if(body.containsKey("token")){var token=auth.confirmationToken(text(body,"token",20,200),false);if(!id.equals(token.get("inquiry_id"))||!kind.equals(token.get("kind")))throw new ApiError(400,"INVALID_TOKEN","This link is for a different action.");return auth.confirm(body,request);}
  return clients.decision(id,body,request);
 }
 @PostMapping("/{id}/not-hired") @Transactional
 Map<String,Object> close(@PathVariable UUID id,HttpServletRequest request){var user=clients.client(request);if(clients.db.queryForObject("SELECT count(*) FROM inquiries WHERE id=? AND client_email=?",Integer.class,id,user.get("email"))>0)return clients.close(id,request);return inquiries.transition(id,"not-hired",Map.of(),request);}
 @PostMapping("/{id}/review") @ResponseStatus(HttpStatus.CREATED) @Transactional
 Map<String,Object> review(@PathVariable UUID id,@RequestBody Map<String,Object> body,HttpServletRequest request){return clients.review(id,body,request);}
}
