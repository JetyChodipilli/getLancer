import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync,mkdirSync,writeFileSync,readFileSync,readdirSync,utimesSync,rmSync} from 'node:fs';
import {join} from 'node:path';
import {tmpdir} from 'node:os';
import {assessBackendFindings} from '../scripts/sbom-dispositions.mjs';

const finding={id:'GHSA-pc63-qcmh-9cmg',purl:'pkg:maven/org.springframework/spring-webmvc@6.2.19?type=jar',modified:'2026-10-05T23:30:04.884745Z'};
const now=new Date('2026-10-06T12:00:00Z');
function fixture(t,clock=now){
 const root=mkdtempSync(join(tmpdir(),'getlancer-mvc-assessment-'));
 t.after(()=>rmSync(root,{recursive:true,force:true}));
 mkdirSync(join(root,'src/main'),{recursive:true});
 mkdirSync(join(root,'src/test/java/com/getlancer/integration'),{recursive:true});
 mkdirSync(join(root,'target/surefire-reports'),{recursive:true});
 for(let i=0;i<51;i++)writeFileSync(join(root,`src/main/Controller${i}.java`),'@RestController class Example {}');
 const report=join(root,'target/surefire-reports/TEST-com.getlancer.integration.ComponentsIntegrationTest.xml');
 const passed='<testsuite><testcase name="mvcHandlersDoNotExposeXsltViewRendering" classname="com.getlancer.integration.ComponentsIntegrationTest" time="0.1"/></testsuite>';
 writeFileSync(join(root,'pom.xml'),'<project/>');
 writeFileSync(join(root,'src/test/java/com/getlancer/integration/ComponentsIntegrationTest.java'),'class ComponentsIntegrationTest {}');
 const saveReport=evidence=>{
  // Keep fixture clocks independent of when CI runs; the assessment date is fixed.
  const before=new Date(clock.getTime()-1000);
  for(const relative of ['pom.xml',...readdirSync(join(root,'src/test'),{recursive:true}).map(name=>'src/test/'+name),...readdirSync(join(root,'src/main'),{recursive:true}).map(name=>'src/main/'+name)])utimesSync(join(root,relative),before,before);
  writeFileSync(report,evidence);utimesSync(report,clock,clock);
 };
 saveReport(passed);
 return {root,report,passed,saveReport};
}
test('applicability retains exact advisory and hashed evidence while other matches remain blocking',t=>{
 const {root}=fixture(t),unknown={...finding,id:'GHSA-other'},otherVersion={...finding,purl:finding.purl.replace('6.2.19','6.2.18')};
 const result=assessBackendFindings([finding,unknown,otherVersion],root,now);
 assert.equal(result.length,3);assert.equal(result[0].id,finding.id);assert.equal(result[0].disposition.status,'not_affected');
 assert.equal(result[0].disposition.evidence.verifiedInputs.length,53);
 assert.match(result[0].disposition.evidence.mvcReportSha256,/^[a-f0-9]{64}$/);
 assert.deepEqual(result.slice(1),[unknown,otherVersion]);
 assert.equal(result.filter(item=>item.disposition?.status!=='not_affected').length,2);
});
test('changed advisories and expired assessments fail closed',t=>{
 const {root}=fixture(t);
 assert.throws(()=>assessBackendFindings([{...finding,modified:'2026-10-07T00:00:00Z'}],root,now),/advisory changed/);
 assert.throws(()=>assessBackendFindings([finding],root,new Date('2026-11-05T00:00:00Z')),/expired/);
});
test('missing, failed, skipped and wrong-class MVC test evidence cannot authorize assessment',t=>{
 const {root,report,passed,saveReport}=fixture(t);
 for(const evidence of ['',passed.replace('/>','><failure/></testcase>'),passed.replace('/>','><error/></testcase>'),passed.replace('/>','><skipped/></testcase>'),passed.replace('com.getlancer.integration.ComponentsIntegrationTest','fake.Test')]){
  saveReport(evidence);assert.throws(()=>assessBackendFindings([finding],root,now),/JUnit|security regression/);
 }
 rmSync(report);assert.throws(()=>assessBackendFindings([finding],root,now),/ENOENT/);
});
test('logged or commented MVC testcase strings are not passing test elements',t=>{
 const {root,passed,saveReport}=fixture(t);
 saveReport('<testsuite><testcase name="unrelated" classname="Unrelated"><system-out><![CDATA['+passed+']]></system-out></testcase><!--'+passed+'--></testsuite>');
 assert.throws(()=>assessBackendFindings([finding],root,now),/security regression/);
});
test('XSLT configuration and resources invalidate applicability',t=>{
 const {root,passed,saveReport}=fixture(t),source=join(root,'src/main/Controller0.java');
 writeFileSync(source,'new XsltViewResolver()');saveReport(passed);
 assert.throws(()=>assessBackendFindings([finding],root,now),/XSLT configuration/);
 writeFileSync(source,'@RestController class Example {}');writeFileSync(join(root,'src/main/template.xsl'),'<stylesheet/>');saveReport(passed);
 assert.throws(()=>assessBackendFindings([finding],root,now),/XSLT resource/);
});
test('source changes after MVC test and incomplete sources invalidate applicability',t=>{
 const {root,report}=fixture(t);
 const source=join(root,'src/main/Controller0.java'),later=new Date(now.getTime()+1000);
 utimesSync(source,later,later);
 assert.throws(()=>assessBackendFindings([finding],root,now),/predates production source/);
 rmSync(join(root,'src/main'),{recursive:true});mkdirSync(join(root,'src/main'));
 assert.throws(()=>assessBackendFindings([finding],root,now),/empty or incomplete/);
});
test('old, future and dependency or test changes cannot reuse passing MVC evidence',t=>{
 const {root,report,passed,saveReport}=fixture(t);
 for(const timestamp of [new Date(now.getTime()-60*60*1000-1),new Date(now.getTime()+1000)]){
  utimesSync(report,timestamp,timestamp);assert.throws(()=>assessBackendFindings([finding],root,now),/last hour/);
 }
 saveReport(passed);
 for(const relative of ['pom.xml','src/test/java/com/getlancer/integration/ComponentsIntegrationTest.java']){
  const path=join(root,relative),later=new Date(now.getTime()+1000);
  utimesSync(path,later,later);assert.throws(()=>assessBackendFindings([finding],root,now),/dependency or test inputs/);
  utimesSync(path,new Date(now.getTime()-1000),new Date(now.getTime()-1000));
 }
});
const sse={...finding,id:'GHSA-j9f9-w8pj-32f8',modified:'2026-10-07T13:30:04.802772Z'};
const sseNow=new Date('2026-10-08T12:00:00Z');
test('SSE assessment needs its own passing runtime proof and exact advisory revision',t=>{
 const {root,passed,saveReport}=fixture(t,sseNow),proof=passed.replace('mvcHandlersDoNotExposeXsltViewRendering','mvcHandlersDoNotExposeSseFragmentRendering');
 assert.throws(()=>assessBackendFindings([sse],root,sseNow),/security regression/);
 saveReport(proof);
 const result=assessBackendFindings([sse],root,sseNow)[0];
 assert.equal(result.disposition.status,'not_affected');
 assert.equal(result.disposition.evidence.mvcTestCase,'mvcHandlersDoNotExposeSseFragmentRendering');
 assert.throws(()=>assessBackendFindings([{...sse,modified:'2026-10-08T00:00:00Z'}],root,sseNow),/advisory changed/);
 for(const invalid of [proof.replace('/>','><failure/></testcase>'),proof.replace('/>','><skipped/></testcase>'),'<testsuite><!--'+proof+'--></testsuite>']){
  saveReport(invalid);assert.throws(()=>assessBackendFindings([sse],root,sseNow),/security regression/);
 }
});
test('adding any SSE or reactive streaming configuration invalidates the assessment',t=>{
 const {root,passed,saveReport}=fixture(t,sseNow),proof=passed.replace('mvcHandlersDoNotExposeXsltViewRendering','mvcHandlersDoNotExposeSseFragmentRendering');
 for(const source of ['new SseEmitter()','new ResponseBodyEmitter()','StreamingResponseBody','FragmentsRendering','TEXT_EVENT_STREAM','text/event-stream','org.springframework.web.reactive','reactor.core']){
  writeFileSync(join(root,'src/main/Controller0.java'),source);saveReport(proof);
  assert.throws(()=>assessBackendFindings([sse],root,sseNow),/SSE fragment rendering configuration/);
 }
});
test('only the exact finite lab replay with current passing owner/JSON proof can qualify',t=>{
 const {root,passed,saveReport}=fixture(t,sseNow),proof=passed.replace('mvcHandlersDoNotExposeXsltViewRendering','mvcHandlersDoNotExposeSseFragmentRendering');
 const dir=join(root,'src/main/java/com/getlancer/labs');mkdirSync(dir,{recursive:true});
 const source=readFileSync(new URL('../backend/src/main/java/com/getlancer/labs/LabController.java',import.meta.url),'utf8'),path=join(dir,'LabController.java');writeFileSync(path,source);
 writeFileSync(join(root,'src/test/java/com/getlancer/integration/LabsIntegrationTest.java'),'class LabsIntegrationTest {}');saveReport(proof);
 const report=join(root,'target/surefire-reports/TEST-com.getlancer.integration.LabsIntegrationTest.xml');
 const labProof='<testsuite><testcase name="finiteOwnerReplayUsesJsonFramesWithoutFragmentRendering" classname="com.getlancer.integration.LabsIntegrationTest" time="0.1"/></testsuite>';
 const saveLab=value=>{writeFileSync(report,value);utimesSync(report,sseNow,sseNow);};
 assert.throws(()=>assessBackendFindings([sse],root,sseNow),/ENOENT/);saveLab(labProof);
 assert.equal(assessBackendFindings([sse],root,sseNow)[0].disposition.status,'not_affected');
 for(const invalid of [labProof.replace('/>','><failure/></testcase>'),labProof.replace('/>','><skipped/></testcase>'),labProof.replace('com.getlancer.integration.LabsIntegrationTest','fake.Test')]){saveLab(invalid);assert.throws(()=>assessBackendFindings([sse],root,sseNow),/security regression/);}
 saveLab(labProof);utimesSync(report,new Date(sseNow.getTime()-3600001),new Date(sseNow.getTime()-3600001));assert.throws(()=>assessBackendFindings([sse],root,sseNow),/current finite replay/);saveLab(labProof);
 for(const changed of [source.replace('ResponseEntity<String> events','ResponseEntity<Object> events'),source.replace('body(service.events(id,cursor,request))','body(otherService.events(id,cursor,request))'),source+'\ntext/event-stream',source+'\nnew SseEmitter()']){
  writeFileSync(path,changed);saveReport(proof);assert.throws(()=>assessBackendFindings([sse],root,sseNow),/SSE fragment rendering configuration/);
 }
});
