import test from 'node:test';
import assert from 'node:assert/strict';
import {backendChecks,verifyBackendEvidence,verifyBrowserEvidence,observeRequirements} from '../scripts/phase-completion-evidence.mjs';
import {readFileSync} from 'node:fs';
const reports=()=>Object.fromEntries(backendChecks.map(check=>['TEST-'+check.className+'.xml',Buffer.from('<testsuite>'+check.tests.map(name=>'<testcase classname="'+check.className+'" name="'+name+'"/>').join('')+'</testsuite>')]));
const browser=()=>({suites:[{specs:Array.from({length:6},(_,index)=>({title:'E2E-0'+(index+1)+' observed journey',ok:true,tests:['desktop','phone','tablet'].map(projectName=>({projectName,expectedStatus:'passed',status:'expected',results:[{status:'passed'}]}))}))}]});
test('backend repair signoff requires every exact named test with real unambiguous passing XML',()=>{
 const valid=reports();assert.equal(verifyBackendEvidence(name=>valid[name]).length,3);
 for(const mutate of [v=>delete v[Object.keys(v)[0]],v=>v[Object.keys(v)[0]]=Buffer.from('<testsuite><system-out>all passed</system-out></testsuite>'),v=>v[Object.keys(v)[0]]=Buffer.from(v[Object.keys(v)[0]].toString().replace('/>','><skipped/></testcase>')),v=>v[Object.keys(v)[0]]=Buffer.from(v[Object.keys(v)[0]].toString().replace('</testsuite>',v[Object.keys(v)[0]].toString().match(/<testcase[^>]+\/>/)[0]+'</testsuite>'))]){
  const value=reports();mutate(value);assert.throws(()=>verifyBackendEvidence(name=>value[name]));
 }
});
test('browser repair signoff rejects missing journeys, widths, skips, failures and hidden retries',()=>{
 assert.equal(verifyBrowserEvidence(browser()).length,6);
 for(const mutate of [v=>v.suites[0].specs.pop(),v=>v.suites[0].specs[0].ok=false,v=>v.suites[0].specs[0].tests.pop(),v=>v.suites[0].specs[0].tests[0].projectName='unknown',v=>v.suites[0].specs[0].tests[0].results[0].status='skipped',v=>v.suites[0].specs[0].tests[0].results.push({status:'passed'})]){const value=browser();mutate(value);assert.throws(()=>verifyBrowserEvidence(value))}
});
test('requirement observations retain owner review and launch gates, and reject ambiguous or missing artifacts',()=>{
 const register=JSON.parse(readFileSync('docs/v52/requirements.json','utf8'));
 const observed=observeRequirements(register,path=>readFileSync(path));assert.equal(observed.length,68);
 assert.ok(observed.every(row=>row.clauseReview==='REQUIRED'&&row.artifacts.every(file=>/^[a-f0-9]{64}$/.test(file.sha256))));
 for(let i=0;i<68;i++){assert.equal(observed[i].launchGate,register.requirements[i].launchGate);assert.equal(observed[i].launchEvidence,register.requirements[i].launchEvidence)}
 const duplicate=structuredClone(register);duplicate.requirements[1].id=duplicate.requirements[0].id;assert.throws(()=>observeRequirements(duplicate,()=>Buffer.from('source')));
 const unsafe=structuredClone(register);unsafe.requirements[0].primaryVerification='../outside';assert.throws(()=>observeRequirements(unsafe,()=>Buffer.from('source')));
 assert.throws(()=>observeRequirements(register,()=>{throw new Error('missing artifact')}));
});
