import {Miniflare, convertV4MiniflareOptions} from 'miniflare';
import {readdirSync} from 'node:fs';
import {join} from 'node:path';

// Execute the built Cloudflare Worker with workerd, including its native bindings.
// Node cannot directly import a Worker that uses cloudflare:workers.
export function createBuiltWorker({bindings={},assetsFetch,port,host='127.0.0.1'}={}) {
 const root=new URL('../dist/server/',import.meta.url).pathname;
 const paths=readdirSync(root,{recursive:true}).filter(path=>path.endsWith('.js')).sort((a,b)=>a==='index.js'?-1:b==='index.js'?1:a.localeCompare(b));
 const worker={name:'getlancer',modules:paths.map(path=>({type:'ESModule',path:join(root,path)})),modulesRoot:root,compatibilityDate:'2026-09-30',compatibilityFlags:['nodejs_compat'],bindings};
 if(assetsFetch)worker.serviceBindings={ASSETS:assetsFetch};
 else worker.assets={directory:new URL('../dist/client/',import.meta.url).pathname,binding:'ASSETS'};
 return new Miniflare(convertV4MiniflareOptions({...(port===undefined?{}:{port,host}),workers:[worker]}));
}
export function runtimeBindings(env=process.env){
 return Object.fromEntries(['BACKEND_URL','DEMO_MODE','APP_BASE_URL','INDEX_PUBLIC_PAGES','BACKEND_PROXY_SECRET'].filter(key=>env[key]!==undefined).map(key=>[key,env[key]]));
}
