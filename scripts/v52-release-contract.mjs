import assert from 'node:assert/strict';
import {readFileSync,existsSync} from 'node:fs';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';

// Structural traceability is supplementary. Passing this cannot certify a deployment.
export function verifyReleaseContract(value,root=resolve('.')) {
 assert.equal(value.schema,1,'Unsupported release contract.');
 assert.equal(value.requirements.length,68,'All 46 FRs and 22 NFRs must have owners and evidence paths.');
 const required=new Set([...Array.from({length:46},(_,n)=>`FR-${String(n+1).padStart(3,'0')}`),...Array.from({length:22},(_,n)=>`NFR-${String(n+1).padStart(3,'0')}`)]);
 for(const item of value.requirements){
  assert.ok(required.delete(item.id),'Duplicate or unknown requirement.');
  assert.equal(item.acceptanceId,'AC-'+item.id); assert.equal(item.verificationId,'TC-'+item.id);
  assert.equal(item.priority,'Must'); assert.ok(item.owner?.length>=2&&item.title?.length>=2);
  for(const key of ['primaryVerification','supplementaryVerification']){
   assert.ok(typeof item[key]==='string'&&!item[key].includes('..')&&!item[key].startsWith('/'),'Unsafe evidence path.');
   assert.ok(existsSync(resolve(root,item[key])),`Missing evidence artifact for ${item.id}: ${item[key]}`);
  }
  assert.ok(['PENDING_FINAL_HEAD_CI','PASSING_FINAL_HEAD_CI','PENDING_CLAUSE_REVIEW'].includes(item.softwareEvidence),'Unsupported software evidence status.');
  assert.ok(item.launchGate===null||/^L[1-7]$/.test(item.launchGate));
  assert.equal(item.launchEvidence,item.launchGate?'UNMET':'REVIEW_REQUIRED','This inventory cannot self-certify a live deployment.');
 }
 assert.equal(required.size,0,'Missing requirement.');
 return true;
}
if(process.argv[1]&&pathToFileURL(resolve(process.argv[1])).href===import.meta.url){
 verifyReleaseContract(JSON.parse(readFileSync('docs/v52/requirements.json','utf8')));
 console.log('V52 release contract verified: every requirement mapped; deployment evidence remains independently gated.');
}
