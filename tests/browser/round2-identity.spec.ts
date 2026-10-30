import {test,expect,type Page} from '@playwright/test';
import {build} from 'esbuild';

type Pending={path:string;method:string;body:string|undefined;resolve:(response:Response)=>void};
type HarnessWindow=Window&{requests:Omit<Pending,'resolve'>[];pending:Pending[];switchIdentity:()=>void;unverify:()=>void};
const base='/api/v1/me/inquiries';
const row=(id:string,status='INQUIRY_RECEIVED')=>({id,projectTitle:'Project '+id,builder:'Builder '+id,description:'Private request for project '+id,budgetBand:'NEED_ESTIMATE',timelineBand:'FLEXIBLE',status,moderationStatus:'CLEAR',reviewSubmitted:false});
const list=(id='first',hasMore=true)=>({items:[row(id)],page:0,size:1,hasMore});

async function mount(page:Page){
 const script=(await build({stdin:{contents:`
  import React from 'react';import {createRoot} from 'react-dom/client';import ClientRequests from './app/components/client-requests';import {WorkspaceFrame} from './app/components/workspace-frame';
  window.requests=[];window.pending=[];
  window.fetch=(path,init={})=>{const request={path:String(path),method:init.method||'GET',body:init.body};window.requests.push(request);return new Promise(resolve=>window.pending.push({...request,resolve}));};
  function Harness(){const [id,setId]=React.useState('first'),[verified,setVerified]=React.useState(true);window.switchIdentity=()=>setId('second');window.unverify=()=>setVerified(false);return <><button onClick={window.switchIdentity}>Switch identity</button><button onClick={window.unverify}>Unverify identity</button><ClientRequests key={id} verified={verified}/></>;}
  createRoot(document.getElementById('root')).render(<WorkspaceFrame section="personal" title="Client requests" description="Private request recovery for the current account."><Harness/></WorkspaceFrame>);
 `,loader:'tsx',resolveDir:process.cwd()},bundle:true,write:false,platform:'browser',format:'iife',jsx:'automatic',define:{'process.env.NODE_ENV':'"test"'},plugins:[{name:'link-boundary',setup(plugin){
  plugin.onResolve({filter:/^next\/link$/},()=>({path:'link',namespace:'fixture'}));
  plugin.onLoad({filter:/.*/,namespace:'fixture'},()=>({contents:'import React from "react";export default function Link({href,children,prefetch,...props}){return <a href={href} {...props}>{children}</a>}',loader:'jsx',resolveDir:process.cwd()}));
 }}]})).outputFiles[0].text;
 // Keep the production CSS emitted by the real application, including dialog utilities.
 await page.goto('/');const styles=await page.locator('link[rel="stylesheet"],style').evaluateAll(nodes=>nodes.map(node=>node.outerHTML).join(''));
 expect(styles.length).toBeGreaterThan(0);
 await page.route('**/round2-identity-fixture',route=>route.fulfill({contentType:'text/html',body:'<!doctype html><html lang="en"><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>Client request recovery</title>'+styles+'</head><body><div id="root"></div></body></html>'}));
 await page.goto('/round2-identity-fixture');await page.addScriptTag({content:script});
 await expect.poll(()=>page.evaluate(()=>(window as unknown as HarnessWindow).pending.length)).toBe(1);
}
async function respond(page:Page,path:string,body:unknown,status=200,method='GET'){
 await expect.poll(()=>page.evaluate(({path,method})=>(window as unknown as HarnessWindow).pending.some(row=>row.path===path&&row.method===method),{path,method})).toBe(true);
 await page.evaluate(({path,method,body,status})=>{const state=window as unknown as HarnessWindow,index=state.pending.findIndex(row=>row.path===path&&row.method===method);state.pending.splice(index,1)[0].resolve(new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json'}}));},{path,method,body,status});
}
async function open(page:Page,id:string){await page.locator('article').filter({has:page.getByRole('heading',{name:'Project '+id+' · Builder '+id,exact:true})}).getByRole('button',{name:'View request',exact:true}).click();await respond(page,base+'/'+id,{events:[]});}

test('client request continuation waits for page zero, retries a failed page and deduplicates the accepted result',async({page})=>{
 await mount(page);await expect(page.getByRole('button',{name:'Load more',exact:true})).toHaveCount(0);await expect(page.getByText('Start with a project you like.',{exact:true})).toHaveCount(0);
 await respond(page,base,list());await page.getByRole('button',{name:'Load more',exact:true}).click();await respond(page,base+'?page=1&size=1',{error:{message:'Next page unavailable.',code:'UNAVAILABLE'}},503);
 await expect(page.getByRole('alert')).toHaveText('Next page unavailable.');await expect(page.getByRole('heading',{name:'Project first · Builder first',exact:true})).toBeVisible();
 await page.getByRole('button',{name:'Load more',exact:true}).click();await respond(page,base+'?page=1&size=1',{items:[row('first'),row('next')],page:1,size:1,hasMore:false});await expect(page.locator('article')).toHaveCount(2);await expect(page.getByRole('button',{name:'Load more',exact:true})).toHaveCount(0);
});

test('a same-verified identity switch and an unverified switch discard old private reads and writes',async({page})=>{
 await mount(page);await page.getByRole('button',{name:'Switch identity',exact:true}).click();await respond(page,base,list('old-private'));await expect(page.getByText('Private request for project old-private',{exact:true})).toHaveCount(0);
 await respond(page,base,list('second',false));await open(page,'second');await page.getByRole('button',{name:'Close request as not hired',exact:true}).click();
 await expect.poll(()=>page.evaluate(()=>(window as unknown as HarnessWindow).requests.some(row=>row.method==='POST'))).toBe(true);
 await page.evaluate(()=>(window as unknown as HarnessWindow).unverify());await expect(page.getByRole('dialog')).toHaveCount(0);await expect(page.getByText('Confirm your account email to view requests sent with that address.',{exact:true})).toBeVisible();
 await respond(page,base+'/second/not-hired',{ok:true},200,'POST');await expect(page.getByText('Your response has been recorded.',{exact:true})).toHaveCount(0);await expect(page.locator('article')).toHaveCount(0);
 expect(await page.evaluate(path=>(window as unknown as HarnessWindow).requests.filter(row=>row.path===path&&row.method==='GET').length,base)).toBe(2);
});

test('an accepted client action with failed refresh exposes read-only recovery and resets continuation',async({page})=>{
 await mount(page);await respond(page,base,list());await page.getByRole('button',{name:'Load more',exact:true}).click();await open(page,'first');await page.getByRole('button',{name:'Close request as not hired',exact:true}).click();
 await respond(page,base+'/first/not-hired',{ok:true},200,'POST');await respond(page,base,{error:{message:'Current requests unavailable.',code:'UNAVAILABLE'}},503);
 await expect(page.getByRole('status')).toContainText('Your response has been recorded. Current requests could not be refreshed.');await expect(page.getByRole('button',{name:'View request',exact:true})).toBeDisabled();await expect(page.getByRole('button',{name:'Load more',exact:true})).toHaveCount(0);
 await respond(page,base+'?page=1&size=1',{items:[row('stale-continuation')],page:1,size:1,hasMore:false});await expect(page.getByText('Private request for project stale-continuation',{exact:true})).toHaveCount(0);
 await page.getByRole('button',{name:'Retry loading requests',exact:true}).click();await respond(page,base,list('fresh'));await expect(page.getByRole('button',{name:'View request',exact:true})).toBeEnabled();await page.getByRole('button',{name:'Load more',exact:true}).click();await respond(page,base+'?page=1&size=1',{items:[row('fresh-next')],page:1,size:1,hasMore:false});await expect(page.locator('article')).toHaveCount(2);
 expect(await page.evaluate(()=>(window as unknown as HarnessWindow).requests.filter(row=>row.method==='POST').length)).toBe(1);
});

for(const status of [401,403])test(`lost client authority (${status}) clears rows, closes private context and finishes loading`,async({page})=>{
 await mount(page);await respond(page,base,list());await page.getByRole('button',{name:'Load more',exact:true}).click();await respond(page,base+'?page=1&size=1',{error:{message:'Client authority unavailable.',code:'FORBIDDEN'}},status);
 await expect(page.getByRole('alert')).toHaveText('Client authority unavailable.');await expect(page.locator('article')).toHaveCount(0);await expect(page.getByText('Loading your requests…',{exact:true})).toHaveCount(0);await expect(page.getByRole('button',{name:'Retry loading requests',exact:true})).toBeEnabled();await expect(page.getByText('Start with a project you like.',{exact:true})).toHaveCount(0);
});

test('rejected client actions retain their error after refresh and unknown outcomes block repeat writes',async({page})=>{
 await mount(page);await respond(page,base,list('first',false));await open(page,'first');await page.getByRole('button',{name:'Close request as not hired',exact:true}).click();await respond(page,base+'/first/not-hired',{error:{message:'This request already changed.',code:'CONFLICT'}},409,'POST');await respond(page,base,list('first',false));
 await expect(page.getByRole('dialog').getByRole('alert')).toHaveText('This request already changed.');await page.getByRole('button',{name:'Close request as not hired',exact:true}).click();await respond(page,base+'/first/not-hired',{error:{message:'Outcome unknown.',code:'UNAVAILABLE'}},503,'POST');await expect(page.getByRole('button',{name:'Close request as not hired',exact:true})).toBeDisabled();await page.keyboard.press('Escape');await expect(page.getByRole('status')).toContainText('This change could not be confirmed.');await expect(page.getByRole('button',{name:'Retry loading requests',exact:true})).toBeEnabled();
});

test('confirmation lookup retries safely after stripping the bearer URL without resubmitting the action',async({page})=>{
 let checks=0,commits=0;const tokens:string[]=[];
 await page.route('**/api/v1/auth/confirmation',route=>{checks++;tokens.push(route.request().postDataJSON().token);return checks===1?route.fulfill({status:503,json:{error:{message:'Link lookup unavailable.',code:'UNAVAILABLE'}}}):route.fulfill({json:{kind:'EMAIL_VERIFICATION',used:false}});});
 await page.route('**/api/v1/auth/confirm',route=>{commits++;return route.fulfill({json:{ok:true}});});
 await page.goto('/confirm#token=synthetic-confirmation-token-one');await expect(page).toHaveURL(/\/confirm$/);await expect(page.getByRole('alert')).toHaveText('Link lookup unavailable.');
 await page.getByRole('button',{name:'Retry checking link',exact:true}).click();await expect(page.getByRole('heading',{name:'Confirm your email',exact:true})).toBeVisible();expect(checks).toBe(2);expect(tokens).toEqual(['synthetic-confirmation-token-one','synthetic-confirmation-token-one']);expect(commits).toBe(0);
 await page.getByRole('button',{name:'Confirm',exact:true}).click();await expect(page.getByRole('status')).toHaveText('Your response has been recorded.');expect(commits).toBe(1);
});

test('expired confirmation links do not offer a transient retry',async({page})=>{
 await page.route('**/api/v1/auth/confirmation',route=>route.fulfill({status:410,json:{error:{message:'This link expired.',code:'TOKEN_EXPIRED'}}}));await page.goto('/confirm#token=synthetic-confirmation-token-expired');await expect(page.getByRole('alert')).toHaveText('This link expired.');await expect(page.getByRole('button',{name:'Retry checking link',exact:true})).toHaveCount(0);await expect(page).toHaveURL(/\/confirm$/);
});
