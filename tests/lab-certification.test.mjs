import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync, writeFileSync, rmSync, symlinkSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {generateKeyPairSync, createHash, sign, randomUUID} from 'node:crypto';
import {verifyBundle, verifiedPayload} from '../ops/labs/certify.mjs';

const now = Date.parse('2026-10-09T06:00:00Z');
const hash = value => createHash('sha256').update(value).digest('hex');
function fixture(t) {
  const root = mkdtempSync(join(tmpdir(), 'getlancer-lab-certification-'));
  t.after(()=>rmSync(root,{recursive:true,force:true}));
  const {privateKey, publicKey} = generateKeyPairSync('ed25519'), operatorEpoch = randomUUID();
  const publicKeyPath = join(root,'operator-public.pem');writeFileSync(publicKeyPath,publicKey.export({type:'spki',format:'pem'}));
  const records = ['isolation','network','cleanup','restore','licence','cost','build'].map(control=>{
    const evidencePath = control+'.txt', text = 'Unit-test control fixture: '+control;
    writeFileSync(join(root,evidencePath),text);
    return {control, evidencePath, artifactSha256:hash(text), recordedAt:new Date(now-1000).toISOString(), result:'PASS'};
  });
  const inventory = {version:1,mode:'PRODUCTION',operatorEpoch,provider:'test-provider',records};
  const inventoryPath = join(root,'inventory.json'), admissionPath=join(root,'admission.json');
  const admission = {provider:'test-provider',isolation:'KVM',licenceReviewed:true,budgetApproved:true,networkVerified:true,cleanupVerified:true,restoreVerified:true,approvedUntil:new Date(now+86400000).toISOString(),operatorEpoch};
  function save() {
    const bytes = Buffer.from(JSON.stringify(inventory));writeFileSync(inventoryPath,bytes);admission.evidenceSha256=hash(bytes);
    const payload=Buffer.from(JSON.stringify(admission));writeFileSync(admissionPath,JSON.stringify({payload:payload.toString('base64'),signature:sign(null,payload,privateKey).toString('base64')}));
  }
  save();return {root,inventory,admission,save,privateKey,args:{admissionPath,inventoryPath,publicKeyPath,operatorEpoch,now}};
}

test('offline verifier accepts a signed complete fixture and rejects independently altered bytes',t=>{
  const f=fixture(t);assert.equal(verifyBundle(f.args).controls.length,7);
  writeFileSync(join(f.root,'isolation.txt'),'Changed host report');assert.throws(()=>verifyBundle(f.args),/artifact changed/);
});
test('signed booleans without each independent evidence control cannot pass',t=>{
  const f=fixture(t);f.inventory.records.pop();f.save();assert.throws(()=>verifyBundle(f.args),/recorded once/);
});
test('fixture/replay mode, expired approval and pre-restore epoch are refused',t=>{
  const f=fixture(t);f.inventory.mode='REPLAY';f.save();assert.throws(()=>verifyBundle(f.args),/production/);
  f.inventory.mode='PRODUCTION';f.admission.approvedUntil=new Date(now-1).toISOString();f.save();assert.throws(()=>verifyBundle(f.args),/current/);
  f.admission.approvedUntil=new Date(now+1000).toISOString();f.save();assert.throws(()=>verifyBundle({...f.args,operatorEpoch:randomUUID()}),/epoch/);
});
test('forged admission, unauthenticated extra fields and non-Ed25519 keys fail',t=>{
  const f=fixture(t);assert.equal(verifyBundle(f.args).provider,'test-provider');
  const payload=Buffer.from(JSON.stringify({...f.admission,provider:'other-provider'}));
  const other=generateKeyPairSync('ed25519');const bytes=Buffer.from(JSON.stringify({payload:payload.toString('base64'),signature:sign(null,payload,other.privateKey).toString('base64')}));
  assert.throws(()=>verifiedPayload(bytes,f.privateKey.export({type:'pkcs8',format:'pem'})),/signature/);
  const extra=Buffer.from(JSON.stringify({payload:payload.toString('base64'),signature:sign(null,payload,f.privateKey).toString('base64'),approved:true}));
  assert.throws(()=>verifiedPayload(extra,f.privateKey.export({type:'pkcs8',format:'pem'})),/fields/);
  const rsa=generateKeyPairSync('rsa',{modulusLength:2048});
  assert.throws(()=>verifiedPayload(bytes,rsa.publicKey.export({type:'spki',format:'pem'})),/signature/);
});
test('bundle traversal and linked evidence cannot satisfy admission',t=>{
  const f=fixture(t);f.inventory.records[0].evidencePath='../isolation.txt';f.save();assert.throws(()=>verifyBundle(f.args),/inside/);
  f.inventory.records[0].evidencePath='linked.txt';symlinkSync(join(f.root,'isolation.txt'),join(f.root,'linked.txt'));f.save();assert.throws(()=>verifyBundle(f.args),/Linked/);
});
test('duplicate, undated and unapproved control evidence fails with a positive control',t=>{
  const f=fixture(t);verifyBundle(f.args);f.inventory.records[0].control='network';f.save();assert.throws(()=>verifyBundle(f.args),/once/);
  f.inventory.records[0].control='isolation';f.inventory.records[0].recordedAt='invalid';f.save();assert.throws(()=>verifyBundle(f.args),/dated/);
  f.inventory.records[0].recordedAt=new Date(now-1000).toISOString();f.admission.licenceReviewed=false;f.save();assert.throws(()=>verifyBundle(f.args),/approval/);
});
