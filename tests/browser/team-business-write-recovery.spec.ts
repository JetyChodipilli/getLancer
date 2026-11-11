import {test,expect,type Locator,type Page} from '@playwright/test';
import {build} from 'esbuild';

type Kind='team'|'business';
type RecoveryState={mode:'success'|'lost'|'invalid'|'refresh-failure';writes:number;saved:number;holdRead:boolean;failNextRead?:boolean;releaseRead?:()=>void};
type RecoveryWindow=Window&{writeRecovery:RecoveryState};

async function mount(page:Page,kind:Kind){
 const component=`./app/components/${kind}-workspace`,factory=kind==='team'?'createTeamDemo':'createBusinessDemo';
 const script=(await build({stdin:{contents:`
  import {createRoot} from 'react-dom/client';import Workspace from '${component}';import {${factory}} from './lib/${kind}-demo';
  const demo=${factory}(),state=window.writeRecovery={mode:'success',writes:0,saved:0,holdRead:false};
  window.fetch=async(path,init={})=>{
   const route=String(path).startsWith('/api/v1')?String(path).slice(7):String(path),write=(init.method||'GET')!=='GET';
   const respond=(data,status=200)=>new Response(JSON.stringify(data),{status,headers:{'Content-Type':'application/json'}});
   if(write){state.writes++;if(state.mode==='invalid')return respond({error:{code:'VALIDATION_FAILED',message:'Correct the workspace name before trying again.'}},422);}
   if(!write&&state.failNextRead&&route==='${kind==='team'?'/me/teams':'/businesses'}'){state.failNextRead=false;return respond({error:{code:'UNAVAILABLE',message:'Synthetic workspace refresh failed.'}},503);}
   let result;try{result=await demo.request(route,init)}catch(problem){return respond({error:{code:'FIXTURE_ERROR',message:problem.message}},problem.status||400)}
   if(write){state.saved++;if(state.mode==='lost')throw new TypeError('Synthetic connection lost after commit.');if(state.mode==='refresh-failure'){state.mode='success';state.failNextRead=true;}}
   // Deliberately ignore cancellation: a pre-write read must not release the recovery fence.
   if(!write&&route==='${kind==='team'?'/me/teams':'/businesses'}'&&state.holdRead){state.holdRead=false;await new Promise(resolve=>state.releaseRead=resolve);}
   return respond(result);
  };
  createRoot(document.getElementById('root')).render(<Workspace/>);
 `,resolveDir:process.cwd(),loader:'tsx'},bundle:true,write:false,format:'iife',jsx:'automatic',loader:{'.css':'empty'},define:{'process.env.NODE_ENV':'"test"'},plugins:[{name:'write-recovery-link',setup(plugin){
  plugin.onResolve({filter:/^next\/link$/},()=>({path:'link',namespace:'fixture'}));
  plugin.onLoad({filter:/.*/,namespace:'fixture'},()=>({contents:'export default function Link({children,prefetch,...props}){return <a {...props}>{children}</a>}',loader:'jsx',resolveDir:process.cwd()}));
 }}]})).outputFiles[0].text;
 await page.setContent('<div id="root"></div>');await page.addScriptTag({content:script});
 await expect(page.getByRole('heading',{name:kind==='team'?'Northstar Studio':'Customer approval portal',exact:true})).toBeVisible();
}
async function creation(page:Page,kind:Kind,name='Recovery workspace'){
 if(kind==='team'){
  const section=page.locator('.team-create');if(await section.getAttribute('open')===null)await section.locator('summary').click();
  const form=section.locator('form');await fill(form,kind,name);return form;
 }
 await page.getByRole('button',{name:'Create business',exact:true}).click();const form=page.getByRole('dialog').locator('form');await fill(form,kind,name);return form;
}
async function fill(form:Locator,kind:Kind,name:string){
 await form.getByLabel(kind==='team'?'Team name':'Business name',{exact:true}).fill(name);
 await form.getByLabel(kind==='team'?'What does your team build?':'What does your business do?',{exact:true}).fill('An independent group building accessible customer operations software.');
 if(kind==='team')await form.getByLabel('Typical project budget range',{exact:true}).fill('10000 to 20000 INR');
}
async function reconcile(page:Page,kind:Kind){
 await page.evaluate(()=>(window as unknown as RecoveryWindow).writeRecovery.mode='success');
 if(kind==='business'){await page.keyboard.press('Escape');await page.getByRole('button',{name:'Reload business workspace',exact:true}).click();}
 else await page.getByRole('button',{name:'Retry your workspace',exact:true}).click();
 await expect(page.getByRole('status').filter({hasText:kind==='team'?'Loading team workspace…':'Loading business workspace…'})).toHaveCount(0);
 await expect(page.getByLabel(kind==='team'?'Your team':'Business workspace',{exact:true})).toContainText('Recovery workspace');
}

for(const kind of ['team','business'] as const){
 test(`${kind} lost committed create response requires an authoritative read before another write`,async({page})=>{
  await mount(page,kind);const form=await creation(page,kind),submit=form.getByRole('button',{name:kind==='team'?'Create team':'Create workspace',exact:true});
  await page.evaluate(()=>(window as unknown as RecoveryWindow).writeRecovery.mode='lost');await submit.click();
  await expect(page.getByRole('alert').filter({hasText:'could not confirm this change'}).first()).toBeVisible();await expect(submit).toBeDisabled();
  await form.evaluate(element=>element.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true})));
  expect(await page.evaluate(()=>{const {writes,saved}=(window as unknown as RecoveryWindow).writeRecovery;return {writes,saved}})).toEqual({writes:1,saved:1});
  await reconcile(page,kind);
  expect(await page.evaluate(()=>(window as unknown as RecoveryWindow).writeRecovery.writes)).toBe(1);
  const next=kind==='team'?form:await creation(page,kind,'Second recovery workspace');if(kind==='team')await fill(next,kind,'Second recovery workspace');
  await expect(next.getByRole('button',{name:kind==='team'?'Create team':'Create workspace',exact:true})).toBeEnabled();
  await next.getByRole('button',{name:kind==='team'?'Create team':'Create workspace',exact:true}).click();
  await expect.poll(()=>page.evaluate(()=>(window as unknown as RecoveryWindow).writeRecovery.saved)).toBe(2);
 });

 test(`${kind} explicit validation rejection permits a corrected retry`,async({page})=>{
  await mount(page,kind);const form=await creation(page,kind),submit=form.getByRole('button',{name:kind==='team'?'Create team':'Create workspace',exact:true});
  await page.evaluate(()=>(window as unknown as RecoveryWindow).writeRecovery.mode='invalid');await submit.click();
  await expect(form.getByRole('alert')).toContainText('Correct the workspace name');await expect(submit).toBeEnabled();
  expect(await page.evaluate(()=>(window as unknown as RecoveryWindow).writeRecovery.saved)).toBe(0);
  await page.evaluate(()=>(window as unknown as RecoveryWindow).writeRecovery.mode='success');await fill(form,kind,'Corrected recovery workspace');await submit.click();
  await expect.poll(()=>page.evaluate(()=>{const {writes,saved}=(window as unknown as RecoveryWindow).writeRecovery;return {writes,saved}})).toEqual({writes:2,saved:1});
 });

 test(`${kind} pre-write read cannot release the fence after an ambiguous create`,async({page})=>{
  await mount(page,kind);const form=await creation(page,kind),submit=form.getByRole('button',{name:kind==='team'?'Create team':'Create workspace',exact:true});
  await page.evaluate(()=>(window as unknown as RecoveryWindow).writeRecovery.holdRead=true);
  // Start the normal workspace-switch read while the existing form is still mounted.
  await page.getByLabel(kind==='team'?'Your team':'Business workspace',{exact:true}).evaluate(element=>element.dispatchEvent(new Event('change',{bubbles:true})));
  await expect.poll(()=>page.evaluate(()=>!!(window as unknown as RecoveryWindow).writeRecovery.releaseRead)).toBe(true);
  await page.evaluate(()=>(window as unknown as RecoveryWindow).writeRecovery.mode='lost');await submit.click();
  await expect(page.getByRole('alert').filter({hasText:'could not confirm this change'}).first()).toBeVisible();await expect(submit).toBeDisabled();await page.evaluate(()=>(window as unknown as RecoveryWindow).writeRecovery.releaseRead!());
  await page.evaluate(()=>new Promise<void>(done=>requestAnimationFrame(()=>requestAnimationFrame(()=>done()))));
  await expect(page.getByRole('status').filter({hasText:kind==='team'?'Loading team workspace…':'Loading business workspace…'})).toHaveCount(0);
  await expect(submit).toBeDisabled();expect(await page.evaluate(()=>(window as unknown as RecoveryWindow).writeRecovery.writes)).toBe(1);
  await reconcile(page,kind);
 });
 test(`${kind} saved create with failed refresh stays fenced until a read succeeds`,async({page})=>{
  await mount(page,kind);const form=await creation(page,kind);
  await page.evaluate(()=>(window as unknown as RecoveryWindow).writeRecovery.mode='refresh-failure');
  await form.getByRole('button',{name:kind==='team'?'Create team':'Create workspace',exact:true}).click();
  await expect.poll(()=>page.evaluate(()=>{const {writes,saved}=(window as unknown as RecoveryWindow).writeRecovery;return {writes,saved}})).toEqual({writes:1,saved:1});
  await expect(page.getByRole('alert').filter({hasText:kind==='team'?'The change was saved':'Synthetic workspace refresh failed'}).first()).toBeVisible();
  if(kind==='team')await expect(form.getByRole('button',{name:'Create team',exact:true})).toBeDisabled();
  else await expect(page.getByRole('button',{name:'Create business',exact:true})).toHaveCount(0);
  await expect(page.getByRole('status').filter({hasText:kind==='team'?'Sample preview: Team created.':'Business workspace created.'})).toHaveCount(0);
  await reconcile(page,kind);expect(await page.evaluate(()=>(window as unknown as RecoveryWindow).writeRecovery.writes)).toBe(1);
 });

}
