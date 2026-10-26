import {test,expect,type Page} from '@playwright/test';
import {build} from 'esbuild';
import {readFileSync} from 'node:fs';

type AuditState={holdSearch:boolean;releaseSearch?:()=>void;denySearch:boolean;denyWrite:boolean;failAfterWrite:boolean;failRefresh:boolean;failModeration:boolean;failQueue:boolean;denyBusinessPage:boolean;writes:{path:string;body:Record<string,unknown>}[]};
type AuditWindow=Window&{teamBusinessAudit:AuditState};

async function render(page:Page,kind:'team'|'business'='team'){
 const component=kind==='team'?'TeamWorkspace':'BusinessWorkspace',file=kind==='team'?'team-workspace':'business-workspace';
 const result=await build({stdin:{contents:`import {createRoot} from 'react-dom/client';import ${component} from './app/components/${file}';createRoot(document.getElementById('root')).render(<${component} demo/>);`,resolveDir:process.cwd(),loader:'tsx'},bundle:true,write:false,format:'iife',jsx:'automatic',loader:{'.css':'empty'},define:{'process.env.NODE_ENV':'"test"'},plugins:[{name:'audit-fixture',setup(plugin){
  plugin.onResolve({filter:/^next\/link$/},()=>({path:'link',namespace:'fixture'}));
  plugin.onLoad({filter:/.*/,namespace:'fixture'},()=>({contents:'export default function Link({children,...props}){return <a {...props}>{children}</a>}',loader:'jsx',resolveDir:process.cwd()}));
  plugin.onLoad({filter:/\/lib\/(team|business)-demo\.ts$/},args=>{
   const team=args.path.endsWith('/team-demo.ts'),factory=team?'createTeamDemo':'createBusinessDemo';
   const source=readFileSync(args.path,'utf8').replace('export function '+factory+'()','function createBaseDemo()').replace('followUpAt:future(3)',"followUpAt:'2020-01-02T03:04:57.123Z'").replace("assigneeId:'business',followUpAt:","assigneeId:'expired-assignee',followUpAt:");
   return {loader:'ts',contents:source+`
    export function ${factory}(){const demo=createBaseDemo(),original=demo.request;window.teamBusinessAudit={writes:[],holdSearch:false,denySearch:false,denyWrite:false,failAfterWrite:false,failRefresh:false,failModeration:false,failQueue:false,denyBusinessPage:false};
     let sourcingStatus='REQUESTED';const a=window.teamBusinessAudit;
     return {...demo,request:async(path,init)=>{
      const method=init?.method||'GET';
      if(method!=='GET'){a.writes.push({path,body:JSON.parse(init.body||'{}')});if(a.denyWrite)throw Object.assign(new Error('Current team access is required.'),{status:403});}
      if(path==='/me/teams'&&a.failRefresh){a.failRefresh=false;throw new Error('Team refresh temporarily unavailable.');}
      if(path.includes('/candidates?')){if(a.denySearch)throw Object.assign(new Error('Recruitment access was revoked.'),{status:403});const data={items:[{id:'held-builder',name:'Previous actor builder',headline:'Held search',skills:'React'}],page:0,size:1,hasMore:false};if(a.holdSearch)return new Promise(resolve=>a.releaseSearch=()=>resolve(data));return data;}
      if(path==='/admin/teams'&&a.failModeration){a.failModeration=false;throw new Error('Team review refresh unavailable.');}
      if(path.startsWith('/admin/concierge')){
       if(method!=='GET'){sourcingStatus=JSON.parse(init.body).status;if(a.failAfterWrite)a.failQueue=true;return {ok:true};}
       if(a.failQueue){a.failQueue=false;throw new Error('Queue refresh unavailable.');}
       return {items:[{id:'source-one',requestId:'brief-portal',businessName:'Audit business',title:'Private sourcing request',description:'Review eligible public proof for this customer portal.',category:'CRM',technology:'React',budget:'10k',timeline:'2 months',status:sourcingStatus}],history:[],itemsPage:{page:0,size:50,hasMore:false,totalItems:1,totalPages:1},historyPage:{page:0,size:50,hasMore:false,totalItems:0,totalPages:0}};
      }
      if(path.startsWith('/businesses/business-sample/requests')&&method==='GET'&&!path.split('?')[0].slice('/businesses/business-sample/requests'.length)){
       const url=new URL(path,'https://fixture.invalid');if(url.searchParams.has('page')&&a.denyBusinessPage)throw Object.assign(new Error('Business membership ended.'),{status:404});
       const result=await original(path,init);return {...result,items:result.items.slice(0,1),size:1,hasMore:true};
      }
      const result=await original(path,init);if(method!=='GET'&&a.failAfterWrite){if(path.startsWith('/admin/teams/'))a.failModeration=true;else a.failRefresh=true;}return result;
     }};
    }`};
  });
 }}]});
 await page.setContent('<div id="root"></div>');await page.addScriptTag({content:result.outputFiles[0].text});
 await expect(page.getByRole('heading',{name:kind==='team'?'Northstar Studio':'Customer approval portal',exact:true})).toBeVisible();
}
async function controls(page:Page){await page.locator('.studio-preview > summary').click();}
async function members(page:Page){await page.getByRole('tab',{name:'Members',exact:true}).click();await page.getByText('Find and invite a builder',{exact:true}).click();}
async function lead(page:Page){await page.getByRole('tab',{name:'Leads',exact:true}).click();await page.getByText('Customer portal for a design practice · New',{exact:true}).click();}

for(const refresh of ['actor','same workspace'] as const)test('initial builder search cannot repopulate results after '+refresh+' reload',async({page})=>{
 await render(page);await controls(page);await members(page);await page.evaluate(()=>(window as unknown as AuditWindow).teamBusinessAudit.holdSearch=true);
 await page.getByRole('button',{name:'Search builders',exact:true}).click();
 await expect.poll(()=>page.evaluate(()=>!!(window as unknown as AuditWindow).teamBusinessAudit.releaseSearch)).toBe(true);
 if(refresh==='actor'){await page.getByLabel('Preview actor',{exact:false}).selectOption('recruiter');await expect(page.getByText('Recruiter access',{exact:true})).toBeVisible();}
 else{await page.getByRole('button',{name:'Reset sample data',exact:true}).click();await expect(page.getByText('Loading team workspace…',{exact:true})).toHaveCount(0);}
 await page.evaluate(()=>(window as unknown as AuditWindow).teamBusinessAudit.releaseSearch!());await members(page);
 await expect(page.getByText('Previous actor builder · Held search',{exact:true})).toHaveCount(0);
});

test('unchanged overdue follow-up is omitted while status and private notes save',async({page})=>{
 await render(page);await lead(page);await page.getByLabel('Stage',{exact:true}).selectOption('INTERESTED');await page.getByLabel('Add a private note',{exact:true}).fill('Discuss the existing approval workflow.');
 await page.getByRole('button',{name:'Update lead',exact:true}).click();await expect(page.getByRole('status').filter({hasText:'Changes saved.'})).toBeVisible();
 const writes=await page.evaluate(()=>(window as unknown as AuditWindow).teamBusinessAudit.writes);expect(writes).toHaveLength(1);expect(writes[0].body).not.toHaveProperty('followUpAt');expect(writes[0].body).not.toHaveProperty('assigneeId');expect(writes[0].body.status).toBe('INTERESTED');
 await page.getByText('Customer portal for a design practice · Interested',{exact:true}).click();await expect(page.getByLabel('Follow-up (local time)',{exact:true})).toHaveValue('2020-01-02T03:04');await expect(page.getByText('Discuss the existing approval workflow.',{exact:true})).toBeVisible();
});

for(const denial of ['search','mutation'] as const)test('team '+denial+' access denial removes cached workspace and invitations',async({page})=>{
 await render(page);
 if(denial==='search'){await members(page);await page.evaluate(()=>(window as unknown as AuditWindow).teamBusinessAudit.denySearch=true);await page.getByRole('button',{name:'Search builders',exact:true}).click();}
 else{await lead(page);await page.evaluate(()=>(window as unknown as AuditWindow).teamBusinessAudit.denyWrite=true);await page.getByRole('button',{name:'Update lead',exact:true}).click();}
 await expect(page.getByRole('link',{name:'Log in',exact:true})).toBeVisible();await expect(page.getByRole('heading',{name:'Northstar Studio',exact:true})).toHaveCount(0);await expect(page.getByLabel('Your team',{exact:true})).toHaveCount(0);
});

test('accepted team update with failed refresh offers a read retry without false success or duplicate write',async({page})=>{
 await render(page);await lead(page);await page.evaluate(()=>(window as unknown as AuditWindow).teamBusinessAudit.failAfterWrite=true);await page.getByRole('button',{name:'Update lead',exact:true}).click();
 await expect(page.getByRole('alert')).toContainText('The change was saved, but your team workspace could not be refreshed.');await expect(page.getByText('Changes saved.',{exact:true})).toHaveCount(0);
 await page.getByRole('button',{name:'Retry your workspace',exact:true}).click();await expect(page.getByRole('heading',{name:'Northstar Studio',exact:true})).toBeVisible();expect(await page.evaluate(()=>(window as unknown as AuditWindow).teamBusinessAudit.writes.length)).toBe(1);
});

test('accepted moderation decision with failed list refresh never reports verified success',async({page})=>{
 await render(page);await controls(page);await page.getByLabel('Preview actor',{exact:false}).selectOption('admin');await page.getByText('Teams and studios',{exact:true}).click();await page.getByRole('button',{name:'Load teams',exact:true}).click();
 const form=page.locator('form').filter({has:page.getByRole('heading',{name:'Northstar Studio',exact:true})});await form.getByLabel('Reason',{exact:true}).fill('Pause recruitment while the team profile is reviewed.');await page.evaluate(()=>(window as unknown as AuditWindow).teamBusinessAudit.failAfterWrite=true);await form.getByRole('button',{name:'Record decision',exact:true}).click();
 await expect(page.getByRole('alert')).toContainText('The decision was recorded, but the team list could not be refreshed.');await expect(page.getByText('Team decision recorded.',{exact:true})).toHaveCount(0);await page.getByRole('button',{name:'Load teams',exact:true}).click();await expect(page.getByText('Current status: SUSPENDED',{exact:true})).toBeVisible();expect(await page.evaluate(()=>(window as unknown as AuditWindow).teamBusinessAudit.writes.length)).toBe(1);
});

test('business continuation denial clears private hiring records',async({page})=>{
 await render(page,'business');await page.evaluate(()=>(window as unknown as AuditWindow).teamBusinessAudit.denyBusinessPage=true);await page.getByRole('button',{name:'Load more project requests',exact:true}).click();
 await expect(page.getByRole('link',{name:'Log in',exact:true})).toBeVisible();await expect(page.getByRole('heading',{name:'Customer approval portal',exact:true})).toHaveCount(0);await expect(page.getByLabel('Business workspace',{exact:true})).toHaveCount(0);
});

test('accepted sourcing update with failed queue refresh offers read retry and clears stale actions',async({page})=>{
 await render(page,'business');await controls(page);await page.getByLabel('Preview actor',{exact:true}).selectOption('admin');await expect(page.getByRole('heading',{name:'Private sourcing request',exact:true})).toBeVisible();await page.evaluate(()=>(window as unknown as AuditWindow).teamBusinessAudit.failAfterWrite=true);
 await page.getByRole('button',{name:'Start sourcing',exact:true}).click();await expect(page.getByRole('alert')).toContainText('The sourcing update was saved, but the queue could not be refreshed.');await expect(page.getByText('Sourcing update saved.',{exact:true})).toHaveCount(0);await expect(page.getByRole('button',{name:'Start sourcing',exact:true})).toHaveCount(0);
 await page.getByRole('button',{name:'Refresh queue',exact:true}).click();await expect(page.getByRole('button',{name:'Mark fulfilled',exact:true})).toBeVisible();expect(await page.evaluate(()=>(window as unknown as AuditWindow).teamBusinessAudit.writes.length)).toBe(1);
});
