// Synthetic built frontend only; ZIP creation never executes a submitted package.
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {readEnvironment} from './local-config.mjs';

export function hostingFixture(){return execFileSync('python3',['-c',String.raw`
import io,sys,zipfile
stream=io.BytesIO()
with zipfile.ZipFile(stream,'w',zipfile.ZIP_DEFLATED) as z:
 z.writestr('index.html','<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>CI isolated frontend</title><link rel="stylesheet" href="assets/app.css"><main><h1>CI isolated frontend</h1><p>Built static HTML, CSS and browser JavaScript.</p><button id="increment">Count: 0</button></main><script src="assets/app.js"></script></html>')
 z.writestr('assets/app.css','body{margin:0;padding:24px;font:16px system-ui;background:#f7f7fa;color:#252538}main{max-width:640px;margin:auto}button{min-height:44px;padding:12px;border:1px solid #666;border-radius:8px;font:inherit}')
 z.writestr('assets/app.js','let n=0;document.getElementById("increment").onclick=()=>{document.getElementById("increment").textContent="Count: "+(++n)};')
 z.writestr('LICENSE.txt','Synthetic CI-only package. Permission granted for testing this fixture. No dependencies or real customer data.')
sys.stdout.buffer.write(stream.getvalue())
`]);}

export async function publicDemo(record,path='/'){
 const url=new URL(record.url);
 assert.match(url.hostname,/^[0-9a-f-]+\.demo\.localhost$/);
 return fetch('http://localhost:8090'+path,{headers:{Host:url.host},redirect:'manual',signal:AbortSignal.timeout(6000)});
}

export async function hostingFixtures({builder,client,visitor,admin,productId}){
 assert.equal(process.env.CI,'true');assert.match(process.env.COMPOSE_PROJECT_NAME||'',/^getlancer-ci-[0-9]+$/);
 const env=readEnvironment();assert.equal(env.ADMIN_EMAIL,'ci-admin@example.test');assert.equal(env.HOSTED_DEMOS_ENABLED,'true');
 const bytes=hostingFixture(),config=await visitor('/api/v1/hosting/config');assert.equal(config.enabled,true);
 assert.ok((await builder('/api/v1/me/hosting/sources')).items.some(item=>item.id===productId));
 const form=new FormData();for(const [key,value] of Object.entries({productId,title:'CI Hosting Smoke',version:'1.0.0',rightsConsent:'true'}))form.set(key,value);
 form.set('file',new Blob([bytes],{type:'application/zip'}),'frontend.zip');
 const draft=await builder('/api/v1/me/hosting',form,'POST',201);
 await client('/api/v1/me/hosting/'+draft.id,undefined,'GET',404);
 await client('/api/v1/admin/hosting',undefined,'GET',403);
 await visitor('/api/v1/hosting/gateway/'+draft.id,undefined,'GET',403);
 await builder('/api/v1/me/hosting/'+draft.id+'/submit',{rightsConsent:true});
 await admin('/api/v1/admin/hosting/'+draft.id+'/review',{action:'APPROVE',reason:'CI rights and static package review.',rightsReviewed:true,packageReviewed:true},'POST',409);
 const download=await admin('/api/v1/admin/hosting/'+draft.id+'/package',undefined,'GET',200,{raw:true});
 assert.deepEqual(Buffer.from(await download.arrayBuffer()),bytes);assert.match(download.headers.get('cache-control'),/no-store/);
 await admin('/api/v1/admin/hosting/'+draft.id+'/review',{action:'APPROVE',reason:'CI inspected actual immutable package bytes, static manifest and synthetic rights.',rightsReviewed:true,packageReviewed:true});
 let ready=await builder('/api/v1/me/hosting/'+draft.id+'/deploy',{});assert.equal(ready.deploymentState,'READY');
 const page=await publicDemo(ready);assert.equal(page.status,200);assert.match(await page.text(),/CI isolated frontend/);
 assert.match(page.headers.get('content-security-policy'),/worker-src 'none'/);assert.match(page.headers.get('content-security-policy'),/form-action 'none'/);
 assert.equal(page.headers.get('x-content-type-options'),'nosniff');assert.match(page.headers.get('cache-control'),/no-store/);
 const adminProbe=await publicDemo(ready,'/deployments/'+ready.deploymentId);assert.equal(adminProbe.status,403);
 assert.equal((await builder('/api/v1/me/hosting/'+draft.id+'/deploy',{})).deploymentId,ready.deploymentId);
 const exported=await builder('/api/v1/me/export');assert.ok(exported.hostedDemos.hosting.some(item=>item.id===draft.id));assert.ok(!JSON.stringify(exported.hostedDemos).includes('storage_key'));
 await admin('/api/v1/admin/hosting/'+draft.id+'/review',{action:'SUSPEND',reason:'CI verifies approval withdrawal denies public content immediately.',rightsReviewed:true,packageReviewed:true});
 assert.equal((await publicDemo(ready)).status,403);
 const removed=await builder('/api/v1/me/hosting/'+draft.id+'/withdraw',{});assert.equal(removed.deploymentState,'DELETED');
 assert.equal((await builder('/api/v1/me/hosting/'+draft.id+'/withdraw',{})).deploymentState,'DELETED');
 assert.notEqual((await publicDemo(ready)).status,200);
 // One ready record remains for the real database-outage drill and restore check.
 const live=new FormData();for(const [key,value] of Object.entries({productId,title:'CI Hosting Recovery',version:'1.0.1',rightsConsent:'true'}))live.set(key,value);
 live.set('file',new Blob([bytes],{type:'application/zip'}),'frontend.zip');
 const record=await builder('/api/v1/me/hosting',live,'POST',201);await builder('/api/v1/me/hosting/'+record.id+'/submit',{rightsConsent:true});
 await admin('/api/v1/admin/hosting/'+record.id+'/package',undefined,'GET',200,{raw:true});
 await admin('/api/v1/admin/hosting/'+record.id+'/review',{action:'APPROVE',reason:'CI verified actual ZIP and synthetic rights for outage and restore acceptance.',rightsReviewed:true,packageReviewed:true});
 ready=await builder('/api/v1/me/hosting/'+record.id+'/deploy',{});assert.equal(ready.deploymentState,'READY');
 console.log('Connected static hosting passed: private S3 ZIP, current proof, mandatory MFA download review, real isolated publisher, gateway revocation, withdrawal tombstone and safe export.');
 return {productId,ready};
}
