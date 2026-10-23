import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {restoreFixture,seedRestoreSql,restoreSnapshotSql,expectedRestoreSnapshot,verifyNewDomainRestore} from '../scripts/acceptance-restore.mjs';

test('new-domain restore retains original ZIP bytes and exact package, buyer, licence and shutdown identity',()=>{
 const fixture=restoreFixture(),before=expectedRestoreSnapshot(fixture),after=structuredClone(before);
 assert.equal(fixture.archive.subarray(0,4).toString('hex'),'504b0304');
 assert.equal(createHash('sha256').update(fixture.archive).digest('hex'),before.free.packageSha256);
 const result=verifyNewDomainRestore(before,after,fixture);
 assert.equal(result.archiveSha256,fixture.source.archiveSha256);assert.equal(result.pendingStop,fixture.ids.stop);
 assert.equal(after.lab.outbox[0].generation,1);assert.equal(after.lab.generation,2);
 assert.equal(after.purchase.mode,'test');assert.equal(after.lab.manifestRevoked,true);
});
test('binding corruption fails despite unchanged row counts and otherwise retained records',()=>{
 const fixture=restoreFixture(),before=expectedRestoreSnapshot(fixture);
 const faults={
  archive:s=>s.component.source.archiveBase64=Buffer.from('replacement source').toString('base64'),
  sourceHash:s=>s.component.sourceSha256='0'.repeat(64),license:s=>s.paid.licenseTerms='Replacement licence',
  freePackage:s=>s.free.packageId=fixture.ids.version,freeHash:s=>s.free.packageSha256='0'.repeat(64),
  paidHash:s=>s.paid.sourceHash='0'.repeat(64),version:s=>s.paid.versionId=fixture.ids.freePackage,
  purchaseRelease:s=>s.purchase.releaseId=fixture.ids.freeRelease,
  purchaseSnapshot:s=>s.purchase.snapshot.package.licenseTerms='Replacement agreement',
  payment:s=>s.purchase.paymentId='pay_different',entitlement:s=>s.purchase.entitlementRevoked=true,
  outbox:s=>s.lab.outbox[1].id=fixture.ids.start,delivery:s=>s.lab.outbox[1].delivered=true,
  generation:s=>s.lab.outbox[1].generation=1,epoch:s=>s.lab.operatorEpoch=fixture.ids.buyer,
  runStatus:s=>s.lab.status='RUNNING',preview:s=>s.component.previewState='READY',
  paused:s=>s.paused=false,publication:s=>s.publicEducation=1,missing:s=>s.free=null,
 };
 for(const [name,mutate] of Object.entries(faults)){const after=structuredClone(before);mutate(after);assert.throws(()=>verifyNewDomainRestore(before,after,fixture),{name:'AssertionError'},name);}
});
test('a corrupt or incomplete baseline cannot make an equally corrupt restore pass',()=>{
 const fixture=restoreFixture(),before=expectedRestoreSnapshot(fixture);before.purchase.packageSha256='0'.repeat(64);
 assert.throws(()=>verifyNewDomainRestore(before,structuredClone(before),fixture),/Require complete canonical/);
});
test('disposable SQL seeds distinct new-domain objects without overwrite or valid runtime authority',()=>{
 const fixture=restoreFixture(),sql=seedRestoreSql(fixture);
 for(const table of ['component_releases','component_previews','education_free_packages','education_releases','source_versions','template_purchases','lab_manifests','lab_runs','lab_outbox'])assert.ok(sql.includes('INSERT INTO '+table),table);
 assert.ok(!sql.includes('ON CONFLICT'));assert.ok(sql.includes('synthetic-not-valid-admission'));
 assert.ok(restoreSnapshotSql().includes("'schema',1"));
});
