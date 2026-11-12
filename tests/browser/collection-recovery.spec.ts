import {test,expect,type Page} from '@playwright/test';
import {build} from 'esbuild';

type Call={path:string;method:string};
type Pending=Call&{resolve(body:unknown,status?:number):void};
type HarnessWindow=Window&{calls:Call[];pending:Pending[]};
const saved='/api/v1/me/saved-products?page=0';
const continuation='/api/v1/me/saved-products?page=1&size=50';
const projects=(start:number,count:number)=>Array.from({length:count},(_,offset)=>({id:String(start+offset),slug:'project-'+(start+offset),title:'Saved project '+(start+offset),summary:'Original work',available:true}));
const first=(items:unknown[],hasMore=false)=>({items,page:0,size:50,hasMore});
const review=(id:string)=>({id,rating:5,reviewText:'Review '+id,createdAt:'2030-01-01T12:00:00Z'});

async function mount(page:Page,kind:'saved'|'reviews'|'history',initialPage:unknown=null){
 const harness=kind==='saved'?'<Saved/>':kind==='reviews'?`<PublicReviews slug="first-builder" initialPage={${JSON.stringify(initialPage)}} initialError={${initialPage?'""':'"Client reviews are temporarily unavailable. Retry loading them."'}}/>`:`<HistoryHarness/>`;
 const content=(await build({stdin:{contents:`
  import {useState} from 'react';import {createRoot} from 'react-dom/client';import Saved from './app/saved/page';import PublicReviews from './app/components/public-reviews';import ComponentVersionHistory from './app/components/component-version-history';
  window.calls=[];window.pending=[];
  // Ignore AbortSignal intentionally: stale-response guards must remain effective
  // even when a response has already crossed the cancellation boundary.
  window.fetch=(path,init={})=>new Promise(resolve=>{if(String(path)==='/api/v1/me'){resolve(Response.json({id:'00000000-0000-4000-8000-000000000001',roles:['CLIENT']}));return;}const call={path:String(path),method:init.method||'GET'};window.calls.push(call);window.pending.push({...call,resolve:(body,status=200)=>resolve(new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json'}}))});});
  function HistoryHarness(){const [slug,setSlug]=useState('first'),[version,setVersion]=useState('1'),[preview,setPreview]=useState(false);return <><button onClick={()=>setSlug('second')}>Change component</button><button onClick={()=>setVersion('2')}>Change release</button><button onClick={()=>setPreview(true)}>Use preview</button><ComponentVersionHistory slug={slug} preview={preview} version={version} sha256={'hash-'+version}/></>;}
  createRoot(document.getElementById('root')).render(${harness});
 `,loader:'tsx',resolveDir:process.cwd()},bundle:true,write:false,platform:'browser',format:'iife',jsx:'automatic',define:{'process.env.NODE_ENV':'"test"'},plugins:[{name:'fixture-link',setup(plugin){
  plugin.onResolve({filter:/^next\/link$/},()=>({path:'link',namespace:'fixture'}));
  plugin.onLoad({filter:/.*/,namespace:'fixture'},()=>({contents:'export default function Link({href,children,...props}){return <a href={href} {...props}>{children}</a>}',loader:'jsx',resolveDir:process.cwd()}));
 }}]})).outputFiles[0].text;
 await page.setContent('<div id="root"></div>');await page.addScriptTag({content});
}
async function respond(page:Page,path:string,body:unknown,status=200,method='GET'){
 await expect.poll(()=>page.evaluate(({path,method})=>(window as unknown as HarnessWindow).pending.some(call=>call.path===path&&call.method===method),{path,method})).toBe(true);
 await page.evaluate(({path,body,status,method})=>{const pending=(window as unknown as HarnessWindow).pending;pending.splice(pending.findIndex(call=>call.path===path&&call.method===method),1)[0].resolve(body,status);},{path,body,status,method});
}
const failure=(message:string)=>({error:{code:'UNAVAILABLE',message}});
async function calls(page:Page){return page.evaluate(()=>(window as unknown as HarnessWindow).calls);}

test('review recovery retries page0 before continuing with the verified default page size',async({page})=>{
 await mount(page,'reviews');await expect(page.getByRole('alert')).toContainText('temporarily unavailable');
 await expect(page.getByText('No published engagement reviews yet.',{exact:true})).toHaveCount(0);await expect(page.getByRole('button',{name:'Load more client reviews',exact:true})).toHaveCount(0);
 const retry=page.getByRole('button',{name:'Retry client reviews',exact:true});await retry.click();
 const path='/api/v1/builders/first-builder/reviews?page=0';await respond(page,path,failure('Review service unavailable'),503);await expect(page.getByRole('alert')).toHaveText('Review service unavailable');
 await retry.click();await respond(page,path,first([review('first'),...Array.from({length:49},(_,index)=>review('page0-'+index))],true));await expect(page.getByText('Review first',{exact:true})).toBeVisible();
 await page.getByRole('button',{name:'Load more client reviews',exact:true}).click();await respond(page,'/api/v1/builders/first-builder/reviews?page=1&size=50',{items:[review('second')],page:1,size:50,hasMore:false});
 await expect(page.getByText('Review first',{exact:true})).toHaveCount(1);await expect(page.getByText('Review second',{exact:true})).toHaveCount(1);await expect(page.locator('#client-reviews article')).toHaveCount(51);await expect(page.getByRole('button',{name:'Load more client reviews',exact:true})).toHaveCount(0);
 expect((await calls(page)).map(call=>call.path)).toEqual([path,path,'/api/v1/builders/first-builder/reviews?page=1&size=50']);
});

test('a successful terminal review page needs no retry or continuation request',async({page})=>{
 await mount(page,'reviews',first([]));await expect(page.getByText('No published engagement reviews yet.',{exact:true})).toBeVisible();await expect(page.getByRole('button')).toHaveCount(0);expect(await calls(page)).toEqual([]);
});

test('saved initial loading and malformed/failed page0 cannot become an empty list or page1 retry',async({page})=>{
 await mount(page,'saved');await expect(page.getByRole('status')).toHaveText('Loading saved projects…');await expect(page.getByText('Your next idea could be here.',{exact:true})).toHaveCount(0);await expect(page.getByRole('button',{name:'Load more saved projects',exact:true})).toHaveCount(0);
 await respond(page,saved,failure('Saved service unavailable'),503);const retry=page.getByRole('button',{name:'Retry loading saved projects',exact:true});await expect(page.getByRole('alert')).toContainText('Saved service unavailable');
 await retry.click();await respond(page,saved,{items:projects(0,1),hasMore:true});await expect(page.getByRole('alert')).toContainText('could not be verified');await expect(page.getByRole('button',{name:'Load more saved projects',exact:true})).toHaveCount(0);
 await retry.click();await respond(page,saved,first([]));await expect(page.getByText('Your next idea could be here.',{exact:true})).toBeVisible();await expect(page.getByRole('alert')).toHaveCount(0);await expect(page.getByRole('button',{name:'Load more saved projects',exact:true})).toHaveCount(0);
 expect((await calls(page)).map(call=>call.path)).toEqual([saved,saved,saved]);
});

for(const order of [[0,1],[1,0]])test('parallel saved removals stay absent in response order '+order.join(','),async({page})=>{
 await mount(page,'saved');await respond(page,saved,first(projects(0,3)));
 await page.getByRole('button',{name:'Remove Saved project 0',exact:true}).click();await page.getByRole('button',{name:'Remove Saved project 1',exact:true}).click();
 await expect(page.getByRole('button',{name:'Remove Saved project 0',exact:true})).toBeDisabled();
 await respond(page,'/api/v1/products/'+order[0]+'/save',{},200,'DELETE');await expect(page.getByRole('link',{name:'Saved project '+order[0],exact:true})).toHaveCount(0);
 expect((await calls(page)).filter(call=>call.path===saved)).toHaveLength(1);await expect(page.getByRole('link',{name:'Saved project 2',exact:true})).toBeVisible();
 await respond(page,'/api/v1/products/'+order[1]+'/save',{},200,'DELETE');await respond(page,saved,first(projects(2,1)));
 await expect(page.getByRole('link',{name:'Saved project 0',exact:true})).toHaveCount(0);await expect(page.getByRole('link',{name:'Saved project 1',exact:true})).toHaveCount(0);await expect(page.getByRole('link',{name:'Saved project 2',exact:true})).toBeVisible();
 expect((await calls(page)).filter(call=>call.method==='DELETE').map(call=>call.path).sort()).toEqual(['/api/v1/products/0/save','/api/v1/products/1/save']);
});

test('a transient saved removal failure retains rows and permits retry after reconciliation',async({page})=>{
 await mount(page,'saved');await respond(page,saved,first(projects(0,2)));const remove=page.getByRole('button',{name:'Remove Saved project 0',exact:true});
 await remove.click();await respond(page,'/api/v1/products/0/save',failure('Removal not confirmed'),503,'DELETE');await expect(page.getByRole('alert')).toHaveText('Removal not confirmed');await expect(page.getByRole('link',{name:'Saved project 1',exact:true})).toBeVisible();
 await respond(page,saved,first(projects(0,2)));await expect(remove).toBeEnabled();await remove.click();await respond(page,'/api/v1/products/0/save',{},200,'DELETE');await respond(page,saved,first(projects(1,1)));
 await expect(page.getByRole('link',{name:'Saved project 0',exact:true})).toHaveCount(0);await expect(page.getByRole('link',{name:'Saved project 1',exact:true})).toBeVisible();await expect(page.getByRole('alert')).toHaveCount(0);
});

test('saved deletion resets OFFSET pagination and discards an outstanding old continuation',async({page})=>{
 await mount(page,'saved');await respond(page,saved,first(projects(0,50),true));await page.getByRole('button',{name:'Load more saved projects',exact:true}).click();
 await page.getByRole('button',{name:'Remove Saved project 0',exact:true}).click();await respond(page,'/api/v1/products/0/save',{},200,'DELETE');await respond(page,saved,first(projects(1,50),true));
 await respond(page,continuation,{items:projects(999,1),page:1,size:50,hasMore:false});await expect(page.getByRole('link',{name:'Saved project 999',exact:true})).toHaveCount(0);
 await expect(page.getByRole('link',{name:'Saved project 50',exact:true})).toBeVisible();const more=page.getByRole('button',{name:'Load more saved projects',exact:true});await more.click();await respond(page,continuation,failure('Continuation unavailable'),503);await expect(page.getByRole('alert')).toHaveText('Continuation unavailable');await expect(page.getByRole('link',{name:'Saved project 1',exact:true})).toBeVisible();
 await more.click();await respond(page,continuation,{items:projects(51,2),page:1,size:50,hasMore:false});
 await expect(page.locator('a[href^="/products/"]')).toHaveCount(52);await expect(page.getByRole('link',{name:'Saved project 0',exact:true})).toHaveCount(0);await expect(page.getByRole('link',{name:'Saved project 52',exact:true})).toBeVisible();await expect(more).toHaveCount(0);
});

test('authorization denial during parallel removals clears rows and ignores the other late acknowledgement',async({page})=>{
 await mount(page,'saved');await respond(page,saved,first(projects(0,2),true));await page.getByRole('button',{name:'Remove Saved project 0',exact:true}).click();await page.getByRole('button',{name:'Remove Saved project 1',exact:true}).click();
 await respond(page,'/api/v1/products/0/save',{error:{code:'FORBIDDEN',message:'Current account access required'}},403,'DELETE');await expect(page.getByRole('alert')).toContainText('Current account access required');await expect(page.locator('a[href^="/products/"]')).toHaveCount(0);await expect(page.getByRole('link',{name:'Log in',exact:true})).toBeVisible();
 await respond(page,'/api/v1/products/1/save',{},200,'DELETE');await expect(page.getByRole('button',{name:'Load more saved projects',exact:true})).toHaveCount(0);expect((await calls(page)).filter(call=>call.path===saved)).toHaveLength(1);
 await page.getByRole('button',{name:'Retry loading saved projects',exact:true}).click();await respond(page,saved,first(projects(10,1)));await expect(page.getByRole('link',{name:'Saved project 10',exact:true})).toBeVisible();await expect(page.getByRole('alert')).toHaveCount(0);
});

test('saved continuation authorization denial clears the collection and continuation',async({page})=>{
 await mount(page,'saved');await respond(page,saved,first(projects(0,1),true));await page.getByRole('button',{name:'Load more saved projects',exact:true}).click();await respond(page,continuation,{error:{code:'UNAUTHORIZED',message:'Sign in again'}},401);
 await expect(page.locator('a[href^="/products/"]')).toHaveCount(0);await expect(page.getByRole('alert')).toContainText('Sign in again');await expect(page.getByRole('button',{name:'Load more saved projects',exact:true})).toHaveCount(0);await expect(page.getByRole('button',{name:'Retry loading saved projects',exact:true})).toBeVisible();
});

test('version history resets errors and loading for slug, release and preview changes',async({page})=>{
 await mount(page,'history');await respond(page,'/api/v1/components/first/versions',failure('Earlier release unavailable'),503);await expect(page.getByRole('alert')).toHaveText('Earlier release unavailable');
 await page.getByRole('button',{name:'Change component',exact:true}).click();await expect(page.getByRole('status')).toHaveText('Loading releases…');await expect(page.getByRole('alert')).toHaveCount(0);await respond(page,'/api/v1/components/second/versions',{items:[{version:'1',sha256:'second-source'}]});await expect(page.getByText('second-source',{exact:true})).toBeVisible();
 await page.getByRole('button',{name:'Change release',exact:true}).click();await expect(page.getByRole('status')).toHaveText('Loading releases…');await expect(page.getByText('second-source',{exact:true})).toHaveCount(0);await respond(page,'/api/v1/components/second/versions',{items:[{version:'2',sha256:'new-source'}]});await expect(page.getByText('new-source',{exact:true})).toBeVisible();
 await page.getByRole('button',{name:'Use preview',exact:true}).click();await expect(page.getByText('hash-2',{exact:true})).toBeVisible();await expect(page.getByText('new-source',{exact:true})).toHaveCount(0);await expect(page.getByRole('alert')).toHaveCount(0);
});

test('version history ignores a late response for the previous component',async({page})=>{
 await mount(page,'history');await expect.poll(()=>calls(page).then(rows=>rows.length)).toBe(1);await page.getByRole('button',{name:'Change component',exact:true}).click();await respond(page,'/api/v1/components/second/versions',{items:[{version:'2',sha256:'current-source'}]});await respond(page,'/api/v1/components/first/versions',{items:[{version:'1',sha256:'stale-source'}]});
 await expect(page.getByText('current-source',{exact:true})).toBeVisible();await expect(page.getByText('stale-source',{exact:true})).toHaveCount(0);
});
