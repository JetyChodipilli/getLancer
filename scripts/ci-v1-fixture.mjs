// Initial states for the original six V1 browser journeys. Disposable CI only.
// No application test endpoint or domain-table SQL is used here.
import assert from 'node:assert/strict';
import {createHmac,randomBytes,randomUUID} from 'node:crypto';
import {readFileSync,writeFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
import {setTimeout as delay} from 'node:timers/promises';
import {readEnvironment} from './local-config.mjs';
import {resetCiRateBudgets} from './ci-connected-budget.mjs';

export function ciV1Totp(secret,now=Date.now()){
 assert.match(secret,/^[A-Z2-7]+$/);
 const bits=[...secret].map(c=>'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'.indexOf(c).toString(2).padStart(5,'0')).join('');
 const key=Buffer.from((bits.match(/.{8}/g)||[]).map(byte=>parseInt(byte,2)));
 const counter=Buffer.alloc(8);counter.writeBigUInt64BE(BigInt(Math.floor(now/30000)));
 const digest=createHmac('sha1',key).update(counter).digest(),offset=digest[digest.length-1]&15;
 return String((digest.readUInt32BE(offset)&0x7fffffff)%1000000).padStart(6,'0');
}

function session(){
 const cookies=new Map();
 return async(path,body,method=body?'POST':'GET',status=200)=>{
  const response=await fetch('http://localhost:8080/api/v1'+path,{
   method,headers:{Origin:'http://localhost:3000','X-Requested-With':'getlancer','Content-Type':'application/json',Cookie:[...cookies].map(([k,v])=>k+'='+v).join('; ')},
   body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(15000),
  });
  for(const value of response.headers.getSetCookie()){
   const pair=value.split(';')[0],split=pair.indexOf('=');cookies.set(pair.slice(0,split),pair.slice(split+1));
  }
  // Do not put password bodies, confirmation tokens or cookie values in diagnostics.
  assert.equal(response.status,status,`${method} ${path}: expected HTTP ${status}, received ${response.status}`);
  return response.status===204?{}:response.json();
 };
}

async function deliveredToken(email,consumed){
 const deadline=Date.now()+90000;
 while(Date.now()<deadline){
  const response=await fetch('http://localhost:8025/api/v1/messages?limit=500',{signal:AbortSignal.timeout(5000)});
  assert.ok(response.ok,'The disposable Mailpit inbox must be reachable.');
  for(const item of (await response.json()).messages){
   if(consumed.has(item.ID)||!item.To.some(to=>to.Address===email))continue;
   const message=await fetch('http://localhost:8025/api/v1/message/'+encodeURIComponent(item.ID),{signal:AbortSignal.timeout(5000)}).then(r=>{assert.ok(r.ok);return r.json();});
   const link=message.Text?.match(/http:\/\/localhost:3000\/confirm#token=([^\s]+)/)?.[1];
   if(link){consumed.add(item.ID);return link;}
  }
  await delay(1000);
 }
 throw Error('The fixture confirmation was not delivered to Mailpit.');
}

export async function prepareV1Fixtures(){
 assert.equal(process.env.CI,'true','V1 fixtures run only in disposable CI.');
 assert.match(process.env.COMPOSE_PROJECT_NAME||'',/^getlancer-ci-[0-9]+$/);
 const fixturePath=new URL('../.ci-connected.json',import.meta.url);
 const fixture=JSON.parse(readFileSync(fixturePath,'utf8'));
 assert.equal(fixture.project,process.env.COMPOSE_PROJECT_NAME);
 assert.equal(fixture.v1,undefined,'Prepare V1 once per fresh disposable stack.');
 const env=readEnvironment(new URL('../.env',import.meta.url));
 assert.equal(env.ADMIN_EMAIL,'ci-admin@example.test');
 // This validates the concrete CI container, project labels, private volume and synthetic users.
 resetCiRateBudgets(fixture,1);
 const admin=session(),visitor=session(),runId=randomUUID().slice(0,8),devices={};
 assert.equal((await admin('/auth/login',{email:env.ADMIN_EMAIL,password:env.ADMIN_BOOTSTRAP_PASSWORD})).mfaRequired,true);
 await admin('/auth/login/mfa',{totp:ciV1Totp(env.ADMIN_TOTP_SECRET)});
 assert.ok((await admin('/me')).roles.includes('ADMIN'));
 const account=(device,journey,role)=>({
  email:`ci-v1-${runId}-${device}-${journey}-${role}@example.test`,
  password:randomBytes(24).toString('base64url')+'aA1!',
  displayName:`V1 ${runId} ${device} ${journey} ${role}`,
 });
 async function register(actor,consumed,approved=false){
  const api=session();
  await api('/auth/signup',{...actor,acceptedTerms:true},'POST',201);
  await visitor('/auth/verify-email',{token:await deliveredToken(actor.email,consumed)});
  await api('/auth/login',{email:actor.email,password:actor.password});
  const me=await api('/me');assert.equal(me.emailVerified,true);
  if(approved){
   await api('/developer/profile',{
    displayName:actor.displayName,headline:'Inventory software developer',bio:'I build accessible inventory systems with stock tracking and order workflows.',
    technology:'React',category:'Inventory',availabilityStatus:'AVAILABLE_NOW',githubUrl:'',linkedinUrl:'',
   },'PUT');
   await api('/developer/profile/submit',{});
   await admin('/admin/profiles/'+me.id+'/approve',{reason:'Disposable V1 prerequisite: review of a synthetic builder profile.'});
  }
  return api;
 }
 async function product(builder,title){
  const created=await builder('/developer/products',{
   title,summary:'Track stock and customer orders for independent shops.',
   description:'A synthetic inventory showcase with stock tracking, product search and accessible order history.',
   projectType:'SAAS',category:'Inventory',technology:'React',visibility:'PUBLIC',contribution:'Designed the schema and implemented the inventory workflow.',
   liveUrl:'https://example.com/getlancer-v1-demo',videoUrl:'',repositoryUrl:'',pricingNote:'',availableForSimilarWork:true,rightsConfirmed:true,
  },'POST',201);
  await builder('/developer/products/'+created.id+'/submit',{},'POST',204);
  await admin('/admin/products/'+created.id+'/approve',{reason:'Disposable V1 prerequisite: synthetic showcase with a public example URL.'});
  const owned=await builder('/developer/products');
  const result=owned.items.find(item=>item.id===created.id);assert.ok(result);
  return {id:result.id,slug:result.slug,title:result.title,builderSlug:result.builderSlug};
 }
 for(const device of ['desktop','phone','tablet']){
  const journeys={};
  // Signup, verification, profile submission and approval are all browser-owned in E2E-01.
  journeys.onboarding={builder:account(device,'onboarding','builder'),mailIds:[]};
  for(const journey of ['publication','slots','conversion','outcome','moderation']){
   // Never reset counters inside a browser journey. Here each group is independent setup.
   resetCiRateBudgets(fixture,1);
   const consumed=new Set(),builderAccount=account(device,journey,'builder');
   const builder=await register(builderAccount,consumed,true);
   const initial={builder:builderAccount,mailIds:[],products:[]};
   if(journey==='slots'){
    for(let index=1;index<=4;index++)initial.products.push(await product(builder,`V1 ${runId} ${device} slot ${index}`));
    const owned=await builder('/developer/products');assert.equal(owned.activeCount,3);
    assert.equal(owned.items.find(item=>item.id===initial.products[3].id).lifecycleStatus,'DRAFT');
   }else if(journey!=='publication'){
    initial.products.push(await product(builder,`V1 ${runId} ${device} ${journey}`));
   }
   if(journey==='conversion')initial.client=account(device,journey,'client'); // Anonymous inquiry is intentional.
   if(journey==='outcome'){
    const clientAccount=account(device,journey,'client');
    await register(clientAccount,consumed);
    initial.client=clientAccount;
    initial.description=`V1 ${runId} ${device}: build an accessible stock approval system with order history.`;
    const inquiry=await visitor('/inquiries',{
     referenceProductId:initial.products[0].id,clientEmail:clientAccount.email,clientName:clientAccount.displayName,
     companyName:'Synthetic V1 shop',requestType:'SIMILAR_BUILD',description:initial.description,
     budgetBand:'USD_3K_10K',timelineBand:'ONE_TO_THREE_MONTHS',website:'',
    },'POST',201);
    await visitor('/inquiries/confirm-email',{token:await deliveredToken(clientAccount.email,consumed)});
    assert.equal((await builder('/developer/inquiries')).items.find(item=>item.id===inquiry.id).status,'INQUIRY_RECEIVED');
    initial.inquiryId=inquiry.id;
   }
   initial.mailIds=[...consumed];journeys[journey]=initial;
  }
  devices[device]=journeys;
 }
 await admin('/auth/logout',{},'POST',204);
 // Preserve all existing connected fixtures and permissions. Existing CI cleanup removes this file.
 writeFileSync(fixturePath,JSON.stringify({...fixture,v1:{version:1,runId,devices}}),{mode:0o600});
 resetCiRateBudgets(fixture,1);
 console.log('Original V1 browser initial states prepared for desktop, phone and tablet; all scenario transitions remain browser-owned.');
}

if(process.argv[1]&&pathToFileURL(resolve(process.argv[1])).href===import.meta.url)await prepareV1Fixtures();
