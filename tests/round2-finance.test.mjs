import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import {runInNewContext} from 'node:vm';
import test from 'node:test';

// Run production handlers with controlled state/HTTP boundaries. Real rendering is
// covered separately by round2-finance.spec.ts; this is not a React/SQL test.
const delivery=readFileSync(new URL('../app/components/delivery-operations.tsx',import.meta.url),'utf8');
const maintenance=readFileSync(new URL('../app/components/maintenance-operations.tsx',import.meta.url),'utf8');
const extract=(source,start,end)=>{
 const first=source.indexOf(start),last=source.indexOf(end,first);
 assert.ok(first>=0&&last>first,'Actual production routine boundaries exist');
 return stripTypeScriptTypes(source.slice(first,last),{mode:'transform'});
};
const pageCode=extract(delivery,'export function operationPage(','export function appendOperationRecords(').replace('export ','');
const deliveryCode=extract(delivery,' const clear=useCallback(',' const pageRequest=')+extract(delivery,' async function save(',' function more(');
const maintenanceCode=extract(maintenance,' async function load(',' return <details');
class ApiError extends Error{constructor(message,code,status){super(message);this.code=code;this.status=status;}}
const defer=()=>{let resolve,reject;const promise=new Promise((yes,no)=>{resolve=yes;reject=no;});return {promise,resolve,reject};};
const page=items=>({items,page:0,size:12,hasMore:false,totalItems:items.length,totalPages:items.length?1:0});
function operations(kind='delivery'){
 const state={pages:{},page:null,loading:{},errors:{},busy:false,blocked:false,error:'',note:'',epoch:0};
 const context={ApiError,JSON,Error,Object,Number,Promise,page:null,useCallback:fn=>fn,mounted:{current:true},generation:{current:0},mutating:{current:false},versions:{current:{disputes:0,accounts:0,attention:0}},paths:{disputes:'/admin/delivery/disputes',accounts:'/admin/payments/accounts',attention:'/admin/payments/attention'}};
 for(const key of Object.keys(state))context['set'+key[0].toUpperCase()+key.slice(1)]=value=>{state[key]=typeof value==='function'?value(state[key]):value;};
 context.api=async(_path,options)=>options?.method==='POST'?{status:'RESOLVED'}:page([{id:'current'}]);
 return {...runInNewContext(pageCode+(kind==='delivery'?deliveryCode:maintenanceCode)+(kind==='delivery'?';({load,save,clear})':';({load,save})'),context),state,context};
}

test('delivery decisions report success only after all current collections refresh',async()=>{
 const {load,save,state}=operations();await load();await save('/admin/delivery/disputes/current/resolve',{resolution:'RESUME',reason:'Reviewed evidence.'});
 assert.equal(state.note,'Decision saved.');assert.equal(state.error,'');assert.equal(state.busy,false);
 assert.deepEqual(Object.keys(state.pages).sort(),['accounts','attention','disputes']);
});

for(const status of [400,409,503])test('a rejected or unconfirmed delivery POST '+status+' retains the decision without automatically replaying it',async()=>{
 const {load,save,state,context}=operations();await load();const prior=state.pages;let writes=0,reads=0;
 context.api=async(_path,options)=>{if(options?.method==='POST'){writes++;throw new ApiError('Operator decision could not be confirmed.','OPERATION_UNAVAILABLE',status);}reads++;return page([]);};
 await save('/admin/delivery/disputes/current/resolve',{resolution:'RESUME',reason:'Retained decision evidence.'});
 assert.equal(writes,1);assert.equal(reads,0);assert.equal(state.pages,prior);assert.equal(state.note,'');assert.equal(state.error,'Operator decision could not be confirmed.');assert.equal(state.busy,false);
});

for(const status of [401,403,503])test('accepted delivery decision with '+status+' refresh never claims a refreshed success',async()=>{
 const {load,save,state,context}=operations();await load();let writes=0;
 context.api=async(path,options)=>{if(options?.method==='POST'){writes++;return{status:'RESOLVED'};}if(path.startsWith('/admin/payments/accounts'))throw new ApiError('Current accounts unavailable.','UNAVAILABLE',status);return page([{id:'current'}]);};
 await save('/admin/delivery/disputes/current/resolve',{resolution:'RESUME',reason:'Reviewed evidence.'});
 assert.equal(writes,1,'Refresh failure must never replay an accepted decision');assert.equal(state.note,'');
 assert.match(state.error,status<500?/Current accounts unavailable/:/decision was saved, but current operations could not be refreshed/i);
 assert.equal(state.busy,false);if(status<500){assert.equal(state.blocked,true);assert.equal(Object.keys(state.pages).length,0);}
});

for(const kind of ['delivery','maintenance'])test('closing '+kind+' operations during post-write refresh suppresses its late success',async()=>{
 const {load,save,clear,state,context}=operations(kind);await load();const pending=defer();let refreshStarted=false;
 context.api=async(_path,options)=>{if(options?.method==='POST')return{status:'RESOLVED'};refreshStarted=true;return pending.promise;};
 const saving=kind==='delivery'?save('/admin/delivery/disputes/current/resolve',{}):save('billing-current',{action:'reconcile',reason:'Reviewed provider evidence.'});
 for(let n=0;n<10&&!refreshStarted;n++)await Promise.resolve();assert.equal(refreshStarted,true,'The actual post-write GET started before close');
 if(kind==='delivery')clear();else{context.generation.current++;state.page=null;}state.note='';pending.resolve(page([{id:'late-private'}]));await saving;
 assert.equal(state.note,'');assert.equal(state.page,null);assert.equal(Object.keys(state.pages).length,0);
});

test('accepted maintenance action distinguishes unavailable refresh from rejected mutation',async()=>{
 const {load,save,state,context}=operations('maintenance');await load();let writes=0;
 context.api=async(_path,options)=>{if(options?.method==='POST'){writes++;return{status:'ACTIVE'};}throw new ApiError('Provider attention unavailable.','UNAVAILABLE',503);};
 await assert.rejects(save('billing-current',{action:'reconcile',reason:'Reviewed provider evidence.'}),/action was saved, but its refreshed status is unavailable/);
 assert.equal(writes,1);assert.equal(state.note,'');assert.match(state.error,/action was saved, but its refreshed status is unavailable/);assert.equal(state.page,null);assert.equal(state.busy,false);
});

test('a current maintenance refresh still reports its confirmed provider state',async()=>{
 const {save,state}=operations('maintenance');await save('billing-current',{action:'reconcile',reason:'Reviewed provider evidence.'});
 assert.equal(state.note,'Operator action recorded. Provider state: RESOLVED');assert.equal(state.error,'');assert.equal(state.busy,false);
});
