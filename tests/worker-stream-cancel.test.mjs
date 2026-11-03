import assert from 'node:assert/strict';
import {connect} from 'node:net';
import {setTimeout as delay} from 'node:timers/promises';
import test from 'node:test';
import {Miniflare,convertV4MiniflareOptions} from 'miniflare';

// ReadableStream cleanup must run when a client leaves an in-flight response.
// Keep this independent of the application build so an upstream runtime regression
// cannot be mistaken for a route, asset or framework failure.
const source=`
const records=new Map();
const encode=value=>new TextEncoder().encode(value);
export default {fetch(request){
 const url=new URL(request.url);
 if(url.pathname==='/health')return new Response('healthy');
 if(url.pathname==='/record')return Response.json(records.get(url.searchParams.get('id'))||null);
 const record={ticks:0,cancelled:false,ended:false};
 records.set(url.searchParams.get('id'),record);
 const next=async controller=>{
  await new Promise(resolve=>setTimeout(resolve,40));
  if(record.cancelled)return;
  if(record.ticks===20){record.ended=true;controller.close();return;}
  controller.enqueue(encode('tick:'+record.ticks+++' '+'.'.repeat(1024)+'\\n'));
 };
 return new Response(new ReadableStream(url.searchParams.get('mode')==='pull'?{
  start(controller){controller.enqueue(encode('first\\n'));},
  pull:next,
  cancel(){record.cancelled=true;}
 }:{
  async start(controller){controller.enqueue(encode('first\\n'));while(!record.cancelled&&!record.ended)await next(controller);},
  cancel(){record.cancelled=true;}
 }),{headers:{'Content-Type':'text/plain'}});
}};`;

async function abortAfterFirstChunk(origin,path){
 const url=new URL(path,origin);
 await new Promise((resolve,reject)=>{
  const socket=connect(Number(url.port),url.hostname);
  let received='';
  const timeout=setTimeout(()=>{socket.destroy();reject(new Error('Streaming response did not produce its first chunk.'));},5000);
  socket.once('error',reject);
  socket.once('close',()=>clearTimeout(timeout));
  socket.once('connect',()=>socket.write('GET '+url.pathname+url.search+' HTTP/1.1\r\nHost: '+url.host+'\r\n\r\n'));
  socket.on('data',chunk=>{
   received+=chunk.toString();
   if(received.includes('first')){socket.resetAndDestroy();resolve();}
  });
 });
}

for(const proxied of [false,true]){
 test(`native Worker cancels abandoned ${proxied?'service-binding':'direct'} response streams`,{timeout:30000},async t=>{
  const streamWorker={name:'stream-source',modules:[{type:'ESModule',path:'stream.js',contents:source}],compatibilityDate:'2026-09-30'};
  const workers=proxied?[{
   name:'entry',modules:[{type:'ESModule',path:'entry.js',contents:'export default {fetch(request,env){return env.SOURCE.fetch(request)}}'}],
   serviceBindings:{SOURCE:'stream-source'},compatibilityDate:'2026-09-30'
  },streamWorker]:[streamWorker];
  const runtime=new Miniflare(convertV4MiniflareOptions({port:0,host:'127.0.0.1',workers}));
  t.after(()=>runtime.dispose());
  const origin=await runtime.ready;
  const read=async id=>{
   const response=await fetch(new URL('/record?id='+id,origin),{signal:AbortSignal.timeout(5000)});
   assert.equal(response.status,200);
   return response.json();
  };
  for(const mode of ['start','pull']){
   await t.test(`async ${mode}: complete response closes without cancellation`,async()=>{
    const id=mode+'-complete';
    const response=await fetch(new URL('/stream?id='+id+'&mode='+mode,origin),{signal:AbortSignal.timeout(5000)});
    assert.equal(response.status,200);
    assert.ok((await response.text()).includes('tick:19 '));
    assert.deepEqual(await read(id),{ticks:20,cancelled:false,ended:true});
   });
   await t.test(`async ${mode}: disconnect calls source cleanup and preserves service health`,async()=>{
    const id=mode+'-abort';
    await abortAfterFirstChunk(origin,'/stream?id='+id+'&mode='+mode);
    const deadline=Date.now()+2000;
    let record;
    do{record=await read(id);if(record?.cancelled)break;await delay(25);}while(Date.now()<deadline);
    assert.equal(record?.cancelled,true,'Peer disconnect must call the JavaScript response source cancel callback.');
    assert.equal(record.ended,false);
    const ticks=record.ticks;
    await delay(100);
    assert.equal((await read(id)).ticks,ticks,'Cancelled producer must stop emitting chunks.');
    const health=await fetch(new URL('/health',origin),{signal:AbortSignal.timeout(5000)});
    assert.equal(health.status,200);
    assert.equal(await health.text(),'healthy');
   });
  }
 });
}
