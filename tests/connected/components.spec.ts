import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createHmac} from 'node:crypto';
import {test,expect,type Page} from './test';
import {readEnvironment} from '../../scripts/local-config.mjs';

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

test('connected component review, free preview and college privacy use the real API',async({page,browser},info)=>{
 const options={viewport:info.project.use.viewport,isMobile:info.project.use.isMobile,hasTouch:info.project.use.hasTouch,baseURL:'http://localhost:3000'};
 const sellerContext=await browser.newContext(options),adminContext=await browser.newContext(options);
 const seller=await sellerContext.newPage(),admin=await adminContext.newPage(),title='CI component '+info.project.name;
 const errors:string[]=[];for(const p of [page,seller,admin])p.on('pageerror',e=>errors.push(e.message));
 try{
  await login(seller,'builder');await seller.goto('/workspace/components');await expect(seller.getByRole('button',{name:'Review price & buy one slot',exact:true})).toBeDisabled();
  await seller.getByRole('button',{name:'New component',exact:true}).click();const dialog=seller.getByRole('dialog');await dialog.getByLabel('Base component',{exact:true}).selectOption('portfolio-card');await dialog.getByLabel('Component title',{exact:true}).fill(title);await dialog.getByLabel('Summary',{exact:true}).fill('An original curated recipe selection for CI browser verification.');await dialog.getByLabel('Your contribution & intended use',{exact:true}).fill('I selected the MIT recipe to demonstrate a portfolio use case with attribution.');await dialog.getByRole('checkbox').check();await dialog.getByRole('button',{name:'Save component draft',exact:true}).click();await expect(dialog).not.toBeVisible();await expect(seller.getByRole('button',{name:'New component',exact:true})).toBeFocused();
  const entry=seller.locator('article').filter({has:seller.getByRole('heading',{name:title,exact:true})});await entry.getByRole('button',{name:'Submit for review',exact:true}).click();await expect(entry.getByText('pending',{exact:true})).toBeVisible();await reflow(seller);
  const own=await (await call(seller,'/me/components')).json(),component=own.items.find((c:any)=>c.title===title);expect(component).toBeTruthy();expect((await call(page,'/components/'+component.slug)).status()).toBe(404);
  await login(admin,'admin');await admin.goto('/workspace/components');const review=admin.locator('.kit-admin-list article').filter({has:admin.getByRole('heading',{name:title+' · pending',exact:true})});await expect(review).toBeVisible();await review.getByLabel('Component decision',{exact:true}).selectOption('APPROVE');await review.getByLabel('Review reason',{exact:true}).fill('Verified original source, clear contribution and the current component revision.');await review.getByRole('button',{name:'Record component review',exact:true}).click();await expect(admin.getByText('Review decision recorded.',{exact:true})).toBeVisible();await reflow(admin);
  await page.goto('/components/'+component.slug);await expect(page.getByRole('heading',{name:title,exact:true})).toBeVisible();const frame=page.frameLocator('.kit-preview-stage iframe');await frame.getByRole('button',{name:'Save project',exact:true}).click();await expect(frame.getByRole('button',{name:'Saved ✓',exact:true})).toHaveAttribute('aria-pressed','true');const downloaded=page.waitForEvent('download');await page.getByRole('button',{name:'Download source',exact:true}).click();expect((await downloaded).suggestedFilename()).toContain('1.0.0.zip');await reflow(page);
  const owned=await (await call(seller,'/developer/products')).json(),product=owned.items.find((p:any)=>p.approvalStatus==='APPROVED'&&p.lifecycleStatus==='ACTIVE'&&p.visibility==='PUBLIC');expect(product).toBeTruthy();await seller.goto('/workspace/college-projects');await seller.getByRole('button',{name:'Add college context',exact:true}).click();await seller.getByLabel('Existing project showcase',{exact:true}).selectOption(product.id);await seller.getByLabel('Coding language / framework',{exact:true}).fill('TypeScript / SQL');await seller.getByLabel('Problem you solved',{exact:true}).fill('Explain an original college workflow using a reproducible project.');await seller.getByLabel('Outcome & supporting evidence',{exact:true}).fill('A documented demonstration with clearly stated limitations.');await seller.getByLabel('Prerequisites / local setup',{exact:true}).fill('TypeScript and SQL fundamentals with the documented setup.');await seller.getByLabel('Your individual contribution',{exact:true}).fill('I implemented the data model and user interface with original contribution.');await seller.getByRole('checkbox',{name:/I have permission/}).check();await seller.getByRole('button',{name:'Save context for review',exact:true}).click();await expect(seller.getByText('TypeScript / SQL · Academic details private',{exact:true})).toBeVisible();await reflow(seller);
  await admin.reload();const college=admin.locator('.kit-admin-list article').filter({has:admin.getByRole('heading',{name:product.title+' · College context',exact:true})});await college.getByLabel('College metadata decision',{exact:true}).selectOption('APPROVED');await college.getByLabel('College review reason',{exact:true}).fill('Verified independent educational context, permission and private academic details.');await college.getByRole('button',{name:'Record college review',exact:true}).click();await expect(admin.getByText('Review decision recorded.',{exact:true})).toBeVisible();const publicProject=await (await call(page,'/college-projects/'+product.slug)).json();expect(publicProject.education).not.toHaveProperty('institution');expect(publicProject.education.shareAcademicDetails).toBe(false);expect(errors).toEqual([]);
 }finally{await sellerContext.close();await adminContext.close();}
});
