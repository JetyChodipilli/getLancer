package com.getlancer;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.net.ssl.*;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.jdbc.core.JdbcTemplate;

/** HEAD-only probe. Connects to a validated address, with TLS bound to the original hostname. */
@Component class DemoHealth {
 final JdbcTemplate db;
 @Value("${app.demo-health-enabled:false}") boolean enabled;
 DemoHealth(JdbcTemplate db){this.db=db;}
 static URI target(String raw){
  try{URI u=URI.create(raw);String h=u.getHost();if(!"https".equalsIgnoreCase(u.getScheme())||h==null||u.getRawUserInfo()!=null||(u.getPort()!=-1&&u.getPort()!=443)||h.contains(":")||h.matches("[0-9.]+")||!h.contains(".")||h.endsWith(".local")||h.endsWith(".internal")||h.endsWith(".localhost")||raw.length()>2000)throw new IllegalArgumentException();return u;}catch(Exception e){throw new IllegalArgumentException("UNSAFE_EXTERNAL_URL");}
 }
 static void validateAddresses(InetAddress[] addresses){if(addresses.length==0)throw new IllegalArgumentException("UNSAFE_EXTERNAL_URL");for(var ip:addresses)if(!Rules.publicAddress(ip))throw new IllegalArgumentException("UNSAFE_EXTERNAL_URL");}
 static String classify(int status){if(status>=200&&status<400)return "REACHABLE";if(Set.of(401,403,405,429).contains(status))return "BLOCKED";return "UNREACHABLE";}
 String probe(String raw){
  try{URI u=target(raw);for(int hop=0;hop<3;hop++){
   var addresses=InetAddress.getAllByName(u.getHost());validateAddresses(addresses);
   try(Socket tcp=new Socket()){
    tcp.connect(new InetSocketAddress(addresses[0],443),3000);tcp.setSoTimeout(3000);
    try(SSLSocket ssl=(SSLSocket)((SSLSocketFactory)SSLSocketFactory.getDefault()).createSocket(tcp,u.getHost(),443,true)){
     ssl.setSoTimeout(3000);var parameters=ssl.getSSLParameters();parameters.setEndpointIdentificationAlgorithm("HTTPS");parameters.setServerNames(List.of(new SNIHostName(u.getHost())));ssl.setSSLParameters(parameters);ssl.startHandshake();
     String path=u.getRawPath();if(path==null||path.isEmpty())path="/";if(u.getRawQuery()!=null)path+="?"+u.getRawQuery();
     ssl.getOutputStream().write(("HEAD "+path+" HTTP/1.1\r\nHost: "+u.getHost()+"\r\nUser-Agent: getLancer-DemoHealth/1.5\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
     String headers=readHeaders(ssl.getInputStream());String[] lines=headers.split("\r\n");if(!lines[0].matches("HTTP/1\\.[01] [0-9]{3}.*"))return "UNKNOWN";
     int status=Integer.parseInt(lines[0].substring(9,12));if(Set.of(301,302,303,307,308).contains(status)){
      String location=null;for(String line:lines)if(line.toLowerCase(Locale.ROOT).startsWith("location:"))location=line.substring(9).trim();
      if(location==null||hop==2)return "UNKNOWN";u=target(u.resolve(location).toString());continue;
     }return classify(status);
    }
   }
  }return "UNKNOWN";
  }catch(IllegalArgumentException e){return "BLOCKED";}catch(IOException e){return "UNREACHABLE";}
 }
 static String readHeaders(InputStream in)throws IOException{
  var bytes=new ByteArrayOutputStream();long deadline=System.nanoTime()+3_000_000_000L;int tail=0;
  while(bytes.size()<16384&&System.nanoTime()<deadline){int b=in.read();if(b<0)throw new EOFException();bytes.write(b);tail=(tail<<8)|b;if(tail==0x0d0a0d0a)return bytes.toString(StandardCharsets.ISO_8859_1);}
  throw new IOException("Header limit exceeded");
 }
 @Scheduled(fixedDelayString="${app.demo-health-interval-ms:3600000}") public void check(){
  if(!enabled)return;
  // Small bounded batches; no network request holds a database transaction open.
  var rows=db.queryForList(ProductsController.SELECT+" WHERE "+ProductsController.PUBLIC+" AND p.live_url IS NOT NULL AND p.live_url<>'' AND (p.demo_checked_at IS NULL OR p.demo_checked_at<now()-interval '24 hours') ORDER BY p.demo_checked_at NULLS FIRST,p.id LIMIT 10");
  for(var row:rows){String url=(String)row.get("live_url");String status=probe(url);db.update("UPDATE products SET demo_health=?,demo_checked_at=now(),demo_checked_url=? WHERE id=? AND live_url=? AND approval_status='APPROVED' AND lifecycle_status='ACTIVE' AND visibility='PUBLIC'",status,url,row.get("id"),url);}
 }
}
