import {test} from 'node:test';
import assert from 'node:assert/strict';
import {resetCiRateBudgets} from '../scripts/ci-connected-budget.mjs';

const project='getlancer-ci-123',id='a'.repeat(64);
const restored=new WeakSet();
function scenario(t,change={}){
 if(!restored.has(t)){
  restored.add(t);
  const old={CI:process.env.CI,COMPOSE_PROJECT_NAME:process.env.COMPOSE_PROJECT_NAME};
  t.after(()=>{for(const [key,value] of Object.entries(old)){if(value===undefined)delete process.env[key];else process.env[key]=value;}});
 }
 process.env.CI=change.ci??'true';process.env.COMPOSE_PROJECT_NAME=change.project??project;
 const calls=[],labels={'com.docker.compose.project':change.containerProject??project,'com.docker.compose.service':'db'};
 const volume={Type:change.mountType??'volume',Name:change.volumeName??project+'_docker-database',Destination:'/var/lib/postgresql/data'};
 const run=(bin,args)=>{
  assert.equal(bin,'docker');calls.push(args);
  if(args[0]==='compose')return change.id??id;
  if(args[0]==='inspect')return JSON.stringify([{Config:{Labels:labels},Mounts:[volume]}]);
  if(args[0]==='volume')return JSON.stringify([{Labels:{'com.docker.compose.project':change.volumeProject??project}}]);
  if(args.at(-1).startsWith('SELECT'))return change.fixtureData??'t';
  return 'DELETE 7';
 };
 return {calls,run,fixture:{project:change.fixtureProject??project}};
}
const mutated=calls=>calls.some(args=>args.at(-1)==='DELETE FROM getlancer.rate_buckets');

test('connected counter isolation refuses non-CI, mismatched and parallel targets before invoking Docker',t=>{
 for(const change of [{ci:'false'},{project:'getlancer'},{fixtureProject:'getlancer-ci-456'},{workers:2}]){
  const state=scenario(t,change);assert.throws(()=>resetCiRateBudgets(state.fixture,change.workers??1,state.run));assert.equal(state.calls.length,0);
 }
});
test('connected counter isolation refuses ambiguous containers and foreign Compose ownership',t=>{
 for(const change of [{id:id+'\n'+id},{containerProject:'production'}]){
  const state=scenario(t,change);assert.throws(()=>resetCiRateBudgets(state.fixture,1,state.run));assert.equal(mutated(state.calls),false);
 }
});
test('connected counter isolation refuses host, shared and foreign database volumes',t=>{
 for(const change of [{mountType:'bind'},{volumeName:'production-database'},{volumeProject:'production'}]){
  const state=scenario(t,change);assert.throws(()=>resetCiRateBudgets(state.fixture,1,state.run));assert.equal(mutated(state.calls),false);
 }
});
test('connected counter isolation refuses a database containing non-fixture accounts',t=>{
 const state=scenario(t,{fixtureData:'f'});assert.throws(()=>resetCiRateBudgets(state.fixture,1,state.run));assert.equal(mutated(state.calls),false);
});
test('connected counter isolation mutates only verified CI counters through the inspected container',t=>{
 const state=scenario(t);resetCiRateBudgets(state.fixture,1,state.run);
 assert.equal(state.calls.filter(args=>args.at(-1)==='DELETE FROM getlancer.rate_buckets').length,1);
 assert.deepEqual(state.calls.at(-1).slice(0,2),['exec',id]);
 assert.equal(state.calls.filter(args=>args[0]==='exec').length,2);
});
