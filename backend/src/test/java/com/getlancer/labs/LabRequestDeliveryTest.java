package com.getlancer.labs;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.dto.LabRequests;
import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Unit state-machine tests only: no database, HTTP server or provider is contacted. */
class LabRequestDeliveryTest {
  private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
  private final Store store=new Store();
  private final Transactions transactions=new Transactions();
  private final Security security=mock(Security.class);
  private final List<LabProviderClient.Command> sent=Collections.synchronizedList(new ArrayList<>());
  private final MockHttpServletRequest browser=new MockHttpServletRequest();
  @TempDir Path temporary;
  private LabConfiguration config;
  private LabProviderClient provider;
  private Function<LabProviderClient.Command,LabProviderClient.Result> delivery=command->result(command,"RESULT");
  private LabService labs;

  @BeforeEach void setUp()throws Exception{
    var key=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    var evidence=new LabConfiguration.Evidence("Synthetic offline unit evidence","KVM",true,true,true,true,true,Instant.now().plusSeconds(3600),"e".repeat(64),store.epoch);
    String payload=json.writeValueAsString(evidence);Path evidencePath=temporary.resolve("evidence.json");
    Files.writeString(evidencePath,json.writeValueAsString(Map.of("payload",Base64.getEncoder().encodeToString(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)),"signature",sign(key.getPrivate(),payload))));
    config=new LabConfiguration(true,"http://127.0.0.1:9","synthetic-offline-unit-secret-0123456789",Base64.getEncoder().encodeToString(key.getPublic().getEncoded()),evidencePath.toString(),store.epoch.toString(),1000,json);
    String manifestPayload=json.writeValueAsString(manifest());store.manifest=Map.of("payload_text",manifestPayload,"payload_sha256",LabConfiguration.hex(manifestPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8)),"signature",sign(key.getPrivate(),manifestPayload));
    provider=new LabProviderClient(config,json);HttpClient http=mock(HttpClient.class);
    when(http.sendAsync(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any())).thenAnswer(call->{
      var command=json.readValue(body(call.getArgument(0)),LabProviderClient.Command.class);sent.add(command);
      byte[] response=json.writeValueAsBytes(delivery.apply(command));
      @SuppressWarnings("unchecked") HttpResponse<byte[]> reply=mock(HttpResponse.class);
      when(reply.statusCode()).thenReturn(200);when(reply.body()).thenReturn(response);
      when(reply.headers()).thenReturn(HttpHeaders.of(Map.of("Content-Type",List.of("application/json")),(name,value)->true));
      return CompletableFuture.completedFuture(reply);
    });
    ReflectionTestUtils.setField(provider,"http",http);
    when(security.user(browser)).thenAnswer(call->{if(!store.sessionActive)throw new ApiError(401,"UNAUTHENTICATED","Session revoked.");return store.owner;});
    when(security.role(any(),anyString())).thenReturn(true);
    JdbcTemplate db=mock(JdbcTemplate.class,call->{
      if(call.getMethod().getName().equals("toString"))return "Unit-test lab state";
      Object[] arguments=call.getArguments();String sql=(String)arguments[0];
      if(call.getMethod().getName().equals("queryForList"))return store.list(sql,arguments);
      if(call.getMethod().getName().equals("queryForMap"))return store.map(sql,arguments);
      if(call.getMethod().getName().equals("queryForObject"))return store.object(sql,arguments);
      if(call.getMethod().getName().equals("update"))return store.update(sql,arguments);
      throw new AssertionError("Unexpected JDBC method: "+call.getMethod().getName());
    });
    labs=new LabService(db,security,config,provider,json,transactions,new LabDataAiPolicy(false),new LabIotPolicy(false),new LabAdapterPolicy());
  }

  @Test void validRequestSendsOutsideTransactionAndCachesTheExactResult(){
    delivery=command->{
      assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),"Provider I/O must not retain the admission or authority locks");
      assertEquals(1,store.requests.size(),"The stable command must be committed before sending");
      assertEquals(store.onlyCommand(),store.run.get("request_delivery_command"));
      assertTrue(((Timestamp)store.run.get("request_delivery_until")).toInstant().isAfter(store.now.plusMillis(config.timeoutMs)));
      return result(command,"RESULT");
    };
    var first=request("valid-request");assertEquals("Bounded synthetic result",first.output());
    assertEquals(first,request("valid-request"));assertEquals(1,sent.size());
    assertEquals(1,store.completed);assertNull(store.run.get("request_delivery_command"));assertNull(store.run.get("request_delivery_until"));
    assertEquals("IDEMPOTENCY_CONFLICT",assertThrows(ApiError.class,()->labs.request(store.id,input("Changed"),"valid-request",browser)).code);
  }

  @Test void stopCommitsWhileProviderWaitsAndLateResultCannotReopenTheRun()throws Exception{
    var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
    delivery=command->{entered.countDown();await(release);return result(command,"RESULT");};
    try{
      var pending=pool.submit(()->assertThrows(ApiError.class,()->request("cancel-in-flight")));
      assertTrue(entered.await(2,TimeUnit.SECONDS));
      var stopped=pool.submit(()->labs.stop(store.id,browser));
      assertEquals("CANCELLING",stopped.get(1,TimeUnit.SECONDS).status());
      assertEquals(2L,store.run.get("lease_generation"));assertEquals(1,store.stops);assertNull(store.run.get("cleanup_confirmed_at"));
      release.countDown();assertEquals("LAB_LEASE_STALE",pending.get(2,TimeUnit.SECONDS).code);
      assertNull(store.requests.get(store.onlyCommand()).get("response"));assertEquals(0,store.completed);
    }finally{release.countDown();pool.shutdownNow();}
  }

  @Test void currentSourceAndSessionRevocationAreRecheckedAfterProviderReply()throws Exception{
    for(boolean revokeSource:List.of(true,false)){
      store.sourceEligible=true;store.sessionActive=true;
      var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
      delivery=command->{entered.countDown();await(release);return result(command,"RESULT");};
      try{
        var pending=pool.submit(()->assertThrows(ApiError.class,()->request(revokeSource?"source-revocation":"session-revocation")));
        assertTrue(entered.await(2,TimeUnit.SECONDS));
        pool.submit(()->transactions.mutate(()->{if(revokeSource)store.sourceEligible=false;else store.sessionActive=false;})).get(1,TimeUnit.SECONDS);
        release.countDown();assertEquals(revokeSource?"LAB_SOURCE_REVOKED":"UNAUTHENTICATED",pending.get(2,TimeUnit.SECONDS).code);
        assertEquals(0,store.completed);assertTrue(store.requests.values().stream().allMatch(row->row.get("response")==null));
        assertNull(store.run.get("request_delivery_command"));
      }finally{release.countDown();pool.shutdownNow();}
    }
  }

  @Test void concurrentRetryCannotSendTheSameCommittedCommandTwice()throws Exception{
    var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
    delivery=command->{entered.countDown();await(release);return result(command,"RESULT");};
    try{
      var pending=pool.submit(()->request("same-command-retry"));assertTrue(entered.await(2,TimeUnit.SECONDS));
      var duplicate=pool.submit(()->assertThrows(ApiError.class,()->request("same-command-retry")));
      assertEquals("LAB_REQUEST_IN_PROGRESS",duplicate.get(1,TimeUnit.SECONDS).code);
      var different=pool.submit(()->assertThrows(ApiError.class,()->request("other-command-same-run")));
      assertEquals("LAB_REQUEST_IN_PROGRESS",different.get(1,TimeUnit.SECONDS).code);
      assertEquals(1,sent.size());assertEquals(1,store.requests.size());
      release.countDown();assertEquals(pending.get(2,TimeUnit.SECONDS),request("same-command-retry"));
      assertEquals(1,sent.size());
    }finally{release.countDown();pool.shutdownNow();}
  }

  @Test void lostReplyReleasesClaimButRetryKeepsItsCommittedIdentity(){
    var sends=new AtomicInteger();var identities=new ArrayList<UUID>();
    delivery=command->{
      identities.add(command.commandId());
      if(sends.getAndIncrement()==0)throw new ApiError(502,"LAB_PROVIDER_UNCONFIRMED","Synthetic lost response.");
      return result(command,"RESULT");
    };
    assertEquals("LAB_PROVIDER_UNCONFIRMED",assertThrows(ApiError.class,()->request("lost-response-retry")).code);
    assertNull(store.run.get("request_delivery_command"));
    var retried=request("lost-response-retry");assertEquals(List.of(retried.commandId(),retried.commandId()),identities);
    assertEquals(1,store.requests.size());assertEquals(1,store.completed);
  }

  @Test void operatorAdmissionRevocationDuringDeliverySuppressesTheResult(){
    delivery=command->{ReflectionTestUtils.setField(config,"requested",false);return result(command,"RESULT");};
    assertEquals("LAB_LEASE_STALE",assertThrows(ApiError.class,()->request("operator-revocation")).code);
    assertNull(store.requests.get(store.onlyCommand()).get("response"));assertEquals(0,store.completed);assertNull(store.run.get("request_delivery_command"));
  }

  @Test void expiredClaimCanBeReplacedButOldCompletionCannotClearNewSameCommandClaim()throws Exception{
    var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newSingleThreadExecutor();
    delivery=command->{entered.countDown();await(release);return result(command,"RESULT");};
    try{
      var old=pool.submit(()->assertThrows(ApiError.class,()->request("expired-claim-retry")));assertTrue(entered.await(2,TimeUnit.SECONDS));
      UUID command=store.onlyCommand();Instant next=store.now.plusSeconds(10);
      transactions.mutate(()->{store.now=next;store.run.put("request_delivery_command",command);store.run.put("request_delivery_until",Timestamp.from(next.plusSeconds(2)));});
      release.countDown();assertEquals("LAB_REQUEST_SUPERSEDED",old.get(2,TimeUnit.SECONDS).code);
      assertEquals(command,store.run.get("request_delivery_command"));assertEquals(Timestamp.from(next.plusSeconds(2)),store.run.get("request_delivery_until"));
      assertNull(store.requests.get(command).get("response"));assertEquals(0,store.completed);
      store.now=next.plusSeconds(3);delivery=commandValue->result(commandValue,"RESULT");
      assertEquals(command,request("expired-claim-retry").commandId());assertEquals(2,sent.size());
    }finally{release.countDown();pool.shutdownNow();}
  }

  @Test void claimClockIsReadAfterLocksRatherThanAtTransactionStart(){
    store.run.put("request_delivery_command",UUID.randomUUID());
    store.run.put("request_delivery_until",Timestamp.from(store.now.plusSeconds(1)));
    store.afterRunLock=()->store.now=store.now.plusSeconds(2);
    assertNotNull(request("claim-after-lock-wait"));assertTrue(store.clockReads>0);
    assertEquals(1,sent.size());
  }

  @Test void confirmedFailureRemainsRecordedWhenTheSameCommandLaterSucceeds(){
    delivery=command->result(command,"FAILED");
    assertEquals("LAB_SCENARIO_FAILED",assertThrows(ApiError.class,()->request("confirmed-failure-retry")).code);
    UUID command=store.onlyCommand();assertEquals(true,store.requests.get(command).get("confirmed_failure"));assertNull(store.run.get("request_delivery_command"));
    delivery=commandValue->result(commandValue,"RESULT");
    assertEquals(command,request("confirmed-failure-retry").commandId());assertEquals(true,store.requests.get(command).get("confirmed_failure"));
  }

  private LabResponses.RequestResult request(String key){return labs.request(store.id,input("Hello"),key,browser);}
  private static void await(CountDownLatch latch){try{assertTrue(latch.await(5,TimeUnit.SECONDS));}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new AssertionError(interrupted);}}
  private static String sign(java.security.PrivateKey key,String payload)throws Exception{var signature=Signature.getInstance("Ed25519");signature.initSign(key);signature.update(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));return Base64.getEncoder().encodeToString(signature.sign());}
  private static byte[] body(HttpRequest request){var bytes=new ByteArrayOutputStream();var completed=new CompletableFuture<byte[]>();request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>(){
    @Override public void onSubscribe(Flow.Subscription subscription){subscription.request(Long.MAX_VALUE);}
    @Override public void onNext(ByteBuffer buffer){byte[] part=new byte[buffer.remaining()];buffer.get(part);bytes.writeBytes(part);}
    @Override public void onError(Throwable failure){completed.completeExceptionally(failure);}
    @Override public void onComplete(){completed.complete(bytes.toByteArray());}
  });return completed.join();}
  private LabRequests.Request input(String message){return new LabRequests.Request("echo",LabRequests.Inputs.from(Map.of("message",message)));}
  private LabProviderClient.Result result(LabProviderClient.Command command,String state){return new LabProviderClient.Result(command.runId(),command.leaseGeneration(),command.operatorEpoch(),command.commandId(),command.manifestSha256(),command.imageDigest(),command.expiresAt(),state,true,true,true,"Bounded synthetic result","Synthetic unit result");}
  private LabManifest manifest(){return new LabManifest("unit-protocol",store.component,1,"a".repeat(64),"sha256:"+"b".repeat(64),"c".repeat(64),"d".repeat(64),"c".repeat(64),"getlancer-lab-v1","Unit protocol","Synthetic unit scenario without remote execution.","Protocol","Fixture","Unit state transitions only; no real provider evidence.",64,1000,Instant.now().plusSeconds(3600),"e".repeat(64),List.of(new LabManifest.Scenario("request","Unit scenario","Bounded synthetic scenario operation.",List.of(new LabManifest.Input("message","Message","TEXT",true,120,0,0,List.of())),List.of(new LabManifest.Operation("echo","Echo unit input")))));}

  /** Serializes fake transactions, with rollback snapshots; it models lock ownership, not PostgreSQL. */
  private final class Transactions implements PlatformTransactionManager {
    private final ReentrantLock lock=new ReentrantLock();private final ThreadLocal<Runnable> rollback=new ThreadLocal<>();
    @Override public TransactionStatus getTransaction(TransactionDefinition definition){lock.lock();rollback.set(store.snapshot());TransactionSynchronizationManager.setActualTransactionActive(true);return new SimpleTransactionStatus();}
    @Override public void commit(TransactionStatus status){finish();}
    @Override public void rollback(TransactionStatus status){rollback.get().run();finish();}
    private void finish(){rollback.remove();TransactionSynchronizationManager.setActualTransactionActive(false);lock.unlock();}
    void mutate(Runnable change){lock.lock();try{change.run();}finally{lock.unlock();}}
  }

  /** Small stateful JDBC double; every unexpected statement fails instead of silently succeeding. */
  private static final class Store {
    final UUID id=UUID.randomUUID(),owner=UUID.randomUUID(),sourceOwner=UUID.randomUUID(),component=UUID.randomUUID(),epoch=UUID.randomUUID();
    final Map<String,Object> run=new HashMap<>();final Map<UUID,Map<String,Object>> requests=new HashMap<>();Map<String,Object> manifest;
    Instant now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);boolean sourceEligible=true,sessionActive=true;int completed,stops,clockReads;Runnable afterRunLock=()->{};
    Store(){run.putAll(Map.of("id",id,"owner_id",owner,"manifest_id","unit-protocol","scenario_id","request","manifest_sha256","a".repeat(64),"image_digest","sha256:"+"b".repeat(64),"operator_epoch",epoch,"lease_generation",1L,"status","RUNNING","memory_mib",64));run.put("requested_at",Timestamp.from(now));run.put("expires_at",Timestamp.from(now.plusSeconds(300)));run.put("last_activity_at",Timestamp.from(now));run.put("healthy_at",Timestamp.from(now));run.put("cleanup_confirmed_at",null);run.put("request_delivery_command",null);run.put("request_delivery_until",null);run.put("reason","Synthetic running unit");}
    UUID onlyCommand(){assertEquals(1,requests.size());return requests.keySet().iterator().next();}
    Map<String,Object> settings(){return new HashMap<>(Map.of("id",1,"paused",false,"pause_reason","","observed_epoch",epoch,"updated_at",Timestamp.from(now)));}
    Object[] parameters(Object[] arguments,int start){if(arguments.length==start+1&&arguments[start] instanceof Object[] nested)return nested;return java.util.Arrays.copyOfRange(arguments,start,arguments.length);}
    Object list(String sql,Object[] arguments){Object[] values=parameters(arguments,arguments.length>1&&arguments[1] instanceof Class<?>?2:1);
      if(sql.startsWith("SELECT owner_id FROM lab_runs"))return List.of(owner);
      if(sql.startsWith("SELECT id,owner_id,")){afterRunLock.run();afterRunLock=()->{};return List.of(new HashMap<>(run));}
      if(sql.contains(" FROM lab_requests WHERE run_id=?"))return requests.values().stream().filter(row->row.get("idempotency_key").equals(values[1])).map(HashMap::new).toList();
      if(sql.contains(" FROM lab_manifests "))return List.of(manifest);
      if(sql.startsWith("SELECT owner_id FROM component_entries"))return List.of(sourceOwner);
      if(sql.startsWith("SELECT c.owner_id,c.slug"))return sourceEligible?List.of(Map.of("owner_id",sourceOwner,"slug","synthetic-unit")):List.of();
      if(sql.startsWith("SELECT id FROM users")&&sql.contains("account_status"))return List.of(owner);
      if(sql.contains(" FROM users ")||sql.contains(" FROM sessions ")||sql.contains(" FROM user_roles ")||sql.contains(" FROM developer_profiles "))return List.of();
      throw new AssertionError("Unexpected queryForList: "+sql);
    }
    Object map(String sql,Object[] arguments){Object[] values=parameters(arguments,1);
      if(sql.contains(" FROM lab_runtime_settings "))return settings();
      if(sql.contains(" FROM lab_requests WHERE command_id=?"))return new HashMap<>(requests.get(values[0]));
      throw new AssertionError("Unexpected queryForMap: "+sql);
    }
    Object object(String sql,Object[] arguments){Object[] values=parameters(arguments,2);
      if(sql.equals("SELECT clock_timestamp()")){clockReads++;return Timestamp.from(now);}
      if(sql.startsWith("SELECT observed_epoch"))return epoch;
      if(sql.startsWith("SELECT lease_generation FROM lab_requests"))return requests.get(values[0]).get("lease_generation");
      throw new AssertionError("Unexpected queryForObject: "+sql);
    }
    int update(String sql,Object[] arguments){Object[] values=parameters(arguments,1);
      if(sql.startsWith("INSERT INTO lab_requests")){var row=new HashMap<String,Object>();String[] names={"command_id","run_id","idempotency_key","request_hash","lease_generation","operation_id","inputs"};for(int i=0;i<names.length;i++)row.put(names[i],values[i]);row.put("response",null);row.put("created_at",Timestamp.from(now));row.put("confirmed_failure",false);requests.put((UUID)values[0],row);return 1;}
      if(sql.startsWith("UPDATE lab_runs SET request_delivery_command=?")){run.put("request_delivery_command",values[0]);run.put("request_delivery_until",values[1]);return 1;}
      if(sql.startsWith("UPDATE lab_runs SET request_delivery_command=NULL")){if(java.util.Objects.equals(run.get("request_delivery_command"),values[1])&&java.util.Objects.equals(run.get("request_delivery_until"),values[2])){run.put("request_delivery_command",null);run.put("request_delivery_until",null);return 1;}return 0;}
      if(sql.startsWith("UPDATE lab_requests SET response=")){requests.get(values[1]).put("response",values[0]);return 1;}
      if(sql.startsWith("UPDATE lab_requests SET confirmed_failure=true")){requests.get(values[0]).put("confirmed_failure",true);return 1;}
      if(sql.startsWith("UPDATE lab_runs SET last_activity_at=")){run.put("last_activity_at",Timestamp.from(now));return 1;}
      if(sql.startsWith("UPDATE lab_runs SET status='CANCELLING'")){run.put("status","CANCELLING");run.put("lease_generation",values[0]);run.put("reason",values[1]);return 1;}
      if(sql.startsWith("INSERT INTO lab_events")){if(values[1].equals("REQUEST_COMPLETED"))completed++;return 1;}
      if(sql.startsWith("INSERT INTO lab_outbox")){if(values[3].equals("STOP"))stops++;return 1;}
      throw new AssertionError("Unexpected update: "+sql);
    }
    Runnable snapshot(){var beforeRun=new HashMap<>(run);var beforeRequests=new HashMap<UUID,Map<String,Object>>();requests.forEach((key,value)->beforeRequests.put(key,new HashMap<>(value)));int beforeCompleted=completed,beforeStops=stops;return ()->{run.clear();run.putAll(beforeRun);requests.clear();requests.putAll(beforeRequests);completed=beforeCompleted;stops=beforeStops;};}
  }
}
