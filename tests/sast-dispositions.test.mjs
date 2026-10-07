import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync,mkdirSync,writeFileSync,readFileSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join,dirname} from 'node:path';
import {createHash} from 'node:crypto';
import {requiredSecurityTests,validateSastEvidence,assessSastFindings} from '../scripts/sast-dispositions.mjs';
const hash=value=>createHash('sha256').update(value).digest('hex');
function fixture(t){
 const root=mkdtempSync(join(tmpdir(),'sast-proof-'));t.after(()=>rmSync(root,{recursive:true,force:true}));
 const now=new Date('2026-10-07T06:00:00Z'),identity={sourceSha:'a'.repeat(40),workflowRunId:'123',workflowRunAttempt:'1'};
 const paths=['backend/src/main/java/com/getlancer/admin/AdminController.java','backend/src/main/java/com/getlancer/auth/GoogleAuthController.java','backend/src/main/java/com/getlancer/auth/GitHubAuthController.java'];
 const spec={schema:1,reviewedAt:'2026-10-07',expiresAt:'2026-11-05T00:00:00Z',findings:paths.map(path=>({path,startLine:35,score:8.8,ruleSha256:'r',reason:'Reviewed GET state protection'})),tests:structuredClone(requiredSecurityTests),productionInputs:{},testInputs:{}};
 const put=(path,content)=>{const full=join(root,path);mkdirSync(dirname(full),{recursive:true});writeFileSync(full,content);return hash(content);};
 for(const path of [...paths,...Array.from({length:51},(_,n)=>'backend/src/main/java/Fixture'+n+'.java')])spec.productionInputs[path]=put(path,'fixture source '+path);
 for(const path of ['backend/src/test/java/com/getlancer/auth/AuthenticationHardeningIntegrationTest.java','backend/src/test/java/com/getlancer/integration/DataExposureIntegrationTest.java'])spec.testInputs[path]=put(path,'fixture regression '+path);
 const evidence={schema:1,...identity,generatedAt:now.toISOString(),specSha256:hash(JSON.stringify(spec)),reports:{}};
 for(const test of spec.tests)evidence.reports[test.report]=put('backend/target/surefire-reports/'+test.report,'<testsuite>'+test.names.map(name=>'<testcase classname="'+test.className+'" name="'+name+'"/>').join('')+'</testsuite>');
 return {root,spec,evidence,identity,now,put};
}
test('only exact reviewed GET findings retain a visible assessment',t=>{
 const f=fixture(t);assert.equal(validateSastEvidence(f.root,f.spec,f.evidence,f.identity,f.now),true);
 const finding={blocking:true,ruleId:'java/csrf-unprotected-request-type',ruleSha256:'r',score:8.8,result:{locations:[{physicalLocation:{artifactLocation:{uri:f.spec.findings[0].path},region:{startLine:35}}}]}};
 const assessed=assessSastFindings([finding],f.spec,f.evidence,'raw-hash')[0];assert.equal(assessed.blocking,false);assert.equal(assessed.disposition.evidence.rawSarifSha256,'raw-hash');assert.equal(assessed.score,8.8);
 for(const change of [{ruleId:'java/sql-injection'},{ruleSha256:'changed'},{score:9.8},{result:{locations:[{physicalLocation:{artifactLocation:{uri:'another.java'},region:{startLine:35}}}]}},
  {result:{locations:[{physicalLocation:{artifactLocation:{uri:f.spec.findings[0].path},region:{startLine:36}}}]}}])assert.equal(assessSastFindings([{...finding,...change}],f.spec,f.evidence,'raw-hash')[0].blocking,true);
});
test('changed, added or missing production/test source invalidates the assessment',t=>{
 const f=fixture(t);f.put(Object.keys(f.spec.productionInputs)[0],'changed');assert.throws(()=>validateSastEvidence(f.root,f.spec,f.evidence,f.identity,f.now));
 const g=fixture(t);g.put('backend/src/main/java/New.java','new');assert.throws(()=>validateSastEvidence(g.root,g.spec,g.evidence,g.identity,g.now));
 const h=fixture(t);rmSync(join(h.root,Object.keys(h.spec.testInputs)[0]));assert.throws(()=>validateSastEvidence(h.root,h.spec,h.evidence,h.identity,h.now));
});
test('old, future, expired and cross-commit/run/attempt evidence fail closed',t=>{
 const f=fixture(t);
 for(const change of [{sourceSha:'b'.repeat(40)},{workflowRunId:'other'},{workflowRunAttempt:'2'},{generatedAt:'2026-10-07T04:00:00Z'},{generatedAt:'2026-10-07T07:00:00Z'},{specSha256:'changed'}])assert.throws(()=>validateSastEvidence(f.root,f.spec,{...f.evidence,...change},f.identity,f.now));
 assert.throws(()=>validateSastEvidence(f.root,{...f.spec,expiresAt:'2026-10-06T00:00:00Z'},f.evidence,f.identity,f.now));
});
test('missing, failed, skipped or substituted regressions cannot authorize a disposition',t=>{
 for(const failure of ['<failure/>','<error/>','<skipped/>','missing']){
  const f=fixture(t),test=f.spec.tests[0],path='backend/target/surefire-reports/'+test.report;
  let xml=readFileSync(join(f.root,path),'utf8');
  const old='<testcase classname="'+test.className+'" name="'+test.names[0]+'"/>';
  xml=xml.replace(old,failure==='missing'?'':old.slice(0,-2)+'>'+failure+'</testcase>');
  f.evidence.reports[test.report]=f.put(path,xml);assert.throws(()=>validateSastEvidence(f.root,f.spec,f.evidence,f.identity,f.now));
 }
 const f=fixture(t);f.put('backend/target/surefire-reports/'+f.spec.tests[0].report,'<testsuite/>');assert.throws(()=>validateSastEvidence(f.root,f.spec,f.evidence,f.identity,f.now));
 const g=fixture(t);g.spec.tests=[];g.evidence.specSha256=hash(JSON.stringify(g.spec));assert.throws(()=>validateSastEvidence(g.root,g.spec,g.evidence,g.identity,g.now));
});
