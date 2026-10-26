import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {componentReviewBody, componentReviewCriteria} from '../lib/components.ts';
const evidence='Independent checks of this exact submitted artifact.';
function form() { const data = new FormData();for (const [key,value] of Object.entries({revision:'4',sourceHash:'a'.repeat(64),decision:'APPROVE',reason:evidence})) data.set(key,value);for (const key of componentReviewCriteria){data.set(key+'Result','PASS');data.set(key+'Note',evidence);}return data; }
test('moderator command binds exact submission and includes only four explicit criterion results',()=>{
  const input=form();input.set('adminId','forged');input.set('restoredDecision','forged');const result=componentReviewBody(input);
  assert.deepEqual(Object.keys(result),['revision','sourceHash','decision','reason','originality','quality','license','execution']);
  assert.equal(result.revision,4);assert.equal(result.sourceHash,'a'.repeat(64));for(const key of componentReviewCriteria)assert.deepEqual(result[key],{result:'PASS',note:evidence});
});
test('every criterion and bounded evidence is mandatory including not-applicable reasons',()=>{
  for(const key of componentReviewCriteria){for(const [field,value] of [[key+'Result',''],[key+'Result','APPROVE'],[key+'Note','short'],[key+'Note','x'.repeat(1001)]]){const input=form();input.set(field,value);assert.throws(()=>componentReviewBody(input),/each assessment/);}}
  const input=form();input.set('executionResult','NOT_APPLICABLE');assert.equal(componentReviewBody(input).execution.result,'NOT_APPLICABLE');input.set('executionNote',' '.repeat(20));assert.throws(()=>componentReviewBody(input),/each assessment/);
});
test('invalid source binding and internal recovery commands cannot enter ordinary review',()=>{
  for(const [field,value] of [['revision','1.2'],['revision','0'],['sourceHash','A'.repeat(64)],['decision','APPEAL_RESTORE'],['reason','short']]){const input=form();input.set(field,value);assert.throws(()=>componentReviewBody(input));}
  for(const decision of ['APPROVE','CHANGES_REQUESTED','SUSPEND']){const input=form();input.set('decision',decision);assert.equal(componentReviewBody(input).decision,decision);}
});
test('server audit is private append-only evidence with exact artifact and appeal provenance',()=>{
 const migration=readFileSync('backend/src/main/resources/db/migration/V50__component_review_assessments.sql','utf8');assert.match(migration,/BEFORE UPDATE OR DELETE/);assert.match(migration,/ENABLE ROW LEVEL SECURITY/);assert.match(migration,/REVOKE ALL ON component_review_assessments FROM PUBLIC/);
 const service=readFileSync('backend/src/main/java/com/getlancer/components/ComponentService.java','utf8');const review=service.slice(service.indexOf('private Object review('),service.indexOf('static void checkRevision'));
 assert.match(review,/reviewHash\(c,submitted\)\.equals\(b.get\("sourceHash"\)\)/);assert.match(review,/reviewAssessments\(b,decision,submitted\)/);assert.match(review,/INSERT INTO component_review_assessments/);assert.match(review,/restoredDecision==null\?decision:"APPEAL_RESTORE"/);
 const projection=service.slice(service.indexOf('Map<String,Object> entry('),service.indexOf('static Map<String,Object> sourceProjection'));assert.doesNotMatch(projection,/reviewHistory|assessments/);
 const dto=readFileSync('backend/src/main/java/com/getlancer/dto/ComponentRequests.java','utf8');assert.equal((dto.match(/@NotNull @Valid Assessment/g)||[]).length,4);assert.match(dto,/APPROVE\|CHANGES_REQUESTED\|SUSPEND/);
});
