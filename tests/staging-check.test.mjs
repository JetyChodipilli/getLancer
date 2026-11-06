import test from 'node:test';
import assert from 'node:assert/strict';
import {verifyStaging,runStagingCli} from '../scripts/verify-staging.mjs';
import {mkdtempSync,writeFileSync,rmSync} from 'node:fs';
import {spawnSync} from 'node:child_process';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';

const config={APP_BASE_URL:'https://frontend.example.test',BACKEND_URL:'https://backend.example.test'};
function service(fault){
 return async(address,options)=>{
  const url=new URL(address),path=url.pathname;
  assert.equal(options.redirect,'manual');
  if(fault==='outage'&&path.includes('readiness'))return Response.json({status:'DOWN'},{status:503});
  if(path.startsWith('/actuator/'))return Response.json({status:'UP'},{status:fault==='health-exposed'?200:403});
  if(path==='/api/v1/auth/providers')return Response.json({google:fault==='proxy'&&url.hostname.startsWith('frontend')});
  if(path==='/api/v1/me')return Response.json({error:{}},{status:fault==='fake-account'?200:401});
  if(path.startsWith('/preview/')){
   const name=path.split('/').pop();
   return new Response('',{status:fault==='samples'?200:307,headers:{location:fault==='external'?'https://elsewhere.example.test/workspace':'/workspace'+(name==='workspace'?'':'/'+name)}});
  }
  return Response.json({items:[]});
 };
}
test('hosted read-only acceptance checks private probes, proxy identity, auth, redirects and measures requests',async()=>{
 const result=await verifyStaging(config,service());
 assert.equal(result.requests,8);assert.equal(result.concurrency,2);assert.ok(result.p95Ms>=0);
});
test('hosted acceptance rejects outage, proxy mismatch, fabricated login, samples and external redirects',async()=>{
 for(const fault of ['outage','health-exposed','proxy','fake-account','samples','external'])await assert.rejects(()=>verifyStaging(config,service(fault)),fault);
});
test('hosted acceptance rejects HTTP and credential-bearing origins before making a request',async()=>{
 for(const APP_BASE_URL of ['http://frontend.example.test','https://user:secret@frontend.example.test','https://frontend.example.test/path']){
  let calls=0;await assert.rejects(()=>verifyStaging({...config,APP_BASE_URL},async()=>{calls++;throw Error('Should not request.')}));assert.equal(calls,0);
 }
});
test('private Site access is sent only to the configured frontend origin',async()=>{
 const request=service();let frontend=0,backend=0;
 await verifyStaging({...config,FRONTEND_SITE_ACCESS_TOKEN:'synthetic-test-token'},async(address,options)=>{
  if(new URL(address).origin===config.APP_BASE_URL){frontend++;assert.equal(options.headers['OAI-Sites-Authorization'],'Bearer synthetic-test-token');}
  else{backend++;assert.deepEqual(options.headers,{});}
  return request(address,options);
 });
 assert.equal(frontend,14);assert.equal(backend,3);
});
test('staging CLI checks and requests the same injected snapshot with explicit existing-admin parsing',async()=>{
 const environment={...config,APP_ENV:'staging',FRONTEND_SITE_ACCESS_TOKEN:'synthetic-private-site'},calls=[];
 const request=service();let checked=false;
 const result=await runStagingCli(['--existing-admin','--from-env'],{environment,check:(flags,values)=>{
  assert.deepEqual(flags,['--from-env','--existing-admin']);assert.deepEqual(values,environment);assert.notEqual(values,environment);checked=true;environment.APP_BASE_URL='https://changed.example.test';
 },request:async(address,options)=>{assert(checked);calls.push(address);return request(address,options);}});
 assert.equal(result.requests,8);assert(calls.some(address=>address.startsWith(config.APP_BASE_URL)));assert(!calls.some(address=>address.startsWith(environment.APP_BASE_URL)));
});
test('staging CLI rejects filenames, missing mode, duplicate or unknown flags before checking or requests',async()=>{
 for(const args of [[],['.env.staging'],['--from-env','.env.staging'],['--existing-admin'],['--from-env','--from-env'],['--from-env','--unknown']]){
  let calls=0;await assert.rejects(()=>runStagingCli(args,{check:()=>{calls++;},request:async()=>{calls++;throw Error('Unexpected request');}}),/Use --from-env/);assert.equal(calls,0);
 }
 let calls=0;await assert.rejects(()=>runStagingCli(['--from-env'],{check:()=>{throw Error('Invalid injected configuration');},request:async()=>{calls++;}}),/Invalid injected/);assert.equal(calls,0);
});
test('actual staging CLI uses injected mode without reading the default staging file',t=>{
 const dir=mkdtempSync(join(tmpdir(),'getlancer-staging-cli-'));t.after(()=>rmSync(dir,{recursive:true,force:true}));
 writeFileSync(join(dir,'.env.staging'),'APP_BASE_URL=https://wrong.example.test\n');
 const hook=join(dir,'injected-check.mjs');
 writeFileSync(hook,`import assert from 'node:assert/strict';
import cp from 'node:child_process';import fs from 'node:fs';import {syncBuiltinESMExports} from 'node:module';
const originalRead=fs.readFileSync;
fs.readFileSync=(file,...args)=>{assert(!String(file).endsWith('.env.staging'),'Hosted CLI must not read an environment file');return originalRead(file,...args);};
cp.execFileSync=(command,args,options)=>{assert.equal(command,process.execPath);assert(args[0].endsWith('check-environment.mjs'));assert.deepEqual(args.slice(1),['--from-env','--existing-admin']);assert.equal(options.env.APP_ENV,'staging');assert.equal(options.env.APP_BASE_URL,'https://frontend.example.test');};
syncBuiltinESMExports();
globalThis.fetch=async address=>{const path=new URL(address).pathname;if(path.startsWith('/actuator/'))return new Response('',{status:403});if(path==='/api/v1/auth/providers')return Response.json({google:false});if(path==='/api/v1/me')return new Response('',{status:401});if(path.startsWith('/preview/')){const name=path.split('/').pop();return new Response('',{status:307,headers:{location:'/workspace'+(name==='workspace'?'':'/'+name)}});}return Response.json({items:[]});};
`);
 const result=spawnSync(process.execPath,['--import',hook,fileURLToPath(new URL('../scripts/verify-staging.mjs',import.meta.url)),'--from-env','--existing-admin'],{cwd:dir,encoding:'utf8',timeout:10000,env:{...process.env,...config,APP_ENV:'staging'}});
 assert.equal(result.status,0,result.stdout+result.stderr);assert.match(result.stdout,/Hosted read-only acceptance passed/);
});
