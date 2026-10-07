import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {readFileSync,readdirSync,writeFileSync,openSync,closeSync,fstatSync,constants} from 'node:fs';
import {resolve,join,relative} from 'node:path';
import {pathToFileURL} from 'node:url';
import {junitCases,requirePassingTest} from './junit-evidence.mjs';
const digest=value=>createHash('sha256').update(value).digest('hex');
const rule='java/csrf-unprotected-request-type';
export const requiredBuildInputs=['backend/pom.xml','.github/workflows/ci.yml','scripts/check-sast-results.mjs','scripts/sast-dispositions.mjs','scripts/junit-evidence.mjs'];
const allowed=new Set(['backend/src/main/java/com/getlancer/admin/AdminController.java','backend/src/main/java/com/getlancer/auth/GoogleAuthController.java','backend/src/main/java/com/getlancer/auth/GitHubAuthController.java']);
export const requiredSecurityTests=[
 {report:'TEST-com.getlancer.auth.AuthenticationHardeningIntegrationTest.xml',className:'com.getlancer.auth.AuthenticationHardeningIntegrationTest',names:[
 'googleCallbackRejectsMismatchesWithoutConsumingStateOrClearingBinding',
 'githubCallbackRejectsMismatchesWithoutConsumingStateOrClearingBinding',
 'googleCallbackRejectsExpiredAndCrossProviderStateWithoutExchange',
 'githubCallbackRejectsExpiredAndCrossProviderStateWithoutExchange',
 'googleSuccessfulCallbackConsumesStateBeforeExchangeAndRejectsRacingAndLaterReplay',
 'githubSuccessfulCallbackConsumesStateBeforeExchangeAndRejectsRacingAndLaterReplay',
 'googleFailedProviderExchangeCannotRestoreConsumedStateOrIssueSessionOnReplay',
 'githubFailedProviderExchangeCannotRestoreConsumedStateOrIssueSessionOnReplay']},
 {report:'TEST-com.getlancer.integration.DataExposureIntegrationTest.xml',className:'com.getlancer.integration.DataExposureIntegrationTest',names:[
 'administratorInquiryEvidenceGetOnlyAppendsAuditAfterCurrentMfa']}
];
const inputs=(root,directory)=>{
 function walk(path){return readdirSync(path,{withFileTypes:true}).flatMap(entry=>{
  assert.ok(!entry.isSymbolicLink(),'Review inputs cannot contain symlinks.');
  const next=join(path,entry.name);return entry.isDirectory()?walk(next):[next];
 });}
 return walk(join(root,directory)).map(path=>relative(root,path)).sort();
};
function read(path){
 const fd=openSync(path,constants.O_RDONLY|constants.O_NOFOLLOW);
 try{assert.ok(fstatSync(fd).isFile());return readFileSync(fd);}finally{closeSync(fd);}
}
export function validateSastEvidence(root,spec,evidence,identity,now=new Date()){
 assert.equal(spec.schema,1);assert.ok(Date.parse(spec.expiresAt)>now.getTime(),'GET assessment expired.');
 assert.equal(spec.findings.length,3);assert.deepEqual(new Set(spec.findings.map(f=>f.path)),allowed);
 assert.deepEqual(spec.tests,requiredSecurityTests,'Security regression requirements changed.');
 const production=inputs(root,'backend/src/main');assert.ok(production.length>50,'Incomplete production review inputs.');
 assert.deepEqual(production,Object.keys(spec.productionInputs).sort(),'Production review inventory changed.');
 assert.deepEqual(inputs(root,'backend/src/test'),Object.keys(spec.testInputs).sort(),'Security test review inventory changed.');
 assert.ok(Object.keys(spec.testInputs).includes('backend/src/test/java/com/getlancer/auth/AuthenticationHardeningIntegrationTest.java')
     &&Object.keys(spec.testInputs).includes('backend/src/test/java/com/getlancer/integration/DataExposureIntegrationTest.java'));
 assert.deepEqual(Object.keys(spec.buildInputs).sort(),[...requiredBuildInputs].sort(),'Build review inventory changed.');
 for(const [path,hash]of Object.entries({...spec.productionInputs,...spec.testInputs,...spec.buildInputs}))
  assert.equal(digest(read(join(root,path))),hash,'Reviewed source or test changed: '+path);
 assert.equal(evidence.schema,1);
 for(const key of ['sourceSha','workflowRunId','workflowRunAttempt']){
  assert.ok(identity[key]&&String(identity[key])===String(evidence[key]),'Evidence is from another commit/run/attempt.');
 }
 const age=now.getTime()-Date.parse(evidence.generatedAt);
 assert.ok(Number.isFinite(age)&&age>=0&&age<=60*60*1000,'Require current same-run security tests.');
 assert.equal(evidence.specSha256,digest(JSON.stringify(spec)),'Assessment differs from tested review specification.');
 for(const test of spec.tests){
  const bytes=read(join(root,'backend/target/surefire-reports',test.report));
  assert.equal(digest(bytes),evidence.reports[test.report],'Test artifact changed after verification.');
  const cases=junitCases(bytes);
  for(const name of test.names)requirePassingTest(cases,test.className,name);
 }
 return true;
}
export function assessSastFindings(findings,spec,evidence,rawSha256){
 return findings.map(finding=>{
  if(!finding.blocking||finding.ruleId!==rule)return finding;
  const locations=finding.result.locations;
  if(!Array.isArray(locations)||locations.length!==1)return finding;
  const physical=locations[0].physicalLocation;
  const entry=spec.findings.find(item=>item.path===physical?.artifactLocation?.uri
      &&item.startLine===physical?.region?.startLine&&item.ruleSha256===finding.ruleSha256&&item.score===finding.score);
  if(!entry)return finding;
  return {...finding,blocking:false,disposition:{status:'not_affected',reason:entry.reason,
      reviewedAt:spec.reviewedAt,expiresAt:spec.expiresAt,
      evidence:{rawSarifSha256:rawSha256,specSha256:evidence.specSha256,sourceSha:evidence.sourceSha,
        workflowRunId:evidence.workflowRunId,workflowRunAttempt:evidence.workflowRunAttempt,reports:evidence.reports}}};
 });
}
export function identityFromEnvironment(){return {sourceSha:process.env.SECURITY_SOURCE_SHA,
 workflowRunId:process.env.GITHUB_RUN_ID,workflowRunAttempt:process.env.GITHUB_RUN_ATTEMPT};}
export function loadSastAssessment(root=resolve('.')){
 const spec=JSON.parse(read(join(root,'docs/SAST_APPLICABILITY.json')));
 const evidence=JSON.parse(read(join(root,'backend/target/surefire-reports/sast-evidence.json')));
 validateSastEvidence(root,spec,evidence,identityFromEnvironment());return {spec,evidence};
}
if(process.argv[1]&&pathToFileURL(resolve(process.argv[1])).href===import.meta.url){
 assert.equal(process.argv[2],'--write-evidence');
 const root=resolve('.'),spec=JSON.parse(read(join(root,'docs/SAST_APPLICABILITY.json')));
 const evidence={schema:1,...identityFromEnvironment(),generatedAt:new Date().toISOString(),specSha256:digest(JSON.stringify(spec)),reports:{}};
 for(const test of spec.tests)evidence.reports[test.report]=digest(read(join(root,'backend/target/surefire-reports',test.report)));
 validateSastEvidence(root,spec,evidence,identityFromEnvironment());
 writeFileSync(join(root,'backend/target/surefire-reports/sast-evidence.json'),JSON.stringify(evidence,null,2)+'\n');
 console.log('Bound passing GET security regressions to current reviewed inputs and this CI commit/run/attempt.');
}
