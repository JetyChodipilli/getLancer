import {test} from 'node:test';
import assert from 'node:assert/strict';
import {evaluateSast} from '../scripts/check-sast-results.mjs';
const rule={id:'java/sql-injection',properties:{tags:['security'],'security-severity':'9.3'}};
const report=(results=[{ruleId:rule.id,ruleIndex:0}],rules=[rule])=>({version:'2.1.0',runs:[{tool:{driver:{name:'CodeQL',rules}},invocations:[{executionSuccessful:true}],results}]});
test('valid clean runs pass, while empty or failed analysis cannot pass',()=>{
  assert.deepEqual(evaluateSast(report([],[])),[]);
  for(const bad of [{},{version:'2.1.0',runs:[]},report(null),report([],[])]){
    if(bad.runs?.[0]?.results?.length===0)bad.runs[0].invocations[0].executionSuccessful=false;
    assert.throws(()=>evaluateSast(bad));
  }
  for(const key of ['toolExecutionNotifications','toolConfigurationNotifications']){
    const bad=report([],[]);bad.runs[0].invocations[0][key]=[{level:'error'}];assert.throws(()=>evaluateSast(bad));
  }
});
test('all valid rule references preserve component-scoped severity',()=>{
  for(const result of [{ruleId:rule.id},{ruleId:rule.id,ruleIndex:0},{rule:{id:rule.id,index:0}}])assert.equal(evaluateSast(report([result]))[0].blocking,true);
  const driver=report();driver.runs[0].tool.extensions=[{name:'extension',rules:[{...rule,properties:{'security-severity':'1'}}]}];assert.equal(evaluateSast(driver)[0].score,9.3);
  const extension=report([{ruleId:rule.id,rule:{id:rule.id,index:0,toolComponent:{index:0}}}],[]);extension.runs[0].tool.extensions=[{name:'queries',rules:[rule]}];assert.equal(evaluateSast(extension)[0].blocking,true);
});
test('unknown, ambiguous and conflicting references fail closed',()=>{
  for(const result of [{ruleId:'unknown'},{ruleId:rule.id,ruleIndex:1},{ruleId:rule.id,rule:{id:'unknown'}},{ruleIndex:0},{rule:{id:rule.id,toolComponent:{index:3}}}])assert.throws(()=>evaluateSast(report([result])));
  assert.throws(()=>evaluateSast(report([{ruleId:rule.id}],[rule,rule])));
});
test('security severity must be present, finite and bounded',()=>{
  for(const raw of [undefined,'','garbage','Infinity',false,null,-1,11]){
    const invalid={...rule,properties:{tags:['security'],...(raw===undefined?{}:{'security-severity':raw})}};assert.throws(()=>evaluateSast(report(undefined,[invalid])));
  }
  assert.equal(evaluateSast(report([{ruleId:'diagnostic'}],[{id:'diagnostic'}]))[0].blocking,false);
});
test('accepted scanner suppressions do not exempt a high finding',()=>{
  assert.equal(evaluateSast(report([{ruleId:rule.id,suppressions:[{kind:'inSource',status:'accepted'}]}]))[0].blocking,true);
});
