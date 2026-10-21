import test from 'node:test';
import assert from 'node:assert/strict';
import {createTeamDemo} from '../lib/team-demo.ts';

const base='/teams/team-studio';
const send=(demo,path,body,method='POST')=>demo.request(path,{method,body:JSON.stringify(body)});
const profile={name:'Paging studio',summary:'A team building practical software.',availability:'AVAILABLE_NOW',projectRange:'5k–10k'};
const role={title:'API builder',description:'Build accessible software with our team.',skills:'Java',contractType:'PERMANENT',compensationBand:'5k'};
async function allPages(d,path,key){
 const result=[],ids=new Set();let page=0,total;
 for(;;){const data=await d.request(path+`&page=${page}&size=17`),meta=key?data.pages[key]:data,rows=key?data[key]:data.items;
  assert.equal(meta.page,page);assert.equal(meta.size,17);assert.ok(rows.length<=17);total=meta.totalItems;
  for(const row of rows){const id=row.id||row.userId;assert.ok(!ids.has(id),'pages must not repeat records');ids.add(id);result.push(row)}
  if(!meta.hasMore)break;assert.ok(++page<100,'pagination must terminate');
 }
 assert.equal(result.length,total);return result;
}

test('team index, directory, roles and activity keep full history reachable beyond old limits',async()=>{
 const d=createTeamDemo();
 for(let n=0;n<126;n++)await send(d,'/teams',{...profile,name:`Paging studio ${n}`});
 assert.equal((await d.request('/me/teams')).items.length,50);
 assert.equal((await allPages(d,'/me/teams?collection=items','items')).length,127);
 assert.equal((await allPages(d,'/teams?q=Paging')).length,126);
 for(let n=0;n<126;n++)await send(d,base+'/roles',{...role,title:`Role ${n}`});
 assert.equal((await allPages(d,base+'?collection=roles','roles')).length,127);
 assert.equal((await allPages(d,base+'/workspace?collection=roles','roles')).length,127);
 assert.equal((await allPages(d,base+'/workspace?collection=activity','activity')).length,126);
 const selected=await d.request(base+'/workspace?collection=roles&size=7');
 assert.equal(selected.roles.length,7);assert.equal(selected.members,undefined);assert.equal(selected.leads,undefined);
 assert.equal(selected.pages.roles.totalItems,127);
});

test('leads and private notes are bounded while every note remains independently reachable',async()=>{
 const d=createTeamDemo();d.setActor('client');
 for(let n=0;n<121;n++)await send(d,base+'/leads',{title:`Portal ${n}`,description:'A portal for customer approvals.',budget:'10k',timeline:'2 months'});
 const requests=await allPages(d,'/me/teams?collection=requests','requests');assert.equal(requests.length,122);
 assert.ok(requests.every(row=>row.notes.length===0&&row.assigneeId===null&&row.followUpAt===null));
 d.setActor('owner');
 for(let n=0;n<32;n++){
  const updated=await send(d,base+'/leads/lead-portal',{note:`Private note ${n}`},'PATCH');
  assert.ok(updated.notes.length<=5);
 }
 const workspace=await d.request(base+'/workspace?collection=leads&size=100');assert.equal(workspace.leads.length,100);assert.equal(workspace.pages.leads.totalItems,122);
 const leads=await allPages(d,base+'/workspace?collection=leads','leads'),lead=leads.find(l=>l.id==='lead-portal');
 assert.equal(lead.notes.length,5);assert.equal(lead.notesPage.totalItems,33);assert.equal(lead.notesPage.hasMore,true);
 const notes=await allPages(d,base+'/leads/lead-portal/notes?');assert.equal(notes.length,33);assert.equal(notes.at(-1).text,'Private note 31');
 const other=await send(d,'/teams',profile);await assert.rejects(d.request(`/teams/${other.id}/leads/lead-portal/notes`),e=>e.status===404);
 d.setActor('client');await assert.rejects(d.request(base+'/leads/lead-portal/notes?page=1'),e=>e.status===403);
});

test('recruitment histories and staffing remain reachable in their own authorized collections',async()=>{
 const d=createTeamDemo();
 for(let n=0;n<105;n++){
  d.setActor('owner');const open=await send(d,base+'/roles',{...role,title:`Recruitment ${n}`});
  const invite=await send(d,base+'/invitations',{userId:'applicant',role:'MEMBER',membershipType:'PERMANENT'});
  await send(d,base+'/staffing',{userId:'member',projectLabel:`Assignment ${n}`,skills:'React',endsAt:new Date(Date.now()+86400000).toISOString()});
  d.setActor('applicant');await send(d,`/team-invitations/${invite.id}/respond`,{action:'DECLINE'});
  await send(d,base+'/roles/'+open.id+'/applications',{message:'I build accessible products.'});
 }
 assert.equal((await allPages(d,'/me/teams?collection=invitations','invitations')).length,105);
 assert.equal((await allPages(d,'/me/teams?collection=applications','applications')).length,105);
 d.setActor('owner');
 for(const collection of ['invitations','applications','staffing'])assert.equal((await allPages(d,base+'/workspace?collection='+collection,collection)).length,105);
 d.setActor('recruiter');await assert.rejects(d.request(base+'/workspace?collection=staffing&page=1'),e=>e.status===403);
 d.setActor('pm');await assert.rejects(d.request(base+'/workspace?collection=applications&page=1'),e=>e.status===403);
});

test('visible role application lookups include old applications on every public roles page',async()=>{
 const d=createTeamDemo(),roleIds=[];
 for(let n=0;n<53;n++)roleIds.push((await send(d,base+'/roles',{...role,title:`Application role ${n}`})).id);
 d.setActor('applicant');
 for(const roleId of roleIds.slice().reverse())await send(d,base+'/roles/'+roleId+'/applications',{message:'I build accessible products.'});
 const history=await d.request('/me/teams');assert.equal(history.applications.length,50);assert.equal(history.pages.applications.totalItems,53);
 assert.ok(!history.applications.some(a=>a.roleId===roleIds.at(-1)),'the oldest application must be outside the history preview');
 for(let page=0;page<2;page++){
  const visible=await d.request(base+'?collection=roles&page='+page);
  const query=new URLSearchParams({collection:'applications',size:'100',roleIds:visible.roles.map(r=>r.id).join(',')});
  const matching=await d.request('/me/teams?'+query);
  assert.deepEqual(new Set(matching.applications.map(a=>a.roleId)),new Set(visible.roles.filter(r=>roleIds.includes(r.id)).map(r=>r.id)));
  assert.equal(matching.pages.applications.hasMore,false);assert.equal(matching.items,undefined);
 }
 const lookup='/me/teams?collection=applications&roleIds='+roleIds.at(-1);
 d.setActor('owner');assert.deepEqual((await d.request(lookup)).applications,[],'another actor cannot see the applicant status');
 for(const query of ['roleIds='+roleIds[0],'collection=items&roleIds='+roleIds[0],'collection=applications&roleIds=','collection=applications&roleIds='+Array(101).fill(roleIds[0]).join(',')])await assert.rejects(d.request('/me/teams?'+query),e=>e.status===400);
});

test('every continuation rechecks current role and filters activity before pagination',async()=>{
 const d=createTeamDemo();
 for(let n=0;n<22;n++)await send(d,base+'/leads/lead-portal',{note:'Commercial secret'},'PATCH');
 for(let n=0;n<4;n++)await send(d,base,{...profile,name:`Public update ${n}`},'PUT');
 d.setActor('member');const activity=await allPages(d,base+'/workspace?collection=activity','activity');
 assert.equal(activity.length,4);assert.ok(activity.every(a=>a.action==='TEAM_UPDATED'));assert.ok(!JSON.stringify(activity).includes('Commercial secret'));
 d.setActor('business');assert.equal((await d.request(base+'/workspace?collection=leads&size=1')).myRole,'BUSINESS_MANAGER');
 d.setActor('owner');await send(d,base+'/members/business',{role:'MEMBER'},'PATCH');d.setActor('business');
 await assert.rejects(d.request(base+'/workspace?collection=leads&page=1'),e=>e.status===403);
 await assert.rejects(d.request(base+'/leads/lead-portal/notes?page=1'),e=>e.status===403);
 const normal=await d.request(base+'/workspace');assert.deepEqual(normal.leads,[]);assert.equal(normal.pages.leads.totalItems,0);
});

test('expired access and malformed pagination fail closed on continuation',async()=>{
 const d=createTeamDemo(),clock=Date.now;
 const invite=await send(d,base+'/invitations',{userId:'applicant',role:'BUSINESS_MANAGER',membershipType:'CONTRACT',expiresAt:new Date(clock()+60000).toISOString()});
 d.setActor('applicant');await send(d,`/team-invitations/${invite.id}/respond`,{action:'ACCEPT'});
 assert.equal((await d.request(base+'/workspace?collection=leads&size=1')).myRole,'BUSINESS_MANAGER');
 try{Date.now=()=>clock()+120000;await assert.rejects(d.request(base+'/workspace?collection=leads&page=1'),e=>e.status===403);await assert.rejects(d.request(base+'/leads/lead-portal/notes?page=1'),e=>e.status===403)}finally{Date.now=clock}
 d.setActor('owner');for(const query of ['page=-1','page=100001','page=1.5','size=0','size=101','size=oops','collection=constructor','collection=leads']){
  await assert.rejects(d.request('/me/teams?'+query),e=>e.status===400);
 }
 const beyond=await d.request(base+'/workspace?collection=roles&page=999&size=2');assert.deepEqual(beyond.roles,[]);assert.equal(beyond.pages.roles.hasMore,false);assert.equal(beyond.pages.roles.totalItems,1);
});
