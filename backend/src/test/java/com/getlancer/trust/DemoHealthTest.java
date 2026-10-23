package com.getlancer.trust;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class DemoHealthTest {
  static final Instant NOW=Instant.parse("2026-10-23T12:00:00Z");
  static final String URL="https://example.org/demo",HASH="a".repeat(64);
  static InetAddress publicIp()throws Exception{return InetAddress.getByAddress(new byte[]{8,8,8,8});}
  static Map<String,Object> fact(){return new HashMap<>(Map.of("demo_health","UNREACHABLE","demo_checked_at",Timestamp.from(NOW.minusSeconds(60)),"demo_checked_url",URL,"demo_checked_source_hash",HASH));}
  @Test void healthRequiresExactUrlHashAndCurrentTime(){
    var row=fact();assertEquals("UNREACHABLE",DemoHealth.current(row,URL,HASH,NOW));
    assertEquals("UNKNOWN",DemoHealth.current(row,URL+"?new",HASH,NOW));assertEquals("UNKNOWN",DemoHealth.current(row,URL,"b".repeat(64),NOW));
    for(Instant checked:List.of(NOW.minusSeconds(86400),NOW.plusSeconds(1))){row.put("demo_checked_at",Timestamp.from(checked));assertEquals("UNKNOWN",DemoHealth.current(row,URL,HASH,NOW));}
    row.put("demo_checked_at",Timestamp.from(NOW));row.put("demo_health","BLOCKED");assertEquals("BLOCKED",DemoHealth.current(row,URL,HASH,NOW));row.remove("demo_checked_at");assertEquals("UNKNOWN",DemoHealth.current(row,URL,HASH,NOW));
  }
  @Test void probePinsValidatedPublicAddressAndDoesNotResolveAgainInTransport()throws Exception{
    var ip=publicIp();var resolutions=new ArrayList<String>();var connections=new ArrayList<InetAddress>();
    var worker=new DemoHealth(mock(JdbcTemplate.class)){
      @Override InetAddress[] lookup(String host){resolutions.add(host);return new InetAddress[]{ip};}
      @Override String head(URI uri,InetAddress pinned){connections.add(pinned);assertEquals("example.org",uri.getHost());return "HTTP/1.1 200 OK\r\n\r\n";}
    };
    try{assertEquals("REACHABLE",worker.probe(URL));assertEquals(List.of("example.org"),resolutions);assertEquals(List.of(ip),connections);}finally{worker.close();}
  }
  @Test void probeRejectsPrivateRedirectAndMixedDnsWithoutConnecting()throws Exception{
    var publicIp=publicIp();var privateIp=InetAddress.getByAddress(new byte[]{127,0,0,1});var calls=new ArrayList<String>();
    var worker=new DemoHealth(mock(JdbcTemplate.class)){
      @Override InetAddress[] lookup(String host){return host.equals("example.org")?new InetAddress[]{publicIp}:new InetAddress[]{publicIp,privateIp};}
      @Override String head(URI uri,InetAddress pinned){calls.add(uri.getHost());return "HTTP/1.1 302 Found\r\nLocation: https://redirect.example.org/private\r\n\r\n";}
    };
    try{assertEquals("BLOCKED",worker.probe(URL));assertEquals(List.of("example.org"),calls);calls.clear();assertEquals("BLOCKED",worker.probe("https://redirect.example.org/private"));assertTrue(calls.isEmpty());assertEquals("BLOCKED",worker.probe("https://SERVICE.INTERNAL/"));}finally{worker.close();}
  }
  @Test void changedDnsOnSameHostRedirectCannotConnectToPrivateAnswer()throws Exception{
    var ip=publicIp();var privateIp=InetAddress.getByAddress(new byte[]{10,0,0,1});int[] lookups={0},heads={0};
    var worker=new DemoHealth(mock(JdbcTemplate.class)){
      @Override InetAddress[] lookup(String host){return new InetAddress[]{++lookups[0]==1?ip:privateIp};}
      @Override String head(URI uri,InetAddress pinned){assertEquals(ip,pinned);heads[0]++;return "HTTP/1.1 307 Temporary Redirect\r\nLocation: /changed\r\n\r\n";}
    };
    try{assertEquals("BLOCKED",worker.probe(URL));assertEquals(2,lookups[0]);assertEquals(1,heads[0]);}finally{worker.close();}
  }
  @Test void probeBoundsRedirectsAndKeepsRestrictionsDistinct()throws Exception{
    var ip=publicIp();int[] heads={0};String[] response={"HTTP/1.1 302 Found\r\nLocation: /again\r\n\r\n"};
    var worker=new DemoHealth(mock(JdbcTemplate.class)){
      @Override InetAddress[] lookup(String host){return new InetAddress[]{ip};}
      @Override String head(URI uri,InetAddress pinned)throws IOException{heads[0]++;if(response[0]==null)throw new IOException("timeout");return response[0];}
    };
    try{assertEquals("UNKNOWN",worker.probe(URL));assertEquals(3,heads[0]);response[0]="HTTP/1.1 403 Forbidden\r\n\r\n";assertEquals("BLOCKED",worker.probe(URL));response[0]="HTTP/1.1 404 Not Found\r\n\r\n";assertEquals("UNREACHABLE",worker.probe(URL));response[0]=null;assertEquals("UNREACHABLE",worker.probe(URL));}finally{worker.close();}
  }
  @Test void educationBatchReusesExactFreshProductFactWithoutRefreshingItsClock(){
    var db=mock(JdbcTemplate.class);Instant checked=Instant.now().minusSeconds(120);UUID id=UUID.randomUUID();
    when(db.queryForList(anyString())).thenReturn(List.of(Map.of("id",id,"source_hash",HASH,"demo_url",URL,"product_demo_health","UNREACHABLE","product_demo_checked_at",Timestamp.from(checked),"product_demo_checked_url",URL)));
    var worker=new DemoHealth(db){@Override protected String probe(String url){fail("Fresh exact URL fact should be reused");return "UNKNOWN";}};
    try{worker.checkEducation();verify(db).update(contains("UPDATE education_releases"),eq("UNREACHABLE"),eq(Timestamp.from(checked)),eq(URL),eq(HASH),eq(id),eq(HASH),eq(URL));
      var sql=ArgumentCaptor.forClass(String.class);verify(db).queryForList(sql.capture());assertTrue(sql.getValue().contains("LIMIT 10"));assertTrue(sql.getValue().contains("cm.status='APPROVED'"));assertTrue(sql.getValue().contains("latest.status='APPROVED'"));
    }finally{worker.close();}
  }
  @Test void educationResultWriteRechecksAuthorityAndExactSourceAfterNetworkIo(){
    var db=mock(JdbcTemplate.class);UUID id=UUID.randomUUID();when(db.queryForList(anyString())).thenReturn(List.of(Map.of("id",id,"source_hash",HASH,"demo_url",URL)));
    var worker=new DemoHealth(db){@Override protected String probe(String url){assertEquals(URL,url);return "REACHABLE";}};
    try{worker.checkEducation();var sql=ArgumentCaptor.forClass(String.class);verify(db).update(sql.capture(),eq("REACHABLE"),any(Timestamp.class),eq(URL),eq(HASH),eq(id),eq(HASH),eq(URL));String update=sql.getValue();for(String expected:List.of("e.source_hash=?","e.snapshot->>'demoUrl'=?","e.status='APPROVED'","u.account_status='ACTIVE'","ur.role='DEVELOPER'","latest.id","cm.status='APPROVED'"))assertTrue(update.contains(expected),expected);}finally{worker.close();}
  }
}
