import {test,expect,type Page,type Route} from '@playwright/test';

const first='11111111-1111-4111-8111-111111111111',second='22222222-2222-4222-8222-222222222222';
const builder='33333333-3333-4333-8333-333333333333';
const inquiry=(id:string)=>({id,clientName:id===first?'First client':'Second client',clientEmail:'client@example.test',description:id===first?'First private qualified brief':'Second private qualified brief',budgetBand:'NEED_ESTIMATE',timelineBand:'FLEXIBLE',status:'INQUIRY_RECEIVED',projectTitle:'A reviewed project',updatedAt:'2026-01-01T00:00:00Z'});
const list=(items=[inquiry(first),inquiry(second)],hasMore=false)=>({items,page:0,size:20,hasMore,totalItems:hasMore?21:items.length,totalPages:hasMore?2:items.length?1:0});
const user={id:builder,displayName:'Current builder',email:'builder@example.test',emailVerified:true,roles:['DEVELOPER','CLIENT'],activeSlotLimit:3,profile:{approval_status:'APPROVED',display_name:'Current builder'}};
type Held={path:string;route:Route};
const pageErrors=new WeakMap<Page,string[]>();
test.beforeEach(async({page})=>{const errors:string[]=[];pageErrors.set(page,errors);page.on('pageerror',error=>errors.push(error.message));});
test.afterEach(async({page})=>{expect(pageErrors.get(page)).toEqual([]);});
async function expectApplicationDocument(page:Page){
 expect(await page.locator('meta[name="viewport"]').getAttribute('content')).toContain('width=device-width');
 expect(await page.locator('link[rel="stylesheet"]').count()).toBeGreaterThan(0);
}

async function mount(page:Page,{more=false,filterStatus=200,holdContinuation=false}:{more?:boolean;filterStatus?:number;holdContinuation?:boolean}={}){
 const held:Held[]=[],requests:string[]=[];
 await page.route('**/api/v1/**',async route=>{
  const url=new URL(route.request().url()),path=url.pathname.replace('/api/v1','');requests.push(path+url.search);
  if(path==='/me')return route.fulfill({json:user});
  if(path.startsWith('/developer/inquiries/')){held.push({path,route});return;}
  if(path==='/developer/inquiries'){
   if(url.searchParams.get('page')==='1'&&holdContinuation){held.push({path:path+url.search,route});return;}
   if(url.searchParams.has('status'))return route.fulfill({status:filterStatus,json:filterStatus===200?list([{...inquiry(second),status:url.searchParams.get('status')!}]):{error:{message:'Builder authority lost.',code:'FORBIDDEN'}}});
   return route.fulfill({json:list(undefined,more)});
  }
  if(path==='/developer/analytics')return route.fulfill({json:{qualifiedInquiries:2,responseSample:0}});
  return route.fulfill({json:{...list([]),totalOwned:0,activeCount:0}});
 });
 // This is the real Next route, so it includes production components, compiled
 // application styles, viewport metadata and the actual Radix dialog lifecycle.
 await page.goto('/workspace?tab=inquiries');
 await expect(page.getByRole('heading',{name:'Qualified inquiries',exact:true})).toBeVisible();
 await expect(page.getByText('First private qualified brief',{exact:true})).toBeVisible();
 await expectApplicationDocument(page);
 return {held,requests};
}
async function history(page:Page,client:string){await page.locator('.row').filter({has:page.getByRole('heading',{name:client+' · A reviewed project',exact:true})}).getByRole('button',{name:'History',exact:true}).click();}
async function answer(held:Held[],path:string,event:string,status=200){
 await expect.poll(()=>held.some(item=>item.path===path)).toBe(true);
 const row=held.splice(held.findIndex(item=>item.path===path),1)[0];
 await row.route.fulfill({status,json:status===200?{events:[{event_type:event,actor_type:'DEVELOPER',created_at:'2026-01-01T00:00:00Z'}]}:{error:{message:'Builder authority lost.',code:'FORBIDDEN'}}});await (await row.route.request().response())?.finished();
}
async function settle(page:Page){await page.evaluate(()=>new Promise<void>(resolve=>requestAnimationFrame(()=>requestAnimationFrame(()=>resolve()))));}

test('qualified inquiry history keeps the latest selection and a closed dialog stays closed after an older response',async({page})=>{
 const {held}=await mount(page);await expect(page.getByRole('button',{name:'Load more',exact:true})).toHaveCount(0);
 await history(page,'First client');await expect.poll(()=>held.length).toBe(1);await history(page,'Second client');await expect.poll(()=>held.length).toBe(2);
 await answer(held,'/developer/inquiries/'+second,'IN_PROGRESS');await expect(page.getByRole('dialog')).toContainText('In Progress');
 await page.keyboard.press('Escape');await expect(page.getByRole('dialog')).toHaveCount(0);
 await answer(held,'/developer/inquiries/'+first,'RESPONDED');await settle(page);await expect(page.getByRole('dialog')).toHaveCount(0);
 // A new current read remains usable after cancelling the earlier context.
 await history(page,'Second client');await answer(held,'/developer/inquiries/'+second,'COMPLETED');await expect(page.getByRole('dialog')).toContainText('Completed');
});

test('an older history cannot replace the currently displayed inquiry history',async({page})=>{
 const {held}=await mount(page);await history(page,'First client');await expect.poll(()=>held.length).toBe(1);await history(page,'Second client');await answer(held,'/developer/inquiries/'+second,'IN_PROGRESS');await expect(page.getByRole('dialog')).toContainText('In Progress');
 await answer(held,'/developer/inquiries/'+first,'RESPONDED');await settle(page);await expect(page.getByRole('dialog')).toContainText('In Progress');await expect(page.getByRole('dialog')).not.toContainText('Responded');
});

for(const status of [401,403])test(`qualified history denial (${status}) clears private workspace data and invalidates an older pending read`,async({page})=>{
 const {held}=await mount(page);await history(page,'First client');await expect.poll(()=>held.length).toBe(1);await history(page,'Second client');await answer(held,'/developer/inquiries/'+second,'',status);
 await expect(page.getByRole('heading',{name:'Make your best work visible.',exact:true})).toBeVisible();await expect(page.getByRole('alert')).toHaveText('Builder authority lost.');await expect(page.getByText('First private qualified brief',{exact:true})).toHaveCount(0);
 await answer(held,'/developer/inquiries/'+first,'RESPONDED');await settle(page);await expect(page.getByRole('dialog')).toHaveCount(0);
});

test('filter denial clears cached qualified briefs and offers authentication recovery',async({page})=>{
 const {requests}=await mount(page,{filterStatus:403});await page.getByLabel('Status',{exact:true}).selectOption('DISCUSSION');await page.getByRole('button',{name:'Apply filters',exact:true}).click();
 await expect.poll(()=>requests.includes('/developer/inquiries?status=DISCUSSION')).toBe(true);await expect(page.getByRole('heading',{name:'Make your best work visible.',exact:true})).toBeVisible();await expect(page.getByRole('link',{name:'Log in',exact:true}).last()).toBeVisible();await expect(page.getByText('First private qualified brief',{exact:true})).toHaveCount(0);
});

test('filter replacement discards a delayed continuation and carries the confirmed page metadata',async({page})=>{
 const {held,requests}=await mount(page,{more:true,holdContinuation:true});await page.getByRole('button',{name:'Load more',exact:true}).click();await expect.poll(()=>held.length).toBe(1);
 await page.getByLabel('Status',{exact:true}).selectOption('DISCUSSION');await page.getByRole('button',{name:'Apply filters',exact:true}).click();await expect.poll(()=>requests.includes('/developer/inquiries?status=DISCUSSION')).toBe(true);await expect(page.getByText('First private qualified brief',{exact:true})).toHaveCount(0);await expect(page.getByRole('button',{name:'Load more',exact:true})).toHaveCount(0);
 const pending=held.shift()!;await pending.route.fulfill({json:{...list([inquiry(first)]),page:1,hasMore:false}});await settle(page);await expect(page.getByText('First private qualified brief',{exact:true})).toHaveCount(0);await expect(page.getByText('Second private qualified brief',{exact:true})).toBeVisible();
});

test('a transient history failure retains current briefs and a subsequent read recovers',async({page})=>{
 const {held}=await mount(page);await history(page,'First client');await expect.poll(()=>held.length).toBe(1);await held.shift()!.route.fulfill({status:503,json:{error:{message:'History temporarily unavailable.',code:'UNAVAILABLE'}}});
 await expect(page.getByRole('alert')).toContainText('History temporarily unavailable.');await expect(page.getByText('First private qualified brief',{exact:true})).toBeVisible();await history(page,'First client');await answer(held,'/developer/inquiries/'+first,'RESPONDED');await expect(page.getByRole('dialog')).toContainText('Responded');
});

async function mountAccount(page:Page){
 const held:Held[]=[],requests:string[]=[];let reads=0;
 await page.route('**/api/v1/**',async route=>{
  const url=new URL(route.request().url()),path=url.pathname.replace('/api/v1','');requests.push(path+url.search);
  if(path==='/me'&&route.request().method()==='DELETE'){held.push({path:'/me:DELETE',route});return;}
  if(path==='/me')return route.fulfill({json:user});
  if(path==='/notifications'&&!url.searchParams.has('page')){reads++;return route.fulfill({json:{...list([]),items:[{id:first,title:reads===1?'Private account notification':'Recovered account notification',created_at:'2026-01-01T00:00:00Z'}],size:1,hasMore:reads===1,totalItems:reads===1?2:1,totalPages:reads===1?2:1}});}
  if(path==='/me/export'||path==='/notifications/'+first+'/read'||(path==='/notifications'&&url.searchParams.has('page'))){held.push({path:path+url.search,route});return;}
  if(path==='/developer/inquiries')return route.fulfill({json:list([])});
  if(path==='/developer/analytics')return route.fulfill({json:{qualifiedInquiries:0,responseSample:0}});
  return route.fulfill({json:{...list([]),totalOwned:0,activeCount:0}});
 });
 await page.goto('/workspace?tab=account');await expect(page.getByText('Private account notification',{exact:true})).toBeVisible();await expectApplicationDocument(page);
 return{held,requests};
}
async function deny(held:Held[],path:string,status:number){await expect.poll(()=>held.some(item=>item.path===path)).toBe(true);const pending=held.splice(held.findIndex(item=>item.path===path),1)[0];await pending.route.fulfill({status,json:{error:{message:'Account session expired.',code:'UNAUTHENTICATED'}}});}

for(const status of [401,403])test(`notification write denial (${status}) clears private rows, cancels earlier export publication and recovers by reading`,async({page})=>{
 const {held,requests}=await mountAccount(page),downloads:string[]=[];page.on('download',download=>downloads.push(download.suggestedFilename()));
 await page.getByRole('button',{name:'Download my account data',exact:true}).click();await expect.poll(()=>held.some(item=>item.path==='/me/export')).toBe(true);
 await page.getByRole('button',{name:'Mark read',exact:true}).click();await deny(held,'/notifications/'+first+'/read',status);
 const panel=page.locator('.account-notifications');await expect(panel.getByRole('alert')).toHaveText('Account session expired.');await expect(page.locator('.account-notification')).toHaveCount(0);await expect(page.getByRole('button',{name:'Load more notifications',exact:true})).toHaveCount(0);
 const pending=held.splice(held.findIndex(item=>item.path==='/me/export'),1)[0];await pending.route.fulfill({json:{account:{id:builder,email:'builder@example.test'}}});await settle(page);expect(downloads).toEqual([]);await expect(page.getByText('Your account export is ready.',{exact:true})).toHaveCount(0);
 await page.getByRole('button',{name:'Retry notifications',exact:true}).click();await expect(page.getByText('Recovered account notification',{exact:true})).toBeVisible();await expect(panel.getByRole('alert')).toHaveCount(0);expect(requests.filter(path=>path==='/notifications').length).toBe(2);expect(requests.filter(path=>path==='/notifications/'+first+'/read').length).toBe(1);
});

for(const status of [401,403])test(`notification continuation denial (${status}) clears previously visible private rows`,async({page})=>{
 const {held}=await mountAccount(page);await page.getByRole('button',{name:'Load more notifications',exact:true}).click();await deny(held,'/notifications?page=1&size=1',status);await expect(page.locator('.account-notifications').getByRole('alert')).toHaveText('Account session expired.');await expect(page.locator('.account-notification')).toHaveCount(0);await expect(page.getByRole('button',{name:'Retry notifications',exact:true})).toBeEnabled();
});

test('account closure acknowledgement cannot republish after a newer notification authority denial',async({page})=>{
 const {held}=await mountAccount(page);await page.getByText('Close my account',{exact:true}).click();await page.getByLabel('Type your email to request a confirmation link',{exact:true}).fill('builder@example.test');await page.getByRole('button',{name:'Email me a closure confirmation',exact:true}).click();await expect.poll(()=>held.some(item=>item.path==='/me:DELETE')).toBe(true);
 await page.getByRole('button',{name:'Mark read',exact:true}).click();await deny(held,'/notifications/'+first+'/read',401);const pending=held.splice(held.findIndex(item=>item.path==='/me:DELETE'),1)[0];await pending.route.fulfill({status:202,json:{status:'EMAIL_CONFIRMATION_REQUIRED'}});await (await pending.route.request().response())?.finished();await settle(page);await expect(page.getByText(/Check your email to confirm account closure/)).toHaveCount(0);await expect(page.locator('.account-notification')).toHaveCount(0);
});
