import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs, {mkdtempSync, writeFileSync, rmSync, symlinkSync, mkdirSync, renameSync} from 'node:fs';
import {syncBuiltinESMExports} from 'node:module';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {generateKeyPairSync, createHash, sign, randomUUID} from 'node:crypto';
import {readEvidence, verifyBundle, verifiedPayload} from '../ops/labs/certify.mjs';

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
test('captured trust key remains authoritative when its file is replaced between envelope checks',t=>{
  const f=fixture(t), captured=readEvidence(f.args.publicKeyPath);
  const replacement=generateKeyPairSync('ed25519');
  writeFileSync(f.args.publicKeyPath,replacement.publicKey.export({type:'spki',format:'pem'}));
  assert.throws(()=>verifyBundle(f.args),/signature/);
  assert.equal(verifyBundle({...f.args,publicKeyBytes:captured}).controls.length,7);
  const payload=Buffer.from(JSON.stringify({provider:f.admission.provider,mode:'HOSTED_EXECUTION'}));
  const envelope=key=>Buffer.from(JSON.stringify({payload:payload.toString('base64'),signature:sign(null,payload,key).toString('base64')}));
  assert.equal(verifiedPayload(envelope(f.privateKey),captured).provider,f.admission.provider);
  assert.throws(()=>verifiedPayload(envelope(replacement.privateKey),captured),/signature/);
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

test('signed admission approval uses backend-compatible ISO instants, including fractional precision',t=>{
 const f=fixture(t);f.admission.approvedUntil='2026-10-10T06:00:00.123456789Z';f.save();assert.equal(verifyBundle(f.args).provider,'test-provider');
 f.admission.approvedUntil='2026-10-10T07:00:00+01:00';f.save();assert.equal(verifyBundle(f.args).provider,'test-provider');
 for(const value of ['Sat, 10 Oct 2026 06:00:00 GMT','2026-10-10T06:00:00+19:00','2026-10-32T06:00:00Z']){f.admission.approvedUntil=value;f.save();assert.throws(()=>verifyBundle(f.args),/ISO instant/);}
});

test('approval horizon preserves nanosecond precision at its exact boundary',t=>{
 const f=fixture(t),limit=new Date(f.args.now+31*86400000).toISOString();
 f.admission.approvedUntil=limit;f.save();assert.equal(verifyBundle(f.args).provider,'test-provider');
 f.admission.approvedUntil=limit.replace('Z','000001Z');f.save();assert.throws(()=>verifyBundle(f.args),/ISO instant/);
});

test('operator-signed admission rejects fields outside the backend Evidence record',t=>{
 const f=fixture(t);assert.equal(verifyBundle(f.args).provider,'test-provider');
 for(const field of ['unexpectedField','resourceClass']){
  f.admission[field]='signed additional metadata';f.save();
  assert.throws(()=>verifyBundle(f.args),/Unexpected admission fields/);
  delete f.admission[field];f.save();assert.equal(verifyBundle(f.args).provider,'test-provider');
 }
});

test('signed admission providers obey Java isBlank semantics with matching inventory',t=>{
 const f=fixture(t);
 const check=(provider,blank)=>{
  f.admission.provider=provider;f.inventory.provider=provider;f.save();
  if(blank)assert.throws(()=>verifyBundle(f.args),/named independently verified KVM provider/);
  else assert.equal(verifyBundle(f.args).provider,provider);
 };
 // Character.isWhitespace includes these controls and Unicode spaces, including U+001C–001F.
 const javaWhitespace=[...Array.from({length:5},(_,i)=>0x0009+i),...Array.from({length:5},(_,i)=>0x001c+i),0x1680,...Array.from({length:7},(_,i)=>0x2000+i),0x2008,0x2009,0x200a,0x2028,0x2029,0x205f,0x3000];
 for(const point of javaWhitespace)check(String.fromCodePoint(point).repeat(2),true);
 check('\t\u001c\u1680\u2000\u2028\u3000',true);
 // Java deliberately excludes non-breaking spaces, BOM and NEXT LINE from isWhitespace.
 for(const point of [0x00a0,0x2007,0x202f,0xfeff,0x0085])check(String.fromCodePoint(point).repeat(2),false);
 check(' \tverified-provider\u3000',false);
 check('test-provider',false);
});

test('signed admission rejects duplicate decoded record keys without scanning string contents',t=>{
 const f=fixture(t),original=JSON.stringify(f.admission);
 const writePayload=text=>{const payload=Buffer.from(text);writeFileSync(f.args.admissionPath,JSON.stringify({payload:payload.toString('base64'),signature:sign(null,payload,f.privateKey).toString('base64')}));};
 for(const replacement of ['"provider":"  ","provider":"test-provider"','"\\u0070rovider":"hidden-provider","provider":"test-provider"']){
  writePayload(original.replace('"provider":"test-provider"',replacement));assert.throws(()=>verifyBundle(f.args),/Duplicate signed record field/);
 }
 writePayload(original.replace('"licenceReviewed":true','"licenceReviewed":false,"licenceReviewed":true'));assert.throws(()=>verifyBundle(f.args),/Duplicate signed record field/);
 const provider='Provider "1e0": {"provider":128.0} \\ [true,null]';f.admission.provider=provider;f.inventory.provider=provider;f.save();assert.equal(verifyBundle(f.args).provider,provider);
 // Benchmark and other generic signed reports retain their original numeric parsing contract.
 const payload=Buffer.from('{"value":1.25,"nested":[{"number":1e2}]}'),envelope=Buffer.from(JSON.stringify({payload:payload.toString('base64'),signature:sign(null,payload,f.privateKey).toString('base64')}));
 assert.deepEqual(verifiedPayload(envelope,readEvidence(f.args.publicKeyPath)),{value:1.25,nested:[{number:100}]});
});

test('signed host envelopes and decoded payloads obey the exact backend byte limits',t=>{
 const f=fixture(t),path=f.args.admissionPath,original=fs.readFileSync(path);
 // Envelope whitespace changes no signed payload facts, but still counts toward the file bound.
 writeFileSync(path,Buffer.concat([original,Buffer.alloc(65536-original.length,32)]));assert.equal(verifyBundle(f.args).provider,'test-provider');
 writeFileSync(path,Buffer.concat([original,Buffer.alloc(65537-original.length,32)]));assert.throws(()=>verifyBundle(f.args),/Admission envelope exceeded/);
 const encoded=Buffer.from(JSON.stringify(f.admission));
 const writePayload=length=>{const payload=Buffer.concat([encoded,Buffer.alloc(length-encoded.length,32)]);assert.deepEqual(JSON.parse(payload),f.admission);writeFileSync(path,JSON.stringify({payload:payload.toString('base64'),signature:sign(null,payload,f.privateKey).toString('base64')}));};
 writePayload(32768);assert.equal(verifyBundle(f.args).provider,'test-provider');
 writePayload(32769);assert.ok(fs.statSync(path).size<65536);assert.throws(()=>verifyBundle(f.args),/Signed payload exceeded/);
});
