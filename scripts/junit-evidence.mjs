import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';

// Parse element identity, never strings embedded in system-out, CDATA or comments.
// Python's standard XML parser adds no npm runtime dependency to backend verification.
export function junitCases(bytes){
 assert.ok(bytes.length>0&&bytes.length<=16*1024*1024,'Invalid JUnit report size.');
 assert.ok(!/<!\s*(?:DOCTYPE|ENTITY)\b/i.test(bytes.toString('utf8')),'JUnit evidence cannot declare entities.');
 const parser='import sys,json,xml.etree.ElementTree as ET\nroot=ET.fromstring(sys.stdin.buffer.read())\nassert root.tag in ("testsuite","testsuites")\nprint(json.dumps([{"name":c.get("name"),"className":c.get("classname"),"failed":any(e.tag in ("failure","error","skipped") for e in c.iter())} for c in root.iter("testcase")]))';
 const result=spawnSync('python3',['-c',parser],{input:bytes,encoding:'utf8',timeout:10000,maxBuffer:1024*1024});
 assert.ok(!result.error&&result.status===0,'JUnit evidence is not valid completed XML.');
 return JSON.parse(result.stdout);
}
export function requirePassingTest(cases,className,name){
 const matches=cases.filter(test=>test.name===name&&test.className===className);
 assert.equal(matches.length,1,'Missing/ambiguous real security regression: '+name);
 assert.equal(matches[0].failed,false,'Required security regression did not pass: '+name);
}
