import test from 'node:test';
import assert from 'node:assert/strict';
import {createHostingPreview,hostedDemoUrl,hostingAttention,hostingCanPublish,hostingLimits,hostingPage,safeHostedUrl,validateHostingUpload} from '../lib/hosting.ts';

const now=new Date('2026-10-03T05:00:00Z');
const decision={action:'APPROVE',reason:'Inspected the bundled sample manifest and recorded consent.',packageReviewed:true,rightsReviewed:true};
function approved(){const p=createHostingPreview(now),id=p.list()[0].id;p.submit(id,true);p.setActor('OPERATOR');p.review(id,decision);p.setActor('BUILDER');return {p,id};}

test('bundled exercise makes no network requests, deep copies records and resets actor and complete state',()=>{
  const fetch=globalThis.fetch;globalThis.fetch=()=>{throw Error('Unexpected network request');};
  try{const {p,id}=approved();p.publish(id);const copy=p.detail(id);copy.files[0].path='private.env';copy.audit[0].detail='Mutated';copy.status='REJECTED';assert.equal(p.detail(id).files[0].path,'index.html');assert.equal(p.detail(id).status,'APPROVED');assert.notEqual(p.detail(id).audit[0].detail,'Mutated');assert.equal(p.detail(id).url,null);p.withdraw(id);p.setActor('OPERATOR');p.reset();assert.equal(p.detail(id).status,'DRAFT');assert.equal(p.detail(id).deploymentId,null);assert.equal(p.detail(id).rightsConsentAt,null);assert.equal(p.detail(id).audit.length,0);p.submit(id,true);}finally{globalThis.fetch=fetch;}
});
test('owner rights, review checks, actor and sequence are separate gates from publication',()=>{
  const p=createHostingPreview(now),id=p.list()[0].id;assert.throws(()=>p.publish(id));assert.throws(()=>p.submit(id,false));assert.throws(()=>p.review(id,decision));p.submit(id,true);assert.throws(()=>p.submit(id,true));assert.throws(()=>p.publish(id));p.setActor('OPERATOR');for(const body of [{packageReviewed:false},{rightsReviewed:false},{reason:'x'},{reason:'x'.repeat(2001)},{action:'OVERRIDE'}])assert.throws(()=>p.review(id,{...decision,...body}));p.review(id,decision);assert.equal(p.detail(id).deploymentState,'NOT_CREATED');assert.equal(hostingCanPublish(p.detail(id)),true);assert.throws(()=>p.publish(id));p.setActor('BUILDER');p.publish(id);assert.throws(()=>p.publish(id));assert.equal(p.detail(id).deploymentState,'READY');
});
test('unknown publication and delayed withdrawal recover the same fixed identity without resurrection',()=>{
  const {p,id}=approved();p.publish(id,true);const frozen=p.detail(id);assert.equal(frozen.deploymentState,'UNKNOWN');assert.equal(hostingAttention(frozen),true);assert.throws(()=>p.publish(id));p.reconcile(id);assert.equal(p.detail(id).deploymentId,frozen.deploymentId);assert.equal(p.detail(id).expiresAt,frozen.expiresAt);assert.equal(p.detail(id).expiresAt,'2026-10-10T05:00:00.000Z');p.withdraw(id,true);assert.equal(p.detail(id).desiredState,'WITHDRAWN');assert.equal(p.detail(id).deploymentState,'DELETE_PENDING');assert.equal(p.detail(id).url,null);p.reconcile(id);assert.equal(p.detail(id).deploymentState,'DELETED');assert.equal(p.detail(id).deploymentId,frozen.deploymentId);assert.throws(()=>p.publish(id));assert.throws(()=>p.reconcile(id));p.withdraw(id);assert.equal(p.detail(id).deploymentState,'DELETED');
});
test('changes requested can resubmit with fresh consent; suspension and withdrawal deny publication',()=>{
  const p=createHostingPreview(now),id=p.list()[0].id;p.submit(id,true);p.setActor('OPERATOR');p.review(id,{...decision,action:'CHANGES_REQUESTED'});p.setActor('BUILDER');assert.throws(()=>p.submit(id,false));p.submit(id,true);p.setActor('OPERATOR');p.review(id,decision);p.review(id,{...decision,action:'SUSPEND'});p.setActor('BUILDER');assert.throws(()=>p.publish(id));p.setActor('OPERATOR');p.review(id,decision);p.setActor('BUILDER');assert.equal(p.detail(id).status,'APPROVED');p.withdraw(id);assert.equal(p.detail(id).desiredState,'WITHDRAWN');const second=p.create('Another frontend','2.0.0',true);p.withdraw(second.id);assert.throws(()=>p.publish(second.id));assert.throws(()=>p.submit(second.id,true));
});
test('upload hints reject missing/empty/oversized/non-ZIP packages and invalid metadata without claiming server validation',()=>{
  const valid={name:'frontend.zip',size:hostingLimits.maxCompressedBytes};validateHostingUpload(valid,'Static frontend','1.0.0+build',true);
  for(const file of [null,{name:'frontend.tar',size:2},{name:'frontend.zip',size:0},{name:'frontend.zip',size:hostingLimits.maxCompressedBytes+1}])assert.throws(()=>validateHostingUpload(file,'Frontend','1.0.0',true));
  for(const [title,version,consent] of [['x','1',true],['x'.repeat(101),'1',true],['Frontend','..1',true],['Frontend','1/2',true],['Frontend','a'.repeat(41),true],['Frontend','1',false]])assert.throws(()=>validateHostingUpload(valid,title,version,consent));
});
test('sample record and unresolved deployment bounds are enforced',()=>{
  const {p,id}=approved();assert.throws(()=>p.create('Duplicate immutable version','1.0.0',true));p.publish(id,true);
  for(let i=0;i<2;i++){const created=p.create('Additional frontend '+i,'1.1.'+i,true);p.submit(created.id,true);p.setActor('OPERATOR');p.review(created.id,decision);p.setActor('BUILDER');p.publish(created.id,true);}
  const fourth=p.create('Fourth frontend','4',true);p.submit(fourth.id,true);p.setActor('OPERATOR');p.review(fourth.id,decision);p.setActor('BUILDER');assert.throws(()=>p.publish(fourth.id));p.withdraw(id);p.publish(fourth.id);
  while(p.list().length<10)p.create('Retained frontend','retained-'+p.list().length,true);assert.throws(()=>p.create('Over retained limit','1',true));const page=hostingPage(p.list(),1,3);assert.equal(page.items.length,3);assert.equal(page.totalItems,10);assert.equal(page.totalPages,4);assert.equal(page.hasMore,true);page.items[0].files[0].path='mutated';assert.notEqual(p.list()[3].files[0].path,'mutated');
});
test('open links require eligible READY, unexpired published state and safe isolated URL syntax',()=>{
  const base={status:'APPROVED',deploymentState:'READY',desiredState:'PUBLISHED',expiresAt:'2026-10-10T00:00:00Z',url:'https://demo.example.test/'};assert.equal(hostedDemoUrl(base,now.getTime()),base.url);
  for(const change of [{status:'PENDING'},{status:'SUSPENDED'},{deploymentState:'UNKNOWN'},{deploymentState:'DELETE_PENDING'},{desiredState:'WITHDRAWN'},{expiresAt:'2026-10-02T00:00:00Z'},{expiresAt:'invalid'},{url:'javascript:alert(1)'},{url:'https://user:pass@demo.example.test/'},{url:'http://demo.example.test/'}])assert.equal(hostedDemoUrl({...base,...change},now.getTime()),undefined);
  assert.equal(safeHostedUrl('http://11111111-1111-4111-8111-111111111111.demo.localhost:8090/'),'http://11111111-1111-4111-8111-111111111111.demo.localhost:8090/');assert.equal(safeHostedUrl('http://demo.localhost.evil.test/'),undefined);assert.equal(safeHostedUrl('http://------------------------------------.demo.localhost/'),undefined);assert.equal(safeHostedUrl('https://demo.example.test/\n'),undefined);
});
