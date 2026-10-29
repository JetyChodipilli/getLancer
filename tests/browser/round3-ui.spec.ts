import {expect,test,type Page} from '@playwright/test';
import {build} from 'esbuild';

const alpha='10000000-0000-4000-8000-000000000001',beta='10000000-0000-4000-8000-000000000002';

async function renderExplore(page:Page,initiallySaved=false){
  const errors:string[]=[];page.on('pageerror',error=>errors.push(error.message));
  const fixture=await build({stdin:{contents:`
    import {useState} from 'react';import {createRoot} from 'react-dom/client';
    import Explore from './app/components/explore';import {examples} from './lib/catalog';import {emptyFilters} from './lib/discovery';
    const products=examples.slice(0,3).map((product,index)=>({...product,id:'10000000-0000-4000-8000-00000000000'+(index+1),illustrative:false}));
    function Fixture(){const [expanded,setExpanded]=useState(false);return <><button className="button" onClick={()=>setExpanded(true)}>Refresh visible collection</button><Explore items={products.slice(0,expanded?3:2)} featured={[]} preview={false} totalItems={expanded?3:2} totalPages={1} filters={emptyFilters}/></>}
    createRoot(document.getElementById('round3-ui-root')).render(<Fixture/>);
  `,resolveDir:process.cwd(),loader:'tsx'},bundle:true,write:false,platform:'browser',format:'iife',jsx:'automatic',alias:{'@':process.cwd()},define:{'process.env.NODE_ENV':'"test"'},plugins:[{name:'round3-ui-navigation',setup(plugin){
    plugin.onResolve({filter:/^next\/(link|navigation)$/},args=>({path:args.path,namespace:'round3-ui-next'}));
    plugin.onLoad({filter:/.*/,namespace:'round3-ui-next'},args=>({contents:args.path==='next/link'?'export default function Link({children,...props}){return <a {...props}>{children}</a>}':'export function useRouter(){return {push(){}}}',loader:'jsx',resolveDir:process.cwd()}));
  }}]});
  await page.goto('/report');
  const styles=await page.locator('link[rel="stylesheet"],style').evaluateAll(nodes=>nodes.map(node=>node.outerHTML).join('\n'));
  expect(styles).not.toBe('');
  await page.route('**/__round3/ui',route=>route.fulfill({contentType:'text/html',body:'<!doctype html><html lang="en"><head><meta name="viewport" content="width=device-width,initial-scale=1">'+styles+'</head><body><div id="round3-ui-root"></div></body></html>'}));
  await page.route('**/api/v1/categories',route=>route.fulfill({json:{items:[{name:'Inventory'},{name:'Booking'}]}}));
  await page.route('**/api/v1/technologies',route=>route.fulfill({json:{items:[{name:'React'}]}}));
  await page.goto('/__round3/ui');await page.addScriptTag({content:fixture.outputFiles[0].text});
  await expect(page.getByRole('button',{name:(initiallySaved?'Unsave ':'Save ')+'Stockroom',exact:true})).toBeEnabled();
  await page.evaluate(()=>document.fonts.ready);
  return errors;
}

test('parallel catalogue saves keep each pending project disabled and confirm its exact saved state',async({page},testInfo)=>{
  const writes:{id:string;method:string}[]=[],held=new Map<string,()=>void>();
  await page.route('**/api/v1/me/saved-products?**',route=>route.fulfill({json:{items:[]}}));
  await page.route('**/api/v1/products/*/save',async route=>{
    const id=new URL(route.request().url()).pathname.split('/').at(-2)!;writes.push({id,method:route.request().method()});
    if(route.request().method()==='POST')await new Promise<void>(resolve=>held.set(id,resolve));
    await route.fulfill({json:{ok:true}});
  });
  const errors=await renderExplore(page);
  const first=page.getByRole('button',{name:'Save Stockroom',exact:true}),second=page.getByRole('button',{name:'Save Booklane',exact:true});
  await first.click();await expect.poll(()=>writes.length).toBe(1);await expect(first).toBeDisabled();
  await second.click();await expect.poll(()=>writes.length).toBe(2);
  await expect(first).toBeDisabled();await expect(second).toBeDisabled();
  await first.evaluate(button=>(button as HTMLButtonElement).click());expect(writes).toHaveLength(2);
  await expect.poll(()=>held.has(beta)).toBe(true);held.get(beta)!();
  await expect(page.getByRole('button',{name:'Unsave Booklane',exact:true})).toBeEnabled();await expect(first).toBeDisabled();
  held.get(alpha)!();
  const saved=page.getByRole('button',{name:'Unsave Stockroom',exact:true});await expect(saved).toBeEnabled();await expect(saved).toHaveAttribute('aria-pressed','true');
  await saved.focus();await page.keyboard.press('Enter');await expect.poll(()=>writes.length).toBe(3);
  await expect(page.getByRole('button',{name:'Save Stockroom',exact:true})).toHaveAttribute('aria-pressed','false');
  expect(writes).toEqual([{id:alpha,method:'POST'},{id:beta,method:'POST'},{id:alpha,method:'DELETE'}]);
  await page.setViewportSize({width:320,height:812});
  await page.evaluate(()=>{
    const sizes=Array.from(document.querySelectorAll<HTMLElement>('#round3-ui-root :is(h1,h2,p,button,input,select)')).map(element=>({element,size:parseFloat(getComputedStyle(element).fontSize)}));
    for(const {element,size} of sizes)element.style.setProperty('font-size',size*2+'px','important');
  });
  await expect(page.getByRole('button',{name:'Unsave Booklane',exact:true})).toBeVisible();
  await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(321);
  expect(errors).toEqual([]);await testInfo.attach('catalogue-save-recovery-320px',{body:await page.screenshot({fullPage:true}),contentType:'image/png'});
});

for(const initiallySaved of [false,true])for(const readFirst of [false,true])test(`a ${initiallySaved?'removal':'save'} survives a collection read finishing ${readFirst?'before':'after'} its acknowledgement`,async({page})=>{
  let releaseWrite!:()=>void,releaseRead!:()=>void,reads=0;
  const writeStarted=Promise.withResolvers<void>(),writeFinished=Promise.withResolvers<void>(),readStarted=Promise.withResolvers<void>();
  await page.route('**/api/v1/me/saved-products?**',async route=>{
    reads++;
    if(reads===1)return route.fulfill({json:{items:initiallySaved?[{id:alpha}]:[]}});
    readStarted.resolve();await new Promise<void>(resolve=>{releaseRead=resolve;});
    // A completed write is visible if this list wins; otherwise this is the older snapshot.
    const contains=readFirst?!initiallySaved:initiallySaved;
    await route.fulfill({json:{items:contains?[{id:alpha}]:[]}});
  });
  await page.route('**/api/v1/products/*/save',async route=>{writeStarted.resolve();await new Promise<void>(resolve=>{releaseWrite=resolve;});await route.fulfill({json:{ok:true}});writeFinished.resolve();});
  const errors=await renderExplore(page,initiallySaved);
  await page.getByRole('button',{name:(initiallySaved?'Unsave ':'Save ')+'Stockroom',exact:true}).click();await writeStarted.promise;
  await page.getByRole('button',{name:'Refresh visible collection',exact:true}).click();await readStarted.promise;
  if(readFirst){
    releaseRead();await expect(page.getByRole('button',{name:(initiallySaved?'Save ':'Unsave ')+'Stockroom',exact:true})).toBeDisabled();releaseWrite();
  }else{
    releaseWrite();await writeFinished.promise;
    await expect(page.getByRole('button',{name:/^(?:Unsave|Save) Stockroom$/})).toHaveAttribute('aria-pressed',String(!initiallySaved));releaseRead();
  }
  await expect(page.getByRole('button',{name:(initiallySaved?'Save ':'Unsave ')+'Stockroom',exact:true})).toBeEnabled();
  await expect(page.getByRole('button',{name:/^(?:Unsave|Save) Stockroom$/})).toHaveAttribute('aria-pressed',String(!initiallySaved));
  expect(errors).toEqual([]);
});

test('a rejected catalogue save retains its state and permits a corrected retry',async({page})=>{
  let writes=0;
  await page.route('**/api/v1/me/saved-products?**',route=>route.fulfill({json:{items:[]}}));
  await page.route('**/api/v1/products/*/save',route=>{writes++;return writes===1?route.fulfill({status:503,json:{error:{code:'UNAVAILABLE',message:'Project save unavailable.'}}}):route.fulfill({json:{ok:true}});});
  const errors=await renderExplore(page);const save=page.getByRole('button',{name:'Save Stockroom',exact:true});
  await save.click();await expect.poll(()=>writes).toBe(1);await expect(page.getByRole('alert')).toHaveText('Project save unavailable.');
  await expect(save).toBeEnabled();await expect(save).toHaveAttribute('aria-pressed','false');
  await save.click();await expect.poll(()=>writes).toBe(2);
  await expect(page.getByRole('button',{name:'Unsave Stockroom',exact:true})).toHaveAttribute('aria-pressed','true');await expect(page.getByRole('alert')).toHaveCount(0);
  expect(errors).toEqual([]);
});
