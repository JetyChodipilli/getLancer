package com.getlancer.labs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.dto.LabRequests;
import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Private durable reservations, commands and replay. Database locks serialize all resource admission. */
@Service
public class LabService {
  public static final long DAILY_BUDGET_MICROS=50_000_000L, MONTHLY_BUDGET_MICROS=1_000_000_000L;
  private final JdbcTemplate db;private final Security security;private final LabConfiguration config;
  private final LabProviderClient provider;private final ObjectMapper json;private final TransactionTemplate transaction;private final LabDataAiPolicy dataAi;private final LabIotPolicy iot;private final LabAdapterPolicy adapters;
  public LabService(JdbcTemplate db,Security security,LabConfiguration config,LabProviderClient provider,ObjectMapper json,PlatformTransactionManager manager,LabDataAiPolicy dataAi,LabIotPolicy iot,LabAdapterPolicy adapters){
    this.db=db;this.security=security;this.config=config;this.provider=provider;this.json=json;this.transaction=new TransactionTemplate(manager);this.dataAi=dataAi;this.iot=iot;this.adapters=adapters;
  }
  private <T>T tx(java.util.function.Supplier<T> work){return transaction.execute(status->work.get());}
  // ponytail: one admission lock for ten runs; partition only if measured throughput requires it.
  private void readLock(){db.queryForList("SELECT id FROM lab_runtime_settings WHERE id=1 FOR UPDATE");}
  private void syncEpoch(){tx(()->settings());}
  @EventListener(ApplicationReadyEvent.class) public void initializeEpoch(){syncEpoch();}
  private Map<String,Object> settings(){var settings=db.queryForMap("SELECT id,paused,pause_reason,observed_epoch,updated_at FROM lab_runtime_settings WHERE id=1 FOR UPDATE");
    if(config.epoch!=null&&!config.epoch.equals(settings.get("observed_epoch"))){
      boolean restored=settings.get("observed_epoch")!=null;
      db.update("UPDATE lab_runtime_settings SET observed_epoch=?,paused=CASE WHEN ? THEN true ELSE paused END,pause_reason=CASE WHEN ? THEN 'Operator epoch changed; old leases are quarantined until confirmed cleanup.' ELSE pause_reason END,updated_at=now() WHERE id=1",config.epoch,restored,restored);
      settings=db.queryForMap("SELECT id,paused,pause_reason,observed_epoch,updated_at FROM lab_runtime_settings WHERE id=1");
    }return settings;
  }
  private UUID actor(HttpServletRequest request,boolean admin){UUID actor=admin?security.admin(request):security.user(request);
    db.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE",actor);db.queryForList("SELECT user_id FROM sessions WHERE user_id=? FOR UPDATE",actor);db.queryForList("SELECT user_id FROM user_roles WHERE user_id=? FOR UPDATE",actor);
    UUID fresh=admin?security.admin(request):security.user(request);if(!actor.equals(fresh))throw new ApiError(401,"UNAUTHENTICATED","Please log in again.");
    if(admin&&!security.loadPrincipal(request).recentMfa())throw new ApiError(403,"FORBIDDEN","Recent administrator MFA is required.");return actor;
  }
  private LabManifest manifest(String id){return manifest(id,true);}
  private LabManifest manifest(String id,boolean current){var rows=db.queryForList("SELECT id,payload_text,payload_sha256,signature,revoked_at,created_at,execution_approved FROM lab_manifests WHERE id=? AND revoked_at IS NULL FOR SHARE",id);if(rows.isEmpty())throw absent();
    var row=rows.get(0);if(current&&!Boolean.TRUE.equals(row.get("execution_approved")))throw new ApiError(503,"LAB_MANIFEST_SOURCE_ONLY","This signed manifest is registered for source inspection only.");var manifest=current?config.manifest((String)row.get("payload_text"),(String)row.get("payload_sha256"),(String)row.get("signature"),json):config.sourceManifest((String)row.get("payload_text"),(String)row.get("payload_sha256"),(String)row.get("signature"),json);if(!manifest.id().equals(id))throw absent();return manifest;
  }
  private String source(LabManifest manifest,boolean lock){
    if(lock){var ownerRows=db.queryForList("SELECT owner_id FROM component_entries WHERE id=?",UUID.class,manifest.componentId());if(ownerRows.isEmpty())throw absent();UUID owner=ownerRows.get(0);
      db.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE",owner);db.queryForList("SELECT user_id FROM user_roles WHERE user_id=? FOR UPDATE",owner);db.queryForList("SELECT user_id FROM developer_profiles WHERE user_id=? FOR UPDATE",owner);
    }

    var rows=db.queryForList("SELECT c.owner_id,c.slug FROM component_entries c JOIN component_releases cr ON cr.component_id=c.id AND cr.revision=? JOIN users u ON u.id=c.owner_id JOIN developer_profiles p ON p.user_id=c.owner_id"
      +" WHERE c.id=? AND c.status='ACTIVE' AND c.withdrawn_at IS NULL AND cr.source->>'kind'='BACKEND' AND cr.revision=(SELECT max(latest.revision) FROM component_releases latest WHERE latest.component_id=c.id)"
      +" AND cr.source_sha256=? AND cr.source->>'archiveSha256'=? AND cr.source->>'manifestSha256'=?"
      +" AND u.account_status='ACTIVE' AND u.email_verified_at IS NOT NULL AND p.approval_status='APPROVED'"
      +" AND EXISTS(SELECT 1 FROM user_roles r WHERE r.user_id=u.id AND r.role='DEVELOPER')"+(lock?" FOR UPDATE OF c,u,p":""),
      manifest.revision(),manifest.componentId(),manifest.sourceSha256(),manifest.archiveSha256(),manifest.manifestSha256());
    if(rows.isEmpty())throw new ApiError(403,"LAB_SOURCE_REVOKED","This exact reviewed backend source is no longer eligible for execution.");
    if(lock){db.queryForList("SELECT user_id FROM user_roles WHERE user_id=? FOR UPDATE",rows.get(0).get("owner_id"));if(!security.role((UUID)rows.get(0).get("owner_id"),"DEVELOPER"))throw new ApiError(403,"LAB_SOURCE_REVOKED","The source owner no longer has publication authority.");}return "/components/"+rows.get(0).get("slug");
  }
  private LabConfiguration.Runtime runtime(){var available=config.projection();if(!available.enabled())return available;UUID observed=db.queryForObject("SELECT observed_epoch FROM lab_runtime_settings WHERE id=1",UUID.class);if(!Objects.equals(config.epoch,observed))return new LabConfiguration.Runtime(false,"The operator epoch changed; admission and old routes are closed pending quarantine.");Boolean paused=db.queryForObject("SELECT paused FROM lab_runtime_settings WHERE id=1",Boolean.class);return Boolean.TRUE.equals(paused)?new LabConfiguration.Runtime(false,"Lab admissions are paused. Reviewed source and setup remain free."):available;}
  public LabConfiguration.Runtime runtimeAvailability(){return runtime();}
  public LabResponses.Catalogue catalogue(){var items=new java.util.ArrayList<LabResponses.Manifest>();
    for(String id:db.queryForList("SELECT id FROM lab_manifests WHERE revoked_at IS NULL ORDER BY id LIMIT 100",String.class))try{
      var manifest=manifest(id,false);if(!dataAi.visible(manifest))continue;String sourceUrl=source(manifest,false);var available=availability(manifest);
      items.add(new LabResponses.Manifest(manifest.id(),manifest.title(),manifest.summary(),manifest.language(),manifest.framework(),sourceUrl,manifest.setup(),available.enabled()?"Isolated provider":"Source only",manifest.scenarios(),manifest.adapter(),available,manifest.sourceSha256()));
    }catch(ApiError unavailable){/* Unsigned/revoked manifests or currently ineligible sources are not public. */}
    return new LabResponses.Catalogue(List.copyOf(items),runtime());
  }
  private LabConfiguration.Runtime availability(LabManifest manifest){
    try{manifest(manifest.id());for(var scenario:manifest.scenarios())requirePolicies(manifest,scenario.id(),true);return runtime();}
    catch(ApiError unavailable){return new LabConfiguration.Runtime(false,unavailable.getMessage());}
  }
  private String requirePolicies(LabManifest manifest,String scenario,boolean admission){
    String kind=dataAi.require(manifest,scenario,admission),iotKind=iot.require(manifest,scenario,admission);adapters.require(manifest,scenario,admission);
    return iotKind.equals("GENERAL")?kind:iotKind;
  }
  public LabResponses.Quota quota(HttpServletRequest request){return tx(()->{readLock();UUID owner=actor(request,false);return quota(owner);});}
  private LabResponses.Quota quota(UUID owner){int used=db.queryForObject("SELECT count(*) FROM lab_runs WHERE owner_id=? AND quota_counted AND requested_at>=date_trunc('day',now() AT TIME ZONE 'UTC') AT TIME ZONE 'UTC'",Integer.class,owner);
    var active=db.queryForList("SELECT id FROM lab_runs WHERE owner_id=? AND cleanup_confirmed_at IS NULL LIMIT 1",UUID.class,owner);
    Instant reset=Instant.now().atZone(ZoneOffset.UTC).toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();int limit=limits().dailyRunLimit();return new LabResponses.Quota(limit,Math.max(0,limit-used),reset,active.isEmpty()?null:active.get(0),runtime());
  }
  public LabResponses.Run start(LabRequests.Start input,String key,HttpServletRequest request){key(key);syncEpoch();return tx(()->{
    var settings=settings();UUID owner=actor(request,false);String hash=hash(input);var old=db.queryForList("SELECT id,owner_id,manifest_id,scenario_id,request_hash,idempotency_key,inputs,manifest_sha256,image_digest,operator_epoch,lease_generation,status,requested_at,expires_at,last_activity_at,healthy_at,cleanup_confirmed_at,reason,attention,memory_mib,cost_micros,quota_counted FROM lab_runs WHERE owner_id=? AND idempotency_key=? FOR UPDATE",owner,key);
    if(!old.isEmpty()){if(!hash.equals(old.get(0).get("request_hash")))throw idempotency();reconcileAuthority(old.get(0));return projection(row((UUID)old.get(0).get("id")));}
    config.ready();if(Boolean.TRUE.equals(settings.get("paused")))throw new ApiError(503,"LAB_PAUSED","New lab admissions are paused. Source and setup remain free.");
    LabManifest manifest=manifest(input.manifestId());source(manifest,true);var scenario=manifest.scenario(input.scenarioId());String resourceClass=requirePolicies(manifest,scenario.id(),true);UUID id=UUID.randomUUID();Map<String,String> inputs=iot.inputs(manifest,manifest.inputs(scenario,input.inputs().values()),id);
    var quota=quota(owner);if(quota.remaining()<1)throw new ApiError(429,"LAB_QUOTA_EXHAUSTED","Your current daily runtime reservations are used. Source and setup remain free.");
    if(quota.activeRunId()!=null)throw new ApiError(429,"LAB_ACTIVE_RUN","Finish cleanup of your current run before starting another.");
    var resources=resources();var limits=limits();if(number(resources,"active")>=limits.maxActiveRuns()||number(resources,"memory")+manifest.memoryMiB()>limits.maxMemoryMiB()||number(resources,"daily")+manifest.maxCostMicros()>limits.dailyBudgetMicros()||number(resources,"monthly")+manifest.maxCostMicros()>limits.monthlyBudgetMicros())
      throw new ApiError(429,"LAB_CAPACITY","The isolated runtime has reached its reserved capacity or budget. Source and setup remain free.");
    if(!resourceClass.equals("GENERAL")){
      var reserved=classResources(resourceClass);boolean iotClass=resourceClass.equals(LabIotPolicy.RESOURCE_CLASS);
      if(number(reserved,"active")>=(iotClass?LabIotPolicy.CLASS_RUNS:LabDataAiPolicy.CLASS_RUNS)||number(reserved,"memory")+manifest.memoryMiB()>(iotClass?LabIotPolicy.CLASS_MEMORY_MIB:LabDataAiPolicy.CLASS_MEMORY_MIB))
        throw new ApiError(429,iotClass?"IOT_CAPACITY":"DATA_AI_CAPACITY","This execution class has reached its independent reserved capacity. Source and setup remain free.");
    }
    Instant expiry=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS).plusSeconds(300);
    String manifestHash=db.queryForObject("SELECT payload_sha256 FROM lab_manifests WHERE id=?",String.class,manifest.id());
    db.update("INSERT INTO lab_runs(id,owner_id,manifest_id,scenario_id,request_hash,idempotency_key,inputs,manifest_sha256,image_digest,operator_epoch,status,expires_at,memory_mib,cost_micros) VALUES(?,?,?,?,?,?,?::jsonb,?,?,?,'QUEUED',?,?,?)",id,owner,manifest.id(),scenario.id(),hash,key,encode(inputs),manifestHash,manifest.imageDigest(),config.epoch,Timestamp.from(expiry),manifest.memoryMiB(),manifest.maxCostMicros());
    event(id,"QUEUED","QUEUED","Waiting for the isolated provider.");outbox(id,1,"START");return projection(row(id));
  });}
  private Map<String,Object> row(UUID id){var owner=db.queryForList("SELECT owner_id FROM lab_runs WHERE id=?",UUID.class,id);if(owner.isEmpty())throw absent();db.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE",owner.get(0));var rows=db.queryForList("SELECT id,owner_id,manifest_id,scenario_id,request_hash,idempotency_key,inputs,manifest_sha256,image_digest,operator_epoch,lease_generation,status,requested_at,expires_at,last_activity_at,healthy_at,cleanup_confirmed_at,reason,attention,memory_mib,cost_micros,quota_counted,request_delivery_command,request_delivery_until,observation_sequence FROM lab_runs WHERE id=? FOR UPDATE",id);if(rows.isEmpty())throw absent();return rows.get(0);}
  private Map<String,Object> owned(UUID id,UUID actor,boolean admin){var row=row(id);if(!admin&&!actor.equals(row.get("owner_id")))throw absent();return row;}
  private void current(Map<String,Object> run){
    var activeOwner=db.queryForList("SELECT id FROM users WHERE id=? AND account_status='ACTIVE' FOR UPDATE",UUID.class,run.get("owner_id"));if(activeOwner.isEmpty())throw new ApiError(403,"LAB_ACCOUNT_REVOKED","This account is no longer eligible for runtime execution.");

    if(!config.projection().enabled()||!Objects.equals(config.epoch,run.get("operator_epoch"))||!Objects.equals(config.epoch,db.queryForObject("SELECT observed_epoch FROM lab_runtime_settings WHERE id=1",UUID.class)))throw new ApiError(403,"LAB_LEASE_STALE","Runtime admission or the operator epoch has changed. This lease is closed.");
    if(!instant(run,"expires_at").isAfter(Instant.now()))throw new ApiError(410,"LAB_EXPIRED","This run has expired.");
    var manifest=manifest((String)run.get("manifest_id"));requirePolicies(manifest,(String)run.get("scenario_id"),false);source(manifest,true);
  }
  public LabResponses.Run detail(UUID id,HttpServletRequest request){return tx(()->{readLock();UUID owner=actor(request,false);boolean admin=security.role(owner,"ADMIN")&&security.loadPrincipal(request).recentMfa();var run=owned(id,owner,admin);return observedProjection(run);});}
  private void reconcileAuthority(Map<String,Object> run){if(run.get("cleanup_confirmed_at")!=null)return;try{current(run);}catch(ApiError revoked){cancel(run,revoked.code.equals("LAB_EXPIRED")?"EXPIRED":"FAILED",revoked.getMessage());}}
  public LabResponses.Run stop(UUID id,HttpServletRequest request){syncEpoch();return tx(()->{settings();UUID owner=actor(request,false);var run=owned(id,owner,false);if(run.get("cleanup_confirmed_at")==null)cancel(run,"CANCELLED","Owner closed the run. Provider cleanup is pending.");return projection(row(id));});}
  private void cancel(Map<String,Object> run,String terminal,String reason){if(run.get("cleanup_confirmed_at")!=null||run.get("status").equals("CANCELLING"))return;
    long lease=number(run,"lease_generation")+1;UUID id=(UUID)run.get("id");
    db.update("UPDATE lab_runs SET status='CANCELLING',lease_generation=?,reason=?,attention=true WHERE id=?",lease,reason,id);
    event(id,terminal,"CANCELLING",reason);outbox(id,lease,"STOP");
  }
  private record RequestIntent(UUID owner,UUID command,LabProviderClient.Command delivery,Instant claimedUntil,LabResponses.RequestResult cached) {}
  public LabResponses.RequestResult request(UUID id,LabRequests.Request input,String key,HttpServletRequest request){key(key);syncEpoch();
    RequestIntent intent=tx(()->{
      settings();UUID owner=actor(request,false);var run=owned(id,owner,false);current(run);if(!run.get("status").equals("RUNNING")||run.get("healthy_at")==null)throw new ApiError(409,"LAB_NOT_RUNNING","Only a currently healthy running lab accepts requests.");
      var manifest=manifest((String)run.get("manifest_id"));var scenario=manifest.scenario((String)run.get("scenario_id"));if(scenario.operations().stream().noneMatch(op->op.id().equals(input.operationId())))throw new ApiError(400,"INVALID_LAB_OPERATION","Choose an operation certified for this scenario.");
      var inputs=iot.inputs(manifest,manifest.inputs(scenario,input.inputs().values()),id);String hash=hash(input);var previous=db.queryForList("SELECT command_id,run_id,idempotency_key,request_hash,lease_generation,operation_id,inputs,response,created_at FROM lab_requests WHERE run_id=? AND idempotency_key=? FOR UPDATE",id,key);UUID command;
      if(!previous.isEmpty()){var existing=previous.get(0);if(!hash.equals(existing.get("request_hash")))throw idempotency();command=(UUID)existing.get("command_id");
        if(number(existing,"lease_generation")!=number(run,"lease_generation"))throw new ApiError(403,"LAB_LEASE_STALE","This request belongs to an earlier closed lease.");
        if(existing.get("response")!=null)return new RequestIntent(owner,command,null,null,decode(existing.get("response").toString(),LabResponses.RequestResult.class));
      }
      else{command=UUID.randomUUID();db.update("INSERT INTO lab_requests(command_id,run_id,idempotency_key,request_hash,lease_generation,operation_id,inputs) VALUES(?,?,?,?,?,?,?::jsonb)",command,id,key,hash,number(run,"lease_generation"),input.operationId(),encode(inputs));}
      // Read the wall clock after all locks: a queued transaction must not revive an expired claim.
      Instant now=db.queryForObject("SELECT clock_timestamp()",Timestamp.class).toInstant();
      if(run.get("request_delivery_until")!=null&&instant(run,"request_delivery_until").isAfter(now))throw new ApiError(409,"LAB_REQUEST_IN_PROGRESS","A scenario operation is already being delivered. Retry with the same request identity.");
      Instant claimedUntil=now.plusMillis((long)config.timeoutMs+1000);
      db.update("UPDATE lab_runs SET request_delivery_command=?,request_delivery_until=? WHERE id=?",command,Timestamp.from(claimedUntil),id);
      return new RequestIntent(owner,command,command(run,command,"REQUEST",input.operationId(),inputs),claimedUntil,null);
    });
    if(intent.cached()!=null)return intent.cached();
    // The committed claim bounds retries without holding admission, account or source locks across provider I/O.
    try{var result=provider.send(intent.delivery());if(result.state().equals("FAILED"))throw new ApiError(502,"LAB_SCENARIO_FAILED","The isolated provider confirmed failure of this scenario operation.");if(!result.state().equals("RESULT"))throw new ApiError(502,"LAB_REQUEST_UNCONFIRMED","The isolated provider has not confirmed this request.");
      return tx(()->{settings();UUID owner=actor(request,false);var run=owned(id,owner,false);current(run);
      if(!run.get("status").equals("RUNNING")||number(run,"lease_generation")!=intent.delivery().leaseGeneration())throw new ApiError(403,"LAB_LEASE_STALE","This request belongs to an earlier closed lease.");
      if(!requestClaim(run,intent))throw new ApiError(409,"LAB_REQUEST_SUPERSEDED","A newer delivery attempt owns this request. Retry with the same request identity.");
      var signed=manifest((String)run.get("manifest_id"));
      var observation=LabObservations.parse(result.output(),id,signed.sourceSha256(),(String)run.get("scenario_id"),number(run,"observation_sequence"),json);
      var safe=new LabResponses.RequestResult(id,intent.command(),Objects.toString(result.output(),""),Objects.toString(result.summary(),""),observation);
      db.update("UPDATE lab_requests SET response=?::jsonb WHERE command_id=?",encode(safe),intent.command());db.update("UPDATE lab_runs SET last_activity_at=now(),observation_sequence=? WHERE id=?",observation==null?number(run,"observation_sequence"):observation.events().get(observation.events().size()-1).sequence(),id);event(id,"REQUEST_COMPLETED","RUNNING","A certified scenario operation completed.",intent.command());return safe;
    });}catch(ApiError unsafe){if(unsafe.code.equals("LAB_SCENARIO_FAILED")||unsafe.code.equals("LAB_ISOLATION_LOST"))tx(()->{settings();var run=owned(id,intent.owner(),false);
      if(requestClaim(run,intent)&&number(run,"lease_generation")==intent.delivery().leaseGeneration()){
        if(unsafe.code.equals("LAB_SCENARIO_FAILED"))db.update("UPDATE lab_requests SET confirmed_failure=true WHERE command_id=?",intent.command());else cancel(run,"FAILED",unsafe.getMessage());
      }return true;});throw unsafe;
    }finally{tx(()->{settings();db.update("UPDATE lab_runs SET request_delivery_command=NULL,request_delivery_until=NULL WHERE id=? AND request_delivery_command=? AND request_delivery_until=?",id,intent.command(),Timestamp.from(intent.claimedUntil()));return true;});}
  }
  private boolean requestClaim(Map<String,Object> run,RequestIntent intent){return intent.command().equals(run.get("request_delivery_command"))&&run.get("request_delivery_until")!=null&&intent.claimedUntil().equals(instant(run,"request_delivery_until"));}
  public String events(UUID id,String cursor,HttpServletRequest request){long after=0;try{if(cursor!=null){if(!cursor.matches("[0-9]{1,18}"))throw new NumberFormatException();after=Long.parseLong(cursor);}}catch(NumberFormatException invalid){throw new ApiError(400,"INVALID_LAB_CURSOR","Use the sequence of the last received event.");}
    final long sequence=after;return tx(()->{readLock();UUID owner=actor(request,false);var run=owned(id,owner,false);current(run);
      var replay=db.queryForList("SELECT e.run_id,e.sequence,e.event_type,e.status,e.reason,e.recorded_at,q.response FROM lab_events e LEFT JOIN lab_requests q ON q.command_id=e.command_id WHERE e.run_id=? AND e.sequence>? ORDER BY e.sequence LIMIT 100",id,sequence);var body=new StringBuilder();int emittedBytes=0;
      for(var item:replay){actor(request,false);current(run);if(!Objects.equals(owner,row(id).get("owner_id")))throw absent();
        var event=new LabResponses.Event(id,number(item,"sequence"),(String)item.get("event_type"),instant(item,"recorded_at"),new LabResponses.EventData((String)item.get("status"),(String)item.get("reason"),item.get("response")==null?null:decode(item.get("response").toString(),LabResponses.RequestResult.class).observation()));
        String chunk="id: "+event.sequence()+"\nevent: lab\ndata: "+encode(event)+"\n\n";int chunkBytes=chunk.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;if(emittedBytes+chunkBytes>60000)break;emittedBytes+=chunkBytes;body.append(chunk);
      }return body.length()==0?": no newer events\n\n":body.toString();
    });
  }
  public LabResponses.Admin admin(HttpServletRequest request){return tx(()->{readLock();var settings=db.queryForMap("SELECT id,paused,pause_reason,observed_epoch,updated_at FROM lab_runtime_settings WHERE id=1");actor(request,true);var resources=resources();
    return new LabResponses.Admin(runtime(),Boolean.TRUE.equals(settings.get("paused")),(String)settings.get("pause_reason"),count("QUEUED"),count("STARTING"),count("RUNNING"),count("CANCELLING"),db.queryForObject("SELECT count(*) FROM lab_runs WHERE attention",Long.class),number(resources,"active"),number(resources,"memory"),number(resources,"daily"),number(resources,"monthly"),limits(),certifications(),alerts());});}
  public LabResponses.Admin pause(LabRequests.Pause input,HttpServletRequest request){syncEpoch();tx(()->{settings();UUID admin=actor(request,true);if(!input.paused())config.ready();db.update("UPDATE lab_runtime_settings SET paused=?,pause_reason=?,updated_at=now() WHERE id=1",input.paused(),input.reason());db.update("INSERT INTO lab_operator_audit(id,actor_id,action,reason) VALUES(?,?,?,?)",UUID.randomUUID(),admin,input.paused()?"ADMISSIONS_PAUSED":"ADMISSIONS_RESUMED",input.reason());return true;});return admin(request);}
  private LabResponses.Limits limits(){var row=db.queryForMap("SELECT daily_run_limit,max_active_runs,max_memory_mib,daily_budget_micros,monthly_budget_micros FROM lab_runtime_settings WHERE id=1");return new LabResponses.Limits((int)number(row,"daily_run_limit"),(int)number(row,"max_active_runs"),(int)number(row,"max_memory_mib"),number(row,"daily_budget_micros"),number(row,"monthly_budget_micros"));}
  public LabResponses.Admin limits(LabRequests.Limits input,HttpServletRequest request){
    if(input.monthlyBudgetMicros()<input.dailyBudgetMicros())throw new ApiError(400,"INVALID_LAB_LIMITS","The monthly budget must cover at least the daily budget.");
    tx(()->{settings();UUID admin=actor(request,true);var before=limits();
      db.update("UPDATE lab_runtime_settings SET daily_run_limit=?,max_active_runs=?,max_memory_mib=?,daily_budget_micros=?,monthly_budget_micros=?,updated_at=now() WHERE id=1",input.dailyRunLimit(),input.maxActiveRuns(),input.maxMemoryMiB(),input.dailyBudgetMicros(),input.monthlyBudgetMicros());
      audit(admin,"LIMITS_UPDATED",input.reason(),Map.of("before",before,"after",limits()));return true;});return admin(request);
  }
  public LabResponses.Admin certify(LabRequests.Certification input,HttpServletRequest request){tx(()->{settings();UUID admin=actor(request,true);
    if(input.action().equals("REVOKE")){
      var rows=db.queryForList("SELECT payload_sha256 FROM lab_manifests WHERE id=? FOR UPDATE",String.class,input.manifestId());if(rows.isEmpty())throw absent();
      actor(request,true); // Recheck current MFA after the target manifest row wait.
      db.update("UPDATE lab_manifests SET revoked_at=coalesce(revoked_at,now()) WHERE id=?",input.manifestId());audit(admin,"CERTIFICATION_REVOKED",input.reason(),Map.of("manifestId",input.manifestId(),"manifestSha256",rows.get(0)));
    }else{
      if(input.payload()==null||input.signature()==null)throw new ApiError(400,"INVALID_CERTIFICATION","Supply the immutable operator-signed manifest payload and signature.");
      String sha=LabConfiguration.hex(input.payload().getBytes(java.nio.charset.StandardCharsets.UTF_8));boolean publish=input.action().equals("PUBLISH");var manifest=publish?config.manifest(input.payload(),sha,input.signature(),json):config.sourceManifest(input.payload(),sha,input.signature(),json);
      if(!manifest.id().equals(input.manifestId()))throw new ApiError(400,"INVALID_CERTIFICATION","The manifest identity must match its signed payload.");
      if(publish&&(manifest.adapter()==null||manifest.certification()==null))throw new ApiError(400,"INVALID_CERTIFICATION","New certifications require explicit versioned adapter and review attestations.");
      if(publish)for(var scenario:manifest.scenarios())requirePolicies(manifest,scenario.id(),true);source(manifest,true);
      if(!db.queryForList("SELECT id FROM lab_manifests WHERE id=?",String.class,manifest.id()).isEmpty())throw new ApiError(409,"CERTIFICATION_EXISTS","Manifest identities are immutable. Publish a new identity for re-certification.");
      actor(request,true); // Source/publisher locks may have crossed the MFA deadline.
      db.update("INSERT INTO lab_manifests(id,payload_text,payload_sha256,signature,execution_approved) VALUES(?,?,?,?,?)",manifest.id(),input.payload(),sha,input.signature(),publish);
      audit(admin,publish?"CERTIFICATION_PUBLISHED":"SOURCE_REGISTERED",input.reason(),Map.of("manifestId",manifest.id(),"manifestSha256",sha,"executionApproved",publish));
    }return true;});return admin(request);
  }
  private void audit(UUID actor,String action,String reason,Object details){db.update("INSERT INTO lab_operator_audit(id,actor_id,action,reason,details) VALUES(?,?,?,?,?::jsonb)",UUID.randomUUID(),actor,action,reason,encode(details));}
  private List<LabResponses.Certificate> certifications(){var result=new java.util.ArrayList<LabResponses.Certificate>();
    for(var row:db.queryForList("SELECT id,payload_text,payload_sha256,signature,revoked_at FROM lab_manifests ORDER BY created_at DESC,id LIMIT 100"))try{
      var manifest=config.sourceManifest((String)row.get("payload_text"),(String)row.get("payload_sha256"),(String)row.get("signature"),json);
      var availability=availability(manifest);String state=row.get("revoked_at")!=null?"REVOKED":availability.enabled()?"CURRENT":"SOURCE_ONLY";
      result.add(new LabResponses.Certificate(manifest.id(),manifest.title(),manifest.language(),manifest.framework(),manifest.adapter(),manifest.approvedUntil(),(String)row.get("payload_sha256"),state,state.equals("REVOKED")?"Permanently revoked; existing routes fail closed.":availability.reason()));
    }catch(ApiError invalid){/* Invalid signatures never become operator-approved metadata. */}
    return List.copyOf(result);
  }
  private List<LabResponses.Alert> alerts(){var result=new java.util.ArrayList<LabResponses.Alert>();
    long stale=db.queryForObject("SELECT count(*) FROM lab_runtime_settings WHERE id=1 AND (worker_observed_at IS NULL OR worker_observed_at<now()-interval '30 seconds') AND EXISTS(SELECT 1 FROM lab_runs WHERE cleanup_confirmed_at IS NULL)",Long.class);
    long orphan=db.queryForObject("SELECT count(*) FROM lab_runs r WHERE r.cleanup_confirmed_at IS NULL AND (r.status IN('CANCELLED','SUCCEEDED','FAILED','EXPIRED') OR r.status IN('QUEUED','STARTING','CANCELLING') AND NOT EXISTS(SELECT 1 FROM lab_outbox o WHERE o.run_id=r.id AND o.lease_generation=r.lease_generation AND o.delivered_at IS NULL AND o.action=CASE WHEN r.status='CANCELLING' THEN 'STOP' ELSE 'START' END))",Long.class);
    long cleanup=db.queryForObject("SELECT count(*) FROM lab_runs WHERE cleanup_confirmed_at IS NULL AND attention",Long.class);
    if(stale>0)result.add(new LabResponses.Alert("STALE_WORKER",stale,"The worker heartbeat is stale while reservations remain active. Check the runner before changing admission."));
    if(orphan>0)result.add(new LabResponses.Alert("ORPHAN_RESERVATION",orphan,"Reservations have no matching lifecycle delivery or confirmed cleanup. Capacity remains held."));
    if(cleanup>0)result.add(new LabResponses.Alert("CLEANUP_ATTENTION",cleanup,"Provider confirmation is pending. Do not release reserved capacity without confirmed cleanup."));
    return List.copyOf(result);
  }
  /** Bounded scheduler pass; commands remain durable and capacity stays quarantined on uncertainty. */
  public void tick(){
    tx(()->{settings();redactRetainedInputs();for(UUID id:db.queryForList("SELECT id FROM lab_runs WHERE cleanup_confirmed_at IS NULL ORDER BY requested_at LIMIT 100",UUID.class)){
      var run=row(id);if(run.get("status").equals("CANCELLING"))continue;
      Integer owners=db.queryForObject("SELECT count(*) FROM users WHERE id=? AND account_status='ACTIVE'",Integer.class,run.get("owner_id"));
      if(owners==null||owners==0){cancel(run,"FAILED","The account is no longer active.");continue;}
      if(instant(run,"expires_at").isBefore(Instant.now())){cancel(run,"EXPIRED","The five minute runtime limit was reached.");continue;}
      if(run.get("status").equals("RUNNING")&&instant(run,"last_activity_at").isBefore(Instant.now().minusSeconds(90))){cancel(run,"EXPIRED","The ninety second idle cleanup limit was reached.");continue;}
      reconcileAuthority(run);
    }return true;});
    for(int pass=0;pass<10;pass++){
      Map<String,Object> delivery=tx(()->{settings();var rows=db.queryForList("SELECT command_id,run_id,lease_generation,action,delivered_at,next_attempt_at,attempts FROM lab_outbox WHERE delivered_at IS NULL AND next_attempt_at<=now() ORDER BY next_attempt_at LIMIT 1 FOR UPDATE SKIP LOCKED");if(rows.isEmpty())return null;
        var outbox=rows.get(0);var run=row((UUID)outbox.get("run_id"));
        if(number(run,"lease_generation")!=number(outbox,"lease_generation")||run.get("cleanup_confirmed_at")!=null){db.update("UPDATE lab_outbox SET delivered_at=now() WHERE command_id=?",outbox.get("command_id"));return Map.of("skip",true);}
        if(outbox.get("action").equals("START")){try{current(run);}catch(ApiError unavailable){cancel(run,"FAILED",unavailable.getMessage());db.update("UPDATE lab_outbox SET delivered_at=now() WHERE command_id=?",outbox.get("command_id"));return Map.of("skip",true);}
          if(run.get("status").equals("QUEUED")){db.update("UPDATE lab_runs SET status='STARTING',reason='The isolated provider is starting this reservation.' WHERE id=?",run.get("id"));event((UUID)run.get("id"),"STARTING","STARTING","The isolated provider is starting this reservation.");}
        }
        db.update("UPDATE lab_outbox SET attempts=attempts+1,next_attempt_at=now()+interval '10 seconds' WHERE command_id=?",outbox.get("command_id"));
        return Map.of("command",outbox,"run",row((UUID)run.get("id")));
      });
      if(delivery==null)break;if(delivery.containsKey("skip"))continue;
      @SuppressWarnings("unchecked") var run=(Map<String,Object>)delivery.get("run");@SuppressWarnings("unchecked") var outbox=(Map<String,Object>)delivery.get("command");
      LabProviderClient.Result result;
      try{result=provider.send(command(run,(UUID)outbox.get("command_id"),(String)outbox.get("action"),null,outbox.get("action").equals("STOP")?Map.of():storedInputs(run.get("inputs"))));}
      catch(ApiError unavailable){tx(()->{settings();var fresh=row((UUID)run.get("id"));if(fresh.get("cleanup_confirmed_at")==null){if(unavailable.code.equals("LAB_ISOLATION_LOST"))cancel(fresh,"FAILED",unavailable.getMessage());else db.update("UPDATE lab_runs SET attention=true,reason='Provider confirmation is pending; capacity remains reserved.' WHERE id=?",run.get("id"));}return true;});continue;}
      tx(()->{settings();var fresh=row((UUID)run.get("id"));if(db.queryForObject("SELECT delivered_at IS NOT NULL FROM lab_outbox WHERE command_id=?",Boolean.class,outbox.get("command_id")))return true;if(number(fresh,"lease_generation")!=result.leaseGeneration()||fresh.get("cleanup_confirmed_at")!=null)return true;
        if(outbox.get("action").equals("STOP")&&result.state().equals("CLEANED")){
          String terminal=db.queryForObject("SELECT event_type FROM lab_events WHERE run_id=? AND status='CANCELLING' ORDER BY sequence DESC LIMIT 1",String.class,run.get("id"));if(!List.of("CANCELLED","EXPIRED","FAILED").contains(terminal))terminal="CANCELLED";
          db.update("UPDATE lab_runs SET status=?,cleanup_confirmed_at=now(),quota_counted=(healthy_at IS NOT NULL OR ?),attention=false,reason='The isolated provider confirmed cleanup.' WHERE id=?",terminal,Boolean.TRUE.equals(result.everHealthy()),run.get("id"));event((UUID)run.get("id"),terminal,terminal,"The isolated provider confirmed cleanup.");if(db.queryForObject("SELECT count(*) FROM deletion_requests WHERE user_id=? AND status='PROFILE_ANONYMIZED'",Integer.class,run.get("owner_id"))>0)redactOwner((UUID)run.get("owner_id"));db.update("UPDATE lab_outbox SET delivered_at=now() WHERE command_id=?",outbox.get("command_id"));
        }else if(outbox.get("action").equals("START")&&result.state().equals("RUNNING")){
          try{current(fresh);}catch(ApiError revoked){cancel(fresh,"FAILED",revoked.getMessage());return true;}
          db.update("UPDATE lab_runs SET status='RUNNING',healthy_at=coalesce(healthy_at,now()),last_activity_at=now(),attention=false,reason='The isolated provider confirmed healthy execution.' WHERE id=?",run.get("id"));event((UUID)run.get("id"),"RUNNING","RUNNING","The isolated provider confirmed healthy execution.");db.update("UPDATE lab_outbox SET delivered_at=now() WHERE command_id=?",outbox.get("command_id"));
        }else if(result.state().equals("FAILED")){cancel(fresh,"FAILED","The isolated provider could not start this run.");}
        return true;
      });
    }
    db.update("UPDATE lab_runtime_settings SET worker_observed_at=now() WHERE id=1");
  }
  private Map<String,String> storedInputs(Object value){try{return json.readValue(value.toString(),new com.fasterxml.jackson.core.type.TypeReference<Map<String,String>>(){});}catch(Exception invalid){throw new ApiError(409,"LAB_RECORD_INVALID","The stored inputs require operator attention.");}}
  /** Internal privacy hook; joins the caller transaction and closes routes before asynchronous cleanup. */
  public void revokeOwner(UUID owner){tx(()->{for(UUID id:db.queryForList("SELECT id FROM lab_runs WHERE owner_id=? AND cleanup_confirmed_at IS NULL ORDER BY requested_at LIMIT 100",UUID.class,owner))cancel(row(id),"CANCELLED","The account requested closure. Isolated provider cleanup is pending.");return true;});}
  /** Only provider-confirmed cleanup permits removal of private input/result bytes. Identity and audit stay. */
  public void redactOwner(UUID owner){tx(()->{db.update("UPDATE lab_requests q SET inputs='{}'::jsonb,response=NULL FROM lab_runs r WHERE q.run_id=r.id AND r.owner_id=? AND r.cleanup_confirmed_at IS NOT NULL",owner);db.update("UPDATE lab_runs SET inputs='{}'::jsonb WHERE owner_id=? AND cleanup_confirmed_at IS NOT NULL",owner);return true;});}
  private void redactRetainedInputs(){for(UUID id:db.queryForList("SELECT r.id FROM lab_runs r WHERE r.cleanup_confirmed_at IS NOT NULL AND ((r.requested_at<now()-interval '24 hours' OR EXISTS(SELECT 1 FROM lab_requests aged WHERE aged.run_id=r.id AND aged.created_at<now()-interval '24 hours')) OR EXISTS(SELECT 1 FROM deletion_requests d WHERE d.user_id=r.owner_id AND d.status='PROFILE_ANONYMIZED')) AND (r.inputs<>'{}'::jsonb OR EXISTS(SELECT 1 FROM lab_requests q WHERE q.run_id=r.id AND (q.inputs<>'{}'::jsonb OR q.response IS NOT NULL))) ORDER BY r.cleanup_confirmed_at LIMIT 100",UUID.class)){
    row(id);db.update("UPDATE lab_requests SET inputs='{}'::jsonb,response=NULL WHERE run_id=?",id);db.update("UPDATE lab_runs SET inputs='{}'::jsonb WHERE id=?",id);
  }}
  private long count(String status){return db.queryForObject("SELECT count(*) FROM lab_runs WHERE status=? AND cleanup_confirmed_at IS NULL",Long.class,status);}
  /** Includes failed/cancelling reservations until confirmed cleanup, under the same admission lock. */
  private Map<String,Object> classResources(String resourceClass){
    if(resourceClass.equals(LabIotPolicy.RESOURCE_CLASS))return db.queryForMap("SELECT count(*) AS active,coalesce(sum(r.memory_mib),0) AS memory FROM lab_runs r JOIN lab_manifests m ON m.id=r.manifest_id WHERE r.cleanup_confirmed_at IS NULL AND (m.payload_text::jsonb->>'resourceClass'=? OR r.scenario_id=?)",resourceClass,LabIotPolicy.SCENARIO);
    String first=resourceClass.equals("DATA_ANALYTICS")?"revenue-summary":"sentiment-inference",second=resourceClass.equals("DATA_ANALYTICS")?"sensor-quality":"equipment-inference";
    return db.queryForMap("SELECT count(*) AS active,coalesce(sum(r.memory_mib),0) AS memory FROM lab_runs r JOIN lab_manifests m ON m.id=r.manifest_id WHERE r.cleanup_confirmed_at IS NULL AND (m.payload_text::jsonb->>'resourceClass'=? OR r.scenario_id IN (?,?))",resourceClass,first,second);
  }
  /** Reservations and stored monetary ceilings share one SQL snapshot without changing caller locks. */
  public Map<String,Object> reservedResources(){return resources();}
  private Map<String,Object> resources(){return db.queryForMap("SELECT count(*) FILTER(WHERE cleanup_confirmed_at IS NULL) AS active,coalesce(sum(memory_mib) FILTER(WHERE cleanup_confirmed_at IS NULL),0) AS memory,coalesce(sum(cost_micros) FILTER(WHERE (greatest(requested_at,coalesce(healthy_at,requested_at),coalesce(cleanup_confirmed_at,requested_at))>=date_trunc('day',now() AT TIME ZONE 'UTC') AT TIME ZONE 'UTC' AND quota_counted) OR cleanup_confirmed_at IS NULL),0) AS daily,coalesce(sum(cost_micros) FILTER(WHERE (greatest(requested_at,coalesce(healthy_at,requested_at),coalesce(cleanup_confirmed_at,requested_at))>=date_trunc('month',now() AT TIME ZONE 'UTC') AT TIME ZONE 'UTC' AND quota_counted) OR cleanup_confirmed_at IS NULL),0) AS monthly,(SELECT daily_budget_micros FROM lab_runtime_settings WHERE id=1) AS daily_budget,(SELECT monthly_budget_micros FROM lab_runtime_settings WHERE id=1) AS monthly_budget FROM lab_runs");}
  /** GET is observation only; watchdog/writes commit route closure and cleanup intent. */
  private LabResponses.Run observedProjection(Map<String,Object> run){if(run.get("cleanup_confirmed_at")!=null)return projection(run);try{current(run);return projection(run);}catch(ApiError unavailable){var safe=new java.util.HashMap<>(run);safe.put("status",unavailable.code.equals("LAB_EXPIRED")?"EXPIRED":"CANCELLING");safe.put("reason",unavailable.getMessage());return projection(safe);}}
  private LabResponses.Run projection(Map<String,Object> run){String status=(String)run.get("status");return new LabResponses.Run((UUID)run.get("id"),(String)run.get("manifest_id"),(String)run.get("scenario_id"),status,"Isolated provider",status.equals("RUNNING")&&run.get("healthy_at")!=null,instant(run,"requested_at"),instant(run,"expires_at"),(String)run.get("reason"),"/api/v1/lab-runs/"+run.get("id")+"/events");}
  private void event(UUID id,String type,String status,String reason){event(id,type,status,reason,null);}
  private void event(UUID id,String type,String status,String reason,UUID command){db.update("INSERT INTO lab_events(run_id,sequence,event_type,status,reason,command_id) SELECT ?,coalesce(max(sequence),0)+1,?,?,?,? FROM lab_events WHERE run_id=?",id,type,status,reason,command,id);
    if(java.util.Set.of("QUEUED","RUNNING","FAILED","EXPIRED","CANCELLED").contains(type))org.slf4j.LoggerFactory.getLogger(LabService.class).info("operation=lab_transition result={}",type);
  }
  private void outbox(UUID id,long generation,String action){db.update("INSERT INTO lab_outbox(command_id,run_id,lease_generation,action) VALUES(?,?,?,?) ON CONFLICT(run_id,lease_generation,action) DO NOTHING",UUID.randomUUID(),id,generation,action);}
  private LabProviderClient.Command command(Map<String,Object> run,UUID command,String action,String operation,Map<String,String> inputs){return new LabProviderClient.Command((UUID)run.get("id"),number(run,"lease_generation"),(UUID)run.get("operator_epoch"),command,(String)run.get("manifest_sha256"),(String)run.get("image_digest"),instant(run,"expires_at"),action,(String)run.get("scenario_id"),operation,inputs,(int)number(run,"memory_mib"));}
  private void key(String key){if(key==null||!key.matches("[A-Za-z0-9._:-]{8,100}"))throw new ApiError(400,"INVALID_IDEMPOTENCY_KEY","Supply a stable request identity between 8 and 100 ASCII characters.");}
  private String hash(Object input){return LabConfiguration.hex(encode(input).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
  private String encode(Object value){try{return json.writer().with(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsString(value);}catch(Exception invalid){throw new ApiError(400,"INVALID_LAB_INPUT","Check the lab input fields.");}}
  private <T>T decode(String value,Class<T> type){try{return json.readValue(value,type);}catch(Exception invalid){throw new ApiError(409,"LAB_RECORD_INVALID","This lab record requires operator attention.");}}
  private static long number(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
  private static Instant instant(Map<String,Object> row,String key){Object value=row.get(key);return value instanceof Timestamp stamp?stamp.toInstant():value instanceof java.time.OffsetDateTime offset?offset.toInstant():(Instant)value;}
  private static ApiError absent(){return new ApiError(404,"NOT_FOUND","Lab run or manifest not found.");}
  private static ApiError idempotency(){return new ApiError(409,"IDEMPOTENCY_CONFLICT","This request identity already belongs to different input.");}
}
