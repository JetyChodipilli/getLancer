// Scan public dependency coordinates, never application/user data or credentials.
import assert from 'node:assert/strict';
import {readFileSync,writeFileSync} from 'node:fs';
const [input,output]=process.argv.slice(2);assert.ok(input&&output,'Provide CycloneDX input and JSON report output.');
const bom=JSON.parse(readFileSync(input,'utf8')),components=bom.components||[];
const packages=components.filter(item=>/^pkg:(npm|maven)\//.test(item.purl||''));
assert.ok(packages.length>0,'Refuse to report an empty dependency scan as clean.');
const findings=[];
for(let offset=0;offset<packages.length;offset+=100){
 let queries=packages.slice(offset,offset+100).map(item=>({package:{purl:item.purl}}));
 for(let page=0;queries.length;page++){
  assert.ok(page<100,'OSV pagination exceeded the bounded scan.');
  const response=await fetch('https://api.osv.dev/v1/querybatch',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({queries}),signal:AbortSignal.timeout(30000)});
  assert.ok(response.ok,`OSV returned ${response.status}; scan incomplete.`);
  const data=await response.json();assert.equal(data.results?.length,queries.length,'Incomplete OSV result cohort.');
  const next=[];data.results.forEach((result,index)=>{assert.ok(!result.error,'OSV package lookup failed.');for(const vulnerability of result.vulns||[])findings.push({purl:queries[index].package.purl,id:vulnerability.id,modified:vulnerability.modified});if(result.next_page_token)next.push({...queries[index],page_token:result.next_page_token});});queries=next;
 }
}
writeFileSync(output,JSON.stringify({checkedAt:new Date().toISOString(),source:'https://api.osv.dev/v1/querybatch',input,packages:packages.length,findings,licenses:components.map(({purl,name,version,licenses})=>({purl,name,version,licenses:licenses||[],reviewRequired:!licenses?.length})),licenseApproval:'Operator compatibility review required; metadata is not approval.'},null,2)+'\n');
console.log(`OSV scanned ${packages.length} package versions: ${findings.length} known advisory matches. License metadata saved for review.`);
assert.equal(findings.length,0,'Known vulnerable dependencies require remediation or an explicit reviewed disposition.');
