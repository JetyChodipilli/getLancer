// Ephemeral CI stack only; never use the owner's database or credentials here.
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {createHmac,randomBytes} from 'node:crypto';
import {setTimeout as delay} from 'node:timers/promises';
import {readEnvironment} from './local-config.mjs';
const env=readEnvironment(new URL('../.env',import.meta.url));
assert.equal(process.env.CI,'true','This smoke check runs only in disposable CI.');
assert.match(process.env.COMPOSE_PROJECT_NAME||'',/^getlancer-ci-[0-9]+$/,'Use an isolated CI Compose project.');
assert.equal(env.ADMIN_EMAIL,'ci-admin@example.test','Use the synthetic CI administrator.');
const query=sql=>execFileSync('docker',['compose','exec','-T','db','psql','-U','postgres','-d','getLancer','-Atc',sql],{encoding:'utf8'}).trim();
assert.equal(query('SELECT current_database()'),'getLancer');
assert.equal(query("SELECT count(*) FROM getlancer.user_roles WHERE role='ADMIN'"),'1');
assert.equal(query("SELECT count(*) FROM getlancer.flyway_schema_history WHERE success AND type='SQL'"), '10');
function session(){
 const cookies=new Map();
 return async function request(path,body,method=body?'POST':'GET',expected){
  const response=await fetch('http://localhost:8080'+path,{method,headers:{Origin:'http://localhost:3000','X-Requested-With':'getlancer','Content-Type':'application/json',Cookie:[...cookies].map(([k,v])=>k+'='+v).join('; ')},body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(10000)});
  for(const value of response.headers.getSetCookie()){const pair=value.split(';')[0],split=pair.indexOf('=');cookies.set(pair.slice(0,split),pair.slice(split+1));}
  if(expected)assert.equal(response.status,expected,`${method} ${path}`);
  else assert.ok(response.ok,`${method} ${path} returned HTTP ${response.status}`);
  return response.status===204?{}:response.json();
 };
}
const api=session(),builder=session(),client=session(),visitor=session();
assert.equal((await api('/actuator/health/readiness')).status,'UP');
assert.equal((await api('/api/v1/auth/providers')).google,false);
assert.equal((await api('/api/v1/auth/login',{email:env.ADMIN_EMAIL,password:env.ADMIN_BOOTSTRAP_PASSWORD})).mfaRequired,true);
// RFC 6238, matching the backend's SHA-1 / six digit / 30 second authenticator.
const bits=[...env.ADMIN_TOTP_SECRET].map(c=>'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'.indexOf(c).toString(2).padStart(5,'0')).join('');
const secret=Buffer.from(bits.match(/.{8}/g).map(b=>parseInt(b,2)));
const counter=Buffer.alloc(8);counter.writeBigUInt64BE(BigInt(Math.floor(Date.now()/30000)));
const digest=createHmac('sha1',secret).update(counter).digest(),offset=digest[digest.length-1]&15;
const totp=String((digest.readUInt32BE(offset)&0x7fffffff)%1000000).padStart(6,'0');
await api('/api/v1/auth/login/mfa',{totp});
assert.ok((await api('/api/v1/me')).roles.includes('ADMIN'));
assert.deepEqual((await api('/api/v1/admin/products/pending')).items,[]);

const storage=await fetch('http://localhost:9000/getlancer',{signal:AbortSignal.timeout(10000)});
assert.equal(storage.status,403,'Proof bucket must not allow anonymous listing.');
console.log('Docker smoke passed: exact database, ten migrations, one admin, password + MFA, protected admin API, private bucket.');

// Read actual delivered SMTP messages, never account_tokens or the outbox body.
const seenMail=new Set();
async function confirmation(email,kind){
 for(let attempt=0;attempt<30;attempt++){
  const response=await fetch('http://localhost:8025/api/v1/messages?limit=100',{signal:AbortSignal.timeout(5000)});
  assert.ok(response.ok,'Mailpit inbox must be reachable');
  for(const summary of (await response.json()).messages){
   if(seenMail.has(summary.ID)||!summary.To.some(to=>to.Address===email))continue;
   const message=await fetch('http://localhost:8025/api/v1/message/'+summary.ID,{signal:AbortSignal.timeout(5000)}).then(r=>r.json());
   seenMail.add(summary.ID);
   const token=message.Text?.match(/http:\/\/localhost:3000\/confirm#token=([^\s]+)/)?.[1];
   if(!token)continue;
   const context=await visitor('/api/v1/auth/confirmation',{token});
   if(context.kind===kind)return token;
  }
  await delay(2000);
 }
 throw Error('Expected confirmation email was not delivered: '+kind);
}
async function register(account,email,name){
 const password=randomBytes(24).toString('base64url')+'aA1!';
 await account('/api/v1/auth/signup',{email,password,displayName:name,acceptedTerms:true},'POST',201);
 await account('/api/v1/auth/verify-email',{token:await confirmation(email,'EMAIL_VERIFICATION')});
 await account('/api/v1/auth/login',{email,password});
 const me=await account('/api/v1/me');assert.equal(me.emailVerified,true);return me;
}
const builderEmail='ci-builder@example.test',clientEmail='ci-client@example.test';
const owner=await register(builder,builderEmail,'CI Builder');
await register(client,clientEmail,'CI Client');
await builder('/api/v1/admin/products/pending',undefined,'GET',403);
await builder('/api/v1/developer/profile',{displayName:'CI Builder',headline:'Inventory software builder',bio:'I design and build inventory software for independent businesses.',technology:'React',category:'Inventory',availabilityStatus:'AVAILABLE_NOW',githubUrl:'',linkedinUrl:''},'PUT');
await builder('/api/v1/developer/profile/submit',{});
await api('/api/v1/admin/profiles/'+owner.id+'/approve',{reason:'CI reviewed the complete builder profile.'});
console.log('Connected onboarding passed: SMTP verification, login, profile approval and administrator isolation.');

const products=[];let mediaId;
// Valid PNG fixture; bytes go through signed S3 PUT and backend image decoding.
const png=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAEElEQVR4nGNQSjsDRAwQCgAilgVRqmC7UQAAAABJRU5ErkJggg==','base64');
for(let index=0;index<4;index++){
 const {id}=await builder('/api/v1/developer/products',{title:'CI Inventory '+index,summary:'Track stock and orders for independent shops.',description:'A working inventory showcase with stock tracking, product search and order history.',projectType:'SAAS',category:'Inventory',technology:'React',visibility:'PUBLIC',contribution:'Designed the schema and implemented stock tracking.',liveUrl:index?'https://example.com/demo':'',videoUrl:'',repositoryUrl:'',pricingNote:'',availableForSimilarWork:true,rightsConfirmed:true},'POST',201);
 products.push(id);
 if(index===0){
  const upload=await builder(`/api/v1/developer/products/${id}/media/upload-request`,{filename:'proof.png',contentType:'image/png',sizeBytes:png.length});
  assert.equal(new URL(upload.url).origin,'http://localhost:9000');
  const put=await fetch(upload.url,{method:'PUT',headers:upload.headers,body:png,signal:AbortSignal.timeout(10000)});assert.ok(put.ok,'Signed proof upload must succeed');
  const complete=await builder(`/api/v1/developer/products/${id}/media/complete`,{uploadId:upload.uploadId,altText:'Inventory proof fixture'});
  mediaId=complete.id;assert.ok(mediaId);
  assert.equal((await builder(`/api/v1/developer/products/${id}/media/complete`,{uploadId:upload.uploadId,altText:'Inventory proof fixture'})).id,mediaId);
  await visitor('/api/v1/media/'+mediaId,undefined,'GET',401);
 }
 await builder(`/api/v1/developer/products/${id}/submit`,{});
 await api(`/api/v1/admin/products/${id}/approve`,{reason:'CI inspected the proof and ownership declaration.'});
}
assert.equal((await builder('/api/v1/developer/products')).activeCount,3);
await builder(`/api/v1/developer/products/${products[3]}/activate`,{},'POST',409);
await builder(`/api/v1/developer/products/${products[1]}/archive`,{});
await builder(`/api/v1/developer/products/${products[3]}/activate`,{});
assert.equal((await builder('/api/v1/developer/products')).activeCount,3);
const discovery=await visitor('/api/v1/products?q=Inventory&technology=React&projectType=SAAS');
assert.equal(discovery.totalItems,3);
const project=discovery.items.find(p=>p.id===products[0]);assert.ok(project);
assert.equal((await visitor('/api/v1/products/'+project.slug)).id,project.id);
for(const suffix of ['', '?variant=thumbnail']){
 const image=await fetch('http://localhost:8080/api/v1/media/'+mediaId+suffix,{signal:AbortSignal.timeout(10000)});
 assert.equal(image.status,200);assert.match(image.headers.get('content-type'),/^image\/png/);assert.match(image.headers.get('cache-control'),/no-store/);
 assert.ok((await image.arrayBuffer()).byteLength>0);
}
await client(`/api/v1/products/${project.id}/save`,{});
assert.equal((await client('/api/v1/me/saved-products')).items[0].id,project.id);
console.log('Connected publishing passed: signed upload, clean image/thumbnail, private drafts, discovery, saved projects and three-slot enforcement.');

const inquiry=await client('/api/v1/inquiries',{referenceProductId:project.id,clientEmail,clientName:'CI Client',companyName:'CI shop',requestType:'SIMILAR_BUILD',description:'Please build a stock tracking system with order history for our shop.',budgetBand:'USD_3K_10K',timelineBand:'ONE_TO_THREE_MONTHS',website:''},'POST',201);
assert.equal((await builder('/api/v1/developer/inquiries')).totalItems,0);
await visitor('/api/v1/inquiries/confirm-email',{token:await confirmation(clientEmail,'CLIENT_INQUIRY_CONFIRMATION')});
assert.equal((await builder('/api/v1/developer/inquiries')).items[0].status,'INQUIRY_RECEIVED');
await client(`/api/v1/me/inquiries/${inquiry.id}/review`,{rating:5,reviewText:'This premature review must be rejected.',visibility:'ANONYMOUS'},'POST',409);
for(const action of ['responded','discussion','proposal-sent','request-hire-confirmation'])await builder(`/api/v1/inquiries/${inquiry.id}/${action}`,{});
await visitor('/api/v1/auth/confirm',{token:await confirmation(clientEmail,'HIRE_CONFIRMATION'),decision:'ACCEPT'});
assert.equal((await client('/api/v1/me/inquiries/'+inquiry.id)).status,'HIRED');
await builder(`/api/v1/inquiries/${inquiry.id}/in-progress`,{});
await builder(`/api/v1/inquiries/${inquiry.id}/request-completion-confirmation`,{});
await visitor('/api/v1/auth/confirm',{token:await confirmation(clientEmail,'COMPLETION_CONFIRMATION'),decision:'ACCEPT'});
assert.equal((await client('/api/v1/me/inquiries/'+inquiry.id)).status,'COMPLETED');
await client(`/api/v1/me/inquiries/${inquiry.id}/review`,{rating:5,reviewText:'The completed inventory system matches the agreed scope and works well.',visibility:'ANONYMOUS'});
assert.equal((await visitor(`/api/v1/builders/${project.builderSlug}/reviews`)).totalItems,0);
const review=(await api('/api/v1/admin/reviews')).items[0];assert.ok(review);
await api(`/api/v1/admin/reviews/${review.id}/publish`,{reason:'CI verified completed engagement and review eligibility.'});
const reviews=await visitor(`/api/v1/builders/${project.builderSlug}/reviews`);
assert.equal(reviews.totalItems,1);assert.equal(reviews.items[0].clientName,'Verified client');assert.ok(!JSON.stringify(reviews).includes(clientEmail));
console.log('Connected inquiry passed: delivered confirmation links, builder follow-up, client-confirmed hire/completion and moderated anonymous review.');

const report=await client('/api/v1/reports',{targetType:'PRODUCT',targetId:project.id,reason:'MISLEADING_CLAIM',detail:'CI moderation scenario: proof requires correction.'},'POST',201);
await api(`/api/v1/admin/reports/${report.reference}/resolve`,{targetAction:'SUSPEND',reason:'Proof requires correction before this showcase can be restored.'});
await visitor('/api/v1/products/'+project.slug,undefined,'GET',404);
await visitor('/api/v1/media/'+mediaId,undefined,'GET',401);
assert.ok((await builder('/api/v1/developer/products')).items.find(p=>p.id===project.id).moderationReason);
await api('/api/v1/auth/logout',{});
console.log('Connected moderation passed: suspended showcase and proof hidden; owner sees the reason.');

// Stop only the guarded disposable CI database; always restore it, even on failure.
try{
 execFileSync('docker',['compose','stop','db'],{stdio:'pipe'});
 assert.equal((await visitor('/actuator/health/readiness',undefined,'GET',503)).status,'DOWN');
 assert.equal((await visitor('/actuator/health/liveness')).status,'UP');
}finally{execFileSync('docker',['compose','start','db'],{stdio:'pipe'});}
let recovered=false;
for(let attempt=0;attempt<30;attempt++){
 const health=await fetch('http://localhost:8080/actuator/health/readiness',{signal:AbortSignal.timeout(10000)});
 if(health.ok){recovered=true;break;}await delay(2000);
}
assert.ok(recovered,'Readiness must recover after PostgreSQL restarts.');
console.log('Database outage passed: readiness fails, liveness survives, readiness recovers.');
