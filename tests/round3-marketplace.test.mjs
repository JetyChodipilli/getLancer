import assert from 'node:assert/strict';
import {readFileSync,mkdtempSync,writeFileSync,rmSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import {spawnSync} from 'node:child_process';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import test from 'node:test';

const source=()=>readFileSync(new URL('../app/components/education-source.tsx',import.meta.url),'utf8');
function routine(start,end,context){
 const text=source(),first=text.indexOf(start),last=text.indexOf(end,first);
 assert.ok(first>=0&&last>first,'Actual production routine boundaries exist');
 return Function(...Object.keys(context),stripTypeScriptTypes(text.slice(first,last),{mode:'transform'})+';return '+start.match(/function (\w+)/)[1]+';')(...Object.values(context));
}
const deferred=()=>Promise.withResolvers();
function fixture({orderStatus='ORDER_CREATED',orderWork,verificationWork,checkoutLoad}={}){
 const state={orders:0,verifies:0,reconciles:0,opened:0,closed:0,busy:false,message:'',error:'',order:null,options:null,failed:null,keys:[]};
 const context={preview:false,consent:true,offer:{checkoutAvailable:true,package:{templateId:'template-1',versionId:'version-1'}},release:{id:'education-1',snapshot:{title:'Reviewed education source'}},
  key:{current:''},alive:{current:true},generation:{current:1},pending:{current:false},checkoutGeneration:{current:0},gatewayRef:{current:null},order:null,
  crypto:{randomUUID:()=> 'immutable-key'},templateStatus:value=>value,
  setBusy:value=>state.busy=value,setMessage:value=>state.message=value,setError:value=>state.error=value,setOrder:value=>{state.order=value;},
  api:async(path,options)=>{if(path.endsWith('/verify')){state.verifies++;return verificationWork?verificationWork.promise:{status:'CAPTURED'};}
   if(path.endsWith('/reconcile')){state.reconciles++;return{status:'CAPTURED'};}
   state.orders++;state.keys.push(options.headers['Idempotency-Key']);if(orderWork)return orderWork.promise;
   return{id:'purchase-1',status:orderStatus,orderId:'provider-1',keyId:'test-key',mode:'test',amountMinor:25000,currency:'INR'};
  },
  loadTemplateCheckout:checkoutLoad??(async()=>class{constructor(options){state.options=options;}on(_name,callback){state.failed=callback;}open(){state.opened++;}close(){state.closed++;throw Error('SDK close unavailable');}}),
 };
 if(source().includes('function invalidateCheckout()'))context.invalidateCheckout=routine('function invalidateCheckout()','\n useEffect',context);
 const checkout=routine('async function checkout(){','\n const s=',context);
 return{state,context,checkout};
}

test('education checkout current success verifies once and retains the original purchase',async()=>{
 const {state,checkout}=fixture();await checkout();assert.equal(state.orders,1);assert.equal(state.opened,1);
 await state.options.handler({razorpay_payment_id:'payment-1',razorpay_signature:'signed'});
 assert.equal(state.verifies,1);assert.equal(state.order.id,'purchase-1');assert.match(state.message,/CAPTURED/);assert.equal(state.busy,false);
});

test('education checkout serializes order creation before React can disable the control',async()=>{
 const orderWork=deferred(),f=fixture({orderWork});const first=f.checkout(),second=f.checkout();
 const orders=f.state.orders;orderWork.resolve({id:'purchase-1',status:'UNKNOWN'});await Promise.all([first,second]);assert.equal(orders,1);
 assert.equal(f.state.opened,0);assert.match(f.state.message,/UNKNOWN/);assert.equal(f.state.busy,false);
});

test('dismissed and failed education gateways cannot send a late verification',async()=>{
 for(const outcome of ['dismiss','failed']){
  const f=fixture();await f.checkout();if(outcome==='dismiss')f.state.options.modal.ondismiss();else f.state.failed({error:{description:'Payment declined'}});
  await f.state.options.handler({});assert.equal(f.state.verifies,0,outcome);assert.equal(f.state.busy,false);assert.equal(f.state.closed,1);
  await f.checkout();assert.equal(f.state.orders,2);assert.deepEqual(f.state.keys,['immutable-key','immutable-key']);
 }
});

test('education verification is single-flight and dismissal cannot overwrite its result',async()=>{
 const verificationWork=deferred(),f=fixture({verificationWork});await f.checkout();const first=f.state.options.handler({}),second=f.state.options.handler({});
 const verifies=f.state.verifies;f.state.options.modal.ondismiss();f.state.failed({error:{description:'Late failure'}});
 const during={busy:f.state.busy,message:f.state.message,error:f.state.error};await f.checkout();const orders=f.state.orders;
 verificationWork.resolve({status:'CAPTURED'});await Promise.all([first,second]);
 assert.equal(verifies,1);assert.equal(during.busy,true);assert.doesNotMatch(during.message,/Checkout closed/);assert.equal(during.error,'');assert.equal(orders,1);
 assert.equal(f.state.busy,false);assert.match(f.state.message,/CAPTURED/);await f.state.options.handler({});assert.equal(f.state.verifies,1);
});

test('old education order responses and callbacks stop at the release generation',async()=>{
 const orderWork=deferred(),old=fixture({orderWork});const waiting=old.checkout();old.context.generation.current++;
 orderWork.resolve({id:'old',status:'ORDER_CREATED',orderId:'old-provider',keyId:'old-key'});await waiting;
 assert.equal(old.state.opened,0);assert.equal(old.state.order,null);
 const current=fixture();await current.checkout();current.context.generation.current++;await current.state.options.handler({});assert.equal(current.state.verifies,0);
});

test('education verification failure releases controls and reconciles the retained purchase without another order',async()=>{
 const verificationWork=deferred(),f=fixture({verificationWork});await f.checkout();const waiting=f.state.options.handler({});
 verificationWork.reject(Error('Verification unavailable'));await waiting;assert.equal(f.state.busy,false);assert.match(f.state.error,/Verification unavailable/);
 f.context.order=f.state.order;await routine('async function reconcile(){','\n async function checkout',f.context)();
 assert.equal(f.state.reconciles,1);assert.equal(f.state.orders,1);assert.match(f.state.message,/CAPTURED/);
});

test('education source controls serialize downloads and release them after success or failure',async()=>{
 for(const fail of [false,true]){
  const work=deferred(),f=fixture();let downloads=0;
  Object.assign(f.context,{downloadTemplateSource:()=>{downloads++;return work.promise;}});
  const free=routine('async function free(){','\n async function reconcile',f.context),first=free(),second=free();
  assert.equal(downloads,1);if(fail)work.reject(Error('Source unavailable'));else work.resolve();await Promise.all([first,second]);
  assert.equal(f.context.pending.current,false);assert.equal(f.state.busy,false);if(fail)assert.equal(f.state.error,'Source unavailable');else assert.match(f.state.message,/downloaded/);
 }
});

test('education reconcile refuses competing checkout work and ignores obsolete completion',async()=>{
 const work=deferred(),f=fixture();f.context.order={id:'retained-purchase'};let reconciles=0;
 f.context.api=()=>{reconciles++;return work.promise;};const reconcile=routine('async function reconcile(){','\n async function checkout',f.context);
 f.context.pending.current=true;await reconcile();assert.equal(reconciles,0);f.context.pending.current=false;
 const first=reconcile(),second=reconcile();assert.equal(reconciles,1);f.context.generation.current++;work.resolve({status:'CAPTURED'});await Promise.all([first,second]);assert.equal(f.state.order,null);assert.equal(f.state.message,'');
});

test('education checkout load failure retains the purchase for a read of provider status',async()=>{
 const f=fixture({checkoutLoad:async()=>{throw Error('Checkout loader unavailable');}});await f.checkout();assert.equal(f.state.orders,1);assert.equal(f.state.opened,0);assert.equal(f.state.busy,false);assert.equal(f.context.pending.current,false);assert.match(f.state.error,/loader unavailable/);
 f.context.order=f.state.order;await routine('async function reconcile(){','\n async function checkout',f.context)();assert.equal(f.state.reconciles,1);assert.equal(f.state.orders,1);assert.match(f.state.message,/CAPTURED/);
});

test('education SDK initialization failure invalidates retained callbacks and recovers by reconciliation',async()=>{
 for(const stage of ['constructor','on','open']){
  let retained,closed=0;
  const f=fixture({checkoutLoad:async()=>class{
   constructor(options){retained=options;if(stage==='constructor')throw Error('Checkout SDK '+stage+' unavailable');}
   on(){if(stage==='on')throw Error('Checkout SDK '+stage+' unavailable');}
   open(){throw Error('Checkout SDK '+stage+' unavailable');}
   close(){closed++;throw Error('SDK close unavailable');}
  }});
  await f.checkout();assert.equal(f.state.busy,false);assert.equal(f.context.pending.current,false);assert.match(f.state.error,/Checkout SDK/);
  await retained.handler({});assert.equal(f.state.verifies,0,stage+' failed but retained callback still verified');assert.equal(closed,stage==='constructor'?0:1);
  f.context.order=f.state.order;await routine('async function reconcile(){','\n async function checkout',f.context)();assert.equal(f.state.reconciles,1);assert.equal(f.state.orders,1);assert.match(f.state.message,/CAPTURED/);
 }
});

function javaMethod(text,signature){
 const start=text.indexOf(signature);assert.ok(start>=0,signature);const begin=text.indexOf('{',start);let depth=1,end=begin+1;
 for(;depth&&end<text.length;end++){if(text[end]==='{')depth++;else if(text[end]==='}')depth--;}
 assert.equal(depth,0);return text.slice(start,end);
}
for(const mode of ['current','revoked'])test('actual private source download '+mode+' authority after storage I/O',()=>{
 const text=readFileSync(new URL('../backend/src/main/java/com/getlancer/commerce/CommerceService.java',import.meta.url),'utf8');
 const java=`
import java.util.*;
class MarketplaceAuthorityHarness {
 static class ApiError extends RuntimeException {final int status;ApiError(int status,String code,String message){super(message);this.status=status;}}
 static class HttpServletRequest {final UUID actor=UUID.randomUUID();boolean active=true,admin=true,mfa=true,recent=true;}
 static class Security {record Principal(boolean recentMfa){}Principal loadPrincipal(HttpServletRequest r){return r.active?new Principal(r.mfa&&r.recent):null;}UUID user(HttpServletRequest r){if(!r.active)throw new ApiError(401,"AUTH","Expired");return r.actor;}UUID admin(HttpServletRequest r){UUID actor=user(r);if(!r.admin||!r.mfa)throw new ApiError(403,"DENIED","Revoked");return actor;}UUID developer(HttpServletRequest r,boolean approved){return user(r);}}
 static class Repo {UUID seller;Map<String,Object> template(UUID t,boolean lock){return Map.of("seller_id",seller);}Map<String,Object> version(UUID t,UUID v,boolean lock){return Map.of("id",v);}}
 static class Publishing {void lock(UUID seller){}}
 static class ResponseEntity<T> {final T bytes;ResponseEntity(T bytes){this.bytes=bytes;}}
 final Repo repo=new Repo();final Security security=new Security();final Publishing publishing=new Publishing();Runnable storage=()->{};int reads;
 <T>T tx(java.util.function.Supplier<T> work){return work.get();}
 ResponseEntity<byte[]> archiveResponse(Map<String,Object> version){reads++;storage.run();return new ResponseEntity<>(new byte[]{1,2,3});}
 ${javaMethod(text,'private Map<String,Object> owner(')}
 ${text.includes('private Map<String,Object> readablePackage(')?javaMethod(text,'private Map<String,Object> readablePackage('):''}
 ${javaMethod(text,'public ResponseEntity<byte[]> packageDownload(')}
 public static void main(String[] args){int checks=0;var failures=new ArrayList<String>();
  for(boolean admin:List.of(false,true))for(String change:args[0].equals("current")?List.of("none"):admin?List.of("session","role","mfa","recency"):List.of("session")){
   var service=new MarketplaceAuthorityHarness();var request=new HttpServletRequest();service.repo.seller=request.actor;
   service.storage=()->{if(change.equals("session"))request.active=false;if(change.equals("role"))request.admin=false;if(change.equals("mfa"))request.mfa=false;if(change.equals("recency"))request.recent=false;};
   boolean denied=false;try{var response=service.packageDownload(UUID.randomUUID(),UUID.randomUUID(),request,admin);if(response.bytes.length!=3)throw new AssertionError("Current actor did not receive exact bytes");}catch(ApiError error){denied=true;}
   if(denied==change.equals("none"))failures.add("Private source bytes escaped after "+change+" revocation; admin="+admin);
   if(service.reads!=1)throw new AssertionError("Storage boundary was not exercised exactly once");checks++;
  }if(!failures.isEmpty())throw new AssertionError(failures);System.out.println("MARKETPLACE_AUTHORITY_OK checks="+checks);
 }
}`;
 const directory=mkdtempSync(join(tmpdir(),'round3-marketplace-'));
 try{writeFileSync(join(directory,'MarketplaceAuthorityHarness.java'),java);const result=spawnSync('java',['MarketplaceAuthorityHarness.java',mode],{cwd:directory,encoding:'utf8',timeout:30000});assert.equal(result.status,0,String(result.error||'')+result.stdout+result.stderr);assert.match(result.stdout,/MARKETPLACE_AUTHORITY_OK/);}
 finally{rmSync(directory,{recursive:true,force:true});}
});
