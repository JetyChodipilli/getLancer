#!/usr/bin/env node
import assert from 'node:assert/strict';
import {performance} from 'node:perf_hooks';
import {fileURLToPath} from 'node:url';
import {resolve} from 'node:path';
import {execFileSync} from 'node:child_process';

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
  const headers=base===frontend&&config.FRONTEND_SITE_ACCESS_TOKEN?{'OAI-Sites-Authorization':'Bearer '+config.FRONTEND_SITE_ACCESS_TOKEN}:{};
  const response=await request(base+path,{headers,redirect:'manual',signal:AbortSignal.timeout(10000)});
  return response;
 }
 for(const path of ['/actuator/health/readiness','/actuator/health/liveness']){
  const response=await get(backend,path);
  assert.ok([401,403,404].includes(response.status),'Health probes must be private at hosted ingress: '+path);
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

// Hosted verification uses one injected snapshot for both the configuration
// checker and requests. This command never loads a local environment file.
export async function runStagingCli(args,{environment=process.env,request=fetch,check=(flags,env)=>execFileSync(process.execPath,[fileURLToPath(new URL('./check-environment.mjs',import.meta.url)),...flags],{stdio:'inherit',env})}={}){
 assert.ok(args.includes('--from-env')&&args.length===new Set(args).size&&args.every(value=>['--from-env','--existing-admin'].includes(value)),'Use --from-env [--existing-admin]; inject hosted configuration without an environment file.');
 const config={...environment};
 const flags=['--from-env',...(args.includes('--existing-admin')?['--existing-admin']:[])];
 check(flags,config);
 return verifyStaging(config,request);
}

if(process.argv[1]&&fileURLToPath(import.meta.url)===resolve(process.argv[1])){
 try{
  const result=await runStagingCli(process.argv.slice(2));
  console.log('Hosted read-only acceptance passed: '+JSON.stringify(result));
 }catch{console.error('Hosted acceptance failed. Use --from-env [--existing-admin] with injected configuration; check HTTPS, health, proxy responses and connected redirects. No credentials are printed.');process.exitCode=1;}
}
