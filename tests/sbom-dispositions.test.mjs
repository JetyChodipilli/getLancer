import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync,mkdirSync,writeFileSync,utimesSync,rmSync} from 'node:fs';
import {join} from 'node:path';
import {tmpdir} from 'node:os';
import {assessBackendFindings} from '../scripts/sbom-dispositions.mjs';

const finding={id:'GHSA-pc63-qcmh-9cmg',purl:'pkg:maven/org.springframework/spring-webmvc@6.2.19?type=jar',modified:'2026-10-05T23:30:04.884745Z'};
const now=new Date('2026-10-06T12:00:00Z');
function fixture(t){
 const root=mkdtempSync(join(tmpdir(),'getlancer-mvc-assessment-'));
 t.after(()=>rmSync(root,{recursive:true,force:true}));
 mkdirSync(join(root,'src/main'),{recursive:true});
 mkdirSync(join(root,'src/test/java/com/getlancer/integration'),{recursive:true});
 mkdirSync(join(root,'target/surefire-reports'),{recursive:true});
 for(let i=0;i<51;i++)writeFileSync(join(root,`src/main/Controller${i}.java`),'@RestController class Example {}');
 const report=join(root,'target/surefire-reports/TEST-com.getlancer.integration.ComponentsIntegrationTest.xml');
 const passed='<testcase name="mvcHandlersDoNotExposeXsltViewRendering" classname="com.getlancer.integration.ComponentsIntegrationTest" time="0.1"/>';
 writeFileSync(join(root,'pom.xml'),'<project/>');
 writeFileSync(join(root,'src/test/java/com/getlancer/integration/ComponentsIntegrationTest.java'),'class ComponentsIntegrationTest {}');
 const saveReport=evidence=>{writeFileSync(report,evidence);utimesSync(report,now,now);};
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
  saveReport(evidence);assert.throws(()=>assessBackendFindings([finding],root,now),/passing real MVC/);
 }
 rmSync(report);assert.throws(()=>assessBackendFindings([finding],root,now),/ENOENT/);
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
