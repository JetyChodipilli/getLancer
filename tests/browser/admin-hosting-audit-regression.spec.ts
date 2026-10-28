import {test,expect,type Page,type Route} from '@playwright/test';
import {build} from 'esbuild';

type Surface='moderation'|'privacy'|'triage'|'admin';
async function fixture(page:Page,surface:Surface){
 const appeals=Array.from({length:3},(_,n)=>({id:'appeal-'+n,status:'OPEN',statement:'Retained appeal '+n,original_action:'suspend',original_reason:'Independent review required.'}));
 const privacy=Array.from({length:3},(_,n)=>({id:'privacy-'+n,status:'REQUESTED'}));
 const reports=Array.from({length:3},(_,n)=>({id:'report-'+n,severity:'HIGH',status:'OPEN',reason:'REPORTED '+n,detail:'Private reported evidence '+n,target_type:'COMPONENT'}));
 const calls:string[]=[],writes:unknown[]=[],failures=new Map<string,number>();let mutationStatus=0,holdPath='',held:Route|undefined;
 const collection=(path:string)=>path==='/admin/appeals'?appeals:path==='/admin/deletion-requests'?privacy:path==='/admin/reports'?reports:path==='/admin/email-jobs'?[{id:'mail-0',subject:'Failed mail',attempts:8,created_at:'2026-01-01',expired:false}]:[];
 const result=(path:string,number:number)=>{const rows=collection(path);return {items:rows.slice(number*2,(number+1)*2),page:number,size:2,hasMore:(number+1)*2<rows.length,totalItems:rows.length,totalPages:Math.ceil(rows.length/2)};};
 await page.route('**/api/v1/**',async route=>{
  const url=new URL(route.request().url()),path=url.pathname.replace('/api/v1',''),number=Number(url.searchParams.get('page')||0);
  if(route.request().method()==='POST'){writes.push(route.request().postDataJSON());if(mutationStatus)return route.fulfill({status:mutationStatus,json:{error:{code:'UNAVAILABLE',message:'The decision was not saved. Retry after recovery.'}}});return route.fulfill({json:{ok:true}});}
  calls.push(path+':'+number);
  const status=failures.get(path+':'+number);if(status){failures.delete(path+':'+number);return route.fulfill({status,json:{error:{code:'UNAVAILABLE',message:status===403?'Current administrator MFA is required.':'Temporary records failure. Please retry.'}}});}
  if(holdPath===path){held=route;return;}
  if(path==='/admin/categories'||path==='/admin/technologies')return route.fulfill({json:{items:[{slug:path.endsWith('categories')?'business':'typescript',name:path.endsWith('categories')?'Business':'TypeScript',active:true}]}});
  if(path==='/admin/metrics')return route.fulfill({json:{weekly:[],sources:[],reportedValues:[]}});
  return route.fulfill({json:result(path,number)});
 });
 const name={moderation:'ModerationWorkflow',privacy:'PrivacyOperations',triage:'ReportTriage',admin:'AdminTools'}[surface],file={moderation:'moderation-workflow',privacy:'privacy-operations',triage:'report-triage',admin:'admin-tools'}[surface];
 const built=await build({stdin:{contents:`import {createRoot} from 'react-dom/client';import ${name} from './app/components/${file}';createRoot(document.getElementById('root')).render(<${name} ${surface==='moderation'?'admin':''}/>);`,resolveDir:process.cwd(),loader:'tsx'},bundle:true,write:false,platform:'browser',format:'iife',jsx:'automatic',loader:{'.css':'empty'},define:{'process.env.NODE_ENV':'"test"'},plugins:[{name:'fixture-link',setup(plugin){plugin.onResolve({filter:/^next\/link$/},()=>({path:'link',namespace:'fixture'}));plugin.onLoad({filter:/.*/,namespace:'fixture'},()=>({contents:'export default function Link({href,children,...props}){return <a href={href} {...props}>{children}</a>}',loader:'jsx',resolveDir:process.cwd()}));}}]});
 await page.route('**/admin-audit-fixture',route=>route.fulfill({contentType:'text/html',body:'<!doctype html><meta name="viewport" content="width=device-width, initial-scale=1"><style>input,textarea,select{max-width:100%;box-sizing:border-box}form{margin:12px 0}label{display:block}section,details{overflow-wrap:anywhere}</style><div id="root"></div>'}));
 return {calls,writes,failures,failMutation:(status:number)=>{mutationStatus=status;},hold:(path:string)=>{holdPath=path;},held:()=>!!held,release:async()=>{const route=held;held=undefined;holdPath='';if(route){const url=new URL(route.request().url());await route.fulfill({json:result(url.pathname.replace('/api/v1',''),Number(url.searchParams.get('page')||0))});}},mount:async()=>{await page.goto('/admin-audit-fixture');await page.addScriptTag({content:built.outputFiles[0].text});}};
}

test('moderation first-load failure is retryable and a saved action with a failed refresh never announces success',async({page})=>{
 const f=await fixture(page,'moderation');f.failures.set('/admin/appeals:0',503);await f.mount();
 await expect(page.getByRole('alert')).toContainText('Temporary records failure');await expect(page.getByRole('button',{name:'Load more appeals',exact:true})).toHaveCount(0);
 await page.getByRole('button',{name:'Refresh decisions and appeals',exact:true}).click();await expect(page.getByText('Retained appeal 0',{exact:true})).toBeVisible();
 const form=page.locator('article').first().locator('form');await form.getByLabel('Review reason',{exact:true}).fill('Reviewed exact independent evidence.');
 f.failures.set('/admin/appeals:0',503);await form.getByRole('button',{name:'Record appeal decision',exact:true}).click();
 await expect(page.getByRole('alert')).toContainText('may have been recorded');await expect(page.getByText('Your changes were recorded.',{exact:true})).toHaveCount(0);await expect(page.getByText('Retained appeal 0',{exact:true})).toHaveCount(0);
 await page.getByRole('button',{name:'Refresh decisions and appeals',exact:true}).click();await expect(page.getByText('Retained appeal 0',{exact:true})).toBeVisible();expect(f.writes).toHaveLength(1);
});

test('moderation validation/service errors retain inputs and cursor; successful refresh resets continuation to page one',async({page})=>{
 const f=await fixture(page,'moderation');await f.mount();await page.getByRole('button',{name:'Load more appeals',exact:true}).click();await expect(page.getByText('Retained appeal 2',{exact:true})).toBeVisible();
 const form=page.locator('article').last().locator('form'),reason='Keep the independently reviewed appeal reason.';await form.getByLabel('Review reason',{exact:true}).fill(reason);await form.getByLabel('Outcome',{exact:true}).selectOption('UPHELD');
 for(const status of [400,409,503]){f.failMutation(status);await form.getByRole('button',{name:'Record appeal decision',exact:true}).click();await expect(page.getByRole('alert')).toContainText('not saved');await expect(form.getByLabel('Review reason',{exact:true})).toHaveValue(reason);await expect(form.getByLabel('Outcome',{exact:true})).toHaveValue('UPHELD');await expect(page.getByRole('button',{name:'Load more appeals',exact:true})).toHaveCount(0);}
 f.failMutation(0);await form.getByRole('button',{name:'Record appeal decision',exact:true}).click();await expect(page.getByText('Your changes were recorded.',{exact:true})).toBeVisible();await expect(page.locator('article')).toHaveCount(2);await page.getByRole('button',{name:'Load more appeals',exact:true}).click();await expect(page.getByText('Retained appeal 2',{exact:true})).toBeVisible();expect(f.calls.filter(path=>path.startsWith('/admin/appeals'))).toEqual(['/admin/appeals:0','/admin/appeals:1','/admin/appeals:0','/admin/appeals:1']);
});

test('moderation authority denial clears appeals and failed-mail recovery controls together',async({page})=>{
 const f=await fixture(page,'moderation');await f.mount();await expect(page.getByText(/Failed mail · 8 attempts/)).toBeVisible();f.failures.set('/admin/appeals:1',403);await page.getByRole('button',{name:'Load more appeals',exact:true}).click();await expect(page.getByRole('alert').first()).toContainText('MFA');await expect(page.locator('article')).toHaveCount(0);await expect(page.getByRole('button',{name:'Retry delivery',exact:true})).toHaveCount(0);
});

test('privacy failed first page has a retry, successful decisions reset pagination, and authority denial clears private rows',async({page})=>{
 const f=await fixture(page,'privacy');f.failures.set('/admin/deletion-requests:0',503);await f.mount();await page.getByText('Account deletion review',{exact:true}).click();await expect(page.getByRole('alert')).toContainText('Temporary');await expect(page.getByRole('button',{name:'Load more privacy requests',exact:true})).toHaveCount(0);
 await page.getByRole('button',{name:'Refresh privacy decisions',exact:true}).click();await page.getByRole('button',{name:'Load more privacy requests',exact:true}).click();await expect(page.locator('form')).toHaveCount(3);
 const form=page.locator('form').last();await form.getByLabel('Policy reference and reason',{exact:true}).fill('Documented policy reference and independent review.');await form.getByRole('button',{name:'Record privacy decision',exact:true}).click();await expect(page.locator('form')).toHaveCount(2);await page.getByRole('button',{name:'Load more privacy requests',exact:true}).click();await expect(page.locator('form')).toHaveCount(3);
 expect(f.calls.filter(path=>path.startsWith('/admin/deletion-requests'))).toEqual(['/admin/deletion-requests:0','/admin/deletion-requests:0','/admin/deletion-requests:1','/admin/deletion-requests:0','/admin/deletion-requests:1']);
 f.failures.set('/admin/deletion-requests:0',403);await page.getByRole('button',{name:'Refresh privacy decisions',exact:true}).click();await expect(page.locator('form')).toHaveCount(0);await expect(page.getByRole('alert')).toContainText('MFA');
});

test('older pending privacy continuation cannot repopulate a refreshed collection',async({page})=>{
 const f=await fixture(page,'privacy');await f.mount();await page.getByText('Account deletion review',{exact:true}).click();await expect(page.locator('form')).toHaveCount(2);f.hold('/admin/deletion-requests');await page.getByRole('button',{name:'Load more privacy requests',exact:true}).click();await expect.poll(f.held).toBe(true);
 // Release a newer page-zero request before releasing the old continuation.
 f.hold('');await page.getByRole('button',{name:'Refresh privacy decisions',exact:true}).click();await expect(page.getByRole('button',{name:'Refresh privacy decisions',exact:true})).toBeEnabled();await expect(page.locator('form')).toHaveCount(2);await f.release();await expect(page.locator('form')).toHaveCount(2);
});

test('report first-page retry and continuation failure retain the same page and private evidence',async({page})=>{
 const f=await fixture(page,'triage');f.failures.set('/admin/reports:0',503);await f.mount();await page.getByText('Report priority and triage',{exact:true}).click();await expect(page.getByRole('alert')).toContainText('Temporary');await page.getByRole('button',{name:'Refresh report triage',exact:true}).click();await expect(page.getByText('Private reported evidence 0',{exact:true})).toBeVisible();
 f.failures.set('/admin/reports:1',503);await page.getByRole('button',{name:'Load more reports',exact:true}).click();await expect(page.getByRole('alert')).toContainText('Temporary');await expect(page.getByText('Private reported evidence 0',{exact:true})).toBeVisible();await page.getByRole('button',{name:'Load more reports',exact:true}).click();await expect(page.getByText('Private reported evidence 2',{exact:true})).toBeVisible();expect(f.calls.filter(path=>path.startsWith('/admin/reports'))).toEqual(['/admin/reports:0','/admin/reports:0','/admin/reports:1','/admin/reports:1']);
});

test('taxonomy save owns its refresh, locks selection, and exposes refresh errors without unhandled promises or false success',async({page})=>{
 const f=await fixture(page,'admin'),errors:string[]=[];page.on('pageerror',error=>errors.push(error.message));await f.mount();await page.getByText('Categories and technologies',{exact:true}).click();const field=page.getByRole('textbox',{name:'Name for business',exact:true});await expect(field).toHaveValue('Business');await field.fill('Reviewed business');f.failures.set('/admin/categories:0',503);await page.getByRole('button',{name:'Save',exact:true}).click();await expect(page.getByRole('alert').first()).toContainText('may have been saved');await expect(field).toHaveValue('Reviewed business');await expect(page.getByText('Saved and current records refreshed.',{exact:true})).toHaveCount(0);expect(errors).toEqual([]);
 f.hold('/admin/audit');await page.getByRole('button',{name:'Save',exact:true}).click();await expect.poll(f.held).toBe(true);await expect(page.getByLabel('Manage',{exact:true})).toBeDisabled();await expect(field).toBeDisabled();await f.release();await expect(page.getByText('Saved and current records refreshed.',{exact:true})).toBeVisible();await page.getByLabel('Manage',{exact:true}).selectOption('technologies');await expect(page.getByRole('textbox',{name:'Name for typescript',exact:true})).toHaveValue('TypeScript');
});
