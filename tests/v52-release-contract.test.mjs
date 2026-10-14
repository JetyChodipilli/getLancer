import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {verifyReleaseContract} from '../scripts/v52-release-contract.mjs';
const contract=()=>JSON.parse(readFileSync('docs/v52/requirements.json','utf8'));
test('release traceability refuses omitted, duplicated and nonexistent verification ownership',()=>{
 for(const mutate of [v=>v.requirements.pop(),v=>v.requirements[1]=structuredClone(v.requirements[0]),v=>v.requirements[0].owner='',v=>v.requirements[0].primaryVerification='missing-evidence-file']){
  const value=contract();mutate(value);assert.throws(()=>verifyReleaseContract(value));
 }
});
test('structural checks cannot close a production or research gate',()=>{
 const value=contract();value.requirements.find(r=>r.launchGate).launchEvidence='PASS';assert.throws(()=>verifyReleaseContract(value));
});
