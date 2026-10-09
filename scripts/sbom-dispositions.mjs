import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {readFileSync,readdirSync,openSync,closeSync,fstatSync,constants} from 'node:fs';
import {join} from 'node:path';
import {junitCases,requirePassingTest} from './junit-evidence.mjs';

const coordinate='pkg:maven/org.springframework/spring-webmvc@6.2.19?type=jar';
const assessments=new Map([
 ['GHSA-pc63-qcmh-9cmg',{modified:'2026-10-05T23:30:04.884745Z',test:'mvcHandlersDoNotExposeXsltViewRendering',feature:'XSLT',forbidden:/xslt/i,reviewedAt:'2026-10-06',reference:'https://spring.io/security/cve-2026-47884/',reason:'Required XSLT rendering is absent: application handlers return response bodies; no XSLT view/resolver beans, source configuration or resources.'}],
 ['GHSA-j9f9-w8pj-32f8',{modified:'2026-10-07T13:30:04.802772Z',test:'mvcHandlersDoNotExposeSseFragmentRendering',feature:'SSE fragment rendering',forbidden:/SseEmitter|ResponseBodyEmitter|StreamingResponseBody|FragmentsRendering|TEXT_EVENT_STREAM|text\/event-stream|org\.springframework\.web\.reactive|reactor\.core/i,reviewedAt:'2026-10-09',reference:'https://spring.io/security/cve-2026-47890/',reason:'Required SSE view-fragment rendering is absent. The sole reviewed event-stream handler returns a finite owner-authorized JSON String, with current runtime regression proof; no streaming emitter, view fragment or WebFlux renderer is allowed.'}]
]);
const expiresAt='2026-11-05T00:00:00.000Z';
const digest=value=>createHash('sha256').update(value).digest('hex');
// Only this exact finite response is reviewed; any other SSE source still fails closed.
const finiteReplay='@GetMapping("/lab-runs/{id}/events") public ResponseEntity<String> events(@PathVariable UUID id,@RequestHeader(value="Last-Event-ID",required=false) String cursor,HttpServletRequest request){return ResponseEntity.ok().header("Content-Type","text/event-stream; charset=UTF-8").header("Cache-Control","private, no-store").header("X-Accel-Buffering","no").body(service.events(id,cursor,request));}';
function readEvidence(path){
  const descriptor=openSync(path,constants.O_RDONLY|constants.O_NOFOLLOW);
  try{
    const stat=fstatSync(descriptor);
    assert.ok(stat.isFile(),'Applicability evidence must be a regular file.');
    return {bytes:readFileSync(descriptor),mtimeMs:stat.mtimeMs};
  }finally{closeSync(descriptor);}
}

// This is an application-specific applicability assessment, never a general ignore list.
// Keep the match visible and require both current runtime and source evidence.
export function assessBackendFindings(findings,backendRoot,now=new Date()){
  return findings.map(finding=>{
    const assessment=assessments.get(finding.id);
    if(!assessment||finding.purl!==coordinate) return finding;
    assert.equal(finding.modified,assessment.modified,'Spring advisory changed; reassess its applicability.');
    assert.ok(now.getTime()<Date.parse(expiresAt),'Spring applicability assessment expired; reassess or upgrade.');
    const reportPath=join(backendRoot,'target/surefire-reports/TEST-com.getlancer.integration.ComponentsIntegrationTest.xml');
    const reportFile=readEvidence(reportPath),report=reportFile.bytes.toString('utf8');
    requirePassingTest(junitCases(reportFile.bytes),'com.getlancer.integration.ComponentsIntegrationTest',assessment.test);
    const reportTime=reportFile.mtimeMs,evidence=[];
    assert.ok(reportTime<=now.getTime()&&now.getTime()-reportTime<=60*60*1000,'Require MVC test evidence from the last hour with no future timestamp.');
    for(const relative of ['pom.xml','src/test/java/com/getlancer/integration/ComponentsIntegrationTest.java']){
      const path=join(backendRoot,relative);
      const input=readEvidence(path);
      assert.ok(input.mtimeMs<=reportTime,'MVC test evidence predates dependency or test inputs.');
      evidence.push({path,sha256:digest(input.bytes)});
    }
    function inspect(dir){
      for(const entry of readdirSync(dir,{withFileTypes:true})){
        const path=join(dir,entry.name);
        assert.ok(!entry.isSymbolicLink(),'Applicability source must not contain symlinks.');
        if(entry.isDirectory()){inspect(path);continue;}
        assert.ok(!/\.(xsl|xslt)$/i.test(path),'XSLT resource introduced; reassess Spring applicability.');
        const input=readEvidence(path),bytes=input.bytes;
        let source=bytes.toString('utf8');
        if(assessment.feature==='SSE fragment rendering'&&path===join(backendRoot,'src/main/java/com/getlancer/labs/LabController.java')&&source.includes(finiteReplay)){
          const labReportPath=join(backendRoot,'target/surefire-reports/TEST-com.getlancer.integration.LabsIntegrationTest.xml');
          const labReport=readEvidence(labReportPath),labTestPath=join(backendRoot,'src/test/java/com/getlancer/integration/LabsIntegrationTest.java'),labTest=readEvidence(labTestPath);
          requirePassingTest(junitCases(labReport.bytes),'com.getlancer.integration.LabsIntegrationTest','finiteOwnerReplayUsesJsonFramesWithoutFragmentRendering');
          assert.ok(labReport.mtimeMs<=now.getTime()&&now.getTime()-labReport.mtimeMs<=60*60*1000,'Require current finite replay test evidence.');
          assert.ok(labTest.mtimeMs<=labReport.mtimeMs,'Finite replay test evidence predates its inputs.');
          evidence.push({path:labReportPath,sha256:digest(labReport.bytes)},{path:labTestPath,sha256:digest(labTest.bytes)});
          source=source.replace(finiteReplay,'');
        }
        assert.ok(!assessment.forbidden.test(source),assessment.feature+' configuration introduced; reassess Spring applicability.');
        assert.ok(input.mtimeMs<=reportTime,'MVC test evidence predates production source.');
        evidence.push({path,sha256:digest(bytes)});
      }
    }
    const configurationInputs=evidence.length;
    inspect(join(backendRoot,'src/main'));
    assert.ok(evidence.length-configurationInputs>50,'Refuse an empty or incomplete application source assessment.');
    return {...finding,disposition:{status:'not_affected',reason:assessment.reason,reviewedAt:assessment.reviewedAt,expiresAt,reference:assessment.reference,evidence:{mvcTest:reportPath,mvcTestCase:assessment.test,mvcReportSha256:digest(report),verifiedInputs:evidence}}};
  });
}
