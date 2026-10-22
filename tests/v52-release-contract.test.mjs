import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {verifyReleaseContract,verifyClauseReview} from '../scripts/v52-release-contract.mjs';
const contract=()=>JSON.parse(readFileSync('docs/v52/requirements.json','utf8'));
test('release traceability refuses omitted, duplicated and nonexistent verification ownership',()=>{
 for(const mutate of [v=>v.requirements.pop(),v=>v.requirements[1]=structuredClone(v.requirements[0]),v=>v.requirements[0].owner='',v=>v.requirements[0].primaryVerification='missing-evidence-file']){
  const value=contract();mutate(value);assert.throws(()=>verifyReleaseContract(value));
 }
});
test('structural checks cannot close a production or research gate',()=>{
 const value=contract();value.requirements.find(r=>r.launchGate).launchEvidence='PASS';assert.throws(()=>verifyReleaseContract(value));
});
test('clause audit retains every original acceptance ID and refuses false certification',()=>{
 const review=()=>JSON.parse(readFileSync('docs/v52/clause-review.json','utf8'));
 assert.equal(verifyClauseReview(review()),true);
 for(const mutate of [v=>v.requirements.pop(),v=>v.requirements[1]=structuredClone(v.requirements[0]),v=>v.acceptanceCertified=true,v=>v.requirements[0].certified=true,v=>v.requirements.find(r=>r.reviewState==='REVIEWED_WITH_OPEN_ITEMS').reviewState='SOURCE_SUPPORTED',v=>v.requirements[0].clauses=[]]){
  const value=review();mutate(value);assert.throws(()=>verifyClauseReview(value));
 }
});
