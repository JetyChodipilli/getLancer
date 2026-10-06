import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {readFileSync,readdirSync,statSync} from 'node:fs';
import {join} from 'node:path';

const advisory='GHSA-pc63-qcmh-9cmg';
const coordinate='pkg:maven/org.springframework/spring-webmvc@6.2.19?type=jar';
const reviewedRevision='2026-10-05T23:30:04.884745Z';
const expiresAt='2026-11-05T00:00:00.000Z';
const digest=value=>createHash('sha256').update(value).digest('hex');

// This is an application-specific applicability assessment, never a general ignore list.
// Keep the match visible and require both current runtime and source evidence.
export function assessBackendFindings(findings,backendRoot,now=new Date()){
  return findings.map(finding=>{
    if(finding.id!==advisory||finding.purl!==coordinate) return finding;
    assert.equal(finding.modified,reviewedRevision,'Spring advisory changed; reassess its applicability.');
    assert.ok(now.getTime()<Date.parse(expiresAt),'Spring applicability assessment expired; reassess or upgrade.');
    const reportPath=join(backendRoot,'target/surefire-reports/TEST-com.getlancer.integration.ComponentsIntegrationTest.xml');
    const report=readFileSync(reportPath,'utf8');
    const testcase=report.match(/<testcase\b(?=[^>]*\bname="mvcHandlersDoNotExposeXsltViewRendering")(?=[^>]*\bclassname="com\.getlancer\.integration\.ComponentsIntegrationTest")[^>]*(?:\/>|>[\s\S]*?<\/testcase>)/)?.[0];
    assert.ok(testcase&&!/<(?:failure|error|skipped)\b/.test(testcase),'Require a passing real MVC applicability test.');
    const reportTime=statSync(reportPath).mtimeMs,evidence=[];
    assert.ok(reportTime<=now.getTime()&&now.getTime()-reportTime<=60*60*1000,'Require MVC test evidence from the last hour with no future timestamp.');
    for(const relative of ['pom.xml','src/test/java/com/getlancer/integration/ComponentsIntegrationTest.java']){
      const path=join(backendRoot,relative);
      assert.ok(statSync(path).mtimeMs<=reportTime,'MVC test evidence predates dependency or test inputs.');
      evidence.push({path,sha256:digest(readFileSync(path))});
    }
    function inspect(dir){
      for(const entry of readdirSync(dir,{withFileTypes:true})){
        const path=join(dir,entry.name);
        assert.ok(!entry.isSymbolicLink(),'Applicability source must not contain symlinks.');
        if(entry.isDirectory()){inspect(path);continue;}
        assert.ok(!/\.(xsl|xslt)$/i.test(path),'XSLT resource introduced; reassess Spring applicability.');
        const bytes=readFileSync(path);
        assert.ok(!/xslt/i.test(bytes.toString('utf8')),'XSLT configuration introduced; reassess Spring applicability.');
        assert.ok(statSync(path).mtimeMs<=reportTime,'MVC test evidence predates production source.');
        evidence.push({path,sha256:digest(bytes)});
      }
    }
    const configurationInputs=evidence.length;
    inspect(join(backendRoot,'src/main'));
    assert.ok(evidence.length-configurationInputs>50,'Refuse an empty or incomplete application source assessment.');
    return {...finding,disposition:{status:'not_affected',reason:'Required XSLT rendering is absent: application handlers return response bodies; no XSLT view/resolver beans, source configuration or resources.',reviewedAt:'2026-10-06',expiresAt,reference:'https://spring.io/security/cve-2026-47884/',evidence:{mvcTest:reportPath,mvcReportSha256:digest(report),verifiedInputs:evidence}}};
  });
}
