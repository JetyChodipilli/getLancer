import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync,writeFileSync,mkdtempSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {execFileSync,spawnSync} from 'node:child_process';

function production(path){
 const revision=process.env.ROUND3_RUNTIME_REVISION;
 if(!revision)return readFileSync(new URL('../'+path,import.meta.url),'utf8');
 assert.match(revision,/^[a-f0-9]{40}$/,'The optional reproduction revision must be an exact commit');
 return execFileSync('git',['show',revision+':'+path],{cwd:new URL('..',import.meta.url),encoding:'utf8'});
}
const service=production('backend/src/main/java/com/getlancer/labs/LabService.java');
const configuration=production('backend/src/main/java/com/getlancer/labs/LabConfiguration.java');
function method(source,signature,optional=false){const start=source.indexOf(signature);if(start<0){if(optional)return '';throw Error('Missing actual method '+signature);}const open=source.indexOf('{',start);let depth=1,end=open+1;for(;depth&&end<source.length;end++){if(source[end]==='{')depth++;if(source[end]==='}')depth--;}assert.equal(depth,0);return source.slice(start,end);}

for(const entry of ['current','start','replay','detail'])for(const scenario of entry==='current'?['positive','run-expiry','certificate-expiry','admission-expiry']:entry==='start'?['positive','certificate-expiry','admission-expiry','session-expiry']:entry==='replay'?['positive','session-expiry']:['positive','mfa-expiry','owner-mfa-expiry'])test('lab '+entry+' authority after source lock: '+scenario,()=>{
 const source=`import java.time.*;import java.sql.Timestamp;import java.util.*;import java.util.concurrent.locks.LockSupport;
class RuntimeExpiryHarness {
 static class ApiError extends RuntimeException{final int status;final String code;ApiError(int status,String code,String message){super(message);this.status=status;this.code=code;}}
 static class HttpServletRequest{} static class LabRequests{record Inputs(Map<String,String> values){}record Start(String manifestId,String scenarioId,Inputs inputs){}}
 static class LabResponses{record Run(){}record Quota(int remaining,UUID activeRunId){}record Limits(int maxActiveRuns,int maxMemoryMiB,long dailyBudgetMicros,long monthlyBudgetMicros){}}
 static class LabIotPolicy{static final String RESOURCE_CLASS="IOT";static final int CLASS_RUNS=4,CLASS_MEMORY_MIB=1024;}
 static class LabDataAiPolicy{static final int CLASS_RUNS=4,CLASS_MEMORY_MIB=1024;}
 record LabManifest(Instant approvedUntil,String evidenceSha256){String id(){return "fixture";}String imageDigest(){return "sha256:fixture";}Scenario scenario(String id){return new Scenario(id);}Map<String,String> inputs(Scenario scenario,Map<String,String> values){return values;}int memoryMiB(){return 64;}long maxCostMicros(){return 1000;}}
 record Scenario(String id){}
 static class Config{
  UUID epoch=UUID.randomUUID();Evidence evidence;Config(Instant deadline){evidence=new Evidence(deadline,"e");}
  record Evidence(Instant approvedUntil,String evidenceSha256){} record Runtime(boolean enabled){}
  Runtime projection(){return new Runtime(evidence.approvedUntil().isAfter(Instant.now()));}
  void ready(){if(!projection().enabled())throw new ApiError(503,"LAB_DISABLED","expired");}
  ${method(configuration,'void current(',true)}
 }
 static class Db{UUID epoch;int inserted;Map<String,Object> previous;List<UUID> queryForList(String sql,Class<UUID> type,Object...args){return List.of((UUID)args[0]);}List<Map<String,Object>> queryForList(String sql,Object...args){return sql.contains("FROM lab_runs")&&previous!=null?List.of(previous):List.of();}<T>T queryForObject(String sql,Class<T> type,Object...args){return type.cast(type==UUID.class?epoch:"fixture-hash");}void update(String sql,Object...args){if(sql.startsWith("INSERT INTO lab_runs"))inserted++;}}
 static class Service{
  final Db db=new Db();final Config config;final LabManifest signed;final Instant waitUntil,sessionUntil,mfaUntil;final UUID user=UUID.randomUUID();final Security security=new Security(this);int sourceCalls;
  Service(String scenario,String entry){Instant future=Instant.now().plusSeconds(60),deadline=Instant.now().plusSeconds(1);waitUntil=scenario.equals("positive")?Instant.now():deadline;sessionUntil=scenario.equals("session-expiry")?deadline:future;mfaUntil=scenario.contains("mfa-expiry")?deadline:future;config=new Config(scenario.equals("admission-expiry")?deadline:future);signed=new LabManifest(scenario.equals("certificate-expiry")||scenario.equals("admission-expiry")?deadline:future,"e");db.epoch=config.epoch;if(entry.equals("replay")||entry.equals("detail"))db.previous=Map.of("id",UUID.randomUUID(),"owner_id",scenario.equals("owner-mfa-expiry")?user:UUID.randomUUID(),"request_hash","hash","operator_epoch",config.epoch,"expires_at",future,"manifest_id","fixture","scenario_id","request");}
  static class Security{final Service service;Security(Service service){this.service=service;}record Principal(boolean recentMfa){}boolean role(UUID user,String role){return true;}Principal loadPrincipal(HttpServletRequest request){return service.sessionUntil.isAfter(Instant.now())?new Principal(service.mfaUntil.isAfter(Instant.now())):null;}}
  LabManifest manifest(String id){if(!signed.approvedUntil().isAfter(Instant.now()))throw new ApiError(503,"LAB_MANIFEST_UNAVAILABLE","expired");return signed;}
  String requirePolicies(LabManifest manifest,String scenario,boolean admission){return "GENERAL";}
  final Iot iot=new Iot();static class Iot{Map<String,String> inputs(LabManifest manifest,Map<String,String> values,UUID id){return values;}}
  <T>T tx(java.util.function.Supplier<T> work){return work.get();}void key(String key){}void syncEpoch(){}void readLock(){}Map<String,Object> settings(){return Map.of("paused",false);}UUID actor(HttpServletRequest request,boolean admin){if(!sessionUntil.isAfter(Instant.now()))throw new ApiError(401,"UNAUTHENTICATED","expired");if(admin&&!security.loadPrincipal(request).recentMfa())throw new ApiError(403,"FORBIDDEN","MFA expired");return user;}String hash(Object input){return "hash";}void cancel(Map<String,Object> run,String status,String reason){}Map<String,Object> row(UUID id){return db.previous==null?Map.of():db.previous;}Map<String,Object> owned(UUID id,UUID actor,boolean admin){var run=row(id);if(!admin&&!actor.equals(run.get("owner_id")))throw new ApiError(404,"NOT_FOUND","Not owned");return run;}LabResponses.Run projection(Map<String,Object> run){return new LabResponses.Run();}LabResponses.Quota quota(UUID owner){return new LabResponses.Quota(5,null);}Map<String,Object> resources(){return Map.of("active",0,"memory",0,"daily",0,"monthly",0);}LabResponses.Limits limits(){return new LabResponses.Limits(10,5120,50000000,1000000000);}Map<String,Object> classResources(String kind){return Map.of("active",0,"memory",0);}static long number(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}String encode(Object input){return "{}";}void event(UUID id,String action,String status,String reason){}void outbox(UUID id,int generation,String action){}ApiError idempotency(){return new ApiError(409,"IDEMPOTENCY_CONFLICT","conflict");}
  String source(LabManifest manifest,boolean lock){sourceCalls++;while(Instant.now().isBefore(waitUntil))LockSupport.parkNanos(1_000_000);return "/components/fixture";}
  static Instant instant(Map<String,Object> run,String key){Object value=run.get(key);return value instanceof Timestamp stamp?stamp.toInstant():(Instant)value;}
  ${method(service,'private void current(')}
  ${method(service,'private void reconcileAuthority(')}
  ${method(service,'private LabResponses.Run observedProjection(')}
  ${method(service,'public LabResponses.Run start(')}
  ${method(service,'public LabResponses.Run detail(')}
 }
 public static void main(String[] args){String scenario=args[0],entry=args[1];Service service=new Service(scenario,entry);Instant expiry=scenario.equals("run-expiry")?service.waitUntil:Instant.now().plusSeconds(60);Map<String,Object> run=Map.of("owner_id",UUID.randomUUID(),"operator_epoch",service.config.epoch,"expires_at",expiry,"manifest_id","fixture","scenario_id","request");ApiError denied=null;try{if(entry.equals("start")||entry.equals("replay"))service.start(new LabRequests.Start("fixture","request",new LabRequests.Inputs(Map.of())),"request-key",new HttpServletRequest());else if(entry.equals("detail"))service.detail((UUID)service.db.previous.get("id"),new HttpServletRequest());else service.current(run);}catch(ApiError error){denied=error;}
  if(service.sourceCalls!=1)throw new AssertionError("The request must reach the source lock boundary");
  if(scenario.equals("positive")||scenario.equals("owner-mfa-expiry")){if(denied!=null)throw denied;if(entry.equals("start")&&service.db.inserted!=1)throw new AssertionError("Current admission must reserve once");if(entry.equals("replay")&&service.db.inserted!=0)throw new AssertionError("Replay must not reserve twice");System.out.println("CURRENT_AUTHORITY_ALLOWED");}
  else{if(denied==null)throw new AssertionError("Expired "+scenario+" must reject authority after source lock wait");String expected=scenario.equals("session-expiry")?"UNAUTHENTICATED":scenario.equals("mfa-expiry")?"FORBIDDEN":scenario.equals("run-expiry")?"LAB_EXPIRED":"LAB_MANIFEST_UNAVAILABLE";if(!expected.equals(denied.code))throw new AssertionError("Expected "+expected+" but got "+denied.code);if(service.db.inserted!=0)throw new AssertionError("Expired admission must not reserve a run");System.out.println("EXPIRED_AUTHORITY_REJECTED "+scenario);}
 }
}`;
 const directory=mkdtempSync(join(tmpdir(),'round3-runtime-'));
 try{writeFileSync(join(directory,'RuntimeExpiryHarness.java'),source);const run=spawnSync('java',['RuntimeExpiryHarness.java',scenario,entry],{cwd:directory,encoding:'utf8',timeout:30000});assert.equal(run.status,0,String(run.error??'')+run.stdout+run.stderr);assert.match(run.stdout,scenario==='positive'||scenario==='owner-mfa-expiry'?/CURRENT_AUTHORITY_ALLOWED/:/EXPIRED_AUTHORITY_REJECTED/);}finally{rmSync(directory,{recursive:true,force:true});}
});
