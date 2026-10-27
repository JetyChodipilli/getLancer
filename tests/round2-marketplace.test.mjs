import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import test from 'node:test';

const source=name=>readFileSync(new URL('../app/components/'+name+'.tsx',import.meta.url),'utf8');
const apiSource=stripTypeScriptTypes(readFileSync(new URL('../lib/api.ts',import.meta.url),'utf8'),{mode:'transform'});
const {ApiError}=await import('data:text/javascript;base64,'+Buffer.from(apiSource).toString('base64'));
function routine(text,start,end,context){
 const first=text.indexOf(start),last=text.indexOf(end,first);assert.ok(first>=0&&last>first,'Actual production routine boundaries exist');
 return Function(...Object.keys(context),stripTypeScriptTypes(text.slice(first,last),{mode:'transform'})+';return '+start.match(/function (\w+)/)[1]+';')(...Object.values(context));
}
const deferred=()=>Promise.withResolvers();

test('old component save acknowledgement cannot update an unmounted selection; current acknowledgement succeeds',async()=>{
 async function sample(changeSelection){
  const work=deferred(),state={saved:false,busy:false},active={current:true},pending={current:false};
  const toggle=routine(source('component-save-button'),'async function toggle()','\n  return <div',{
   pending,ready:true,needsLogin:false,saved:false,preview:false,slug:'alpha',active,ApiError,api:()=>work.promise,
   setSaved:value=>state.saved=value,setBusy:value=>state.busy=value,setError:()=>{},setNeedsLogin:()=>{},
  });
  const waiting=toggle();if(changeSelection)active.current=false;work.resolve({saved:true});await waiting;return state;
 }
 assert.equal((await sample(false)).saved,true);
 assert.equal((await sample(true)).saved,false);
});

test('save mutation rejects duplicate synchronous submission and invalidates denied saved state',async()=>{
 const work=deferred(),state={saved:true,login:false};let calls=0;
 const toggle=routine(source('component-save-button'),'async function toggle()','\n  return <div',{
  pending:{current:false},ready:true,needsLogin:false,saved:true,preview:false,slug:'alpha',active:{current:true},ApiError,
  api:()=>{calls++;return work.promise;},setSaved:value=>state.saved=value,setNeedsLogin:value=>state.login=value,setBusy:()=>{},setError:()=>{},
 });
 const first=toggle(),second=toggle();assert.equal(calls,1);work.reject(new ApiError('Access ended.','FORBIDDEN',403));await Promise.all([first,second]);assert.deepEqual(state,{saved:false,login:true});
});

test('saved removal authority denial clears cached private entries while a transient failure preserves them',async()=>{
 for(const status of [401,403,503]){
  const state={items:[{slug:'alpha',title:'Prior private save'}],login:false,error:''},generation={current:0},active={current:true};
  const context={pending:{current:false},loading:false,needsLogin:false,generation,preview:false,ApiError,
   current:value=>active.current&&value===generation.current,
   api:async()=>{throw new ApiError('Removal failed.','REQUEST_FAILED',status);},
   setItems:value=>state.items=typeof value==='function'?value(state.items):value,setNeedsLogin:value=>state.login=value,setBusy:()=>{},setError:value=>state.error=value,
  };
  context.denied=routine(source('saved-components'),'function denied(','\n  async function load',context);
  await routine(source('saved-components'),'async function remove(','\n  return <main',context)('alpha');
  assert.equal(state.items.length,status===503?1:0);assert.equal(state.login,status!==503);assert.equal(state.error,'Removal failed.');
 }
});

test('late saved removal cannot alter a replacement list and a current success removes only its row',async()=>{
 for(const changed of [false,true]){
  const work=deferred(),generation={current:0},state={items:[{slug:'alpha'},{slug:'beta'}]};
  const context={pending:{current:false},loading:false,needsLogin:false,generation,preview:false,current:value=>value===generation.current,api:()=>work.promise,
   setBusy:()=>{},setError:()=>{},setItems:value=>state.items=value(state.items),denied:()=>{},
  };
  const waiting=routine(source('saved-components'),'async function remove(','\n  return <main',context)('alpha');
  if(changed)generation.current++;work.resolve({saved:false});await waiting;
  assert.deepEqual(state.items.map(item=>item.slug),changed?['alpha','beta']:['beta']);
 }
});

test('private preview effect cannot publish an old resource after its cleanup',async()=>{
 const text=source('private-preview'),start=text.indexOf(' useEffect(()=>{'),end=text.indexOf('\n return <main',start);assert.ok(start>=0&&end>start);
 const alpha=deferred(),beta=deferred(),state={project:null,error:''};let cleanup;
 const run=Function('id','api','setProject','setError','useEffect',text.slice(start,end));
 const api=path=>path.endsWith('/alpha')?alpha.promise:beta.promise;
 const effect=work=>{cleanup=work();};
 run('alpha',api,value=>state.project=value,value=>state.error=value,effect);cleanup();
 run('beta',api,value=>state.project=value,value=>state.error=value,effect);
 beta.resolve({id:'beta'});await beta.promise;await Promise.resolve();assert.equal(state.project.id,'beta');
 alpha.resolve({id:'alpha'});await alpha.promise;await Promise.resolve();assert.equal(state.project.id,'beta');assert.equal(state.error,'');
});

test('an old template order does not publish recovery state or load checkout after release selection changes',async()=>{
 async function sample(changeSelection){
  const work=deferred(),state={orders:[],opened:0,loaderCalls:0},alive={current:true};
  const context={consent:true,preview:false,template:{id:'alpha',versionId:'alpha-v1',title:'Alpha',version:'1.0'},canOpenTemplateCheckout:()=>true,
   key:{current:''},pending:{current:false},checkoutGeneration:{current:0},gatewayRef:{current:null},alive,crypto:{randomUUID:()=> 'stable-key'},api:()=>work.promise,
   setBusy:()=>{},setError:()=>{},setNote:()=>{},setOpen:()=>{},setOrder:order=>state.orders.push(order),
   loadTemplateCheckout:async()=>{state.loaderCalls++;return class{on(){}open(){state.opened++;}};},
  };
  context.invalidateCheckout=routine(source('template-marketplace'),'function invalidateCheckout()','\n useEffect',context);
  const waiting=routine(source('template-marketplace'),'async function checkout(){','\n return <main',context)();
  if(changeSelection)alive.current=false;
  work.resolve({id:'purchase-alpha',status:'ORDER_CREATED',orderId:'provider-alpha',keyId:'test-key',amountMinor:100,currency:'INR',mode:'test'});await waiting;return state;
 }
 const current=await sample(false);assert.equal(current.opened,1);assert.equal(current.orders.length,1);
 const old=await sample(true);assert.deepEqual(old,{orders:[],opened:0,loaderCalls:0});
});

test('template checkout dismissals and duplicate provider callbacks cannot race current verification',async()=>{
 async function fixture(){
  const verification=deferred(),state={orders:0,verifies:0,busy:false,note:'',options:null,closed:0};
  const context={consent:true,preview:false,template:{id:'alpha',versionId:'alpha-v1'},canOpenTemplateCheckout:()=>true,key:{current:''},pending:{current:false},checkoutGeneration:{current:0},gatewayRef:{current:null},alive:{current:true},crypto:{randomUUID:()=> 'stable-key'},
   api:async path=>{if(path.endsWith('/verify')){state.verifies++;return verification.promise;}state.orders++;return{id:'purchase-alpha',status:'ORDER_CREATED',orderId:'provider-alpha',keyId:'test-key',mode:'test'};},
   setBusy:value=>state.busy=value,setError:()=>{},setNote:value=>state.note=value,setOpen:()=>{},setOrder:()=>{},templateStatus:value=>value,
   loadTemplateCheckout:async()=>class{constructor(options){state.options=options;}on(){}open(){}close(){state.closed++;throw Error('Synthetic SDK close failure');}},
  };
  context.invalidateCheckout=routine(source('template-marketplace'),'function invalidateCheckout()','\n useEffect',context);
  const checkout=routine(source('template-marketplace'),'async function checkout(){','\n return <main',context);await checkout();return{state,context,checkout,verification};
 }
 const dismissed=await fixture();assert.doesNotThrow(()=>dismissed.state.options.modal.ondismiss());assert.equal(dismissed.state.closed,1);await dismissed.state.options.handler({});assert.equal(dismissed.state.verifies,0);assert.equal(dismissed.state.busy,false);
 const current=await fixture(),first=current.state.options.handler({});await current.state.options.handler({});assert.equal(current.state.verifies,1,'Duplicate handler does not send another verify.');current.state.options.modal.ondismiss();assert.equal(current.state.busy,true,'Dismissal cannot unlock actions during verification.');assert.equal(current.context.pending.current,true);await current.checkout();assert.equal(current.state.orders,1);
 current.verification.resolve({status:'CAPTURED'});await first;assert.equal(current.state.busy,false);assert.equal(current.context.pending.current,false);assert.match(current.state.note,/CAPTURED/);await current.state.options.handler({});assert.equal(current.state.verifies,1,'A completed receipt callback is also one-shot.');
});
