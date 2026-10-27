import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync, mkdtempSync, writeFileSync, rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {spawnSync} from 'node:child_process';

// Compile actual production methods with in-process boundaries. This exercises
// decision ordering; PostgreSQL/Spring/provider compatibility is covered in CI.
const media=readFileSync(new URL('../backend/src/main/java/com/getlancer/media/MediaService.java',import.meta.url),'utf8');
const hosting=readFileSync(new URL('../backend/src/main/java/com/getlancer/hosting/HostingService.java',import.meta.url),'utf8');
function method(source,signature,optional=false){
 const start=source.indexOf(signature);if(start<0){assert.ok(optional,`Missing method ${signature}`);return '';}
 const begin=source.indexOf('{',start);let depth=1,cursor=begin+1;
 for(;cursor<source.length&&depth;cursor++){if(source[cursor]==='{')depth++;else if(source[cursor]==='}')depth--;}
 assert.equal(depth,0);return source.slice(start,cursor);
}

for(const domain of ['media','hosting-withdraw','hosting-reconcile'])test(`actual ${domain} methods recheck authority after external I/O and retain provider facts`,()=>{
 const source=`
import java.util.*; import java.time.*;
class RuntimeAuthorityHarness {
 static class ApiError extends RuntimeException {final int status; final String code; ApiError(int status,String code,String message){super(message);this.status=status;this.code=code;}}
 static class HttpServletRequest {boolean authenticated=true,admin=true; final UUID user=UUID.randomUUID();}
 static class Security {UUID user(HttpServletRequest r){if(!r.authenticated)throw new ApiError(401,"UNAUTHENTICATED","expired");return r.user;} UUID admin(HttpServletRequest r){UUID u=user(r);if(!r.admin)throw new ApiError(403,"FORBIDDEN","revoked");return u;} UUID developer(HttpServletRequest r,boolean approved){return user(r);} Map<String,Object> principal(HttpServletRequest r){return Map.of("id",user(r));}}
 static class Rules {static boolean publicProduct(String a,String l,String v,String u,String p){return a.equals("APPROVED")&&l.equals("ACTIVE")&&v.equals("PUBLIC")&&u.equals("ACTIVE")&&p.equals("APPROVED");}}
 static class PrivateProjectService {static boolean granted(Db db,UUID id,Map<String,Object> user){return db.granted;}}
 static class Db {boolean exists=true,granted=false;Map<String,Object> row;List<Map<String,Object>> queryForList(String sql,Object... args){return exists?List.of(new HashMap<>(row)):List.of();}}
 static class GetObjectRequest {static Builder builder(){return new Builder();}static class Builder{Builder bucket(String s){return this;}Builder key(String s){return this;}GetObjectRequest build(){return new GetObjectRequest();}}}
 static class S3 {Runnable read=()->{};int reads;Bytes getObjectAsBytes(GetObjectRequest req){reads++;read.run();return new Bytes();}static class Bytes{byte[] asByteArray(){return new byte[]{1,2,3};}}}
 static class MediaType {static final String IMAGE_PNG="image/png";}
 static class ResponseEntity<T> {final T payload;ResponseEntity(T v){payload=v;}static Builder ok(){return new Builder();}static class Builder{Builder contentType(String s){return this;}Builder header(String k,String v){return this;}<T>ResponseEntity<T> body(T value){return new ResponseEntity<>(value);}}}
 static class Media {
 final Db db=new Db();final Security security=new Security();final S3 s3=new S3();final String bucket="private";
 ${method(media,'public ResponseEntity<byte[]> get(')}
 ${method(media,'private Map<String, Object> readable(',true)}
 ${method(media,'private String storageKey(',true)}
 }
 static class DemoPublisherClient {record Metadata(UUID id,String state) {}}
 static class Repo {
 Map<String,Object> row=new HashMap<>();boolean facts;int projections;
 Map<String,Object> record(UUID id,boolean lock){return new HashMap<>(row);}UUID owner(UUID id){return (UUID)row.get("owner_id");}
 void lockActor(UUID actor){} void lockOwner(UUID owner){}
 Map<String,Object> owned(UUID id,UUID owner,boolean lock){if(!owner.equals(row.get("owner_id")))throw new ApiError(404,"NOT_FOUND","missing");return record(id,lock);}
 void withdraw(UUID id){row.put("desired_state","WITHDRAWN");row.put("deployment_state","DELETE_PENDING");}
 void audit(UUID id,UUID actor,String action,String detail){}
 boolean observed(UUID id,DemoPublisherClient.Metadata metadata){facts=true;row.put("deployment_state",metadata.state());return true;}
 Map<String,Object> detail(UUID id){projections++;return Map.of("title","Private package","deploymentState",row.get("deployment_state"));}
 }
 static class Publisher {Runnable io=()->{};DemoPublisherClient.Metadata get(UUID d,String a,String m,Instant e){io.run();return new DemoPublisherClient.Metadata(d,"READY");}DemoPublisherClient.Metadata delete(UUID d,String a,String m,Instant e){io.run();return new DemoPublisherClient.Metadata(d,"DELETED");}}
 static class Hosting {
 final Repo repo=new Repo();final Security security=new Security();final Publisher publisher=new Publisher();final Config config=new Config();
 static class Config {void ready(){}}<T>T tx(java.util.function.Supplier<T> work){return work.get();}
 static Instant instant(Map<String,Object> row,String k){return (Instant)row.get(k);}static String text(Map<String,Object>b,String k,int min,int max){return b.get(k).toString();}
 DemoPublisherClient.Metadata retryExact(UUID id,Map<String,Object> h,HttpServletRequest r,boolean admin){throw new AssertionError("Unexpected retry");}
 ${method(hosting,'public Map<String,Object> detail(')}
 ${method(hosting,'private UUID actor(')}
 ${method(hosting,'private Map<String,Object> access(')}
 ${method(hosting,'private void complete(')}
 ${method(hosting,'public Map<String,Object> withdraw(')}
 ${method(hosting,'public Map<String,Object> reconcile(')}
 ${method(hosting,'private Map<String,Object> currentDetail(',true)}
 }
 static Media media(HttpServletRequest r){var service=new Media();service.db.row=new HashMap<>(Map.of("storage_key","full","thumbnail_key","small","product_id",UUID.randomUUID(),"owner_user_id",r.user,"approval_status","APPROVED","lifecycle_status","ACTIVE","visibility","PUBLIC","account_status","ACTIVE","profile_status","APPROVED"));return service;}
 static Hosting hosting(HttpServletRequest r){var service=new Hosting();service.repo.row.putAll(Map.of("owner_id",r.user,"deployment_id",UUID.randomUUID(),"deployment_state","READY","desired_state","PUBLISHED","archive_sha256","a","manifest_sha256","b","expires_at",Instant.now().plusSeconds(60)));return service;}
 static int checks;
 static void check(boolean valid,String detail){checks++;if(!valid)throw new AssertionError(detail);}
 static void denied(Runnable action,String detail){boolean rejected=false;try{action.run();}catch(ApiError e){rejected=true;}check(rejected,detail);}
 public static void main(String[] args){
  if(args[0].equals("media")){
  var request=new HttpServletRequest();var m=media(request);check(m.get(UUID.randomUUID(),"full",request).payload.length==3,"Public positive control");
  m.db.row.put("visibility","PRIVATE");check(m.get(UUID.randomUUID(),"full",request).payload.length==3,"Private owner positive control");
  request=new HttpServletRequest();request.admin=false;var client=request;m=media(request);m.db.row.put("visibility","PRIVATE");m.db.row.put("owner_user_id",UUID.randomUUID());m.db.granted=true;check(m.get(UUID.randomUUID(),"thumbnail",client).payload.length==3,"Private granted client positive control");var granted=m;m.s3.read=()->granted.db.granted=false;denied(()->granted.get(UUID.randomUUID(),"thumbnail",client),"Revoked private grant must reject fetched bytes");
  request=new HttpServletRequest();request.authenticated=false;var anonymous=request;m=media(request);var revoked=m;m.s3.read=()->revoked.db.row.put("visibility","PRIVATE");denied(()->revoked.get(UUID.randomUUID(),"full",anonymous),"Public media revocation during storage read must reject bytes");
  request=new HttpServletRequest();var owner=request;m=media(request);m.db.row.put("visibility","PRIVATE");var expired=m;m.s3.read=()->owner.authenticated=false;denied(()->expired.get(UUID.randomUUID(),"full",owner),"Private session expiry during storage read must reject bytes");
  request=new HttpServletRequest();var viewer=request;m=media(request);var removed=m;m.s3.read=()->removed.db.exists=false;denied(()->removed.get(UUID.randomUUID(),"full",viewer),"Deleted media must not return bytes");
  m=media(request);var changed=m;m.s3.read=()->changed.db.row.put("storage_key","replacement");denied(()->changed.get(UUID.randomUUID(),"full",viewer),"Replacement key must not authorize old bytes");
  }
  if(args[0].startsWith("hosting-"))for(String operation:List.of(args[0].substring(8)))for(boolean revoke:List.of(false,true)){
   var r=new HttpServletRequest();var h=hosting(r);h.publisher.io=()->{if(revoke)r.admin=false;};var id=UUID.randomUUID();
   Runnable action=()->{if(operation.equals("withdraw"))h.withdraw(id,Map.of("reason","Operator reviewed this exact package"),r,true);else h.reconcile(id,Map.of("reason","Operator reviewed this exact package"),r,true);};
   if(revoke){denied(action,"Revoked admin must not receive post-provider "+operation+" projection");check(h.repo.projections==0,"No projection after denial");}else {action.run();check(h.repo.projections==1,"Current admin positive control");}
   check(h.repo.facts,"Provider facts must persist independently of final viewer authority");
  }
  System.out.println("RUNTIME_AUTHORITY_OK checks="+checks);
 }
}
`;
 const directory=mkdtempSync(join(tmpdir(),'round2-runtime-'));
 try{
  writeFileSync(join(directory,'RuntimeAuthorityHarness.java'),source);
  const run=spawnSync('java',['RuntimeAuthorityHarness.java',domain],{cwd:directory,encoding:'utf8',timeout:30000});
  assert.equal(run.status,0,String(run.error??'')+run.stdout+run.stderr);assert.match(run.stdout,/RUNTIME_AUTHORITY_OK/);
 }finally{rmSync(directory,{recursive:true,force:true});}
});
