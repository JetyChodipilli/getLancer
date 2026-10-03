import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createHmac} from 'node:crypto';
import {test,expect,type Page} from '@playwright/test';
import {readEnvironment} from '../../scripts/local-config.mjs';
import {hostingFixture} from '../../scripts/ci-hosting-fixture.mjs';

const fixture=JSON.parse(readFileSync('.ci-connected.json','utf8'));
assert.equal(fixture.project,process.env.COMPOSE_PROJECT_NAME);assert.match(fixture.project,/^getlancer-ci-[0-9]+$/);
const env=readEnvironment(new URL('../../.env',import.meta.url)) as Record<string,string>;
assert.equal(env.ADMIN_EMAIL,'ci-admin@example.test');
function totp(){const bits=[...env.ADMIN_TOTP_SECRET].map(c=>'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'.indexOf(c).toString(2).padStart(5,'0')).join(''),secret=Buffer.from(bits.match(/.{8}/g)!.map(b=>parseInt(b,2))),counter=Buffer.alloc(8);counter.writeBigUInt64BE(BigInt(Math.floor(Date.now()/30000)));const h=createHmac('sha1',secret).update(counter).digest(),offset=h[h.length-1]&15;return String((h.readUInt32BE(offset)&0x7fffffff)%1000000).padStart(6,'0');}
async function login(page:Page,admin=false){const account=admin?{email:env.ADMIN_EMAIL,password:env.ADMIN_BOOTSTRAP_PASSWORD}:fixture.accounts['ci-builder@example.test'];await page.goto('/login');await page.getByLabel('Email address',{exact:true}).fill(account.email);await page.getByLabel('Password',{exact:true}).fill(account.password);await page.getByRole('button',{name:'Log in',exact:true}).click();if(admin){await page.getByLabel('Authenticator code',{exact:true}).fill(totp());await page.getByRole('button',{name:'Verify and log in',exact:true}).click();}await expect(page).toHaveURL(/\/workspace$/);}
const call=(page:Page,path:string,body?:unknown)=>page.request.fetch('/api/v1'+path,{method:body?'POST':'GET',data:body,headers:{Origin:'http://localhost:3000','X-Requested-With':'getlancer'}});
async function reflow(page:Page){expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(page.viewportSize()!.width+1);}

test('real built frontend ZIP, MFA review, isolated JavaScript and withdrawal work together',async({page,browser},info)=>{
 const options={viewport:info.project.use.viewport,isMobile:info.project.use.isMobile,hasTouch:info.project.use.hasTouch,baseURL:'http://localhost:3000'};
 const adminContext=await browser.newContext(options),demoContext=await browser.newContext(options),admin=await adminContext.newPage(),demo=await demoContext.newPage(),bytes=hostingFixture(),title='CI Browser Hosting '+info.project.name;
 const errors:string[]=[];for(const p of [page,admin])p.on('pageerror',e=>errors.push(e.message));
 try{
  await login(page);await page.goto('/workspace/hosting');await expect(page.getByRole('button',{name:'Upload frontend ZIP',exact:true})).toBeEnabled();
  await page.getByRole('button',{name:'Upload frontend ZIP',exact:true}).click();let dialog=page.getByRole('dialog');
  await dialog.getByLabel('Current public proof',{exact:true}).selectOption(fixture.hosting.productId);
  await dialog.getByLabel('Frontend title',{exact:true}).fill(title);await dialog.getByLabel('Package version',{exact:true}).fill('browser-'+info.project.name);
  await dialog.getByLabel('Built frontend ZIP',{exact:true}).setInputFiles({name:'frontend.zip',mimeType:'application/zip',buffer:bytes});await dialog.getByRole('checkbox').check();
  await dialog.getByRole('button',{name:'Upload immutable package',exact:true}).click();await expect(dialog).not.toBeVisible();await expect(page.getByRole('heading',{name:title,exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Review rights & submit',exact:true}).click();dialog=page.getByRole('dialog');await dialog.getByRole('checkbox').check();await dialog.getByRole('button',{name:'Submit package for review',exact:true}).click();await expect(dialog).not.toBeVisible();
  const record=(await (await call(page,'/me/hosting')).json()).items.find((item:any)=>item.title===title);expect(record.status).toBe('PENDING');
  await login(admin,true);await admin.goto('/workspace');await admin.getByText('Hosted frontend review & recovery',{exact:true}).click();
  const card=admin.locator('.hosting-operator-list [data-slot="card"]').filter({has:admin.getByRole('heading',{name:title,exact:true})});
  await expect(card).toBeVisible();await expect(card.getByRole('button',{name:'Review package & rights',exact:true})).toBeDisabled();
  const downloadEvent=admin.waitForEvent('download');await card.getByRole('button',{name:'Download actual ZIP',exact:true}).click();const download=await downloadEvent;assert.deepEqual(readFileSync((await download.path())!),bytes);
  await card.getByRole('button',{name:'Review package & rights',exact:true}).click();dialog=admin.getByRole('dialog');await dialog.getByLabel('Hosting review decision',{exact:true}).selectOption('APPROVE');await dialog.getByLabel('Hosting evidence & decision reason',{exact:true}).fill('CI reviewed actual immutable static files and the synthetic package rights.');for(const checkbox of await dialog.getByRole('checkbox').all())await checkbox.check();await dialog.getByRole('button',{name:'Record operator decision',exact:true}).click();await expect(dialog).not.toBeVisible();await reflow(admin);
  await page.getByRole('button',{name:'Refresh status',exact:true}).click();await expect(page.getByRole('button',{name:'Publish reviewed frontend',exact:true})).toBeEnabled();await page.getByRole('button',{name:'Publish reviewed frontend',exact:true}).click();
  const link=page.getByRole('link',{name:'Open hosted demo',exact:true});await expect(link).toBeVisible();const url=await link.getAttribute('href');assert.ok(url);expect(new URL(url).hostname).toMatch(/^[0-9a-f-]+\.demo\.localhost$/);expect(new URL(url).origin).not.toBe('http://localhost:3000');expect(await link.getAttribute('rel')).toContain('noopener');await reflow(page);
  const response=await demo.goto(url);expect(response?.status()).toBe(200);expect(response?.headers()['content-security-policy']).toContain("connect-src 'self'");
  await expect(demo.getByRole('heading',{name:'CI isolated frontend'})).toBeVisible();await demo.getByRole('button',{name:'Count: 0',exact:true}).click();await expect(demo.getByRole('button',{name:'Count: 1',exact:true})).toBeVisible();await reflow(demo);
  expect(await demo.evaluate(async()=>{try{await fetch('https://example.com/blocked-by-demo-csp');return false;}catch{return true;}})).toBe(true);
  await page.screenshot({path:info.outputPath('connected-hosting.png'),fullPage:true});await demo.screenshot({path:info.outputPath('isolated-demo.png'),fullPage:true});
  await page.getByRole('button',{name:'Withdraw frontend',exact:true}).click();dialog=page.getByRole('dialog');await dialog.getByRole('button',{name:'Confirm withdrawal',exact:true}).click();await expect(dialog).not.toBeVisible();await expect(page.getByText('Withdrawal completed',{exact:true})).toBeVisible();await expect(link).toHaveCount(0);
  expect((await demo.request.get(url)).status()).not.toBe(200);expect((await (await call(page,'/me/hosting/'+record.id)).json()).deploymentState).toBe('DELETED');
  expect((await call(page,'/auth/logout',{})).ok()).toBeTruthy();await page.getByRole('button',{name:'Refresh status',exact:true}).click();await expect(page.getByRole('heading',{name:'Connect to your hosting workspace',exact:true})).toBeVisible();await expect(page.getByRole('heading',{name:title,exact:true})).toHaveCount(0);expect(errors).toEqual([]);
 }finally{await adminContext.close();await demoContext.close();}
});
