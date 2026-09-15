package com.getlancer;

import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static com.getlancer.Support.*;

@RestController @RequestMapping("/api/v1/me/inquiries")
class ClientController {
 final JdbcTemplate db; final Security security; final AuthController confirmations; final Mail mail;
 ClientController(JdbcTemplate db, Security security, AuthController confirmations, Mail mail){this.db=db;this.security=security;this.confirmations=confirmations;this.mail=mail;}

 Map<String,Object> client(HttpServletRequest request){
  var user=security.principal(request);
  if(user.get("email_verified_at")==null)throw new ApiError(403,"EMAIL_NOT_VERIFIED","Confirm your account email to view your requests.");
  return user;
 }
 Map<String,Object> owned(UUID id,Map<String,Object> client,boolean lock){
  var rows=db.queryForList("SELECT * FROM inquiries WHERE id=? AND client_email=?"+(lock?" FOR UPDATE":""),id,client.get("email"));
  if(rows.isEmpty())throw new ApiError(404,"NOT_FOUND","Request not found.");
  return rows.get(0);
 }
 boolean requestedBy(UUID id,Map<String,Object> user){
  return db.queryForObject("SELECT count(*) FROM inquiries WHERE id=? AND client_email=?",Integer.class,id,user.get("email"))>0;
 }
 @GetMapping Map<String,Object> list(HttpServletRequest request){
  var user=client(request);
  return Pages.query(db,request,"SELECT i.id,i.description,i.current_status AS status,i.moderation_status AS \"moderationStatus\",i.budget_band AS \"budgetBand\",i.timeline_band AS \"timelineBand\",i.updated_at AS \"updatedAt\",p.title AS \"projectTitle\",d.display_name AS builder,EXISTS(SELECT 1 FROM reviews r WHERE r.inquiry_id=i.id) AS \"reviewSubmitted\" FROM inquiries i JOIN products p ON p.id=i.reference_product_id JOIN developer_profiles d ON d.user_id=i.developer_user_id WHERE i.client_email=? ORDER BY i.updated_at DESC,i.id",user.get("email"));
 }
 @GetMapping("/{id}") Map<String,Object> detail(@PathVariable UUID id,HttpServletRequest request){
  var inquiry=owned(id,client(request),false);
  return Map.of("description",inquiry.get("description"),"status",inquiry.get("current_status"),"moderationStatus",inquiry.get("moderation_status"),"events",db.queryForList("SELECT event_type,actor_type,created_at FROM inquiry_events WHERE inquiry_id=? ORDER BY created_at,id",id));
 }
 @PostMapping("/{id}/decision") @Transactional Map<String,Object> decision(@PathVariable UUID id,@RequestBody Map<String,Object> body,HttpServletRequest request){
  var user=client(request);var inquiry=owned(id,user,true);
  InquiryPolicy.requireClear(inquiry);String kind=text(body,"kind",1,40),decision=text(body,"decision",1,10);
  if(!Set.of("HIRE_CONFIRMATION","COMPLETION_CONFIRMATION").contains(kind)||!Set.of("ACCEPT","REJECT").contains(decision))throw new ApiError(400,"VALIDATION_ERROR","Choose a valid confirmation action.");
  String state=(String)inquiry.get("current_status");
  // A retry of the same accepted outcome is harmless; no second event or email.
  if(decision.equals("ACCEPT")&&((kind.equals("HIRE_CONFIRMATION")&&Set.of("HIRED","IN_PROGRESS","COMPLETION_PENDING_CONFIRMATION","COMPLETED").contains(state))||(kind.equals("COMPLETION_CONFIRMATION")&&state.equals("COMPLETED"))))return Map.of("status",state);
  return Map.of("status",confirmations.applyDecision(inquiry,kind,decision,"CLIENT",(UUID)user.get("id")));
 }
 @PostMapping("/{id}/review") @Transactional Map<String,Object> review(@PathVariable UUID id,@RequestBody Map<String,Object> body,HttpServletRequest request){
  confirmations.submitReview(owned(id,client(request),true),body);
  return Map.of("ok",true,"status","HELD_FOR_REVIEW");
 }
 @PostMapping("/{id}/confirmation-link") @Transactional Map<String,Object> resend(@PathVariable UUID id,HttpServletRequest request){
  var inquiry=owned(id,client(request),true);
  InquiryPolicy.requireClear(inquiry);InquiryPolicy.requireSender(db,(String)inquiry.get("client_email"));
  String kind=switch((String)inquiry.get("current_status")){
   case "EXPIRED"->{db.update("UPDATE inquiries SET current_status='CREATED_UNVERIFIED',updated_at=now() WHERE id=?",id);yield "CLIENT_INQUIRY_CONFIRMATION";}
   case "CREATED_UNVERIFIED"->"CLIENT_INQUIRY_CONFIRMATION";
   case "HIRE_PENDING_CONFIRMATION"->"HIRE_CONFIRMATION";
   case "COMPLETION_PENDING_CONFIRMATION"->"COMPLETION_CONFIRMATION";
   case "COMPLETED"->"REVIEW";
   default->throw new ApiError(409,"INVALID_STATE_TRANSITION","No confirmation is waiting.");
  };
  mail.token(kind,null,id,(String)inquiry.get("client_email"));return Map.of("ok",true);
 }
 @PostMapping("/{id}/not-hired") @Transactional Map<String,Object> close(@PathVariable UUID id,HttpServletRequest request){
  var user=client(request);var inquiry=owned(id,user,true);
  if(inquiry.get("current_status").equals("NOT_HIRED"))return Map.of("ok",true);
  InquiryPolicy.requireClear(inquiry);String next=Rules.transition((String)inquiry.get("current_status"),"not-hired");
  db.update("UPDATE inquiries SET current_status=?,updated_at=now() WHERE id=?",next,id);
  db.update("INSERT INTO inquiry_events(id,inquiry_id,event_type,actor_type,actor_id) VALUES(?,?,'NOT_HIRED','CLIENT',?)",Support.id(),id,user.get("id"));
  mail.notify((UUID)inquiry.get("developer_user_id"),"Your client closed an inquiry as not hired.");return Map.of("ok",true);
 }
}
