import test from 'node:test';
import assert from 'node:assert/strict';
import {labApplyEvents,labAttempt,labCanRequest,labCanStop,labEventsPath,labExpired,labExpiryText,labFieldErrors,labRunResponse,labSourceUrl,labTerminal,parseLabEvents} from '../lib/labs.ts';
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
test('terminal status cannot be resurrected by replay and malformed successful responses never establish verified runs',()=>{
 const stopped={...running,status:'CANCELLED'};assert.equal(labApplyEvents(stopped,[envelope(2)]).status,'CANCELLED');assert.equal(labApplyEvents(running,[envelope(3,{status:'SUCCEEDED',reason:'Finished.'})]).status,'SUCCEEDED');assert.throws(()=>labApplyEvents(running,[envelope(4,{}, {runId:'other'})]));
 assert.deepEqual(labRunResponse({...running,eventsUrl:'https://worker.test/private'}),running);
 for(const change of [{status:'WAITING'},{verified:'true'},{expiresAt:'invalid'},{id:'arbitrary-worker'},{requestedAt:'bad'},{reason:'x'.repeat(2001)}])assert.throws(()=>labRunResponse({...running,...change}));
});
