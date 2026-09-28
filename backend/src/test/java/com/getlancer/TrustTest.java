package com.getlancer;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import java.net.*;import java.io.*;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class TrustTest {
 @Test void dnsLookupIsBoundedAndDoesNotQueueStuckResolvers()throws Exception{
  var worker=new DemoHealth(mock(JdbcTemplate.class));var started=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
  try{
   assertThrows(IOException.class,()->DemoHealth.resolve(worker.dns,()->{started.countDown();while(release.getCount()>0){try{release.await();}catch(InterruptedException ignored){}}return new InetAddress[0];},1000));
   assertEquals(0,started.getCount());
   assertThrows(IOException.class,()->DemoHealth.resolve(worker.dns,()->new InetAddress[0],30));
   assertEquals(1,worker.dns.getPoolSize());assertEquals(0,worker.dns.getQueue().size());
  }finally{release.countDown();worker.close();}
  var healthy=new DemoHealth(mock(JdbcTemplate.class));try{assertEquals(1,DemoHealth.resolve(healthy.dns,()->new InetAddress[]{InetAddress.getByAddress(new byte[]{8,8,8,8})},1000).length);}finally{healthy.close();}
 }

 @Test void githubEvidenceRequiresExactRepositoryOrigin(){for(String u:List.of("https://github.com.evil.test/a/b","https://github.com@evil.test/a/b","http://github.com/a/b","https://github.com/a/b/tree/main","https://github.com/a/b?token=x"))assertFalse(TrustController.githubRepository(u));assertTrue(TrustController.githubRepository("https://github.com/team/project"));}
 @Test void demoTargetsRejectCredentialsPrivateSchemesAndPorts(){for(String u:List.of("http://example.com","https://localhost","https://127.0.0.1","https://[::1]","https://foo.internal","https://user:pass@example.com","https://example.com:8443"))assertThrows(IllegalArgumentException.class,()->DemoHealth.target(u));assertEquals("example.com",DemoHealth.target("https://example.com/demo").getHost());}
 @Test void mixedDnsResponsesAreRejected()throws Exception{assertThrows(IllegalArgumentException.class,()->DemoHealth.validateAddresses(new InetAddress[]{InetAddress.getByAddress(new byte[]{8,8,8,8}),InetAddress.getByAddress(new byte[]{127,0,0,1})}));}
 @Test void headParserHasABoundedHeaderAndNeverConsumesBody()throws Exception{var in=new ByteArrayInputStream("HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\nhello".getBytes());assertTrue(DemoHealth.readHeaders(in).endsWith("\r\n\r\n"));assertEquals('h',in.read());assertThrows(IOException.class,()->DemoHealth.readHeaders(new ByteArrayInputStream(new byte[17000])));assertEquals("BLOCKED",DemoHealth.classify(403));assertEquals("UNREACHABLE",DemoHealth.classify(503));}
 @Test void disabledWorkerMakesNoDatabaseOrNetworkCalls(){var db=mock(JdbcTemplate.class);var worker=new DemoHealth(db);worker.check();verifyNoInteractions(db);}
 @Test void verificationChecksOwnerAndApprovedBuilderBeforeWriting(){var db=mock(JdbcTemplate.class);var security=mock(Security.class);var products=mock(ProductsController.class);var request=new MockHttpServletRequest();UUID owner=UUID.randomUUID(),product=UUID.randomUUID();when(security.developer(request,true)).thenReturn(owner);when(products.owned(product,owner)).thenThrow(new ApiError(404,"NOT_FOUND","Not found"));assertThrows(ApiError.class,()->new TrustController(db,security,products).request(product,request));verifyNoInteractions(db);}
 @Test void capacityAwardRejectsUnconfirmedOutcome(){var db=mock(JdbcTemplate.class);var security=mock(Security.class);var request=new MockHttpServletRequest();when(security.admin(request)).thenReturn(UUID.randomUUID());when(db.queryForList(anyString(),any(Object[].class))).thenReturn(List.of());assertEquals("OUTCOME_NOT_ELIGIBLE",assertThrows(ApiError.class,()->new TrustController(db,security,mock(ProductsController.class)).award(UUID.randomUUID(),Map.of("reason","Reviewed client-confirmed completion"),request)).code);verify(db,never()).update(anyString(),any(Object[].class));}
}
