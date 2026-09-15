package com.getlancer;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static com.getlancer.Support.*;

@RestController @RequestMapping("/api/v1") class AccountController {
 final JdbcTemplate db; final Security security;
 AccountController(JdbcTemplate db,Security security){this.db=db;this.security=security;}
 @GetMapping("/me/export") @Transactional(readOnly=true) Map<String,Object> export(HttpServletRequest request){
  var user=security.principal(request); UUID id=(UUID)user.get("id");
  if(user.get("email_verified_at")==null)throw new ApiError(403,"EMAIL_NOT_VERIFIED","Confirm your email before exporting account data.");
  Map<String,Object> result=new LinkedHashMap<>();
  result.put("account",db.queryForMap("SELECT id,email,email_verified_at,created_at FROM users WHERE id=?",id));
  result.put("profile",db.queryForMap("SELECT * FROM developer_profiles WHERE user_id=?",id));
  result.put("products",db.queryForList("SELECT id,slug,title,summary,description,contribution_text,approval_status,lifecycle_status,created_at FROM products WHERE owner_user_id=?",id));
  result.put("requests",db.queryForList("SELECT id,client_email,client_name,description,budget_band,timeline_band,current_status,created_at FROM inquiries WHERE client_email=? OR (developer_user_id=? AND email_confirmed_at IS NOT NULL AND moderation_status='CLEAR')",user.get("email"),id));
  result.put("savedProducts",db.queryForList("SELECT product_id,created_at FROM saved_products WHERE user_id=?",id));
  result.put("legalAcceptances",db.queryForList("SELECT document_version,accepted_at FROM legal_acceptances WHERE user_id=?",id));
  return result;
 }
 @PatchMapping("/notifications/{id}/read") Map<String,Object> read(@PathVariable UUID id,HttpServletRequest request){
  if(db.update("UPDATE notifications SET read_at=COALESCE(read_at,now()) WHERE id=? AND user_id=?",id,security.user(request))==0)throw new ApiError(404,"NOT_FOUND","Notification not found.");
  return Map.of("ok",true);
 }
}
