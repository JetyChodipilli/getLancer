package com.getlancer.hosting;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Actual loopback HTTP peer used solely in tests; production uses the concrete Node publisher. */
public final class HostingPublisherFixture implements AutoCloseable {
  public static final String SECRET="hosting-publisher-test-secret-000000000000000",GATEWAY="hosting-gateway-test-secret-00000000000000000",TEMPLATE="http://{id}.demo.localhost:8090";
  final HttpServer server;final ExecutorService executor=Executors.newCachedThreadPool();final ObjectMapper json=new ObjectMapper();
  public final Map<String,JsonNode> records=new ConcurrentHashMap<>();public final Set<String> deleted=ConcurrentHashMap.newKeySet();
  public final AtomicInteger puts=new AtomicInteger(),gets=new AtomicInteger(),deletes=new AtomicInteger();
  public volatile boolean failPutBefore,failPutAfter,failDelete,mismatch,wrongUrl,wrongId,oversized,redirect,blockPut,blockDelete;
  public volatile CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);public volatile Consumer<String> observer=operation->{};public volatile boolean committed;
  public HostingPublisherFixture(){try{server=HttpServer.create(new InetSocketAddress("localhost",0),0);server.createContext("/deployments/",this::handle);server.setExecutor(executor);server.start();}catch(IOException e){throw new IllegalStateException(e);}}
  public String url(){return "http://localhost:"+server.getAddress().getPort();}
  public void reset(){records.clear();deleted.clear();puts.set(0);gets.set(0);deletes.set(0);failPutBefore=failPutAfter=failDelete=mismatch=wrongUrl=wrongId=oversized=redirect=blockPut=blockDelete=committed=false;entered=new CountDownLatch(1);release=new CountDownLatch(1);observer=operation->{};}
  private void handle(HttpExchange exchange)throws IOException {
    try {
      if(!("Bearer "+SECRET).equals(exchange.getRequestHeaders().getFirst("Authorization"))){send(exchange,403,"{}");return;}
      String id=exchange.getRequestURI().getPath().substring("/deployments/".length());String method=exchange.getRequestMethod();observer.accept(method);
      if(redirect){exchange.getResponseHeaders().set("Location",url()+"/deployments/"+id);send(exchange,302,"{}");return;}
      if(oversized){send(exchange,200,"{\"x\":\""+"a".repeat(5000)+"\"}");return;}
      if(method.equals("PUT")){
        puts.incrementAndGet();JsonNode payload=json.readTree(exchange.getRequestBody().readNBytes(15*1024*1024));if(failPutBefore){send(exchange,503,"{}");return;}
        if(blockPut){entered.countDown();release.await(5,TimeUnit.SECONDS);}
        if(deleted.contains(id)){send(exchange,409,"{}");return;}
        StringBuilder manifest=new StringBuilder();String previous="";int total=0;
        for(JsonNode file:payload.path("files")){byte[] content=Base64.getDecoder().decode(file.path("contentBase64").asText());if(!file.path("sha256").asText().equals(StaticArchive.sha(content))||file.path("sizeBytes").intValue()!=content.length||file.path("path").asText().compareTo(previous)<=0)throw new IllegalStateException("Invalid Java publication bytes");previous=file.path("path").asText();total+=content.length;manifest.append(previous).append('\0').append(file.path("sha256").asText()).append('\0').append(content.length).append('\n');}
        if(!StaticArchive.sha(manifest.toString().getBytes(StandardCharsets.UTF_8)).equals(payload.path("manifestSha256").asText())||total>StaticArchive.MAX_EXPANDED)throw new IllegalStateException("Invalid Java manifest");
        var node=json.createObjectNode();node.put("id",id);node.put("state","READY");node.put("archiveSha256",payload.path("archiveSha256").asText());node.put("manifestSha256",payload.path("manifestSha256").asText());node.put("expiresAt",payload.path("expiresAt").asText());node.put("url",TEMPLATE.replace("{id}",id));JsonNode old=records.putIfAbsent(id,node);if(old!=null&&!old.equals(node)){send(exchange,409,"{}");return;}
        if(failPutAfter){send(exchange,503,"{}");return;}
      }else if(method.equals("DELETE")){
        deletes.incrementAndGet();if(blockDelete){entered.countDown();release.await(5,TimeUnit.SECONDS);}if(failDelete){send(exchange,503,"{}");return;}deleted.add(id);var tombstone=json.createObjectNode().put("id",id).put("state","DELETED");records.put(id,tombstone);
      }else if(method.equals("GET"))gets.incrementAndGet();else{send(exchange,405,"{}");return;}
      JsonNode saved=records.get(id);if(saved==null){send(exchange,404,"{}");return;}var result=saved.deepCopy();if(mismatch&&result.path("state").asText().equals("READY"))((com.fasterxml.jackson.databind.node.ObjectNode)result).put("manifestSha256","0".repeat(64));if(wrongUrl)((com.fasterxml.jackson.databind.node.ObjectNode)result).put("url","https://attacker.example/");if(wrongId)((com.fasterxml.jackson.databind.node.ObjectNode)result).put("id",UUID.randomUUID().toString());send(exchange,200,json.writeValueAsString(result));
    }catch(InterruptedException e){Thread.currentThread().interrupt();send(exchange,503,"{}");}catch(Exception e){send(exchange,500,"{}");}finally{exchange.close();}
  }
  private static void send(HttpExchange exchange,int status,String text)throws IOException {byte[] bytes=text.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);}
  @Override public void close(){server.stop(0);executor.shutdownNow();}
}
