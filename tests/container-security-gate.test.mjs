import {test} from 'node:test';
import assert from 'node:assert/strict';
import {evaluateContainer} from '../scripts/check-container-results.mjs';

const image='getlancer-api:scan';
const finding={VulnerabilityID:'CVE-2026-47884',PkgName:'org.springframework:spring-webmvc',InstalledVersion:'6.2.19',Severity:'CRITICAL'};
const evidence={id:'GHSA-pc63-qcmh-9cmg',purl:'pkg:maven/org.springframework/spring-webmvc@6.2.19?type=jar',disposition:{status:'not_affected',evidence:{mvcReportSha256:'real-proof-required-by-cli'}}};
const report=(vulnerabilities=[finding],type='jar',name=image)=>({SchemaVersion:2,ArtifactName:name,ArtifactType:'container_image',Results:[{Target:'alpine image',Class:'os-pkgs',Type:'alpine'},{Target:'Java',Class:type==='jar'?'lang-pkgs':'os-pkgs',Type:type,Vulnerabilities:vulnerabilities}]});

test('an unassessed critical image finding blocks and stays in the report',()=>{
  const result=evaluateContainer(report(),image);
  assert.equal(result.ApplicationGate.blocking,1);
  assert.deepEqual(result.Results[1].Vulnerabilities[0],finding);
});
test('exact REST assessment retains the critical package with an explicit disposition',()=>{
  const result=evaluateContainer(report(),image,evidence);
  assert.equal(result.ApplicationGate.blocking,0);
  assert.equal(result.ApplicationGate.notAffected,1);
  assert.equal(result.Results[1].Vulnerabilities[0].Severity,'CRITICAL');
  assert.deepEqual(result.Results[1].Vulnerabilities[0].ApplicationDisposition,evidence.disposition);
});
test('the assessment cannot suppress another CVE, coordinate, version, image or operating system',()=>{
  for(const replacement of [{VulnerabilityID:'CVE-other'},{PkgName:'other:package'},{InstalledVersion:'6.2.18'}])
    assert.equal(evaluateContainer(report([{...finding,...replacement}]),image,evidence).ApplicationGate.blocking,1);
  assert.equal(evaluateContainer(report([finding],'alpine'),image,evidence).ApplicationGate.blocking,1);
  assert.equal(evaluateContainer(report([finding],'jar','getlancer-publisher:scan'),'getlancer-publisher:scan',evidence).ApplicationGate.blocking,1);
  for(const replacement of [{id:'GHSA-other'},{purl:'pkg:maven/org.springframework/spring-webmvc@6.2.20?type=jar'},{disposition:{status:'affected'}}])
    assert.equal(evaluateContainer(report(),image,{...evidence,...replacement}).ApplicationGate.blocking,1);
});
test('unknown high and critical findings block even alongside an assessed finding',()=>{
  const result=evaluateContainer(report([finding,{...finding,VulnerabilityID:'CVE-new',Severity:'HIGH'}]),image,evidence);
  assert.equal(result.ApplicationGate.blocking,1);
  assert.equal(result.Results[1].Vulnerabilities.length,2);
});
test('empty, wrong-image and incomplete scans cannot pass',()=>{
  for(const bad of [{},{...report(),Results:[]},{...report(),Results:[{}]},
      {...report(),Results:report().Results.slice(1)},{...report(),ArtifactName:'other-image'},
      {...report(),ArtifactType:'filesystem'}])
    assert.throws(()=>evaluateContainer(bad,image,evidence));
});
test('missing or invalid scanner fields cannot suppress a vulnerability',()=>{
  for(const key of ['VulnerabilityID','PkgName','InstalledVersion','Severity']){
    const incomplete={...finding}; delete incomplete[key];
    assert.throws(()=>evaluateContainer(report([incomplete]),image,evidence));
    assert.throws(()=>evaluateContainer(report([{...finding,[key]:''}]),image,evidence));
  }
  for(const findings of [null,{},'clean']){
    const invalid=report(); invalid.Results[1].Vulnerabilities=findings;
    assert.throws(()=>evaluateContainer(invalid,image,evidence));
  }
  assert.throws(()=>evaluateContainer(report([{...finding,Severity:'critical'}]),image,evidence));
  assert.equal(evaluateContainer(report([{...finding,VulnerabilityID:'CVE-unclassified',Severity:'UNKNOWN'}]),image,evidence).ApplicationGate.blocking,1);
});
