import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import test from 'node:test';

// Execute production routines with state/transport boundaries replaced. React lifecycle
// and controls are separately exercised by tests/browser/round2-identity.spec.ts.
const source=readFileSync(new URL('../app/components/client-requests.tsx',import.meta.url),'utf8');
const confirmation=readFileSync(new URL('../app/components/confirmation.tsx',import.meta.url),'utf8');
const apiSource=stripTypeScriptTypes(readFileSync(new URL('../lib/api.ts',import.meta.url),'utf8'),{mode:'transform'});
const {ApiError}=await import('data:text/javascript;base64,'+Buffer.from(apiSource).toString('base64'));
function routine(start,end,context,text=source){
 const first=text.indexOf(start),last=text.indexOf(end,first);assert.ok(first>=0&&last>first,'Production routine boundaries exist');
 return Function(...Object.keys(context),stripTypeScriptTypes(text.slice(first,last),{mode:'transform'})+';return '+start.match(/function (\w+)/)[1]+';')(...Object.values(context));
}
function deferred(){let resolve,reject;const promise=new Promise((yes,no)=>{resolve=yes;reject=no});return{promise,resolve,reject};}
function fixture(extra={}){
 const state={setItems:[row],setEpoch:0},context={ApiError,verified:true,active:{current:true},listGeneration:{current:0},historyGeneration:{current:0},mutation:{current:false},refreshNeeded:false,...extra};
 for(const name of new Set(source.match(/\bset[A-Z]\w*/g)))context[name]=value=>{state[name]=typeof value==='function'?value(state[name]):value;};
 context.closeRequest=routine('function closeRequest(', '\n  function failed(',context);
 context.failed=routine('function failed(', '\n  async function openRequest(',context);
 context.load=routine('async function load(', '\n  useEffect(() => { load();',context);
 return {state,context,act:()=>routine('async function act(','\n  const kind',context)};
}
const row={id:'owned',projectTitle:'Owned project',builder:'Builder',description:'Private client brief',budgetBand:'NEED_ESTIMATE',timelineBand:'FLEXIBLE',status:'INQUIRY_RECEIVED',moderationStatus:'CLEAR',reviewSubmitted:false};
const page={items:[row],page:0,size:1,hasMore:true};

test('client request reads reject an old identity and an older generation, and accept the current response',async()=>{
 const old=deferred(),{state,context}=fixture({api:()=>old.promise});const pending=context.load();
 context.active.current=false;old.resolve({...page,items:[{...row,id:'stale'}]});await pending;
 assert.deepEqual(state.setItems,[row]);
 const first=deferred(),second=deferred();let calls=0;
 const current=fixture({api:()=>++calls===1?first.promise:second.promise});
 const before=current.context.load(),after=current.context.load();second.resolve({...page,items:[{...row,id:'current'}]});await after;first.reject(new Error('Old read failed'));await before;
 assert.equal(current.state.setItems[0].id,'current');assert.equal(current.state.setError,'');assert.equal(current.state.setLoading,false);
});

for(const status of [401,403])test(`a current ${status} clears private request context and does not leave loading stuck`,async()=>{
 const {state,context}=fixture({api:async()=>{throw new ApiError('Authority lost','FORBIDDEN',status);}});
 await context.load();assert.deepEqual(state.setItems,[]);assert.equal(state.setPage,null);assert.equal(state.setSelected,null);assert.equal(state.setLoading,false);assert.equal(state.setRefreshNeeded,true);assert.equal(state.setError,'Authority lost');
});

test('a transient list failure preserves verified rows and exposes a finished retry state',async()=>{
 const {state,context}=fixture({api:async()=>{throw new ApiError('Temporarily unavailable','UNAVAILABLE',503);}});
 assert.equal(await context.load(),false);assert.deepEqual(state.setItems,[row]);assert.equal(state.setLoading,false);assert.equal(state.setPage,null);assert.equal(state.setError,'Temporarily unavailable');
});

test('an accepted mutation and failed refresh preserve acceptance and recover through a read without a second write',async()=>{
 const calls=[];let available=false;
 const {state,context,act}=fixture({api:async(path,init)=>{calls.push({path,method:init?.method||'GET'});if(init?.method==='POST')return{ok:true};if(!available)throw new ApiError('List unavailable','UNAVAILABLE',503);return{...page,items:[],hasMore:false};}});
 await act()(row,'not-hired');assert.match(state.setMessage,/Your response has been recorded.*could not be refreshed/);assert.equal(state.setBusy,false);assert.equal(state.setRefreshNeeded,true);assert.equal(state.setPage,null);
 available=true;assert.equal(await context.load(),true);assert.deepEqual(state.setItems,[]);assert.equal(state.setRefreshNeeded,false);assert.deepEqual(calls.map(call=>call.method),['POST','GET','GET']);
});

test('a rejected mutation keeps its rejection visible after the current requests refresh',async()=>{
 const {state,act}=fixture({api:async(_path,init)=>{if(init?.method==='POST')throw new ApiError('Request already changed','CONFLICT',409);return page;}});
 await act()(row,'not-hired');assert.equal(state.setError,'Request already changed');assert.equal(state.setMessage,'');assert.equal(state.setPage.page,0);assert.equal(state.setBusy,false);
});

test('an uncertain mutation requires a read before another write and an unmounted response publishes nothing',async()=>{
 const failed=fixture({api:async()=>{throw new TypeError('Connection interrupted');}});await failed.act()(row,'not-hired');assert.equal(failed.state.setRefreshNeeded,true);assert.match(failed.state.setMessage,/could not be confirmed/);assert.equal(failed.state.setBusy,false);
 const response=deferred(),late=fixture({api:()=>response.promise});const pending=late.act()(row,'not-hired');late.context.active.current=false;response.resolve({ok:true});await pending;assert.equal(late.state.setMessage,'');assert.equal(late.state.setRefreshNeeded,undefined);
});

test('link-context retry rejects an older link and distinguishes transient failure from expiry',async()=>{
 const state={},pending=deferred(),context={ApiError,linkRevision:{current:0},api:()=>pending.promise};
 for(const name of new Set(confirmation.match(/\bset[A-Z]\w*/g)))context[name]=value=>{state[name]=value;};
 const check=routine('async function checkLink(', '\n  useEffect(',context,confirmation),old=check('synthetic-token-one');context.linkRevision.current++;pending.resolve({kind:'EMAIL_VERIFICATION',used:false});await old;assert.equal(state.setContext,null);
 context.api=async()=>{throw new ApiError('Lookup unavailable','UNAVAILABLE',503);};await routine('async function checkLink(', '\n  useEffect(',context,confirmation)('synthetic-token-two');assert.equal(state.setCanRetry,true);assert.equal(state.setError,'Lookup unavailable');
 context.api=async()=>{throw new ApiError('Link expired','TOKEN_EXPIRED',410);};await routine('async function checkLink(', '\n  useEffect(',context,confirmation)('synthetic-token-two');assert.equal(state.setCanRetry,false);
 context.api=async(_path,init)=>{assert.equal(JSON.parse(init.body).token,'synthetic-token-two');return{kind:'EMAIL_VERIFICATION',used:false};};await routine('async function checkLink(', '\n  useEffect(',context,confirmation)('synthetic-token-two');assert.equal(state.setError,'');assert.deepEqual(state.setContext,{kind:'EMAIL_VERIFICATION',used:false});
});
