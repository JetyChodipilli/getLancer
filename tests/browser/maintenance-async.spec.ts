import {test,expect,type Page} from '@playwright/test';
import {createMaintenancePreview} from '../../lib/maintenance';

// Browser-only HTTP/SDK controls exercise adverse interface timing, never real provider effects.
async function controlledWorkspace(page:Page){
 const sample=createMaintenancePreview();sample.offerAction('preview-care','send',true);sample.setSide('BUYER');sample.offerAction('preview-care','accept',true);
 const first=sample.detail('preview-care'),second={...structuredClone(first),id:'care-second',title:'Second care agreement'};
 const records=[first,second];let denyDetail=false,confirmCalls=0;
 const list=(items:unknown[],number=0,hasMore=false)=>({items,page:number,size:12,hasMore,totalItems:items.length,totalPages:hasMore?2:1});
 await page.addInitScript(()=>{
  const state=window as unknown as {careGateways:{options:Record<string,unknown>;handlers:Record<string,()=>void>;closed:boolean}[];Razorpay:unknown};state.careGateways=[];
  state.Razorpay=class{record:{options:Record<string,unknown>;handlers:Record<string,()=>void>;closed:boolean};constructor(options:Record<string,unknown>){this.record={options,handlers:{},closed:false};state.careGateways.push(this.record);}open(){}close(){this.record.closed=true;}on(event:string,handler:()=>void){this.record.handlers[event]=handler;}};
 });
 await page.route('**/api/v1/**',async route=>{
  const url=new URL(route.request().url()),path=url.pathname.replace('/api/v1',''),method=route.request().method();
  if(path==='/me')return route.fulfill({json:{email:'interface-fixture@example.test',roles:['CLIENT'],displayName:'Interface fixture'}});
  if(path==='/maintenance/config')return route.fulfill({json:{enabled:true,keyId:'rzp_live_interface_fixture',mode:'live',reason:''}});
  if(path==='/maintenance/sources'){const number=Number(url.searchParams.get('page')||0);return route.fulfill({json:number===0?list([],0,true):list([{id:'later-source',title:'Later eligible delivery',side:'SELLER'}],1)});}
  if(path==='/maintenance')return route.fulfill({json:list(records)});
  const record=records.find(item=>path==='/maintenance/'+item.id||path.startsWith('/maintenance/'+item.id+'/'));
  if(record&&method==='GET')return route.fulfill(denyDetail?{status:403,json:{error:{code:'FORBIDDEN',message:'Workspace access was revoked.'}}}:{json:record});
  if(record&&path.endsWith('/billing/confirm')){confirmCalls++;return route.fulfill({json:{status:'ACTIVE'}});}
  if(record&&path.endsWith('/billing'))return route.fulfill({json:{id:'billing-fixture',providerSubscriptionId:'sub_interfacefixture',keyId:'rzp_live_interface_fixture',mode:'live',status:'CREATED'}});
  if(record&&path.endsWith('/send'))return route.fulfill({json:{id:record.id,status:'OFFERED'}});
  return route.fulfill({status:404,json:{error:{code:'NOT_FOUND',message:'Unexpected controlled request.'}}});
 });
 await page.goto('/workspace/maintenance');await expect(page.getByRole('heading',{name:'Portal care',exact:true})).toBeVisible();
 return {records,setDeny:()=>{denyDetail=true;},confirmCalls:()=>confirmCalls};
}
async function openCheckout(page:Page){await page.getByRole('button',{name:'Authorize recurring billing',exact:true}).click();const dialog=page.getByRole('dialog');await dialog.getByRole('checkbox').check();await dialog.getByRole('button',{name:'Continue to Razorpay',exact:true}).click();await expect(dialog).not.toBeVisible();}

test('a previous checkout cannot overwrite another agreement and a payment failure stays visible',async({page})=>{
 const fixture=await controlledWorkspace(page);await openCheckout(page);
 await page.getByRole('navigation',{name:'Care agreements',exact:true}).getByRole('button',{name:/Second care agreement/}).click();await expect(page.getByRole('heading',{name:'Second care agreement',exact:true})).toBeVisible();
 await page.evaluate(()=>{const state=window as unknown as {careGateways:{options:{handler:(body:unknown)=>void;modal:{ondismiss:()=>void}};handlers:Record<string,()=>void>;closed:boolean}[]};const gateway=state.careGateways[0];if(!gateway.closed)throw Error('Previous checkout did not close');gateway.options.handler({razorpay_payment_id:'pay_latefixture'});gateway.options.modal.ondismiss();gateway.handlers['payment.failed']();});
 await expect(page.getByRole('heading',{name:'Second care agreement',exact:true})).toBeVisible();expect(fixture.confirmCalls()).toBe(0);await expect(page.getByRole('alert')).toHaveCount(0);
 await page.getByRole('navigation',{name:'Care agreements',exact:true}).getByRole('button',{name:/Portal care/}).click();await openCheckout(page);await page.evaluate(()=>{const state=window as unknown as {careGateways:{handlers:Record<string,()=>void>}[]};state.careGateways[1].handlers['payment.failed']();});await expect(page.getByRole('alert')).toContainText('Billing authorization was not confirmed. Refresh billing before trying again.');
});

test('later source pages remain reachable and a saved action with denied refresh clears private forms without success',async({page})=>{
 const fixture=await controlledWorkspace(page);await page.getByRole('button',{name:'Create support offer',exact:true}).click();const dialog=page.getByRole('dialog');await dialog.getByRole('navigation',{name:'Completed work pages',exact:true}).getByRole('button',{name:'Next',exact:true}).click();await expect(dialog.getByLabel('Completed engagement',{exact:true}).locator('option[value="later-source"]')).toHaveCount(1);await page.keyboard.press('Escape');
 fixture.records[0].side='SELLER';fixture.records[0].status='DRAFT';await page.getByRole('navigation',{name:'Care agreements',exact:true}).getByRole('button',{name:/Portal care/}).click();await page.getByRole('button',{name:'Review & send terms',exact:true}).click();await dialog.getByRole('checkbox').check();fixture.setDeny();await dialog.getByRole('button',{name:'Consent & send offer',exact:true}).click();await expect(dialog).not.toBeVisible();await expect(page.getByRole('heading',{name:'Sign in to your maintenance workspace',exact:true})).toBeVisible();await expect(page.getByRole('navigation',{name:'Care agreements',exact:true})).toHaveCount(0);await expect(page.getByText('Agreement Send recorded.',{exact:true})).toHaveCount(0);
});
