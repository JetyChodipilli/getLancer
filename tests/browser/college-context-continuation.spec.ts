import {test,expect} from '@playwright/test';

const context=(id:string,title:string)=>({productId:id,title,category:'FULL_STACK',language:'Java',problem:'An original project problem with concrete evidence.',outcome:'A reproducible implementation with verified behavior.',prerequisites:'A local Java runtime.',contribution:'I implemented the original project and documentation.',shareAcademicDetails:false,status:'PENDING'});
test('college contexts continue independently, retain records on retry and clear them after authority denial',async({page})=>{
 const ids=['00000000-0000-4000-8000-000000000001','00000000-0000-4000-8000-000000000002','00000000-0000-4000-8000-000000000003'];
 let attempts=0;
 await page.route('**/api/v1/**',route=>{
  const url=new URL(route.request().url());
  if(url.pathname==='/api/v1/me/college-projects'){
   const next=Number(url.searchParams.get('page')||0);
   if(next===0)return route.fulfill({json:{items:[context(ids[0],'First retained context')],page:0,hasMore:true}});
   attempts++;
   if(attempts===1)return route.fulfill({status:503,json:{error:{code:'SERVICE_UNAVAILABLE',message:'College contexts temporarily unavailable.'}}});
   if(next===1)return route.fulfill({json:{items:[context(ids[1],'Older retained context')],page:1,hasMore:true}});
   return route.fulfill({status:403,json:{error:{code:'FORBIDDEN',message:'Current builder authentication required.'}}});
  }
  if(url.pathname==='/api/v1/developer/products')return route.fulfill({json:{items:[{id:ids[0],title:'Owned showcase'}],page:0,hasMore:false}});
  if(url.pathname==='/api/v1/me')return route.fulfill({json:{roles:['DEVELOPER'],emailVerified:true,profile:{approval_status:'APPROVED'}}});
  return route.fulfill({json:{items:[],hasMore:false}});
 });
 await page.goto('/workspace/college-projects');
 await expect(page.getByRole('heading',{name:'First retained context',exact:true})).toBeVisible();
 const more=page.getByRole('button',{name:'Load more college contexts',exact:true});
 await expect(more).toBeVisible();
 await expect(page.getByRole('button',{name:'Load more owned showcases',exact:true})).toHaveCount(0);
 await more.click();await expect(page.getByRole('alert')).toContainText('College contexts temporarily unavailable.');
 await expect(page.getByRole('heading',{name:'First retained context',exact:true})).toBeVisible();
 await more.click();
 await expect(page.getByRole('heading',{name:'Older retained context',exact:true})).toBeVisible();
 await expect(page.getByRole('heading',{name:'First retained context',exact:true})).toBeVisible();
 await more.click();
 await expect(page.getByRole('alert')).toContainText('Current builder authentication required.');
 await expect(page.getByRole('heading',{name:/retained context/})).toHaveCount(0);
 await expect(more).toHaveCount(0);
});
