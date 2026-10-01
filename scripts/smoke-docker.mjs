// Ephemeral CI stack only; never use the owner's database or credentials here.
import assert from 'node:assert/strict';
import {readdirSync,writeFileSync} from 'node:fs';
import {execFileSync} from 'node:child_process';
import {createHmac,randomBytes} from 'node:crypto';
import {setTimeout as delay} from 'node:timers/promises';
import {readEnvironment} from './local-config.mjs';
import {sourceFixture} from './ci-source-fixture.mjs';
const env=readEnvironment(new URL('../.env',import.meta.url));
assert.equal(process.env.CI,'true','This smoke check runs only in disposable CI.');
assert.match(process.env.COMPOSE_PROJECT_NAME||'',/^getlancer-ci-[0-9]+$/,'Use an isolated CI Compose project.');
assert.equal(env.ADMIN_EMAIL,'ci-admin@example.test','Use the synthetic CI administrator.');
const query=sql=>execFileSync('docker',['compose','exec','-T','db','psql','-U','postgres','-d','getLancer','-Atc',sql],{encoding:'utf8'}).trim();
assert.equal(query('SELECT current_database()'),'getLancer');
assert.equal(query("SELECT count(*) FROM getlancer.user_roles WHERE role='ADMIN'"),'1');
assert.equal(query('SELECT count(*) FROM getlancer.users'),'1','Startup creates only the configured administrator, never sample accounts.');
assert.equal(query('SELECT count(*) FROM getlancer.products'),'0','Startup must not seed sample projects.');
assert.equal(query('SELECT count(*) FROM getlancer.teams'),'0','Startup must not seed sample teams.');
const migrationFiles=readdirSync(new URL('../backend/src/main/resources/db/migration/',import.meta.url)).filter(name=>/^V[^_]+__.*\.sql$/.test(name)).sort();
const appliedFiles=query("SELECT script FROM getlancer.flyway_schema_history WHERE success AND type='SQL' ORDER BY script").split('\n').sort();
assert.deepEqual(appliedFiles,migrationFiles,'Every versioned migration must be applied exactly once.');
assert.equal(query("SELECT count(*) FROM getlancer.flyway_schema_history WHERE NOT success"),'0');
function session(){
 const cookies=new Map();
 return async function request(path,body,method=body?'POST':'GET',expected,options={}){
  const response=await fetch('http://localhost:8080'+path,{method,headers:{Origin:'http://localhost:3000','X-Requested-With':'getlancer',...(body instanceof FormData?{}:{'Content-Type':'application/json'}),...options.headers,Cookie:[...cookies].map(([k,v])=>k+'='+v).join('; ')},body:body instanceof FormData?body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(10000)});
  for(const value of response.headers.getSetCookie()){const pair=value.split(';')[0],split=pair.indexOf('=');cookies.set(pair.slice(0,split),pair.slice(split+1));}
  if(expected)assert.equal(response.status,expected,`${method} ${path}`);
  else assert.ok(response.ok,`${method} ${path} returned HTTP ${response.status}`);
  return options.raw?response:response.status===204?{}:response.json();
 };
}
const api=session(),builder=session(),client=session(),visitor=session();
assert.equal((await api('/actuator/health/readiness')).status,'UP');
assert.equal((await api('/api/v1/auth/providers')).google,false);
assert.deepEqual((await visitor('/api/v1/products')).items,[],'A real empty database returns no sample listings.');
assert.deepEqual((await visitor('/api/v1/teams')).items,[],'A real empty database returns no sample teams.');
await visitor('/api/v1/me',undefined,'GET',401);
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
console.log('Docker smoke passed: exact database, all versioned migrations, one admin, password + MFA, protected admin API, private bucket.');

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
 const me=await account('/api/v1/me');assert.equal(me.emailVerified,true);browserAccounts[email]={email,password};return me;
}
const browserAccounts={};
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
 const {id}=await builder('/api/v1/developer/products',{title:'CI Inventory '+index,summary:'Track stock and orders for independent shops.',description:'A working inventory showcase with stock tracking, product search and order history.',projectType:'SAAS',category:'Inventory',technology:'React',visibility:'PUBLIC',contribution:'Designed the schema and implemented stock tracking.',liveUrl:index?'https://example.com/demo':'',videoUrl:'',repositoryUrl:'https://github.com/example/ci-inventory-'+index,pricingNote:'',availableForSimilarWork:true,rightsConfirmed:true},'POST',201);
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

// V1.5 exercises the real HTTP permission boundary and persisted state.
await builder('/api/v1/developer/availability',{status:'LIMITED'},'PUT');
assert.equal((await builder('/api/v1/developer/trust')).profile.availability_status,'LIMITED');
await client('/api/v1/admin/trust',undefined,'GET',403);
await builder(`/api/v1/developer/products/${project.id}/verification`,{});
const proof=(await api('/api/v1/admin/trust')).verifications.find(v=>v.product_id===project.id);
assert.ok(proof?.challenge,'Repository request must reach the administrator queue');
await api(`/api/v1/admin/verifications/${project.id}/approve`,{reason:'Disposable CI fixture simulates a manual evidence review.'});
assert.equal((await visitor('/api/v1/products/'+project.slug)).repositoryVerified,true);
assert.ok((await api('/api/v1/admin/trust')).verified.some(v=>v.product_id===project.id),'Verified evidence must be discoverable for revocation');
await api(`/api/v1/admin/verifications/${project.id}/revoke`,{reason:'Disposable CI fixture checks removal of repository evidence.'});
assert.equal((await visitor('/api/v1/products/'+project.slug)).repositoryVerified,false);
assert.ok(!(await api('/api/v1/admin/trust')).verified.some(v=>v.product_id===project.id));
await api(`/api/v1/admin/verifications/${project.id}/revoke`,{reason:'Repeated revocation must be rejected.'},'POST',409);
assert.equal((await api(`/api/v1/admin/earned-capacity/${inquiry.id}`,{reason:'Client-confirmed CI completion reviewed for capacity.'})).awarded,true);
assert.equal((await api(`/api/v1/admin/earned-capacity/${inquiry.id}`,{reason:'Duplicate CI award must leave entitlement unchanged.'})).awarded,false);
assert.equal((await builder('/api/v1/developer/trust')).activeSlotLimit,4);
assert.equal((await visitor('/api/v1/products/'+project.slug)).demoHealth,'UNKNOWN');
console.log('Connected V1.5 passed: availability, admin isolation, repository review/revocation, unknown health and idempotent earned capacity.');

// V2 consent, team privacy and V2.5 business sourcing through the real HTTP service.
await builder('/api/v1/developer/availability',{status:'AVAILABLE_NOW'},'PUT');
await client('/api/v1/developer/profile',{displayName:'CI Client',headline:'Independent API builder',bio:'I build accessible client software and secure Java APIs for businesses.',technology:'React',category:'Inventory',availabilityStatus:'AVAILABLE_NOW',githubUrl:'',linkedinUrl:''},'PUT');
await client('/api/v1/developer/profile/submit',{});
const clientAccount=await client('/api/v1/me');
await api('/api/v1/admin/profiles/'+clientAccount.id+'/approve',{reason:'Disposable CI client/builder role approval.'});
const team=await builder('/api/v1/teams',{name:'CI Studio',summary:'An independent inventory software studio.',availability:'AVAILABLE_NOW',projectRange:'5k-25k'});
await builder(`/api/v1/teams/${team.id}/projects`,{productId:project.id});
const invitation=await builder(`/api/v1/teams/${team.id}/invitations`,{userId:clientAccount.id,role:'MEMBER',membershipType:'PERMANENT'});
await client(`/api/v1/teams/${team.id}/workspace`,undefined,'GET',403);
await client(`/api/v1/team-invitations/${invitation.id}/respond`,{action:'ACCEPT'});
assert.equal((await client(`/api/v1/teams/${team.id}/workspace`)).myRole,'MEMBER');
assert.deepEqual((await client(`/api/v1/teams/${team.id}/workspace`)).leads,[]);
await client(`/api/v1/teams/${team.id}/roles`,{title:'API builder',description:'Build accessible software',skills:'Java',contractType:'CONTRACT',compensationBand:'5k'},'POST',403);
const manager=session();const managerAccount=await register(manager,'ci-manager@example.test','CI Hiring Manager');
const teamLead=await manager(`/api/v1/teams/${team.id}/leads`,{title:'Inventory portal',description:'Build a private stock approval portal.',budget:'10k',timeline:'2 months'});
await builder(`/api/v1/teams/${team.id}/leads/${teamLead.id}`,{status:'INTERESTED',note:'Confidential internal capacity note'},'PATCH');
await builder(`/api/v1/teams/${team.id}/leads/${teamLead.id}`,{status:'PROPOSAL_SENT'},'PATCH');
await manager(`/api/v1/teams/${team.id}/leads/${teamLead.id}/respond`,{action:'ACCEPT'});
assert.ok(!JSON.stringify(await manager('/api/v1/me/export')).includes('Confidential internal capacity note'));
const business=await client('/api/v1/businesses',{name:'CI Business',summary:'Private software hiring workspace.'});
const businessRoot=`/api/v1/businesses/${business.id}`;
const businessInvite=await client(businessRoot+'/invitations',{email:'ci-manager@example.test'});
await manager(businessRoot,undefined,'GET',404);
await manager(`/api/v1/business-invitations/${businessInvite.id}/respond`,{action:'ACCEPT'});
assert.equal((await manager(businessRoot)).myRole,'HIRING_MANAGER');
await manager(businessRoot,{name:'Unauthorized',summary:'Owner-only profile change.'},'PUT',403);
const brief=await manager(businessRoot+'/requests',{title:'Private inventory workflow',description:'Build private inventory approvals and stock planning for our operations team.',category:'Inventory',technology:'React',budget:'10k',timeline:'2 months',availableOnly:true,repositoryVerifiedOnly:false,status:'OPEN'});
const briefRoot=businessRoot+'/requests/'+brief.id;
await builder(briefRoot+'/matches',undefined,'GET',404);
const matches=(await client(briefRoot+'/matches')).items;
assert.ok(matches.some(c=>c.kind==='BUILDER'&&c.targetId===owner.id));
assert.ok(matches.some(c=>c.kind==='TEAM'&&c.targetId===team.id));
assert.ok(matches.every(c=>c.reasons.some(reason=>reason.includes('evidence:'))));
const talent=await client(businessRoot+'/talent-lists',{name:'Inventory talent'});
const candidate={kind:'BUILDER',targetId:owner.id,reason:'Public Inventory and React evidence matches the private brief.'};
await manager(businessRoot+'/talent-lists/'+talent.id+'/entries',candidate);
await manager(briefRoot+'/shortlist',candidate);
assert.equal((await client(businessRoot+'/talent-lists/'+talent.id+'/entries')).items.length,1);
assert.deepEqual((await api('/api/v1/admin/concierge')).items,[]);
await manager(briefRoot+'/concierge',{});
await manager('/api/v1/admin/concierge',undefined,'GET',403);
const sourcing=(await api('/api/v1/admin/concierge')).items.find(c=>c.requestId===brief.id);assert.ok(sourcing);
await api('/api/v1/admin/concierge/'+sourcing.id,{status:'IN_PROGRESS'},'PATCH');
await api('/api/v1/admin/concierge/'+sourcing.id+'/shortlist',{kind:'TEAM',targetId:team.id,reason:'The studio has consented inventory proof and active approved members.'});
await api('/api/v1/admin/concierge/'+sourcing.id,{status:'FULFILLED'},'PATCH');
assert.ok((await manager(briefRoot+'/shortlist')).items.some(s=>s.source==='CONCIERGE'));
await builder(`/api/v1/teams/${team.id}/projects/${project.id}`,{},'DELETE');
assert.ok(!(await manager(briefRoot+'/matches')).items.some(c=>c.kind==='TEAM'&&c.targetId===team.id));
await client(businessRoot+'/members/'+managerAccount.id,{},'DELETE');
await manager(briefRoot+'/shortlist',undefined,'GET',404);
await client(briefRoot,{...brief,status:'CLOSED'},'PUT');
await client(briefRoot+'/matches',undefined,'GET',409);
console.log('Connected V2/V2.5 passed: team consent/private roles, client-reported outcome, business invitations, tenant isolation, real evidence matching, talent lists, opt-in MFA concierge and immediate revocation.');

// Source commerce uses the real Java validation, PostgreSQL and private MinIO storage.
await builder(`/api/v1/developer/products/${products[2]}/verification`,{});
await api(`/api/v1/admin/verifications/${products[2]}/approve`,{reason:'Disposable source fixture: operator ownership review only, no real seller claim.'});
const commerceFixtures={},source=sourceFixture();
for(const device of ['smoke','desktop','phone','tablet']){
 const template=await builder('/api/v1/me/templates',{productId:products[2],title:'CI Source '+device,summary:'A versioned inventory starter for connected acceptance.',description:'A synthetic source package to verify private storage, version review and license handling.',priceMinor:490000,licenseTerms:'Synthetic license for one commercial end product. Modification allowed; source redistribution prohibited.'},'POST',201);
 commerceFixtures[device]=template;
 if(device==='smoke'){
  const form=new FormData();form.set('version','1.0.0');form.set('releaseNotes','Synthetic private source release for storage acceptance.');form.set('rightsConsent','true');form.set('file',new Blob([source],{type:'application/zip'}),'source.zip');
  const release=await builder(`/api/v1/me/templates/${template.id}/versions`,form,'POST',201);
  await visitor('/api/v1/templates/'+template.slug,undefined,'GET',404);
  await client(`/api/v1/me/templates/${template.id}/versions/${release.id}/package`,undefined,'GET',404);
  await builder(`/api/v1/me/templates/${template.id}/versions/${release.id}/submit`,{rightsConsent:true});
  const attachment=await api(`/api/v1/admin/templates/${template.id}/versions/${release.id}/package`,undefined,'GET',200,{raw:true});
  assert.equal(attachment.headers.get('content-type'),'application/zip');assert.match(attachment.headers.get('cache-control'),/no-store/);assert.deepEqual(Buffer.from(await attachment.arrayBuffer()),source);
  await api(`/api/v1/admin/templates/${template.id}/versions/${release.id}/review`,{action:'APPROVE',reason:'CI inspected the package bytes, manifest and synthetic license without executing source.',rightsReviewed:true,packageReviewed:true});
  const listing=await visitor('/api/v1/templates/'+template.slug);assert.equal(listing.version,'1.0.0');assert.ok(!JSON.stringify(listing).includes('storage_key'));
  await client(`/api/v1/templates/${template.id}/orders`,{versionId:release.id,licenseConsent:true},'POST',503,{headers:{'Idempotency-Key':crypto.randomUUID()}});
  assert.equal(query('SELECT count(*) FROM getlancer.template_purchases'),'0','Disabled collection must never create synthetic purchase entitlements.');
  const exported=await builder('/api/v1/me/export');assert.ok(exported.sourceTemplates.some(t=>t.id===template.id));assert.ok(exported.sourceReleases.some(v=>v.id===release.id));assert.ok(!JSON.stringify(exported.sourceReleases).includes('storage_key'));
  await builder(`/api/v1/me/templates/${template.id}/archive`,{});await visitor('/api/v1/templates/'+template.slug,undefined,'GET',404);
 }
}
console.log('Connected V3.5 passed: current ownership review, static ZIP validation, real private S3 bytes, MFA package review, public discovery, scoped export, disabled-payment denial and archival.');

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

// Browser acceptance gets real API-created records, never a production seed or mock.
// The secrets are synthetic, ignored, owner-readable and confined to this CI job.
await builder(`/api/v1/teams/${team.id}/projects`,{productId:products[2]});
const browserBusinesses={};
for(const device of ['desktop','phone','tablet']){
 const b=await client('/api/v1/businesses',{name:'CI Browser '+device,summary:'Disposable connected browser acceptance workspace.'});
 await client(`/api/v1/businesses/${b.id}/invitations`,{email:'ci-manager@example.test'});
 browserBusinesses[device]=b;
}
writeFileSync('.ci-connected.json',JSON.stringify({project:process.env.COMPOSE_PROJECT_NAME,accounts:browserAccounts,businesses:browserBusinesses,teamId:team.id,builderId:owner.id,commerce:commerceFixtures}),{mode:0o600});
console.log('Connected browser prerequisites created through the real Java API.');
