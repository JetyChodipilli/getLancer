package com.getlancer.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.getlancer.shared.ApiError;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;

class AccountBoundRequestTest {
  private final JdbcTemplate db=mock(JdbcTemplate.class);
  private final Security security=new Security(db,"https://app.example.test",30);
  private final UUID current=UUID.fromString("00000000-0000-4000-8000-000000000002");

  private MockHttpServletRequest request(String expected){
    var row=new HashMap<String,Object>();row.put("id",current);row.put("email","current@example.test");
    row.put("email_verified_at",Instant.now());row.put("mfa_verified",false);row.put("issued_at",Instant.now());row.put("observed_at",Instant.now());
    when(db.queryForList(startsWith("WITH observed_clock"),anyString())).thenReturn(List.of(row));
    when(db.queryForList(eq("SELECT role FROM user_roles WHERE user_id=?"),eq(String.class),eq(current))).thenReturn(List.of("CLIENT"));
    var request=new MockHttpServletRequest("POST","/api/v1/me/example");request.setCookies(new Cookie("gl_session","dummy-session"));
    if(expected!=null)request.addHeader("X-GetLancer-Account",expected);
    return request;
  }

  @Test void retainedAccountCannotFollowAReplacedSessionIntoAnotherPrincipal(){
    var request=request("00000000-0000-4000-8000-000000000001");
    assertNull(security.loadPrincipal(request));assertNull(security.optionalUser(request));
    assertEquals(401,assertThrows(ApiError.class,()->security.user(request)).status);
    verify(db,never()).queryForList(eq("SELECT role FROM user_roles WHERE user_id=?"),eq(String.class),any());
  }

  @Test void missingBindingSupportsExistingClientsAndMatchingBindingKeepsTheRealIdentity(){
    assertEquals(current,security.user(request(null)));
    assertEquals(current,security.user(request(current.toString())));
  }

  @Test void matchingBindingDoesNotGrantAnAdministratorRoleOrMfa(){
    var request=request(current.toString());
    assertEquals(403,assertThrows(ApiError.class,()->security.admin(request)).status);
  }

  @Test void malformedBindingFailsClosedInsteadOfSelectingOrNormalizingAnActor(){
    for(String value:List.of("", "not-a-user",current+",another-user"," "+current))
      assertNull(security.loadPrincipal(request(value)));
  }
}
