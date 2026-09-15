package com.getlancer;
import java.net.*;import java.net.http.*;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import java.time.Duration;import java.util.*;
import jakarta.servlet.http.*;import com.fasterxml.jackson.databind.*;
import org.springframework.beans.factory.annotation.Value;import org.springframework.http.ResponseEntity;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.web.bind.annotation.*;
import static com.getlancer.Support.*;
@RestController @RequestMapping("/api/v1/auth") class GitHubAuthController {
 final JdbcTemplate db;final AuthController auth;final GitHubAccounts accounts;final ObjectMapper json;final String clientId,clientSecret,redirectUri;
 final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
 GitHubAuthController(JdbcTemplate db,AuthController auth,GitHubAccounts accounts,ObjectMapper json,@Value("${app.github.client-id:}")String clientId,@Value("${app.github.client-secret:}")String secret,@Value("${app.origin}")String origin){this.db=db;this.auth=auth;this.accounts=accounts;this.json=json;this.clientId=clientId;clientSecret=secret;redirectUri=origin.replaceAll("/$","")+"/api/v1/auth/github/callback";}
 boolean enabled(){return !clientId.isBlank()&&!clientSecret.isBlank();}
 @PostMapping("/github/start") Map<String,Object> start(@RequestBody Map<String,Object> body,HttpServletResponse response)throws Exception{
  if(!enabled())throw new ApiError(503,"GITHUB_NOT_CONFIGURED","GitHub sign-in is not available yet. Use email and password.");
  String intent=text(body,"intent",1,10);if(!Set.of("login","signup").contains(intent))throw new ApiError(400,"VALIDATION_ERROR","Choose login or signup.");if(intent.equals("signup")&&!Boolean.TRUE.equals(body.get("acceptedTerms")))throw new ApiError(400,"TERMS_REQUIRED","Agree to the terms to create an account.");
  String state=randomToken(),browser=randomToken(),verifier=randomToken();
  db.update("INSERT INTO oauth_pending(state_hash,browser_hash,nonce,code_verifier,intent,expires_at,provider,remember_me) VALUES(?,?,'',?,?,now()+interval '10 minutes','github',?)",hash(state),hash(browser),verifier,intent,Boolean.TRUE.equals(body.get("rememberMe")));
  auth.authCookie(response,"gl_github_oauth",browser,600);
  String challenge=Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
  return Map.of("authorizationUrl","https://github.com/login/oauth/authorize?"+GoogleAuthController.form(Map.of("client_id",clientId,"redirect_uri",redirectUri,"scope","read:user user:email","state",state,"code_challenge",challenge,"code_challenge_method","S256")));
 }
 @GetMapping("/github/callback") ResponseEntity<Void> callback(@RequestParam(defaultValue="")String state,@RequestParam(defaultValue="")String code,@RequestParam(defaultValue="")String error,HttpServletRequest request,HttpServletResponse response){
  auth.authCookie(response,"gl_github_oauth","",0);if(!enabled())return back("login","unavailable");String browser=AuthController.cookie(request,"gl_github_oauth");
  if(!state.matches("[A-Za-z0-9_-]{43}")||!browser.matches("[A-Za-z0-9_-]{43}"))return back("login","expired");
  var rows=db.queryForList("DELETE FROM oauth_pending WHERE state_hash=? AND browser_hash=? AND provider='github' AND expires_at>now() RETURNING code_verifier,intent,remember_me",hash(state),hash(browser));if(rows.isEmpty())return back("login","expired");
  var pending=rows.get(0);String intent=(String)pending.get("intent");if(!error.isBlank())return back(intent,"cancelled");if(code.isBlank()||code.length()>4096)return back(intent,"failed");
  try{
   var tokenRequest=HttpRequest.newBuilder(URI.create("https://github.com/login/oauth/access_token")).timeout(Duration.ofSeconds(10)).header("Accept","application/json").header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(GoogleAuthController.form(Map.of("client_id",clientId,"client_secret",clientSecret,"code",code,"redirect_uri",redirectUri,"code_verifier",(String)pending.get("code_verifier"))))).build();
   var tokenResponse=http.send(tokenRequest,HttpResponse.BodyHandlers.ofString());if(tokenResponse.statusCode()!=200)return back(intent,"failed");var token=json.readTree(tokenResponse.body());String access=token.path("access_token").asText();if(access.isBlank()||!token.path("token_type").asText().equalsIgnoreCase("bearer"))return back(intent,"failed");
   JsonNode profile=get("https://api.github.com/user",access),emails=get("https://api.github.com/user/emails?per_page=100",access);var identity=identity(profile,emails);
   UUID user=accounts.resolve(identity,intent);auth.issueSession(user,false,Boolean.TRUE.equals(pending.get("remember_me")),response);return ResponseEntity.status(303).location(URI.create("/workspace")).build();
  }catch(ApiError e){return back(e.code.equals("signup_required")?"signup":intent,e.code);}catch(InterruptedException e){Thread.currentThread().interrupt();return back(intent,"failed");}catch(Exception e){return back(intent,"failed");}
 }
 JsonNode get(String endpoint,String access)throws Exception{var r=http.send(HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(10)).header("Authorization","Bearer "+access).header("Accept","application/vnd.github+json").header("User-Agent","getLancer").GET().build(),HttpResponse.BodyHandlers.ofString());if(r.statusCode()!=200)throw new ApiError(502,"failed","GitHub could not verify this account.");return json.readTree(r.body());}
 static GitHubAccounts.Identity identity(JsonNode profile,JsonNode emails){
  if(!profile.path("id").isIntegralNumber()||profile.path("id").asLong()<=0||!emails.isArray())throw new ApiError(502,"failed","Invalid GitHub identity.");
  String email="";for(JsonNode item:emails)if(item.path("primary").asBoolean()&&item.path("verified").asBoolean()){email=item.path("email").asText();break;}
  if(!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")||email.length()>254)throw new ApiError(403,"email_unverified","Verify your primary GitHub email before continuing.");
  String name=profile.path("name").asText("");if(name.isBlank())name=profile.path("login").asText("");return new GitHubAccounts.Identity(profile.path("id").asText(),email.toLowerCase(Locale.ROOT),name);
 }
 static ResponseEntity<Void> back(String intent,String code){return ResponseEntity.status(303).location(URI.create((intent.equals("signup")?"/signup":"/login")+"?auth_error="+code+"&provider=github")).build();}
}
@RestController class AuthProviders {
 final GoogleAuthController google;final GitHubAuthController github;
 AuthProviders(GoogleAuthController google,GitHubAuthController github){this.google=google;this.github=github;}
 @GetMapping("/api/v1/auth/providers") Map<String,Object> providers(){return Map.of("google",google.enabled(),"github",github.enabled());}
}
