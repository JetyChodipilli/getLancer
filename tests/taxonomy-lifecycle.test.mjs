import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';
import ts from 'typescript';

const source=readFileSync(new URL('../lib/use-taxonomy.ts',import.meta.url),'utf8');
const compiled=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText;

function mount(){
 const window=new EventTarget(),listeners=new Set(),requests=[],state=[],writes=[];let cleanup;
 const add=window.addEventListener.bind(window),remove=window.removeEventListener.bind(window);
 window.addEventListener=(type,listener)=>{listeners.add(listener);add(type,listener);};
 window.removeEventListener=(type,listener)=>{listeners.delete(listener);remove(type,listener);};
 class ApiError extends Error{constructor(code){super(code);this.code=code;}}
 const modules={
  react:{useState(initial){const index=state.length;state.push(initial);return [initial,value=>{state[index]=value;writes.push(index);}];},useEffect(effect){cleanup=effect();}},
  './api':{ApiError,api(path,init){return new Promise((resolve,reject)=>requests.push({path,signal:init?.signal,resolve,reject}));}},
  './catalog':{categories:['Preview category'],technologies:['Preview technology']},
 };
 const exports={};
 runInNewContext(compiled,{exports,require(name){assert(name in modules,`Unexpected dependency ${name}`);return modules[name];},window,AbortController});
 exports.useTaxonomy();
 return {window,listeners,requests,state,writes,ApiError,unmount:()=>cleanup()};
}
const settle=()=>new Promise(resolve=>setImmediate(resolve));

for(const stop of ['unmount','pagehide'])for(const outcome of ['success','failure']){
 test(`taxonomy ${stop} cancels both reads and rejects late ${outcome} state`,async()=>{
  const view=mount();
  assert.deepEqual(view.requests.map(request=>request.path),['/categories','/technologies']);
  assert(view.requests.every(request=>request.signal instanceof AbortSignal&&!request.signal.aborted));
  if(stop==='pagehide')view.window.dispatchEvent(new Event('pagehide'));else view.unmount();
  assert(view.requests.every(request=>request.signal.aborted),'Both in-flight requests must be cancelled');
  for(const request of view.requests){
   if(outcome==='success')request.resolve({items:[{name:'Late option'}]});
   else request.reject(new view.ApiError('BACKEND_NOT_CONFIGURED'));
  }
  await settle();
  assert.deepEqual(view.writes,[],'Cancelled work must not update options, fallback or errors');
  view.unmount();
 });
}

test('taxonomy retains successful options and removes its document listener on unmount',async()=>{
 const view=mount();
 view.requests[0].resolve({items:[{name:'Current category'}]});view.requests[1].resolve({items:[{name:'Current technology'}]});
 await settle();
 assert.deepEqual(Array.from(view.state[0]),['Current category']);assert.deepEqual(Array.from(view.state[1]),['Current technology']);assert.equal(view.state[2],'');
 let abortCalls=0;for(const request of view.requests)request.signal.addEventListener('abort',()=>abortCalls++);
 view.unmount();assert.equal(abortCalls,2);
 assert.equal(view.listeners.size,0,'Both document listeners must be removed');
 view.window.dispatchEvent(new Event('pagehide'));assert.equal(abortCalls,2);
 view.window.dispatchEvent(new Event('pageshow'));assert.equal(view.requests.length,2,'An unmounted hook must not restart reads');
});

test('restoring a cached document restarts cancelled reads and rejects the old continuation',async()=>{
 const view=mount();view.window.dispatchEvent(new Event('pageshow'));
 assert.equal(view.requests.length,2,'Initial pageshow must not duplicate active reads');
 view.window.dispatchEvent(new Event('pagehide'));
 const restored=new Event('pageshow');Object.defineProperty(restored,'persisted',{value:true});view.window.dispatchEvent(restored);
 assert.equal(view.requests.length,4);assert(view.requests.slice(0,2).every(request=>request.signal.aborted));
 assert(view.requests.slice(2).every(request=>!request.signal.aborted));
 for(const request of view.requests.slice(0,2))request.resolve({items:[{name:'Stale option'}]});
 await settle();assert.deepEqual(view.writes,[]);
 view.requests[2].resolve({items:[{name:'Restored category'}]});view.requests[3].resolve({items:[{name:'Restored technology'}]});
 await settle();assert.deepEqual(Array.from(view.state[0]),['Restored category']);assert.deepEqual(Array.from(view.state[1]),['Restored technology']);
 view.unmount();assert.equal(view.listeners.size,0);
});

for(const code of ['BACKEND_NOT_CONFIGURED','NETWORK_ERROR'])test(`taxonomy preserves active ${code} recovery`,async()=>{
 const view=mount();view.requests[0].reject(new view.ApiError(code));
 await settle();
 if(code==='BACKEND_NOT_CONFIGURED'){
  assert.deepEqual(Array.from(view.state[0]),['Preview category']);assert.deepEqual(Array.from(view.state[1]),['Preview technology']);assert.equal(view.state[2],'');
 }else{assert.equal(view.state[0].length,0);assert.equal(view.state[1].length,0);assert.equal(view.state[2],'Category options could not load. Refresh to try again.');}
 view.unmount();
});
