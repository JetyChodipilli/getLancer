import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import {runInNewContext} from 'node:vm';
import {randomUUID} from 'node:crypto';

// Execute the actual production async handlers without requiring a React runtime.
const source=readFileSync(new URL('../app/components/delivery-workspace.tsx',import.meta.url),'utf8');
const code=(start,end)=>stripTypeScriptTypes(source.slice(source.indexOf(start),source.indexOf(end,source.indexOf(start))),{mode:'transform'});
const changeCode=code(' async function change(',' function act('),payCode=code(' async function pay(',' const allPaid='),availableCode=code('function actionAvailable(','const date=');
class ApiError extends Error{constructor(message,code,status){super(message);this.code=code;this.status=status;}}
const deferred=()=>{let resolve,reject;const promise=new Promise((yes,no)=>{resolve=yes;reject=no;});return {promise,resolve,reject};};
function harness(){
 const state={busy:false,error:'',message:'',dialog:'proposal',cleared:0,loads:[],posts:[],orders:[],gateways:[]};
 const record={status:'OPEN',side:'SELLER',proposals:[{status:'DRAFT',revision:1}],milestones:[]};
 const context={ApiError,Error,Map,Set,crypto:{randomUUID},preview:null,config:{enabled:true},detail:{side:'BUYER',title:'Current delivery'},blocked:false,selected:'engagement-a',dialog:'proposal',payments:[],mutating:{current:false},generation:{current:1},checkoutAlive:{current:true},checkoutGeneration:{current:0},gatewayRef:{current:null},paymentKeys:{current:new Map()},milestonePayment:(rows,id)=>rows.find(row=>row.milestoneId===id),setBusy:value=>{state.busy=value;},setError:value=>{state.error=value;},setMessage:value=>{state.message=value;},setDialog:value=>{state.dialog=value;},clearProtected:()=>{state.cleared++;},record};
 context.invalidateCheckout=()=>{context.checkoutGeneration.current++;context.gatewayRef.current?.close?.();context.gatewayRef.current=null;};
 context.load=async id=>{context.invalidateCheckout();context.generation.current++;state.loads.push(id);return {record};};
 context.api=async(path,options)=>{state.orders.push({path,key:options.headers['Idempotency-Key']});return {id:'payment-a',orderId:'order_fixture',keyId:'rzp_test_fixture',status:'ORDER_CREATED',amountMinor:10000,currency:'INR'};};
 context.post=async(...args)=>{state.posts.push(args);};
 context.loadCheckout=async()=>class{constructor(options){this.options=options;state.gateways.push(this);}open(){}close(){this.closed=true;}on(event,handler){this[event]=handler;}};
 const handlers=runInNewContext(availableCode+changeCode+payCode+';({change,pay,actionAvailable})',context);
 return {...handlers,state,context,record};
}

test('delivery validation retains the current form and retries the same mutation successfully',async()=>{
 const {change,state}=harness();await assert.rejects(change(()=>{throw new ApiError('Check milestone dates.','VALIDATION_ERROR',400);}),/Check milestone dates/);
 assert.equal(state.dialog,'proposal');assert.equal(state.cleared,0);assert.equal(state.loads.length,0);assert.equal(state.busy,false);
 await change(()=>({id:'proposal-new'}),'Saved draft.');assert.deepEqual(state.loads,['engagement-a']);assert.equal(state.dialog,'');assert.equal(state.message,'Saved draft.');
});
test('an uncertain committed proposal refreshes its actual state and closes an ineligible confirmation',async()=>{
 const {change,state,context,record}=harness();context.dialog='send';record.proposals[0].status='SENT';
 await assert.rejects(change(()=>{throw new ApiError('Response lost after commit.','UNAVAILABLE',503);}),/Response lost/);
 assert.equal(state.cleared,0);assert.deepEqual(state.loads,['engagement-a']);assert.equal(state.dialog,'');assert.equal(state.message,'');assert.equal(state.error,'Response lost after commit.');
});
for(const outcome of ['success','failure'])test('late delivery mutation '+outcome+' cannot alter another selection or close its dialog',async()=>{
 const {change,state,context}=harness(),pending=deferred();const run=change(()=>pending.promise,'Old action saved.');
 context.generation.current++;state.dialog='new-selection-dialog';state.error='';
 if(outcome==='success')pending.resolve({id:'old-record'});else pending.reject(new ApiError('Old failure.','UNAVAILABLE',503));
 await run;assert.equal(state.dialog,'new-selection-dialog');assert.equal(state.error,'');assert.equal(state.message,'');assert.equal(state.loads.length,0);
});
test('a saved action with an unavailable refresh never reports success',async()=>{
 const {change,state,context}=harness();context.load=async()=>{context.generation.current++;return false;};
 await assert.rejects(change(()=>({id:'saved-record'}),'Saved.'),/saved, but the workspace could not be refreshed/);assert.equal(state.message,'');assert.equal(state.busy,false);
});
test('a closed delivery checkout cannot verify or overwrite a later selection',async()=>{
 const {pay,state,context}=harness();await pay('milestone-a');const gateway=state.gateways[0];gateway.options.modal.ondismiss();
 context.selected='engagement-b';context.generation.current++;await gateway.options.handler({razorpay_payment_id:'pay_late',razorpay_signature:'late'});
 assert.equal(state.posts.length,0);assert.deepEqual(state.loads,['engagement-a']);assert.equal(gateway.closed,true);
});
test('checkout dismissal during verification does not unlock another payment or start a competing reload',async()=>{
 const {pay,state,context}=harness(),pending=deferred();context.post=async(...args)=>{state.posts.push(args);return pending.promise;};
 await pay('milestone-a');const gateway=state.gateways[0],verification=gateway.options.handler({razorpay_payment_id:'pay_valid',razorpay_signature:'signed'});
 gateway.options.modal.ondismiss();assert.equal(state.busy,true);assert.equal(state.loads.length,0);
 pending.resolve({});await verification;assert.equal(state.posts.length,1);assert.deepEqual(state.loads,['engagement-a']);assert.equal(state.busy,false);assert.match(state.message,/server verification/);
});
test('definitive provider rejection permits a fresh key while an uncertain order keeps its original key',async()=>{
 for(const [code,status,newKey] of [['PAYMENT_ORDER_REJECTED',502,true],['PAYMENT_ORDER_UNKNOWN',502,false]]){
  const {pay,state,context}=harness();context.api=async(path,options)=>{state.orders.push(options.headers['Idempotency-Key']);throw new ApiError('Provider unavailable.',code,status);};
  await pay('milestone-a');await pay('milestone-a');assert.equal(state.orders.length,2);assert.equal(state.orders[0]!==state.orders[1],newKey);assert.equal(state.gateways.length,0);
 }
});

const operationSource=readFileSync(new URL('../app/components/maintenance-operations.tsx',import.meta.url),'utf8');
const operationsCode=stripTypeScriptTypes(operationSource.slice(operationSource.indexOf(' async function load('),operationSource.indexOf(' return <details')),{mode:'transform'});
const pageSource=readFileSync(new URL('../app/components/delivery-operations.tsx',import.meta.url),'utf8');
const pageCode=stripTypeScriptTypes(pageSource.slice(pageSource.indexOf('export function operationPage('),pageSource.indexOf('export function appendOperationRecords(')).replace('export ',''),{mode:'transform'});
const operationPage=items=>({items,page:0,size:12,hasMore:false,totalItems:items.length,totalPages:items.length?1:0});
function operations(){
 const state={page:null,busy:false,error:'',note:''},context={ApiError,JSON,Error,page:null,mounted:{current:true},generation:{current:0},mutating:{current:false},setPage:value=>{state.page=value;},setBusy:value=>{state.busy=value;},setError:value=>{state.error=value;},setNote:value=>{state.note=value;},api:async()=>operationPage([{id:'current'}])};
 return {...runInNewContext(pageCode+operationsCode+';({load,save})',context),state,context};
}
test('an older maintenance operations response cannot restore records after a newer denied refresh',async()=>{
 const {load,state,context}=operations(),old=deferred();context.api=()=>old.promise;const first=load();
 context.api=async()=>{throw new ApiError('Administrator MFA was revoked.','FORBIDDEN',403);};await load();old.resolve(operationPage([{id:'private-old'}]));await first;
 assert.equal(state.page,null);assert.equal(state.error,'Administrator MFA was revoked.');assert.equal(state.busy,false);
});
test('maintenance operations reject an unbounded or mismatched page without rendering private records',async()=>{
 const {load,state,context}=operations();for(const invalid of [{...operationPage([{id:'old'}]),page:1},{...operationPage([{id:'old'}]),size:1000}]){context.api=async()=>invalid;assert.equal(await load(),false);assert.equal(state.page,null);assert.match(state.error,/could not be verified/);}
});
test('closing maintenance operations invalidates a pending read',async()=>{
 const {load,state,context}=operations(),pending=deferred();context.api=()=>pending.promise;const loading=load();context.generation.current++;pending.resolve(operationPage([{id:'old'}]));await loading;assert.equal(state.page,null);assert.equal(state.note,'');
});
