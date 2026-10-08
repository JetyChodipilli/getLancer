import {readFileSync} from 'node:fs';
import {createRequire} from 'node:module';
import {test,expect,type Page} from '@playwright/test';

const axe=readFileSync(createRequire(import.meta.url).resolve('axe-core/axe.min.js'),'utf8');
async function scan(page:Page,label:string,info:import('@playwright/test').TestInfo){
 await page.evaluate(axe);
 const result=await page.evaluate(async()=> (window as any).axe.run(document.querySelector('main'),{runOnly:{type:'tag',values:['wcag2a','wcag2aa','wcag21aa','wcag22aa']}}));
 await info.attach(label,{body:JSON.stringify(result),contentType:'application/json'});
 expect(result.violations.filter((v:any)=>['critical','serious'].includes(v.impact)).map((v:any)=>({id:v.id,nodes:v.nodes.map((n:any)=>n.target)}))).toEqual([]);
}
test('new component routes and conditional draft fields have no serious automated WCAG violations',async({page},info)=>{
 test.setTimeout(120000);
 for(const [label,path] of [['catalogue','/components'],['detail','/components/feedback-form'],['saved','/saved/components'],['creator','/preview/components']]){
  await page.goto(path);await expect(page.locator('main h1')).toBeVisible();await scan(page,label,info);
 }
 await page.getByRole('button',{name:'New component',exact:true}).click();
 // axe includes the portal dialog as an explicit scan root, outside main.
 const dialog=page.getByRole('dialog');await expect(dialog).toBeVisible();await expect(dialog).toHaveCSS('opacity','1');
 for(const step of [1,2]){
  if(step===2){await dialog.getByLabel('Component title',{exact:true}).fill('Accessible private draft');await dialog.getByLabel('Summary',{exact:true}).fill('An original source contribution with a clear summary.');await dialog.getByRole('button',{name:'Continue to contribution',exact:true}).click();await dialog.getByRole('checkbox',{name:'Upload my self-contained frontend source',exact:true}).check();}
  await page.evaluate(axe);const result=await page.evaluate(async()=> (window as any).axe.run(document.querySelector('[role="dialog"]'),{runOnly:{type:'tag',values:['wcag2a','wcag2aa','wcag21aa','wcag22aa']}}));
  await info.attach('draft-step-'+step,{body:JSON.stringify(result),contentType:'application/json'});expect(result.violations.filter((v:any)=>['critical','serious'].includes(v.impact)).map((v:any)=>({id:v.id,nodes:v.nodes.map((n:any)=>n.target)}))).toEqual([]);
 }
});
