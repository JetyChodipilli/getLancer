import assert from 'node:assert/strict';
import {readFileSync,mkdtempSync,writeFileSync,rmSync,existsSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {spawnSync} from 'node:child_process';
import test from 'node:test';

// Source-launch the actual order routine with deterministic in-process boundaries.
// Real JDBC locking, HTTP DTOs and provider receipts are exercised separately in CI.
const source=readFileSync(new URL('../backend/src/main/java/com/getlancer/payments/PaymentService.java',import.meta.url),'utf8');
function method(signature,optional=false,content=source){
 const start=content.indexOf(signature);if(start<0){assert.ok(optional,`Missing actual method ${signature}`);return '';}
 let cursor=content.indexOf('{',start)+1,depth=1;
 for(;cursor<content.length&&depth;cursor++){if(content[cursor]==='{')depth++;else if(content[cursor]==='}')depth--;}
 assert.equal(depth,0);return content.slice(start,cursor);
}
const harness=`
import java.util.*;import java.util.function.Supplier;
class FinanceOrderHarness {
 static class ApiError extends RuntimeException {final int status;final String code;ApiError(int s,String c,String m){super(m);status=s;code=c;}}
 static class HttpServletRequest {final UUID user=UUID.randomUUID(),key=UUID.randomUUID();String getHeader(String name){return key.toString();}}
 static UUID uuid(String value){return UUID.fromString(value);}static long number(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
 static class PaymentResponses {record Checkout(Map<String,Object> value){}}
 static class OrderNode {Text path(String name){return new Text();}}
 static class Text {String asText(){return "order_fixture123456";}}
 static class RazorpayClient {String mode="test";int creates;Runnable io=()->{};void requireCollection(){}OrderNode createOrder(UUID id,long amount,String account){creates++;io.run();return new OrderNode();}static class ProviderFailure extends RuntimeException{boolean rejected;}}
 static class Security {UUID user(HttpServletRequest r){return r.user;}}
 static class DeliveryRepository {
 final UUID milestone=UUID.randomUUID(),engagement=UUID.randomUUID();final Map<String,Object> row=new HashMap<>();
 DeliveryRepository(){row.putAll(Map.of("id",milestone,"engagementId",engagement,"amountMinor",10000L,"currency","INR","status","ACCEPTED","engagementStatus","ACTIVE"));}
 Map<String,Object> lockMilestone(UUID id,HttpServletRequest r,String side){return new HashMap<>(row);}void event(UUID id,UUID actor,String kind,String detail){}
 }
 static class PaymentRepository {
 Map<String,Object> row;boolean eligible=true;String account="acc_original123456";final DeliveryRepository delivery;
 PaymentRepository(DeliveryRepository d){delivery=d;}void advisoryLock(String key){}
 Map<String,Object> byKey(UUID payer,UUID key){return row==null?null:new HashMap<>(row);}Map<String,Object> active(UUID milestone){return row==null?null:new HashMap<>(row);}
 String payee(Map<String,Object> m,String mode){if(!eligible)throw new ApiError(409,"PAYEE_UNAVAILABLE","Seller is no longer eligible");return account;}
 void reserve(UUID id,UUID milestone,UUID payer,UUID key,long amount,String account,String mode){row=new HashMap<>(Map.of("id",id,"milestone_id",milestone,"amount_minor",amount,"account_id",account,"mode",mode,"status","CREATING"));}
 Map<String,Object> attempt(UUID id,boolean lock){return new HashMap<>(row);}void bind(UUID id,String order,String transfer,String settlement){row.put("order_id",order);row.put("status","ORDER_CREATED");}void creationFailed(UUID id,boolean rejected){row.put("status",rejected?"REJECTED":"UNKNOWN");}
 }
 static class Service {
 final DeliveryRepository delivery=new DeliveryRepository();final PaymentRepository repo=new PaymentRepository(delivery);final Security security=new Security();final RazorpayClient provider=new RazorpayClient();
 <T>T tx(Supplier<T> work){return work.get();}void mode(Map<String,Object> row){}PaymentResponses.Checkout checkout(Map<String,Object> row){return new PaymentResponses.Checkout(row);}
 Map<String,Object> accessible(UUID id,HttpServletRequest request,String side){delivery.lockMilestone(delivery.milestone,request,side);return repo.attempt(id,true);}Map<String,Object> system(UUID id){return repo.attempt(id,true);}
 record TransferState(String status,String settlement){}TransferState validateOrder(Map<String,Object> row,OrderNode order){return new TransferState("CREATED","UNKNOWN");}
 ${method('public PaymentResponses.Checkout order(')}
 ${method('private PaymentResponses.Checkout availableCheckout(',true)}
 }
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static void denied(Service s,HttpServletRequest r,String code){try{s.order(s.delivery.milestone,r);throw new AssertionError("Checkout was exposed after seller authority changed");}catch(ApiError e){check(e.code.equals(code),"Expected "+code+" but got "+e.code);}}
 public static void main(String[] args){
 var s=new Service();var r=new HttpServletRequest();String scenario=args[0];
 if(scenario.equals("current")){check(s.order(s.delivery.milestone,r).value().get("status").equals("ORDER_CREATED"),"Current seller can create checkout");s.order(s.delivery.milestone,r);check(s.provider.creates==1,"Current seller can reuse the original order without a second create");}
 else if(scenario.startsWith("reuse-")){s.order(s.delivery.milestone,r);if(scenario.equals("reuse-suspended")){s.repo.eligible=false;denied(s,r,"PAYEE_UNAVAILABLE");}else{s.repo.account="acc_replacement123456";denied(s,r,"PAYEE_CHANGED");}check(s.provider.creates==1,"Reuse denial must retain the original provider order");check(s.repo.row.get("account_id").equals("acc_original123456"),"Frozen recipient remains unchanged");}
 else{if(scenario.equals("create-suspended"))s.provider.io=()->s.repo.eligible=false;else s.provider.io=()->s.repo.account="acc_replacement123456";denied(s,r,scenario.equals("create-suspended")?"PAYEE_UNAVAILABLE":"PAYEE_CHANGED");check(s.provider.creates==1,"Exactly one provider create");check(s.repo.row.get("order_id")!=null&&s.repo.row.get("status").equals("ORDER_CREATED"),"Provider order binding survives final authority denial");check(s.repo.row.get("account_id").equals("acc_original123456"),"Frozen recipient remains unchanged");}
 System.out.println("FINANCE_ORDER_OK "+scenario);
 }
}
`;

for(const scenario of ['current','reuse-suspended','reuse-remapped','create-suspended','create-remapped'])test('actual payment order '+scenario,()=>{
 const directory=mkdtempSync(join(tmpdir(),'round3-finance-'));
 try{
  writeFileSync(join(directory,'FinanceOrderHarness.java'),harness);
  const java=existsSync('/usr/lib/jvm/java-17-openjdk-amd64/bin/java')?'/usr/lib/jvm/java-17-openjdk-amd64/bin/java':'java';
  const run=spawnSync(java,['FinanceOrderHarness.java',scenario],{cwd:directory,encoding:'utf8',timeout:30000});
  assert.equal(run.status,0,String(run.error??'')+run.stdout+run.stderr);assert.match(run.stdout,/FINANCE_ORDER_OK/);
 }finally{rmSync(directory,{recursive:true,force:true});}
});

const repository=readFileSync(new URL('../backend/src/main/java/com/getlancer/payments/PaymentRepository.java',import.meta.url),'utf8');
for(const scenario of ['current','builder-revoked-while-locking','team-revoked-while-locking'])test('actual payee routine '+scenario,()=>{
 const payeeHarness=`
import java.util.*;
class PayeeHarness {
 static class ApiError extends RuntimeException {final String code;ApiError(int status,String code,String message){super(message);this.code=code;}}
 static class Db {boolean eligible=true;Runnable locked=()->{};
 List<Map<String,Object>> queryForList(String sql,Object... args){
  if(sql.contains("FROM payment_accounts"))return List.of(Map.of("account_id","acc_original123456"));
  // PostgreSQL locking SELECTs can evaluate joins before waiting for a user/team
  // lock held by a transaction that revokes the joined profile/role.
  boolean observed=eligible;if(sql.contains("FOR SHARE"))locked.run();return observed?List.of(Map.of("id",UUID.randomUUID())):List.of();
 }
 }
 static class Repo {final Db db=new Db();${method('public String payee(',false,repository)}}
 public static void main(String[] args){var repo=new Repo();var row=new HashMap<String,Object>();row.put(args[0].startsWith("team")?"teamId":"builderUserId",UUID.randomUUID());
  if(!repo.payee(row,"test").equals("acc_original123456"))throw new AssertionError("Current eligible seller positive control");
  if(!args[0].equals("current")){repo.db.locked=()->repo.db.eligible=false;try{repo.payee(row,"test");throw new AssertionError("Eligibility evaluated before a lock wait was reused after revocation");}catch(ApiError expected){if(!expected.code.equals("PAYEE_UNAVAILABLE"))throw expected;}}
  System.out.println("PAYEE_AUTHORITY_OK "+args[0]);
 }
}
`;
 const directory=mkdtempSync(join(tmpdir(),'round3-payee-'));
 try{
  writeFileSync(join(directory,'PayeeHarness.java'),payeeHarness);
  const java=existsSync('/usr/lib/jvm/java-17-openjdk-amd64/bin/java')?'/usr/lib/jvm/java-17-openjdk-amd64/bin/java':'java';
  const run=spawnSync(java,['PayeeHarness.java',scenario],{cwd:directory,encoding:'utf8',timeout:30000});
  assert.equal(run.status,0,String(run.error??'')+run.stdout+run.stderr);assert.match(run.stdout,/PAYEE_AUTHORITY_OK/);
 }finally{rmSync(directory,{recursive:true,force:true});}
});
