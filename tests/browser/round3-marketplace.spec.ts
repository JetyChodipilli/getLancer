import {expect,test,type Page} from '@playwright/test';
import {build} from 'esbuild';
import {readFileSync} from 'node:fs';
import type {EducationRelease} from '../../lib/education';

const seeds=JSON.parse(readFileSync('backend/src/main/resources/catalog/education.json','utf8'));
const sourceArchive=readFileSync('public'+seeds[0].previewPackageUrl);
const productId='00000000-0000-4000-8000-000000000031',templateId='00000000-0000-4000-8000-000000000032',versionId='00000000-0000-4000-8000-000000000033';
const purchaseId='00000000-0000-4000-8000-000000000034',freeId='00000000-0000-4000-8000-000000000035';
const paid:EducationRelease={...seeds[0].release,productId,snapshot:{...seeds[0].release.snapshot,productId,mode:'PAID',priceMinor:25000,currency:'INR',sourceBinding:{...seeds[0].release.snapshot.sourceBinding,templateId,versionId}}};
delete paid.snapshot.sourceBinding!.packageId;
const free:EducationRelease={...seeds[0].release,id:freeId,productId,snapshot:{...seeds[0].release.snapshot,productId,mode:'FREE'}};
const order={id:purchaseId,status:'ORDER_CREATED',orderId:'order_round3_fixture',keyId:'key_round3_fixture',mode:'test',amountMinor:25000,currency:'INR'};
type CallbackOptions={handler:(payload:Record<string,string>)=>Promise<void>;modal:{ondismiss:()=>void}};
type Session={options:CallbackOptions;failed?:(event:{error:{description:string}})=>void};
type FixtureWindow=Window&{round3Marketplace:{select:(release:EducationRelease)=>void;opened:number;closed:number;sessions:Session[];throwOnOpen?:boolean}};
let bundled:Promise<string>|undefined;
function script(){return bundled??=build({stdin:{contents:`
 import {useState} from 'react';import {createRoot} from 'react-dom/client';import EducationSource from './app/components/education-source';
 const fixture=window.round3Marketplace={opened:0,closed:0,sessions:[]};
 window.Razorpay=class{constructor(options){this.session={options};fixture.sessions.push(this.session);}on(name,handler){this.session.failed=handler;}open(){fixture.opened++;if(fixture.throwOnOpen)throw Error('Synthetic SDK open unavailable');}close(){fixture.closed++;throw Error('Synthetic unavailable SDK close');}};
 function Harness(){const [release,setRelease]=useState(${JSON.stringify(paid)});fixture.select=setRelease;return <EducationSource release={release}/>;}
 createRoot(document.getElementById('root')).render(<Harness/>);
`,loader:'tsx',resolveDir:process.cwd()},bundle:true,write:false,format:'iife',platform:'browser',jsx:'automatic',loader:{'.css':'empty'},define:{'process.env.NODE_ENV':'"test"'},plugins:[{name:'fixture-link',setup(plugin){plugin.onResolve({filter:/^next\/link$/},()=>({path:'link',namespace:'fixture'}));plugin.onLoad({filter:/.*/,namespace:'fixture'},()=>({contents:'export default function Link({href,children,...props}){return <a href={href} {...props}>{children}</a>}',loader:'jsx',resolveDir:process.cwd()}));}}]}).then(result=>result.outputFiles[0].text);}
async function render(page:Page){
 await page.goto('/college-projects');const styles=await page.locator('link[rel="stylesheet"],style').evaluateAll(nodes=>nodes.map(node=>node.outerHTML).join(''));expect(styles.length).toBeGreaterThan(0);
 await page.route('**/round3-marketplace-fixture',route=>route.fulfill({contentType:'text/html',body:'<!doctype html><html lang="en"><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>Reviewed education source</title>'+styles+'</head><body><main id="main"><h1>Reviewed education source</h1><div id="root"></div></main></body></html>'}));
 await page.route('**/api/v1/education-releases/*/source-offer',route=>{const release=route.request().url().includes(freeId)?free:paid;return route.fulfill({json:{release,package:release.snapshot.sourceBinding,available:true,checkoutAvailable:release.snapshot.mode==='PAID',paidEnabled:true,paymentMode:'test'}});});
 await page.goto('/round3-marketplace-fixture');await page.addScriptTag({content:await script()});
 await expect(page.getByRole('checkbox',{name:/I have read and accept/})).toBeEnabled();
}
async function begin(page:Page){await page.getByRole('checkbox',{name:/I have read and accept/}).check();await page.getByRole('button',{name:'Accept license & continue to payment',exact:true}).click();await expect.poll(()=>page.evaluate(()=>(window as unknown as FixtureWindow).round3Marketplace.opened)).toBe(1);}
async function callback(page:Page,index:number,kind:'verify'|'dismiss'|'fail',settled=false){
 await page.evaluate(async({index,kind,settled})=>{const session=(window as unknown as FixtureWindow).round3Marketplace.sessions[index];if(kind==='verify'){const completion=session.options.handler({razorpay_payment_id:'pay_round3_fixture',razorpay_signature:'synthetic-signature'});if(settled)await completion;}else if(kind==='dismiss')session.options.modal.ondismiss();else session.failed?.({error:{description:'Synthetic declined payment'}});},{index,kind,settled});
}
function reflow(page:Page){return expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1)).toBe(true);}

test('education order creation is single-flight and an unknown order remains recoverable',async({page})=>{
 let orders=0,release!:()=>void,started!:()=>void;const waiting=new Promise<void>(resolve=>release=resolve),began=new Promise<void>(resolve=>started=resolve);
 await page.route('**/api/v1/templates/*/orders',async route=>{orders++;const body=route.request().postDataJSON();expect(body).toEqual({versionId,licenseConsent:true,educationReleaseId:paid.id});expect(route.request().headers()['idempotency-key']).toMatch(/^[0-9a-f-]{36}$/);started();await waiting;await route.fulfill({json:{...order,status:'UNKNOWN',orderId:null,keyId:null}});});
 await render(page);await page.getByRole('checkbox',{name:/I have read and accept/}).check();
 await page.getByRole('button',{name:'Accept license & continue to payment',exact:true}).evaluate(element=>{(element as HTMLButtonElement).click();(element as HTMLButtonElement).click();});await began;
 expect(orders).toBe(1);await expect(page.getByRole('button',{name:'Accept license & continue to payment',exact:true})).toBeDisabled();release();
 await expect(page.getByRole('status')).toContainText(/Purchase status: unknown/i);await expect(page.getByRole('button',{name:'Refresh provider status',exact:true})).toBeEnabled();
 expect(await page.evaluate(()=>(window as unknown as FixtureWindow).round3Marketplace.opened)).toBe(0);await reflow(page);
});

for(const outcome of ['dismiss','fail'] as const)test('a '+outcome+' education gateway cannot verify late and retry retains the purchase key',async({page})=>{
 const keys:string[]=[];let verifies=0;
 await page.route('**/api/v1/templates/*/orders',route=>{keys.push(route.request().headers()['idempotency-key']);return route.fulfill({json:order});});
 await page.route('**/api/v1/template-purchases/*/verify',route=>{verifies++;return route.fulfill({json:{status:'CAPTURED'}});});
 await render(page);await begin(page);await expect(page.getByRole('button',{name:'Refresh provider status',exact:true})).toBeDisabled();
 await callback(page,0,outcome);await expect(page.getByRole('button',{name:'Refresh provider status',exact:true})).toBeEnabled();
 await callback(page,0,'verify',true);await page.getByRole('button',{name:'Accept license & continue to payment',exact:true}).click();
 await expect.poll(()=>page.evaluate(()=>(window as unknown as FixtureWindow).round3Marketplace.opened)).toBe(2);
 expect(verifies).toBe(0);expect(keys).toHaveLength(2);expect(keys[0]).toBe(keys[1]);expect(await page.evaluate(()=>(window as unknown as FixtureWindow).round3Marketplace.closed)).toBe(1);await reflow(page);
});

test('education verification ignores duplicate, dismiss and failure callbacks while preserving server confirmation',async({page})=>{
 let orders=0,verifies=0,release!:()=>void,started!:()=>void;const waiting=new Promise<void>(resolve=>release=resolve),began=new Promise<void>(resolve=>started=resolve);
 await page.route('**/api/v1/templates/*/orders',route=>{orders++;return route.fulfill({json:order});});
 await page.route('**/api/v1/template-purchases/*/verify',async route=>{verifies++;expect(route.request().postDataJSON()).toEqual({razorpay_payment_id:'pay_round3_fixture',razorpay_signature:'synthetic-signature'});started();await waiting;await route.fulfill({json:{status:'CAPTURED'}});});
 await render(page);await begin(page);await callback(page,0,'verify');await began;await callback(page,0,'verify');await callback(page,0,'dismiss');await callback(page,0,'fail');
 await expect(page.getByRole('button',{name:'Refresh provider status',exact:true})).toBeDisabled();await expect(page.getByRole('button',{name:'Accept license & continue to payment',exact:true})).toBeDisabled();await expect(page.getByRole('alert')).toHaveCount(0);
 release();await expect(page.getByRole('status').filter({hasText:/Server-verified purchase status/})).toContainText(/captured/i);await expect(page.getByRole('button',{name:'Refresh provider status',exact:true})).toBeEnabled();
 await callback(page,0,'verify',true);expect(orders).toBe(1);expect(verifies).toBe(1);await reflow(page);
});

test('replacing an education release closes the old checkout and rejects its later provider callback',async({page})=>{
 let verifies=0;const errors:string[]=[];page.on('pageerror',error=>errors.push(error.message));
 await page.route('**/api/v1/templates/*/orders',route=>route.fulfill({json:order}));await page.route('**/api/v1/template-purchases/*/verify',route=>{verifies++;return route.fulfill({json:{status:'CAPTURED'}});});
 await render(page);await begin(page);await page.evaluate(release=>(window as unknown as FixtureWindow).round3Marketplace.select(release),free);
 await expect(page.getByRole('button',{name:'Download reviewed free source',exact:true})).toBeEnabled();await callback(page,0,'verify',true);expect(verifies).toBe(0);
 expect(await page.evaluate(()=>(window as unknown as FixtureWindow).round3Marketplace.closed)).toBe(1);await expect(page.getByRole('status').filter({hasText:/Server-verified purchase/})).toHaveCount(0);expect(errors).toEqual([]);await reflow(page);
});

test('failed education verification retains its purchase and recovers through reconciliation only',async({page})=>{
 let orders=0,reconciles=0,verifies=0;
 await page.route('**/api/v1/templates/*/orders',route=>{orders++;return route.fulfill({json:order});});
 await page.route('**/api/v1/template-purchases/*/verify',route=>{verifies++;return route.fulfill({status:503,json:{error:{code:'SERVICE_UNAVAILABLE',message:'Verification unavailable'}}});});
 await page.route('**/api/v1/template-purchases/*/reconcile',route=>{reconciles++;expect(route.request().method()).toBe('POST');expect(route.request().postDataJSON()).toEqual({});return route.fulfill({json:{status:'CAPTURED'}});});
 await render(page);await begin(page);await callback(page,0,'verify');await expect(page.getByRole('alert')).toContainText('Verification unavailable');
 await page.getByRole('button',{name:'Refresh provider status',exact:true}).click();await expect(page.getByRole('status').filter({hasText:/Current purchase status/})).toContainText(/captured/i);
 expect({orders,reconciles,verifies}).toEqual({orders:1,reconciles:1,verifies:1});await reflow(page);
});

test('education order failure releases controls and explicit retry keeps its idempotency identity',async({page})=>{
 const keys:string[]=[];
 await page.route('**/api/v1/templates/*/orders',route=>{keys.push(route.request().headers()['idempotency-key']);return keys.length===1?route.fulfill({status:503,json:{error:{code:'SERVICE_UNAVAILABLE',message:'Order transport unavailable'}}}):route.fulfill({json:order});});
 await render(page);await page.getByRole('checkbox',{name:/I have read and accept/}).check();await page.getByRole('button',{name:'Accept license & continue to payment',exact:true}).click();await expect(page.getByRole('alert')).toContainText('Order transport unavailable');
 await page.getByRole('button',{name:'Accept license & continue to payment',exact:true}).click();await expect.poll(()=>page.evaluate(()=>(window as unknown as FixtureWindow).round3Marketplace.opened)).toBe(1);
 expect(keys).toHaveLength(2);expect(keys[0]).toBe(keys[1]);await reflow(page);
});

test('education checkout script failure retains the order and refresh only reconciles it',async({page})=>{
 let orders=0,reconciles=0,scripts=0;
 await page.route('**/api/v1/templates/*/orders',route=>{orders++;return route.fulfill({json:order});});
 await page.route('https://checkout.razorpay.com/v1/checkout.js',route=>{scripts++;return route.abort('failed');});
 await page.route('**/api/v1/template-purchases/*/reconcile',route=>{reconciles++;expect(route.request().postDataJSON()).toEqual({});return route.fulfill({json:{status:'CAPTURED'}});});
 await render(page);await page.evaluate(()=>{delete (window as unknown as {Razorpay?:unknown}).Razorpay;});
 await page.getByRole('checkbox',{name:/I have read and accept/}).check();await page.getByRole('button',{name:'Accept license & continue to payment',exact:true}).click();
 await expect(page.getByRole('alert')).toContainText('Checkout could not load');await expect(page.getByRole('button',{name:'Refresh provider status',exact:true})).toBeEnabled();
 await page.getByRole('button',{name:'Refresh provider status',exact:true}).click();await expect(page.getByRole('status').filter({hasText:/Current purchase status/})).toContainText(/captured/i);
 expect({orders,reconciles,scripts}).toEqual({orders:1,reconciles:1,scripts:1});expect(await page.evaluate(()=>(window as unknown as FixtureWindow).round3Marketplace.opened)).toBe(0);await reflow(page);
});

test('education SDK open failure invalidates its callbacks while retaining reconciliation',async({page})=>{
 let orders=0,reconciles=0,verifies=0;
 await page.route('**/api/v1/templates/*/orders',route=>{orders++;return route.fulfill({json:order});});
 await page.route('**/api/v1/template-purchases/*/verify',route=>{verifies++;return route.fulfill({json:{status:'CAPTURED'}});});
 await page.route('**/api/v1/template-purchases/*/reconcile',route=>{reconciles++;return route.fulfill({json:{status:'CAPTURED'}});});
 await render(page);await page.evaluate(()=>{(window as unknown as FixtureWindow).round3Marketplace.throwOnOpen=true;});await begin(page);
 await expect(page.getByRole('alert')).toContainText('Synthetic SDK open unavailable');await callback(page,0,'verify',true);await callback(page,0,'fail');await callback(page,0,'dismiss');
 await expect(page.getByRole('alert')).toContainText('Synthetic SDK open unavailable');expect(verifies).toBe(0);expect(await page.evaluate(()=>(window as unknown as FixtureWindow).round3Marketplace.closed)).toBe(1);
 await page.getByRole('button',{name:'Refresh provider status',exact:true}).click();await expect(page.getByRole('status').filter({hasText:/Current purchase status/})).toContainText(/captured/i);expect({orders,reconciles,verifies}).toEqual({orders:1,reconciles:1,verifies:0});await reflow(page);
});

for(const failure of [false,true])test('free education source '+(failure?'failure permits a safe retry':'download is single-flight and saves the reviewed filename'),async({page})=>{
 let reads=0,release!:()=>void,started!:()=>void;const waiting=new Promise<void>(resolve=>release=resolve),began=new Promise<void>(resolve=>started=resolve);
 await page.route('**/api/v1/education-releases/*/source',async route=>{reads++;expect(route.request().method()).toBe('GET');expect(route.request().url()).toContain('/'+freeId+'/source');if(reads===1){started();await waiting;if(failure)return route.fulfill({status:503,json:{error:{message:'Reviewed source temporarily unavailable'}}});}return route.fulfill({contentType:'application/zip',body:sourceArchive});});
 await render(page);await page.evaluate(release=>(window as unknown as FixtureWindow).round3Marketplace.select(release),free);
 const button=page.getByRole('button',{name:'Download reviewed free source',exact:true});await expect(button).toBeEnabled();
 await button.evaluate(element=>{(element as HTMLButtonElement).click();(element as HTMLButtonElement).click();});await began;expect(reads).toBe(1);await expect(page.getByRole('button',{name:'Preparing source…',exact:true})).toBeDisabled();
 const downloaded=page.waitForEvent('download');if(failure){release();await expect(page.getByRole('alert')).toContainText('Reviewed source temporarily unavailable');await expect(button).toBeEnabled();await button.click();}else release();
 const download=await downloaded;expect(download.suggestedFilename()).toBe('education-'+freeId+'.zip');expect(readFileSync((await download.path())!)).toEqual(sourceArchive);await expect(page.getByRole('status')).toContainText('Reviewed free source downloaded');await expect(button).toBeEnabled();expect(reads).toBe(failure?2:1);await reflow(page);
});

test('education reconciliation is single-flight and cannot overwrite a replacement release',async({page})=>{
 let reconciles=0,release!:()=>void,started!:()=>void;const waiting=new Promise<void>(resolve=>release=resolve),began=new Promise<void>(resolve=>started=resolve);
 await page.route('**/api/v1/templates/*/orders',route=>route.fulfill({json:{...order,status:'UNKNOWN',orderId:null,keyId:null}}));
 await page.route('**/api/v1/template-purchases/*/reconcile',async route=>{reconciles++;started();await waiting;await route.fulfill({json:{status:'CAPTURED'}});});
 await render(page);await page.getByRole('checkbox',{name:/I have read and accept/}).check();await page.getByRole('button',{name:'Accept license & continue to payment',exact:true}).click();
 const button=page.getByRole('button',{name:'Refresh provider status',exact:true});await expect(button).toBeEnabled();await button.evaluate(element=>{(element as HTMLButtonElement).click();(element as HTMLButtonElement).click();});await began;expect(reconciles).toBe(1);await expect(button).toBeDisabled();
 await page.evaluate(release=>(window as unknown as FixtureWindow).round3Marketplace.select(release),free);await expect(page.getByRole('button',{name:'Download reviewed free source',exact:true})).toBeEnabled();
 const response=page.waitForResponse(value=>value.url().endsWith('/template-purchases/'+purchaseId+'/reconcile'));release();await response;await expect(page.getByRole('status').filter({hasText:/Current purchase status/})).toHaveCount(0);await expect(page.getByRole('button',{name:'Download reviewed free source',exact:true})).toBeEnabled();await reflow(page);
});
