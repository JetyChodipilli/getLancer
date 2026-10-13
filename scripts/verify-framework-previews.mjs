import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {gunzipSync} from 'node:zlib';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {buildFrameworkMaterial,frameworkSource,root,sha} from './build-framework-material.mjs';
/** Independently unpack every source member and compare it with current reviewed source. */
export function inspectFrameworkArchive(archive,expected){
 const bytes=gunzipSync(archive,{maxOutputLength:4194304}),wanted=new Map(expected),seen=new Set();let offset=0;
 while(offset+512<=bytes.length){const header=bytes.subarray(offset,offset+512);if(header.every(byte=>byte===0))break;const name=header.subarray(0,100).toString().split('\0')[0],size=parseInt(header.subarray(124,136).toString().replace(/\0.*$/,''),8),checksum=parseInt(header.subarray(148,156).toString().trim(),8);
  assert(wanted.has(name)&&!seen.has(name)&&Number.isSafeInteger(size)&&size>=0&&size<=262144&&header[156]===48,'Unsafe framework archive member');let actual=0;for(let i=0;i<512;i++)actual+=i>=148&&i<156?32:header[i];assert.equal(checksum,actual,'Framework tar checksum mismatch');assert(bytes.subarray(offset+512,offset+512+size).equals(wanted.get(name)),'Framework archive differs from current source');seen.add(name);offset+=512+Math.ceil(size/512)*512;
 }
 assert.equal(seen.size,wanted.size,'Framework archive omitted source');assert(bytes.subarray(offset).every(byte=>byte===0),'Foreign trailing archive bytes');return seen;
}
export async function verifyFrameworkPreviews({native=false}={}){
 const result=await buildFrameworkMaterial({native,verify:true}),material=JSON.parse(await readFile(resolve(root,'app/components/frameworks/material.json'),'utf8'));
 assert.equal(material.schemaVersion,'getlancer-native-framework-material-v1');assert.deepEqual(material.items.map(item=>item.id),['vue','svelte','angular']);
 for(const item of material.items){const source=await frameworkSource(item.id),archive=Buffer.from(item.archiveBase64,'base64');assert.equal(item.sourceSha256,source.sourceSha256);assert.equal(sha(archive),item.archiveSha256);assert.equal(sha(item.html),item.previewSha256);inspectFrameworkArchive(archive,source.files);
  assert(item.html.startsWith('<!doctype html><html lang="en">'),'Native document language missing');assert.equal((item.html.match(/<script>/g)||[]).length,1);assert.equal((item.html.match(/<style>/g)||[]).length,1);assert(!/<(?:iframe|link|img|form)[^>]*(?:src|action|href)=/i.test(item.html.replace(/<script>[\s\S]*?<\/script>/g,'').replace(/<style>[\s\S]*?<\/style>/g,'')),'Native document external resource');assert(item.policy.includes("connect-src 'none'")&&item.policy.includes('sandbox allow-scripts')&&!item.policy.includes('allow-same-origin')&&!item.policy.includes('unsafe-inline')&&!item.policy.includes('unsafe-eval'),'Preview isolation weakened');
 }
 return result;
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)){try{assert(process.argv.slice(2).every(arg=>arg==='--native'),'Unknown verifier argument');console.log('FRAMEWORK_PREVIEWS_OK '+JSON.stringify(await verifyFrameworkPreviews({native:process.argv.includes('--native')})));}catch(error){console.error('FRAMEWORK_PREVIEWS_REJECTED '+error.message);process.exitCode=1;}}
