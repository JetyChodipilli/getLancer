import test from 'node:test';
import assert from 'node:assert/strict';
import {createHostingPreview,hostedDemoUrl,hostingCanPublish} from '../lib/hosting.ts';

test('hosting interrupted publication and withdrawal preserve immutable identity and never reopen a tombstone',()=>{
 const preview=createHostingPreview(new Date('2026-10-26T00:00:00Z')),id=preview.list()[0].id;
 preview.submit(id,true);preview.setActor('OPERATOR');preview.review(id,{action:'APPROVE',reason:'Inspected the exact static archive and recorded ownership consent.',packageReviewed:true,rightsReviewed:true});preview.setActor('BUILDER');preview.publish(id,true);
 const fixed=preview.detail(id);assert.equal(fixed.deploymentState,'UNKNOWN');assert.equal(hostingCanPublish(fixed),false);assert.equal(hostedDemoUrl(fixed),undefined);
 preview.withdraw(id,true);preview.reconcile(id);const withdrawn=preview.detail(id);assert.equal(withdrawn.deploymentId,fixed.deploymentId);assert.equal(withdrawn.expiresAt,fixed.expiresAt);assert.equal(withdrawn.archiveSha256,fixed.archiveSha256);assert.equal(withdrawn.deploymentState,'DELETED');assert.throws(()=>preview.publish(id));assert.equal(hostedDemoUrl(withdrawn),undefined);
});
