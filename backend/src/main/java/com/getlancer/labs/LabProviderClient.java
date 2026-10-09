package com.getlancer.labs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.shared.ApiError;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/** A fixed authenticated provider protocol; no app process ever builds or executes contributor code. */
@Component
public final class LabProviderClient {
  public record Command(UUID runId,long leaseGeneration,UUID operatorEpoch,UUID commandId,String manifestSha256,
      String imageDigest,Instant expiresAt,String action,String scenarioId,String operationId,Map<String,String> inputs,int memoryMiB) {}
  public record Result(UUID runId,long leaseGeneration,UUID operatorEpoch,UUID commandId,String manifestSha256,
      String imageDigest,Instant expiresAt,String state,boolean healthy,boolean isolated,Boolean everHealthy,String output,String summary) {}
  private final LabConfiguration config;private final ObjectMapper json;private final HttpClient http;
  public LabProviderClient(LabConfiguration config,ObjectMapper json){this.config=config;this.json=json;
    http=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofMillis(Math.max(500,Math.min(5000,config.timeoutMs)))).build();}
  Result send(Command command){
    if(config.origin==null||!LabConfiguration.validOrigin(config.origin)||config.secret==null||config.secret.isBlank())throw unavailable();
    try{
      byte[] body=json.writeValueAsBytes(command);if(body.length>65536)throw unavailable();
      HttpRequest request=HttpRequest.newBuilder(config.origin.resolve("/v1/lab-commands")).timeout(Duration.ofMillis(config.timeoutMs))
          .header("Authorization","Bearer "+config.secret).header("Content-Type","application/json").header("Accept","application/json")
          .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
      var subscriber=new BoundedBody();var future=http.sendAsync(request,info->subscriber);HttpResponse<byte[]> response;
      try{response=future.get(config.timeoutMs,TimeUnit.MILLISECONDS);}catch(Exception timeout){subscriber.cancel();future.cancel(true);throw timeout;}
      byte[] bytes=response.body();
      if(response.statusCode()!=200||bytes.length>16384||!response.headers().firstValue("Content-Type").orElse("").toLowerCase(java.util.Locale.ROOT).startsWith("application/json"))throw unavailable();
      Result result=json.readValue(bytes,Result.class);
      if(!command.runId().equals(result.runId())||command.leaseGeneration()!=result.leaseGeneration()||!command.operatorEpoch().equals(result.operatorEpoch())
          ||!command.commandId().equals(result.commandId())||!command.manifestSha256().equals(result.manifestSha256())||!command.imageDigest().equals(result.imageDigest())||!command.expiresAt().equals(result.expiresAt())
          ||!java.util.Set.of("RUNNING","CLEANED","PENDING","RESULT","FAILED").contains(result.state())||result.output()!=null&&result.output().length()>4096||result.summary()!=null&&result.summary().length()>500)throw unavailable();
      if(java.util.Set.of("RUNNING","RESULT").contains(result.state())&&(!result.healthy()||!result.isolated()))throw new ApiError(502,"LAB_ISOLATION_LOST","The provider no longer confirms healthy isolated execution. Routing is closed pending cleanup.");if(result.state().equals("CLEANED")&&result.everHealthy()==null)throw unavailable();return result;
    }catch(InterruptedException error){Thread.currentThread().interrupt();throw unavailable();}catch(ApiError unsafe){throw unsafe;}catch(Exception error){throw unavailable();}
  }
  /** Full-body completion is timed and bounded; a fast header cannot hide an endless response. */
  private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
    private final CompletableFuture<byte[]> completed=new CompletableFuture<>();private final java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();private volatile Flow.Subscription subscription;
    @Override public CompletionStage<byte[]> getBody(){return completed;}
    @Override public void onSubscribe(Flow.Subscription offered){if(subscription!=null){offered.cancel();return;}subscription=offered;offered.request(1);}
    @Override public void onNext(List<ByteBuffer> chunks){try{for(var chunk:chunks){if(bytes.size()+chunk.remaining()>16384){cancel();completed.completeExceptionally(new IllegalArgumentException("Bounded provider body exceeded"));return;}byte[] part=new byte[chunk.remaining()];chunk.get(part);bytes.write(part);}subscription.request(1);}catch(Exception invalid){cancel();completed.completeExceptionally(invalid);}}
    @Override public void onError(Throwable error){completed.completeExceptionally(error);}
    @Override public void onComplete(){completed.complete(bytes.toByteArray());}
    void cancel(){Flow.Subscription active=subscription;if(active!=null)active.cancel();}
  }
  private static ApiError unavailable(){return new ApiError(502,"LAB_PROVIDER_UNCONFIRMED","The isolated provider has not confirmed this exact command. Its reservation remains protected.");}
}
