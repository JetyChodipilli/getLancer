/** Fixed free source inventory; deterministic tar/gzip, actual execution before publication. */
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {readFile,writeFile,mkdir,lstat} from 'node:fs/promises';
import {gzipSync,gunzipSync} from 'node:zlib';
import {dirname,join,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
export const root=resolve(dirname(fileURLToPath(import.meta.url)),'..');
export const base=join(root,'labs/data-ai');
export const publicBase=join(root,'public/data-ai');
export const sha=bytes=>createHash('sha256').update(bytes).digest('hex');
export const inventory=['DATA_LICENSE.md','License.md','MODEL_LICENSE.md','README.md','catalogue.json','fixtures/equipment-inference-evaluation.json','fixtures/equipment-inference-inputs.json','fixtures/revenue.json','fixtures/sensors.json','fixtures/sentiment-inference-evaluation.json','fixtures/sentiment-inference-inputs.json','models/equipment-inference.json','models/sentiment-inference.json','runtime.py'].sort();
export const setup='Python 3.12+ standard library only. Extract the free archive; from its root run LAB_RUN_ID=00000000-0000-4000-8000-000000000001 LAB_PORT=8105 python3 runtime.py. Use GET /health and exact form POST /request, operationId=execute. Source README/catalogue document bounded inputs and units. Local process limits do not certify hosted isolation.';

/** Preserve duplicate JSON keys for rejection, including escaped aliases and __proto__. */
export function parseStrictJson(raw){
 assert(typeof raw==='string'&&Buffer.byteLength(raw)<=65536,'Evidence JSON exceeds byte budget');let at=0,nodes=0;
 const whitespace=()=>{while(/[ \n\r\t]/.test(raw[at]??'!'))at++;};
 function string(){const start=at++;while(at<raw.length){const ch=raw[at++];if(ch==='\\'){at++;continue;}if(ch==='"')return JSON.parse(raw.slice(start,at));}throw Error('Incomplete JSON string');}
 function value(depth){assert(depth<=16&&++nodes<=3000,'Evidence exceeds structural budget');whitespace();const ch=raw[at];
  if(ch==='"')return string();
  if(ch==='{'){at++;whitespace();const object={},keys=new Set();if(raw[at]==='}'){at++;return object;}while(true){whitespace();assert.equal(raw[at],'"');const key=string();assert(!keys.has(key),'Duplicate JSON key');keys.add(key);whitespace();assert.equal(raw[at++],':');Object.defineProperty(object,key,{value:value(depth+1),enumerable:true,writable:true,configurable:true});whitespace();const end=raw[at++];if(end==='}')return object;assert.equal(end,',');}}
  if(ch==='['){at++;whitespace();const array=[];if(raw[at]===']'){at++;return array;}while(true){array.push(value(depth+1));whitespace();const end=raw[at++];if(end===']')return array;assert.equal(end,',');}}
  const token=/^(?:-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?|true|false|null)/.exec(raw.slice(at));assert(token,'Malformed JSON value');at+=token[0].length;const parsed=JSON.parse(token[0]);assert(typeof parsed!=='number'||Number.isFinite(parsed),'Non-finite JSON value');return parsed;
 }
 const result=value(0);whitespace();assert.equal(at,raw.length,'Trailing JSON data');return result;
}
export function validateCatalogue(catalogue){
 const exact=(value,keys)=>{assert(value&&typeof value==='object'&&!Array.isArray(value),'Catalogue object required');assert.deepEqual(Object.keys(value).sort(),keys.slice().sort(),'Catalogue schema drift');};
 const ids=['revenue-summary','sensor-quality','sentiment-inference','equipment-inference'];
 exact(catalogue,['schemaVersion','inventory','items','assets','contracts']);assert.equal(catalogue.schemaVersion,'getlancer-data-ai-catalogue-v1');assert.deepEqual(catalogue.items.map(item=>item.id),ids);exact(catalogue.assets,ids);exact(catalogue.contracts,ids);
 for(const item of catalogue.items){
  exact(item,['id','kind','title','summary','outputSchema','inputs','defaultInputs','changedInputs','dataLicense','dataProvenance','dataSha256','modelLicense','modelProvenance','modelSha256','modelFormat','evaluationSplit','evaluationProtocol','evaluationSha256','limitations','recordingUrl']);
  const analytic=ids.indexOf(item.id)<2;assert.equal(item.kind,analytic?'DATA_ANALYTICS':'AI_ML');assert.equal(item.outputSchema,'getlancer-data-ai-result-v1');assert.equal(item.dataLicense,'MIT');assert.equal(item.recordingUrl,'/data-ai/recordings/'+item.id+'.json');
  for(const key of ['title','summary','dataProvenance','limitations'])assert(typeof item[key]==='string'&&item[key].length>0&&item[key].length<=240,'Unbounded catalogue text');assert(/^[0-9a-f]{64}$/.test(item.dataSha256));
  const modelKeys=['modelLicense','modelProvenance','modelSha256','modelFormat','evaluationSplit','evaluationProtocol','evaluationSha256'];if(analytic)for(const key of modelKeys)assert.equal(item[key],null);else{assert.equal(item.modelLicense,'MIT');assert.equal(item.modelFormat,'JSON');assert.equal(item.evaluationSplit,'held-out-synthetic');assert.equal(item.evaluationProtocol,'frozen-model-no-training');assert(/^[0-9a-f]{64}$/.test(item.modelSha256)&&/^[0-9a-f]{64}$/.test(item.evaluationSha256));assert(typeof item.modelProvenance==='string'&&item.modelProvenance.length>0&&item.modelProvenance.length<=240);}
  assert(Array.isArray(item.inputs)&&item.inputs.length>=1&&item.inputs.length<=2);for(const input of item.inputs){exact(input,['name','label','type','required','maxLength','min','max','choices']);assert(['TEXT','INTEGER','ENUM'].includes(input.type)&&input.required===true&&Number.isSafeInteger(input.maxLength)&&input.maxLength>=1&&input.maxLength<=160&&Array.isArray(input.choices));}for(const key of ['defaultInputs','changedInputs']){exact(item[key],item.inputs.map(i=>i.name));assert(Object.values(item[key]).every(value=>typeof value==='string'&&value.length<=160));}
  const assets=catalogue.assets[item.id];exact(assets,['dataPath','modelPath','evaluationPath']);for(const value of Object.values(assets))assert(value===null||(typeof value==='string'&&inventory.includes(value)),'Foreign source inventory path');
  const contract=catalogue.contracts[item.id];exact(contract,['tables','metrics','classes']);assert(contract.tables.length===1&&contract.metrics.length>=2&&contract.metrics.length<=3);for(const table of contract.tables){exact(table,['id','title','columns']);assert(table.columns.length>=2&&table.columns.length<=4);for(const column of table.columns){exact(column,['key','label','unit','type']);assert(['number','string'].includes(column.type));}}for(const metric of contract.metrics)exact(metric,['key','label','unit']);assert(analytic?contract.classes===null:Array.isArray(contract.classes)&&contract.classes.length===2);
 }
 return catalogue;
}
export async function sourceFiles(){
 const catalogue=validateCatalogue(parseStrictJson(await readFile(join(base,'catalogue.json'),'utf8')));assert.deepEqual(catalogue.inventory,inventory,'Fixed inventory changed');
 for(const path of [base,join(base,'fixtures'),join(base,'models')])assert(!(await lstat(path)).isSymbolicLink(),'Unsafe source directory');
 const files=[];for(const name of inventory){const path=join(base,name),stat=await lstat(path);assert(stat.isFile()&&!stat.isSymbolicLink()&&stat.size<=65536,'Unsafe source file');files.push([name,await readFile(path)]);}return {catalogue,files};
}
export function archiveFor(files){
 const chunks=[];for(const [name,bytes] of files){assert(/^[A-Za-z0-9_./-]{1,99}$/.test(name)&&!name.startsWith('/')&&!name.split('/').includes('..'),'Unsafe archive name');const h=Buffer.alloc(512);h.write(name,0,100);h.write('0000644\0',100);h.write('0000000\0',108);h.write('0000000\0',116);h.write(bytes.length.toString(8).padStart(11,'0')+'\0',124);h.write('00000000000\0',136);h.fill(32,148,156);h[156]=48;h.write('ustar\0',257);h.write('00',263);let sum=0;for(const byte of h)sum+=byte;h.write(sum.toString(8).padStart(6,'0')+'\0 ',148);chunks.push(h,bytes,Buffer.alloc((512-bytes.length%512)%512));}return gzipSync(Buffer.concat([...chunks,Buffer.alloc(1024)]),{level:9});
}
export function unpackArchive(archive,files){
 const bytes=gunzipSync(archive,{maxOutputLength:1048576}),expected=new Map(files),found=new Map();let at=0;
 while(at+512<=bytes.length){const h=bytes.subarray(at,at+512);if(h.every(v=>v===0))break;const name=h.subarray(0,100).toString().split('\0')[0],size=parseInt(h.subarray(124,136).toString().replace(/\0.*$/,''),8),checksum=parseInt(h.subarray(148,156).toString().trim(),8);assert(expected.has(name)&&!found.has(name)&&Number.isSafeInteger(size)&&size<=65536&&h[156]===48,'Unsafe archive entry');let actual=0;for(let i=0;i<512;i++)actual+=i>=148&&i<156?32:h[i];assert.equal(checksum,actual,'Archive checksum mismatch');assert.equal(h.subarray(100,108).toString(),'0000644\0');assert.equal(h.subarray(108,116).toString(),'0000000\0');assert.equal(h.subarray(116,124).toString(),'0000000\0');assert.equal(h.subarray(136,148).toString(),'00000000000\0');const content=bytes.subarray(at+512,at+512+size);assert(content.equals(expected.get(name)),'Archive source drift: '+name);found.set(name,content);at+=512+Math.ceil(size/512)*512;}
 assert.equal(found.size,expected.size,'Archive omitted fixed source');assert(bytes.subarray(at).every(v=>v===0),'Archive trailing foreign data');return found;
}
export async function buildMaterial(verify=false){
 const {verifyRuntime,validateRecording}=await import('./verify-data-ai.mjs');
 // All execution, independent oracle, hostile parser and clean reproduction checks finish before any write.
 const checked=await verifyRuntime({requireRecordings:verify,validateStored:verify}),{catalogue,files}=await sourceFiles(),archive=archiveFor(files);unpackArchive(archive,files);const sourceSha256=sha(files.find(([n])=>n==='runtime.py')[1]),archiveSha256=sha(archive);
 const manifest=Buffer.from(JSON.stringify({schemaVersion:'getlancer-data-ai-build-manifest-v1',sourceSha256,archiveSha256},null,2)+'\n');
 const index={schemaVersion:'getlancer-data-ai-index-v1',sourceSha256,archiveSha256,sourceUrl:'/data-ai/source.tar.gz',setup,items:catalogue.items};
 const outputs=[['source.tar.gz',archive],['material-index.json',Buffer.from(JSON.stringify(index,null,2)+'\n')],...files.filter(([name])=>['License.md','DATA_LICENSE.md','MODEL_LICENSE.md'].includes(name))];
 for(const {item,recording} of checked.recordings){let bytes;if(verify){bytes=await readFile(join(base,'recordings',item.id+'.json'));validateRecording(parseStrictJson(bytes.toString('utf8')),item,sourceSha256,catalogue.contracts[item.id]);}else bytes=Buffer.from(JSON.stringify(recording,null,2)+'\n');outputs.push(['recordings/'+item.id+'.json',bytes]);}
 for(const [name,bytes] of outputs){const path=join(publicBase,name);if(verify)assert((await readFile(path)).equals(bytes),'Material drift: '+name);else{await mkdir(dirname(path),{recursive:true});await writeFile(path,bytes);}}
 if(verify)assert((await readFile(join(base,'evidence-manifest.json'))).equals(manifest),'Build-pinned evidence manifest drift');else await writeFile(join(base,'evidence-manifest.json'),manifest);
 if(!verify){await mkdir(join(base,'recordings'),{recursive:true});for(const [name,bytes] of outputs.filter(([n])=>n.startsWith('recordings/')))await writeFile(join(base,name),bytes);}
 return {items:4,sourceSha256,archiveSha256,archiveBytes:archive.length,measurements:checked.measurements};
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 try{assert(process.argv.slice(2).every(a=>a==='--verify'),'Unknown builder argument');buildMaterial(process.argv.includes('--verify')).then(result=>console.log('DATA_AI_MATERIAL_OK '+JSON.stringify(result))).catch(error=>{console.error('DATA_AI_MATERIAL_REJECTED '+error.message);process.exitCode=1;});}catch(error){console.error('DATA_AI_MATERIAL_REJECTED '+error.message);process.exitCode=1;}
}
