import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import test from 'node:test';
import ts from 'typescript';

const workspace=readFileSync(new URL('../app/workspace/page.tsx',import.meta.url),'utf8');
const templates=readFileSync(new URL('../app/components/template-workspace.tsx',import.meta.url),'utf8');
const operations=readFileSync(new URL('../app/components/template-operations.tsx',import.meta.url),'utf8');
const proof=readFileSync(new URL('../app/components/proof-editor.tsx',import.meta.url),'utf8');
const apiSource=stripTypeScriptTypes(readFileSync(new URL('../lib/api.ts',import.meta.url),'utf8'),{mode:'transform'});
const {api,ApiError}=await import('data:text/javascript;base64,'+Buffer.from(apiSource).toString('base64'));
function routine(source,name,context){
 const tree=ts.createSourceFile('publishing-fixture.tsx',source,ts.ScriptTarget.Latest,true,ts.ScriptKind.TSX),matches=[];
 function visit(node){
  if(ts.isFunctionDeclaration(node)&&node.name?.text===name)matches.push(node);
  ts.forEachChild(node,visit);
 }
 visit(tree);
 assert.equal(matches.length,1,'Exactly one actual '+name+' declaration must exist');
 const text=matches[0].getText(tree).replace(/^export\s+/,'');
 const js=stripTypeScriptTypes(text,{mode:'transform'});
 return Function(...Object.keys(context),js+';return '+name+';')(...Object.values(context));
}
function deferred(){let resolve;const promise=new Promise(done=>{resolve=done;});return{promise,resolve};}
function stateContext(source,extra={}){const values={},context={ApiError,...(source===workspace?{historyGeneration:{current:0}}:{}),...extra};for(const name of source.match(/\bset[A-Z]\w*/g)||[])context[name]=value=>{values[name]=typeof value==='function'?value(values[name]||0):value;};if(source===workspace)context.closeHistory=routine(workspace,'closeHistory',context);return{values,context};}

test('showcase refresh preserves the authenticated editor on a transient read failure',async()=>{
 const original={id:'builder',roles:['DEVELOPER']};let cleared=0;
 const {context,values}=stateContext(workspace,{loadGeneration:{current:0},inquiryGeneration:{current:0},currentUser:{current:original},status:'DRAFT',inquiryFilter:'',clearPrivate:()=>{cleared++;},api:async path=>{if(path==='/me')return original;throw new ApiError('Temporary read outage','UNAVAILABLE',503);}});
 const load=routine(workspace,'load',context);
 assert.equal(await load(),false);assert.equal(cleared,0);assert.equal(values.setMe,undefined);assert.equal(values.setEdit,undefined);assert.match(values.setError,/Temporary read outage/);assert.equal(values.setLoading,false);
});

test('a late showcase response cannot replace the latest lifecycle selection',async()=>{
 const first=deferred(),second=deferred(),user={id:'builder',roles:['DEVELOPER']};let reads=0;
 const {context,values}=stateContext(workspace,{loadGeneration:{current:0},inquiryGeneration:{current:0},currentUser:{current:user},status:'DRAFT',inquiryFilter:'',clearPrivate:()=>assert.fail('unexpected authority reset'),api:async path=>{if(path==='/me')return user;if(path.startsWith('/developer/products'))return ++reads===1?first.promise:second.promise;return path==='/developer/analytics'?{}:{items:[]};}});
 const load=routine(workspace,'load',context);
 const old=load();await Promise.resolve();const latest=load();await Promise.resolve();
 second.resolve({items:[{id:'current'}],page:0,hasMore:false});assert.equal(await latest,true);
 first.resolve({items:[{id:'obsolete'}],page:0,hasMore:false});assert.equal(await old,false);assert.deepEqual(values.setItems,[{id:'current'}]);
});

test('only a current showcase authority denial removes private state',async()=>{
 for(const status of [401,403]){let cleared=0;const {context}=stateContext(workspace,{loadGeneration:{current:0},inquiryGeneration:{current:0},currentUser:{current:{id:'builder'}},status:'ACTIVE',inquiryFilter:'',clearPrivate:()=>{cleared++;},api:async()=>{throw new ApiError('Authority changed','FORBIDDEN',status);}});assert.equal(await routine(workspace,'load',context)(),false);assert.equal(cleared,1);}
});

test('workspace authority reset invalidates an outstanding private read',async()=>{
 const response=deferred(),user={id:'builder',roles:['DEVELOPER']};const {context,values}=stateContext(workspace,{loadGeneration:{current:0},inquiryGeneration:{current:0},currentUser:{current:user},status:'ACTIVE',inquiryFilter:'',api:async path=>path==='/me'?user:path.startsWith('/developer/products')?response.promise:path==='/developer/analytics'?{}:{items:[]}});
 context.clearPrivate=routine(workspace,'clearPrivate',context);const load=routine(workspace,'load',context);const old=load();await Promise.resolve();context.clearPrivate();response.resolve({items:[{id:'private-old-record'}]});assert.equal(await old,false);assert.equal(values.setMe,null);assert.deepEqual(values.setItems,[]);
});

test('an identity change clears the previous editor and installs the new account inquiry list',async()=>{
 const user={id:'new-account',roles:['DEVELOPER']};const {context,values}=stateContext(workspace,{window:{location:{search:''}},loadGeneration:{current:0},inquiryGeneration:{current:0},currentUser:{current:{id:'old-account'}},status:'ACTIVE',inquiryFilter:'',api:async path=>path==='/me'?user:path.startsWith('/developer/products')?{items:[],totalOwned:0}:path==='/developer/analytics'?{}:{items:[{id:'new-inquiry'}]}});context.clearPrivate=routine(workspace,'clearPrivate',context);assert.equal(await routine(workspace,'load',context)(),true);assert.equal(values.setEdit,null);assert.equal(values.setMe.id,'new-account');assert.deepEqual(values.setInquiries,[{id:'new-inquiry'}]);
});

test('template refresh retains release forms on outage and clears them on authority loss',async()=>{
 for(const status of [503,401,403]){let cleared=0;const {context,values}=stateContext(templates,{generation:{current:0},selected:'saved-template',owned:{page:0},purchases:{page:0},preview:null,demo:false,clear:()=>{cleared++;},api:async()=>{throw new ApiError('Read failed','READ_FAILED',status);}});assert.equal(await routine(templates,'load',context)(),false);assert.equal(cleared,status===503?0:1);assert.equal(values.setOwned,undefined);if(status!==503)assert.equal(values.setDialog,'');}
});

test('an accepted immutable release is reported as saved when its follow-up read fails',async()=>{
 let writes=0,reads=0;const {context,values}=stateContext(templates,{selected:'template',owned:{page:0},preview:null,clear:()=>assert.fail('unexpected authority reset'),load:async()=>{reads++;return false;}});
 const change=routine(templates,'change',context);const result=await change(async()=>{writes++;return{id:'version'};},'Version uploaded privately.');
 assert.equal(writes,1);assert.equal(reads,1);assert.deepEqual(result,{id:'version'});assert.match(values.setNote,/Version uploaded privately.*change was accepted.*reload/);assert.equal(values.setBusy,false);
});

test('operator read failure keeps reviewed records while current authority loss clears them',async()=>{
 for(const status of [503,401,403]){const {context,values}=stateContext(operations,{generation:{current:0},demo:false,previewStore:null,api:async()=>{throw new ApiError('Read failed','READ_FAILED',status);}});assert.equal(await routine(operations,'load',context)(),false);assert.equal(values.setTemplates===undefined,status===503);assert.equal(values.setAttention===undefined,status===503);}
});

test('private template ZIP transport preserves multipart boundaries and rejects unconfirmed JSON',async t=>{
 const original=globalThis.fetch;t.after(()=>{globalThis.fetch=original;});const upload=routine(templates,'uploadPackage',{api});
 const body=new FormData();body.set('file',new File(['source'],'release.zip'));
 globalThis.fetch=async(_url,init)=>{assert.equal(init.body,body);assert.equal(init.headers.has('Content-Type'),false);return new Response('upstream HTML',{status:200});};
 await assert.rejects(upload('/me/templates/template/versions',body),error=>error instanceof ApiError&&error.code==='INVALID_RESPONSE');
 globalThis.fetch=async()=>Response.json({error:{code:'VERSION_EXISTS',message:'Use a new immutable release.',fieldErrors:{version:'Already uploaded.'}}},{status:409});
 await assert.rejects(upload('/me/templates/template/versions',body),error=>error instanceof ApiError&&error.fieldErrors.version==='Already uploaded.');
});

test('closing proof during confirmation suppresses editor state and parent refresh callbacks',async()=>{
 const response=deferred();let active=true,refreshed=0,pendingChanges=0;
 const complete=routine(proof,'complete',{product:{id:'product'},api:()=>response.promise,current:()=>active,ApiError,setPendingUpload:()=>{pendingChanges++;},refresh:()=>{refreshed++;}});
 const task=complete('original-upload',1);active=false;response.resolve({id:'saved-image'});await task;assert.equal(pendingChanges,0);assert.equal(refreshed,0);
});

test('proof write stays busy through its parent refresh and ignores late failures after close',async()=>{
 const wait=deferred(),working={current:false},generation={current:1};let active=true,notifications=0;
 const {context,values}=stateContext(proof,{working,generation,current:()=>active,denied:()=>true,onChange:()=>{notifications++;}});
 const action=routine(proof,'action',context);const task=action(()=>wait.promise);assert.equal(values.setBusy,true);assert.equal(working.current,true);assert.equal(await action(async()=>assert.fail('overlapping write')),undefined);assert.equal(values.setBusy,true);active=false;wait.resolve();await task;assert.equal(notifications,0);assert.equal(values.setBusy,true);
 const failure=deferred();active=true;const old=action(()=>failure.promise);active=false;failure.resolve(Promise.reject(new ApiError('Expired','FORBIDDEN',403)));await old;assert.equal(notifications,0);
});
