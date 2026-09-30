import test from 'node:test';
import assert from 'node:assert/strict';
import { createTeamDemo } from '../lib/team-demo.ts';
const team='/teams/team-studio';
const send=(d,path,body,method='POST')=>d.request(path,{method,body:JSON.stringify(body)});
const denied=(p,status=403)=>assert.rejects(p,e=>e.status===status);
const invitation={userId:'applicant',role:'MEMBER',membershipType:'PERMANENT'};
const newTeam={name:'Second studio',summary:'A focused engineering team.',availability:'AVAILABLE_NOW',projectRange:'$5k–$10k'};
const inquiry={title:'Client portal',description:'A portal for approvals',budget:'$10k',timeline:'2 months'};

test('seed public/private projections, deep-copy isolation and reset',async()=>{
 const d=createTeamDemo();const pub=await d.request('/api/v1'+team);assert.equal(pub.team.memberCount,5);assert.equal(pub.projects.length,1);assert.deepEqual(pub.reviews,[]);assert.equal(pub.leads,undefined);
 pub.team.name='Tampered';assert.equal((await d.request(team)).team.name,'Northstar Studio');
 const owner=await d.request(team+'/workspace');assert.equal(owner.leads.length,1);
 d.setActor('member');const member=await d.request(team+'/workspace');assert.deepEqual(member.leads,[]);assert.deepEqual(member.applications,[]);assert.deepEqual(member.staffing,[]);
 d.setActor('client');await denied(d.request(team+'/workspace'));assert.deepEqual((await d.request('/me/teams')).requests[0].notes,[]);
 d.reset();assert.equal((await d.request(team+'/workspace')).myRole,'OWNER');
});
test('creation eligibility and multi-team ownership',async()=>{
 const d=createTeamDemo();for(const actor of ['client','unverified','recruiter']){d.setActor(actor);await denied(send(d,'/teams',newTeam));}
 d.setActor('owner');await send(d,'/teams',newTeam);assert.equal((await d.request('/me/teams')).items.length,2);
});
test('recipient consent, role boundaries, decline and no forced joining',async()=>{
 const d=createTeamDemo();d.setActor('recruiter');await denied(send(d,team+'/invitations',{...invitation,role:'BUSINESS_MANAGER'}));
 const i=await send(d,team+'/invitations',invitation);assert.equal((await d.request(team)).team.memberCount,5);
 await denied(send(d,`/team-invitations/${i.id}/respond`,{action:'ACCEPT'}));d.setActor('applicant');await send(d,`/team-invitations/${i.id}/respond`,{action:'DECLINE'});await denied(d.request(team+'/workspace'));
 d.setActor('owner');const next=await send(d,team+'/invitations',invitation);d.setActor('applicant');await send(d,`/team-invitations/${next.id}/respond`,{action:'ACCEPT'});assert.equal((await d.request(team+'/workspace')).myRole,'MEMBER');
 await denied(send(d,team+'/members/member',{role:'RECRUITER'},'PATCH'));d.setActor('owner');await denied(send(d,team+'/members/member',{role:'OWNER'},'PATCH'),400);await denied(d.request(team+'/members/owner',{method:'DELETE'}),400);
 await send(d,team+'/members/member',{role:'RECRUITER'},'PATCH');assert.equal((await d.request(team+'/workspace')).members.find(m=>m.userId==='member').role,'RECRUITER');
});
test('invitation and contract access expire at query time',async()=>{
 const d=createTeamDemo(),clock=Date.now;const end=new Date(clock()+60000).toISOString();
 await denied(send(d,team+'/invitations',{...invitation,membershipType:'CONTRACT'}),400);
 const i=await send(d,team+'/invitations',{...invitation,membershipType:'CONTRACT',expiresAt:end});d.setActor('applicant');
 try{Date.now=()=>clock()+120000;await denied(send(d,`/team-invitations/${i.id}/respond`,{action:'ACCEPT'}),400);assert.equal((await d.request('/me/teams')).invitations[0].status,'EXPIRED');}finally{Date.now=clock;}
 d.reset();const j=await send(d,team+'/invitations',{...invitation,membershipType:'CONTRACT',expiresAt:end});d.setActor('applicant');await send(d,`/team-invitations/${j.id}/respond`,{action:'ACCEPT'});
 try{Date.now=()=>clock()+120000;await denied(d.request(team+'/workspace'));assert.equal((await d.request('/me/teams')).items.length,0);assert.equal((await d.request(team)).team.memberCount,5);}finally{Date.now=clock;}
});
test('application decision produces invitation requiring consent, tenant scoping',async()=>{
 const d=createTeamDemo();const other=await send(d,'/teams',newTeam);d.setActor('applicant');const a=await send(d,team+'/roles/role-frontend/applications',{message:'I build accessible React products.'});
 d.setActor('owner');await denied(send(d,`/teams/${other.id}/applications/${a.id}/decision`,{status:'INVITED'}),404);
 await send(d,`${team}/applications/${a.id}/decision`,{status:'SHORTLISTED'});await send(d,`${team}/applications/${a.id}/decision`,{status:'INVITED'});
 d.setActor('applicant');await denied(d.request(team+'/workspace'));const mine=await d.request('/me/teams');assert.equal(mine.invitations.length,1);await send(d,`/team-invitations/${mine.invitations[0].id}/respond`,{action:'ACCEPT'});assert.equal((await d.request(team+'/workspace')).myRole,'MEMBER');
});
test('CRM self-inquiry, commercial permissions, atomic validation, transitions and privacy',async()=>{
 const d=createTeamDemo();await denied(send(d,team+'/leads',inquiry));d.setActor('unverified');await denied(send(d,team+'/leads',inquiry));d.setActor('client');const lead=await send(d,team+'/leads',inquiry);
 await denied(send(d,`${team}/leads/${lead.id}`,{status:'WON'},'PATCH'));d.setActor('pm');await denied(send(d,`${team}/leads/${lead.id}`,{note:'Private'},'PATCH'));
 d.setActor('owner');await denied(send(d,`${team}/leads/${lead.id}`,{status:'WON'},'PATCH'),409);await denied(send(d,`${team}/leads/${lead.id}`,{status:'INTERESTED',assigneeId:'applicant'},'PATCH'),400);assert.equal((await d.request(team+'/workspace')).leads.find(l=>l.id===lead.id).status,'NEW');
 for(const status of ['INTERESTED','PROPOSAL_SENT','WON'])await send(d,`${team}/leads/${lead.id}`,{status,assigneeId:'business',note:'Private client context',followUpAt:new Date(Date.now()+86400000).toISOString()},'PATCH');
 d.setActor('member');assert.ok(!(JSON.stringify(await d.request(team+'/workspace'))).includes('Private client context'));d.setActor('client');assert.ok(!(JSON.stringify(await d.request('/me/teams'))).includes('Private client context'));assert.deepEqual((await d.request(team)).reviews,[]);
});
test('product owner consent, tenant isolation, staffing permissions and expiry',async()=>{
 const d=createTeamDemo();await denied(send(d,team+'/projects',{productId:'product-member'}));d.setActor('member');const p=await send(d,team+'/projects',{productId:'product-member'});assert.equal(p.id,'product-member');await denied(send(d,team+'/staffing',{userId:'member',projectLabel:'Portal',endsAt:new Date(Date.now()+86400000).toISOString()}));
 d.setActor('pm');await denied(send(d,team+'/staffing',{userId:'applicant',projectLabel:'Portal',endsAt:new Date(Date.now()+86400000).toISOString()}),400);const s=await send(d,team+'/staffing',{userId:'member',projectLabel:'Portal',skills:['React'],endsAt:new Date(Date.now()+86400000).toISOString()});await send(d,team+'/staffing/'+s.id,{status:'COMPLETED'},'PATCH');
 d.setActor('owner');const other=await send(d,'/teams',newTeam);await denied(send(d,`/teams/${other.id}/staffing/${s.id}`,{status:'COMPLETED'},'PATCH'),404);await denied(send(d,`/teams/${other.id}/leads/lead-portal`,{note:'Wrong tenant'},'PATCH'),404);
 d.setActor('member');await d.request(team+'/projects/product-member',{method:'DELETE'});assert.equal((await d.request(team)).projects.length,1);
});
test('moderation blocks public listing, recruitment and new interest with audit',async()=>{
 const d=createTeamDemo();await denied(send(d,'/admin/teams/team-studio/moderate',{status:'SUSPENDED',reason:'Sample moderation review'}));d.setActor('admin');assert.equal((await d.request('/admin/teams')).items.length,1);await send(d,'/admin/teams/team-studio/moderate',{status:'SUSPENDED',reason:'Sample moderation review'});assert.equal((await d.request('/teams')).items.length,0);await denied(d.request(team),404);
 d.setActor('owner');await denied(send(d,team+'/invitations',invitation));assert.ok((await d.request(team+'/workspace')).activity.some(a=>a.action==='TEAM_MODERATED'));d.setActor('client');await denied(send(d,team+'/leads',inquiry));d.setActor('admin');await send(d,'/admin/teams/team-studio/moderate',{status:'ACTIVE',reason:'Review complete'});assert.equal((await d.request('/teams')).items.length,1);
});
test('open role lifecycle and search',async()=>{
 const d=createTeamDemo();const role=await send(d,team+'/roles',{title:'API developer',description:'Build secure APIs',skills:['Java'],contractType:'CONTRACT',compensationBand:'$80/hour'});assert.equal((await d.request(team+'/candidates?q=Amara')).items[0].id,'applicant');await send(d,team+'/roles/'+role.id,{status:'CLOSED'},'PATCH');d.setActor('applicant');await denied(send(d,team+'/roles/'+role.id+'/applications',{message:'Interested'}),400);assert.equal((await d.request('/teams?q=Northstar')).items.length,1);assert.equal((await d.request('/teams?q=nonexistent')).items.length,0);
});
test('account compatibility, application contract terms and final decisions',async()=>{
 const d=createTeamDemo();const me=await d.request('/me');assert.equal(me.profile.approval_status,'APPROVED');assert.ok(me.roles.includes('DEVELOPER'));assert.equal((await d.request('/developer/products')).items[0].id,'product-owner');
 d.setActor('applicant');const a=await send(d,team+'/roles/role-frontend/applications',{message:'Accessible React development'});assert.equal(a.status,'APPLIED');d.setActor('owner');await send(d,team+'/applications/'+a.id+'/decision',{status:'SHORTLISTED'});await denied(send(d,team+'/applications/'+a.id+'/decision',{status:'SHORTLISTED'}),409);await send(d,team+'/applications/'+a.id+'/decision',{status:'INVITED'});d.setActor('applicant');const i=(await d.request('/me/teams')).invitations[0];assert.equal(i.membershipType,'CONTRACT');assert.ok(Date.parse(i.expiresAt)>Date.now());
 d.setActor('unverified');assert.equal((await d.request('/me')).emailVerified,false);assert.deepEqual((await d.request('/developer/products')).items,[]);
});
test('permanent invitations also expire; closed roles cannot issue invitations',async()=>{
 const d=createTeamDemo(),clock=Date.now;const i=await send(d,team+'/invitations',invitation);d.setActor('applicant');try{Date.now=()=>clock()+15*86400000;await denied(send(d,'/team-invitations/'+i.id+'/respond',{action:'ACCEPT'}),400);}finally{Date.now=clock;}
 d.reset();d.setActor('applicant');const a=await send(d,team+'/roles/role-frontend/applications',{message:'Please consider my application'});d.setActor('owner');await send(d,team+'/roles/role-frontend',{status:'CLOSED'},'PATCH');await denied(send(d,team+'/applications/'+a.id+'/decision',{status:'INVITED'}),404);assert.equal((await d.request(team+'/workspace')).invitations.length,0);
});
test('membership departure revokes old consent, clears assignee and completes staffing',async()=>{
 const d=createTeamDemo();await send(d,team+'/leads/lead-portal',{assigneeId:'member'},'PATCH');d.setActor('member');await send(d,team+'/projects',{productId:'product-member'});d.setActor('pm');const staffing=await send(d,team+'/staffing',{userId:'member',projectLabel:'Portal',skills:'React',endsAt:new Date(Date.now()+86400000).toISOString()});d.setActor('member');await d.request(team+'/members/member',{method:'DELETE'});d.setActor('owner');const w=await d.request(team+'/workspace');assert.equal(w.leads[0].assigneeId,null);assert.equal(w.staffing.find(s=>s.id===staffing.id).status,'COMPLETED');assert.equal(w.projects.length,1);const i=await send(d,team+'/invitations',{...invitation,userId:'member'});d.setActor('member');await send(d,'/team-invitations/'+i.id+'/respond',{action:'ACCEPT'});assert.equal((await d.request(team)).projects.length,1);
});
test('terminal staffing cannot be reopened and restricted teams cannot mutate operations',async()=>{
 const d=createTeamDemo();const s=await send(d,team+'/staffing',{userId:'member',projectLabel:'Portal',skills:'React',endsAt:new Date(Date.now()+86400000).toISOString()});await send(d,team+'/staffing/'+s.id,{status:'COMPLETED'},'PATCH');await denied(send(d,team+'/staffing/'+s.id,{status:'ACTIVE'},'PATCH'),400);
 d.setActor('admin');await send(d,'/admin/teams/team-studio/moderate',{status:'SUSPENDED',reason:'Review required'});d.setActor('owner');await denied(send(d,team,newTeam,'PUT'),409);await denied(send(d,team+'/leads/lead-portal',{note:'Cannot update'},'PATCH'),409);await denied(send(d,team+'/staffing/'+s.id,{status:'COMPLETED'},'PATCH'),409);d.setActor('member');await denied(send(d,team+'/projects',{productId:'product-member'}),409);
});
test('independent demo instances and malformed input leave state untouched',async()=>{
 const d=createTeamDemo(),other=createTeamDemo();await send(d,team,{...newTeam,name:'Changed studio'},'PUT');assert.equal((await other.request(team)).team.name,'Northstar Studio');await denied(d.request(team,{method:'PUT',body:'[]'}),400);await denied(d.request(team,{method:'PUT',body:'null'}),400);await denied(d.request(team,{method:'PUT',body:'{' }),400);assert.equal((await d.request(team)).team.name,'Changed studio');
 await denied(send(d,team+'/leads/lead-portal',{status:'',note:'Must not persist'},'PATCH'),409);assert.ok(!JSON.stringify(await d.request(team+'/workspace')).includes('Must not persist'));
});

test('actor metadata cannot alter permissions and malformed routes cannot mutate',async()=>{
 const d=createTeamDemo();d.actors.find(a=>a.id==='owner').id='impostor';assert.throws(()=>d.setActor('impostor'));assert.equal((await d.request('/me')).id,'owner');await denied(send(d,team+'/projects/extra',{productId:'product-owner'}),404);await denied(send(d,team+'/members/member/extra',{role:'RECRUITER'},'PATCH'),404);assert.equal((await d.request(team+'/workspace')).members.find(m=>m.userId==='member').role,'MEMBER');
});

test('only original verified client can acknowledge an awaiting proposal',async()=>{
 const d=createTeamDemo();d.setActor('client');const lead=await send(d,team+'/leads',inquiry);const path=team+'/leads/'+lead.id;
 await denied(send(d,path+'/respond',{action:'ACCEPT'}),409);d.setActor('owner');await send(d,path,{status:'INTERESTED'},'PATCH');await send(d,path,{status:'PROPOSAL_SENT',note:'private terms'},'PATCH');await denied(send(d,path+'/respond',{action:'ACCEPT'}),404);
 d.setActor('client');const result=await send(d,path+'/respond',{action:'ACCEPT'});assert.equal(result.status,'WON');assert.deepEqual(result.notes,[]);await denied(send(d,path+'/respond',{action:'DECLINE'}),409);assert.deepEqual((await d.request(team)).reviews,[]);
});
