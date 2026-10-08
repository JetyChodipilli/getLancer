import assert from 'node:assert/strict';
import {readFileSync,writeFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
import {assessBackendFindings} from './sbom-dispositions.mjs';

const advisories=new Map([['CVE-2026-47884','GHSA-pc63-qcmh-9cmg'],['CVE-2026-47890','GHSA-j9f9-w8pj-32f8']]);
const coordinate='pkg:maven/org.springframework/spring-webmvc@6.2.19?type=jar';
const critical=new Set(['HIGH','CRITICAL']);
const severities=new Set(['UNKNOWN','LOW','MEDIUM','HIGH','CRITICAL']);
const nonempty=value=>typeof value==='string'&&value.trim().length>0;

// Keep every scanner finding visible. Only this exact application-specific assessment
// can dispose the reviewed Spring findings; there is no CVE ignore file.
export function evaluateContainer(report,expectedImage,assessment){
  const assessments=Array.isArray(assessment)?assessment:assessment?[assessment]:[];
  assert.equal(report.SchemaVersion,2,'Unsupported or incomplete image scan.');
  assert.equal(report.ArtifactName,expectedImage,'Scan must describe the reviewed image.');
  assert.equal(report.ArtifactType,'container_image','Require a runtime image scan.');
  assert.ok(Array.isArray(report.Results)&&report.Results.length,'Refuse an empty image scan.');
  for(const result of report.Results){
    assert.ok(result&&nonempty(result.Target)&&nonempty(result.Type)
        &&['os-pkgs','lang-pkgs'].includes(result.Class),'Incomplete package scan result.');
    assert.ok(result.Vulnerabilities===undefined||Array.isArray(result.Vulnerabilities),
        'Invalid vulnerability collection.');
    for(const finding of result.Vulnerabilities||[])
      assert.ok(finding&&nonempty(finding.VulnerabilityID)&&nonempty(finding.PkgName)
          &&nonempty(finding.InstalledVersion)&&severities.has(finding.Severity),
          'Incomplete vulnerability evidence.');
  }
  assert.ok(report.Results.some(result=>result.Class==='os-pkgs'),
      'Runtime image scan must include operating system packages.');
  let blocking=0,notAffected=0;
  const Results=report.Results.map(result=>({...result,Vulnerabilities:(result.Vulnerabilities||[]).map(finding=>{
    if(!critical.has(finding.Severity)&&finding.Severity!=='UNKNOWN')return finding;
    const reviewed=assessments.find(item=>item.id===advisories.get(finding.VulnerabilityID)&&item.purl===coordinate);
    if(expectedImage==='getlancer-api:scan'&&result.Type==='jar'
        &&advisories.has(finding.VulnerabilityID)
        &&finding.PkgName==='org.springframework:spring-webmvc'&&finding.InstalledVersion==='6.2.19'
        &&reviewed?.disposition?.status==='not_affected'){
      notAffected++;
      return {...finding,ApplicationDisposition:reviewed.disposition};
    }
    blocking++;
    return finding;
  })}));
  return {...report,Results,ApplicationGate:{checkedAt:new Date().toISOString(),blocking,notAffected}};
}

function currentBackendAssessment(){
  const root=resolve('backend');
  const evidence=JSON.parse(readFileSync(resolve(root,'target/dependency-audit.json'),'utf8'));
  assert.equal(evidence.source,'https://api.osv.dev/v1/querybatch');
  assert.equal(evidence.blockingFindings,0,'Java dependency gate did not pass.');
  assert.ok(!evidence.assessmentError,'Java applicability assessment failed.');
  const age=Date.now()-Date.parse(evidence.checkedAt);
  assert.ok(Number.isFinite(age)&&age>=0&&age<=60*60*1000,'Require current dependency evidence.');
  const findings=evidence.findings.filter(item=>[...advisories.values()].includes(item.id)&&item.purl===coordinate);
  assert.equal(new Set(findings.map(item=>item.id)).size,findings.length,'Duplicate applicability evidence.');
  return findings.map(finding=>{
  assert.equal(finding.disposition?.status,'not_affected');
  const fresh=assessBackendFindings([finding],root)[0];
  assert.equal(fresh.disposition.evidence.mvcReportSha256,finding.disposition.evidence.mvcReportSha256,
      'Image assessment requires the same passing real MVC test report.');
  const relative=path=>path.slice(path.lastIndexOf('/backend/')+9);
  const previous=new Map(finding.disposition.evidence.verifiedInputs.map(item=>[relative(item.path),item.sha256]));
  assert.equal(previous.size,fresh.disposition.evidence.verifiedInputs.length);
  for(const item of fresh.disposition.evidence.verifiedInputs)
    assert.equal(previous.get(relative(item.path)),item.sha256,'Image source differs from tested assessment inputs.');
  return fresh;
  });
}

if(process.argv[1]&&pathToFileURL(resolve(process.argv[1])).href===import.meta.url){
  const [input,output,image,assessment]=process.argv.slice(2);
  assert.ok(input&&output&&['getlancer-api:scan','getlancer-publisher:scan'].includes(image));
  assert.ok(!assessment||assessment==='--backend-rest-assessment');
  assert.ok(!assessment||image==='getlancer-api:scan');
  const report=evaluateContainer(JSON.parse(readFileSync(input,'utf8')),image,
      assessment?currentBackendAssessment():undefined);
  writeFileSync(output,JSON.stringify(report,null,2)+'\n');
  console.log(`Image gate: ${report.ApplicationGate.blocking} blocking high/critical findings; ${report.ApplicationGate.notAffected} verified not applicable. Full findings retained.`);
  assert.equal(report.ApplicationGate.blocking,0,'Runtime image findings require remediation or exact reviewed applicability evidence.');
}
