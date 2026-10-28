import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import test from 'node:test';

const source=readFileSync(new URL('../app/components/team-public.tsx',import.meta.url),'utf8');

// Execute the production mutation callback, stubbing only transport and React state.
// The inline fallback also runs against the pre-repair component as a negative control.
function callback(kind,context){
 const name=kind==='interest'?'sendInterest':'sendApplication';
 let code;
 if(source.includes('async function '+name+'(')){
  const start=source.indexOf('async function '+name+'('),end=source.indexOf('\n ',start+1);
  code=source.slice(start,end);
  code=stripTypeScriptTypes(code,{mode:'transform'})+';return '+name+';';
 }else{
  const marker=kind==='interest'?"onSave={async d=>{await request('/teams/'+id+'/leads'":"onSave={async d=>{await request('/teams/'+id+'/roles/'+r.id+'/applications'";
  const start=source.indexOf(marker),end=source.indexOf('}}>',start);
  assert.ok(start>=0&&end>start,'The actual production callback exists');
  code='return '+source.slice(start+'onSave={'.length,end+1)+';';
 }
 return Function(...Object.keys(context),code)(...Object.values(context));
}
function deferred(){let resolve,reject;const promise=new Promise((yes,no)=>{resolve=yes;reject=no;});return{promise,resolve,reject};}
function harness(pending){
 const state={notice:'',applied:[]},writes=[];
 const context={id:'alpha',r:{id:'role-alpha'},generation:{current:1},request:async(path,init)=>{writes.push({path,body:JSON.parse(init.body)});await pending.promise;return {ok:true};},setNotice:value=>{state.notice=value;},setApplied:update=>{state.applied=update(state.applied);}};
 return {state,writes,context};
}

for(const kind of ['interest','application']){
 test('team '+kind+' acknowledgement stays with the team and request context that started it',async()=>{
  const pending=deferred(),{context,state,writes}=harness(pending),send=callback(kind,context);
  const request=kind==='interest'?send({title:'An accessible project'}):source.includes('async function sendApplication(')?send('role-alpha',{message:'Relevant builder experience'}):send({message:'Relevant builder experience'});
  assert.equal(writes.length,1,'A real mutation started before changing context');
  context.generation.current++;
  pending.resolve();await request;
  assert.equal(state.notice,'','The old success cannot be announced for the current team');
  assert.deepEqual(state.applied,[],'An old application cannot change current application state');
 });
 test('team '+kind+' unchanged context still acknowledges its accepted write',async()=>{
  const pending=deferred(),{context,state,writes}=harness(pending),send=callback(kind,context);
  const request=kind==='interest'?send({title:'An accessible project'}):source.includes('async function sendApplication(')?send('role-alpha',{message:'Relevant builder experience'}):send({message:'Relevant builder experience'});
  pending.resolve();await request;
  assert.match(state.notice,kind==='interest'?/^Project interest sent\./:/^Application sent\./);
  assert.equal(writes[0].path,kind==='interest'?'/teams/alpha/leads':'/teams/alpha/roles/role-alpha/applications');
  assert.deepEqual(state.applied,kind==='interest'?[]:['role-alpha']);
 });
 test('team '+kind+' rejected write does not publish success',async()=>{
  const pending=deferred(),{context,state}=harness(pending),send=callback(kind,context);
  const request=kind==='interest'?send({title:'An accessible project'}):source.includes('async function sendApplication(')?send('role-alpha',{message:'Relevant builder experience'}):send({message:'Relevant builder experience'});
  pending.reject(new Error('Current team access is required.'));
  await assert.rejects(request,/Current team access/);assert.equal(state.notice,'');assert.deepEqual(state.applied,[]);
 });
}
