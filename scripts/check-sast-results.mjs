import assert from 'node:assert/strict';
import {readFileSync,readdirSync,writeFileSync} from 'node:fs';
import {join,resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
import {createHash} from 'node:crypto';
import {loadSastAssessment,assessSastFindings} from './sast-dispositions.mjs';

const object=value=>value!==null&&typeof value==='object'&&!Array.isArray(value);
const text=value=>typeof value==='string'&&value.length>0;
function componentFor(run,reference){
  if(reference===undefined)return run.tool.driver;
  assert.ok(object(reference),'Invalid rule component reference.');
  const extensions=run.tool.extensions||[],candidates=[];
  if(reference.index!==undefined){
    assert.ok(Number.isInteger(reference.index)&&reference.index>=0&&reference.index<extensions.length,'Unknown rule component index.');
    candidates.push(extensions[reference.index]);
  }
  for(const field of ['name','guid'])if(reference[field]!==undefined){
    assert.ok(text(reference[field]));
    const matches=[run.tool.driver,...extensions].filter(component=>component[field]===reference[field]);
    assert.equal(matches.length,1,'Unknown or ambiguous rule component.');candidates.push(matches[0]);
  }
  assert.ok(candidates.length,'Empty rule component reference.');
  assert.ok(candidates.every(candidate=>candidate===candidates[0]),'Conflicting rule component references.');
  return candidates[0];
}
function descriptorFor(run,result){
  assert.ok(object(result),'Invalid SAST finding.');
  const reference=result.rule;
  assert.ok(reference===undefined||object(reference),'Invalid rule reference.');
  const component=componentFor(run,reference?.toolComponent),rules=component.rules||[],candidates=[];
  assert.ok(text(result.ruleId)||text(reference?.id),'Finding has no rule identity.');
  for(const id of [result.ruleId,reference?.id])if(id!==undefined){
    assert.ok(text(id),'Invalid rule ID.');
    const matches=rules.filter(rule=>rule.id===id);
    assert.equal(matches.length,1,'Unknown or ambiguous SAST rule.');candidates.push(matches[0]);
  }
  for(const index of [result.ruleIndex,reference?.index])if(index!==undefined){
    assert.ok(Number.isInteger(index)&&index>=0&&index<rules.length,'Unknown rule index.');candidates.push(rules[index]);
  }
  assert.ok(candidates.every(candidate=>candidate===candidates[0]),'Conflicting SAST rule references.');
  return candidates[0];
}
export function evaluateSast(document){
  assert.ok(object(document)&&document.version==='2.1.0','Require complete SARIF 2.1.0 output.');
  assert.ok(Array.isArray(document.runs)&&document.runs.length,'No SAST runs were produced.');
  const findings=[];
  for(const run of document.runs){
    assert.ok(object(run)&&object(run.tool)&&object(run.tool.driver)&&text(run.tool.driver.name),'Invalid analysis tool.');
    assert.ok(Array.isArray(run.results),'Missing SAST result collection.');
    assert.ok(run.tool.extensions===undefined||Array.isArray(run.tool.extensions),'Invalid tool extensions.');
    for(const component of [run.tool.driver,...(run.tool.extensions||[])]){
      assert.ok(object(component)&&text(component.name),'Invalid tool component.');
      assert.ok(component.rules===undefined||Array.isArray(component.rules),'Invalid rule collection.');
      for(const rule of component.rules||[])assert.ok(object(rule)&&text(rule.id),'Invalid rule descriptor.');
    }
    assert.ok(run.invocations===undefined||Array.isArray(run.invocations),'Invalid analysis execution evidence.');
    for(const invocation of run.invocations||[]){
      assert.ok(object(invocation)&&invocation.executionSuccessful===true,'Analysis did not complete successfully.');
      for(const key of ['toolExecutionNotifications','toolConfigurationNotifications']){
        assert.ok(invocation[key]===undefined||Array.isArray(invocation[key]),'Invalid analysis diagnostics.');
        for(const notification of invocation[key]||[])assert.ok(object(notification)&&notification.level!=='error','Analysis reported an execution/configuration error.');
      }
    }
    for(const result of run.results){
      const rule=descriptorFor(run,result),raw=rule.properties?.['security-severity'];
      const security=rule.properties?.tags?.includes('security');
      assert.ok(raw!==undefined||!security,'Security finding has no severity evidence.');
      let score=0;
      if(raw!==undefined){
        assert.ok((typeof raw==='number'||typeof raw==='string'&&raw.trim()!=='')&&Number.isFinite(Number(raw))&&Number(raw)>=0&&Number(raw)<=10,'Invalid security severity.');score=Number(raw);
      }
      // Scanner suppression metadata is not a reviewed applicability disposition.
      findings.push({ruleId:rule.id,ruleSha256:createHash('sha256').update(JSON.stringify(rule)).digest('hex'),score,blocking:score>=7,result});
    }
  }
  return findings;
}
if(process.argv[1]&&pathToFileURL(resolve(process.argv[1])).href===import.meta.url){
  const directory=process.argv[2],files=readdirSync(directory).filter(file=>file.endsWith('.sarif'));
  assert.ok(files.length,'No SAST output was produced.');
  assert.ok(process.argv.length<=4&&(!process.argv[3]||process.argv[3]==='--reviewed-get-assessments'));
  const assessment=process.argv[3]?loadSastAssessment():undefined;
  let blocked=0,total=0,notAffected=0;
  for(const file of files){
    const raw=readFileSync(join(directory,file));
    let findings=evaluateSast(JSON.parse(raw));
    if(assessment)findings=assessSastFindings(findings,assessment.spec,assessment.evidence,createHash('sha256').update(raw).digest('hex'));
    total+=findings.length;notAffected+=findings.filter(finding=>finding.disposition).length;
    writeFileSync(join(directory,file+'.assessed.json'),JSON.stringify({findings,blockingFindings:findings.filter(finding=>finding.blocking).length},null,2)+'\n');
    for(const finding of findings)if(finding.blocking){blocked++;console.error('Blocked SAST finding: '+finding.ruleId+' severity '+finding.score+' in '+file);}
  }
  console.log('SAST evaluated '+files.length+' SARIF files and '+total+' findings; '+blocked+' blocking high/critical, '+notAffected+' verified not applicable. Raw findings retained.');
  if(blocked)process.exitCode=1;
}
