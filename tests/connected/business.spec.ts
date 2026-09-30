import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createHmac} from 'node:crypto';
import {test,expect,type Page} from '@playwright/test';
import {readEnvironment} from '../../scripts/local-config.mjs';

const fixture=JSON.parse(readFileSync('.ci-connected.json','utf8'));
assert.equal(fixture.project,process.env.COMPOSE_PROJECT_NAME);
assert.match(fixture.project,/^getlancer-ci-[0-9]+$/);
const env=readEnvironment(new URL('../../.env',import.meta.url)) as Record<string,string>;
assert.equal(env.ADMIN_EMAIL,'ci-admin@example.test');

function code(){
 const bits=[...env.ADMIN_TOTP_SECRET].map(c=>'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'.indexOf(c).toString(2).padStart(5,'0')).join('');
 const key=Buffer.from(bits.match(/.{8}/g)!.map(b=>parseInt(b,2))),counter=Buffer.alloc(8);
 counter.writeBigUInt64BE(BigInt(Math.floor(Date.now()/30000)));
 const digest=createHmac('sha1',key).update(counter).digest(),offset=digest[digest.length-1]&15;
 return String((digest.readUInt32BE(offset)&0x7fffffff)%1000000).padStart(6,'0');
}
async function login(page:Page,actor:'client'|'manager'|'builder'|'admin'){
 const account=actor==='admin'?{email:env.ADMIN_EMAIL,password:env.ADMIN_BOOTSTRAP_PASSWORD}:fixture.accounts['ci-'+actor+'@example.test'];
 await page.goto('/login');
 await page.getByLabel('Email address',{exact:true}).fill(account.email);
 await page.getByLabel('Password',{exact:true}).fill(account.password);
 await expect(page.getByRole('button',{name:'Log in',exact:true})).toBeEnabled();
 await page.getByRole('button',{name:'Log in',exact:true}).click();
 if(actor==='admin'){
  await page.getByLabel('Authenticator code',{exact:true}).fill(code());
  await page.getByRole('button',{name:'Verify and log in',exact:true}).click();
 }
 await expect(page).toHaveURL(/\/workspace$/);
 await page.goto('/workspace/business');
 await expect(page.getByRole('button',{name:'Create business',exact:true})).toBeVisible();
 await expect(page.getByText('Interactive business preview',{exact:true})).toHaveCount(0);
}
async function call(page:Page,path:string,data?:unknown,method=data?'POST':'GET'){
 return page.request.fetch('/api/v1'+path,{method,data,headers:{Origin:'http://localhost:3000','X-Requested-With':'getlancer'}});
}
async function reflow(page:Page){
 expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(page.viewportSize()!.width+1);
}

test('real hiring journey persists consent, matching, talent, MFA concierge and revocation',async({page,browser},info)=>{
 const b=fixture.businesses[info.project.name],root='/businesses/'+b.id,title='Connected '+info.project.name+' inventory';
 const {viewport,isMobile,hasTouch}=info.project.use;
 const contexts=await Promise.all(['manager','builder','admin'].map(()=>browser.newContext({viewport,isMobile,hasTouch,baseURL:'http://localhost:3000'})));
 const [manager,outsider,admin]=await Promise.all(contexts.map(c=>c.newPage()));
 const errors:string[]=[];
 for(const p of [page,manager,outsider,admin])p.on('pageerror',e=>errors.push(e.message));
 try{
  await login(page,'client');await page.getByLabel('Business workspace',{exact:true}).selectOption(b.id);
  await expect(page.getByRole('button',{name:'Business settings',exact:true})).toBeVisible();
  await login(manager,'manager');
  expect((await call(manager,root)).status()).toBe(404);
  const invitation=manager.locator('.business-invitation').filter({hasText:b.name+' invited you to join.'});
  await invitation.getByRole('button',{name:'Accept invitation',exact:true}).click();
  await manager.getByLabel('Business workspace',{exact:true}).selectOption(b.id);
  await expect(manager.getByRole('button',{name:'New project request',exact:true})).toBeVisible();
  await expect(manager.getByRole('button',{name:'Business settings',exact:true})).toHaveCount(0);
  expect((await call(manager,root,{name:'Unauthorized',summary:'Owner-only mutation must fail.'},'PUT')).status()).toBe(403);

  await page.getByRole('button',{name:'New project request',exact:true}).click();
  const form=page.getByRole('dialog');
  await form.getByLabel('Project title',{exact:true}).fill(title);
  await form.getByLabel('Problem, users and essential requirements',{exact:true}).fill('Build an accessible private inventory approval workspace for our operations team.');
  await form.getByLabel('Budget range',{exact:true}).fill('10k');
  await form.getByLabel('Timeline',{exact:true}).fill('2 months');
  await form.getByRole('button',{name:'Create project request',exact:true}).click();
  await expect(form.getByRole('alert')).toContainText('Choose a category or technology');
  await expect(form.getByRole('alert')).toBeFocused();
  await form.getByLabel('Business category (optional if technology provided)',{exact:true}).fill('Inventory');
  await form.getByLabel('Technology (one, optional if category provided)',{exact:true}).fill('React');
  await reflow(page);
  await form.getByRole('button',{name:'Create project request',exact:true}).click();
  await expect(form).not.toBeVisible();
  await page.reload();await page.getByLabel('Business workspace',{exact:true}).selectOption(b.id);await expect(page.getByRole('heading',{name:title,exact:true})).toBeVisible();
  const brief=(await (await call(page,root+'/requests')).json()).items.find((r:any)=>r.title===title);
  assert.ok(brief);const briefRoot=root+'/requests/'+brief.id;
  await page.getByRole('button',{name:'Open for matching',exact:true}).click();
  const matches=page.locator('.business-candidate-section');
  await expect(matches.getByRole('heading',{name:'CI Builder',exact:true})).toBeVisible();
  await expect(matches.getByRole('heading',{name:'CI Studio',exact:true})).toBeVisible();
  await page.getByRole('tab',{name:'Saved talent',exact:true}).click();
  await page.getByRole('button',{name:'New talent list',exact:true}).click();
  await page.getByRole('dialog').getByLabel('List name',{exact:true}).fill('Connected inventory talent');
  await page.getByRole('dialog').getByRole('button',{name:'Create talent list',exact:true}).click();
  await expect(page.getByRole('dialog')).not.toBeVisible();
  await page.getByRole('tab',{name:'Requests',exact:true}).click();
  const builder=matches.locator('.business-candidate').filter({has:page.getByRole('heading',{name:'CI Builder',exact:true})});
  const list=(await (await call(page,root+'/talent-lists')).json()).items[0];
  await builder.getByLabel('Save CI Builder to a talent list',{exact:true}).selectOption(list.id);
  await expect(page.getByRole('status').filter({hasText:'Changes saved.'})).toBeVisible();
  await builder.getByRole('button',{name:'Shortlist',exact:true}).click();
  await expect(builder.getByRole('button',{name:'Shortlisted',exact:true})).toBeDisabled();
  await manager.reload();
  await manager.getByLabel('Business workspace',{exact:true}).selectOption(b.id);
  await manager.getByRole('tab',{name:'Saved talent',exact:true}).click();
  await expect(manager.getByRole('heading',{name:'CI Builder',exact:true})).toBeVisible();

  await login(outsider,'builder');
  expect((await call(outsider,briefRoot+'/shortlist')).status()).toBe(404);
  await page.getByRole('button',{name:'Request concierge sourcing',exact:true}).click();
  await page.getByRole('dialog').getByRole('button',{name:'Share brief for sourcing',exact:true}).click();
  await expect(page.getByRole('dialog')).not.toBeVisible();
  await login(admin,'admin');
  const sourcing=admin.locator('.business-sourcing-record').filter({has:admin.getByRole('heading',{name:title,exact:true})});
  await sourcing.getByRole('button',{name:'Start sourcing',exact:true}).click();
  await sourcing.getByRole('button',{name:'Review matching evidence',exact:true}).click();
  const studio=sourcing.locator('.business-candidate').filter({has:admin.getByRole('heading',{name:'CI Studio',exact:true})});
  await studio.getByLabel('Client-visible recommendation reason',{exact:true}).fill('Consented public Inventory and React evidence fits this private brief.');
  await studio.getByRole('button',{name:'Recommend candidate',exact:true}).click();
  await sourcing.getByRole('button',{name:'Mark fulfilled',exact:true}).click();
  await expect(sourcing.getByText('Fulfilled',{exact:true})).toBeVisible();
  await page.reload();
  await page.getByLabel('Business workspace',{exact:true}).selectOption(b.id);
  await expect(page.locator('.business-shortlist').getByText('Concierge recommendation',{exact:true})).toBeVisible();
  await reflow(page);await reflow(manager);await reflow(admin);
  await page.screenshot({path:info.outputPath('connected-business.png'),fullPage:true});

  await page.getByRole('tab',{name:'Hiring team',exact:true}).click();
  await page.getByRole('button',{name:'Remove access',exact:true}).click();
  await page.getByRole('dialog').getByRole('button',{name:'Remove access',exact:true}).click();
  await expect(page.getByRole('dialog')).not.toBeVisible();
  expect((await call(manager,briefRoot+'/shortlist')).status()).toBe(404);
  await manager.reload();await expect(manager.getByRole('heading',{name:title,exact:true})).toHaveCount(0);
  await page.getByRole('tab',{name:'Requests',exact:true}).click();
  await page.getByRole('button',{name:'Close request',exact:true}).click();
  await page.getByRole('dialog').getByRole('button',{name:'Close request',exact:true}).click();
  await expect(page.getByRole('dialog')).not.toBeVisible();
  await page.reload();await page.getByLabel('Business workspace',{exact:true}).selectOption(b.id);
  await expect(page.getByRole('button',{name:'Edit brief',exact:true})).toHaveCount(0);
  expect((await call(page,briefRoot+'/matches')).status()).toBe(409);
  expect(errors).toEqual([]);
 }finally{await Promise.all(contexts.map(c=>c.close()));}
});
