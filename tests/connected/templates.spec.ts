import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createHmac} from 'node:crypto';
import {test,expect,type Page} from '@playwright/test';
import {readEnvironment} from '../../scripts/local-config.mjs';
import {sourceFixture} from '../../scripts/ci-source-fixture.mjs';

const fixture=JSON.parse(readFileSync('.ci-connected.json','utf8'));
assert.equal(fixture.project,process.env.COMPOSE_PROJECT_NAME);
assert.match(fixture.project,/^getlancer-ci-[0-9]+$/);
const env=readEnvironment(new URL('../../.env',import.meta.url)) as Record<string,string>;
assert.equal(env.ADMIN_EMAIL,'ci-admin@example.test');
function totp(){
 const bits=[...env.ADMIN_TOTP_SECRET].map(c=>'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'.indexOf(c).toString(2).padStart(5,'0')).join('');
 const secret=Buffer.from(bits.match(/.{8}/g)!.map(b=>parseInt(b,2))),counter=Buffer.alloc(8);counter.writeBigUInt64BE(BigInt(Math.floor(Date.now()/30000)));
 const h=createHmac('sha1',secret).update(counter).digest(),offset=h[h.length-1]&15;return String((h.readUInt32BE(offset)&0x7fffffff)%1000000).padStart(6,'0');
}
async function login(page:Page,actor:'client'|'builder'|'admin'){
 const account=actor==='admin'?{email:env.ADMIN_EMAIL,password:env.ADMIN_BOOTSTRAP_PASSWORD}:fixture.accounts['ci-'+actor+'@example.test'];
 await page.goto('/login');await page.getByLabel('Email address',{exact:true}).fill(account.email);await page.getByLabel('Password',{exact:true}).fill(account.password);
 await page.getByRole('button',{name:'Log in',exact:true}).click();
 if(actor==='admin'){await page.getByLabel('Authenticator code',{exact:true}).fill(totp());await page.getByRole('button',{name:'Verify and log in',exact:true}).click();}
 await expect(page).toHaveURL(/\/workspace$/);
}
const call=(page:Page,path:string,body?:unknown)=>page.request.fetch('/api/v1'+path,{method:body?'POST':'GET',data:body,headers:{Origin:'http://localhost:3000','X-Requested-With':'getlancer'}});
async function reflow(page:Page){expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(page.viewportSize()!.width+1);}

test('real private ZIP release, MFA review, buyer consent and disabled checkout preserve isolation',async({page,browser},info)=>{
 const template=fixture.commerce[info.project.name],source=sourceFixture();
 const options={viewport:info.project.use.viewport,isMobile:info.project.use.isMobile,hasTouch:info.project.use.hasTouch,baseURL:'http://localhost:3000'};
 const sellerContext=await browser.newContext(options),adminContext=await browser.newContext(options);
 const seller=await sellerContext.newPage(),admin=await adminContext.newPage(),errors:string[]=[],gatewayRequests:string[]=[];
 for(const p of [page,seller,admin]){p.on('pageerror',e=>errors.push(e.message));p.on('request',r=>{if((new URL(r.url()).hostname==='razorpay.com'||new URL(r.url()).hostname.endsWith('.razorpay.com')))gatewayRequests.push(r.url());});}
 try{
  await login(seller,'builder');await seller.goto('/workspace/templates?templateId='+template.id);
  await expect(seller.getByRole('heading',{name:template.title,exact:true})).toBeVisible();
  const upload=seller.locator('.commerce-package-form');
  await upload.getByLabel('Version number',{exact:true}).fill('1.0.0');await upload.getByLabel('Release notes',{exact:true}).fill('Real connected browser fixture with a build manifest and license notices.');
  await upload.getByLabel('Private source ZIP',{exact:true}).setInputFiles({name:'source.zip',mimeType:'application/zip',buffer:source});await upload.getByRole('checkbox').check();
  await upload.getByRole('button',{name:'Upload private version',exact:true}).click();
  await expect(seller.getByText('Version 1.0.0',{exact:true})).toBeVisible();await reflow(seller);
  await seller.getByRole('button',{name:'Submit version for review',exact:true}).click();
  const consent=seller.getByRole('dialog');await expect(consent.getByRole('button',{name:'Confirm rights & submit version',exact:true})).toBeDisabled();await consent.getByRole('checkbox').check();await consent.getByRole('button',{name:'Confirm rights & submit version',exact:true}).click();await expect(consent).not.toBeVisible();
  await login(page,'client');expect((await call(page,'/templates/'+template.slug)).status()).toBe(404);
  const owned=(await (await call(seller,'/me/templates')).json()).items.find((item:any)=>item.id===template.id),release=owned.versions[0];
  expect((await call(page,`/me/templates/${template.id}/versions/${release.id}/package`)).status()).toBe(404);
  expect((await call(page,`/admin/templates/${template.id}/versions/${release.id}/package`)).status()).toBe(403);
  await login(admin,'admin');await admin.goto('/workspace');await admin.getByText('Source marketplace review & recovery',{exact:true}).click();
  const record=admin.locator('.commerce-operator-record').filter({has:admin.getByRole('heading',{name:template.title,exact:true})});
  await expect(record).toBeVisible();const downloading=admin.waitForEvent('download');await record.getByRole('button',{name:'Download private review package',exact:true}).click();const download=await downloading;assert.deepEqual(readFileSync((await download.path())!),source);
  await record.getByLabel('Decision for version 1.0.0',{exact:true}).selectOption('APPROVE');await record.getByLabel('Review reason for version 1.0.0',{exact:true}).fill('CI inspected actual package bytes, source manifest and license notices without executing the source.');
  for(const checkbox of await record.getByRole('checkbox').all())await checkbox.check();await record.getByRole('button',{name:'Record package review',exact:true}).click();await expect(record.getByText('Approved',{exact:true})).toBeVisible();await reflow(admin);
  await page.goto('/templates/'+template.slug);await expect(page.getByRole('heading',{name:template.title,exact:true})).toBeVisible();await reflow(page);
  await page.getByRole('button',{name:'Review license & buy',exact:true}).click();const license=page.getByRole('dialog');
  await expect(license.getByRole('button',{name:'Accept license & continue to payment',exact:true})).toBeDisabled();await license.getByRole('checkbox').check();await license.getByRole('button',{name:'Accept license & continue to payment',exact:true}).click();await expect(license.getByRole('alert')).toContainText(/operator|not configured|requires/i);
  expect((await (await call(page,'/me/template-purchases')).json()).items).toEqual([]);expect(gatewayRequests).toEqual([]);
  await seller.getByRole('button',{name:'Archive listing',exact:true}).click();await seller.getByRole('dialog').getByRole('button',{name:'Archive source listing',exact:true}).click();await expect(seller.getByRole('dialog')).not.toBeVisible();
  expect((await call(page,'/templates/'+template.slug)).status()).toBe(404);expect(errors).toEqual([]);
 }finally{await sellerContext.close();await adminContext.close();}
});

test('revoked personal session clears loaded private workspace content',async({page})=>{
 await login(page,'builder');await page.goto('/workspace');await page.getByRole('tab',{name:/Showcases/}).click();
 await expect(page.getByRole('button',{name:'New draft',exact:true})).toBeVisible();
 expect((await call(page,'/auth/logout',{})).ok()).toBeTruthy();
 await page.getByRole('tab',{name:/Drafts & review/}).click();
 await expect(page.getByRole('link',{name:'Log in',exact:true}).last()).toBeVisible();
 await expect(page.getByRole('button',{name:'New draft',exact:true})).toHaveCount(0);
 await expect(page.getByRole('heading',{name:/CI Inventory/})).toHaveCount(0);
});
