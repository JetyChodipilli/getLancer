import test from 'node:test';
import assert from 'node:assert/strict';
import {createTeamDemo,normalizeTeamAvailability,teamAvailabilityOptions} from '../lib/team-demo.ts';

const base='/teams/team-studio';
const send=(d,path,body,method='POST')=>d.request(path,{method,body:JSON.stringify(body)});
const profile={name:'Studio',summary:'A team that builds useful customer software.',projectRange:'5k–10k'};

test('known availability aliases are canonical and unknown free text survives profile writes',async()=>{
 const d=createTeamDemo();
 for(const label of ['Open to projects','Available now','AVAILABLE_NOW','  aVailable NOW  ']){
  assert.equal(normalizeTeamAvailability(label),'AVAILABLE_NOW');
  assert.equal((await send(d,base,{...profile,availability:label},'PUT')).availability,'AVAILABLE_NOW');
  assert.equal((await send(d,'/teams',{...profile,availability:label})).availability,'AVAILABLE_NOW');
 }
 for(const label of ['Ask after our R&D milestone','Available after summer','Available-ish']){
  assert.equal(normalizeTeamAvailability(label),label);
  assert.equal((await send(d,base,{...profile,availability:label},'PUT')).availability,label);
  assert.equal((await d.request(base)).team.availability,label);
 }
 assert.equal(normalizeTeamAvailability('Unavailable'),'NOT_ACCEPTING');
 assert.equal(normalizeTeamAvailability('Limited'),'LIMITED');
 for(const status of teamAvailabilityOptions)assert.equal(normalizeTeamAvailability(status),status);
});

test('private consent flags describe the actual actor and never appear in public product DTOs',async()=>{
 const d=createTeamDemo();d.setActor('member');
 await send(d,base+'/projects',{productId:'product-member'});
 const shared=async()=>((await d.request(base+'/workspace?collection=projects&size=1&page=1')).projects[0]);
 // The original owner's contribution is on page 1 of this private collection.
 const mine=(await d.request(base+'/workspace?collection=projects')).projects.find(p=>p.id==='product-member');
 assert.equal(mine.canWithdraw,true);assert.equal(mine.ownerId,undefined);assert.equal(mine.consentedBy,undefined);
 assert.equal((await shared()).canWithdraw,false);
 for(const product of (await d.request(base)).projects){assert.equal(product.canWithdraw,undefined);assert.equal(product.ownerId,undefined)}
 d.setActor('recruiter');
 assert.equal((await d.request(base+'/workspace')).projects.find(p=>p.id==='product-member').canWithdraw,false);
 await assert.rejects(d.request(base+'/projects/product-member',{method:'DELETE'}),e=>e.status===403);
 d.setActor('client');
 await assert.rejects(d.request(base+'/workspace?collection=projects&page=1'),e=>e.status===403);
 await assert.rejects(d.request(base+'/projects/product-member',{method:'DELETE'}),e=>e.status===403);
 d.setActor('owner');assert.ok((await d.request(base+'/workspace')).projects.every(p=>p.canWithdraw===true));
 d.setActor('member');await d.request(base+'/projects/product-member',{method:'DELETE'});
 assert.ok(!(await d.request(base+'/workspace')).projects.some(p=>p.id==='product-member'));
});

test('the preview owner product endpoint is bounded, filtered and authorized on every request',async()=>{
 const d=createTeamDemo();
 const first=await d.request('/developer/products?lifecycleStatus=ACTIVE&size=1');
 assert.equal(first.items.length,1);assert.equal(first.page,0);assert.equal(first.size,1);assert.equal(first.hasMore,false);
 assert.equal((await d.request('/developer/products?lifecycleStatus=ACTIVE&size=1&page=1')).items.length,0);
 assert.deepEqual((await d.request('/developer/products?lifecycleStatus=ARCHIVED')).items,[]);
 assert.deepEqual((await d.request('/developer/products?approvalStatus=DRAFT')).items,[]);
 await assert.rejects(d.request('/developer/products?lifecycleStatus=UNKNOWN'),e=>e.status===400);
 d.setActor('client');await assert.rejects(d.request('/developer/products?page=1'),e=>e.status===403);
});
