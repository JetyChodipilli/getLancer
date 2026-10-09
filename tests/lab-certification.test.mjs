import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs, {mkdtempSync, writeFileSync, rmSync, symlinkSync, mkdirSync, renameSync} from 'node:fs';
import {syncBuiltinESMExports} from 'node:module';
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

test('nested regular evidence is admitted and linked parent directories are refused',t=>{
  const f=fixture(t), directory=join(f.root,'reports');mkdirSync(directory);
  renameSync(join(f.root,'isolation.txt'),join(directory,'isolation.txt'));
  f.inventory.records[0].evidencePath='reports/isolation.txt';f.save();verifyBundle(f.args);
  renameSync(directory,join(f.root,'original-reports'));symlinkSync(join(f.root,'original-reports'),directory);
  assert.throws(()=>verifyBundle(f.args),/Linked/);
});

function afterOpened(t, path, change) {
  const original=fs.fstatSync, descriptor=fs.openSync(path,fs.constants.O_RDONLY|fs.constants.O_NOFOLLOW);
  let target;try{target=original(descriptor);}finally{fs.closeSync(descriptor);}
  let changed=false;
  t.mock.method(fs,'fstatSync',descriptor=>{
    const info=original(descriptor);
    if (!changed && info.dev===target.dev && info.ino===target.ino) {changed=true;change();}
    return info;
  });
  syncBuiltinESMExports();
  t.after(()=>{t.mock.restoreAll();syncBuiltinESMExports();});
  return ()=>changed;
}

test('a path replaced after opening cannot change the evidence bytes being verified',t=>{
  const f=fixture(t), path=join(f.root,'isolation.txt');
  const changed=afterOpened(t,path,()=>{
    renameSync(path,join(f.root,'original-isolation.txt'));
    writeFileSync(join(f.root,'replacement.txt'),'Unreviewed replacement report');
    symlinkSync(join(f.root,'replacement.txt'),path);
  });
  assert.equal(verifyBundle(f.args).controls.length,7);assert.ok(changed());
  assert.throws(()=>verifyBundle(f.args),/Linked/);
});

test('a renamed parent remains the directory used for evidence traversal',t=>{
  const f=fixture(t), directory=join(f.root,'reports'), replacement=join(f.root,'unreviewed');
  mkdirSync(directory);mkdirSync(replacement);
  renameSync(join(f.root,'isolation.txt'),join(directory,'isolation.txt'));
  writeFileSync(join(replacement,'isolation.txt'),'Unreviewed parent replacement');
  f.inventory.records[0].evidencePath='reports/isolation.txt';f.save();
  const original=fs.openSync;let changed=false;
  t.mock.method(fs,'openSync',(path,...args)=>{
    const descriptor=original(path,...args);
    if (!changed && typeof path==='string' && path.endsWith('/reports')) {
      changed=true;renameSync(directory,join(f.root,'original-reports'));symlinkSync(replacement,directory);
    }
    return descriptor;
  });
  syncBuiltinESMExports();t.after(()=>{t.mock.restoreAll();syncBuiltinESMExports();});
  assert.equal(verifyBundle(f.args).controls.length,7);assert.ok(changed);
  assert.throws(()=>verifyBundle(f.args),/Linked/);
});

test('evidence that grows after its metadata check is read within the hard limit',t=>{
  const f=fixture(t), path=join(f.root,'isolation.txt');
  const changed=afterOpened(t,path,()=>writeFileSync(path,Buffer.alloc(1024*1024+2)));
  assert.throws(()=>verifyBundle(f.args),/size limit/);assert.ok(changed());
});

test('empty and oversized regular evidence cannot satisfy admission',t=>{
  const f=fixture(t), path=join(f.root,'isolation.txt');
  writeFileSync(path,'');assert.throws(()=>verifyBundle(f.args),/bounded regular/);
  writeFileSync(path,Buffer.alloc(1024*1024+1));assert.throws(()=>verifyBundle(f.args),/bounded regular/);
});
