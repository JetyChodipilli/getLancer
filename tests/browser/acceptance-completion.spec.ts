import {test,expect,type Page,type Locator,type TestInfo} from '@playwright/test';
import {readFileSync} from 'node:fs';
import {createRequire} from 'node:module';

const axe=readFileSync(createRequire(import.meta.url).resolve('axe-core/axe.min.js'),'utf8');
const widths=[320,390,768,1024,1440];
const routes=['/components','/components/feedback-form','/components/frameworks','/college-projects','/college-projects/stockroom','/labs','/labs/scenarios','/labs/data-ai','/labs/iot','/saved/components','/preview/components','/preview/college-projects'];
async function scan(page:Page,root:string,label:string,info:TestInfo){
 await page.evaluate(axe);
 const result=await page.evaluate(async selector=>{
  const context=document.querySelector(selector);if(!context)throw new Error('Accessibility scan root is absent: '+selector);
  return (window as unknown as Window & {axe:typeof import('axe-core')}).axe.run(context,{runOnly:{type:'tag',values:['wcag2a','wcag2aa','wcag21aa','wcag22aa']}});
 },root);
 await info.attach(label,{body:JSON.stringify(result),contentType:'application/json'});
 expect(result.violations.filter(finding=>['critical','serious'].includes(finding.impact||'')).map(finding=>({id:finding.id,nodes:finding.nodes.map(node=>node.target)}))).toEqual([]);
}
async function reflow(page:Page){
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1)).toBe(true);
 const unexpected=await page.locator('main').evaluate(main=>Array.from(main.querySelectorAll<HTMLElement>('*')).filter(element=>element.clientWidth>0&&element.scrollWidth>element.clientWidth+1&&['auto','scroll'].includes(getComputedStyle(element).overflowX)&&!element.closest('pre,code,table')&&!element.querySelector('pre,code,table')).map(element=>({tag:element.tagName,id:element.id,className:element.className})));
 expect(unexpected).toEqual([]);
}
async function unobscured(control:Locator){
 await control.focus();await expect(control).toBeFocused();
 const result=await control.evaluate(element=>{
  const box=element.getBoundingClientRect(),cx=box.left+box.width/2,cy=box.top+box.height/2;
  const dx=Math.min(3,box.width/4),dy=Math.min(3,box.height/4);
  const describe=(node:Element|null)=>node?{tag:node.tagName,id:node.id,className:node.getAttribute('class')}:null;
  // Edge midpoints lie inside rounded controls; transparent corners are not painted targets.
  const probes=[{edge:'top',x:cx,y:box.top+dy},{edge:'right',x:box.right-dx,y:cy},{edge:'bottom',x:cx,y:box.bottom-dy},{edge:'left',x:box.left+dx,y:cy},{edge:'center',x:cx,y:cy}].map(point=>{
   const hit=document.elementFromPoint(point.x,point.y);return {...point,owned:hit!==null&&(element===hit||element.contains(hit)),hit:describe(hit)};
  });
  const inViewport=box.width>0&&box.height>0&&box.top>=0&&box.left>=0&&box.bottom<=innerHeight+1&&box.right<=innerWidth+1;
  return {clear:inViewport&&probes.every(point=>point.owned),path:location.pathname,control:describe(element),name:(element.getAttribute('aria-label')||element.textContent||'').trim().replace(/\s+/g,' ').slice(0,100),bounds:{x:box.x,y:box.y,width:box.width,height:box.height},viewport:{width:innerWidth,height:innerHeight},inViewport,probes};
 });
 // Full bounds and edge/center clearance exceed the AA not-entirely-obscured minimum.
 expect(result.clear,'Focused control must be fully visible: '+JSON.stringify(result)).toBe(true);
}
async function unchanged(before:{width:number;height:number}|null,control:Locator){const after=await control.boundingBox();expect(before).not.toBeNull();expect(after).not.toBeNull();expect(Math.abs(after!.width-before!.width)).toBeLessThanOrEqual(1);expect(Math.abs(after!.height-before!.height)).toBeLessThanOrEqual(1);}
async function reducedMotion(page:Page){
 expect(await page.evaluate(()=>matchMedia('(prefers-reduced-motion: reduce)').matches)).toBe(true);
 expect(await page.locator('main').evaluate(main=>main.getAnimations({subtree:true}).filter(animation=>animation.playState==='running'&&Number(animation.effect?.getComputedTiming().duration)>1).length)).toBe(0);
}

test('initial lab availability errors preserve keyboard focus and explicit retries focus recovery',async({page},info)=>{
 await page.setViewportSize({width:768,height:1000});await page.emulateMedia({reducedMotion:'reduce'});
 let releaseInitial!:()=>void;const initialGate=new Promise<void>(resolve=>{releaseInitial=resolve;});
 let announceRequest!:()=>void;const initialRequest=new Promise<void>(resolve=>{announceRequest=resolve;});let catalogueCalls=0;
 await page.route('**/api/v1/lab-manifests',async route=>{catalogueCalls++;if(catalogueCalls===1){announceRequest();await initialGate;}await route.fulfill({status:503,json:{error:{code:'LAB_UNAVAILABLE',message:'Current catalogue is unavailable.'}}});});
 await page.route('**/api/v1/lab-quota',route=>route.fulfill({status:401,json:{error:{code:'UNAUTHORIZED',message:'Sign in to check allowance.'}}}));
 await page.goto('/labs');await initialRequest;
 const explorer=page.getByRole('link',{name:'Explore data & AI',exact:true}),next=page.getByRole('link',{name:'Explore backend scenarios',exact:true});
 await explorer.focus();await page.keyboard.press('Tab');await expect(next).toBeFocused();releaseInitial();
 const alert=page.getByRole('alert');await expect(alert).toHaveText('Current catalogue is unavailable.');await expect(page.getByText('Loading current lab availability…',{exact:true})).toHaveCount(0);
 // Let the error commit and passive effects finish before checking focus; never re-focus to hide a loss.
 await page.evaluate(()=>new Promise<void>(done=>requestAnimationFrame(()=>requestAnimationFrame(()=>done()))));await expect(next).toBeFocused();
 await reflow(page);await scan(page,'main','initial-lab-error-keyboard',info);
 const retry=page.getByRole('button',{name:'Refresh availability',exact:true});await retry.focus();await page.keyboard.press('Enter');
 await expect(alert).toBeFocused();expect(catalogueCalls).toBe(2);await unobscured(alert);await reflow(page);await scan(page,'main','retried-lab-error-keyboard',info);
});

for(const width of widths)test('new route shells retain keyboard focus, reduced-motion evidence and reflow at '+width+'px',async({page},info)=>{
 test.setTimeout(180000);await page.setViewportSize({width,height:1000});await page.emulateMedia({reducedMotion:'reduce'});
 const errors:string[]=[];page.on('pageerror',error=>errors.push(error.message));
 for(const path of routes){
  await page.goto(path);await expect(page.locator('main h1')).toBeVisible();await reflow(page);await reducedMotion(page);
  const controls=page.locator('main').locator('a[href],button:not(:disabled),input:not(:disabled):not([type="hidden"]),select:not(:disabled),textarea:not(:disabled)').filter({visible:true});
  if(await controls.count()){
   await unobscured(controls.first());await page.keyboard.press('Tab');
   const active=await page.evaluate(()=>({tag:document.activeElement?.tagName,hidden:document.activeElement?.getAttribute('aria-hidden')}));expect(active.tag).not.toBe('BODY');expect(active.hidden).not.toBe('true');
   await unobscured(controls.last());
  }
  if(path==='/components'){
   const filter=page.getByLabel('Component category',{exact:true}),before=await filter.boundingBox();await filter.selectOption('FORM');await unchanged(before,filter);
  }else if(path==='/components/feedback-form'){
   const phone=page.getByRole('button',{name:'Phone',exact:true}),before=await phone.boundingBox();await phone.click();await expect(phone).toHaveAttribute('aria-pressed','true');await unchanged(before,phone);
   await page.getByRole('tab',{name:'Source',exact:true}).click();await expect(page.getByRole('tabpanel')).toContainText('index.html');
  }else if(path==='/college-projects'){
   const filter=page.getByLabel('Access mode',{exact:true}),before=await filter.boundingBox();await filter.selectOption('FREE');await unchanged(before,filter);
  }else if(path==='/preview/college-projects'){
   await page.getByRole('button',{name:'New education release',exact:true}).click();
   const category=page.getByLabel('Release category',{exact:true}),before=await category.boundingBox();await category.selectOption('AI_ML');await unchanged(before,category);
   await page.getByRole('button',{name:'Next step',exact:true}).click();
   const heading=page.getByRole('heading',{name:'Build / Evidence',exact:true});await expect(heading).toBeFocused();await unobscured(heading);
   await expect(page.getByLabel('Dataset & model licenses',{exact:true})).toBeVisible();
   await page.getByRole('button',{name:'3. Access / License',exact:true}).click();
   const access=page.getByLabel('Access mode',{exact:true}),box=await access.boundingBox();await access.selectOption('PAID');await unchanged(box,access);await expect(page.getByLabel('Approved source version',{exact:true})).toBeVisible();
  }
  await reflow(page);await scan(page,'main','route-'+path.replaceAll('/','-')+'-'+width,info);
  if(info.project.name==='desktop')await info.attach('route-'+path.replaceAll('/','-')+'-'+width,{body:await page.screenshot({fullPage:true,animations:'disabled'}),contentType:'image/png'});
 }
 expect(errors).toEqual([]);
});

for(const width of widths)test('draft server-error and selected controls keep size, labels and keyboard recovery at '+width+'px',async({page},info)=>{
 test.setTimeout(90000);await page.setViewportSize({width,height:1000});await page.emulateMedia({reducedMotion:'reduce'});
 const pricing={amountMinor:null,enabled:false,salesEnabled:false,configured:false,currency:'INR',mode:'disabled',reason:'No additional slot price configured.'};
 // Controlled API errors prove frontend recovery; connected CI owns stack authorization.
 await page.route('**/api/v1/**',route=>{
  const path=new URL(route.request().url()).pathname;
  if(path==='/api/v1/me')return route.fulfill({json:{roles:['DEVELOPER'],emailVerified:true,profile:{approval_status:'APPROVED'}}});
  if(path==='/api/v1/me/components'&&route.request().method()==='GET')return route.fulfill({json:{items:[],capacity:{free:3,purchased:0,used:0,limit:3},pricing}});
  if(path==='/api/v1/me/component-slot-purchases')return route.fulfill({json:{items:[],hasMore:false}});
  if(path==='/api/v1/me/components'&&route.request().method()==='POST')return route.fulfill({status:422,json:{error:{code:'VALIDATION_ERROR',message:'Correct the source details.',fieldErrors:{title:'Use the reviewed title.',file:'Choose the reviewed ZIP.'}}}});
  return route.fulfill({status:404,json:{error:{code:'NOT_FOUND',message:'No unrelated fixture route.'}}});
 });
 await page.goto('/workspace/components');const opener=page.getByRole('button',{name:'New component',exact:true});await opener.click();
 const dialog=page.getByRole('dialog');await expect(dialog).toBeVisible();await expect(dialog).toHaveCSS('opacity','1');
 const title=dialog.getByLabel('Component title',{exact:true}),before=await title.boundingBox();
 await title.fill('Original accessible source');await dialog.getByLabel('Summary',{exact:true}).fill('An original source contribution with complete documentation.');
 const recipe=dialog.getByLabel('Base component',{exact:true}),recipeBox=await recipe.boundingBox();await recipe.selectOption({index:1});await unchanged(recipeBox,recipe);
 await dialog.getByRole('button',{name:'Continue to contribution',exact:true}).click();
 await dialog.getByLabel('Your contribution & intended use',{exact:true}).fill('I implemented the original interaction and preserve attribution.');await dialog.getByRole('checkbox',{name:/I preserve/}).check();
 await dialog.getByRole('button',{name:'Save component draft',exact:true}).click();await expect(dialog.getByRole('alert')).toContainText('Correct the source details.');
 const errorLink=dialog.getByRole('link',{name:'title: Use the reviewed title.',exact:true});await errorLink.focus();await page.keyboard.press('Enter');
 await expect(title).toBeFocused();await expect(title).toHaveAttribute('aria-invalid','true');await expect(title).toHaveAttribute('aria-describedby','title-error');await unchanged(before,title);await unobscured(title);
 await scan(page,'[role="dialog"]','draft-error-title-'+width,info);
 const fileLink=dialog.getByRole('link',{name:'file: Choose the reviewed ZIP.',exact:true});await fileLink.focus();await page.keyboard.press('Enter');
 const file=dialog.getByLabel('Source ZIP',{exact:true});await expect(file).toBeFocused();await expect(file).toHaveAttribute('aria-invalid','true');await expect(file).toHaveAttribute('aria-describedby','file-error');await unobscured(file);
 await scan(page,'[role="dialog"]','draft-error-source-'+width,info);await reflow(page);await reducedMotion(page);
 if(info.project.name==='desktop')await info.attach('draft-error-'+width,{body:await page.screenshot({fullPage:true,animations:'disabled'}),contentType:'image/png'});
 await page.keyboard.press('Escape');await expect(dialog).not.toBeVisible();await expect(opener).toBeFocused();await unobscured(opener);
});
