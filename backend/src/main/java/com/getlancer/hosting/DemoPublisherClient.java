package com.getlancer.hosting;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.getlancer.shared.ApiError;
import java.io.ByteArrayOutputStream;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import org.springframework.stereotype.Component;

/** Concrete bounded HTTP client; ambiguous responses never authorize a demo. */
@Component
public final class DemoPublisherClient {
  public record Metadata(UUID id,String state,String archiveSha256,String manifestSha256,Instant expiresAt,String url) {}
  private final HostingConfiguration config;private final ObjectMapper json=new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
  private final HttpClient http;
  public DemoPublisherClient(HostingConfiguration config){this.config=config;http=HttpClient.newBuilder().connectTimeout(Duration.ofMillis(config.timeoutMs>=500&&config.timeoutMs<=10000?config.timeoutMs:5000)).followRedirects(HttpClient.Redirect.NEVER).build();}
  public Metadata publish(UUID id,StaticArchive.Inspection archive,Instant expiresAt) {
    var payload=Map.of("id",id.toString(),"archiveSha256",archive.archiveSha256(),"manifestSha256",archive.manifestSha256(),"expiresAt",expiresAt.toString(),"files",archive.files().stream().map(StaticArchive.File::publication).toList());
    try{return call("PUT",id,json.writeValueAsBytes(payload),archive.archiveSha256(),archive.manifestSha256(),expiresAt);}catch(ApiError e){throw e;}catch(Exception e){throw unavailable();}
  }
  public Metadata get(UUID id,String archiveHash,String manifestHash,Instant expiresAt){return call("GET",id,null,archiveHash,manifestHash,expiresAt);}
  public Metadata delete(UUID id,String archiveHash,String manifestHash,Instant expiresAt){return call("DELETE",id,null,archiveHash,manifestHash,expiresAt);}
  private Metadata call(String method,UUID id,byte[] body,String archiveHash,String manifestHash,Instant expiry) {
    config.ready();String base=config.publisher.toString().replaceAll("/$","");
    var request=HttpRequest.newBuilder(java.net.URI.create(base+"/deployments/"+id)).timeout(Duration.ofMillis(config.timeoutMs)).header("Authorization","Bearer "+config.publisherSecret).header("Accept","application/json");
    if(body!=null)request.header("Content-Type","application/json");request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(body));
    CompletableFuture<HttpResponse<byte[]>> pending=null;
    try {
      pending=http.sendAsync(request.build(),info->new BoundedBody());var response=pending.get(config.timeoutMs,TimeUnit.MILLISECONDS);
      if(response.statusCode()==404&&method.equals("GET"))throw new ApiError(409,"DEPLOYMENT_UNCONFIRMED","The publisher has no metadata for this exact reserved deployment.");
      if(response.statusCode()!=200&&response.statusCode()!=201)throw unavailable();
      if(!response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT).startsWith("application/json"))throw mismatch();
      JsonNode node=json.readTree(response.body());if(node==null||!node.isObject()||!node.path("id").isTextual()||!node.path("id").asText().equals(id.toString()))throw mismatch();
      String state=node.path("state").asText();if(!Set.of("READY","DELETED").contains(state)||method.equals("DELETE")&&!state.equals("DELETED"))throw mismatch();
      // Unknown-id deletion tombstones may omit all publication metadata. Present facts must still match.
      if(state.equals("DELETED")) {
        optionalEqual(node,"archiveSha256",archiveHash);optionalEqual(node,"manifestSha256",manifestHash);
        if(node.hasNonNull("expiresAt")&&(!node.path("expiresAt").isTextual()||!Instant.parse(node.path("expiresAt").asText()).equals(expiry)))throw mismatch();
        if(node.hasNonNull("url")&&!node.path("url").asText().equals(config.publicUrl(id)))throw mismatch();
        return new Metadata(id,state,archiveHash,manifestHash,expiry,null);
      }
      if(!node.path("archiveSha256").isTextual()||!node.path("archiveSha256").asText().equals(archiveHash)||!node.path("manifestSha256").isTextual()||!node.path("manifestSha256").asText().equals(manifestHash)
          ||!node.path("expiresAt").isTextual()||!Instant.parse(node.path("expiresAt").asText()).equals(expiry)||!node.path("url").isTextual()||!node.path("url").asText().equals(config.publicUrl(id)))throw mismatch();
      return new Metadata(id,state,archiveHash,manifestHash,expiry,config.publicUrl(id));
    }catch(ApiError e){throw e;}catch(InterruptedException e){Thread.currentThread().interrupt();throw unavailable();}catch(Exception e){throw unavailable();}finally{if(pending!=null&&!pending.isDone())pending.cancel(true);}
  }
  private static void optionalEqual(JsonNode n,String key,String expected){if(n.hasNonNull(key)&&(!n.path(key).isTextual()||!n.path(key).asText().equals(expected)))throw mismatch();}
  private static ApiError unavailable(){return new ApiError(502,"PUBLISHER_UNCONFIRMED","The publisher did not confirm this operation. The committed intent remains for safe reconciliation.");}
  private static ApiError mismatch(){return new ApiError(409,"PUBLISHER_MISMATCH","Publisher facts do not match the immutable reserved deployment.");}
  /** Cancels before buffering >4KiB; the enclosing future also bounds slow or unfinished bodies. */
  private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
    private final CompletableFuture<byte[]> result=new CompletableFuture<>();private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();private Flow.Subscription subscription;
    public CompletionStage<byte[]> getBody(){return result;}
    public void onSubscribe(Flow.Subscription s){subscription=s;s.request(1);}
    public void onNext(List<ByteBuffer> chunks){for(var chunk:chunks){if(chunk.remaining()>4096-bytes.size()){subscription.cancel();result.completeExceptionally(new IllegalStateException("Publisher body exceeds limit"));return;}byte[] part=new byte[chunk.remaining()];chunk.get(part);bytes.writeBytes(part);}subscription.request(1);}
    public void onError(Throwable error){result.completeExceptionally(error);}
    public void onComplete(){result.complete(bytes.toByteArray());}
  }
}
