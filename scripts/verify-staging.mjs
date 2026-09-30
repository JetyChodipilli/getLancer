#!/usr/bin/env node
import assert from 'node:assert/strict';
import {performance} from 'node:perf_hooks';
import {fileURLToPath} from 'node:url';
import {execFileSync} from 'node:child_process';
import {readEnvironment} from './local-config.mjs';

function origin(value){
 const url=new URL(value);
 assert.equal(url.protocol,'https:','Hosted acceptance requires HTTPS.');
 assert.ok(!url.username&&!url.password&&!url.search&&!url.hash&&url.pathname==='/','Use an HTTPS origin without credentials, path or query.');
 return url.origin;
}

// Read-only deployed checks. SMTP delivery, signed browser uploads, MFA and
// authenticated journeys still require the operator's staging accounts.
export async function verifyStaging(config,request=fetch){
 const frontend=origin(config.APP_BASE_URL),backend=origin(config.BACKEND_URL);
 async function get(base,path){
  const response=await request(base+path,{redirect:'manual',signal:AbortSignal.timeout(10000)});
  return response;
 }
 for(const path of ['/actuator/health/readiness','/actuator/health/liveness']){
  const response=await get(backend,path);assert.equal(response.status,200,path);
  assert.equal((await response.json()).status,'UP',path);
 }
 const providers=await get(frontend,'/api/v1/auth/providers'),direct=await get(backend,'/api/v1/auth/providers');
 assert.equal(providers.status,200);assert.equal(direct.status,200);
 assert.deepEqual(await providers.json(),await direct.json(),'The frontend must return the actual Java provider configuration.');
 const me=await get(frontend,'/api/v1/me');assert.equal(me.status,401,'Anonymous requests must never become a sample account.');
 for(const path of ['workspace','trust','teams','business']){
  const response=await get(frontend,'/preview/'+path);
  assert.equal(response.status,307,'Connected preview routes must redirect.');
  const destination=new URL(response.headers.get('location'),frontend);
  assert.equal(destination.origin,frontend);assert.equal(destination.pathname,'/workspace'+(path==='workspace'?'':'/'+path));
 }
 const durations=[];
 for(let wave=0;wave<4;wave++){
  await Promise.all([0,1].map(async()=>{
   const start=performance.now(),response=await get(frontend,'/api/v1/products?size=1');
   assert.equal(response.status,200,'Public catalog should remain available.');
   assert.ok(Array.isArray((await response.json()).items),'Catalog must be a real list envelope.');
   durations.push(performance.now()-start);
  }));
 }
 durations.sort((a,b)=>a-b);
 return {requests:durations.length,concurrency:2,p95Ms:Math.round(durations[Math.ceil(durations.length*.95)-1]),providerAcceptance:'pending operator delivery/upload/MFA journeys'};
}

if(process.argv[1]&&fileURLToPath(import.meta.url)===process.argv[1]){
 try{
  const file=process.argv[2]||'.env.staging';
  assert.ok(process.argv.length<=4&&(!process.argv[3]||process.argv[3]==='--existing-admin'),'Use [file] [--existing-admin].');
  execFileSync(process.execPath,[fileURLToPath(new URL('./check-environment.mjs',import.meta.url)),file,...process.argv.slice(3)],{stdio:'inherit'});
  const result=await verifyStaging(readEnvironment(file));
  console.log('Hosted read-only acceptance passed: '+JSON.stringify(result));
 }catch{console.error('Hosted acceptance failed. Check HTTPS configuration, health, proxy responses and connected redirects. No credentials are printed.');process.exitCode=1;}
}
