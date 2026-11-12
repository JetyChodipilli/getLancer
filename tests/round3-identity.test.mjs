import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import test from 'node:test';
import ts from 'typescript';

// Execute the actual production routines and their authorization callbacks.
// React/dialog/transport acceptance is separately covered by the browser tests.
const workspace=readFileSync(process.env.ROUND3_IDENTITY_SOURCE||new URL('../app/workspace/page.tsx',import.meta.url),'utf8');
const account=readFileSync(process.env.ROUND3_IDENTITY_ACCOUNT_SOURCE||new URL('../app/components/account-settings.tsx',import.meta.url),'utf8');
const apiSource=stripTypeScriptTypes(readFileSync(new URL('../lib/api.ts',import.meta.url),'utf8'),{mode:'transform'});
const {ApiError}=await import('data:text/javascript;base64,'+Buffer.from(apiSource).toString('base64'));

function productionScope(source,name){
 const tree=ts.createSourceFile(name+'.tsx',source,ts.ScriptTarget.Latest,true,ts.ScriptKind.TSX);
 const matches=tree.statements.filter(node=>ts.isFunctionDeclaration(node)&&node.name?.text===name);
 assert.equal(matches.length,1,'Exactly one actual '+name+' component must exist');
 assert.ok(matches[0].body,'The actual '+name+' component has a body');
 return{tree,statements:matches[0].body.statements};
}
function routine(scope,name){
 const matches=scope.statements.filter(node=>ts.isFunctionDeclaration(node)&&node.name?.text===name);
 assert.equal(matches.length,1,'Exactly one actual '+name+' routine must exist');
 return matches[0].getText(scope.tree);
}
function callback(scope,name){
 const matches=scope.statements.filter(ts.isVariableStatement).flatMap(node=>[...node.declarationList.declarations]).filter(node=>ts.isIdentifier(node.name)&&node.name.text===name);
 assert.equal(matches.length,1,'Exactly one actual '+name+' callback must exist');
 const initializer=matches[0].initializer;
 assert.ok(initializer&&ts.isCallExpression(initializer)&&['useCallback','useMemo'].includes(initializer.expression.getText(scope.tree)),'The actual '+name+' callback is initialized by React');
 return 'const '+matches[0].getText(scope.tree)+';';
}
function evaluate(declarations,context,names){
 const code=stripTypeScriptTypes(declarations.join('\n'),{mode:'transform'});
 return Function(...Object.keys(context),code+'\nreturn {'+names.join(',')+'};')(...Object.values(context));
}
function setters(source,state){
 return Object.fromEntries([...new Set(source.match(/\bset[A-Z]\w*/g))].map(name=>[name,value=>{state[name]=typeof value==='function'?value(state[name]):value;}]));
}
function deferred(){let resolve,reject;const promise=new Promise((yes,no)=>{resolve=yes;reject=no});return{promise,resolve,reject};}
const workspaceScope=productionScope(workspace,'Workspace');
const workspaceNames=['clearPrivate','request','closeHistory','inquiryFailure','openHistory','filterInquiries','load'];
const workspaceDeclarations=[routine({tree:workspaceScope.tree,statements:workspaceScope.tree.statements},'uncertain'),callback(workspaceScope,'clearPrivate'),callback(workspaceScope,'request'),...workspaceNames.slice(2).map(name=>routine(workspaceScope,name))];
const user=id=>({id,roles:['DEVELOPER'],email:id+'@example.test',emailVerified:true,profile:{approval_status:'APPROVED'}});
function fixture({api=()=>assert.fail('Unexpected private transport'),...extra}={}){
 const me=user('builder'),calls=[];
 const state={setMe:me,setActorEpoch:0,setInquiries:[{id:'private',description:'Private brief'}],setItems:[{id:'private-product'}],setHistory:null,setInquiryPage:{page:0,hasMore:true},setGeneration:0,setInquiryVersion:0,setInquiryFilter:'',setStatus:'ACTIVE',setError:'',setMessage:'',setBusy:false,setWriteFence:false};
 const context={ApiError,loadGeneration:{current:0},currentUser:{current:me},inquiryGeneration:{current:0},historyGeneration:{current:0},mounted:{current:true},mutation:{current:null},writeGeneration:{current:0},pendingWrites:{current:new Set()},authority:{current:{actor:me.id,epoch:0,fenced:false}},URLSearchParams,FormData:class{forEach(fn){fn('DISCUSSION','status');}},window:{location:{search:''}},useCallback:fn=>fn,useMemo:fn=>fn(),api,accountApi:(actor,path,init)=>{calls.push({actor,path,method:init?.method||'GET'});return api(path,init);},...extra,...setters(workspace,state)};
 // A render captures me/epoch while refs and state setters remain shared, just
 // as the memoized facade retained by an old child does in the real component.
 const render=()=>evaluate(workspaceDeclarations,{...context,me:state.setMe,actorEpoch:state.setActorEpoch,status:state.setStatus,inquiryFilter:state.setInquiryFilter},workspaceNames);
 return{state,context,calls,render,...render()};
}
const event={preventDefault(){},currentTarget:{}};

test('qualified inquiry history accepts the current response and ignores an older selection',async()=>{
 const old=deferred(),current=deferred();let calls=0;const f=fixture({api:()=>++calls===1?old.promise:current.promise});
 const before=f.openHistory('first'),after=f.openHistory('second');current.resolve({events:[{event_type:'CURRENT'}]});await after;assert.equal(f.state.setHistory.events[0].event_type,'CURRENT');old.resolve({events:[{event_type:'STALE'}]});await before;
 assert.equal(f.state.setHistory.events[0].event_type,'CURRENT');assert.deepEqual(f.calls.map(call=>call.actor),['builder','builder']);
});

test('closing inquiry history or replacing the private context invalidates delayed results',async()=>{
 for(const invalidate of ['closeHistory','clearPrivate']){
  const response=deferred(),f=fixture({api:()=>response.promise}),pending=f.openHistory('first');f[invalidate]();response.resolve({events:[{event_type:'OBSOLETE'}]});await pending;assert.equal(f.state.setHistory,null);
 }
});

for(const status of [401,403])test(`current qualified inquiry read denial (${status}) clears protected workspace context`,async()=>{
 const f=fixture({api:async()=>{throw new ApiError('Builder authority lost','FORBIDDEN',status);}});await f.openHistory('first');
 assert.deepEqual(f.state.setInquiries,[]);assert.deepEqual(f.state.setItems,[]);assert.equal(f.state.setMe,null);assert.equal(f.state.setInquiryPage,null);assert.equal(f.state.setHistory,null);assert.equal(f.state.setError,'Builder authority lost');assert.equal(f.request.isCurrent(),false);
});

test('an obsolete filter failure cannot replace the current filters or error state',async()=>{
 const old=deferred(),current=deferred();let calls=0;const f=fixture({api:()=>++calls===1?old.promise:current.promise});
 const before=f.filterInquiries(event),after=f.filterInquiries(event);current.resolve({items:[{id:'current'}],page:0,size:20,hasMore:false});await after;old.reject(new ApiError('Obsolete failure','UNAVAILABLE',503));await before;
 assert.equal(f.state.setInquiries[0].id,'current');assert.equal(f.state.setInquiryFilter,'?status=DISCUSSION');assert.equal(f.state.setError,'');assert.equal(f.state.setBusy,false);
});

test('a transient current history failure preserves verified rows and permits a successful retry',async()=>{
 let failure=true;const f=fixture({api:async()=>{if(failure)throw new ApiError('History unavailable','UNAVAILABLE',503);return{events:[{event_type:'RECOVERED'}]};}});await f.openHistory('first');assert.equal(f.state.setInquiries[0].id,'private');assert.equal(f.state.setError,'History unavailable');failure=false;await f.openHistory('first');assert.equal(f.state.setHistory.events[0].event_type,'RECOVERED');assert.equal(f.state.setError,'');
});

for(const status of [401,403])test(`a current qualified filter denial (${status}) clears cached private rows`,async()=>{
 const f=fixture({api:async()=>{throw new ApiError('Filter authority lost','FORBIDDEN',status);}});await f.filterInquiries(event);assert.deepEqual(f.state.setInquiries,[]);assert.equal(f.state.setInquiryPage,null);assert.equal(f.state.setBusy,false);assert.equal(f.state.setError,'Filter authority lost');
});

test('a denial from an older history selection still revokes the current actor',async()=>{
 const first=deferred(),second=deferred();let calls=0;const f=fixture({api:()=>++calls===1?first.promise:second.promise}),old=f.openHistory('first'),current=f.openHistory('second');second.resolve({events:[{event_type:'CURRENT'}]});await current;first.reject(new ApiError('Current actor authority lost','FORBIDDEN',403));await old;assert.deepEqual(f.state.setInquiries,[]);assert.equal(f.state.setMe,null);assert.equal(f.state.setHistory,null);assert.equal(f.state.setError,'Current actor authority lost');
});

function privateBundle(path,id='builder'){
 if(path==='/me')return user(id);
 if(path.startsWith('/developer/products?'))return{items:[],page:0,hasMore:false,totalOwned:0};
 if(path.startsWith('/developer/inquiries'))return{items:[{id:id+'-inquiry'}],page:0,hasMore:false};
 if(path==='/developer/analytics')return{};
 assert.fail('Unexpected private bundle path: '+path);
}
for(const status of [200,401,403])test(`a previous actor history response (${status}) cannot alter a newly loaded account`,async()=>{
 const response=deferred(),f=fixture({api:path=>path==='/developer/inquiries/first'?response.promise:Promise.resolve(privateBundle(path,'replacement'))});
 const pending=f.openHistory('first');assert.equal(await f.load(),true);const current=f.render();assert.equal(current.request.isCurrent(),true);assert.equal(f.request.isCurrent(),false);
 if(status===200)response.resolve({events:[{event_type:'OLD_ACCOUNT'}]});else response.reject(new ApiError('Old account authority denial','FORBIDDEN',status));await pending;
 assert.equal(f.state.setMe.id,'replacement');assert.deepEqual(f.state.setInquiries,[{id:'replacement-inquiry'}]);assert.equal(f.state.setHistory,null);assert.equal(f.state.setError,'');
 const before=f.calls.length;await assert.rejects(f.request('/notifications'),problem=>problem instanceof ApiError&&problem.code==='ACCOUNT_CHANGED');assert.equal(f.calls.length,before,'A retained old facade must not send another request');
 await current.request('/developer/inquiries');assert.equal(f.calls.at(-1).actor,'replacement');
});

test('unmounted inquiry reads cannot publish delayed results',async()=>{
 for(const kind of ['history','filter']){const response=deferred(),f=fixture({api:()=>response.promise}),pending=kind==='history'?f.openHistory('first'):f.filterInquiries(event);f.context.mounted.current=false;response.resolve(kind==='history'?{events:[]}:{items:[{id:'OBSOLETE'}],page:0,hasMore:false});await pending;assert.equal(f.state.setInquiries[0].id,'private');assert.equal(f.state.setHistory,null);}
});

for(const status of [0,503])test(`an uncertain private write (${status}) requires a fresh bundle before retry`,async()=>{
 let writes=0;const f=fixture({api:async(path,init)=>{if(init?.method==='POST'){writes++;if(writes===1)throw new ApiError('Change unconfirmed','UNAVAILABLE',status);return{ok:true};}return privateBundle(path);}});
 await assert.rejects(f.request('/inquiries/private/responded',{method:'POST'}),problem=>problem.status===status);assert.equal(f.state.setWriteFence,true);assert.equal(f.context.pendingWrites.current.size,0);
 await assert.rejects(f.request('/inquiries/private/responded',{method:'POST'}),problem=>problem.code==='RECONCILIATION_REQUIRED');assert.equal(writes,1);
 await f.request('/developer/inquiries');assert.equal(f.state.setWriteFence,true,'An unrelated read cannot clear the write barrier');
 assert.equal(await f.load(),true);assert.equal(f.state.setWriteFence,false);await f.render().request('/inquiries/private/responded',{method:'POST'});assert.equal(writes,2);
});

test('a refresh begun during a pending write cannot clear its barrier',async()=>{
 const response=deferred(),f=fixture({api:(path,init)=>init?.method==='POST'?response.promise:Promise.resolve(privateBundle(path))});
 const pending=f.request('/inquiries/private/responded',{method:'POST'});assert.equal(f.context.pendingWrites.current.size,1);assert.equal(await f.load(),false);assert.equal(f.state.setWriteFence,true);
 response.resolve({ok:true});await pending;assert.equal(f.state.setWriteFence,true,'Acknowledgement alone still requires reconciliation');assert.equal(await f.load(),true);assert.equal(f.state.setWriteFence,false);
});

test('a definitive rejected private write releases its barrier for a corrected retry',async()=>{
 let writes=0;const f=fixture({api:async()=>{if(++writes===1)throw new ApiError('Correct the submitted value','VALIDATION_ERROR',400);return{ok:true};}});
 await assert.rejects(f.request('/inquiries/private/responded',{method:'POST'}),problem=>problem.status===400);assert.equal(f.state.setWriteFence,false);await f.request('/inquiries/private/responded',{method:'POST'});assert.equal(writes,2);
});

const accountScope=productionScope(account,'AccountSettingsForIdentity');
const accountNames=['revokeExports','failed','markRead','exportData'];
const accountDeclarations=[callback(accountScope,'revokeExports'),...accountNames.slice(1).map(name=>routine(accountScope,name))];
function accountFixture({api,request=api,reload,...extra}={}){
 const state={setNotifications:[{id:'private-notification',title:'Private account notification'}],setNotificationPage:{page:0,hasMore:true},setNotificationError:'',setError:'',setMessage:'',setReading:[]},downloads=[],createdURLs=[],revokedURLs=[],timers=new Map();let timerId=0;
 const context={ApiError,active:{current:true},generation:{current:0},reads:{current:new Set()},mutation:{current:false},exportURLs:{current:new Map()},useCallback:fn=>fn,request:request||(()=>assert.fail('Unexpected account transport')),reload,Blob,URL:{createObjectURL:()=>{const url='blob:private-'+createdURLs.length;createdURLs.push(url);return url;},revokeObjectURL:url=>revokedURLs.push(url)},window:{setTimeout:fn=>{const id=++timerId;timers.set(id,fn);return id;},clearTimeout:id=>timers.delete(id)},document:{createElement:()=>({click(){downloads.push(this.download);}})},...extra,...setters(account,state)};
 return{state,context,downloads,createdURLs,revokedURLs,timers,...evaluate(accountDeclarations,context,accountNames)};
}
for(const status of [401,403])test(`notification write denial (${status}) clears protected account notifications`,async()=>{
 const f=accountFixture({api:async()=>{throw new ApiError('Notification authority lost','FORBIDDEN',status);}});await f.markRead('private-notification');assert.deepEqual(f.state.setNotifications,[]);assert.equal(f.state.setNotificationPage,null);assert.equal(f.state.setNotificationError,'Notification authority lost');assert.deepEqual(f.state.setReading,[]);
});

test('a denied notification write invalidates an earlier account export before it can download',async()=>{
 const exportResponse=deferred();
 const f=accountFixture({api:path=>path==='/me/export'?exportResponse.promise:Promise.reject(new ApiError('Account session expired','UNAUTHENTICATED',401))});
 const pending=f.exportData();await f.markRead('private-notification');exportResponse.resolve({account:{email:'private@example.test'}});await pending;assert.deepEqual(f.downloads,[]);assert.deepEqual(f.createdURLs,[]);assert.equal(f.state.setMessage,'');
});

test('a completed export is revoked by a later notification authority denial',async()=>{
 const f=accountFixture({api:async path=>{if(path==='/me/export')return{account:{email:'private@example.test'}};throw new ApiError('Account session expired','UNAUTHENTICATED',401);}});
 await f.exportData();assert.deepEqual(f.downloads,['getlancer-account.json']);assert.equal(f.context.exportURLs.current.size,1);assert.equal(f.timers.size,1);
 await f.markRead('private-notification');assert.deepEqual(f.revokedURLs,f.createdURLs);assert.equal(f.context.exportURLs.current.size,0);assert.equal(f.timers.size,0);assert.equal(f.state.setMessage,'');
});

test('a transient standalone notification failure retains rows and permits a successful retry',async()=>{
 let failure=true;const f=accountFixture({api:async()=>{if(failure)throw new ApiError('Read temporarily unavailable','UNAVAILABLE',503);return{ok:true};}});await f.markRead('private-notification');assert.equal(f.state.setNotifications.length,1);assert.equal(f.state.setError,'Read temporarily unavailable');failure=false;await f.markRead('private-notification');assert.ok(f.state.setNotifications[0].read_at);assert.equal(f.state.setError,'');
});

test('a workspace notification retry respects the shared uncertain-write barrier',async()=>{
 let writes=0;const parent=fixture({api:async(path,init)=>{if(init?.method==='PATCH'){if(++writes===1)throw new ApiError('Read update unconfirmed','UNAVAILABLE',503);return{ok:true};}return privateBundle(path);}}),f=accountFixture({request:parent.request,reload:parent.load});
 await f.markRead('private-notification');assert.equal(f.state.setNotifications[0].read_at,undefined);assert.equal(parent.state.setWriteFence,true);
 await f.markRead('private-notification');assert.equal(writes,1);assert.match(f.state.setError,/Reload current workspace records/);
 assert.equal(await parent.load(),true);await f.markRead('private-notification');assert.equal(writes,2);assert.ok(f.state.setNotifications[0].read_at);assert.equal(f.state.setError,'');assert.equal(parent.state.setWriteFence,false);
});

test('a retained account export cannot download after the workspace changes actors',async()=>{
 const response=deferred(),parent=fixture({api:path=>path==='/me/export'?response.promise:Promise.resolve(privateBundle(path,'replacement'))}),f=accountFixture({request:parent.request});
 const pending=f.exportData();assert.equal(await parent.load(),true);response.resolve({account:{id:'builder'},secret:'Old account export'});await pending;
 assert.deepEqual(f.downloads,[]);assert.deepEqual(f.createdURLs,[]);assert.equal(parent.state.setMe.id,'replacement');assert.equal(parent.state.setError,'');
});
