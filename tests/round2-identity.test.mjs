import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import test from 'node:test';
import ts from 'typescript';

// Execute production routines with state/transport boundaries replaced. React lifecycle
// and controls are separately exercised by tests/browser/round2-identity.spec.ts.
const source=readFileSync(new URL('../app/components/client-requests.tsx',import.meta.url),'utf8');
const confirmation=readFileSync(new URL('../app/components/confirmation.tsx',import.meta.url),'utf8');
const apiSource=stripTypeScriptTypes(readFileSync(new URL('../lib/api.ts',import.meta.url),'utf8'),{mode:'transform'});
const {ApiError}=await import('data:text/javascript;base64,'+Buffer.from(apiSource).toString('base64'));
function productionScope(text,name){
 const tree=ts.createSourceFile(name+'.tsx',text,ts.ScriptTarget.Latest,true,ts.ScriptKind.TSX);
 const components=tree.statements.filter(node=>ts.isFunctionDeclaration(node)&&node.name?.text===name);
 assert.equal(components.length,1,'Exactly one actual '+name+' component must exist');
 assert.ok(components[0].body,'The actual '+name+' component has a body');
 return{tree,statements:components[0].body.statements};
}
function declaration(scope,name){
 const matches=[];
 for(const statement of scope.statements){
  if(ts.isFunctionDeclaration(statement)&&statement.name?.text===name)matches.push(statement.getText(scope.tree));
  if(ts.isVariableStatement(statement))for(const node of statement.declarationList.declarations){
   if(!ts.isIdentifier(node.name)||node.name.text!==name)continue;
   const initializer=node.initializer;
   assert.ok(initializer&&ts.isCallExpression(initializer)&&['useCallback','useMemo'].includes(initializer.expression.getText(scope.tree)),'The actual '+name+' callback is initialized by React');
   matches.push('const '+node.getText(scope.tree)+';');
  }
 }
 assert.equal(matches.length,1,'Exactly one actual '+name+' routine must exist');return matches[0];
}
function evaluate(scope,names,context){
 const code=stripTypeScriptTypes(names.map(name=>declaration(scope,name)).join('\n'),{mode:'transform'});
 return Function(...Object.keys(context),code+'\nreturn {'+names.join(',')+'};')(...Object.values(context));
}
function deferred(){let resolve,reject;const promise=new Promise((yes,no)=>{resolve=yes;reject=no});return{promise,resolve,reject};}
const requestScope=productionScope(source,'RequestsForIdentity'),confirmationScope=productionScope(confirmation,'Confirmation');
const requestNames=['closeRequest','failed','openRequest','load','reconcile','act'];
function fixture({api,request=api,reload,...extra}={}){
 const state={setItems:[row],setEpoch:0,setPage:null,setSelected:row,setHistory:[],setRefreshNeeded:false,setLoading:false,setBusy:false,setError:'',setMessage:''},context={ApiError,verified:true,active:{current:true},listGeneration:{current:0},historyGeneration:{current:0},mutation:{current:false},useCallback:fn=>fn,useMemo:fn=>fn(),request:request||(()=>assert.fail('Unexpected client request transport')),reload,...extra};
 for(const name of new Set(source.match(/\bset[A-Z]\w*/g)))context[name]=value=>{state[name]=typeof value==='function'?value(state[name]):value;};
 const render=()=>evaluate(requestScope,requestNames,{...context,refreshNeeded:state.setRefreshNeeded});
 return {state,context:{...context,...render()},render,act:()=>render().act};
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
 await act()(row,'not-hired');assert.deepEqual(calls.map(call=>call.method),['POST','GET'],'The rendered recovery state must block another write');
 available=true;assert.equal(await context.reconcile(),true);assert.deepEqual(state.setItems,[]);assert.equal(state.setRefreshNeeded,false);assert.deepEqual(calls.map(call=>call.method),['POST','GET','GET']);
});

test('a rejected mutation keeps its rejection visible after the current requests refresh',async()=>{
 const {state,act}=fixture({api:async(_path,init)=>{if(init?.method==='POST')throw new ApiError('Request already changed','CONFLICT',409);return page;}});
 await act()(row,'not-hired');assert.equal(state.setError,'Request already changed');assert.equal(state.setMessage,'');assert.equal(state.setPage.page,0);assert.equal(state.setBusy,false);
});

test('an uncertain mutation requires a read before another write and an unmounted response publishes nothing',async()=>{
 let calls=0,available=false;const failed=fixture({api:async()=>{calls++;if(!available)throw new TypeError('Connection interrupted');return page;}});await failed.act()(row,'not-hired');assert.equal(failed.state.setRefreshNeeded,true);assert.match(failed.state.setMessage,/could not be confirmed/);assert.equal(failed.state.setBusy,false);
 await failed.act()(row,'not-hired');assert.equal(calls,1,'The uncertain result must block another write');available=true;assert.equal(await failed.context.reconcile(),true);assert.equal(calls,2);assert.equal(failed.state.setRefreshNeeded,false);
 const response=deferred(),late=fixture({api:()=>response.promise});const pending=late.act()(row,'not-hired');late.context.active.current=false;response.resolve({ok:true});await pending;assert.equal(late.state.setMessage,'');assert.equal(late.state.setRefreshNeeded,false);
});

test('a successful child refresh keeps the write barrier until the parent account also refreshes',async()=>{
 let writes=0,reloads=0,available=false;const f=fixture({api:async(_path,init)=>{if(init?.method==='POST'){writes++;return{ok:true};}return page;},reload:async()=>{reloads++;return available;}});
 await f.act()(row,'not-hired');assert.equal(f.state.setPage.page,0);assert.equal(f.state.setRefreshNeeded,true);assert.match(f.state.setMessage,/Your response has been recorded.*could not be refreshed/);
 await f.act()(row,'not-hired');assert.equal(writes,1);assert.equal(reloads,1);available=true;assert.equal(await f.context.reconcile(),true);assert.equal(f.state.setRefreshNeeded,false);assert.equal(reloads,2);assert.equal(writes,1);
});

test('link-context retry rejects an older link and distinguishes transient failure from expiry',async()=>{
 const state={},pending=deferred(),context={ApiError,linkRevision:{current:0},api:()=>pending.promise};
 for(const name of new Set(confirmation.match(/\bset[A-Z]\w*/g)))context[name]=value=>{state[name]=value;};
 const check=evaluate(confirmationScope,['checkLink'],context).checkLink,old=check('synthetic-token-one');context.linkRevision.current++;pending.resolve({kind:'EMAIL_VERIFICATION',used:false});await old;assert.equal(state.setContext,null);
 context.api=async()=>{throw new ApiError('Lookup unavailable','UNAVAILABLE',503);};await evaluate(confirmationScope,['checkLink'],context).checkLink('synthetic-token-two');assert.equal(state.setCanRetry,true);assert.equal(state.setError,'Lookup unavailable');
 context.api=async()=>{throw new ApiError('Link expired','TOKEN_EXPIRED',410);};await evaluate(confirmationScope,['checkLink'],context).checkLink('synthetic-token-two');assert.equal(state.setCanRetry,false);
 context.api=async(_path,init)=>{assert.equal(JSON.parse(init.body).token,'synthetic-token-two');return{kind:'EMAIL_VERIFICATION',used:false};};await evaluate(confirmationScope,['checkLink'],context).checkLink('synthetic-token-two');assert.equal(state.setError,'');assert.deepEqual(state.setContext,{kind:'EMAIL_VERIFICATION',used:false});
});
