import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile,mkdtemp,cp,writeFile,rm,symlink,mkdir} from 'node:fs/promises';
import {spawnSync} from 'node:child_process';
import {connect} from 'node:net';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {randomUUID} from 'node:crypto';
import {parseStrictJson,validateCatalogue,sourceFiles,archiveFor,unpackArchive,sha,base} from '../scripts/build-data-ai-material.mjs';
import {canonical,digest,validateEnvelope,validateRecording,validateRecordedResults,startRuntime,send,verifyRuntime} from '../scripts/verify-data-ai.mjs';
const catalogue=parseStrictJson(await readFile(join(base,'catalogue.json'),'utf8'));
const sourceSha256=sha(await readFile(join(base,'runtime.py')));
const itemFor=id=>catalogue.items.find(item=>item.id===id);
const fields=(runtime,id,input)=>({runId:runtime.runId,scenarioId:id,operationId:'execute',...input});
function rawRequest(runtime,text){return new Promise((done,reject)=>{const socket=connect(runtime.port,'127.0.0.1'),chunks=[];socket.setTimeout(2500);socket.on('connect',()=>socket.write(text));socket.on('data',chunk=>chunks.push(chunk));socket.on('error',reject);socket.on('timeout',()=>socket.destroy(Error('Raw deadline exceeded')));socket.on('close',()=>done(Buffer.concat(chunks).toString()));});}

// Every negative has an original positive control; no source-text-only assertions stand in for execution.
test('all four original actual recordings validate; hash/identity/time/unknown-field corruption rejects',async()=>{
 for(const item of catalogue.items){const recording=parseStrictJson(await readFile(join(base,'recordings',item.id+'.json'),'utf8')),contract=catalogue.contracts[item.id];validateRecording(recording,item,sourceSha256,contract);await validateRecordedResults(recording,item,contract,catalogue.assets[item.id]);const reject=change=>{const bad=structuredClone(recording);change(bad);assert.throws(()=>validateRecording(bad,item,sourceSha256,contract));};reject(r=>r.sourceSha256='0'.repeat(64));reject(r=>r.runs[0].inputSha256='a'.repeat(64));reject(r=>r.runs[0].resultSha256='f'.repeat(64));reject(r=>r.runs[1].runId=randomUUID());reject(r=>r.runs[0].startedAt='2099-01-01T00:00:00.000Z');reject(r=>r.runs[0].result.privateAcademicAnnotation='not allowed');reject(r=>r.runs[0].result.metrics[0].value+=1);}
});

test('self-consistent forged analytics values fail independent pinned-byte numeric oracle',async()=>{
 const item=itemFor('revenue-summary'),contract=catalogue.contracts[item.id],recording=parseStrictJson(await readFile(join(base,'recordings',item.id+'.json'),'utf8'));const bad=structuredClone(recording),run=bad.runs[0];run.result.tables[0].rows.forEach(row=>row.revenue*=2);run.result.metrics[0].value*=2;run.result.metrics[2].value*=2;run.resultSha256=digest(run.result);validateRecording(bad,item,sourceSha256,contract);await assert.rejects(()=>validateRecordedResults(bad,item,contract,catalogue.assets[item.id]));
});

test('strict raw JSON preserves duplicate aliases, rejects excess and avoids prototype pollution',()=>{
 assert.deepEqual(parseStrictJson('{"a":{"b":1},"c":[true,null]}'),{a:{b:1},c:[true,null]});for(const value of ['{"mode":"REPLAY","mode":"LOCAL"}','{"nested":{"a":1,"\\u0061":2}}','{"n":1e999}','[1,]','{"n":NaN}','{"a":1}trailing','"unterminated','['.repeat(18)+'0'+']'.repeat(18),' '.repeat(65537)])assert.throws(()=>parseStrictJson(value));const object=parseStrictJson('{"__proto__":{"polluted":true}}');assert.equal(Object.getPrototypeOf(object),Object.prototype);assert.equal({}.polluted,undefined);
});

test('catalogue accepts originals and rejects private/executable/unbounded metadata before publication',()=>{
 validateCatalogue(catalogue);for(const change of [c=>c.items[0].privateStorageKey='secret',c=>c.items[2].modelFormat='pickle',c=>c.items[0].dataProvenance='x'.repeat(241),c=>c.assets['revenue-summary'].dataPath='/etc/passwd',c=>c.items[0].inputs[0].remoteUrl='https://example.invalid']){const mutated=structuredClone(catalogue);change(mutated);assert.throws(()=>validateCatalogue(mutated));}
});

test('free fourteen-file archive is deterministic; foreign names, omissions and corruption deny',async()=>{
 const {files}=await sourceFiles(),archive=archiveFor(files);assert(archive.equals(archiveFor(files)));assert.equal(unpackArchive(archive,files).size,14);for(const name of ['../env','/etc/passwd','fixtures/../../env'])assert.throws(()=>archiveFor([[name,Buffer.from('secret')]]));assert.throws(()=>unpackArchive(archiveFor(files.slice(1)),files));const corrupt=Buffer.from(archive);corrupt[Math.floor(corrupt.length/2)]^=64;assert.throws(()=>unpackArchive(corrupt,files));
});

test('changed fixtures, executable format, absolute paths and symlink parents reject startup',async t=>{
 const directory=await mkdtemp(join(tmpdir(),'getlancer-data-ai-hostile-source-'));t.after(()=>rm(directory,{recursive:true,force:true}));const restore=async()=>{await rm(directory,{recursive:true,force:true});await cp(base,directory,{recursive:true});};const run=()=>spawnSync('python3',['-I',join(directory,'runtime.py')],{encoding:'utf8',timeout:2500,maxBuffer:4096,env:{...process.env,LAB_RUN_ID:randomUUID(),LAB_PORT:'8105'}});const rejected=()=>{const result=run();assert.equal(result.status,1);assert.match(result.stderr,/pinned assets rejected/);assert(!result.stderr.includes('READY')&&!result.stderr.includes('Traceback'));};
 await restore();await writeFile(join(directory,'fixtures/revenue.json'),'[]');rejected();await restore();const hostile=structuredClone(catalogue);hostile.assets['revenue-summary'].dataPath='/etc/passwd';await writeFile(join(directory,'catalogue.json'),JSON.stringify(hostile));rejected();await restore();const modelPath=join(directory,'models/sentiment-inference.json'),model=parseStrictJson(await readFile(modelPath,'utf8'));model.format='pickle';await writeFile(modelPath,JSON.stringify(model));const modelCatalogue=structuredClone(catalogue);modelCatalogue.items.find(i=>i.id==='sentiment-inference').modelSha256=sha(await readFile(modelPath));await writeFile(join(directory,'catalogue.json'),JSON.stringify(modelCatalogue));rejected();await restore();await mkdir(join(directory,'alternate'));await cp(join(directory,'fixtures'),join(directory,'alternate/fixtures'),{recursive:true});await rm(join(directory,'fixtures'),{recursive:true,force:true});await symlink(join(directory,'alternate/fixtures'),join(directory,'fixtures'),'dir');rejected();
});

test('HTTP rejects framing, origins, identity, content/path/method, malformed UTF-8 and oversized bytes',async t=>{
 const runtime=await startRuntime();t.after(()=>runtime.close());const item=itemFor('revenue-summary'),sample=fields(runtime,item.id,item.defaultInputs);assert.equal((await send(runtime,sample)).status,200);
 for(const [input,options,status] of [[{...sample,runId:randomUUID()},{},403],[{...sample,url:'file:///private'},{},400],[Buffer.from(new URLSearchParams(sample)+'&region=north'),{},400],[Buffer.from('runId=%ff'),{},400],[Buffer.from('runId=%zz'),{},400],[Buffer.from([255]),{},400],[sample,{path:'/request?model=x'},400],[sample,{path:'//request'},400],[sample,{method:'OPTIONS'},405],[sample,{headers:{Origin:''}},403],[sample,{headers:{Origin:'https://example.invalid'}},403],[sample,{headers:{Host:'localhost:'+runtime.port}},403],[sample,{headers:{'Content-Type':'application/json'}},415],[Buffer.alloc(8193,65),{},413]]){const reply=await send(runtime,input,options);assert.equal(reply.status,status);assert.deepEqual(Object.keys(reply.value).sort(),['error','message']);assert(!Object.keys(reply.headers).some(key=>key.startsWith('access-control-')));}
 for(const headers of ['Content-Length: 0\r\nContent-Length: 0\r\n','Transfer-Encoding: chunked\r\nContent-Length: 0\r\n','Transfer-Encoding: \r\nContent-Length: 0\r\n']){const result=await rawRequest(runtime,'POST /request HTTP/1.1\r\nHost: 127.0.0.1:'+runtime.port+'\r\nContent-Type: application/x-www-form-urlencoded\r\n'+headers+'\r\n');assert.match(result,/HTTP\/1\.0 (400|403)/);assert(!result.includes('getlancer-data-ai-result-v1'));}
 assert(!runtime.stderr.includes('file:///private'));
});

test('valid extreme inputs retain actual cross-language hashes; malformed numeric/text inputs deny',async t=>{
 const runtime=await startRuntime();t.after(()=>runtime.close());for(const [id,input] of [['revenue-summary',{region:'north',upliftPercent:'-20'}],['sensor-quality',{channel:'all',tolerance:'10'}],['sentiment-inference',{text:'terrible '.repeat(7)}],['sentiment-inference',{text:'excellent '.repeat(16)}],['equipment-inference',{temperature:'100',vibration:'20'}]]){const reply=await send(runtime,fields(runtime,id,input));assert.equal(reply.status,200);validateEnvelope(reply.value,itemFor(id),sourceSha256,catalogue.contracts[id]);assert(reply.bytes<=4096);}
 for(const [id,input] of [['revenue-summary',{region:'all',upliftPercent:'00'}],['revenue-summary',{region:'all',upliftPercent:'1.0'}],['sensor-quality',{channel:'all',tolerance:'0'}],['sentiment-inference',{text:'x'.repeat(161)}],['sentiment-inference',{text:'private\nsecret'}],['equipment-inference',{temperature:'NaN',vibration:'1'}],['equipment-inference',{temperature:'101',vibration:'1'}]]){const reply=await send(runtime,fields(runtime,id,input));assert.equal(reply.status,400);assert(!JSON.stringify(reply.value).includes('private'));}assert(!runtime.stderr.includes('terrible')&&!runtime.stderr.includes('private'));
});

test('rejected attempts exhaust total budget before compute; request101 returns429 and process exits',async t=>{
 const runtime=await startRuntime();t.after(()=>runtime.close());const sample=fields(runtime,'revenue-summary',itemFor('revenue-summary').defaultInputs);for(let i=0;i<2;i++)assert.match(await rawRequest(runtime,'MALFORMED\r\n\r\n'),/HTTP\/1\.0 400/);for(let i=0;i<98;i++)assert.equal((await send(runtime,sample,{path:'/request?denied=1'})).status,400);const reply=await send(runtime,sample);assert.equal(reply.status,429);assert.equal(reply.value.error,'ATTEMPT_LIMIT');await new Promise(resolve=>runtime.child.exitCode===null?runtime.child.once('exit',resolve):resolve());assert.equal(runtime.child.exitCode,0);
});

test('absolute request deadline blocks dribbled framing and idle/total expiry closes disposable state',async t=>{
 const runtime=await startRuntime({env:{LAB_LIFETIME_SECONDS:'1'}});t.after(()=>runtime.close());const started=performance.now();const response=await new Promise((done,reject)=>{const socket=connect(runtime.port,'127.0.0.1'),parts=[];let timer;socket.on('connect',()=>{socket.write('POST /request HTTP/1.1\r\nHost: 127.0.0.1:'+runtime.port+'\r\nX-Dribble: ');timer=setInterval(()=>socket.write('x'),60);});socket.on('data',part=>parts.push(part));socket.on('error',error=>{if(error.code!=='ECONNRESET')reject(error);});socket.on('close',()=>{clearInterval(timer);done(Buffer.concat(parts).toString());});});assert.match(response,/408/);assert.match(response,/REQUEST_TIMEOUT/);assert(performance.now()-started<2000);await new Promise(resolve=>runtime.child.exitCode===null?runtime.child.once('exit',resolve):resolve());assert.equal(runtime.child.exitCode,0);
 const idle=await startRuntime({env:{LAB_IDLE_SECONDS:'1'}});t.after(()=>idle.close());const exited=await new Promise(resolve=>{if(idle.child.exitCode!==null)return resolve(true);const timer=setTimeout(()=>resolve(false),2200);idle.child.once('exit',()=>{clearTimeout(timer);resolve(true);});});assert(exited);assert.equal(idle.child.exitCode,0);
});

test('actual scenarios and clean extracted source reproduce under measured output/resource bounds',async()=>{const result=await verifyRuntime({requireRecordings:true});assert.equal(result.recordings.length,4);assert.equal(result.measurements.cleanExecutions,8);assert(result.measurements.maxResponseBytes<=4096&&result.measurements.cleanupEmpty);});
