import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync,writeFileSync,readFileSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {generateKeyPairSync,createHash,sign,randomUUID} from 'node:crypto';
import {verifyBundle,verifyManifestBundle} from '../ops/labs/certify.mjs';
const hash=x=>createHash('sha256').update(x).digest('hex');
function fixture(t){
 const root=mkdtempSync(join(tmpdir(),'native-cert-'));t.after(()=>rmSync(root,{recursive:true,force:true}));
 const now=Date.now(),epoch=randomUUID(),{privateKey,publicKey}=generateKeyPairSync('ed25519');
 const records=['isolation','network','cleanup','restore','licence','cost','build'].map(control=>{const bytes=Buffer.from('Synthetic test control '+control);writeFileSync(join(root,control+'.txt'),bytes);return {control,evidencePath:control+'.txt',artifactSha256:hash(bytes),recordedAt:new Date(now-2000).toISOString(),result:'PASS'};});
 const inventory={version:1,mode:'PRODUCTION',provider:'unit-fixture',operatorEpoch:epoch,records}, inventoryPath=join(root,'inventory.json');writeFileSync(inventoryPath,JSON.stringify(inventory));
 const envelope=x=>{const bytes=Buffer.from(JSON.stringify(x));return JSON.stringify({payload:bytes.toString('base64'),signature:sign(null,bytes,privateKey).toString('base64')});};
 const approvedUntil=new Date(now+86400000).toISOString(),evidenceSha256=hash(readFileSync(inventoryPath)),admissionPath=join(root,'admission.json');
 writeFileSync(admissionPath,envelope({provider:'unit-fixture',isolation:'KVM',operatorEpoch:epoch,approvedUntil,evidenceSha256,licenceReviewed:true,budgetApproved:true,networkVerified:true,cleanupVerified:true,restoreVerified:true}));
 const manifest={id:'native-java',componentId:randomUUID(),revision:1,sourceSha256:'a'.repeat(64),archiveSha256:'b'.repeat(64),buildSourceSha256:'b'.repeat(64),manifestSha256:'c'.repeat(64),imageDigest:'sha256:'+'d'.repeat(64),protocolVersion:'getlancer-lab-v1',approvedUntil,evidenceSha256,language:'Java',framework:'Standard library',resourceClass:'GENERAL',adapter:{runtime:'jdk',version:'17.0.12',state:'ACTIVE'},certification:{frameworkVersion:'17.0.12',maintainer:'maintainer@example.test',reviewedAt:new Date(now-1000).toISOString(),licenceReviewed:true,reproductionVerified:true,resourcesVerified:true,isolationVerified:true,reproductionSha256:records.find(x=>x.control==='build').artifactSha256}};
 const manifestPath=join(root,'manifest.json'),save=()=>writeFileSync(manifestPath,envelope(manifest));save();
 return {manifest,save,args:{admissionPath,inventoryPath,manifestPath,publicKeyBytes:Buffer.from(publicKey.export({type:'spki',format:'pem'})),operatorEpoch:epoch,now}};
}
test('native certification binds the reviewed combination and all reproduction attestations',t=>{
 const f=fixture(t);assert.equal(verifyManifestBundle(f.args).runtimeVersion,'17.0.12');
 for(const key of ['licenceReviewed','reproductionVerified','resourcesVerified','isolationVerified']){f.manifest.certification[key]=false;f.save();assert.throws(()=>verifyManifestBundle(f.args),/attestation/);f.manifest.certification[key]=true;}
 const cases=[m=>m.adapter.state='RETIRED',m=>m.adapter.version='18.0.1',m=>m.framework='Unreviewed',m=>m.certification.frameworkVersion='17.0.1',m=>m.certification.reproductionSha256='f'.repeat(64),m=>m.certification.reviewedAt=new Date(f.args.now+1000).toISOString(),m=>m.certification.maintainer='  x',m=>m.evidenceSha256='e'.repeat(64),m=>m.buildSourceSha256='f'.repeat(64),m=>m.revision=0];
 const original=structuredClone(f.manifest);for(const change of cases){Object.assign(f.manifest,structuredClone(original));change(f.manifest);f.save();assert.throws(()=>verifyManifestBundle(f.args));}
});
test('raw pins verify the same captured bytes including harmless serialization changes',t=>{
 const f=fixture(t),pinned={...f.args,expectedAdmissionSha256:hash(readFileSync(f.args.admissionPath)),expectedInventorySha256:hash(readFileSync(f.args.inventoryPath)),expectedManifestSha256:hash(readFileSync(f.args.manifestPath))};
 assert.equal(verifyManifestBundle(pinned).manifestId,'native-java');
 for(const field of ['expectedAdmissionSha256','expectedInventorySha256','expectedManifestSha256'])assert.throws(()=>verifyManifestBundle({...pinned,[field]:'0'.repeat(64)}),/expected digest/);
 writeFileSync(f.args.manifestPath,readFileSync(f.args.manifestPath,'utf8')+'\n');assert.equal(verifyBundle(pinned).provider,'unit-fixture');assert.throws(()=>verifyManifestBundle(pinned),/expected digest/);
});
