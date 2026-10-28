import {test,expect,type Page,type Route} from '@playwright/test';
import {build} from 'esbuild';
import {readFileSync} from 'node:fs';

// Actual operator components and production styles; HTTP controls simulate
// adverse response timing. No payment provider is contacted by these fixtures.
async function fixture(page:Page,kind:'delivery'|'maintenance'){
 const id='11111111-1111-4111-8111-111111111111';
 const attention={id,offerId:'22222222-2222-4222-8222-222222222222',status:'UNKNOWN',mode:'test',creationStep:'SUBSCRIPTION',amountMinor:10000,accountId:'acc_operatorfixture',localHold:false,periods:[]};
 const dispute={id,title:'Independent delivery decision',engagementId:'33333333-3333-4333-8333-333333333333',status:'OPEN',reason:'The retained handover needs a reviewed operator decision.'};
 const collection=(items:unknown[])=>({items,page:0,size:kind==='delivery'?50:12,hasMore:false,totalItems:items.length,totalPages:items.length?1:0});
 const response=(path:string)=>kind==='maintenance'?collection([attention]):path.includes('/delivery/disputes')?collection(dispute.status==='OPEN'?[dispute]:[]):collection([]);
 let writes=0,readStatus=0,hold=false;const held:{route:Route;path:string}[]=[];
 await page.route('**/api/v1/**',async route=>{
  const path=new URL(route.request().url()).pathname;
  if(route.request().method()==='POST'){writes++;dispute.status='RESOLVED';return route.fulfill({json:{id,status:'ACTIVE'}});}
  if(hold){held.push({route,path});return;}
  if(readStatus&&(kind==='maintenance'||path.endsWith('/payments/accounts')))return route.fulfill({status:readStatus,json:{error:{code:readStatus<500?'FORBIDDEN':'UNAVAILABLE',message:readStatus<500?'Current administrator MFA is required.':'Current provider attention is unavailable.'}}});
  return route.fulfill({json:response(path)});
 });
 const component=kind==='delivery'?'DeliveryOperations':'MaintenanceOperations';
 const built=await build({stdin:{contents:`import {createRoot} from 'react-dom/client';import ${component} from './app/components/${kind}-operations';createRoot(document.getElementById('root')).render(<main className="wrap"><${component}/></main>);`,resolveDir:process.cwd(),loader:'tsx'},bundle:true,write:false,platform:'browser',format:'iife',jsx:'automatic',define:{'process.env.NODE_ENV':'"test"'}});
 const url='/round2-'+kind+'-operations-fixture';
 await page.route('**'+url,route=>route.fulfill({contentType:'text/html',body:'<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"></head><body><div id="root"></div></body></html>'}));
 await page.goto(url);
 for(const file of ['app/globals.css','app/components/workspace.css','app/components/teams.css','app/maintenance.css','app/form-controls.css'])await page.addStyleTag({content:readFileSync(file,'utf8').replace(/^@import[^\n]+\n/gm,'')});
 await page.addScriptTag({content:built.outputFiles[0].text});
 const summary=page.locator('summary').filter({hasText:kind==='delivery'?'Delivery disputes and Razorpay operations':'Maintenance billing & support operations'});
 await summary.click();
 await expect(page.getByText(kind==='delivery'?dispute.title:'Frozen Route account: '+attention.accountId,{exact:true})).toBeVisible();
 const submit=async()=>{
  if(kind==='delivery'){const form=page.locator('form').filter({has:page.getByText(dispute.title,{exact:true})});await form.getByLabel('Decision reason',{exact:true}).fill('Independent review confirmed the saved operator decision.');await form.getByRole('button',{name:'Record dispute resolution',exact:true}).click();}
  else{await page.getByLabel('Evidence & decision reason',{exact:true}).fill('Independent review confirmed provider evidence for this operation.');await page.getByRole('button',{name:'Verify & record maintenance action',exact:true}).click();}
  await expect.poll(()=>writes).toBe(1);
 };
 return {summary,submit,writes:()=>writes,setReadStatus:(status:number)=>{readStatus=status;},hold:()=>{hold=true;},held:()=>held.length,release:async()=>{hold=false;for(const item of held.splice(0))await item.route.fulfill({json:response(item.path)});}};
}

test('accepted delivery decision with unavailable refreshed collection gives read-only recovery without false success',async({page})=>{
 const f=await fixture(page,'delivery');f.setReadStatus(503);await f.submit();
 await expect(page.locator('details > .error')).toContainText('The decision was saved, but current operations could not be refreshed.');await expect(page.getByText('Decision saved.',{exact:true})).toHaveCount(0);
 await expect(page.getByRole('button',{name:'Retry seller accounts',exact:true})).toBeEnabled();f.setReadStatus(0);await page.getByRole('button',{name:'Retry seller accounts',exact:true}).click();
 await expect(page.getByText('No seller accounts.',{exact:true})).toBeVisible();expect(f.writes()).toBe(1);await expect(page.getByRole('button',{name:'Refresh payment operations',exact:true})).toBeEnabled();
});

for(const status of [401,403])test('accepted delivery decision with '+status+' refresh clears protected rows and cannot publish success',async({page})=>{
 const f=await fixture(page,'delivery');f.setReadStatus(status);await f.submit();
 await expect(page.locator('details > .error')).toHaveText('Current administrator MFA is required.');await expect(page.getByRole('region')).toHaveCount(0);await expect(page.getByText('Decision saved.',{exact:true})).toHaveCount(0);expect(f.writes()).toBe(1);
});

test('accepted maintenance action with unavailable refresh preserves the saved-versus-rejected distinction',async({page})=>{
 const f=await fixture(page,'maintenance');f.setReadStatus(503);await f.submit();
 await expect(page.locator('.care-operations > .error')).toContainText('The operator action was saved, but its refreshed status is unavailable.');await expect(page.getByText(/Operator action recorded\. Provider state:/)).toHaveCount(0);
 f.setReadStatus(0);await page.getByRole('button',{name:'Refresh maintenance operations',exact:true}).click();await expect(page.getByText('Frozen Route account: acc_operatorfixture',{exact:true})).toBeVisible();expect(f.writes()).toBe(1);
});

for(const kind of ['delivery','maintenance'] as const)test('closing '+kind+' during accepted-write refresh invalidates old feedback and permits a fresh read',async({page})=>{
 const f=await fixture(page,kind);f.hold();await f.submit();await expect.poll(f.held).toBe(kind==='delivery'?3:1);await f.summary.click();await f.release();
 await expect(page.locator('details .success')).toHaveCount(0);await expect(page.getByText(/Operator action recorded\. Provider state:/)).toHaveCount(0);await expect(page.locator('details > .error')).toHaveCount(0);
 await f.summary.click();await expect(page.getByRole('button',{name:kind==='delivery'?'Refresh payment operations':'Refresh maintenance operations',exact:true})).toBeEnabled();expect(f.writes()).toBe(1);
});

for(const kind of ['delivery','maintenance'] as const)test('current '+kind+' accepted action and successful refresh still reports its confirmed result',async({page})=>{
 const f=await fixture(page,kind);await f.submit();await expect(page.getByText(kind==='delivery'?'Decision saved.':'Operator action recorded. Provider state: ACTIVE',{exact:true})).toBeVisible();await expect(page.locator('details > .error')).toHaveCount(0);expect(f.writes()).toBe(1);
});
