import test from 'node:test';
import assert from 'node:assert/strict';
import {labObservation,labMergeObservations,labApplyEvents,labAttempt,labCanRequest,labCanStop,labEventsPath,labExpired,labExpiryText,labFieldErrors,labRunResponse,labSourceUrl,labTerminal,parseLabEvents} from '../lib/labs.ts';
const id='11111111-1111-4111-8111-111111111111',now=Date.parse('2026-10-09T04:30:00Z');
const running={id,manifestId:'reviewed-manifest',scenarioId:'inspect',status:'RUNNING',executionMode:'ISOLATED',verified:true,requestedAt:'2026-10-09T04:29:00Z',expiresAt:'2026-10-09T04:34:00Z',reason:'Isolated worker is healthy.',eventsUrl:'/api/v1/lab-runs/'+id+'/events'};
const field=(name,type,extra={})=>({name,label:name,type,required:true,maxLength:30,min:1,max:10,choices:['one','two'],...extra});
const envelope=(sequence,data={status:'RUNNING',reason:'Healthy isolated worker.'},extra={})=>({runId:id,sequence,type:'STATE',recordedAt:'2026-10-09T04:30:00Z',data,...extra});
const sse=event=>'id: '+event.sequence+'\ndata: '+JSON.stringify(event)+'\n\n';

test('lost-response run and operation retries retain exact identities and immutable bodies',()=>{
 let creations=0;const key=()=>{creations++;return 'attempt-key-123';};
 const body={manifestId:'manifest',scenarioId:'step',inputs:{z:'last',a:'first'}};
 const attempt=labAttempt(body,null,key);body.inputs.a='mutated';assert.equal(attempt.body.inputs.a,'first');
 const retry=labAttempt({manifestId:'manifest',scenarioId:'step',inputs:{a:'first',z:'last'}},attempt,key);assert.equal(retry,attempt);assert.equal(creations,1);
 assert.throws(()=>labAttempt({...body,inputs:{a:'changed',z:'last'}},attempt,key),/Resolve the previous/);
 const operation=labAttempt({operationId:'inspect',inputs:{limit:'2'}},null,key);assert.equal(labAttempt({operationId:'inspect',inputs:{limit:'2'}},operation,key),operation);assert.throws(()=>labAttempt({operationId:'delete',inputs:{limit:'2'}},operation,key));
 assert.throws(()=>labAttempt(body,null,()=>''));assert.throws(()=>labAttempt({...body,inputs:{large:'é'.repeat(32769)}},null,key),/64 KiB/);
});
test('certified field hints reject invalid whole numbers, absent required fields and unsupported schema values',()=>{
 const fields=[field('count','INTEGER'),field('enabled','BOOLEAN'),field('mode','ENUM'),field('label','TEXT',{maxLength:4}),field('optional','TEXT',{required:false})];
 assert.deepEqual(labFieldErrors(fields,{count:'3',enabled:'false',mode:'one',label:'safe'}),{});
 for(const value of ['1.5','0','11','9007199254740993','1e1','abc'])assert.ok(labFieldErrors([fields[0]],{count:value}).count);
 const errors=labFieldErrors(fields,{count:'',enabled:'yes',mode:'three',label:'oversized'});assert.deepEqual(Object.keys(errors),['count','enabled','mode','label']);
});
test('only a verified, unexpired Running run accepts requests; queued and starting can cancel',()=>{
 assert.equal(labCanRequest(running,now),true);assert.equal(labCanStop(running),true);
 for(const status of ['QUEUED','STARTING','CANCELLING','CANCELLED','SUCCEEDED','FAILED','EXPIRED'])assert.equal(labCanRequest({...running,status},now),false);
 for(const status of ['QUEUED','STARTING'])assert.equal(labCanStop({...running,status}),true);
 for(const status of ['CANCELLING','CANCELLED','SUCCEEDED','FAILED','EXPIRED'])assert.equal(labCanStop({...running,status}),false);
 for(const change of [{verified:false},{expiresAt:'invalid'},{expiresAt:'2026-10-09T04:30:00Z'}])assert.equal(labCanRequest({...running,...change},now),false);
 assert.equal(labExpired(running,now),false);assert.match(labExpiryText(running,now),/Expires at/);assert.match(labExpiryText({...running,expiresAt:'2026-10-09T04:30:30Z'},now),/less than a minute/);assert.match(labExpiryText(running,now+300000),/expired/);
 assert.equal(labTerminal('FAILED'),true);assert.equal(labTerminal('STARTING'),false);
});
test('free source URLs reject scripts, cleartext, credentials and control bytes',()=>{
 assert.equal(labSourceUrl('https://github.com/example/source'), 'https://github.com/example/source');assert.equal(labSourceUrl('/components/feedback-form/source'),'/components/feedback-form/source');
 for(const value of ['javascript:alert(1)','http://example.test/source','https://user:secret@example.test/','https://example.test/\n','//evil.test/source','/api/v1/private',null])assert.equal(labSourceUrl(value),undefined);
 assert.equal(labEventsPath(id),'/api/v1/lab-runs/'+id+'/events');assert.throws(()=>labEventsPath('https://worker.example.test/'));assert.throws(()=>labEventsPath('../private'));
});
test('bounded SSE replay verifies owner identity, increasing cursor, safe fields and exact cursor binding',()=>{
 assert.deepEqual(parseLabEvents(sse(envelope(1))+sse(envelope(2)),id),[envelope(1),envelope(2)]);assert.deepEqual(parseLabEvents(': heartbeat\n\n',id),[]);
 for(const event of [envelope(1,{}, {runId:'other'}),envelope(0),envelope(1,{status:'UNKNOWN'}),envelope(1,{token:'secret'}),envelope(1,{reason:'x'.repeat(2001)}),envelope(1,{}, {recordedAt:'invalid'})])assert.throws(()=>parseLabEvents(sse(event),id));
 assert.throws(()=>parseLabEvents(sse(envelope(1)),id,1));assert.throws(()=>parseLabEvents(sse(envelope(1)).replace('id: 1','id: 2'),id));assert.throws(()=>parseLabEvents('data: invalid\n\n',id));
 assert.throws(()=>parseLabEvents('x'.repeat(65537),id));assert.throws(()=>parseLabEvents(Array.from({length:101},(_,index)=>sse(envelope(index+1))).join(''),id));
});
test('event replay clears current verification after cancellation without manufacturing authority',()=>{
 for(const status of ['CANCELLING','CANCELLED','FAILED','SUCCEEDED','EXPIRED']){
  const applied=labApplyEvents(running,[envelope(2,{status})]);assert.equal(applied.status,status);assert.equal(applied.verified,false);assert.equal(running.verified,true);
 }
 const history=[envelope(1,{status:'QUEUED'}),envelope(2,{status:'STARTING'}),envelope(3,{status:'RUNNING'})];
 assert.equal(labApplyEvents(running,history).verified,true);
 assert.equal(labApplyEvents({...running,status:'STARTING',verified:false},history).verified,false);
 const stopped=labApplyEvents(running,[envelope(4,{status:'CANCELLED'})]);assert.equal(labApplyEvents(stopped,[envelope(5)]).verified,false);
});
test('terminal status cannot be resurrected by replay and malformed successful responses never establish verified runs',()=>{
 const stopped={...running,status:'CANCELLED'};assert.equal(labApplyEvents(stopped,[envelope(2)]).status,'CANCELLED');assert.equal(labApplyEvents(running,[envelope(3,{status:'SUCCEEDED',reason:'Finished.'})]).status,'SUCCEEDED');assert.throws(()=>labApplyEvents(running,[envelope(4,{}, {runId:'other'})]));
 assert.deepEqual(labRunResponse({...running,eventsUrl:'https://worker.test/private'}),running);
 for(const change of [{status:'WAITING'},{verified:'true'},{expiresAt:'invalid'},{id:'arbitrary-worker'},{requestedAt:'bad'},{reason:'x'.repeat(2001)}])assert.throws(()=>labRunResponse({...running,...change}));
});

const sourceHash='a'.repeat(64),nativeRequest='22222222-2222-4222-8222-222222222222';
const observed=()=>({runId:id,sourceHash,requestId:nativeRequest,pattern:'cache',status:200,durationMs:3,state:{cache:'MISS',storeReads:1,ttlMs:2000},events:[{runId:id,sourceHash,requestId:nativeRequest,sequence:1,type:'CACHE_MISS',edge:'service-redis',recordedAt:'2026-10-22T00:00:00Z',summary:'Actually observed cache miss.'},{runId:id,sourceHash,requestId:nativeRequest,sequence:2,type:'STORE_READ',edge:'service-store',recordedAt:'2026-10-22T00:00:00Z',summary:'Actually observed store read.'}]});
test('native observations reject foreign identities, incoherent state, unsupported edges and nonmonotonic evidence',()=>{
 const value=observed();assert.equal(labObservation(value,id,sourceHash).events[1].edge,'service-store');
 const mutations=[v=>v.runId='33333333-3333-4333-8333-333333333333',v=>v.sourceHash='b'.repeat(64),v=>v.requestId='33333333-3333-4333-8333-333333333333',v=>v.state.private='secret',v=>v.state.cache='HIT',v=>v.events[1].sequence=1,v=>v.events[1].edge='service-redis',v=>v.events[1].requestId='33333333-3333-4333-8333-333333333333',v=>v.events[1].sequence=true,v=>v.events[1].summary='x'.repeat(241),v=>v.events[1].recordedAt='not-a-date',v=>v.sourceHash+='a',v=>v.status=401,v=>v.durationMs=30001];
 for(const mutation of mutations){const bad=observed();mutation(bad);assert.throws(()=>labObservation(bad,id,sourceHash));}
});
test('operation and SSE observations merge idempotently and reject whole malformed batches without advancing caller history',()=>{
 const observation=labObservation(observed(),id,sourceHash),previous=labMergeObservations([],[observation],id,sourceHash);assert.equal(labMergeObservations(previous,[observation],id,sourceHash).length,1);
 const altered=structuredClone(observation);altered.state.ttlMs=1000;assert.throws(()=>labMergeObservations(previous,[altered],id,sourceHash));assert.deepEqual(previous,[observation]);
 const stale=structuredClone(observation);stale.requestId='33333333-3333-4333-8333-333333333333';stale.events.forEach(event=>event.requestId=stale.requestId);assert.throws(()=>labMergeObservations(previous,[stale],id,sourceHash));
 const text=sse(envelope(1,{status:'RUNNING',observation}));assert.equal(parseLabEvents(text,id,0,sourceHash)[0].data.observation.events.length,2);
 const bad=structuredClone(observation);bad.sourceHash='b'.repeat(64);assert.throws(()=>parseLabEvents(text+sse(envelope(2,{status:'RUNNING',observation:bad})),id,0,sourceHash));assert.deepEqual(previous,[observation]);
});
test('finite control replay recovers older observations after direct responses while retaining its own monotonic cursor',()=>{
 const early=labObservation(observed(),id,sourceHash),later=structuredClone(early);later.requestId='33333333-3333-4333-8333-333333333333';later.events.forEach((event,index)=>{event.requestId=later.requestId;event.sequence=3+index;});const direct=labMergeObservations([],[later],id,sourceHash),recovered=labMergeObservations(direct,[early,later],id,sourceHash,0);assert.deepEqual(recovered.map(item=>item.events[0].sequence),[1,3]);assert.equal(labMergeObservations(recovered,[later],id,sourceHash).length,2);assert.throws(()=>labMergeObservations(recovered,[early],id,sourceHash,2));assert.throws(()=>labMergeObservations(recovered,[later,early],id,sourceHash,0));assert.equal(direct.length,1);
});
