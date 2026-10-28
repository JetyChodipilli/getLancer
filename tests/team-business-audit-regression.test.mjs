import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import {runInNewContext} from 'node:vm';
import {createTeamDemo} from '../lib/team-demo.ts';
import {createBusinessDemo} from '../lib/business-demo.ts';

// Run the production payload builder without importing the React/TSX module.
const source=readFileSync(new URL('../app/components/team-workspace.tsx',import.meta.url),'utf8');
const helpers=['leadUpdate','localDate'].map(name=>{
 const match=source.match(new RegExp('^(?:export )?function '+name+'\\([^\\n]+$','m'));
 assert.ok(match,'Production helper '+name+' exists');return match[0].replace(/^export /,'');
}).join('\n');
const {leadUpdate,localDate}=runInNewContext(stripTypeScriptTypes(helpers)+';({leadUpdate,localDate})');
const payload=(data,lead)=>JSON.parse(JSON.stringify(leadUpdate(data,lead)));

test('lead edits omit unchanged overdue dates and preserve stored sub-minute precision',()=>{
 const lead={assigneeId:'expired-member',followUpAt:'2020-01-02T03:04:57.123Z'};
 const result=payload({status:'INTERESTED',assigneeId:lead.assigneeId,followUpAt:localDate(lead.followUpAt),note:'  Discuss requirements  '},lead);
 assert.deepEqual(result,{status:'INTERESTED',note:'Discuss requirements'});
 assert.equal(Object.hasOwn(result,'followUpAt'),false);
 assert.equal(Object.hasOwn(result,'assigneeId'),false);
});

test('lead edits distinguish an omitted unchanged value from clearing or rescheduling',()=>{
 assert.equal(Object.hasOwn(payload({followUpAt:'',note:''},{followUpAt:null}),'followUpAt'),false);
 assert.equal(payload({followUpAt:'',note:''},{followUpAt:'2020-01-02T03:04:57.123Z'}).followUpAt,null);
 assert.equal(payload({assigneeId:'',followUpAt:'',note:''},{assigneeId:'expired-member'}).assigneeId,null);
 assert.equal(payload({assigneeId:'active-member',followUpAt:'',note:''},{}).assigneeId,'active-member');
 const fresh='2090-02-03T04:05';
 assert.equal(payload({followUpAt:fresh,note:''},{followUpAt:null}).followUpAt,new Date(fresh).toISOString());
});

test('team candidate and note continuations enforce current capabilities on every page',async()=>{
 const demo=createTeamDemo(),root='/teams/team-studio';
 const candidates=await demo.request(root+'/candidates?q=&size=1');
 assert.equal(candidates.items.length,1);assert.equal(candidates.hasMore,true);
 demo.setActor('member');
 await assert.rejects(demo.request(root+'/candidates?q=&size=1&page=1'),error=>error.status===403);
 await assert.rejects(demo.request(root+'/workspace?collection=leads&page=1'),error=>error.status===403);
 await assert.rejects(demo.request(root+'/leads/lead-portal/notes?page=1'),error=>error.status===403);
 demo.setActor('business');
 assert.equal((await demo.request(root+'/leads/lead-portal/notes?size=1')).items.length,1);
});

test('business ownership and tenant access remain enforced for retained collection paths',async()=>{
 const demo=createBusinessDemo(),root='/businesses/business-sample';
 assert.equal((await demo.request(root+'/requests?size=1')).hasMore,true);
 demo.setActor('manager');
 await assert.rejects(demo.request(root+'/invitations',{method:'POST',body:JSON.stringify({email:'outsider@example.test'})}),error=>error.status===403);
 assert.equal((await demo.request(root+'/requests?size=1&page=1')).items.length,1);
 demo.setActor('outsider');
 for(const path of [root+'?page=1',root+'/requests?page=1',root+'/requests/brief-portal/shortlist?page=1',root+'/talent-lists/list-portal/entries?page=1'])await assert.rejects(demo.request(path),error=>error.status===404);
});
