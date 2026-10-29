import {test,expect,type Page} from '@playwright/test';
import {buildSync} from 'esbuild';

const requests=['A','B'].map(id=>({id,projectTitle:'Project '+id,builder:'Builder '+id,description:'Request for project '+id,budgetBand:'UNSURE',timelineBand:'FLEXIBLE',status:'NOT_HIRED',moderationStatus:'CLEAR',reviewSubmitted:false}));
async function mount(page:Page){
 const script=buildSync({stdin:{contents:"import {createRoot} from 'react-dom/client';import ClientRequests from './app/components/client-requests';createRoot(document.getElementById('fixture')).render(<ClientRequests verified={true}/>);",loader:'tsx',resolveDir:process.cwd()},bundle:true,write:false,format:'iife',platform:'browser',jsx:'automatic',define:{'process.env.NODE_ENV':'"test"','process.env':'{}'}}).outputFiles[0].text;
 await page.route('**/request-history-fixture',route=>route.fulfill({contentType:'text/html',body:'<main><h1>Client requests</h1><div id="fixture"></div></main><script src="/request-history-fixture.js"></script>'}));
 await page.route('**/request-history-fixture.js',route=>route.fulfill({contentType:'application/javascript',body:script}));
 await page.goto('/request-history-fixture');
 await expect(page.getByRole('button',{name:'View request',exact:true})).toHaveCount(2);
}
test('delayed inquiry history cannot replace the currently selected inquiry',async({page})=>{
 let releaseA!:()=>void,startedA!:()=>void;
 const pendingA=new Promise<void>(resolve=>{releaseA=resolve;}),aStarted=new Promise<void>(resolve=>{startedA=resolve;});
 await page.route('**/api/v1/me/inquiries*',route=>route.fulfill({json:{items:requests,hasMore:false}}));
 await page.route('**/api/v1/me/inquiries/*',async route=>{
  const id=new URL(route.request().url()).pathname.split('/').pop();
  if(id==='A'){startedA();await pendingA;}
  await route.fulfill({json:{events:[{event_type:id==='A'?'HIRE_CONFIRMED':'COMPLETION_CONFIRMED',actor_type:'CLIENT',created_at:'2026-10-26T12:00:00Z'}]}});
 });
 await mount(page);
 await page.getByRole('button',{name:'View request',exact:true}).nth(0).click();await aStarted;
 await expect(page.getByRole('dialog')).toContainText('Loading request history');
 await page.keyboard.press('Escape');
 await page.getByRole('button',{name:'View request',exact:true}).nth(1).click();
 const dialog=page.getByRole('dialog');
 await expect(dialog.getByRole('heading',{name:'Project B',exact:true})).toBeVisible();
 await expect(dialog).toContainText('Completion Confirmed');
 const finished=page.waitForResponse(response=>response.url().endsWith('/me/inquiries/A'));
 releaseA();await finished;
 await expect(dialog).toContainText('Completion Confirmed');await expect(dialog).not.toContainText('Hire Confirmed');
});
test('history failures remain scoped to the selected inquiry and a new selection clears them',async({page})=>{
 await page.route('**/api/v1/me/inquiries*',route=>route.fulfill({json:{items:requests,hasMore:false}}));
 await page.route('**/api/v1/me/inquiries/*',route=>route.request().url().endsWith('/A')?route.fulfill({status:503,json:{error:{message:'History is temporarily unavailable.'}}}):route.fulfill({json:{events:[]}}));
 await mount(page);
 await page.getByRole('button',{name:'View request',exact:true}).nth(0).click();
 await expect(page.getByRole('dialog').getByRole('alert')).toHaveText('This service is temporarily unavailable. Please try loading it again.');
 await expect(page.getByRole('dialog').getByRole('alert')).not.toContainText('History is temporarily unavailable.');
 await page.keyboard.press('Escape');
 await page.getByRole('button',{name:'View request',exact:true}).nth(1).click();
 await expect(page.getByRole('dialog')).toContainText('No history events yet.');
 await expect(page.getByRole('dialog').getByRole('alert')).toHaveCount(0);
});
