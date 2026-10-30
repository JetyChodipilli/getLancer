import {test} from 'node:test';
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';

// Exercise the actual Node development server and streamed RSC response.
test('Node local development starts and streams navigation without workerd', {timeout:90000}, async t=>{
 const root=fileURLToPath(new URL('../',import.meta.url));
 const child=spawn(process.execPath,['node_modules/vite/bin/vite.js','--host','127.0.0.1','--port','0'],{cwd:root,env:{...process.env,APP_ENV:'local',APP_BASE_URL:'http://localhost:3000',DEMO_MODE:'true',BACKEND_URL:'',LOCAL_DEV_RUNTIME:'node',NO_COLOR:'1',BROWSER:'none'},stdio:['ignore','pipe','pipe']});
 t.after(()=>child.kill());
 let output='';
 const origin=await new Promise((resolve,reject)=>{
  const timer=setTimeout(()=>reject(Error('Local server startup timed out: '+output.slice(-4000))),45000);
  const read=chunk=>{output+=chunk.toString(); const match=output.match(/Local:\s+(http:\/\/127\.0\.0\.1:\d+)/); if(match){clearTimeout(timer);resolve(match[1]);}};
  child.stdout.on('data',read);child.stderr.on('data',read);
  child.once('error',error=>{clearTimeout(timer);reject(error);});
  child.once('exit',code=>{clearTimeout(timer);reject(Error('Local server exited '+code+': '+output.slice(-4000)));});
 });
 const page=await fetch(origin+'/',{signal:AbortSignal.timeout(30000)});
 assert.equal(page.status,200);assert.match(await page.text(),/The craft behind/);
 const streamed=await fetch(origin+'/login?_rsc=local-runtime-regression',{headers:{RSC:'1'},signal:AbortSignal.timeout(30000)});
 assert.equal(streamed.status,200);assert.match(streamed.headers.get('content-type'),/text\/x-component/);
 const body=await streamed.text();assert.match(body,/Log in/);assert.doesNotMatch(body,/Network connection lost|ERR_RUNTIME_FAILURE|MFERR_RUNTIME_FAILURE/);
 // A missing backend must remain unavailable; it must never masquerade as authenticated service data.
 assert.equal((await fetch(origin+'/api/v1/products',{signal:AbortSignal.timeout(10000)})).status,503);
});
