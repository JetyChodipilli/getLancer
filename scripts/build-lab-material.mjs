/** Deterministic, explicit source inventory. Never package environment files or build output. */
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {readFile,writeFile,mkdir,lstat,access} from 'node:fs/promises';
import {gzipSync,gunzipSync} from 'node:zlib';
import {dirname,join,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

const root=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const verify=process.argv.includes('--verify');
assert(process.argv.slice(2).every(value=>value==='--verify'),'Unknown material builder argument');
const sha=bytes=>createHash('sha256').update(bytes).digest('hex');
const catalogue=JSON.parse(await readFile(join(root,'labs/catalogue.json'),'utf8'));
const publicRoot=join(root,'public/labs');
const {parseMaterialIndex,parseScenarioRecording}=await import('../lib/scenario-evidence.ts');
async function exists(path){try{await access(path);return true;}catch(error){if(error.code==='ENOENT')return false;throw error;}}
async function source(path){assert(/^[A-Za-z0-9_./-]+$/.test(path)&&!path.split('/').includes('..'),'Invalid package path');const stat=await lstat(join(root,path));assert(stat.isFile()&&!stat.isSymbolicLink()&&stat.size<=131072,'Package inventory must be bounded regular source files');return readFile(join(root,path));}
function tar(files){
 const parts=[];
 for(const [path,bytes] of files){assert(Buffer.byteLength(path)<100);const header=Buffer.alloc(512);header.write(path,0,100,'utf8');header.write('0000644\0',100);header.write('0000000\0',108);header.write('0000000\0',116);header.write(bytes.length.toString(8).padStart(11,'0')+'\0',124);header.write('00000000000\0',136);header.fill(32,148,156);header[156]=48;header.write('ustar\0',257);header.write('00',263);let checksum=0;for(const byte of header)checksum+=byte;header.write(checksum.toString(8).padStart(6,'0')+'\0 ',148);parts.push(header,bytes,Buffer.alloc((512-bytes.length%512)%512));
 }
 return gzipSync(Buffer.concat([...parts,Buffer.alloc(1024)]),{level:9});
}
function verifyTar(archive,files){
 const bytes=gunzipSync(archive,{maxOutputLength:1048576});let offset=0;const expected=new Map(files),found=new Set();
 while(offset+512<=bytes.length){const header=bytes.subarray(offset,offset+512);if(header.every(value=>value===0))break;const path=header.subarray(0,100).toString().split('\0')[0],size=parseInt(header.subarray(124,136).toString().replace(/\0.*$/,''),8);assert(expected.has(path)&&!found.has(path),'Archive has a foreign or duplicate path');assert.equal(header[156],48);const recorded=parseInt(header.subarray(148,156).toString().trim(),8);let sum=0;for(let i=0;i<512;i++)sum+=i>=148&&i<156?32:header[i];assert.equal(recorded,sum,'Archive header checksum mismatch');assert.deepEqual(bytes.subarray(offset+512,offset+512+size),expected.get(path),'Archive source differs');found.add(path);offset+=512+Math.ceil(size/512)*512;}
 assert.equal(found.size,expected.size,'Archive omitted a declared source');
}
async function publish(path,bytes){if(verify){assert.deepEqual(await readFile(path),bytes,'Material drift: '+path);}else{await mkdir(dirname(path),{recursive:true});await writeFile(path,bytes);}}
const items=[];
for(const [language,definition] of Object.entries(catalogue.languages)){
 const paths=['labs/README.md','labs/catalogue.json','labs/fixtures.json','docs/v48/CONTRACT.md','scripts/verify-lab-scenarios.mjs',definition.primary,'labs/'+language+'/README.md'].sort();
 const files=await Promise.all(paths.map(async path=>[path,await source(path)]));const archive=tar(files);verifyTar(archive,files);
 const sourceHash=sha(await source(definition.primary)),archiveHash=sha(archive);
 await publish(join(publicRoot,'sources',language+'.tar.gz'),archive);
 for(const [pattern,content] of Object.entries(catalogue.patterns)){
  const id=language+'-'+pattern,path=join(root,'labs/recordings',id+'.json');let recording=null;
  const item={id,language,pattern,title:definition.label+' · '+content.title,summary:content.summary,sourceHash,archiveHash,sourceUrl:'/labs/sources/'+language+'.tar.gz',recordingUrl:null,setup:definition.requires+'; actual Redis 7.2.16 on 127.0.0.1:6379. From the extracted package root:\nLAB_RUN_ID=00000000-0000-4000-8000-000000000001 LAB_PORT=8100 '+definition.command+'\n\nVerify: REDIS_SERVER=/path/to/redis-server node scripts/verify-lab-scenarios.mjs --language '+language,scenarios:content.scenarios,edges:content.edges};
  if(await exists(path)){recording=await readFile(path);parseScenarioRecording(recording.toString('utf8'),item);item.recordingUrl='/labs/recordings/'+id+'.json';await publish(join(publicRoot,'recordings',id+'.json'),recording);}
  items.push(item);
 }
}
assert.equal(items.length,9,'The language/pattern matrix is incomplete');parseMaterialIndex({items});
await publish(join(publicRoot,'material-index.json'),Buffer.from(JSON.stringify({items},null,2)+'\n'));
await publish(join(publicRoot,'License.md'),await source('docs/v48/License.md'));
console.log('LAB_MATERIAL_OK items='+items.length+' recordings='+items.filter(item=>item.recordingUrl).length);
