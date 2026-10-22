import assert from 'node:assert/strict';
import {readFileSync,existsSync} from 'node:fs';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';

// Structural traceability is supplementary. Passing this cannot certify a deployment.
export function verifyClauseReview(value,root=resolve('.')) {
 assert.equal(value.schema,1);assert.equal(value.acceptanceCertified,false,'A source audit cannot certify acceptance.');
 assert.match(value.baseline,/^[a-f0-9]{40}$/);assert.match(value.authority.sha256,/^[a-f0-9]{64}$/);
 const required=new Set([...Array.from({length:46},(_,n)=>`FR-${String(n+1).padStart(3,'0')}`),...Array.from({length:22},(_,n)=>`NFR-${String(n+1).padStart(3,'0')}`)]);
 for(const row of value.requirements){
  assert.ok(required.delete(row.id),'Duplicate or unknown clause review.');assert.equal(row.certified,false);
  assert.ok(row.acceptanceText?.length>20);assert.ok(['SOURCE_SUPPORTED','REVIEWED_WITH_OPEN_ITEMS'].includes(row.reviewState));
  assert.ok(Array.isArray(row.gaps)&&Array.isArray(row.external));assert.ok(row.clauses.length>0);
  for(const clause of row.clauses){
   assert.ok(clause.obligation?.length>10&&clause.reason?.length>20);assert.ok(['SUPPORTED','PARTIAL','GAP','EXTERNAL'].includes(clause.verdict));
   assert.ok(Array.isArray(clause.evidence));if(clause.verdict==='SUPPORTED')assert.ok(clause.evidence.length>0);
   for(const evidence of clause.evidence){assert.ok(typeof evidence.path==='string'&&!evidence.path.includes('..')&&!evidence.path.startsWith('/'));assert.ok(existsSync(resolve(root,evidence.path)));assert.ok(evidence.detail?.length>10);}
  }
  if(row.reviewState==='SOURCE_SUPPORTED')assert.ok(!row.gaps.length&&!row.external.length&&row.clauses.every(c=>c.verdict==='SUPPORTED'),'Open clauses cannot be labelled supported.');
 }
 assert.equal(required.size,0,'Missing clause review.');return true;
}
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
  assert.ok(['PENDING_FINAL_HEAD_CI','PASSING_FINAL_HEAD_CI','PENDING_CLAUSE_REVIEW','CLAUSE_REVIEWED'].includes(item.softwareEvidence),'Unsupported software evidence status.');
  if(item.softwareEvidence==='CLAUSE_REVIEWED')assert.equal(item.clauseReview,'docs/v52/clause-review.json');
  assert.ok(item.launchGate===null||/^L[1-7]$/.test(item.launchGate));
  assert.equal(item.launchEvidence,item.launchGate?'UNMET':'REVIEW_REQUIRED','This inventory cannot self-certify a live deployment.');
 }
 assert.equal(required.size,0,'Missing requirement.');
 if(value.requirements.some(item=>item.softwareEvidence==='CLAUSE_REVIEWED'))verifyClauseReview(JSON.parse(readFileSync(resolve(root,'docs/v52/clause-review.json'),'utf8')),root);
 return true;
}
if(process.argv[1]&&pathToFileURL(resolve(process.argv[1])).href===import.meta.url){
 verifyReleaseContract(JSON.parse(readFileSync('docs/v52/requirements.json','utf8')));
 console.log('V52 release contract verified: every requirement mapped; deployment evidence remains independently gated.');
}
