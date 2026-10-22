import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {writeFileSync,mkdirSync,openSync,fstatSync,readSync,closeSync,constants} from 'node:fs';
import {resolve,dirname} from 'node:path';
import {pathToFileURL} from 'node:url';
import {junitCases,requirePassingTest} from './junit-evidence.mjs';

export const backendChecks=[
 {id:'DISCOVERY',className:'com.getlancer.integration.ComponentsIntegrationTest',tests:['combinedComponentFiltersMatchLegacyMetadataBeforeStablePagination','combinedComponentCountsExcludePrivateDraftsAndRecheckCurrentCreatorAuthority','componentSearchTreatsSqlWildcardsAsLiteralCharacters','componentFilterValidationRejectsUnboundedOrMalformedInput']},
 {id:'BUSINESS',className:'com.getlancer.integration.BusinessIntegrationTest',tests:['everyRetainedBusinessCollectionContinuesBeyondOneHundredWithoutTenantLeakage','savedAndShortlistedUnavailableRecordsStayReachableAndFulfillmentSeesProofPastPageOneHundred','oldFulfilledSourcingCannotHideNewWorkAndHistoryIncludesCompletedClosedBriefs']},
 {id:'TEAMS',className:'com.getlancer.integration.TeamsIntegrationTest',tests:['largeTeamCollectionsAreBoundedAndEveryRoleRemainsReachable','leadPagesHaveBoundedNotePreviewsAndParentScopedContinuation','collectionContinuationRechecksDemotionExpiryAndVisibleActivityTotals']},
];
export function verifyBackendEvidence(read){
 const verified=[];
 for(const check of backendChecks){const bytes=read('TEST-'+check.className+'.xml'),cases=junitCases(bytes);for(const name of check.tests)requirePassingTest(cases,check.className,name);verified.push({id:check.id,tests:check.tests,reportSha256:sha(bytes)})}
 return verified;
}
export function verifyBrowserEvidence(report){
 assert.ok(report&&Array.isArray(report.suites),'Require completed Playwright JSON evidence.');
 const specs=[];function walk(suite){specs.push(...(suite.specs||[]));for(const child of suite.suites||[])walk(child)}for(const suite of report.suites)walk(suite);
 const verified=[];
 for(let number=1;number<=6;number++){
  const prefix='E2E-'+String(number).padStart(2,'0')+' ',matching=specs.filter(spec=>spec.title?.startsWith(prefix));
  // Playwright can emit one spec per project instead of merging their tests.
  // Only combine entries for the same source location and full journey title.
  assert.equal(new Set(matching.map(spec=>JSON.stringify([spec.file,spec.line,spec.column,spec.title]))).size,1,'Missing/ambiguous original browser journey: '+prefix);
  const spec=matching[0],tests=matching.flatMap(entry=>{assert.equal(entry.ok,true,'Browser journey failed: '+prefix);return entry.tests});
  assert.equal(tests.length,3,'Each journey must execute at three viewport profiles.');
  assert.deepEqual(new Set(tests.map(test=>test.projectName)),new Set(['desktop','phone','tablet']));
  for(const test of tests){assert.equal(test.expectedStatus,'passed');assert.equal(test.status,'expected');assert.equal(test.results.length,1,'Require a clean result without hidden retries.');assert.equal(test.results[0].status,'passed','Skipped/failed browser case cannot close acceptance.')}
  verified.push({id:prefix.trim(),title:spec.title,profiles:['desktop','phone','tablet']});
 }
 return verified;
}
// Preserve the distinction between observing files and reviewing requirement clauses.
// Every row gets immutable observations; none is promoted by file existence alone.
export function observeRequirements(register,read){
 assert.equal(register.schema,1);assert.equal(register.requirements.length,68);
 const ids=new Set();
 return register.requirements.map(item=>{
  assert.match(item.id,/^(FR|NFR)-[0-9]{3}$/);assert.ok(!ids.has(item.id));ids.add(item.id);
  const artifacts=['primaryVerification','supplementaryVerification'].map(key=>{
   const path=item[key];assert.ok(typeof path==='string'&&!path.startsWith('/')&&!path.split(/[\\/]/).includes('..'));
   return {path,sha256:sha(read(path))};
  });
  return {id:item.id,title:item.title,owner:item.owner,artifacts,clauseReview:'REQUIRED',launchGate:item.launchGate,launchEvidence:item.launchEvidence};
 });
}
const sha=bytes=>createHash('sha256').update(bytes).digest('hex');
function readBounded(path){
 const fd=openSync(path,constants.O_RDONLY|constants.O_NOFOLLOW|constants.O_NONBLOCK);
 try{
  const file=fstatSync(fd);assert.ok(file.isFile()&&file.size<=16*1024*1024,'Require bounded regular evidence.');
  const bytes=Buffer.alloc(file.size);let offset=0;
  while(offset<bytes.length){const count=readSync(fd,bytes,offset,bytes.length-offset,null);assert.ok(count>0,'Evidence changed during read.');offset+=count}
  assert.equal(readSync(fd,Buffer.alloc(1),0,1,null),0,'Evidence changed during read.');return bytes;
 }finally{closeSync(fd)}
}
export function bindEvidence(stage,root,identity){
 assert.ok(['backend','connected'].includes(stage),'Choose backend or connected evidence.');
 assert.match(identity.sourceSha||'',/^[a-f0-9]{40}$/);assert.match(identity.workflowRunId||'',/^[1-9][0-9]*$/);assert.match(identity.workflowRunAttempt||'',/^[1-9][0-9]*$/);
 const checks=stage==='backend'?verifyBackendEvidence(name=>readBounded(resolve(root,'backend/target/surefire-reports',name))):verifyBrowserEvidence(JSON.parse(readBounded(resolve(root,'test-results/connected/results.json'))));
 const requirements=stage==='backend'?observeRequirements(JSON.parse(readBounded(resolve(root,'docs/v52/requirements.json'))),path=>readBounded(resolve(root,path))):undefined;
 return {schema:1,stage,...identity,generatedAt:new Date().toISOString(),checks,requirements,launchAcceptance:'UNMET',limitations:['Named automated checks prove these repair outcomes only.','Hashed requirement artifacts are traceability observations; every clause still needs the named owner review.','Provider KVM, real payments, load/soak, production restore and manual research require their own launch evidence.']};
}
if(process.argv[1]&&pathToFileURL(resolve(process.argv[1])).href===import.meta.url){
 const stage=process.argv[2],result=bindEvidence(stage,resolve('.'),{sourceSha:process.env.PHASE_SOURCE_SHA,workflowRunId:process.env.GITHUB_RUN_ID,workflowRunAttempt:process.env.GITHUB_RUN_ATTEMPT});
 const output=stage==='backend'?'backend/target/phase-completion-evidence.json':'test-results/connected/phase-completion-evidence.json';mkdirSync(dirname(output),{recursive:true});writeFileSync(output,JSON.stringify(result,null,2)+'\n');console.log('Bound passing phase repair evidence to the current CI source/run; production acceptance remains unmet.');
}
