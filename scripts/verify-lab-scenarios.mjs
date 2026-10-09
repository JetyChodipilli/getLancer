/** Real local HTTP/Redis verification. No fixture server substitutes for a lab or Redis. */
import assert from 'node:assert/strict';
import {createHash,createHmac,randomUUID} from 'node:crypto';
import {spawn} from 'node:child_process';
import {createServer,connect} from 'node:net';
import {mkdtemp,readFile,mkdir,writeFile,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join,resolve,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {fixturePort as port} from './lab-fixture-ports.mjs';

const root=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const args=process.argv.slice(2),selected=args.includes('--language')?args[args.indexOf('--language')+1]:null;
const record=args.includes('--record');
assert(args.every((value,index)=>['--record','--language'].includes(value)||args[index-1]==='--language'),'Unknown verifier argument');
const catalogue=JSON.parse(await readFile(join(root,'labs/catalogue.json'),'utf8'));
assert(!selected||Object.hasOwn(catalogue.languages,selected),'Choose a declared language');
const languages=selected?[selected]:Object.keys(catalogue.languages);
const sha=bytes=>createHash('sha256').update(bytes).digest('hex');
const signature=body=>createHmac('sha256','getlancer-payment-fixture-only').update(body).digest('hex');
const delay=ms=>new Promise(done=>setTimeout(done,ms));
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const hash=/^[0-9a-f]{64}$/;
const eventTypes=new Set(['CACHE_MISS','CACHE_HIT','CACHE_EXPIRED','CACHE_FALLBACK','CACHE_RESET','STORE_READ','AUTH_ALLOWED','AUTH_DENIED','AUTH_REVOKED','AUTH_RESET','PAYMENT_APPLIED','PAYMENT_DUPLICATE','PAYMENT_SIGNATURE_DENIED','PAYMENT_CONFLICT','PAYMENT_PENDING','PAYMENT_RESET','REFUND_PENDING','REFUND_APPLIED']);
function processStart(command,argv,env,cwd=root){
 const child=spawn(command,argv,{cwd,env:{PATH:process.env.PATH,...env},stdio:['ignore','pipe','pipe']});let output='';let problem;
 child.on('error',error=>{problem=error;});for(const stream of [child.stdout,child.stderr])stream.on('data',chunk=>{output=(output+chunk.toString()).slice(-4000);});
 return {child,diagnostic:()=>problem?.message||output};
}
async function stop(handle){if(!handle||!handle.child.pid||handle.child.exitCode!==null||handle.child.signalCode!==null)return;const exited=new Promise(done=>handle.child.once('exit',done));handle.child.kill('SIGTERM');await Promise.race([exited,delay(2000)]);if(handle.child.exitCode===null&&handle.child.signalCode===null){handle.child.kill('SIGKILL');await exited;}}
async function ready(handle,url,timeout=20000){const deadline=Date.now()+timeout;while(Date.now()<deadline){if(handle.child.exitCode!==null)throw Error('Local lab exited: '+handle.diagnostic());try{const response=await fetch(url,{signal:AbortSignal.timeout(500)});if(response.ok)return response.json();}catch{}await delay(80);}throw Error('Local readiness timed out: '+handle.diagnostic());}
async function redisCommand(redisPort,...parts){
 return new Promise((yes,no)=>{const socket=connect({host:'127.0.0.1',port:redisPort});let bytes=Buffer.alloc(0);socket.setTimeout(1000,()=>socket.destroy(Error('Redis verifier deadline')));socket.once('error',no);socket.once('connect',()=>socket.write('*'+parts.length+'\r\n'+parts.map(part=>{const value=Buffer.from(String(part));return '$'+value.length+'\r\n'+value+'\r\n';}).join('')));socket.on('data',chunk=>{bytes=Buffer.concat([bytes,chunk]);if(bytes.length>8192){socket.destroy(Error('Redis verifier bound'));return;}if(bytes.includes('\r\n')){socket.end();yes(bytes.toString());}});});
}
async function verifyLanguage(language){
 const workspace=await mkdtemp(join(tmpdir(),'getlancer-lab-'));const redisPort=await port(),failurePort=await port();const clients=new Set();
 const blackhole=createServer(socket=>{clients.add(socket);socket.on('error',()=>{});socket.on('close',()=>clients.delete(socket));});
 const timeoutPort=await port();await new Promise((yes,no)=>blackhole.once('error',no).listen(timeoutPort,'127.0.0.1',yes));
 const redis=processStart(process.env.REDIS_SERVER||'redis-server',['--bind','127.0.0.1','--port',String(redisPort),'--save','','--appendonly','no','--protected-mode','yes','--maxmemory','16mb','--maxclients','32'],{},workspace);
 const apps=[];let requests=0;const captures=[];
 try{
  const version=await new Promise((yes,no)=>{const child=spawn(process.env.REDIS_SERVER||'redis-server',['--version'],{stdio:['ignore','pipe','pipe']});let output='';child.on('error',no);child.stdout.on('data',chunk=>output+=chunk);child.on('exit',code=>code===0?yes(output):no(Error('Redis version unavailable')));});
  assert.match(version,/Redis server v=7\.2\.16\b/,'Use the pinned actual Redis 7.2.16, never a Redis-compatible substitute');
  let redisReady=false;for(let i=0;i<100;i++){try{assert.match(await redisCommand(redisPort,'PING'),/^\+PONG/);redisReady=true;break;}catch{await delay(50);}}assert(redisReady,'Real Redis startup failed: '+redis.diagnostic());
  const primary=catalogue.languages[language].primary,sourceHash=sha(await readFile(join(root,primary)));
  for(let i=0;i<2;i++){
   const runId=randomUUID(),httpPort=await port();let command,argv;
   if(language==='java'){command=process.env.LAB_JAVA||'java';argv=[primary];}
   else if(language==='typescript'){command=process.execPath;argv=['--experimental-strip-types',primary];}
   else{command=process.env.LAB_PYTHON||'python3';argv=[primary];}
   const handle=processStart(command,argv,{LAB_RUN_ID:runId,LAB_PORT:String(httpPort),LAB_REDIS_PORT:String(redisPort),LAB_REDIS_FAILURE_PORT:String(failurePort),LAB_REDIS_TIMEOUT_PORT:String(timeoutPort)});
   const app={...handle,runId,sourceHash,origin:'http://127.0.0.1:'+httpPort,sequence:0};apps.push(app);
   const health=await ready(handle,app.origin+'/health');assert.deepEqual(health,{mode:'Local execution',language,runId,sourceHash});
  }
  const [app,other]=apps;
  async function request(target,pattern,operationId,inputs={},expected=200,capture=true){
   const start=performance.now();const response=await fetch(target.origin+'/request',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({runId:target.runId,pattern,operationId,...inputs}),signal:AbortSignal.timeout(3000)});requests++;
   const text=await response.text();assert.equal(response.status,expected,`${language} ${pattern}/${operationId}: ${text}`);assert(Buffer.byteLength(text)<=4096,'Response exceeded safe projection');
   const body=JSON.parse(text);assert.deepEqual(Object.keys(body).sort(),['durationMs','events','pattern','requestId','runId','sourceHash','state','status']);assert.equal(body.runId,target.runId);assert.equal(body.sourceHash,sourceHash);assert.equal(body.pattern,pattern);assert.equal(body.status,expected);assert.match(body.requestId,uuid);assert(Number.isFinite(body.durationMs)&&body.durationMs>=0&&body.durationMs<3000);
   assert(Array.isArray(body.events)&&body.events.length>0&&body.events.length<=4,'Observed bounded events required');
   const edges=new Set(catalogue.patterns[pattern].edges.map(edge=>edge.id));
   for(const event of body.events){assert.deepEqual(Object.keys(event).sort(),['edge','recordedAt','requestId','runId','sequence','sourceHash','summary','type']);assert.equal(event.runId,target.runId);assert.equal(event.sourceHash,sourceHash);assert.equal(event.requestId,body.requestId);assert(Number.isSafeInteger(event.sequence)&&event.sequence>target.sequence,'Events are not monotonic');target.sequence=event.sequence;assert(eventTypes.has(event.type));assert(edges.has(event.edge),'Foreign architecture edge');assert(Number.isFinite(Date.parse(event.recordedAt)));assert(typeof event.summary==='string'&&event.summary.length>0&&event.summary.length<=200);}
   assert(!text.includes(signature('evt-1|order-1|payment.succeeded'))&&!text.includes('getlancer-payment-fixture-only')&&!text.includes('http://')&&!text.includes('Bearer '),'Sensitive or endpoint material in projection');
   if(target===app&&capture)captures.push(body);
   return {body,elapsed:performance.now()-start};
  }
  async function denial(body,status,options={}){const response=await fetch(app.origin+(options.path||'/request'),{method:options.method||'POST',headers:{'Content-Type':options.contentType||'application/x-www-form-urlencoded'},...(options.method==='GET'?{}:{body}),signal:AbortSignal.timeout(3000)});requests++;assert.equal(response.status,status,await response.clone().text());const result=await response.json();assert(result.error&&typeof result.error.code==='string');assert(!Object.hasOwn(result,'events'),'Protocol denial may not fabricate execution');}
  const basic=new URLSearchParams({runId:app.runId,pattern:'cache',operationId:'read'}).toString();
  await denial(basic+'&destination=https%3A%2F%2Fexample.com',400);await denial(basic+'&pattern=payment',400);await denial(basic+'&x=%FF',400);await denial(basic+'&x=%Q0',400);
  await denial(new URLSearchParams({runId:other.runId,pattern:'cache',operationId:'read'}).toString(),403);
  await denial(basic,405,{method:'PUT'});await denial('',405,{method:'GET'});await denial(basic,404,{path:'/elsewhere'});await denial(basic,400,{path:'/request?target=other'});await denial(basic,415,{contentType:'application/json'});await denial('x='+('x'.repeat(17000)),413);
  await denial(new URLSearchParams({runId:app.runId,pattern:'cache',operationId:'arbitrary-command'}).toString(),400);
  assert.match(await redisCommand(redisPort,'GET','getlancer:'+app.runId+':item'),/^\$-1/,'Rejected request mutated Redis');
  const cacheReset=(await request(app,'cache','reset')).body;assert.equal(cacheReset.state.storeReads,0);
  const first=(await request(app,'cache','read')).body;assert.equal(first.state.cache,'MISS');assert.equal(first.state.storeReads,1);assert(first.events.some(event=>event.type==='CACHE_MISS'));assert(first.events.some(event=>event.type==='STORE_READ'));
  const hit=(await request(app,'cache','read')).body;assert.equal(hit.state.cache,'HIT');assert.equal(hit.state.storeReads,1);assert(hit.state.ttlMs>0&&hit.state.ttlMs<=5000);assert.match(await redisCommand(redisPort,'GET','getlancer:'+app.runId+':item'),/^\$12\r\nfixture-item/);
  await request(other,'cache','read',{},200,false);await request(other,'cache','read',{},200,false);
  await request(app,'cache','expire');const expired=(await request(app,'cache','read')).body;assert.equal(expired.state.cache,'MISS');assert.equal(expired.state.storeReads,2);
  const unavailable=await request(app,'cache','read-unavailable');assert.equal(unavailable.body.state.cache,'FALLBACK');assert.equal(unavailable.body.state.storeReads,3);assert(unavailable.elapsed<1500);
  const timeout=await request(app,'cache','read-timeout');assert.equal(timeout.body.state.cache,'FALLBACK');assert.equal(timeout.body.state.storeReads,4);assert(timeout.elapsed>=180&&timeout.elapsed<1500,'Real timeout must expire its bounded socket, not simulate an immediate fallback');
  await request(app,'cache','reset');const unaffected=(await request(other,'cache','read',{},200,false)).body;assert.equal(unaffected.state.cache,'HIT');assert.equal(unaffected.state.storeReads,1,'Reset affected another run');
  await request(app,'security','reset');const allowed=(await request(app,'security','authorize',{identity:'editor'})).body;assert.equal(allowed.state.decision,'ALLOW');
  for(const [identity,reason,status] of [['viewer','ROLE',403],['other-tenant','TENANT',403],['expired','EXPIRED',401],['tampered','SIGNATURE',401]]){const denied=(await request(app,'security','authorize',{identity},status)).body;assert.equal(denied.state.decision,'DENY');assert.equal(denied.state.reason,reason);}
  await request(app,'security','revoke');const revoked=(await request(app,'security','authorize',{identity:'editor'},403)).body;assert.equal(revoked.state.reason,'REVOKED');await request(app,'security','reset');assert.equal((await request(app,'security','authorize',{identity:'editor'})).body.state.decision,'ALLOW');
  await denial(new URLSearchParams({runId:app.runId,pattern:'security',operationId:'authorize',identity:'editor',token:'platform-token-positive-control'}).toString(),400);
  await request(app,'payment','reset');const paidBody='evt-1|order-1|payment.succeeded',refundBody='evt-2|order-1|refund.succeeded';
  const paid=(await request(app,'payment','deliver',{eventBody:paidBody,signature:signature(paidBody)})).body;assert.deepEqual(paid.state,{payment:'PAID',entitlements:1,processedEvents:1});
  const duplicate=(await request(app,'payment','deliver',{eventBody:paidBody,signature:signature(paidBody)})).body;assert.deepEqual(duplicate.state,paid.state);assert(duplicate.events.some(event=>event.type==='PAYMENT_DUPLICATE'));
  assert.deepEqual((await request(app,'payment','timeout')).body.state,paid.state,'Timeout lost confirmed payment facts');
  const bad=(await request(app,'payment','deliver',{eventBody:refundBody,signature:signature(paidBody)},401)).body;assert.deepEqual(bad.state,paid.state,'Bad signature altered the ledger');
  const conflictBody='evt-1|order-1|refund.succeeded';const conflict=(await request(app,'payment','deliver',{eventBody:conflictBody,signature:signature(conflictBody)},409)).body;assert.deepEqual(conflict.state,paid.state);
  const refund=(await request(app,'payment','deliver',{eventBody:refundBody,signature:signature(refundBody)})).body;assert.deepEqual(refund.state,{payment:'REFUNDED',entitlements:0,processedEvents:2});
  assert.deepEqual((await request(app,'payment','timeout')).body.state,refund.state,'Timeout resurrected refunded access');
  await request(app,'payment','reset');const pending=(await request(app,'payment','timeout',{},202)).body;assert.deepEqual(pending.state,{payment:'PENDING',entitlements:0,processedEvents:0});
  const earlyRefund=(await request(app,'payment','deliver',{eventBody:refundBody,signature:signature(refundBody)},202)).body;assert.equal(earlyRefund.state.payment,'PENDING');assert.equal(earlyRefund.state.entitlements,0);
  const latePaid=(await request(app,'payment','deliver',{eventBody:paidBody,signature:signature(paidBody)})).body;assert.deepEqual(latePaid.state,{payment:'REFUNDED',entitlements:0,processedEvents:2},'Late payment resurrected refunded access');
  await request(other,'payment','deliver',{eventBody:paidBody,signature:signature(paidBody)},200,false);await request(app,'payment','reset');assert.equal((await request(other,'payment','deliver',{eventBody:paidBody,signature:signature(paidBody)},200,false)).body.state.entitlements,1,'Payment reset affected another run');
  for(const eventBody of ['malformed',paidBody+'\n',paidBody+'\r\n'])await denial(new URLSearchParams({runId:app.runId,pattern:'payment',operationId:'deliver',eventBody,signature:signature(eventBody)}).toString(),400);
  await denial(new URLSearchParams({runId:app.runId,pattern:'payment',operationId:'deliver',eventBody:paidBody,signature:signature(paidBody)+'\n'}).toString(),400);
  for(let index=1;index<=100;index++){const eventBody='evt-'+index+'|order-1|payment.succeeded';const result=(await request(app,'payment','deliver',{eventBody,signature:signature(eventBody)},200,false)).body;assert.equal(result.state.entitlements,1);assert.equal(result.state.processedEvents,index);}
  assert.equal((await request(app,'payment','deliver',{eventBody:paidBody,signature:signature(paidBody)},200,false)).body.state.processedEvents,100,'Retry at capacity must remain idempotent');
  const overflowBody='evt-101|order-1|payment.succeeded';const overflowResponse=await fetch(app.origin+'/request',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({runId:app.runId,pattern:'payment',operationId:'deliver',eventBody:overflowBody,signature:signature(overflowBody)}),signal:AbortSignal.timeout(3000)});requests++;assert.equal(overflowResponse.status,409);const overflowText=await overflowResponse.text();assert(Buffer.byteLength(overflowText)<=4096);const overflow=JSON.parse(overflowText);if(!overflow.error){assert.deepEqual(overflow.state,{payment:'PAID',entitlements:1,processedEvents:100});assert.equal(overflow.runId,app.runId);assert.equal(overflow.sourceHash,sourceHash);for(const event of overflow.events){assert(event.sequence>app.sequence);app.sequence=event.sequence;}}else assert(!Object.hasOwn(overflow,'events'));
  assert.equal((await request(app,'payment','deliver',{eventBody:paidBody,signature:signature(paidBody)},200,false)).body.state.processedEvents,100,'Overflow changed retained effects');
  if(record){await mkdir(join(root,'labs/recordings'),{recursive:true});for(const pattern of Object.keys(catalogue.patterns)){const responses=captures.filter(body=>body.pattern===pattern);assert(responses.length>0);const evidence={mode:'REPLAY',labId:language+'-'+pattern,language,pattern,runId:app.runId,sourceHash,recordedAt:responses[0].events[0].recordedAt,responses};await writeFile(join(root,'labs/recordings',evidence.labId+'.json'),JSON.stringify(evidence,null,2)+'\n');}}
  console.log(`SCENARIO_MATRIX_OK language=${language} labs=${Object.keys(catalogue.patterns).length} requests=${requests} redis=7.2.16 mode=local`);
 }finally{for(const app of apps)await stop(app);await stop(redis);for(const socket of clients)socket.destroy();await new Promise(done=>blackhole.close(done));await rm(workspace,{recursive:true,force:true});}
}
for(const language of languages)await verifyLanguage(language);
if(!selected)console.log('NINE_LAB_MATRIX_OK');
