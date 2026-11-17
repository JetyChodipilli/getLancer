import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {test,expect,type Page} from './test';
const fixture=JSON.parse(readFileSync('.ci-connected.json','utf8'));
assert.equal(fixture.project,process.env.COMPOSE_PROJECT_NAME);
assert.match(fixture.project,/^getlancer-ci-[0-9]+$/);
async function login(page:Page,actor:'client'|'builder'){
 const account=fixture.accounts['ci-'+actor+'@example.test'];
 await page.goto('/login');await page.getByLabel('Email address',{exact:true}).fill(account.email);await page.getByLabel('Password',{exact:true}).fill(account.password);
 await page.getByRole('button',{name:'Log in',exact:true}).click();await expect(page).toHaveURL(/\/workspace$/);
}
const call=(page:Page,path:string,body?:unknown)=>page.request.fetch('/api/v1'+path,{method:body?'POST':'GET',data:body,headers:{Origin:'http://localhost:3000','X-Requested-With':'getlancer'}});

test('retained component saves cannot remove the replacement account’s association',async({page,context})=>{
 const other=await context.newPage(),slug='portfolio-card';
 try{
  await login(page,'client');expect((await call(page,'/components/'+slug+'/save',{})).status()).toBe(200);
  await page.goto('/saved/components');await expect(page.getByRole('link',{name:'Portfolio project card',exact:true})).toBeVisible();
  await login(other,'builder');expect((await call(other,'/components/'+slug+'/save',{})).status()).toBe(200);
  await page.locator('article').filter({has:page.getByRole('link',{name:'Portfolio project card',exact:true})}).getByRole('button',{name:'Remove',exact:true}).click();
  await expect(page.getByRole('link',{name:'Log in to view saved components',exact:true})).toBeVisible();
  expect((await (await call(other,'/me/saved-components')).json()).items.some((item:{slug:string})=>item.slug===slug)).toBe(true);
  await login(page,'client');expect((await (await call(page,'/me/saved-components')).json()).items.some((item:{slug:string})=>item.slug===slug)).toBe(true);
  await page.goto('/components/'+slug);await expect(page.getByRole('button',{name:'Saved component',exact:true})).toBeVisible();
  await login(other,'builder');await page.getByRole('button',{name:'Saved component',exact:true}).click();
  await expect(page.getByRole('link',{name:'Log in to save',exact:true})).toBeVisible();
  expect((await (await call(other,'/me/saved-components')).json()).items.some((item:{slug:string})=>item.slug===slug)).toBe(true);
 }finally{await other.close();}
});
