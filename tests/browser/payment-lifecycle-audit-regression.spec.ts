import {test,expect,type Page,type Route} from '@playwright/test';
import {createDeliveryPreview,type EngagementDetail,type PaymentSummary} from '../../lib/delivery';

// Controlled HTTP/SDK timing only. These checks do not prove provider readiness.
async function workspace(page:Page,buyer=false){
 const sample=createDeliveryPreview(),first=sample.detail(sample.list()[0].id);first.id='10000000-0000-4000-8000-000000000001';
 const second={...structuredClone(first),id:'10000000-0000-4000-8000-000000000002',title:'Other private delivery'};
 const records:EngagementDetail[]=[first,second],payments:PaymentSummary[]=[];
 if(buyer){first.side='BUYER';first.status='ACTIVE';first.agreement={proposalId:first.proposals[0].id,scope:first.proposals[0].scope,terms:first.proposals[0].terms,amountMinor:10000,currency:'INR',digest:'interface-fixture',sellerConsentedAt:'2026-10-01T00:00:00Z',buyerConsentedAt:'2026-10-01T00:00:00Z'};first.milestones=[{...first.proposals[0].milestones[0],id:'20000000-0000-4000-8000-000000000001',amountMinor:10000,status:'ACCEPTED'}];}
 let writeStatus=0,denyReads=false,commitSend=false,holdVerify=false,verify:Route|undefined,verifyCalls=0,orderStatus=0;
 const keys:string[]=[];
 await page.addInitScript(()=>{const state=window as unknown as {auditGateways:unknown[];Razorpay:unknown};state.auditGateways=[];state.Razorpay=class{closed=false;handlers:Record<string,unknown>={};constructor(public options:unknown){state.auditGateways.push(this);}open(){}close(){this.closed=true;}on(event:string,handler:unknown){this.handlers[event]=handler;}};});
 await page.route('**/api/v1/**',async route=>{
  const path=new URL(route.request().url()).pathname.replace('/api/v1',''),method=route.request().method(),failure=(status:number,message:string,code='UNAVAILABLE')=>route.fulfill({status,json:{error:{code,message}}});
  if(path==='/me')return route.fulfill({json:{id:'interface-account',email:'interface@example.test',roles:['CLIENT','DEVELOPER']}});
  if(path==='/payments/config')return route.fulfill({json:{enabled:true,mode:'test',keyId:'rzp_test_interface'}});
  if(path==='/engagements')return route.fulfill({json:{items:records,page:0,size:50,hasMore:false}});
  if(method==='POST'&&path.endsWith('/payment-order')){keys.push(route.request().headers()['idempotency-key']);if(orderStatus){payments.splice(0,payments.length,{id:'payment-interface',milestoneId:first.milestones[0].id,status:orderStatus===400?'REJECTED':'UNKNOWN',amountMinor:10000,currency:'INR',mode:'test',refundedMinor:0});return failure(502,'Provider order needs review.',orderStatus===400?'PAYMENT_ORDER_REJECTED':'PAYMENT_ORDER_UNKNOWN');}return route.fulfill({json:{id:'payment-interface',orderId:'order_interface123',keyId:'rzp_test_interface',status:'ORDER_CREATED',amountMinor:10000,currency:'INR',mode:'test'}});}
  if(path==='/payments/payment-interface/verify'){verifyCalls++;if(holdVerify){verify=route;return;}return route.fulfill({json:{status:'CAPTURED'}});}
  const record=records.find(item=>path==='/engagements/'+item.id||path.startsWith('/engagements/'+item.id+'/'));
  if(record&&method==='GET'){if(denyReads)return failure(403,'Current delivery authority is unavailable.');return route.fulfill({json:path.endsWith('/payments')?{items:payments}:record});}
  if(record&&method==='POST'){if(commitSend)record.proposals[0].status='SENT';if(writeStatus)return failure(writeStatus,'Check the submitted delivery fields.');if(denyReads)return route.fulfill({json:{ok:true}});if(path.endsWith('/proposals'))record.proposals[0].revision++;return route.fulfill({json:{ok:true}});}
  return failure(404,'Unexpected controlled request.');
 });
 await page.goto('/workspace/delivery');await expect(page.getByRole('heading',{name:first.title,exact:true})).toBeVisible();
 return {records,payments,keys,failWrite:(status:number)=>{writeStatus=status;},commitSend:()=>{commitSend=true;writeStatus=503;},denyRefresh:()=>{denyReads=true;},failOrder:(status:number)=>{orderStatus=status;},allowOrder:()=>{orderStatus=0;},holdVerification:()=>{holdVerify=true;},verifyCalls:()=>verifyCalls,hasVerify:()=>!!verify,releaseVerify:async()=>{if(verify)await verify.fulfill({json:{status:'CAPTURED'}});verify=undefined;}};
}
type Gateway={closed:boolean;options:{handler:(result:Record<string,string>)=>Promise<void>;modal:{ondismiss:()=>void}}};
async function gatewayEvent(page:Page,event:'dismiss'|'verify',index=0){await expect.poll(()=>page.evaluate(()=>(window as unknown as {auditGateways:unknown[]}).auditGateways.length)).toBeGreaterThan(index);await page.evaluate(async({event,index})=>{const gateway=(window as unknown as {auditGateways:Gateway[]}).auditGateways[index];if(event==='dismiss')gateway.options.modal.ondismiss();else void gateway.options.handler({razorpay_payment_id:'pay_interface123',razorpay_signature:'0'.repeat(64)});},{event,index});}

test('delivery validation retains a typed proposal and a successful retry closes only after refreshed state',async({page})=>{
 const fixture=await workspace(page);await page.getByRole('button',{name:'Edit proposal',exact:true}).click();const dialog=page.getByRole('dialog'),scope='Retain this revised accessible portal scope while validation is corrected.';
 await dialog.getByLabel('Scope and deliverables',{exact:true}).fill(scope);fixture.failWrite(400);await dialog.getByRole('button',{name:'Save proposal draft',exact:true}).click();
 await expect(dialog.getByRole('alert')).toHaveText('Check the submitted delivery fields.');await expect(dialog.getByLabel('Scope and deliverables',{exact:true})).toHaveValue(scope);await expect(dialog.getByRole('button',{name:'Save proposal draft',exact:true})).toBeEnabled();
 fixture.failWrite(0);await dialog.getByRole('button',{name:'Save proposal draft',exact:true}).click();await expect(dialog).not.toBeVisible();await expect(page.getByText('Proposal draft saved.',{exact:true})).toBeVisible();
});
test('an uncertain send response refreshes its committed state and closes the obsolete consent form',async({page})=>{
 const fixture=await workspace(page);await page.getByRole('button',{name:'Review and send proposal',exact:true}).click();const dialog=page.getByRole('dialog');await dialog.getByRole('checkbox').check();fixture.commitSend();await dialog.getByRole('button',{name:'Send proposal with consent',exact:true}).click();
 await expect(dialog).not.toBeVisible();await expect(page.getByRole('button',{name:'Withdraw proposal',exact:true})).toBeVisible();await expect(page.getByRole('alert')).toContainText('Check the submitted delivery fields.');await expect(page.getByText('Proposal send recorded.',{exact:true})).toHaveCount(0);
});
test('saved delivery with denied refresh clears private forms and never announces success',async({page})=>{
 const fixture=await workspace(page);await page.getByRole('button',{name:'Edit proposal',exact:true}).click();fixture.denyRefresh();await page.getByRole('dialog').getByRole('button',{name:'Save proposal draft',exact:true}).click();await expect(page.getByRole('dialog')).not.toBeVisible();await expect(page.getByRole('alert')).toContainText('saved, but the workspace could not be refreshed');await expect(page.getByRole('heading',{name:'Customer approval portal',exact:true})).toHaveCount(0);await expect(page.getByText('Proposal draft saved.',{exact:true})).toHaveCount(0);
});
test('dismissed checkout callbacks cannot verify or select the previous delivery',async({page})=>{
 const fixture=await workspace(page,true);await page.getByRole('button',{name:'Pay with Razorpay',exact:true}).click();await gatewayEvent(page,'dismiss');await expect(page.getByRole('navigation',{name:'Engagements',exact:true}).getByRole('button').filter({hasText:'Other private delivery'})).toBeEnabled();await page.getByRole('navigation',{name:'Engagements',exact:true}).getByRole('button').filter({hasText:'Other private delivery'}).click();await expect(page.getByRole('heading',{name:'Other private delivery',exact:true})).toBeVisible();await gatewayEvent(page,'verify');await expect(page.getByRole('heading',{name:'Other private delivery',exact:true})).toBeVisible();expect(fixture.verifyCalls()).toBe(0);
});
test('checkout dismissal during verification keeps controls blocked until server refresh',async({page})=>{
 const fixture=await workspace(page,true);fixture.holdVerification();await page.getByRole('button',{name:'Pay with Razorpay',exact:true}).click();await gatewayEvent(page,'verify');await expect.poll(fixture.hasVerify).toBe(true);await gatewayEvent(page,'dismiss');await expect(page.getByRole('navigation',{name:'Engagements',exact:true}).getByRole('button').first()).toBeDisabled();expect(fixture.verifyCalls()).toBe(1);await fixture.releaseVerify();await expect(page.getByText('Provider confirmation received. The payment status below reflects server verification.',{exact:true})).toBeVisible();
});
test('only definitive order rejection enables a fresh-key retry and uncertain orders remain held',async({page})=>{
 const fixture=await workspace(page,true);fixture.failOrder(400);await page.getByRole('button',{name:'Pay with Razorpay',exact:true}).click();await expect(page.getByRole('alert')).toContainText('Provider order needs review.');await expect(page.getByRole('button',{name:'Pay with Razorpay',exact:true})).toBeEnabled();fixture.allowOrder();await page.getByRole('button',{name:'Pay with Razorpay',exact:true}).click();await expect.poll(()=>fixture.keys.length).toBe(2);expect(fixture.keys[0]).not.toBe(fixture.keys[1]);await gatewayEvent(page,'dismiss');fixture.failOrder(503);await page.getByRole('button',{name:'Pay with Razorpay',exact:true}).click();await expect(page.getByRole('alert')).toContainText('Provider order needs review.');await expect(page.getByRole('button',{name:'Pay with Razorpay',exact:true})).toBeDisabled();
});
