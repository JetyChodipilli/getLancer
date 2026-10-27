import {expect,test,type Page} from '@playwright/test';
import {build} from 'esbuild';
import {readFileSync} from 'node:fs';

type Kind='save'|'saved'|'private'|'template';
type Selection={slug:string;preview?:boolean;priceMinor?:number;licenseTerms?:string};
type FixtureWindow=Window&{round2Marketplace:{select:(value:Selection)=>void;opened:number;closed:number;checkoutOptions?:Record<string,unknown>};Razorpay?:new(options:Record<string,unknown>)=>{on:()=>void;open:()=>void;close:()=>void}};
const scripts=new Map<Kind,Promise<string>>();
const recipe=JSON.parse(readFileSync('backend/src/main/resources/catalog/components.json','utf8'))[0];
const saved={...recipe,slug:'alpha',title:'Saved alpha',available:true};
const project=(id:string)=>({id,title:'Private '+id,summary:'Authorized summary '+id,description:'Private description '+id,contribution:'Original builder contribution.',media:[]});
function script(kind:Kind){
 let pending=scripts.get(kind);if(pending)return pending;
 pending=build({stdin:{contents:`
  import {useState} from 'react';import {createRoot} from 'react-dom/client';
  import ComponentSaveButton from './app/components/component-save-button';import SavedComponents from './app/components/saved-components';import PrivatePreview from './app/components/private-preview';import {TemplateDetailView} from './app/components/template-marketplace';
  function Harness(){const [selection,setSelection]=useState({slug:'alpha',preview:false});const f=window.round2Marketplace||={opened:0,closed:0};f.select=setSelection;
   const template={id:selection.slug,slug:selection.slug,title:'Template '+selection.slug,summary:'Reviewed source for the selected release.',description:'Original reviewed source.',category:'SAAS',technology:'REACT',priceMinor:selection.priceMinor||10000,currency:'INR',productId:'project-'+selection.slug,sellerId:'seller',sellerName:'Reviewed builder',versionId:selection.slug+'-v1',version:'1.0.0',licenseTerms:selection.licenseTerms||'One commercial end product. Preserve attribution.',status:'ACTIVE',createdAt:'2026-10-27T06:00:00Z'};
   return ${kind==='save'?'<ComponentSaveButton slug={selection.slug} preview={!!selection.preview}/>':kind==='saved'?'<SavedComponents preview={!!selection.preview}/>':kind==='private'?'<PrivatePreview id={selection.slug}/>':'<TemplateDetailView template={template} preview={!!selection.preview}/>'};
  }createRoot(document.getElementById('root')).render(<Harness/>);
 `,loader:'tsx',resolveDir:process.cwd()},bundle:true,write:false,format:'iife',platform:'browser',jsx:'automatic',loader:{'.css':'empty'},define:{'process.env.NODE_ENV':'"test"'},plugins:[{name:'fixture-link',setup(plugin){plugin.onResolve({filter:/^next\/link$/},()=>({path:'link',namespace:'fixture'}));plugin.onLoad({filter:/.*/,namespace:'fixture'},()=>({contents:'export default function Link({href,children,...props}){return <a href={href} {...props}>{children}</a>}',loader:'jsx',resolveDir:process.cwd()}));}}]}).then(result=>result.outputFiles[0].text);
 scripts.set(kind,pending);return pending;
}
async function render(page:Page,kind:Kind){
 const css=['app/globals.css','app/components.css','app/form-controls.css','app/templates.css'].map(path=>readFileSync(path,'utf8').replace(/^@import[^;]+;/gm,'')).join('\n');
 await page.route('**/round2-marketplace-fixture',route=>route.fulfill({contentType:'text/html',body:'<!doctype html><html lang="en"><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>Marketplace lifecycle regression</title><style>'+css+'</style></head><body><div id="root"></div></body></html>'}));
 await page.goto('/round2-marketplace-fixture');await page.addScriptTag({content:await script(kind)});
}
async function select(page:Page,selection:Selection){await page.evaluate(value=>(window as unknown as FixtureWindow).round2Marketplace.select(value),selection);}

test('pending save stays attached to its slug and the next component can save independently',async({page})=>{
 let release!:()=>void,started!:()=>void;const waiting=new Promise<void>(resolve=>release=resolve),began=new Promise<void>(resolve=>started=resolve);const writes:string[]=[];
 await page.route('**/api/v1/**',async route=>{const request=route.request(),path=new URL(request.url()).pathname;if(request.method()==='GET')return route.fulfill({json:{items:[]}});writes.push(path);if(path==='/api/v1/components/alpha/save'){started();await waiting;}await route.fulfill({json:{saved:true}});});
 await render(page,'save');await page.getByRole('button',{name:'Save component',exact:true}).click();await began;
 await select(page,{slug:'beta'});await expect(page.getByRole('button',{name:'Save component',exact:true})).toBeEnabled();
 const finished=page.waitForResponse(response=>response.url().endsWith('/components/alpha/save'));release();await finished;
 await expect(page.getByRole('button',{name:'Save component',exact:true})).toHaveAttribute('aria-pressed','false');await page.getByRole('button',{name:'Save component',exact:true}).click();await expect(page.getByRole('button',{name:'Saved component',exact:true})).toHaveAttribute('aria-pressed','true');expect(writes).toEqual(['/api/v1/components/alpha/save','/api/v1/components/beta/save']);
});

test('connected save read cannot overwrite a newly selected browser-preview mode',async({page})=>{
 let release!:()=>void,started!:()=>void;const waiting=new Promise<void>(resolve=>release=resolve),began=new Promise<void>(resolve=>started=resolve);
 await page.route('**/api/v1/me/saved-components',async route=>{started();await waiting;await route.fulfill({json:{items:[{slug:'alpha'}]}});});
 await render(page,'save');await began;await select(page,{slug:'alpha',preview:true});await expect(page.getByText('Saved in this browser · sample visitor',{exact:true})).toBeVisible();
 const finished=page.waitForResponse(response=>response.url().endsWith('/me/saved-components'));release();await finished;await expect(page.getByRole('button',{name:'Save component',exact:true})).toHaveAttribute('aria-pressed','false');
 await page.getByRole('button',{name:'Save component',exact:true}).click();await expect(page.getByRole('button',{name:'Saved component',exact:true})).toHaveAttribute('aria-pressed','true');
});

for(const status of [401,403])test('saved deletion '+status+' clears prior account rows and offers sign-in',async({page})=>{
 await page.route('**/api/v1/**',route=>route.request().method()==='GET'?route.fulfill({json:{items:[saved]}}):route.fulfill({status,json:{error:{code:'ACCESS_ENDED',message:'Current account access is required.'}}}));
 await render(page,'saved');await expect(page.getByRole('heading',{name:'Saved alpha',exact:true})).toBeVisible();await page.getByRole('button',{name:'Remove',exact:true}).click();await expect(page.getByRole('heading',{name:'Saved alpha',exact:true})).toHaveCount(0);await expect(page.getByRole('link',{name:'Log in to view saved components',exact:true})).toBeVisible();await expect(page.getByRole('alert')).toContainText('Current account access is required.');
});

test('transient saved deletion failure retains the row and read retry never replays deletion',async({page})=>{
 let writes=0,reads=0;await page.route('**/api/v1/**',route=>{if(route.request().method()==='GET'){reads++;return route.fulfill({json:{items:reads===1?[saved]:[]}});}writes++;return route.fulfill({status:503,json:{error:{message:'Removal outcome is temporarily unavailable.'}}});});
 await render(page,'saved');await page.getByRole('button',{name:'Remove',exact:true}).click();await expect(page.getByRole('heading',{name:'Saved alpha',exact:true})).toBeVisible();await expect(page.getByRole('alert')).toContainText('temporarily unavailable');await page.getByRole('button',{name:'Reload saved components',exact:true}).click();await expect(page.getByRole('heading',{name:'Your next building block is here.',exact:true})).toBeVisible();expect(writes).toBe(1);expect(reads).toBe(2);
});

test('private project selection clears old content and ignores its delayed success',async({page})=>{
 let release!:()=>void,started!:()=>void;const waiting=new Promise<void>(resolve=>release=resolve),began=new Promise<void>(resolve=>started=resolve);
 await page.route('**/api/v1/private/products/*',async route=>{const id=new URL(route.request().url()).pathname.split('/').pop()!;if(id==='alpha'){started();await waiting;}await route.fulfill({json:project(id)});});
 await render(page,'private');await began;await select(page,{slug:'beta'});await expect(page.getByRole('heading',{name:'Private beta',exact:true})).toBeVisible();
 const finished=page.waitForResponse(response=>response.url().endsWith('/private/products/alpha'));release();await finished;await expect(page.getByRole('heading',{name:'Private beta',exact:true})).toBeVisible();await expect(page.getByText('Private description alpha',{exact:true})).toHaveCount(0);
});

test('private preview resets a previous denial for the next authorized resource',async({page})=>{
 await page.route('**/api/v1/private/products/*',route=>route.request().url().endsWith('/alpha')?route.fulfill({status:403,json:{error:{message:'Access to alpha ended.'}}}):route.fulfill({json:project('beta')}));
 await render(page,'private');await expect(page.getByRole('alert')).toContainText('Access to alpha ended.');await select(page,{slug:'beta'});await expect(page.getByRole('heading',{name:'Private beta',exact:true})).toBeVisible();await expect(page.getByRole('alert')).toHaveCount(0);
});

test('prepared order cannot open checkout after the selected template changes',async({page})=>{
 let release!:()=>void,started!:()=>void;const waiting=new Promise<void>(resolve=>release=resolve),began=new Promise<void>(resolve=>started=resolve);const writes:{path:string;body:Record<string,unknown>;key?:string}[]=[];
 await page.route('**/api/v1/**',async route=>{const request=route.request(),path=new URL(request.url()).pathname;writes.push({path,body:request.postDataJSON(),key:request.headers()['idempotency-key']});if(path.endsWith('/verify'))return route.fulfill({json:{status:'CAPTURED'}});if(path==='/api/v1/templates/alpha/orders'){started();await waiting;}await route.fulfill({json:{id:'purchase-'+(path.includes('alpha')?'alpha':'beta'),orderId:'test-order',amountMinor:10000,currency:'INR',keyId:'test-key',mode:'test',status:'ORDER_CREATED'}});});
 await render(page,'template');await page.evaluate(()=>{(window as unknown as FixtureWindow).Razorpay=class{constructor(options:Record<string,unknown>){(window as unknown as FixtureWindow).round2Marketplace.checkoutOptions=options;}on(){}open(){(window as unknown as FixtureWindow).round2Marketplace.opened++;}close(){(window as unknown as FixtureWindow).round2Marketplace.closed++;}};});
 await page.getByRole('button',{name:'Review license & buy',exact:true}).click();await page.getByRole('dialog').getByRole('checkbox').check();await page.getByRole('button',{name:'Accept license & continue to payment',exact:true}).click();await began;
 await select(page,{slug:'beta'});await expect(page.getByRole('heading',{name:'Template beta',exact:true})).toBeVisible();await expect(page.getByRole('dialog')).toHaveCount(0);
 const finished=page.waitForResponse(response=>response.url().endsWith('/templates/alpha/orders'));release();await finished;expect(await page.evaluate(()=>(window as unknown as FixtureWindow).round2Marketplace.opened)).toBe(0);await expect(page.getByText('Purchase Order created',{exact:true})).toHaveCount(0);
 await page.getByRole('button',{name:'Review license & buy',exact:true}).click();await expect(page.getByRole('dialog').getByRole('checkbox')).not.toBeChecked();await page.getByRole('dialog').getByRole('checkbox').check();await page.getByRole('button',{name:'Accept license & continue to payment',exact:true}).click();await expect.poll(()=>page.evaluate(()=>(window as unknown as FixtureWindow).round2Marketplace.opened)).toBe(1);
 expect(writes.map(write=>write.path)).toEqual(['/api/v1/templates/alpha/orders','/api/v1/templates/beta/orders']);expect(writes[0].body.versionId).toBe('alpha-v1');expect(writes[1].body.versionId).toBe('beta-v1');expect(writes[0].key).toBeTruthy();expect(writes[1].key).not.toBe(writes[0].key);
 await page.evaluate(async()=>{const f=(window as unknown as FixtureWindow).round2Marketplace;await (f.checkoutOptions!.handler as (payload:Record<string,string>)=>Promise<void>)({razorpay_payment_id:'test-payment-beta',razorpay_signature:'synthetic-signature'});});await expect(page.getByRole('status')).toContainText('Server-verified purchase status: Captured.');expect(writes).toHaveLength(3);expect(writes[2].path).toBe('/api/v1/template-purchases/purchase-beta/verify');
 await select(page,{slug:'gamma'});await expect(page.getByRole('heading',{name:'Template gamma',exact:true})).toBeVisible();expect(await page.evaluate(()=>(window as unknown as FixtureWindow).round2Marketplace.closed)).toBe(1);
 await page.evaluate(async()=>{const f=(window as unknown as FixtureWindow).round2Marketplace;await (f.checkoutOptions!.handler as (payload:Record<string,string>)=>Promise<void>)({razorpay_payment_id:'late-test-payment-beta',razorpay_signature:'synthetic-signature'});});expect(writes).toHaveLength(3);await expect(page.getByRole('status')).toHaveCount(0);
});

test('changed checkout terms require a fresh explicit license acceptance',async({page})=>{
 await render(page,'template');await page.getByRole('button',{name:'Review license & buy',exact:true}).click();await page.getByRole('dialog').getByRole('checkbox').check();await select(page,{slug:'alpha',priceMinor:20000,licenseTerms:'A revised license for one project.'});await expect(page.getByRole('dialog')).toHaveCount(0);
 await page.getByRole('button',{name:'Review license & buy',exact:true}).click();await expect(page.getByRole('dialog').getByRole('checkbox')).not.toBeChecked();await expect(page.getByRole('dialog')).toContainText('A revised license for one project.');await expect(page.getByRole('button',{name:'Accept license & continue to payment',exact:true})).toBeDisabled();
});
