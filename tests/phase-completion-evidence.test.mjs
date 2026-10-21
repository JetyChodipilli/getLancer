import test from 'node:test';
import assert from 'node:assert/strict';
import {backendChecks,verifyBackendEvidence,verifyBrowserEvidence,observeRequirements,bindEvidence} from '../scripts/phase-completion-evidence.mjs';
import {readFileSync} from 'node:fs';
import fs from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {syncBuiltinESMExports} from 'node:module';
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
test('evidence stays bound to the opened inode when its pathname is replaced and rejects a final symlink',()=>{
 const root=fs.mkdtempSync(join(tmpdir(),'phase-evidence-')),folder=join(root,'test-results/connected');fs.mkdirSync(folder,{recursive:true});
 const path=join(folder,'results.json'),retained=join(folder,'retained.json'),forged=join(folder,'forged.json'),original=browser(),replacement=browser();replacement.suites[0].specs[0].title+=' forged';
 fs.writeFileSync(path,JSON.stringify(original));fs.writeFileSync(forged,JSON.stringify(replacement));
 const lstat=fs.lstatSync,fstat=fs.fstatSync;let swapped=false;
 function swap(){if(swapped)return;swapped=true;fs.renameSync(path,retained);fs.symlinkSync(forged,path)}
 fs.lstatSync=(...args)=>{const value=lstat(...args);if(args[0]===path)swap();return value};
 fs.fstatSync=(...args)=>{const value=fstat(...args);swap();return value};syncBuiltinESMExports();
 try{
  const identity={sourceSha:'a'.repeat(40),workflowRunId:'1',workflowRunAttempt:'1'};
  const evidence=bindEvidence('connected',root,identity);assert.ok(swapped);assert.equal(evidence.checks[0].title,original.suites[0].specs[0].title);
  assert.throws(()=>bindEvidence('connected',root,identity),/ELOOP/);
 }finally{fs.lstatSync=lstat;fs.fstatSync=fstat;syncBuiltinESMExports();fs.rmSync(root,{recursive:true,force:true})}
});
test('evidence rejects an inode that grows after its size was checked',()=>{
 const root=fs.mkdtempSync(join(tmpdir(),'phase-evidence-')),folder=join(root,'test-results/connected');fs.mkdirSync(folder,{recursive:true});const path=join(folder,'results.json');fs.writeFileSync(path,JSON.stringify(browser()));
 const fstat=fs.fstatSync;let grown=false;
 fs.fstatSync=(...args)=>{const value=fstat(...args);if(!grown){grown=true;fs.appendFileSync(path,' ')}return value};syncBuiltinESMExports();
 try{assert.throws(()=>bindEvidence('connected',root,{sourceSha:'a'.repeat(40),workflowRunId:'1',workflowRunAttempt:'1'}),/Evidence changed during read/)}finally{fs.fstatSync=fstat;syncBuiltinESMExports();fs.rmSync(root,{recursive:true,force:true})}
});
