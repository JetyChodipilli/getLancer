import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import test from 'node:test';

// Execute the actual production async routines. Real React/dialog/transport
// acceptance is separately specified in tests/browser/round3-identity.spec.ts.
const workspace=readFileSync(process.env.ROUND3_IDENTITY_SOURCE||new URL('../app/workspace/page.tsx',import.meta.url),'utf8');
const apiSource=stripTypeScriptTypes(readFileSync(new URL('../lib/api.ts',import.meta.url),'utf8'),{mode:'transform'});
const {ApiError}=await import('data:text/javascript;base64,'+Buffer.from(apiSource).toString('base64'));
function bodyAt(source,start){
 const first=source.indexOf(start);assert.ok(first>=0,'Production routine exists: '+start);
 const begin=first+start.length;let depth=1,quote='',escape=false;
 for(let i=begin;i<source.length;i++){
  const c=source[i];
  if(quote){if(escape)escape=false;else if(c==='\\')escape=true;else if(c===quote)quote='';continue;}
  if(c==='"'||c==="'"||c==='`'){quote=c;continue;}
  if(c==='{')depth++;if(c==='}'&&--depth===0)return source.slice(begin,i);
 }
 throw new Error('Unclosed production routine');
}
function routine(start,parameters,context,legacy){
 const body=bodyAt(workspace,workspace.includes(start)?start:legacy);
 return Function(...Object.keys(context),stripTypeScriptTypes('async function run('+parameters+'){'+body+'}',{mode:'transform'})+';return run;')( ...Object.values(context));
}
function deferred(){let resolve,reject;const promise=new Promise((yes,no)=>{resolve=yes;reject=no});return{promise,resolve,reject};}
function fixture(extra={}){
 const state={setInquiries:[{id:'private',description:'Private brief'}],setHistory:null,setInquiryPage:{page:0,hasMore:true},setGeneration:0};
 const context={ApiError,loadGeneration:{current:0},currentUser:{current:{id:'builder'}},inquiryGeneration:{current:0},historyGeneration:{current:0},mounted:{current:true},URLSearchParams,FormData:class{forEach(fn){fn('DISCUSSION','status');}},...extra};
 for(const name of new Set(workspace.match(/\bset[A-Z]\w*/g)))context[name]=value=>{state[name]=typeof value==='function'?value(state[name]):value;};
 context.closeHistory=()=>{context.historyGeneration.current++;context.setHistory(null);};
 context.clearPrivate=()=>{context.currentUser.current=null;context.inquiryGeneration.current++;context.historyGeneration.current++;context.setInquiries([]);context.setHistory(null);context.setInquiryPage(null);};
 if(workspace.includes('function closeHistory(){')){context.closeHistory=Function(...Object.keys(context),'return function(){'+bodyAt(workspace,'function closeHistory(){')+'}')( ...Object.values(context));context.clearPrivate=Function(...Object.keys(context),'return function(invalidate=true){'+bodyAt(workspace,'function clearPrivate(invalidate=true){')+'}')( ...Object.values(context));}
 if(workspace.includes('function inquiryFailure(problem:unknown){'))context.inquiryFailure=Function(...Object.keys(context),stripTypeScriptTypes('function run(problem:unknown){'+bodyAt(workspace,'function inquiryFailure(problem:unknown){')+'}',{mode:'transform'})+';return run;')(...Object.values(context));
 return {state,context,
  history:()=>routine('async function openHistory(id:string){','id',context,'onClick={async()=>{try{setHistory('),
  filter:()=>routine('async function filterInquiries(event:FormEvent<HTMLFormElement>){','event',context,'onSubmit={async e=>{e.preventDefault();const params=new URLSearchParams();')};
}
// Legacy inline bodies are kept only so the same checks can demonstrate the
// original baseline failure instead of failing because a new helper is absent.
function history(f){
 if(workspace.includes('async function openHistory(id:string){'))return f.history();
 const start='onClick={async()=>{try{setHistory(',first=workspace.indexOf(start),end=workspace.indexOf('}}>History</button>',first);
 assert.ok(first>=0&&end>first);return Function(...Object.keys(f.context),stripTypeScriptTypes('async function run(id){const i={id};'+workspace.slice(first+'onClick={async()=>{'.length,end)+'}',{mode:'transform'})+';return run;')( ...Object.values(f.context));
}
function filter(f){
 if(workspace.includes('async function filterInquiries(event:FormEvent<HTMLFormElement>){'))return f.filter();
 const start='onSubmit={async e=>{e.preventDefault();const params=new URLSearchParams();',first=workspace.indexOf(start),end=workspace.indexOf('}}}><label htmlFor="inquiry-status">',first);
 assert.ok(first>=0&&end>first);return Function(...Object.keys(f.context),stripTypeScriptTypes('async function run(e){'+workspace.slice(first+'onSubmit={async e=>{'.length,end+1)+'}',{mode:'transform'})+';return run;')( ...Object.values(f.context));
}
const event={preventDefault(){},currentTarget:{}};

test('qualified inquiry history accepts the current response and ignores an older selection',async()=>{
 const old=deferred(),current=deferred();let calls=0;const f=fixture({api:()=>++calls===1?old.promise:current.promise}),open=history(f);
 const before=open('first'),after=open('second');current.resolve({events:[{event_type:'CURRENT'}]});await after;assert.equal(f.state.setHistory.events[0].event_type,'CURRENT');old.resolve({events:[{event_type:'STALE'}]});await before;
 assert.equal(f.state.setHistory.events[0].event_type,'CURRENT');
});

test('closing inquiry history or replacing the private context invalidates delayed results',async()=>{
 for(const invalidate of ['closeHistory','clearPrivate']){
  const response=deferred(),f=fixture({api:()=>response.promise}),pending=history(f)('first');f.context[invalidate]();response.resolve({events:[{event_type:'OBSOLETE'}]});await pending;assert.equal(f.state.setHistory,null);
 }
});

for(const status of [401,403])test(`current qualified inquiry read denial (${status}) clears protected workspace context`,async()=>{
 const f=fixture({api:async()=>{throw new ApiError('Builder authority lost','FORBIDDEN',status);}});await history(f)('first');
 assert.deepEqual(f.state.setInquiries,[]);assert.equal(f.state.setInquiryPage,null);assert.equal(f.state.setHistory,null);assert.equal(f.state.setError,'Builder authority lost');
});

test('an obsolete filter failure cannot replace the current filters or error state',async()=>{
 const old=deferred(),current=deferred();let calls=0;const f=fixture({api:()=>++calls===1?old.promise:current.promise}),apply=filter(f);
 const before=apply(event),after=apply(event);current.resolve({items:[{id:'current'}],page:0,size:20,hasMore:false});await after;old.reject(new ApiError('Obsolete failure','UNAVAILABLE',503));await before;
 assert.equal(f.state.setInquiries[0].id,'current');assert.equal(f.state.setError,'');assert.equal(f.state.setBusy,false);
});

test('a transient current history failure preserves verified rows and reports recovery',async()=>{
 const f=fixture({api:async()=>{throw new ApiError('History unavailable','UNAVAILABLE',503);}});await history(f)('first');assert.equal(f.state.setInquiries[0].id,'private');assert.equal(f.state.setError,'History unavailable');
});

for(const status of [401,403])test(`a current qualified filter denial (${status}) clears cached private rows`,async()=>{
 const f=fixture({api:async()=>{throw new ApiError('Filter authority lost','FORBIDDEN',status);}});await filter(f)(event);assert.deepEqual(f.state.setInquiries,[]);assert.equal(f.state.setInquiryPage,null);assert.equal(f.state.setBusy,false);assert.equal(f.state.setError,'Filter authority lost');
});

test('obsolete history denial and unmounted reads cannot clear a newer workspace',async()=>{
 const first=deferred(),second=deferred();let calls=0;const f=fixture({api:()=>++calls===1?first.promise:second.promise}),open=history(f),old=open('first'),current=open('second');second.resolve({events:[{event_type:'CURRENT'}]});await current;first.reject(new ApiError('Old authority denial','FORBIDDEN',403));await old;assert.equal(f.state.setInquiries[0].id,'private');assert.equal(f.state.setHistory.events[0].event_type,'CURRENT');assert.equal(f.state.setError,'');
 for(const kind of ['history','filter']){const response=deferred(),hidden=fixture({api:()=>response.promise}),pending=kind==='history'?history(hidden)('first'):filter(hidden)(event);hidden.context.mounted.current=false;response.resolve(kind==='history'?{events:[]}:{items:[{id:'OBSOLETE'}],page:0,hasMore:false});await pending;assert.equal(hidden.state.setInquiries[0].id,'private');assert.equal(hidden.state.setHistory,null);}
});

const account=readFileSync(process.env.ROUND3_IDENTITY_ACCOUNT_SOURCE||new URL('../app/components/account-settings.tsx',import.meta.url),'utf8');
function accountRoutine(start,context){
 const begin=account.indexOf(start),end=account.indexOf('\n  ',begin+start.length);assert.ok(begin>=0&&end>begin);
 const declaration=account.slice(begin,end),name=start.match(/function (\w+)/)[1];
 return Function(...Object.keys(context),stripTypeScriptTypes(declaration,{mode:'transform'})+';return '+name+';')(...Object.values(context));
}
function accountFixture(extra={}){
 const state={setNotifications:[{id:'private-notification',title:'Private account notification'}]},context={ApiError,active:{current:true},generation:{current:0},reads:{current:new Set()},mutation:{current:false},...extra};
 for(const name of new Set(account.match(/\bset[A-Z]\w*/g)))context[name]=value=>{state[name]=typeof value==='function'?value(state[name]):value;};
 if(account.includes('function failed(problem:unknown){'))context.failed=accountRoutine('function failed(problem:unknown){',context);
 return{state,context,mark:()=>accountRoutine('async function markRead(',context),export:()=>accountRoutine('async function exportData(',context)};
}
for(const status of [401,403])test(`notification write denial (${status}) clears protected account notifications`,async()=>{
 const f=accountFixture({api:async()=>{throw new ApiError('Notification authority lost','FORBIDDEN',status);}});await f.mark()('private-notification');assert.deepEqual(f.state.setNotifications,[]);assert.equal(f.state.setNotificationPage,null);assert.equal(f.state.setNotificationError,'Notification authority lost');
});

test('a denied notification write invalidates an earlier account export before it can download',async()=>{
 const exportResponse=deferred();let downloads=0;
 const f=accountFixture({api:path=>path==='/me/export'?exportResponse.promise:Promise.reject(new ApiError('Account session expired','UNAUTHENTICATED',401)),URL:{createObjectURL:()=>{downloads++;return'blob:private';},revokeObjectURL(){}},Blob,document:{createElement:()=>({click(){downloads++;}})},setTimeout:fn=>fn()});
 const pending=f.export()();await f.mark()('private-notification');exportResponse.resolve({account:{email:'private@example.test'}});await pending;assert.equal(downloads,0);assert.equal(f.state.setMessage,'');
});

test('a transient notification failure retains rows and permits a successful retry',async()=>{
 let failure=true;const f=accountFixture({api:async()=>{if(failure)throw new ApiError('Read temporarily unavailable','UNAVAILABLE',503);return{ok:true};}});await f.mark()('private-notification');assert.equal(f.state.setNotifications.length,1);assert.equal(f.state.setError,'Read temporarily unavailable');failure=false;await f.mark()('private-notification');assert.ok(f.state.setNotifications[0].read_at);assert.equal(f.state.setError,'');
});
