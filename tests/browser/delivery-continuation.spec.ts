import {test,expect,type Page,type Route} from '@playwright/test';
import type {Engagement,EngagementDetail} from '../../lib/delivery';

// HTTP fixtures exercise interface timing; PostgreSQL tests separately exercise current authority.
async function delivery(page:Page){
 const rows:Engagement[]=Array.from({length:206},(_,n)=>({id:'00000000-0000-4000-8000-'+String(n+1).padStart(12,'0'),title:n===205?'Old private engagement':'Newer delivery '+n,status:'OPEN',side:'SELLER',createdAt:'2026-01-01T00:00:00Z'}));
 const details=new Map(rows.map(row=>[row.id,{...row,proposals:[],milestones:[],agreement:null,activity:[],disputes:[]} satisfies EngagementDetail]));
 const calls:number[]=[],reads:string[]=[],failPages=new Set<number>(),denyDetails=new Map<string,number>(),holdDetails=new Set<string>(),heldDetails=new Map<string,Route>();
 let deniedPage=0,holdPage=false,heldPage:Route|undefined,legacy=false;
 const collection=(number:number)=>({items:rows.slice(number*50,(number+1)*50),page:number,size:50,hasMore:(number+1)*50<rows.length,totalItems:rows.length,totalPages:5});
 const denied=(route:Route,status:number)=>route.fulfill({status,json:{error:{code:status===403?'FORBIDDEN':'NOT_FOUND',message:'Current engagement access is unavailable.'}}});
 await page.route('**/api/v1/**',async route=>{
  const url=new URL(route.request().url()),path=url.pathname.replace('/api/v1','');
  if(path==='/me')return route.fulfill({json:{id:'delivery-fixture',email:'delivery@example.test',roles:['CLIENT','DEVELOPER']}});
  if(path==='/payments/config')return route.fulfill({json:{enabled:false,mode:'disabled',reason:'Payments are unavailable in this browser fixture.'}});
  if(path==='/engagements'){
   const number=Number(url.searchParams.get('page')||0);calls.push(number);
   if(number>0&&deniedPage)return denied(route,deniedPage);
   if(failPages.delete(number))return route.fulfill({status:503,json:{error:{code:'UNAVAILABLE',message:'Continuation temporarily unavailable. Please retry.'}}});
   if(number>0&&holdPage){heldPage=route;return;}
   return route.fulfill({json:legacy?rows.slice(0,2):collection(number)});
  }
  const match=/^\/engagements\/([^/]+)(\/payments)?$/.exec(path);
  if(match){
   const [,id,payments]=match;
   if(payments)return route.fulfill({json:{items:[]}});
   reads.push(id);
   if(denyDetails.has(id))return denied(route,denyDetails.get(id)!);
   if(holdDetails.has(id)){heldDetails.set(id,route);return;}
   return details.has(id)?route.fulfill({json:details.get(id)}):denied(route,404);
  }
  return denied(route,404);
 });
 return {rows,calls,reads,failPages,denyDetails,holdDetails,heldDetails,setDeniedPage:(status:number)=>{deniedPage=status;},holdPage:()=>{holdPage=true;},hasHeldPage:()=>!!heldPage,setLegacy:()=>{legacy=true;},releasePage:async()=>{const route=heldPage;heldPage=undefined;holdPage=false;if(route)await route.fulfill({json:collection(1)});},releaseDetail:async(id:string)=>{const route=heldDetails.get(id);holdDetails.delete(id);heldDetails.delete(id);if(route)await route.fulfill({json:details.get(id)});}};
}
const navigation=(page:Page)=>page.getByRole('navigation',{name:'Engagements',exact:true});
const entry=(page:Page,title:string)=>navigation(page).getByRole('button').filter({has:page.getByText(title,{exact:true})});

test('an old explicit engagement opens before continuation and remains selected through bounded retry and final focus',async({page})=>{
 const fixture=await delivery(page),old=fixture.rows.at(-1)!;fixture.failPages.add(1);
 await page.goto('/workspace/delivery?engagementId='+old.id);
 await expect(page.getByRole('heading',{name:old.title,exact:true})).toBeVisible();
 await expect(entry(page,old.title)).toHaveAttribute('aria-current','true');
 expect(fixture.calls).toEqual([0]);expect(fixture.reads).toEqual([old.id]);
 await expect(navigation(page).locator('.delivery-list-item')).toHaveCount(51);
 const more=navigation(page).getByRole('button',{name:'Load more engagements',exact:true});
 await more.focus();await page.keyboard.press('Enter');
 await expect(navigation(page).getByRole('alert')).toHaveText('Continuation temporarily unavailable. Please retry.');
 await expect(more).toBeFocused();await expect(navigation(page).locator('.delivery-list-item')).toHaveCount(51);
 for(let number=1;number<=4;number++){
  await more.focus();await page.keyboard.press('Enter');
  await expect.poll(()=>fixture.calls.filter(value=>value===number).length).toBe(number===1?2:1);
  await expect(navigation(page).locator('.delivery-list-item')).toHaveCount(number===4?206:(number+1)*50+1);
  await expect(navigation(page).getByRole('status')).toContainText(number===4?'6 more records loaded.':'50 more records loaded.');
 }
 await expect(navigation(page).locator('.delivery-list-item')).toHaveCount(206);
 await expect(entry(page,old.title)).toHaveCount(1);await expect(entry(page,old.title)).toHaveAttribute('aria-current','true');
 await expect(page.getByRole('heading',{name:old.title,exact:true})).toBeVisible();
 await expect(navigation(page).getByRole('status')).toBeFocused();await expect(more).toHaveCount(0);
 expect(fixture.calls).toEqual([0,1,1,2,3,4]);expect(fixture.reads).toEqual([old.id]);
 await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(page.viewportSize()!.width+1);
});

for(const status of [403,404])test('an explicit '+status+' never selects an unrelated delivery, including retry',async({page})=>{
 const fixture=await delivery(page),old=fixture.rows.at(-1)!;fixture.denyDetails.set(old.id,status);
 await page.goto('/workspace/delivery?id='+old.id);
 await expect(page.getByRole('alert')).toContainText('Current engagement access is unavailable.');
 await expect(page.getByRole('alert')).toBeFocused();await expect(navigation(page)).toHaveCount(0);
 await page.getByRole('button',{name:'Reload workspace',exact:true}).click();
 await expect.poll(()=>fixture.reads.length).toBe(2);expect(fixture.reads).toEqual([old.id,old.id]);
 await expect(page.getByRole('heading',{name:fixture.rows[0].title,exact:true})).toHaveCount(0);
 fixture.denyDetails.delete(old.id);await page.getByRole('button',{name:'Reload workspace',exact:true}).click();
 await expect(page.getByRole('heading',{name:old.title,exact:true})).toBeVisible();
});

test('a denied continuation clears protected delivery detail and collection',async({page})=>{
 const fixture=await delivery(page);await page.goto('/workspace/delivery');
 await expect(page.getByRole('heading',{name:fixture.rows[0].title,exact:true})).toBeVisible();fixture.setDeniedPage(403);
 await navigation(page).getByRole('button',{name:'Load more engagements',exact:true}).click();
 await expect(page.getByRole('alert')).toContainText('Current engagement access is unavailable.');
 await expect(page.getByRole('alert')).toBeFocused();await expect(navigation(page)).toHaveCount(0);
 await expect(page.getByRole('heading',{name:fixture.rows[0].title,exact:true})).toHaveCount(0);
});

test('selecting a loaded record preserves continuation and ignores an older detail response',async({page})=>{
 const fixture=await delivery(page),slow=fixture.rows[1],current=fixture.rows[2];await page.goto('/workspace/delivery');
 await expect(page.getByRole('heading',{name:fixture.rows[0].title,exact:true})).toBeVisible();
 await navigation(page).getByRole('button',{name:'Load more engagements',exact:true}).click();
 await expect(navigation(page).locator('.delivery-list-item')).toHaveCount(100);
 fixture.holdDetails.add(slow.id);await entry(page,slow.title).click();await expect.poll(()=>fixture.heldDetails.has(slow.id)).toBe(true);
 await entry(page,current.title).click();await expect(page.getByRole('heading',{name:current.title,exact:true})).toBeVisible();
 await fixture.releaseDetail(slow.id);await expect(page.getByRole('heading',{name:slow.title,exact:true})).toHaveCount(0);
 await expect(entry(page,current.title)).toHaveAttribute('aria-current','true');
 await navigation(page).getByRole('button',{name:'Load more engagements',exact:true}).click();
 await expect(navigation(page).locator('.delivery-list-item')).toHaveCount(150);expect(fixture.calls).toEqual([0,1,2]);
});

test('a continuation from a cleared collection cannot append after workspace reload',async({page})=>{
 const fixture=await delivery(page),second=fixture.rows[1];await page.goto('/workspace/delivery');
 await expect(page.getByRole('heading',{name:fixture.rows[0].title,exact:true})).toBeVisible();fixture.holdPage();
 await navigation(page).getByRole('button',{name:'Load more engagements',exact:true}).click();await expect.poll(fixture.hasHeldPage).toBe(true);
 fixture.denyDetails.set(second.id,503);await entry(page,second.title).click();await expect(page.getByRole('alert')).toBeVisible();
 fixture.denyDetails.delete(second.id);await page.getByRole('button',{name:'Reload workspace',exact:true}).click();
 await expect(page.getByRole('heading',{name:second.title,exact:true})).toBeVisible();await fixture.releasePage();
 await expect(navigation(page).locator('.delivery-list-item')).toHaveCount(50);
 await navigation(page).getByRole('button',{name:'Load more engagements',exact:true}).click();
 await expect(navigation(page).locator('.delivery-list-item')).toHaveCount(100);expect(fixture.calls).toEqual([0,1,0,1]);
});

test('malformed explicit identifiers cannot alter API paths or select another engagement',async({page})=>{
 const fixture=await delivery(page);
 for(const id of ['../payments/config','..','https://outside.example.test/private','00000000-0000-4000-8000-000000000001?page=999']){
  await page.goto('/workspace/delivery?engagementId='+encodeURIComponent(id));
  await expect(page.getByRole('alert')).toContainText('This engagement link is invalid.');await expect(navigation(page)).toHaveCount(0);
 }
 expect(fixture.reads).toEqual([]);
});

test('legacy array responses still open their first engagement without a continuation control',async({page})=>{
 const fixture=await delivery(page);fixture.setLegacy();await page.goto('/workspace/delivery');
 await expect(page.getByRole('heading',{name:fixture.rows[0].title,exact:true})).toBeVisible();
 await expect(navigation(page).locator('.delivery-list-item')).toHaveCount(2);
 await expect(navigation(page).getByRole('button',{name:'Load more engagements',exact:true})).toHaveCount(0);
});
