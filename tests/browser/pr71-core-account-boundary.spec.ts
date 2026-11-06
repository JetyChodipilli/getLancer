import {test,expect,type Page} from '@playwright/test';
import {build} from 'esbuild';

const A='00000000-0000-4000-8000-000000000001',B='00000000-0000-4000-8000-000000000002';
type Call={path:string;method:string;account:string|null;body:string|null};
type Pending=Call&{resolve(body:unknown,status?:number,deferJSON?:boolean):void;reject():void};
type Harness=Window&{calls:Call[];pending:Pending[];pendingJSON:{path:string;resolve():void}[];downloads:string[];createdURLs:string[];revokedURLs:string[]};
const first=(items:unknown[]=[],hasMore=false)=>({items,page:0,size:1,hasMore});
const denied={error:{code:'ACCOUNT_CHANGED',message:'The account bound to this page is no longer current.'}};
const profile=(actor:string)=>({display_name:actor===A?'Actor A builder':'Actor B builder',headline:'An original builder headline',bio:'An original description of the work and relevant experience.',technology:'React',category:'Business',country:'IN',time_zone:'Asia/Kolkata',languages:'English',availability_status:'AVAILABLE_NOW',approval_status:'APPROVED'});
const user=(actor=A)=>({id:actor,email:actor===A?'a@example.test':'b@example.test',displayName:actor===A?'Actor A':'Actor B',roles:['DEVELOPER'],emailVerified:true,activeSlotLimit:3,profile:profile(actor)});
const inquiry=(id='private-a')=>({id,clientName:'Private actor A client',projectTitle:'Private actor A project',description:'Private actor A inquiry',budgetBand:'UNSURE',timelineBand:'FLEXIBLE',status:'INQUIRY_RECEIVED',clientEmail:'private-a@example.test'});
const request=(id='request-a',status='NOT_HIRED')=>({id,projectTitle:'Private actor A request',builder:'Private actor A builder',description:'Private actor A brief',budgetBand:'UNSURE',timelineBand:'FLEXIBLE',status,moderationStatus:'CLEAR',reviewSubmitted:false});
const notification=(id='notice-a')=>({id,title:'Private actor A notification',created_at:'2026-11-05T00:00:00Z'});
const proofProduct={id:'owned-a',slug:'owned-a',title:'Private actor A showcase',summary:'Private original work',description:'Private original work with evidence.',category:'Business',technology:'React',projectType:'SAAS',builder:'Actor A',builderSlug:'actor-a',availability:'AVAILABLE_NOW',contribution:'Built the original implementation.',approvalStatus:'DRAFT',lifecycleStatus:'ACTIVE',visibility:'PUBLIC',availableForSimilarWork:true,updatedAt:'2026-11-05T00:00:00Z'};
const proofBase='/api/v1/developer/products/owned-a';

async function mount(page:Page){
 const script=(await build({stdin:{contents:`
  import {createRoot} from 'react-dom/client';import Workspace from './app/workspace/page';
  window.calls=[];window.pending=[];window.pendingJSON=[];window.downloads=[];window.createdURLs=[];window.revokedURLs=[];
  const createURL=URL.createObjectURL.bind(URL),revokeURL=URL.revokeObjectURL.bind(URL),schedule=window.setTimeout.bind(window),cancel=window.clearTimeout.bind(window),exportTimers=new Map();let timerId=-1;
  URL.createObjectURL=blob=>{const url=createURL(blob);window.createdURLs.push(url);return url;};URL.revokeObjectURL=url=>{window.revokedURLs.push(url);revokeURL(url);};
  // Hold only export cleanup timers so denial/unmount cleanup is deterministic.
  window.setTimeout=(callback,delay,...args)=>{if(delay===1000&&typeof callback==='function'&&callback.toString().includes('revokeObjectURL')){const id=timerId--;exportTimers.set(id,callback);return id;}return schedule(callback,delay,...args);};window.clearTimeout=id=>{exportTimers.delete(id);cancel(id);};
  // Intentionally ignore AbortSignal: accepted responses can outlive a child.
  window.fetch=(path,init={})=>new Promise((resolve,reject)=>{
   const call={path:String(path),method:init.method||'GET',account:new Headers(init.headers).get('X-GetLancer-Account'),body:typeof init.body==='string'?init.body:null};
   window.calls.push(call);window.pending.push({...call,reject:()=>reject(new TypeError('Synthetic transport loss')),resolve:(body,status=200,deferJSON=false)=>{
    const response=new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json'}});
    if(deferJSON)Object.defineProperty(response,'json',{value:()=>new Promise(finish=>window.pendingJSON.push({path:String(path),resolve:()=>finish(body)}))});
    resolve(response);
   }});
  });
  HTMLAnchorElement.prototype.click=function(){if(this.download)window.downloads.push(this.download);};
  createRoot(document.getElementById('root')).render(<Workspace/>);
 `,loader:'tsx',resolveDir:process.cwd()},bundle:true,write:false,loader:{'.css':'empty'},platform:'browser',format:'iife',jsx:'automatic',define:{'process.env.NODE_ENV':'"test"'},plugins:[{name:'core-account-boundary-fixture',setup(plugin){
  plugin.onResolve({filter:/^next\/link$/},()=>({path:'link',namespace:'boundary-fixture'}));
  plugin.onResolve({filter:/workspace-frame$/},()=>({path:'frame',namespace:'boundary-fixture'}));
  plugin.onResolve({filter:/use-taxonomy$/},()=>({path:'taxonomy',namespace:'boundary-fixture'}));
  plugin.onLoad({filter:/.*/,namespace:'boundary-fixture'},args=>({contents:args.path==='link'?'export default function Link({href,children,prefetch,...props}){return <a href={href} {...props}>{children}</a>}':args.path==='frame'?'export function WorkspaceFrame({title,children,actions}){return <main><h1>{title}</h1>{actions}{children}</main>}':'export function useTaxonomy(){return {categories:["Business"],technologies:["React"],taxonomyError:""}}',loader:'jsx',resolveDir:process.cwd()}));
 }}]})).outputFiles[0].text;
 await page.route('**/core-account-boundary-fixture',route=>route.fulfill({contentType:'text/html',body:'<!doctype html><html lang="en"><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>Core account boundary</title></head><body><div id="root"></div></body></html>'}));
 await page.goto('/core-account-boundary-fixture');await page.addScriptTag({content:script});
}
async function pending(page:Page,path:string,method='GET',account?:string){
 await expect.poll(()=>page.evaluate(({path,method,account})=>(window as unknown as Harness).pending.some(call=>call.path===path&&call.method===method&&(account===undefined||call.account===account)),{path,method,account})).toBe(true);
}
async function respond(page:Page,path:string,body:unknown,status=200,method='GET',account?:string,deferJSON=false){
 await pending(page,path,method,account);
 await page.evaluate(({path,body,status,method,account,deferJSON})=>{const queue=(window as unknown as Harness).pending;queue.splice(queue.findIndex(call=>call.path===path&&call.method===method&&(account===undefined||call.account===account)),1)[0].resolve(body,status,deferJSON);},{path,body,status,method,account,deferJSON});
}
async function records(page:Page,actor=A,status='ACTIVE',inquiries=first([inquiry()],true),products=first()){
 await Promise.all([
  respond(page,'/api/v1/developer/products?lifecycleStatus='+status,{...products,totalOwned:1,activeCount:0},200,'GET',actor),
  respond(page,'/api/v1/developer/inquiries',inquiries,200,'GET',actor),
  respond(page,'/api/v1/developer/analytics',{},200,'GET',actor),
 ]);
}
async function load(page:Page,actor=A){await respond(page,'/api/v1/me',user(actor));await records(page,actor);await expect(page.getByRole('heading',{name:actor===A?'Welcome, Actor A.':'Welcome, Actor B.',exact:true})).toBeVisible();}
async function account(page:Page){await page.getByRole('tab',{name:'Account',exact:true}).click();await respond(page,'/api/v1/notifications',first([notification()],true),200,'GET',A);await respond(page,'/api/v1/me/appeals',first(),200,'GET',A);await respond(page,'/api/v1/me/moderation-decisions',first(),200,'GET',A);}
async function startReload(page:Page,actor=A){await page.getByRole('tab',{name:'Showcases',exact:true}).click();await page.getByRole('tab',{name:'Drafts & review',exact:true}).click();await respond(page,'/api/v1/me',user(actor));await pending(page,'/api/v1/developer/products?lifecycleStatus=DRAFT','GET',actor);}
async function loseResponse(page:Page,path:string,method:string){
 await pending(page,path,method,A);await page.evaluate(({path,method})=>{const queue=(window as unknown as Harness).pending;queue.splice(queue.findIndex(call=>call.path===path&&call.method===method&&call.account==='00000000-0000-4000-8000-000000000001'),1)[0].reject();},{path,method});
}
async function settle(page:Page){await page.evaluate(()=>new Promise<void>(done=>requestAnimationFrame(()=>requestAnimationFrame(()=>done()))));}
async function loggedOut(page:Page){await expect(page.getByRole('link',{name:'Log in',exact:true})).toBeVisible();await expect(page.getByText(/Private actor A/)).toHaveCount(0);await expect(page.getByRole('tab',{name:'Account',exact:true})).toHaveCount(0);}
async function stageProofUpload(page:Page){
 const storageWrites:string[]=[];await page.route('**/proof-storage-upload',route=>{storageWrites.push(route.request().method());return route.fulfill({status:200,body:''});});
 await mount(page);await respond(page,'/api/v1/me',user(A));await records(page,A,'ACTIVE',first(),first([proofProduct]));await page.getByRole('button',{name:'Edit showcase',exact:true}).click();await respond(page,proofBase+'/proof',{items:[]},200,'GET',A);
 await page.getByLabel('Add proof image (PNG or JPEG, up to 5 MB)',{exact:true}).setInputFiles({name:'proof.png',mimeType:'image/png',buffer:Buffer.from('synthetic proof image bytes')});
 await respond(page,proofBase+'/media/upload-request',{url:'/proof-storage-upload',headers:{'Content-Type':'image/png'},uploadId:'staged-upload-a'},200,'POST',A);
 await expect.poll(()=>storageWrites).toEqual(['PUT']);await pending(page,'/api/v1/me');
 await expect(page.getByRole('button',{name:'Retry image confirmation',exact:true})).toBeDisabled();
 expect(await page.evaluate(path=>(window as unknown as Harness).calls.filter(call=>call.path===path),proofBase+'/media/complete')).toEqual([]);
}
async function finishProofConfirmation(page:Page){
 await pending(page,proofBase+'/media/complete','POST',A);expect(await page.evaluate(path=>JSON.parse((window as unknown as Harness).calls.find(call=>call.path===path)!.body!),proofBase+'/media/complete')).toEqual({uploadId:'staged-upload-a',altText:'Project screenshot'});
 await respond(page,proofBase+'/media/complete',{},200,'POST',A);await respond(page,proofBase+'/proof',{items:[{id:'proof-a',url:'/proof-a.png',alt:'Project screenshot'}]},200,'GET',A);await respond(page,'/api/v1/me',user(A));await records(page,A,'ACTIVE',first(),first([proofProduct]));
 await expect(page.getByText('Image upload confirmed. Add a description and save.',{exact:true})).toBeVisible();await expect(page.getByLabel('Image 1 description',{exact:true})).toHaveValue('Project screenshot');await expect(page.getByLabel('Add proof image (PNG or JPEG, up to 5 MB)',{exact:true})).toBeEnabled();
 expect(await page.evaluate(()=>(window as unknown as Harness).calls.filter(call=>call.method==='POST').map(({path,account})=>({path,account})))).toEqual([{path:proofBase+'/media/upload-request',account:A},{path:proofBase+'/media/complete',account:A}]);
}

test('proof upload confirms its staged bytes only after a fresh actor and private bundle',async({page})=>{
 await stageProofUpload(page);await respond(page,'/api/v1/me',user(A));await records(page,A,'ACTIVE',first(),first([proofProduct]));await finishProofConfirmation(page);
});

test('proof upload retains staged bytes after a failed fresh read and retry confirmation reads again',async({page})=>{
 await stageProofUpload(page);await respond(page,'/api/v1/me',user(A));await respond(page,'/api/v1/developer/products?lifecycleStatus=ACTIVE',{error:{code:'UNAVAILABLE',message:'Current products are unavailable.'}},503,'GET',A);await respond(page,'/api/v1/developer/inquiries',first(),200,'GET',A);await respond(page,'/api/v1/developer/analytics',{},200,'GET',A);
 await expect(page.getByText('Image bytes were uploaded. Confirmation is still pending.',{exact:true})).toBeVisible();await expect(page.getByText('Image bytes were uploaded, but current workspace details could not be refreshed. Reload proof images before confirming the upload.',{exact:true})).toBeVisible();const retry=page.getByRole('button',{name:'Retry image confirmation',exact:true});await expect(retry).toBeDisabled();await expect(page.getByLabel('Add proof image (PNG or JPEG, up to 5 MB)',{exact:true})).toBeDisabled();
 expect(await page.evaluate(path=>(window as unknown as Harness).calls.filter(call=>call.path===path),proofBase+'/media/complete')).toEqual([]);
 await page.getByRole('button',{name:'Reload proof images',exact:true}).click();await respond(page,proofBase+'/proof',{items:[]},200,'GET',A);await respond(page,'/api/v1/me',user(A));await records(page,A,'ACTIVE',first(),first([proofProduct]));await expect(retry).toBeEnabled();
 await retry.click();await pending(page,'/api/v1/me');expect(await page.evaluate(path=>(window as unknown as Harness).calls.filter(call=>call.path===path),proofBase+'/media/complete')).toEqual([]);await respond(page,'/api/v1/me',user(A));await records(page,A,'ACTIVE',first(),first([proofProduct]));await finishProofConfirmation(page);
});

test('proof upload cannot confirm old actor bytes after the fresh read changes accounts',async({page})=>{
 await stageProofUpload(page);await respond(page,'/api/v1/me',user(B));await records(page,B,'ACTIVE',first());await expect(page.getByRole('heading',{name:'Welcome, Actor B.',exact:true})).toBeVisible();await expect(page.getByRole('dialog')).toHaveCount(0);await settle(page);
 expect(await page.evaluate(path=>(window as unknown as Harness).calls.filter(call=>call.path===path),proofBase+'/media/complete')).toEqual([]);await expect(page.getByText('Image upload confirmed. Add a description and save.',{exact:true})).toHaveCount(0);
 await page.getByRole('tab',{name:'Profile',exact:true}).click();await page.getByRole('button',{name:'Save profile',exact:true}).click();await pending(page,'/api/v1/developer/profile','PUT',B);await respond(page,'/api/v1/developer/profile',{error:{code:'VALIDATION_ERROR',message:'Synthetic current rejection.'}},400,'PUT',B);
});

test('private reads after fresh identity remain bound across the cookie handoff gap',async({page})=>{
 await mount(page);await respond(page,'/api/v1/me',user(A));
 // The server session has changed to B after /me. A is only a rejection assertion.
 await pending(page,'/api/v1/developer/products?lifecycleStatus=ACTIVE','GET',A);await pending(page,'/api/v1/developer/inquiries','GET',A);await pending(page,'/api/v1/developer/analytics','GET',A);
 await respond(page,'/api/v1/developer/inquiries',denied,401,'GET',A);await loggedOut(page);
 await respond(page,'/api/v1/developer/products?lifecycleStatus=ACTIVE',{...first(),totalOwned:1},200,'GET',A);await respond(page,'/api/v1/developer/analytics',{qualifiedInquiries:999},200,'GET',A);await settle(page);await loggedOut(page);
});

for(const kind of ['developer inquiries','client requests','notifications'] as const)test(kind+' continuation rejects a different current cookie actor',async({page})=>{
 await mount(page);await load(page);
 let path:string;
 if(kind==='developer inquiries'){await page.getByRole('tab',{name:/^Inquiries/}).click();path='/api/v1/developer/inquiries?page=1&size=1';await page.getByRole('button',{name:'Load more',exact:true}).click();}
 else if(kind==='client requests'){await page.getByRole('tab',{name:'My requests',exact:true}).click();await respond(page,'/api/v1/me/inquiries',first([request()],true),200,'GET',A);path='/api/v1/me/inquiries?page=1&size=1';await page.getByRole('button',{name:'Load more',exact:true}).click();}
 else{await account(page);await page.getByText('Close my account',{exact:true}).click();await page.getByLabel('Type your email to request a confirmation link',{exact:true}).fill('a@example.test');path='/api/v1/notifications?page=1&size=1';await page.getByRole('button',{name:'Load more notifications',exact:true}).click();}
 await pending(page,path,'GET',A);await respond(page,path,denied,401,'GET',A);await loggedOut(page);await expect(page.getByRole('alert')).toContainText('no longer current');expect(await page.locator('input').evaluateAll(inputs=>inputs.some(input=>(input as HTMLInputElement).value==='a@example.test'))).toBe(false);
});

for(const status of [401,403])test('unmounted child '+status+' denial invalidates a pending same-actor parent load',async({page})=>{
 await mount(page);await load(page);await page.getByRole('tab',{name:'Account',exact:true}).click();await pending(page,'/api/v1/notifications','GET',A);
 await startReload(page);await respond(page,'/api/v1/notifications',denied,status,'GET',A);await loggedOut(page);
 await records(page,A,'DRAFT');await settle(page);await loggedOut(page);
});

test('a late previous-actor child denial leaves the successfully loaded new account intact',async({page})=>{
 await mount(page);await load(page);await page.getByRole('tab',{name:'Account',exact:true}).click();await pending(page,'/api/v1/notifications','GET',A);
 await startReload(page,B);await records(page,B,'DRAFT',first());await expect(page.getByRole('heading',{name:'Welcome, Actor B.',exact:true})).toBeVisible();
 await respond(page,'/api/v1/notifications',denied,401,'GET',A);await settle(page);
 await expect(page.getByRole('heading',{name:'Welcome, Actor B.',exact:true})).toBeVisible();await expect(page.getByRole('link',{name:'Log in',exact:true})).toHaveCount(0);
 await page.getByRole('tab',{name:'Profile',exact:true}).click();await expect(page.getByLabel('Display name',{exact:true})).toHaveValue('Actor B builder');
});

test('notification denial during export JSON parsing blocks the stale download',async({page})=>{
 await mount(page);await load(page);await account(page);await page.getByRole('button',{name:'Download my account data',exact:true}).click();
 await respond(page,'/api/v1/me/export',{account:{id:A},secret:'Private actor A export'},200,'GET',A,true);
 await expect.poll(()=>page.evaluate(()=>(window as unknown as Harness).pendingJSON.some(row=>row.path==='/api/v1/me/export'))).toBe(true);
 await page.getByRole('button',{name:'Mark read',exact:true}).click();await respond(page,'/api/v1/notifications/notice-a/read',denied,403,'PATCH',A);await loggedOut(page);
 await page.evaluate(()=>{const queue=(window as unknown as Harness).pendingJSON;queue.splice(queue.findIndex(row=>row.path==='/api/v1/me/export'),1)[0].resolve();});await settle(page);
 expect(await page.evaluate(()=>(window as unknown as Harness).downloads)).toEqual([]);await expect(page.getByText('Your account export is ready.',{exact:true})).toHaveCount(0);
});

test('export succeeds for the bound actor and a new actor cannot reuse an old export',async({page})=>{
 await mount(page);await load(page);await account(page);await page.getByRole('button',{name:'Download my account data',exact:true}).click();await respond(page,'/api/v1/me/export',{account:{id:A}},200,'GET',A);
 await expect(page.getByText('Your account export is ready.',{exact:true})).toBeVisible();expect(await page.evaluate(()=>(window as unknown as Harness).downloads)).toEqual(['getlancer-account.json']);
 await page.getByRole('button',{name:'Download my account data',exact:true}).click();await pending(page,'/api/v1/me/export','GET',A);
 await startReload(page,B);await records(page,B,'DRAFT',first());await respond(page,'/api/v1/me/export',{account:{id:A},secret:'Private actor A export'},200,'GET',A);await settle(page);
 expect(await page.evaluate(()=>(window as unknown as Harness).downloads)).toEqual(['getlancer-account.json']);await expect(page.getByRole('heading',{name:'Welcome, Actor B.',exact:true})).toBeVisible();
});

test('a retained profile cannot write through a different cookie actor',async({page})=>{
 await mount(page);await load(page);await page.getByRole('tab',{name:'Profile',exact:true}).click();await page.getByLabel('Display name',{exact:true}).fill('Private actor A unsaved profile');await page.getByRole('button',{name:'Save profile',exact:true}).click();
 await pending(page,'/api/v1/developer/profile','PUT',A);await respond(page,'/api/v1/developer/profile',denied,401,'PUT',A);await loggedOut(page);expect(await page.locator('input').evaluateAll(inputs=>inputs.some(input=>(input as HTMLInputElement).value==='Private actor A unsaved profile'))).toBe(false);
 expect(await page.evaluate(()=>(window as unknown as Harness).calls.filter(call=>call.path==='/api/v1/me').length)).toBe(1);
});

test('client write transport loss requires an explicit read before another write',async({page})=>{
 await mount(page);await load(page);await page.getByRole('tab',{name:'My requests',exact:true}).click();await respond(page,'/api/v1/me/inquiries',first([request('expired','EXPIRED')]),200,'GET',A);
 const resend=page.getByRole('button',{name:'Resend confirmation',exact:true});await resend.click();await pending(page,'/api/v1/me/inquiries/expired/confirmation-link','POST',A);
 await page.evaluate(()=>{const queue=(window as unknown as Harness).pending;queue.splice(queue.findIndex(call=>call.method==='POST'&&call.path.endsWith('/confirmation-link')),1)[0].reject();});
 await expect(page.getByText('This change could not be confirmed. Refresh your requests before trying again.',{exact:true})).toBeVisible();await expect(resend).toBeDisabled();
 expect(await page.evaluate(()=>(window as unknown as Harness).calls.filter(call=>call.path==='/api/v1/me/inquiries').length)).toBe(1);
 await page.getByRole('button',{name:'Retry loading requests',exact:true}).click();await respond(page,'/api/v1/me/inquiries',first([request('expired','EXPIRED')]),200,'GET',A);await respond(page,'/api/v1/me',user(A));await records(page);await expect(resend).toBeEnabled();
 expect(await page.evaluate(()=>(window as unknown as Harness).calls.filter(call=>call.method==='POST').length)).toBe(1);
});

for(const child of ['proof','access'] as const)test(child+' editor uses the rendered actor and propagates denial to the workspace',async({page})=>{
 await mount(page);await respond(page,'/api/v1/me',user(A));
 const product={id:'owned-a',slug:'owned-a',title:'Private actor A showcase',summary:'Private original work',description:'Private original work with evidence.',category:'Business',technology:'React',projectType:'SAAS',builder:'Actor A',builderSlug:'actor-a',availability:'AVAILABLE_NOW',contribution:'Built the original implementation.',approvalStatus:'DRAFT',lifecycleStatus:'ACTIVE',visibility:'PRIVATE_CASE_STUDY',availableForSimilarWork:true,updatedAt:'2026-11-05T00:00:00Z'};
 await records(page,A,'ACTIVE',first(),first([product]));await page.getByRole('button',{name:'Edit showcase',exact:true}).click();
 const path='/api/v1/developer/products/owned-a/'+child;await pending(page,path,'GET',A);
 await respond(page,path,denied,403,'GET',A);await loggedOut(page);await expect(page.getByRole('dialog')).toHaveCount(0);
});

test('an inquiry response keeps the displayed actor assertion on a changed session',async({page})=>{
 await mount(page);await load(page);await page.getByRole('tab',{name:/^Inquiries/}).click();await page.getByRole('button',{name:'Mark responded',exact:true}).click();
 await pending(page,'/api/v1/inquiries/private-a/responded','POST',A);await respond(page,'/api/v1/inquiries/private-a/responded',denied,401,'POST',A);await loggedOut(page);
 expect(await page.evaluate(()=>(window as unknown as Harness).calls.filter(call=>call.method==='POST').map(call=>call.account))).toEqual([A]);
});

for(const status of [0,502])test('workspace write '+status+' remains blocked until a fresh actor/private bundle is loaded',async({page})=>{
 await mount(page);await load(page);await page.getByRole('tab',{name:/^Inquiries/}).click();const respondButton=page.getByRole('button',{name:'Mark responded',exact:true});await respondButton.click();
 if(status===0)await loseResponse(page,'/api/v1/inquiries/private-a/responded','POST');else await respond(page,'/api/v1/inquiries/private-a/responded',{error:{code:'INVALID_RESPONSE',message:'The change response could not be confirmed.'}},status,'POST',A);
 await expect(respondButton).toBeDisabled();await expect(page.getByText('This change could not be confirmed. Reload the workspace before trying again.',{exact:true})).toBeVisible();
 expect(await page.evaluate(()=>(window as unknown as Harness).calls.filter(call=>call.path==='/api/v1/me').length)).toBe(1);
 await page.getByRole('button',{name:'Reload workspace',exact:true}).click();await respond(page,'/api/v1/me',user(A));await records(page);await expect(respondButton).toBeEnabled();
 expect(await page.evaluate(()=>(window as unknown as Harness).calls.filter(call=>call.method==='POST').length)).toBe(1);
});

for(const status of [0,502])test('profile write '+status+' offers a refresh-only recovery before another save',async({page})=>{
 await mount(page);await load(page);await page.getByRole('tab',{name:'Profile',exact:true}).click();const save=page.getByRole('button',{name:'Save profile',exact:true});await page.getByLabel('Display name',{exact:true}).fill('Uncertain profile edit');await save.click();
 if(status===0)await loseResponse(page,'/api/v1/developer/profile','PUT');else await respond(page,'/api/v1/developer/profile',{error:{code:'INVALID_RESPONSE',message:'The saved profile could not be confirmed.'}},status,'PUT',A);
 await expect(save).toBeDisabled();await page.getByRole('button',{name:'Retry profile refresh',exact:true}).click();await respond(page,'/api/v1/me',user(A));await records(page);await expect(save).toBeEnabled();
 expect(await page.evaluate(()=>(window as unknown as Harness).calls.filter(call=>call.path==='/api/v1/developer/profile'&&call.method==='PUT').length)).toBe(1);
});

test('notification retry cannot clear an uncertain account-closure barrier',async({page})=>{
 await mount(page);await load(page);await page.getByRole('tab',{name:'Account',exact:true}).click();
 await respond(page,'/api/v1/notifications',{error:{code:'UNAVAILABLE',message:'Notification fixture unavailable.'}},503,'GET',A);await respond(page,'/api/v1/me/appeals',first(),200,'GET',A);await respond(page,'/api/v1/me/moderation-decisions',first(),200,'GET',A);
 await page.getByText('Close my account',{exact:true}).click();await page.getByLabel('Type your email to request a confirmation link',{exact:true}).fill('a@example.test');const close=page.getByRole('button',{name:'Email me a closure confirmation',exact:true});await close.click();await loseResponse(page,'/api/v1/me','DELETE');await expect(close).toBeDisabled();
 await page.getByRole('button',{name:'Retry notifications',exact:true}).click();await respond(page,'/api/v1/notifications',first(),200,'GET',A);await expect(close).toBeDisabled();
 await page.getByRole('button',{name:'Reload account details',exact:true}).click();await respond(page,'/api/v1/me',user(A));await records(page);await expect(close).toBeEnabled();
 expect(await page.evaluate(()=>(window as unknown as Harness).calls.filter(call=>call.path==='/api/v1/me'&&call.method==='DELETE').length)).toBe(1);
});

test('an accepted workspace write keeps its barrier when the follow-up private read fails',async({page})=>{
 await mount(page);await load(page);await page.getByRole('tab',{name:/^Inquiries/}).click();const respondButton=page.getByRole('button',{name:'Mark responded',exact:true});await respondButton.click();await respond(page,'/api/v1/inquiries/private-a/responded',{},200,'POST',A);
 await respond(page,'/api/v1/me',user(A));await respond(page,'/api/v1/developer/products?lifecycleStatus=ACTIVE',{error:{code:'UNAVAILABLE',message:'Current products are unavailable.'}},503,'GET',A);await respond(page,'/api/v1/developer/inquiries',first([inquiry()],true),200,'GET',A);await respond(page,'/api/v1/developer/analytics',{},200,'GET',A);
 await expect(respondButton).toBeDisabled();await expect(page.getByText('The change was recorded, but current workspace details could not be refreshed. Reload the workspace before another change.',{exact:true})).toBeVisible();
 await page.getByRole('button',{name:'Reload workspace',exact:true}).click();await respond(page,'/api/v1/me',user(A));await records(page);await expect(respondButton).toBeEnabled();
 expect(await page.evaluate(()=>(window as unknown as Harness).calls.filter(call=>call.method==='POST').length)).toBe(1);
});

test('an uncertain appeal waits for current moderation records and the parent account bundle',async({page})=>{
 await mount(page);await load(page);await page.getByRole('tab',{name:'Account',exact:true}).click();await respond(page,'/api/v1/notifications',first(),200,'GET',A);await respond(page,'/api/v1/me/appeals',first(),200,'GET',A);await respond(page,'/api/v1/me/moderation-decisions',first([{id:'decision-a',target_type:'PRODUCT',action:'SUSPEND',reason:'Synthetic decision'}]),200,'GET',A);
 await page.getByLabel('Why should this change?',{exact:true}).fill('Evidence explaining why the synthetic decision needs a fresh review.');const submit=page.getByRole('button',{name:'Submit appeal',exact:true});await submit.click();await loseResponse(page,'/api/v1/appeals','POST');await expect(submit).toBeDisabled();
 await page.getByRole('button',{name:'Refresh decisions and appeals',exact:true}).click();await respond(page,'/api/v1/me/appeals',first(),200,'GET',A);await respond(page,'/api/v1/me/moderation-decisions',first([{id:'decision-a',target_type:'PRODUCT',action:'SUSPEND',reason:'Synthetic decision'}]),200,'GET',A);await respond(page,'/api/v1/me',user(A));await records(page);await expect(submit).toBeEnabled();
 expect(await page.evaluate(()=>(window as unknown as Harness).calls.filter(call=>call.path==='/api/v1/appeals'&&call.method==='POST').length)).toBe(1);
});

test('an uncertain profile write remains fenced after switching tabs and remounting the form',async({page})=>{
 await mount(page);await load(page);await page.getByRole('tab',{name:'Profile',exact:true}).click();await page.getByRole('button',{name:'Save profile',exact:true}).click();await loseResponse(page,'/api/v1/developer/profile','PUT');
 await page.getByRole('tab',{name:'Showcases',exact:true}).click();await page.getByRole('tab',{name:'Profile',exact:true}).click();await page.getByRole('button',{name:'Save profile',exact:true}).click();await settle(page);
 expect(await page.evaluate(()=>(window as unknown as Harness).calls.filter(call=>call.path==='/api/v1/developer/profile'&&call.method==='PUT').length)).toBe(1);await expect(page.getByRole('button',{name:'Reload workspace',exact:true})).toBeVisible();
 await page.getByRole('button',{name:'Reload workspace',exact:true}).click();await respond(page,'/api/v1/me',user(A));await records(page);await page.getByRole('button',{name:'Save profile',exact:true}).click();await pending(page,'/api/v1/developer/profile','PUT',A);
 await respond(page,'/api/v1/developer/profile',{error:{code:'VALIDATION_ERROR',message:'Synthetic current rejection.'}},400,'PUT',A);
});

test('a parent read begun before an uncertain write cannot release its fence or install an older snapshot',async({page})=>{
 await mount(page);await load(page);await startReload(page);await page.getByRole('tab',{name:'Profile',exact:true}).click();await page.getByRole('button',{name:'Save profile',exact:true}).click();await loseResponse(page,'/api/v1/developer/profile','PUT');
 await records(page,A,'DRAFT',first([{...inquiry(),id:'obsolete',description:'Obsolete pre-write private snapshot'}]));await settle(page);
 await page.getByRole('tab',{name:/^Inquiries/}).click();await expect(page.getByText('Obsolete pre-write private snapshot',{exact:true})).toHaveCount(0);
 await page.getByRole('tab',{name:'Profile',exact:true}).click();await page.getByRole('button',{name:'Save profile',exact:true}).click();await settle(page);
 expect(await page.evaluate(()=>(window as unknown as Harness).calls.filter(call=>call.path==='/api/v1/developer/profile'&&call.method==='PUT').length)).toBe(1);await expect(page.getByRole('button',{name:'Reload workspace',exact:true})).toBeEnabled();
});

test('a previous actor write settling late cannot fence the successfully loaded new actor',async({page})=>{
 await mount(page);await load(page);await page.getByRole('tab',{name:'Profile',exact:true}).click();await page.getByRole('button',{name:'Save profile',exact:true}).click();await pending(page,'/api/v1/developer/profile','PUT',A);
 await startReload(page,B);await records(page,B,'DRAFT',first());await expect(page.getByRole('heading',{name:'Welcome, Actor B.',exact:true})).toBeVisible();await loseResponse(page,'/api/v1/developer/profile','PUT');await settle(page);
 await expect(page.getByRole('button',{name:'Reload workspace',exact:true})).toHaveCount(0);await page.getByRole('tab',{name:'Profile',exact:true}).click();await page.getByRole('button',{name:'Save profile',exact:true}).click();await pending(page,'/api/v1/developer/profile','PUT',B);
 await respond(page,'/api/v1/developer/profile',{error:{code:'VALIDATION_ERROR',message:'Synthetic current rejection.'}},400,'PUT',B);
});

for(const cleanup of ['denial','unmount'] as const)test('account export revokes its outstanding object URL on '+cleanup,async({page})=>{
 await mount(page);await load(page);await account(page);await page.getByRole('button',{name:'Download my account data',exact:true}).click();await respond(page,'/api/v1/me/export',{account:{id:A}},200,'GET',A);await expect(page.getByText('Your account export is ready.',{exact:true})).toBeVisible();
 expect(await page.evaluate(()=>(window as unknown as Harness).createdURLs.length)).toBe(1);expect(await page.evaluate(()=>(window as unknown as Harness).revokedURLs)).toEqual([]);
 if(cleanup==='denial'){await page.getByRole('button',{name:'Mark read',exact:true}).click();await respond(page,'/api/v1/notifications/notice-a/read',denied,403,'PATCH',A);await loggedOut(page);}
 else await page.getByRole('tab',{name:'Showcases',exact:true}).click();
 await expect.poll(()=>page.evaluate(()=>{const state=window as unknown as Harness;return state.revokedURLs.includes(state.createdURLs[0]);})).toBe(true);
});
