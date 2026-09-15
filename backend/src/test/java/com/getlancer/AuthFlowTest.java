package com.getlancer;

import java.util.*;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthFlowTest {
 final JdbcTemplate db=mock(JdbcTemplate.class);final Security security=mock(Security.class);final Mail mail=mock(Mail.class);
 final AuthController auth=new AuthController(db,security,mail,true,"admin@example.com");
 @Test void correctAdminPasswordIssuesChallengeWithoutSession(){
  UUID id=UUID.randomUUID();when(db.queryForList("SELECT * FROM users WHERE email=? AND account_status='ACTIVE'","admin@example.com")).thenReturn(List.of(Map.of("id",id,"password_hash",new BCryptPasswordEncoder(12).encode("example-password-123"))));when(security.role(id,"ADMIN")).thenReturn(true);
  var res=new MockHttpServletResponse();var result=auth.login(Map.of("email","admin@example.com","password","example-password-123"),res);
  assertEquals(true,result.get("mfaRequired"));String cookie=res.getHeader("Set-Cookie");assertTrue(cookie.startsWith("gl_mfa="));assertTrue(cookie.contains("HttpOnly"));assertTrue(cookie.contains("Secure"));assertFalse(cookie.contains("gl_session"));
 }
 @Test void wrongPasswordDoesNotRevealAdministratorStep(){
  when(db.queryForList("SELECT * FROM users WHERE email=? AND account_status='ACTIVE'","absent@example.com")).thenReturn(List.of());var res=new MockHttpServletResponse();
  assertThrows(ApiError.class,()->auth.login(Map.of("email","absent@example.com","password","wrong"),res));assertNull(res.getHeader("Set-Cookie"));verify(security).limitIdentity(anyString(),eq("login"));verify(security,never()).role(any(),eq("ADMIN"));
 }
 @Test void ordinaryAccountSignsInWithoutMfa(){
  UUID id=UUID.randomUUID();when(db.queryForList("SELECT * FROM users WHERE email=? AND account_status='ACTIVE'","member@example.com")).thenReturn(List.of(Map.of("id",id,"password_hash",new BCryptPasswordEncoder(12).encode("member-password-123"))));
  var res=new MockHttpServletResponse();var result=auth.login(Map.of("email","member@example.com","password","member-password-123"),res);assertEquals(id,result.get("id"));assertFalse(result.containsKey("mfaRequired"));assertTrue(res.getHeader("Set-Cookie").startsWith("gl_session="));
 }
 @Test void missingChallengeCannotAuthenticate(){
  var req=new MockHttpServletRequest();var res=new MockHttpServletResponse();when(db.queryForList(anyString(),any(Object[].class))).thenReturn(List.of());
  ApiError failure=assertThrows(ApiError.class,()->auth.mfa(Map.of("totp","123456"),req,res));assertEquals("MFA_EXPIRED",failure.code);assertNull(res.getHeader("Set-Cookie"));
 }
 @Test void invalidMfaConsumesAnAttemptAndNeverIssuesSession(){
  UUID id=UUID.randomUUID();String raw="challenge";var req=new MockHttpServletRequest();req.setCookies(new Cookie("gl_mfa",raw));var res=new MockHttpServletResponse();
  when(db.queryForList(anyString(),eq(Support.hash(raw)))).thenReturn(List.of(Map.of("user_id",id,"admin_totp","JBSWY3DPEHPK3PXP","account_status","ACTIVE","attempts",0,"expires_at",Timestamp.from(Instant.now().plusSeconds(300)))));when(security.role(id,"ADMIN")).thenReturn(true);
  ApiError failure=assertThrows(ApiError.class,()->auth.mfa(Map.of("totp","invalid"),req,res));assertEquals("MFA_INVALID",failure.code);verify(db).update("UPDATE login_challenges SET attempts=attempts+1 WHERE token_hash=?",Support.hash(raw));assertNull(res.getHeader("Set-Cookie"));
 }
 @Test void challengeWithFiveFailedAttemptsCannotAuthenticate(){
  UUID id=UUID.randomUUID();var req=new MockHttpServletRequest();req.setCookies(new Cookie("gl_mfa","locked"));var res=new MockHttpServletResponse();
  when(db.queryForList(anyString(),eq(Support.hash("locked")))).thenReturn(List.of(Map.of("user_id",id,"account_status","ACTIVE","attempts",5,"expires_at",Timestamp.from(Instant.now().plusSeconds(300)))));
  ApiError failure=assertThrows(ApiError.class,()->auth.mfa(Map.of("totp","123456"),req,res));assertEquals("MFA_EXPIRED",failure.code);verify(db).update("DELETE FROM login_challenges WHERE token_hash=?",Support.hash("locked"));assertTrue(res.getHeaders("Set-Cookie").stream().noneMatch(c->c.startsWith("gl_session=")));
 }
 @Test void validMfaIssuesOneSessionAndReplayedChallengeFails()throws Exception{
  UUID id=UUID.randomUUID();var req=new MockHttpServletRequest();req.setCookies(new Cookie("gl_mfa","one-time"));var res=new MockHttpServletResponse();
  var record=Map.<String,Object>of("user_id",id,"admin_totp","GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ","account_status","ACTIVE","attempts",0,"expires_at",Timestamp.from(Instant.now().plusSeconds(300)));
  when(db.queryForList(anyString(),eq(Support.hash("one-time")))).thenReturn(List.of(record),List.of());when(security.role(id,"ADMIN")).thenReturn(true);
  // RFC 6238 fixture key; calculate the current moving code independently of the verifier.
  var mac=javax.crypto.Mac.getInstance("HmacSHA1");mac.init(new javax.crypto.spec.SecretKeySpec("12345678901234567890".getBytes(java.nio.charset.StandardCharsets.US_ASCII),"HmacSHA1"));
  byte[] digest=mac.doFinal(java.nio.ByteBuffer.allocate(8).putLong(Instant.now().getEpochSecond()/30).array());int offset=digest[digest.length-1]&15;int value=java.nio.ByteBuffer.wrap(digest,offset,4).getInt()&0x7fffffff;String code=String.format("%06d",value%1000000);
  assertEquals(id,auth.mfa(Map.of("totp",code),req,res).get("id"));assertTrue(res.getHeaders("Set-Cookie").stream().anyMatch(c->c.startsWith("gl_session=")&&c.contains("Max-Age=3600")));
  verify(db).update("DELETE FROM login_challenges WHERE token_hash=?",Support.hash("one-time"));
  var replay=new MockHttpServletResponse();assertEquals("MFA_EXPIRED",assertThrows(ApiError.class,()->auth.mfa(Map.of("totp",code),req,replay)).code);assertNull(replay.getHeader("Set-Cookie"));
 }
 GoogleAuthController google(String id,String secret){return new GoogleAuthController(db,auth,mock(GoogleTokens.class),mock(GoogleAccounts.class),new ObjectMapper(),id,secret,"https://getlancer.example");}
 @Test void missingGoogleConfigurationDoesNotPretendToSignIn(){var controller=google("","");assertEquals(false,controller.enabled());assertThrows(ApiError.class,()->controller.start(Map.of("intent","login"),new MockHttpServletResponse()));}
 @Test void googleSignupRequiresConsent(){assertThrows(ApiError.class,()->google("id","secret").start(Map.of("intent","signup"),new MockHttpServletResponse()));verifyNoInteractions(db);}
 @Test void googleStartUsesPkceAndBrowserBinding()throws Exception{
  var res=new MockHttpServletResponse();var result=google("client","secret").start(Map.of("intent","login"),res);String url=(String)result.get("authorizationUrl");
  assertTrue(url.startsWith("https://accounts.google.com/o/oauth2/v2/auth?"));assertTrue(url.contains("code_challenge_method=S256"));assertTrue(url.contains("nonce="));assertTrue(url.contains("state="));assertFalse(url.contains("secret"));assertTrue(res.getHeader("Set-Cookie").startsWith("gl_oauth="));assertTrue(res.getHeader("Set-Cookie").contains("HttpOnly"));
 }
 @Test void callbackWithoutBrowserCookieNeverExchangesCode(){var response=google("id","secret").callback("state","code","",new MockHttpServletRequest(),new MockHttpServletResponse());assertEquals(303,response.getStatusCode().value());assertEquals("/login?auth_error=expired",response.getHeaders().getLocation().toString());verifyNoInteractions(db);}
}
