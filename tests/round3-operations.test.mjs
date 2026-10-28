import test, {after, before} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync, readFileSync, rmSync, writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {spawnSync} from 'node:child_process';

// Actual production Java methods with strict in-process JDBC/authority boundaries.
// This is a decision regression; real transactions, ordering and grants run in CI.
const moderation=readFileSync(new URL('../backend/src/main/java/com/getlancer/moderation/ModerationService.java',import.meta.url),'utf8');
const admin=readFileSync(new URL('../backend/src/main/java/com/getlancer/admin/AdminService.java',import.meta.url),'utf8');
function method(source,signature,optional=false){
 const start=source.indexOf(signature);if(start<0){assert.ok(optional,'Missing '+signature);return '';}
 const begin=source.indexOf('{',start);let depth=1,cursor=begin+1;
 for(;cursor<source.length&&depth;cursor++){if(source[cursor]==='{')depth++;else if(source[cursor]==='}')depth--;}
 assert.equal(depth,0);return source.slice(start,cursor);
}
let directory;
before(()=>{
 directory=mkdtempSync(join(tmpdir(),'round3-operations-'));
 const source=`
import java.util.*;
class OperationsAppealHarness {
 static class ApiError extends RuntimeException {final int status;final String code;ApiError(int s,String c,String m){super(m);status=s;code=c;}}
 static class HttpServletRequest {boolean admin=true;final UUID actor=UUID.randomUUID();}
 static class Security {UUID admin(HttpServletRequest r){if(!r.admin)throw new ApiError(403,"FORBIDDEN","Administrator required");return r.actor;}}
 static class Mail {int notifications;void notify(UUID u,String text){notifications++;}}
 static class Products {void lockOwner(UUID owner){}}
 static class Components {void restoreReported(UUID id,String reason,HttpServletRequest r){throw new AssertionError("Component restoration has its own exact-revision guard");}}
 static final UUID target=UUID.randomUUID(),owner=UUID.randomUUID(),selected=UUID.randomUUID(),appealId=UUID.randomUUID();
 static Map<String,Object> decision(String type,String action,Long order){var row=new HashMap<String,Object>();row.put("id",selected);row.put("target_type",type);row.put("target_id",target);row.put("action",action);row.put("decision_order",order);return row;}
 static class Db {
  Map<String,Object> original,resource=new HashMap<>(),appeal=new HashMap<>(Map.of("decision_id",selected,"appellant_id",owner,"status","OPEN"));
  List<Map<String,Object>> history=new ArrayList<>();int mutations,restorations;boolean missing;Runnable lock=()->{};
  List<Map<String,Object>> queryForList(String sql,Object...args){
   if(sql.contains("FROM moderation_appeals"))return List.of(appeal);
   if(sql.contains("FROM products")&&sql.startsWith("SELECT owner_user_id"))return missing?List.of():List.of(Map.of("owner_user_id",owner));
   if(sql.contains("FOR UPDATE")){lock.run();return missing?List.of():List.of(resource);}
   throw new AssertionError("Unexpected JDBC list: "+sql);
  }
  Map<String,Object> queryForMap(String sql,Object...args){
   if(sql.contains("FROM moderation_actions")&&sql.contains("WHERE id=?"))return original;
   if(sql.contains("FROM moderation_actions")&&sql.contains("count(*)")){
    var relevant=history.stream().filter(row->sql.contains("'"+row.get("action").toString().toLowerCase(Locale.ROOT)+"'")).toList();
    var orders=relevant.stream().map(row->(Long)row.get("decision_order")).filter(Objects::nonNull).toList();
    var out=new HashMap<String,Object>();out.put("total",(long)relevant.size());out.put("ordered",(long)orders.size());out.put("latest",orders.stream().max(Long::compare).orElse(null));return out;
   }
   if(sql.contains("showcase_entitlements"))return Map.of("user_id",owner);
   throw new AssertionError("Unexpected JDBC map: "+sql);
  }
  int update(String sql,Object...args){mutations++;if(sql.startsWith("UPDATE moderation_appeals"))appeal.put("status",args[0]);return 1;}
 }
 static String text(Map<String,Object> body,String key,int min,int max){return body.get(key).toString();}
 static class Admin {
  final Db db;final Products products=new Products();Admin(Db db){this.db=db;}
  ${method(admin,'public void requireCurrentAppealDecision(',true)}
  void restore(){db.restorations++;}
  void accountAction(UUID id,String action,Map<String,Object> body,HttpServletRequest r){restore();}
  void profileAction(UUID id,String action,Map<String,Object> body,HttpServletRequest r){restore();}
  void inquiryAction(UUID id,String action,Map<String,Object> body,HttpServletRequest r){restore();}
  void reviewAction(UUID id,String action,Map<String,Object> body,HttpServletRequest r){restore();}
  void productAction(UUID id,String action,Map<String,Object> body,HttpServletRequest r){restore();}
  void audit(UUID actor,String type,UUID id,String state,String reason){}
 }
 static class Moderation {
  final Db db;final Admin admin;final Security security=new Security();final Mail mail=new Mail();final Components components=new Components();
  Moderation(Db db){this.db=db;admin=new Admin(db);}
  ${method(moderation,'public Map<String, Object> appealDecision(')}
 }
 static Db setup(String type,Long order){
  var db=new Db();String action=type.equals("REVIEW")?"hide":type.equals("INQUIRY")?"block":"suspend";db.original=decision(type,action,order);db.history.add(db.original);
  db.resource.put("account_status","SUSPENDED");db.resource.put("approval_status","SUSPENDED");db.resource.put("moderation_status",type.equals("REVIEW")?"HIDDEN":"BLOCKED");db.resource.put("lifecycle_status","SUSPENDED");return db;
 }
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static void decide(Db db,boolean allowed,String state){
  var service=new Moderation(db);boolean rejected=false;try{service.appealDecision(appealId,Map.of("status",state,"reason","Independent review of this exact decision"),new HttpServletRequest());}catch(ApiError e){check(e.status==409&&e.code.equals("MODERATION_APPEAL_STALE"),"Named stale-decision conflict: "+e.code);rejected=true;}
  check(rejected!=allowed,allowed?"Current matching appeal must succeed":"Stale or changed appeal must not restore current restrictions");
  check(db.restorations==(allowed&&state.equals("OVERTURNED")?1:0),"Only the exact current overturned decision may restore");
  check(db.appeal.get("status").equals(allowed?state:"OPEN"),"Rejected appeal stays open for safe review");
  check(service.mail.notifications==(allowed?1:0),"Rejected appeal sends no success notification");
 }
 public static void main(String[] args){
  String scenario=args[0];for(String type:List.of("ACCOUNT","USER","PRODUCT","INQUIRY","REVIEW")){
   var db=setup(type,1L);
   switch(scenario){
    case "current" -> decide(db,true,"OVERTURNED");
    case "later" -> {db.history.add(decision(type,db.original.get("action").toString(),2L));decide(db,false,"OVERTURNED");}
    case "wait" -> {var waiting=db;db.lock=()->{waiting.history.add(decision(type,waiting.original.get("action").toString(),2L));waiting.lock=()->{};};decide(db,false,"OVERTURNED");}
    case "unrelated" -> {db.history.add(decision(type,type.equals("PRODUCT")?"REPOSITORY_REVOKE":"EARNED_CAPACITY",2L));decide(db,true,"OVERTURNED");}
    case "legacy" -> {db=setup(type,null);decide(db,true,"OVERTURNED");}
    case "legacy-ambiguous" -> {db=setup(type,null);db.history.add(decision(type,db.original.get("action").toString(),null));decide(db,false,"OVERTURNED");}
    case "legacy-superseded" -> {db=setup(type,null);db.history.add(decision(type,db.original.get("action").toString(),2L));decide(db,false,"OVERTURNED");}
    case "changed" -> {db.resource.put("account_status","DELETED");db.resource.put("approval_status","DRAFT");db.resource.put("moderation_status","CLEAR");db.resource.put("lifecycle_status","ARCHIVED");decide(db,false,"OVERTURNED");}
    case "nonrestrictive" -> {db.original.put("action",type.equals("INQUIRY")?"restore":type.equals("ACCOUNT")?"restore":type.equals("REVIEW")?"publish":"approve");decide(db,false,"OVERTURNED");}
    case "review" -> {db.history.add(decision(type,db.original.get("action").toString(),2L));decide(db,true,"UNDER_REVIEW");}
    case "upheld" -> {db.history.add(decision(type,db.original.get("action").toString(),2L));decide(db,true,"UPHELD");}
    default -> throw new AssertionError(scenario);
   }
  }System.out.println("OPERATIONS_APPEAL_OK "+scenario+" targets=5");
 }
}
`;
 writeFileSync(join(directory,'OperationsAppealHarness.java'),source);
 const compiled=spawnSync('java',['--add-modules','jdk.compiler','com.sun.tools.javac.Main','OperationsAppealHarness.java'],{cwd:directory,encoding:'utf8',timeout:30000});
 assert.equal(compiled.status,0,String(compiled.error??'')+compiled.stdout+compiled.stderr);
});
after(()=>{if(directory)rmSync(directory,{recursive:true,force:true});});
for(const scenario of ['current','later','wait','unrelated','legacy','legacy-ambiguous','legacy-superseded','changed','nonrestrictive','review','upheld']){
 test('actual moderation appeal decision: '+scenario,()=>{
  const result=spawnSync('java',['-cp',directory,'OperationsAppealHarness',scenario],{encoding:'utf8',timeout:30000});
  assert.equal(result.status,0,String(result.error??'')+result.stdout+result.stderr);assert.match(result.stdout,/OPERATIONS_APPEAL_OK/);
 });
}
