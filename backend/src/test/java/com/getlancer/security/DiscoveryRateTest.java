package com.getlancer.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import com.getlancer.hosting.HostingConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class DiscoveryRateTest {
 @Test void discoveryReadUsesSharedTrustedIdentityAndRetryAfter() throws Exception {
  var rates=mock(RateLimits.class);var db=mock(JdbcTemplate.class);var security=new Security(db,"http://localhost:3000",30,rates,"",10,3,300,false);var filter=new BrowserSecurityFilter(security,new AuthorizationService(mock(HostingConfiguration.class)),mock(SecurityAudit.class));
  for(String path:List.of("/api/v1/products","/api/v1/builders/current-builder","/api/v1/teams","/api/v1/templates")){
   var request=new MockHttpServletRequest("GET",path);request.setRemoteAddr("192.0.2.1");request.addHeader("X-GetLancer-Client-IP","192.0.2.99");request.addHeader("X-GetLancer-Proxy","forged");
   var response=new MockHttpServletResponse();filter.doFilterInternal(request,response,(r,s)->fail("Throttled discovery must not reach the query."));assertEquals(429,response.getStatus());assertEquals("60",response.getHeader("Retry-After"));
  }
  verify(rates,times(4)).allow("discovery:192.0.2.1",300);verifyNoInteractions(db);
 }
 @Test void privateReadsUseTheirOwnBoundedAllowance() throws Exception {
  var rates=mock(RateLimits.class);var security=new Security(mock(JdbcTemplate.class),"http://localhost:3000",30,rates,"",10,3,300,false);var filter=new BrowserSecurityFilter(security,new AuthorizationService(mock(HostingConfiguration.class)),mock(SecurityAudit.class));var response=new MockHttpServletResponse();
  when(rates.allow("security-read:127.0.0.1",300)).thenReturn(true);
  filter.doFilterInternal(new MockHttpServletRequest("GET","/api/v1/me/export"),response,(r,s)->{});verify(rates).allow("security-read:127.0.0.1",300);assertEquals(200,response.getStatus());
 }
}
