import {after} from 'node:test';
import {createBuiltWorker,runtimeBindings} from '../../scripts/worker-runtime.mjs';
let runtime,configuration='';
after(async()=>{await runtime?.dispose();});
export default {
 async fetch(request,env={}){
  const bindings={...runtimeBindings(),...Object.fromEntries(Object.entries(env).filter(([,value])=>typeof value==='string'))};
  const next=JSON.stringify(bindings);
  if(!runtime||next!==configuration){
   await runtime?.dispose();
   runtime=createBuiltWorker({bindings,assetsFetch:env.ASSETS?.fetch||(()=>new Response('Not found',{status:404}))});
   configuration=next;
  }
  return runtime.dispatchFetch(request.url,{method:request.method,headers:Object.fromEntries(request.headers),body:['GET','HEAD'].includes(request.method)?undefined:await request.arrayBuffer(),redirect:'manual'});
 }
};
