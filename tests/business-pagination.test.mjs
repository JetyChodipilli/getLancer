import test from 'node:test';
import assert from 'node:assert/strict';
import {createBusinessDemo} from '../lib/business-demo.ts';
import {appendBusinessRecords,businessCollectionRequest,businessPagePath} from '../lib/business.ts';
const root='/businesses/business-sample';
const send=(d,path,body,method='POST')=>d.request(path,{method,body:JSON.stringify(body)});
const fields={title:'Private customer portal',description:'Private workflow for customer approvals and delivery planning.',category:'CRM',technology:'React',budget:'10k',timeline:'2 months',availableOnly:true,repositoryVerifiedOnly:false,status:'OPEN'};
const recommendation={kind:'TEAM',targetId:'team-studio',reason:'Approved category and technology evidence fit the private brief.'};
async function collect(request,path,size=37){let result=await request(path+(path.includes('?')?'&':'?')+'size='+size),rows=[];const total=result.totalItems;let expectedPage=0;while(true){assert.equal(result.page,expectedPage++);assert.equal(result.size,size);assert.equal(result.totalItems,total);assert.equal(result.totalPages,Math.ceil(total/size));assert.ok(result.items.length<=size);rows=appendBusinessRecords(rows,result.items);if(!result.hasMore)break;result=await request(businessPagePath(path,result));}assert.equal(rows.length,total);return rows;}

test('more than 100 private requests, lists, workspaces and invitations are reachable through the demo adapter',async()=>{
 const d=createBusinessDemo();
 for(let i=0;i<121;i++){await send(d,root+'/requests',{...fields,title:'Private portal '+i});await send(d,root+'/talent-lists',{name:'Hiring list '+i});}
 const requests=await collect(d.request,root+'/requests');assert.equal(requests.length,123);assert.equal(requests[0].title,'Private portal 120');assert.equal(requests.at(-1).id,'brief-inventory');
 const lists=await collect(d.request,root+'/talent-lists');assert.equal(lists.length,122);assert.equal(lists.at(-1).id,'list-portal');
 const activity=await collect(businessCollectionRequest(d.request,'activity'),root);assert.equal(activity.length,243);assert.equal(activity.at(-1).action,'BUSINESS_CREATED');
 for(let i=0;i<105;i++){const b=await send(d,'/businesses',{name:'Private company '+i,summary:'A private company hiring workspace.'});await send(d,'/businesses/'+b.id+'/invitations',{email:'outsider@example.test'});}
 const index=await d.request('/businesses?size=37');assert.equal(index.items.length,37);assert.equal(index.itemsPage.totalItems,106);assert.ok(Array.isArray(index.invitations));assert.equal((await collect(businessCollectionRequest(d.request,'items'),'/businesses')).length,106);
 d.setActor('outsider');const invites=await collect(businessCollectionRequest(d.request,'invitations'),'/businesses');assert.equal(invites.length,106);assert.equal(invites.at(-1).id,'invite-alex');await assert.rejects(d.request(root+'?page=2'),e=>e.status===404);
});

test('completed sourcing cannot starve new requests, and fulfilled closed briefs retain paged history',async()=>{
 const d=createBusinessDemo();
 for(let i=0;i<105;i++){
  d.setActor('owner');const brief=await send(d,root+'/requests',{...fields,title:'Historic portal '+i});await send(d,root+'/requests/'+brief.id+'/concierge',{});
  d.setActor('admin');const c=(await d.request('/admin/concierge')).items[0],path='/admin/concierge/'+c.id;await send(d,path,{status:'IN_PROGRESS'},'PATCH');await send(d,path+'/shortlist',recommendation);await send(d,path,{status:'FULFILLED'},'PATCH');
  if(i%2===0){d.setActor('owner');await send(d,root+'/requests/'+brief.id,{...fields,title:brief.title,status:'CLOSED'},'PUT');}
 }
 d.setActor('owner');const first=await send(d,root+'/requests',{...fields,title:'Sourcing in progress'});await send(d,root+'/requests/'+first.id+'/concierge',{});d.setActor('admin');const progress=(await d.request('/admin/concierge')).items[0];await send(d,'/admin/concierge/'+progress.id,{status:'IN_PROGRESS'},'PATCH');
 d.setActor('owner');const newer=await send(d,root+'/requests',{...fields,title:'New urgent request'});await send(d,root+'/requests/'+newer.id+'/concierge',{});
 d.setActor('admin');const queue=await d.request('/admin/concierge?size=1');assert.equal(queue.items[0].requestId,newer.id);assert.equal(queue.itemsPage.totalItems,2);assert.equal(queue.historyPage.totalItems,105);assert.equal(queue.history.length,1);
 const active=await collect(businessCollectionRequest(d.request,'items'),'/admin/concierge',1);assert.deepEqual(active.map(r=>r.status),['REQUESTED','IN_PROGRESS']);
 const history=await collect(businessCollectionRequest(d.request,'history'),'/admin/concierge');assert.equal(history.length,105);assert.ok(history.every(r=>r.status==='FULFILLED'));
 // Paging history has no side effects on the actionable queue or retained recommendations.
 assert.equal((await d.request('/admin/concierge')).items.length,2);d.setActor('owner');await assert.rejects(d.request('/admin/concierge?page=2'),e=>e.status===403);
});

test('pagination bounds, out-of-range pages and adapter failures stay explicit',async()=>{
 const d=createBusinessDemo();
 for(const suffix of ['?page=-1','?page=100001','?page=1.2','?size=0','?size=101','?size=banana'])await assert.rejects(d.request(root+'/requests'+suffix),e=>e.status===400);
 const empty=await d.request(root+'/requests?page=99&size=1');assert.deepEqual(empty.items,[]);assert.equal(empty.hasMore,false);assert.equal(empty.totalItems,2);assert.equal(empty.page,99);
 const denied=businessCollectionRequest(async()=>{throw Object.assign(new Error('MFA required.'),{status:403});},'history');await assert.rejects(denied('/admin/concierge?page=1'),e=>e.status===403);
 assert.equal(businessPagePath('/businesses?filter=kept',{page:2,size:17}),'/businesses?filter=kept&page=3&size=17');
 const a={id:'a'},b={id:'b'};assert.deepEqual(appendBusinessRecords([a],[a,b,b]),[a,b]);
});
