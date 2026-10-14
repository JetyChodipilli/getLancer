import {test,expect,type Page} from '@playwright/test';
import {readFileSync} from 'node:fs';
import {createRequire} from 'node:module';
import {createHash} from 'node:crypto';
import {operationsSnapshot,type OperationsSnapshot} from '../../lib/operations';

const axe=readFileSync(createRequire(import.meta.url).resolve('axe-core/axe.min.js'),'utf8');
type AxeResult={violations:{id:string;impact:string;nodes:{target:string[]}[]}[]};
type EducationSourceEntry={productSlug:string;release:{snapshot:{sourceBinding:{sha256:string}}}};
function snapshot(now=Date.now()):OperationsSnapshot{
 const to=new Date(now).toISOString(),calendarStart=new Date(now);calendarStart.setUTCHours(0,0,0,0);calendarStart.setUTCDate(calendarStart.getUTCDate()-29);const from=calendarStart.toISOString();
 return {window:{from,to,days:30,privacy:'Consented aggregates only; no input, token or inquiry text.',observationStartedAt:from},funnels:[{stage:'component_view',mode:'BROWSER_LOCAL',version:'reviewed-source-1',observations:2,denominator:4,ratePercent:50,denominatorLabel:'Consented events in this mode and immutable source version.'},{stage:'purchase_captured',mode:'TEST',version:'source-2',observations:0,denominator:0,ratePercent:null,denominatorLabel:'Server-created receipts in TEST mode; no real entitlement.'}],runtime:{admitted:10,completed:8,healthy:7,pending:2,infraFailures:1,infraSuccessPercent:87.5,coveragePercent:80,scenarioFailures:1,quotaDenials:4,validationDenials:3,counterEpoch:from,operatorEpoch:'11111111-1111-4111-8111-111111111111',measuredFrom:from},alerts:[{code:'STALE_WORKER',severity:'WARNING',count:1,detail:'A worker heartbeat exceeded its declared freshness threshold.'}],readiness:{runtime:{enabled:false,configurationReady:false,reason:'No current isolated production provider certification.'},commerce:{enabled:false,configurationReady:true,mode:'TEST',reason:'Merchant LIVE verification remains unmet; TEST mode grants no entitlement.'},productionCertified:false},resources:{active:2,memoryMiB:512,dailyReservedMicros:5000000,monthlyReservedMicros:10000000,dailyBudgetMicros:50000000,monthlyBudgetMicros:1000000000}};
}
async function access(page:Page){let failure=0,invalid:unknown=null;const mutations:string[]=[];
 await page.route('**/api/v1/**',async route=>{if(route.request().method()!=='GET')mutations.push(route.request().url());if(new URL(route.request().url()).pathname==='/api/v1/admin/operations')return route.fulfill(failure?{status:failure,json:{error:{code:'UNAVAILABLE',message:failure===403?'Recent administrator MFA has expired.':'The configured backend is unavailable.'}}}:{json:invalid||snapshot()});return route.fulfill({status:401,json:{error:{message:'No other authority.'}}});});
 await page.goto('/workspace/operations');await expect(page.getByRole('heading',{name:'Admitted runtime provisioning',exact:true})).toBeVisible();
 return {deny:()=>{failure=403;},outage:()=>{failure=503;},malformed:(value:unknown)=>{invalid=value;},mutations};
}
async function reflow(page:Page){expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1)).toBe(true);}
async function scan(page:Page){await page.evaluate(axe);const violations=await page.evaluate(async()=> (window as unknown as {axe:{run:(element:Element|null,options:unknown)=>Promise<AxeResult>}}).axe.run(document.querySelector('main'),{runOnly:{type:'tag',values:['wcag2a','wcag2aa','wcag21aa','wcag22aa']}}));expect(violations.violations.filter(row=>['serious','critical'].includes(row.impact)).map(row=>({id:row.id,targets:row.nodes.map(node=>node.target)}))).toEqual([]);}

test('operational schema rejects stale, missing, negative and invented denominator claims',()=>{
 const now=Date.now(),value=snapshot(now);expect(operationsSnapshot(value,now).runtime.pending).toBe(2);
 for(const change of [(v:OperationsSnapshot)=>{v.window.to=new Date(now-6*60000).toISOString();},(v:OperationsSnapshot)=>{v.window.to=new Date(now+2*60000).toISOString();},(v:OperationsSnapshot)=>{v.runtime.healthy=9;},(v:OperationsSnapshot)=>{v.runtime.infraFailures=0;},(v:OperationsSnapshot)=>{v.runtime.operatorEpoch='not-an-epoch';},(v:OperationsSnapshot)=>{v.window.observationStartedAt='2026-02-30T00:00:00Z';},(v:OperationsSnapshot)=>{v.runtime.infraSuccessPercent=99;},(v:OperationsSnapshot)=>{v.runtime.admitted=-1;},(v:OperationsSnapshot)=>{v.funnels[1].ratePercent=0;},(v:OperationsSnapshot)=>{v.funnels[0].observations=5;},(v:OperationsSnapshot)=>{v.funnels.push(v.funnels[0]);},(v:OperationsSnapshot)=>{v.alerts[0].severity='UNKNOWN' as 'WARNING';}]){const changed=structuredClone(value);change(changed);expect(()=>operationsSnapshot(changed,now)).toThrow(/incomplete, inconsistent or stale/);}
 expect(()=>operationsSnapshot({window:value.window},now)).toThrow();expect(()=>operationsSnapshot({...value,readiness:{...value.readiness,productionCertified:true}},now)).toThrow();
 const busy=structuredClone(value);busy.funnels=Array.from({length:1601},(_,index)=>({...value.funnels[0],version:'revision-'+index}));expect(operationsSnapshot(busy,now).funnels).toHaveLength(1601);
 const wrongWindow=structuredClone(value);wrongWindow.window.from=new Date(now-86400000).toISOString();expect(()=>operationsSnapshot(wrongWindow,now)).toThrow();
 const inconsistent=structuredClone(value);inconsistent.readiness.runtime.enabled=true;expect(()=>operationsSnapshot(inconsistent,now)).toThrow();
});

for(const width of [320,390,768,1024,1440])test('operator evidence stays readable and keyboard usable at '+width+'px',async({page},info)=>{
 await page.setViewportSize({width,height:1000});const fixture=await access(page);
 await expect(page.getByRole('heading',{name:'Independent activation gates',exact:true})).toBeVisible();await expect(page.locator('.operations-lanes article').first()).toContainText('No current isolated production provider certification.');await expect(page.locator('.operations-lanes article').last()).toContainText('Merchant LIVE verification remains unmet');
 await expect(page.locator('.operations-runtime').first()).toContainText('87.50%');await expect(page.locator('.operations-runtime').first()).toContainText('80.00%');await expect(page.getByText('No eligible observations',{exact:true})).toBeVisible();
 const filter=page.getByLabel('Observation mode',{exact:true});await filter.focus();await expect(filter).toBeFocused();expect(Number(await filter.evaluate(element=>getComputedStyle(element).fontSize.replace('px','')))).toBeGreaterThanOrEqual(16);expect((await filter.boundingBox())!.height).toBeGreaterThanOrEqual(44);
 await filter.selectOption('TEST');await expect(page.locator('.operations-funnels article')).toHaveCount(1);await expect(page.locator('.operations-funnels article')).toContainText('test · Version source-2');await reflow(page);await scan(page);
 expect((await page.getByRole('button',{name:'Refresh operations',exact:true}).boundingBox())!.height).toBeGreaterThanOrEqual(44);expect(fixture.mutations).toEqual([]);
 if(info.project.name==='desktop')await info.attach('v52-operations-'+width,{body:await page.screenshot({fullPage:true,animations:'disabled'}),contentType:'image/png'});
});

test('revoked MFA and configured backend outage clear every previous operator count and focus the error',async({page})=>{
 const fixture=await access(page);fixture.deny();await page.getByRole('button',{name:'Refresh operations',exact:true}).click();await expect(page.getByRole('alert')).toBeFocused();await expect(page.getByRole('alert')).toContainText('Recent administrator MFA has expired.');await expect(page.getByRole('heading',{name:'Admitted runtime provisioning',exact:true})).toHaveCount(0);await expect(page.locator('.operations-funnels')).toHaveCount(0);await expect(page.getByText('87.50%',{exact:true})).toHaveCount(0);
 fixture.outage();await page.getByRole('button',{name:'Retry operator connection',exact:true}).click();await expect(page.getByRole('alert')).toBeFocused();await expect(page.getByRole('alert')).toContainText('configured backend is unavailable');await expect(page.getByText('Sample interface · No real release evidence',{exact:true})).toHaveCount(0);await expect(page.locator('.operations-runtime')).toHaveCount(0);expect(fixture.mutations).toEqual([]);await reflow(page);await scan(page);
});

test('malformed current response clears previous records without showing sample or false success',async({page})=>{
 const fixture=await access(page),invalid=snapshot();invalid.runtime.coveragePercent=99;fixture.malformed(invalid);await page.getByRole('button',{name:'Refresh operations',exact:true}).click();await expect(page.getByRole('alert')).toBeFocused();await expect(page.getByRole('alert')).toContainText('incomplete, inconsistent or stale');await expect(page.getByRole('heading',{name:'Observed marketplace activity',exact:true})).toHaveCount(0);await expect(page.getByText('Sample interface · No real release evidence',{exact:true})).toHaveCount(0);
});

test('busy cohort windows stay bounded, reach later versions and reset paging on mode changes',async({page})=>{
 const fixture=await access(page),busy=snapshot();busy.funnels=Array.from({length:25},(_,index)=>({...busy.funnels[0],version:'cohort-'+index}));fixture.malformed(busy);await page.getByRole('button',{name:'Refresh operations',exact:true}).click();await expect(page.locator('.operations-funnels article')).toHaveCount(12);await page.getByRole('button',{name:'Next observation cohorts',exact:true}).click();await expect(page.getByText('browser local · Version cohort-12',{exact:true})).toBeVisible();await page.getByRole('button',{name:'Next observation cohorts',exact:true}).click();await expect(page.locator('.operations-funnels article')).toHaveCount(1);await expect(page.getByRole('button',{name:'Next observation cohorts',exact:true})).toBeDisabled();await page.getByLabel('Observation mode',{exact:true}).selectOption('BROWSER_LOCAL');await expect(page.getByText('browser local · Version cohort-0',{exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'Previous observation cohorts',exact:true})).toBeDisabled();await reflow(page);
});

test('evidence expires after five minutes and asks for renewed authority instead of retaining stale counts',async({page})=>{
 await page.clock.install();await access(page);await page.clock.fastForward(5*60*1000+1000);await expect(page.getByRole('alert')).toBeFocused();await expect(page.getByRole('alert')).toContainText('snapshot has expired');await expect(page.locator('.operations-runtime')).toHaveCount(0);await expect(page.locator('.operations-funnels')).toHaveCount(0);
});

test('sample readiness persists without real metrics, requests or enabled mutations, including reduced motion',async({page})=>{
 const operationsRequests:string[]=[];page.on('request',request=>{if(request.url().includes('/api/v1/admin/operations')||request.method()==='POST')operationsRequests.push(request.url());});await page.emulateMedia({reducedMotion:'reduce'});await page.goto('/preview/operations');await expect(page.getByText('Sample interface · No real release evidence',{exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'Live operator controls unavailable in sample',exact:true})).toBeDisabled();await expect(page.getByText(/Real evidence records: 0/)).toBeVisible();await expect(page.getByRole('heading',{name:'Admitted runtime provisioning',exact:true})).toHaveCount(0);
 const panel=page.locator('.operations-ui .panel').first();expect(await panel.evaluate(element=>getComputedStyle(element).animationName)).toBe('none');await reflow(page);await scan(page);expect(operationsRequests).toEqual([]);
});

test('original study planner source downloads preserve hash and avoid fabricated builder or showcase links',async({page})=>{
 const entries=JSON.parse(readFileSync('backend/src/main/resources/catalog/education.json','utf8')) as EducationSourceEntry[],entry=entries.find(row=>row.productSlug==='study-planner');expect(entry).toBeTruthy();await page.goto('/college-projects/study-planner');await expect(page.getByRole('heading',{level:1})).toContainText(/study/i);await expect(page.locator('a[href="/builders/original-learning-samples"]')).toHaveCount(0);await expect(page.locator('a[href="/products/study-planner"]')).toHaveCount(0);await expect(page.getByText(/Original getLancer source, no individual builder profile implied/)).toBeVisible();
 const downloadWait=page.waitForEvent('download');await page.getByRole('link',{name:'Download original sample source',exact:true}).click();const downloaded=await downloadWait,bytes=readFileSync((await downloaded.path())!);expect(createHash('sha256').update(bytes).digest('hex')).toBe(entry!.release.snapshot.sourceBinding.sha256);await reflow(page);await scan(page);
});

test('consent does not turn sample component interactions into real observations',async({page})=>{
 const events:unknown[]=[];await page.addInitScript(()=>localStorage.setItem('getlancer.analytics','yes'));await page.route('**/api/v1/analytics/events',route=>{events.push(route.request().postDataJSON());return route.fulfill({json:{recorded:false}});});await page.goto('/components');await page.getByRole('link',{name:/Try /}).first().click();await page.getByRole('tab',{name:'Source',exact:true}).click();await expect(page.getByRole('tab',{name:'Source',exact:true})).toHaveAttribute('aria-selected','true');expect(events).toEqual([]);
});
