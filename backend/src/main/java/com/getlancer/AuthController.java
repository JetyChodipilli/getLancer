package com.getlancer;
import java.util.*;import java.time.*;import java.sql.Timestamp;import jakarta.servlet.http.*;import org.springframework.web.bind.annotation.*;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.transaction.annotation.Transactional;import org.springframework.beans.factory.annotation.Value;import org.springframework.http.ResponseCookie;import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;import static com.getlancer.Support.*;
@RestController @RequestMapping("/api/v1") class AuthController{
 @org.springframework.beans.factory.annotation.Value("${app.legal-version:v1-draft}") String legalVersion="v1-draft";
 final JdbcTemplate db;final Security security;final Mail mail;final boolean secure;final BCryptPasswordEncoder passwords=new BCryptPasswordEncoder(12);
 final String reservedAdminEmail;
 AuthController(JdbcTemplate db,Security security,Mail mail,@Value("${app.secure-cookie}")boolean secure,@Value("${app.admin-email}")String reservedAdminEmail){this.reservedAdminEmail=reservedAdminEmail.trim().toLowerCase(Locale.ROOT);this.db=db;this.security=security;this.mail=mail;this.secure=secure;}
 @PostMapping("/auth/signup") @ResponseStatus(org.springframework.http.HttpStatus.CREATED) @Transactional Map<String,Object> signup(@RequestBody Map<String,Object>b){String email=email(b,"email"),password=password(b);if(email.equals(reservedAdminEmail))throw new ApiError(409,"EMAIL_UNAVAILABLE","This email cannot be used for public registration.");if(!Boolean.TRUE.equals(b.get("acceptedTerms")))throw new ApiError(400,"TERMS_REQUIRED","Accept the terms and privacy notice to continue.");if(password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)throw new ApiError(400,"VALIDATION_ERROR","Password must be at most 72 UTF-8 bytes.");UUID u=id();db.update("INSERT INTO users(id,email,password_hash) VALUES(?,?,?)",u,email,passwords.encode(password));db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'CLIENT'),(?,'DEVELOPER')",u,u);db.update("INSERT INTO developer_profiles(user_id,slug,display_name) VALUES(?,?,?)",u,"builder-"+u,text(b,"displayName",0,100));db.update("INSERT INTO showcase_entitlements(user_id) VALUES(?)",u);db.update("INSERT INTO legal_acceptances(user_id,document_version) VALUES(?,?)",u,legalVersion);mail.token("EMAIL_VERIFICATION",u,null,email);return Map.of("message","Confirm your email to continue.");}
 @PostMapping("/auth/login") @Transactional Map<String,Object> login(@RequestBody Map<String,Object>b,HttpServletResponse res){
  String email=email(b,"email"),password=Objects.toString(b.get("password"),"");security.limitIdentity(email,"login");
  var users=db.queryForList("SELECT * FROM users WHERE email=? AND account_status='ACTIVE'",email);
  String dummy="$2a$12$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW";
  String stored=users.isEmpty()?dummy:(String)users.get(0).get("password_hash");
  if(password.length()>128||!passwords.matches(password,stored)||users.isEmpty())throw new ApiError(401,"UNAUTHENTICATED","Email or password is incorrect.");
  UUID user=(UUID)users.get(0).get("id");
  if(security.role(user,"ADMIN")){
   db.update("DELETE FROM login_challenges WHERE user_id=? OR expires_at<now()",user);
   String challenge=randomToken();
   db.update("INSERT INTO login_challenges(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '5 minutes')",hash(challenge),user);
   authCookie(res,"gl_mfa",challenge,300);
   return Map.of("mfaRequired",true);
  }
  issueSession(user,false,Boolean.TRUE.equals(b.get("rememberMe")),res);return Map.of("id",user);
 }
 @PostMapping("/auth/login/mfa") @Transactional(noRollbackFor=ApiError.class) Map<String,Object> mfa(@RequestBody Map<String,Object> body,HttpServletRequest req,HttpServletResponse res){
  String challenge=cookie(req,"gl_mfa");
  var rows=db.queryForList("SELECT c.*,u.admin_totp,u.account_status FROM login_challenges c JOIN users u ON u.id=c.user_id WHERE c.token_hash=? FOR UPDATE OF c,u",hash(challenge));
  if(rows.isEmpty())throw new ApiError(401,"MFA_EXPIRED","Please sign in again.");
  var row=rows.get(0);UUID user=(UUID)row.get("user_id");
  if(((Timestamp)row.get("expires_at")).toInstant().isBefore(Instant.now())||((Number)row.get("attempts")).intValue()>=5||!row.get("account_status").equals("ACTIVE")||!security.role(user,"ADMIN")){
   db.update("DELETE FROM login_challenges WHERE token_hash=?",hash(challenge));authCookie(res,"gl_mfa","",0);throw new ApiError(401,"MFA_EXPIRED","Please sign in again.");
  }
  if(!totp((String)row.get("admin_totp"),Objects.toString(body.get("totp"),""))){
   db.update("UPDATE login_challenges SET attempts=attempts+1 WHERE token_hash=?",hash(challenge));
   throw new ApiError(401,"MFA_INVALID","That code is incorrect or has expired. Try the current code from your authenticator.");
  }
  db.update("DELETE FROM login_challenges WHERE token_hash=?",hash(challenge));authCookie(res,"gl_mfa","",0);issueSession(user,true,res);return Map.of("id",user);
 }
 void issueSession(UUID user,boolean admin,HttpServletResponse res){issueSession(user,admin,false,res);}
 void issueSession(UUID user,boolean admin,boolean remember,HttpServletResponse res){
  String token=randomToken();long ttl=admin?3600:86400;
  db.update("INSERT INTO sessions(token_hash,user_id,expires_at,mfa_verified) VALUES(?,?,?,?)",hash(token),user,Timestamp.from(Instant.now().plusSeconds(ttl)),admin);
  if(admin||remember)authCookie(res,"gl_session",token,ttl);else res.addHeader("Set-Cookie",ResponseCookie.from("gl_session",token).httpOnly(true).secure(secure).sameSite("Lax").path("/").build().toString());
 }
 void authCookie(HttpServletResponse res,String name,String value,long seconds){res.addHeader("Set-Cookie",ResponseCookie.from(name,value).httpOnly(true).secure(secure).sameSite("Lax").path("/").maxAge(seconds).build().toString());}
 static String cookie(HttpServletRequest req,String name){if(req.getCookies()!=null)for(Cookie c:req.getCookies())if(c.getName().equals(name))return c.getValue();return "";}
 @PostMapping("/auth/logout") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) Map<String,Object> logout(HttpServletRequest req,HttpServletResponse res){db.update("DELETE FROM login_challenges WHERE token_hash=?",hash(cookie(req,"gl_mfa")));authCookie(res,"gl_mfa","",0);if(req.getCookies()!=null)for(Cookie c:req.getCookies())if(c.getName().equals("gl_session"))db.update("DELETE FROM sessions WHERE token_hash=?",hash(c.getValue()));res.addHeader("Set-Cookie",ResponseCookie.from("gl_session","").httpOnly(true).secure(secure).sameSite("Lax").path("/").maxAge(0).build().toString());return Map.of("ok",true);}
 @GetMapping("/me") Map<String,Object> me(HttpServletRequest r){var p=security.principal(r);UUID u=(UUID)p.get("id");var profile=db.queryForMap("SELECT * FROM developer_profiles WHERE user_id=?",u);return Map.of("id",u,"email",p.get("email"),"displayName",profile.get("display_name"),"profile",profile,"roles",db.queryForList("SELECT role FROM user_roles WHERE user_id=?",String.class,u),"emailVerified",p.get("email_verified_at")!=null,"activeSlotLimit",db.queryForObject("SELECT active_slot_limit FROM showcase_entitlements WHERE user_id=?",Integer.class,u));}
 @PostMapping("/auth/password-reset/request") @Transactional Map<String,Object> reset(@RequestBody Map<String,Object>b){String e=email(b,"email");security.limitIdentity(e,"reset");var rows=db.queryForList("SELECT id FROM users WHERE email=? AND account_status='ACTIVE'",e);if(!rows.isEmpty())mail.token("PASSWORD_RESET",(UUID)rows.get(0).get("id"),null,e);return Map.of("message","If an account exists, a reset email will be sent.");}
 @PostMapping("/auth/confirmation")
 Map<String,Object> confirmationContext(@RequestBody Map<String,Object> body) {
  var token=confirmationToken(text(body,"token",20,200),false);
  if(((Timestamp)token.get("expires_at")).toInstant().isBefore(Instant.now()))throw new ApiError(410,"TOKEN_EXPIRED","This confirmation link has expired.");
  Map<String,Object> result=new LinkedHashMap<>();
  result.put("kind",token.get("kind")); result.put("used",token.get("used_at")!=null);
  if(token.get("inquiry_id")!=null){
   var inquiry=db.queryForMap("SELECT i.description,i.current_status AS status,p.title AS \"projectTitle\",d.display_name AS builder FROM inquiries i JOIN products p ON p.id=i.reference_product_id JOIN developer_profiles d ON d.user_id=i.developer_user_id WHERE i.id=?",token.get("inquiry_id"));
   result.put("inquiry",inquiry);
  }
  return result;
 }
 Map<String,Object> confirmationToken(String raw,boolean lock) {
  var tokens=db.queryForList("SELECT * FROM account_tokens WHERE token_hash=?"+(lock?" FOR UPDATE":""),hash(raw));
  if(tokens.isEmpty())throw new ApiError(400,"INVALID_TOKEN","This confirmation link is invalid.");
  var token=tokens.get(0);
  if(token.get("used_at")==null&&((Timestamp)token.get("expires_at")).toInstant().isBefore(Instant.now()))throw new ApiError(410,"TOKEN_EXPIRED","This confirmation link has expired. Request a fresh link from your workspace.");
  return token;
 }
 @PostMapping({"/auth/confirm","/auth/verify-email","/auth/password-reset/confirm","/inquiries/confirm-email"})
 @Transactional Map<String,Object> confirm(@RequestBody Map<String,Object> body,HttpServletRequest request) {
  String raw=text(body,"token",20,200);
  var token=confirmationToken(raw,false);
  if("ACCOUNT_DELETION".equals(token.get("kind")))db.queryForMap("SELECT id FROM users WHERE id=? FOR UPDATE",token.get("user_id"));
  if(token.get("inquiry_id")!=null)db.queryForMap("SELECT id FROM inquiries WHERE id=? FOR UPDATE",token.get("inquiry_id"));
  token=confirmationToken(raw,true);
  String kind=(String)token.get("kind");
  String required=switch(request.getRequestURI()){
   case "/api/v1/auth/verify-email"->"EMAIL_VERIFICATION";
   case "/api/v1/auth/password-reset/confirm"->"PASSWORD_RESET";
   case "/api/v1/inquiries/confirm-email"->"CLIENT_INQUIRY_CONFIRMATION";
   default->kind;
  };
  if(!required.equals(kind))throw new ApiError(400,"INVALID_TOKEN","This link is for a different action.");
  if(token.get("used_at")!=null)return Map.of("ok",true,"alreadyRecorded",true);
  UUID user=(UUID)token.get("user_id"),inquiryId=(UUID)token.get("inquiry_id");
  switch(kind){
   case "EMAIL_VERIFICATION"->{
    if(db.update("UPDATE users SET email_verified_at=COALESCE(email_verified_at,now()) WHERE id=? AND account_status='ACTIVE'",user)!=1)throw new ApiError(409,"ACCOUNT_UNAVAILABLE","This account is unavailable.");
   }
   case "PASSWORD_RESET"->{
    String password=password(body);
    if(db.update("UPDATE users SET password_hash=? WHERE id=? AND account_status='ACTIVE'",passwords.encode(password),user)!=1)throw new ApiError(409,"ACCOUNT_UNAVAILABLE","This account is unavailable.");
    db.update("DELETE FROM sessions WHERE user_id=?",user);
    db.update("DELETE FROM login_challenges WHERE user_id=?",user);
    db.update("UPDATE account_tokens SET used_at=now() WHERE user_id=? AND kind='PASSWORD_RESET'",user);
   }
   case "ACCOUNT_DELETION"->{
    if(!"DELETE".equals(body.get("decision")))throw new ApiError(400,"CONFIRMATION_REQUIRED","Confirm account closure explicitly.");
    db.queryForMap("SELECT id FROM users WHERE id=? FOR UPDATE",user);
    if(security.role(user,"ADMIN"))throw new ApiError(409,"ADMIN_ACCOUNT_PROTECTED","The sole administrator cannot close their account through self-service.");
    db.update("INSERT INTO deletion_requests(user_id) VALUES(?) ON CONFLICT DO NOTHING",user);
    db.update("UPDATE users SET account_status='DELETED' WHERE id=?",user);
    db.update("DELETE FROM sessions WHERE user_id=?",user);
    db.update("DELETE FROM login_challenges WHERE user_id=?",user);
    db.update("UPDATE account_tokens SET used_at=now() WHERE user_id=?",user);
    event("account_deletion_requested",user);
   }
   case "CLIENT_INQUIRY_CONFIRMATION"->{
    var inquiry=db.queryForMap("SELECT * FROM inquiries WHERE id=? FOR UPDATE",inquiryId);
    InquiryPolicy.requireClear(inquiry);InquiryPolicy.requireSender(db,(String)inquiry.get("client_email"));
    if(!inquiry.get("current_status").equals("CREATED_UNVERIFIED"))throw new ApiError(409,"INVALID_STATE_TRANSITION","This inquiry cannot be confirmed now.");
    var products=db.queryForList(ProductsController.SELECT+" WHERE p.id=? AND "+ProductsController.PUBLIC+" AND p.available_for_similar_work=true AND d.availability_status<>'NOT_ACCEPTING' FOR SHARE OF p,u,d",inquiry.get("reference_product_id"));
    if(products.isEmpty())throw new ApiError(409,"PRODUCT_NOT_AVAILABLE","The project or builder is no longer accepting new inquiries.");
    db.update("UPDATE inquiries SET current_status='INQUIRY_RECEIVED',email_confirmed_at=now(),updated_at=now() WHERE id=?",inquiryId);
    db.update("INSERT INTO inquiry_events(id,inquiry_id,event_type,actor_type) VALUES(?,?,'INQUIRY_RECEIVED','CLIENT_TOKEN')",id(),inquiryId);
    mail.notify((UUID)inquiry.get("developer_user_id"),"A new qualified inquiry is ready.");
    event("inquiry_email_confirmed",inquiryId);
   }
   case "HIRE_CONFIRMATION","COMPLETION_CONFIRMATION"->{
    var inquiry=db.queryForMap("SELECT * FROM inquiries WHERE id=? FOR UPDATE",inquiryId);
    applyDecision(inquiry,kind,text(body,"decision",0,10),"CLIENT_TOKEN",null);
   }
   case "REVIEW"->{
    var inquiry=db.queryForMap("SELECT * FROM inquiries WHERE id=? FOR UPDATE",inquiryId);
    submitReview(inquiry,body);
   }
   default->throw new ApiError(400,"INVALID_TOKEN","Unsupported confirmation link.");
  }
  db.update("UPDATE account_tokens SET used_at=now() WHERE id=?",token.get("id"));
  return Map.of("ok",true);
 }
 String applyDecision(Map<String,Object> inquiry,String kind,String decision,String actor,UUID actorId){
  InquiryPolicy.requireClear(inquiry);InquiryPolicy.requireSender(db,(String)inquiry.get("client_email"));
  if(!Set.of("","ACCEPT","REJECT").contains(decision))throw new ApiError(400,"VALIDATION_ERROR","Choose confirm or not yet.");
  String next=Rules.clientDecision(kind,(String)inquiry.get("current_status"),!decision.equals("REJECT"));
  UUID inquiryId=(UUID)inquiry.get("id"),builder=(UUID)inquiry.get("developer_user_id");
  db.update("UPDATE inquiries SET current_status=?,updated_at=now() WHERE id=?",next,inquiryId);
  db.update("INSERT INTO inquiry_events(id,inquiry_id,event_type,actor_type,actor_id) VALUES(?,?,?,?,?)",id(),inquiryId,next,actor,actorId);
  db.update("UPDATE account_tokens SET used_at=now() WHERE inquiry_id=? AND kind=? AND used_at IS NULL",inquiryId,kind);
  mail.notify(builder,decision.equals("REJECT")?"Your client requested further discussion.":"Your client confirmed an engagement update.");
  if(next.equals("HIRED"))event("hire_confirmed",inquiryId);
  if(next.equals("COMPLETED")){
   event("project_completed",inquiryId);
   mail.token("REVIEW",null,inquiryId,(String)inquiry.get("client_email"));
  }
  return next;
 }
 void submitReview(Map<String,Object> inquiry,Map<String,Object> body) {
  InquiryPolicy.requireClear(inquiry);InquiryPolicy.requireSender(db,(String)inquiry.get("client_email"));
  UUID inquiryId=(UUID)inquiry.get("id"),builder=(UUID)inquiry.get("developer_user_id");
  if(!Rules.reviewEligible((String)inquiry.get("current_status"),db.queryForObject("SELECT count(*) FROM inquiry_events WHERE inquiry_id=? AND event_type='COMPLETED'",Integer.class,inquiryId)>0,false))throw new ApiError(409,"REVIEW_NOT_ELIGIBLE","Completion must be confirmed before reviewing.");
  int rating;
  try{rating=Integer.parseInt(Objects.toString(body.get("rating"),""));}catch(NumberFormatException e){throw new ApiError(400,"VALIDATION_ERROR","Choose a rating from 1 to 5.");}
  if(rating<1||rating>5)throw new ApiError(400,"VALIDATION_ERROR","Choose a rating from 1 to 5.");
  String review=text(body,"reviewText",10,2000);
  String visibility=Objects.toString(body.getOrDefault("visibility","ANONYMOUS"),"");
  if(!Set.of("ANONYMOUS","NAMED").contains(visibility))throw new ApiError(400,"VALIDATION_ERROR","Choose whether to display your name.",Map.of("visibility","Choose anonymous or named."));
  var prior=db.queryForList("SELECT rating,review_text,visibility FROM reviews WHERE inquiry_id=?",inquiryId);
  if(!prior.isEmpty()){
   if(((Number)prior.get(0).get("rating")).intValue()==rating&&prior.get(0).get("review_text").equals(review)&&prior.get(0).get("visibility").equals(visibility))return;
   throw new ApiError(409,"REVIEW_EXISTS","A review has already been submitted for this engagement.");
  }
  db.update("INSERT INTO reviews(id,inquiry_id,developer_user_id,rating,review_text,visibility) VALUES(?,?,?,?,?,?)",id(),inquiryId,builder,rating,review,visibility);
  db.update("UPDATE account_tokens SET used_at=now() WHERE inquiry_id=? AND kind='REVIEW'",inquiryId);
  event("review_submitted",inquiryId);
 }
 @PostMapping("/auth/resend-verification") @Transactional Map<String,Object> resend(HttpServletRequest request){
  var user=security.principal(request);
  security.limitIdentity((String)user.get("email"),"verification");
  if(user.get("email_verified_at")==null)mail.token("EMAIL_VERIFICATION",(UUID)user.get("id"),null,(String)user.get("email"));
  return Map.of("ok",true);
 }
 String password(Map<String,Object> body){
  String value=Objects.toString(body.get("password"),"");
  if(value.length()<12||value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)throw new ApiError(400,"VALIDATION_ERROR","Use at least 12 characters and at most 72 UTF-8 bytes for your password.");
  return value;
 }
 @DeleteMapping("/me") @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED) @Transactional Map<String,Object> deletion(HttpServletRequest r){
  var principal=security.principal(r);UUID user=(UUID)principal.get("id");
  if(security.role(user,"ADMIN"))throw new ApiError(409,"ADMIN_ACCOUNT_PROTECTED","The sole administrator cannot close their account through self-service.");
  security.limitIdentity((String)principal.get("email"),"deletion");
  db.queryForMap("SELECT id FROM users WHERE id=? FOR UPDATE",user);
  db.update("UPDATE account_tokens SET used_at=now() WHERE user_id=? AND kind='ACCOUNT_DELETION' AND used_at IS NULL",user);
  mail.token("ACCOUNT_DELETION",user,null,(String)principal.get("email"));
  return Map.of("status","EMAIL_CONFIRMATION_REQUIRED");
 }
 void event(String name,UUID entity){db.update("INSERT INTO analytics_events(id,event_name,entity_id) VALUES(?,?,?)",id(),name,entity);}
}
