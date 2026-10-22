import {test,expect,type Page} from '@playwright/test';

async function withinViewport(page:Page){
 const layout=await page.evaluate(()=>({
  width:document.documentElement.scrollWidth,viewport:innerWidth,
  overflowing:[...document.querySelectorAll('main form,main form *,main .row,main .row *')].flatMap(element=>{
   const box=element.getBoundingClientRect();
   return box.width&&box.right>innerWidth+1?[{tag:element.tagName,className:element.className,right:box.right,width:box.width,text:element.textContent?.slice(0,90)}]:[];
  }).slice(0,12),
 }));
 expect(layout.width,JSON.stringify(layout)).toBeLessThanOrEqual(layout.viewport+1);
}

test('qualified inquiries and their native filters fit with long client details',async({page})=>{
 const errors:string[]=[];page.on('pageerror',error=>errors.push(error.message));
 const email='ci-v1-be61e178-phone-conversion-client@example.test';
 const inquiry={id:'22222222-2222-4222-8222-222222222222',clientName:'V1 be61e178 phone conversion client',projectTitle:'V1 be61e178 phone conversion',
  description:'Build a stock approval system with accessible product search and order history for our synthetic shop.',
  budgetBand:'USD_3K_10K',timelineBand:'ONE_TO_THREE_MONTHS',status:'INQUIRY_RECEIVED',clientEmail:email};
 await page.route('**/api/v1/**',route=>{
  const url=new URL(route.request().url()),path=url.pathname.slice('/api/v1'.length);
  if(path==='/me')return route.fulfill({json:{id:'11111111-1111-4111-8111-111111111111',displayName:'V1 be61e178 phone conversion builder',email:'builder@example.test',emailVerified:true,roles:['DEVELOPER','CLIENT'],activeSlotLimit:3,profile:{approval_status:'APPROVED'}}});
  if(path==='/categories'||path==='/technologies')return route.fulfill({json:{items:[{name:path==='/categories'?'Inventory':'React'}]}});
  if(path==='/developer/products')return route.fulfill({json:{items:[],page:0,hasMore:false,activeCount:0,totalOwned:1}});
  if(path==='/developer/analytics')return route.fulfill({json:{qualifiedInquiries:1}});
  if(path==='/developer/inquiries')return route.fulfill({json:{items:url.searchParams.get('status')?[]:[inquiry],page:0,hasMore:false}});
  return route.fulfill({status:401,json:{error:{code:'UNAUTHORIZED',message:'No other fixture authority.'}}});
 });
 await page.goto('/workspace?tab=inquiries');
 await expect(page.getByRole('heading',{name:'Qualified inquiries',exact:true})).toBeVisible();
 await expect(page.getByRole('link',{name:email,exact:true})).toHaveAttribute('href','mailto:'+email);
 await expect(page.getByRole('button',{name:'Mark responded',exact:true})).toBeVisible();
 await withinViewport(page);
 const filters=page.getByRole('form',{name:'Filter qualified inquiries',exact:true});
 await filters.getByLabel('Status',{exact:true}).selectOption('COMPLETION_PENDING_CONFIRMATION');
 await filters.getByLabel('From',{exact:true}).fill('2026-01-01');
 await filters.getByLabel('Through',{exact:true}).fill('2026-12-31');
 await withinViewport(page);
 await filters.getByRole('button',{name:'Apply filters',exact:true}).click();
 await expect(page.getByRole('heading',{name:'No qualified inquiries yet.',exact:true})).toBeVisible();
 await withinViewport(page);expect(errors).toEqual([]);
});
