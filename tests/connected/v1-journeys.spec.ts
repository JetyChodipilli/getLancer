import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {setTimeout as delay} from 'node:timers/promises';
import type {Browser,Locator,TestInfo} from '@playwright/test';
import {test,expect,type Page} from './test';
import {readEnvironment} from '../../scripts/local-config.mjs';
import {ciV1Totp} from '../../scripts/ci-v1-fixture.mjs';

type Actor={email:string;password:string;displayName:string};
type Product={id:string;slug:string;title:string;builderSlug:string};
type Initial={builder:Actor;client?:Actor;products:Product[];mailIds:string[];inquiryId?:string;description?:string};
type Journey='onboarding'|'publication'|'slots'|'conversion'|'outcome'|'moderation';

// Loaded only when a test executes: --list is discovery evidence, never service acceptance.
function initial(info:TestInfo,journey:Journey):Initial{
 const fixture=JSON.parse(readFileSync('.ci-connected.json','utf8'));
 assert.equal(process.env.CI,'true');assert.equal(fixture.project,process.env.COMPOSE_PROJECT_NAME);
 assert.match(fixture.project,/^getlancer-ci-[0-9]+$/);assert.equal(info.config.workers,1);
 assert.equal(fixture.v1?.version,1,'Run node scripts/ci-v1-fixture.mjs after smoke-docker.mjs.');
 const state=fixture.v1.devices[info.project.name]?.[journey];assert.ok(state,'Require an isolated actor for this viewport and journey.');return state;
}
async function actorPage(browser:Browser,info:TestInfo){
 const context=await browser.newContext({baseURL:'http://localhost:3000',viewport:info.project.use.viewport,isMobile:info.project.use.isMobile,hasTouch:info.project.use.hasTouch});
 return {context,page:await context.newPage()};
}
async function mutation(page:Page,path:string,action:()=>Promise<unknown>,status=200,method='POST'){
 const [response]=await Promise.all([
  page.waitForResponse(r=>new URL(r.url()).pathname==='/api/v1'+path&&r.request().method()===method),action(),
 ]);
 expect(response.status(),`${method} ${path} caused by the visible UI`).toBe(status);return response;
}
async function login(page:Page,actor:Actor|'admin'){
 const env=actor==='admin'?readEnvironment(new URL('../../.env',import.meta.url)) as Record<string,string>:undefined;
 if(env)assert.equal(env.ADMIN_EMAIL,'ci-admin@example.test');
 const account=actor==='admin'?{email:env!.ADMIN_EMAIL,password:env!.ADMIN_BOOTSTRAP_PASSWORD}:actor;
 await page.goto('/login');
 await page.getByLabel('Email address',{exact:true}).fill(account.email);
 await page.getByLabel('Password',{exact:true}).fill(account.password);
 await expect(page.getByRole('button',{name:'Log in',exact:true})).toBeEnabled();
 await page.getByRole('button',{name:'Log in',exact:true}).click();
 if(actor==='admin'){
  await page.getByLabel('Authenticator code',{exact:true}).fill(ciV1Totp(env!.ADMIN_TOTP_SECRET));
  await page.getByRole('button',{name:'Verify and log in',exact:true}).click();
 }
 await expect(page).toHaveURL(/\/workspace$/);
 await expect(page.getByRole('tab',{name:'Profile',exact:true})).toBeVisible();
 await expect(page.getByText('Sample data only',{exact:true})).toHaveCount(0);
}
async function workspace(page:Page,tab:'showcases'|'inquiries'|'requests'|'profile'){
 await page.goto('/workspace?tab='+tab);
 await expect(page.getByRole('tab',{name:{showcases:'Showcases',requests:'My requests',profile:'Profile',inquiries:/^Inquiries \(/}[tab],exact:tab!=='inquiries'})).toHaveAttribute('aria-selected','true');
}
async function choose(page:Page,scope:Locator,label:string,option:string){
 await scope.getByRole('combobox',{name:label,exact:true}).click();
 await page.getByRole('option',{name:option,exact:true}).click();
}
function card(page:Page,title:string){return page.locator('article.panel').filter({has:page.getByRole('heading',{name:title,exact:true})});}
async function search(page:Page,title:string){
 await page.goto('/');
 await page.getByLabel('Search projects',{exact:true}).fill(title);
 await page.locator('form.market-search').getByRole('button',{name:'Search',exact:true}).click();
 await expect(page).toHaveURL(new RegExp('[?&]q='+encodeURIComponent(title).replaceAll('%20','\\+')));
 await expect(page.getByLabel('Project demos',{exact:true})).toHaveAttribute('aria-busy','false');
 await expect(page.getByText('Projects are illustrative.',{exact:false})).toHaveCount(0);
}
async function reflow(page:Page){
 expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(page.viewportSize()!.width+1);
}
async function mail(email:string,consumed:Set<string>,pattern:RegExp){
 const deadline=Date.now()+60000;
 while(Date.now()<deadline){
  const listing=await fetch('http://localhost:8025/api/v1/messages?limit=500',{signal:AbortSignal.timeout(5000)});
  expect(listing.ok,'Read the delivered SMTP inbox, not database tokens/outbox content.').toBeTruthy();
  for(const entry of (await listing.json()).messages){
   if(consumed.has(entry.ID)||!entry.To.some((to:{Address:string})=>to.Address===email))continue;
   const response=await fetch('http://localhost:8025/api/v1/message/'+encodeURIComponent(entry.ID),{signal:AbortSignal.timeout(5000)});
   expect(response.ok).toBeTruthy();const message=await response.json();
   if(pattern.test(message.Text||'')){consumed.add(entry.ID);return String(message.Text);}
  }
  await delay(1000);
 }
 throw Error('Expected SMTP message was not delivered to the synthetic V1 actor.');
}
async function confirmation(page:Page,email:string,consumed:Set<string>,heading:string){
 const text=await mail(email,consumed,/http:\/\/localhost:3000\/confirm#token=/);
 const link=text.match(/http:\/\/localhost:3000\/confirm#token=[^\s]+/)?.[0];assert.ok(link);
 await page.goto(link);
 await expect(page.getByRole('heading',{name:heading,exact:true})).toBeVisible();
 await expect(page).toHaveURL(/\/confirm$/); // The app removes the bearer token from the address bar.
 await mutation(page,'/auth/confirm',()=>page.getByRole('button',{name:'Confirm',exact:true}).click());
 await expect(page.getByRole('status')).toHaveText('Your response has been recorded.');
}
async function approve(admin:Page,kind:'profile'|'project',title:string,reason:string){
 const row=admin.locator('.row').filter({has:admin.getByRole('heading',{name:title,exact:true})});
 await row.getByRole('button',{name:kind==='profile'?'Review profile':'Review project',exact:true}).click();
 const dialog=admin.getByRole('dialog');
 await dialog.getByLabel('Reason',{exact:true}).fill(reason);
 const [response]=await Promise.all([
  admin.waitForResponse(r=>new RegExp('/api/v1/admin/'+(kind==='profile'?'profiles':'products')+'/[^/]+/approve$').test(new URL(r.url()).pathname)&&r.request().method()==='POST'),
  dialog.getByRole('button',{name:'Approve',exact:true}).click(),
 ]);
 expect(response.status()).toBe(200);await expect(dialog).not.toBeVisible();
 await expect(row).toHaveCount(0);
}
function errors(page:Page){const found:string[]=[];page.on('pageerror',error=>found.push(error.message));return found;}

test('E2E-01 developer signs up, verifies delivered email, submits a profile and receives admin approval',async({page,browser},info)=>{
 const state=initial(info,'onboarding'),actor=state.builder,consumed=new Set(state.mailIds),failures=errors(page);
 const admin=await actorPage(browser,info);
 try{
  await page.goto('/signup');
  await page.getByLabel('Full name',{exact:true}).fill(actor.displayName);
  await page.getByLabel('Email address',{exact:true}).fill(actor.email);
  await page.getByLabel('Password',{exact:true}).fill(actor.password);
  await mutation(page,'/auth/signup',()=>page.getByRole('button',{name:'Create account',exact:true}).click(),201);
  await expect(page.getByRole('heading',{name:'Check your inbox',exact:true})).toBeVisible();
  await confirmation(page,actor.email,consumed,'Confirm your email');
  await login(page,actor);await workspace(page,'showcases');
  await expect(page.getByRole('button',{name:'New draft',exact:true})).toBeDisabled();
  await page.getByRole('tab',{name:'Profile',exact:true}).click();
  await page.getByLabel('Display name',{exact:true}).fill(actor.displayName);
  await page.getByLabel('Professional headline',{exact:true}).fill('Accessible inventory application builder');
  await page.getByLabel('About your work',{exact:true}).fill('I build accessible stock tracking systems and reliable order workflows for independent businesses.');
  await page.getByLabel('Primary technologies',{exact:true}).fill('React');
  await page.getByLabel('Business categories',{exact:true}).fill('Inventory');
  await mutation(page,'/developer/profile',()=>page.getByRole('button',{name:'Save profile',exact:true}).click(),200,'PUT');
  await expect(page.getByRole('status').filter({hasText:'Profile saved.'})).toBeVisible();
  await mutation(page,'/developer/profile/submit',()=>page.getByRole('button',{name:'Submit profile for approval',exact:true}).click());
  await expect(page.getByRole('button',{name:'Submit profile for approval',exact:true})).toHaveCount(0);
  await expect(page.getByText(/Profile status: Profile Pending/)).toBeVisible();
  await login(admin.page,'admin');await approve(admin.page,'profile',actor.displayName,'V1 browser review: complete synthetic builder profile and verified email.');
  await workspace(page,'showcases');
  await expect(page.getByRole('button',{name:'New draft',exact:true})).toBeEnabled();
  await expect(page.getByText(/Profile status:/)).toHaveCount(0);
  await reflow(page);await page.screenshot({path:info.outputPath('v1-onboarding-approved.png'),fullPage:true});
  expect(failures).toEqual([]);
 }finally{await admin.context.close();}
});

test('E2E-02 publishes a new draft with a real screenshot, live URL, review and activation into discovery',async({page,browser},info)=>{
 const state=initial(info,'publication'),title=state.builder.displayName+' showcase',failures=errors(page);
 const admin=await actorPage(browser,info),visitor=await actorPage(browser,info);
 try{
  await login(page,state.builder);await workspace(page,'showcases');
  await page.getByRole('button',{name:'New draft',exact:true}).click();
  const dialog=page.getByRole('dialog');
  await dialog.getByLabel('Title',{exact:true}).fill(title);
  await dialog.getByLabel('Summary',{exact:true}).fill('Accessible stock and order management for small independent shops.');
  await dialog.getByLabel('Description',{exact:true}).fill('A working stock tracking showcase with product search, order history and accessible review controls.');
  await dialog.getByLabel('Contribution',{exact:true}).fill('Designed the schema and implemented the stock review and order history workflows.');
  await dialog.getByLabel('Live Url (optional)',{exact:true}).fill('https://example.com/getlancer-v1-demo');
  await choose(page,dialog,'Choose category','Inventory');
  await dialog.getByRole('checkbox',{name:'I have permission to showcase this work.',exact:true}).check();
  const created=await (await mutation(page,'/developer/products',()=>dialog.getByRole('button',{name:'Save draft',exact:true}).click(),201)).json();
  assert.match(created.id,/^[a-f0-9-]{36}$/);await expect(dialog).not.toBeVisible();
  await page.getByRole('tab',{name:'Drafts & review',exact:true}).click();
  await card(page,title).getByRole('button',{name:'Edit showcase',exact:true}).click();
  const png=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAEElEQVR4nGNQSjsDRAwQCgAilgVRqmC7UQAAAABJRU5ErkJggg==','base64');
  const [put]=await Promise.all([
   page.waitForResponse(r=>new URL(r.url()).origin==='http://localhost:9000'&&r.request().method()==='PUT'),
   mutation(page,'/developer/products/'+created.id+'/media/complete',()=>dialog.getByLabel('Add proof image (PNG or JPEG, up to 5 MB)',{exact:true}).setInputFiles({name:'v1-stock-proof.png',mimeType:'image/png',buffer:png})),
  ]);
  expect(put.ok(),'The browser must upload the actual bytes to signed private S3 storage.').toBeTruthy();
  await expect(dialog.getByRole('status').filter({hasText:'Uploaded. Add a description and save.'})).toBeVisible();
  await dialog.getByLabel('Image 1 description',{exact:true}).fill('Synthetic inventory stock screen submitted through the real browser upload.');
  await mutation(page,'/developer/products/'+created.id+'/media',()=>dialog.getByRole('button',{name:'Save image order and descriptions',exact:true}).click(),200,'PATCH');
  await dialog.getByRole('button',{name:'Close',exact:true}).click();
  await mutation(page,'/developer/products/'+created.id+'/submit',()=>card(page,title).getByRole('button',{name:'Submit for review',exact:true}).click());
  await expect(card(page,title)).toContainText('Pending Review');
  await search(visitor.page,title);
  await expect(visitor.page.getByLabel('Project demos',{exact:true}).getByRole('heading',{name:title,exact:true})).toHaveCount(0);
  await login(admin.page,'admin');
  const submitted=admin.page.locator('.row').filter({has:admin.page.getByRole('heading',{name:title,exact:true})});
  await submitted.getByRole('button',{name:'Review project',exact:true}).click();
  const review=admin.page.getByRole('dialog'),proof=review.getByRole('img',{name:'Synthetic inventory stock screen submitted through the real browser upload.',exact:true});
  await expect(proof).toBeVisible();await expect(proof).toHaveJSProperty('naturalWidth',2);
  await review.getByLabel('Reason',{exact:true}).fill('V1 browser inspection of screenshot bytes, contribution, public URL and ownership consent.');
  await mutation(admin.page,'/admin/products/'+created.id+'/approve',()=>review.getByRole('button',{name:'Approve',exact:true}).click());
  await expect(review).not.toBeVisible();
  // Approval activates automatically when free capacity exists. Also exercise the explicit owner action.
  await workspace(page,'showcases');await expect(card(page,title)).toContainText('Approved · Active');
  await mutation(page,'/developer/products/'+created.id+'/archive',()=>card(page,title).getByRole('button',{name:'Archive',exact:true}).click());
  await expect(page.getByRole('tab',{name:'Active (0)',exact:true})).toBeVisible();
  await page.getByRole('tab',{name:'Archived',exact:true}).click();
  await mutation(page,'/developer/products/'+created.id+'/activate',()=>card(page,title).getByRole('button',{name:'Activate',exact:true}).click());
  await page.getByRole('tab',{name:'Active (1)',exact:true}).click();await expect(card(page,title)).toBeVisible();
  await search(visitor.page,title);
  await visitor.page.getByLabel('Project demos',{exact:true}).getByRole('heading',{name:title,exact:true}).getByRole('link').click();
  await expect(visitor.page.getByRole('heading',{name:title,exact:true})).toBeVisible();
  await expect(visitor.page.getByRole('img',{name:title+' project screenshot',exact:true})).toHaveJSProperty('naturalWidth',2);
  await expect(visitor.page.getByRole('link',{name:'Open live demo',exact:true})).toHaveAttribute('href','https://example.com/getlancer-v1-demo');
  await reflow(visitor.page);await visitor.page.screenshot({path:info.outputPath('v1-publication.png'),fullPage:true});expect(failures).toEqual([]);
 }finally{await Promise.allSettled([admin.context.close(),visitor.context.close()]);}
});

test('E2E-03 blocks the fourth active showcase with SLOT_LIMIT_REACHED and reuses an archived slot',async({page,browser},info)=>{
 const state=initial(info,'slots'),[first,,,fourth]=state.products,visitor=await actorPage(browser,info),failures=errors(page);
 try{
  await login(page,state.builder);await workspace(page,'showcases');
  await expect(page.getByRole('tab',{name:'Active (3)',exact:true})).toHaveAttribute('aria-selected','true');
  for(const product of state.products.slice(0,3))await expect(card(page,product.title)).toBeVisible();
  await page.getByRole('tab',{name:'Drafts & review',exact:true}).click();
  await expect(card(page,fourth.title)).toContainText('Approved · Draft');
  const blocked=await mutation(page,'/developer/products/'+fourth.id+'/activate',()=>card(page,fourth.title).getByRole('button',{name:'Activate',exact:true}).click(),409);
  expect((await blocked.json()).error.code).toBe('SLOT_LIMIT_REACHED');
  await expect(page.getByRole('alert')).toContainText('All slots for this project category are in use.');
  await expect(card(page,fourth.title)).toContainText('Approved · Draft');
  await page.getByRole('tab',{name:'Active (3)',exact:true}).click();
  await mutation(page,'/developer/products/'+first.id+'/archive',()=>card(page,first.title).getByRole('button',{name:'Archive',exact:true}).click());
  await expect(page.getByRole('tab',{name:'Active (2)',exact:true})).toBeVisible();
  await page.getByRole('tab',{name:'Drafts & review',exact:true}).click();
  await mutation(page,'/developer/products/'+fourth.id+'/activate',()=>card(page,fourth.title).getByRole('button',{name:'Activate',exact:true}).click());
  await page.getByRole('tab',{name:'Active (3)',exact:true}).click();
  await expect(card(page,fourth.title)).toContainText('Approved · Active');await expect(card(page,first.title)).toHaveCount(0);
  await page.reload();await expect(page.getByRole('tab',{name:'Active (3)',exact:true})).toHaveAttribute('aria-selected','true');
  await search(visitor.page,first.title);await expect(visitor.page.getByLabel('Project demos',{exact:true}).locator('article')).toHaveCount(0);
  await search(visitor.page,fourth.title);await expect(visitor.page.getByLabel('Project demos',{exact:true}).getByRole('heading',{name:fourth.title,exact:true})).toBeVisible();
  await reflow(page);await page.screenshot({path:info.outputPath('v1-slot-reused.png'),fullPage:true});expect(failures).toEqual([]);
 }finally{await visitor.context.close();}
});

test('E2E-04 converts public search and a live-demo click into an email-confirmed inquiry and developer notification',async({page,browser},info)=>{
 const state=initial(info,'conversion'),product=state.products[0],client=state.client!,consumed=new Set(state.mailIds),owner=await actorPage(browser,info),failures=errors(page);
 const description='Build a stock approval system with accessible product search and order history for our synthetic shop.';
 try{
  await login(owner.page,state.builder);await workspace(owner.page,'inquiries');
  await expect(owner.page.getByRole('heading',{name:'No qualified inquiries yet.',exact:true})).toBeVisible();
  await search(page,product.title);
  await page.getByLabel('Project demos',{exact:true}).getByRole('heading',{name:product.title,exact:true}).getByRole('link').click();
  await expect(page).toHaveURL('/products/'+product.slug);
  const demo=page.getByRole('link',{name:'Open live demo',exact:true});
  await expect(demo).toHaveAttribute('rel','noopener noreferrer');
  const [popup,request]=await Promise.all([
   page.waitForEvent('popup'),page.context().waitForEvent('request',{predicate:r=>r.url()==='https://example.com/getlancer-v1-demo'}),demo.click(),
  ]);
  expect(request.isNavigationRequest()).toBeTruthy();await popup.close();
  await page.getByRole('link',{name:'Build something similar',exact:true}).click();
  await expect(page.getByRole('heading',{name:'Tell the builder what you need.',exact:true})).toBeVisible();
  await page.getByLabel('Describe your project',{exact:true}).fill(description);
  await choose(page,page.locator('main'),'Choose budget','USD 3K 10K');
  await choose(page,page.locator('main'),'Choose timeline','ONE TO THREE MONTHS');
  await page.getByLabel('Your name',{exact:true}).fill(client.displayName);
  await page.getByLabel('Your email',{exact:true}).fill(client.email);
  await page.getByLabel('Company (optional)',{exact:true}).fill('Synthetic conversion shop');
  const created=await (await mutation(page,'/inquiries',()=>page.getByRole('button',{name:'Send inquiry',exact:true}).click(),201)).json();
  assert.match(created.id,/^[a-f0-9-]{36}$/);
  await expect(page.getByRole('heading',{name:'Inquiry saved. Check your inbox.',exact:true})).toBeVisible();
  await workspace(owner.page,'inquiries');await expect(owner.page.getByText(description,{exact:true})).toHaveCount(0);
  await confirmation(page,client.email,consumed,'Send your inquiry to the builder');
  await mail(state.builder.email,consumed,/A new qualified inquiry is ready\./);
  await workspace(owner.page,'inquiries');
  const inquiry=owner.page.locator('.row').filter({has:owner.page.getByText(description,{exact:true})});
  await expect(inquiry).toContainText('Inquiry Received');await expect(inquiry.getByRole('link',{name:client.email,exact:true})).toHaveAttribute('href','mailto:'+client.email);
  await expect(inquiry.getByRole('button',{name:'Mark responded',exact:true})).toBeVisible();
  await reflow(owner.page);await owner.page.screenshot({path:info.outputPath('v1-qualified-inquiry.png'),fullPage:true});expect(failures).toEqual([]);
 }finally{await owner.context.close();}
});

test('E2E-05 records response, discussion, proposal, client-confirmed hire/completion and an eligible moderated review',async({page,browser},info)=>{
 const state=initial(info,'outcome'),product=state.products[0],client=state.client!,consumed=new Set(state.mailIds),failures=errors(page);
 const buyer=await actorPage(browser,info),admin=await actorPage(browser,info),visitor=await actorPage(browser,info);
 const reviewText='The completed stock approval system matches our agreed scope and provides an accessible order history.';
 try{
  await login(buyer.page,client);await workspace(buyer.page,'requests');
  const request=()=>buyer.page.locator('article.row').filter({has:buyer.page.getByText(state.description!,{exact:true})});
  await request().getByRole('button',{name:'View request',exact:true}).click();
  await expect(buyer.page.getByRole('dialog').getByRole('button',{name:'Submit review',exact:true})).toHaveCount(0);
  await buyer.page.getByRole('dialog').getByRole('button',{name:'Close',exact:true}).click();
  await login(page,state.builder);await workspace(page,'inquiries');
  const inquiry=()=>page.locator('.row').filter({has:page.getByText(state.description!,{exact:true})});
  const advance=async(action:string,button:string,next:string)=>{
   await mutation(page,'/inquiries/'+state.inquiryId+'/'+action,()=>inquiry().getByRole('button',{name:button,exact:true}).click());
   await expect(inquiry()).toContainText(next);
  };
  await advance('responded','Mark responded','Responded');
  await advance('discussion','Start discussion','Discussion');
  await inquiry().getByRole('button',{name:'Mark proposal sent',exact:true}).click();
  await inquiry().getByLabel('Reported proposal value (optional)',{exact:true}).fill('5000');
  await inquiry().getByLabel('Currency',{exact:true}).fill('USD');
  await advance('proposal-sent','Mark proposal sent','Proposal Sent');
  await advance('request-hire-confirmation','Request hire confirmation','Hire Pending Confirmation');
  await confirmation(buyer.page,client.email,consumed,'Confirm your hire');
  await workspace(page,'inquiries');await expect(inquiry()).toContainText('Hired');
  await advance('in-progress','Mark in progress','In Progress');
  await advance('request-completion-confirmation','Request completion confirmation','Completion Pending Confirmation');
  await confirmation(buyer.page,client.email,consumed,'Confirm project completion');
  await workspace(page,'inquiries');await expect(inquiry()).toContainText('Completed');
  await workspace(buyer.page,'requests');
  await request().getByRole('button',{name:'Write a review',exact:true}).click();
  const review=buyer.page.getByRole('dialog');
  await review.getByLabel('Rating (1–5)',{exact:true}).fill('5');
  await review.getByLabel('Your experience',{exact:true}).fill(reviewText);
  await review.getByLabel('Name shown with your review',{exact:true}).selectOption('ANONYMOUS');
  await mutation(buyer.page,'/me/inquiries/'+state.inquiryId+'/review',()=>review.getByRole('button',{name:'Submit review',exact:true}).click());
  await expect(review).not.toBeVisible();await expect(buyer.page.getByRole('status').filter({hasText:'Your review is saved for moderation.'})).toBeVisible();
  await expect(request().getByRole('button',{name:'Write a review',exact:true})).toHaveCount(0);
  await visitor.page.goto('/builders/'+product.builderSlug);await expect(visitor.page.getByText(reviewText,{exact:true})).toHaveCount(0);
  await login(admin.page,'admin');
  const pending=admin.page.locator('.row').filter({has:admin.page.getByText(reviewText,{exact:true})});
  await pending.getByRole('button',{name:'Moderate review',exact:true}).click();
  await admin.page.getByRole('dialog').getByLabel('Reason',{exact:true}).fill('V1 browser moderation: client-confirmed completion, eligible review and anonymous visibility.');
  const [published]=await Promise.all([
   admin.page.waitForResponse(r=>/\/api\/v1\/admin\/reviews\/[^/]+\/publish$/.test(new URL(r.url()).pathname)&&r.request().method()==='POST'),
   admin.page.getByRole('dialog').getByRole('button',{name:'Publish',exact:true}).click(),
  ]);
  expect(published.status()).toBe(200);await expect(admin.page.getByRole('dialog')).not.toBeVisible();
  await visitor.page.reload();
  const publishedReview=visitor.page.locator('#client-reviews article').filter({hasText:reviewText});
  await expect(publishedReview).toContainText('5 / 5 · Verified engagement review');await expect(publishedReview).toContainText('Verified client');
  await expect(publishedReview).not.toContainText(client.displayName);await expect(visitor.page.locator('main')).not.toContainText(client.email);
  await reflow(visitor.page);await visitor.page.screenshot({path:info.outputPath('v1-client-confirmed-review.png'),fullPage:true});expect(failures).toEqual([]);
 }finally{await Promise.allSettled([buyer.context.close(),admin.context.close(),visitor.context.close()]);}
});

test('E2E-06 reports a public showcase, triages it, suspends it and shows the owner the enforcement reason',async({page,browser},info)=>{
 const state=initial(info,'moderation'),product=state.products[0],admin=await actorPage(browser,info),owner=await actorPage(browser,info),failures=errors(page);
 const detail='V1 browser report '+state.builder.displayName+': the public proof needs correction before this showcase is restored.';
 const reason='V1 enforcement '+state.builder.displayName+': replace misleading proof and request a fresh moderation review.';
 try{
  await search(page,product.title);await expect(page.getByLabel('Project demos',{exact:true}).getByRole('heading',{name:product.title,exact:true})).toBeVisible();
  await page.goto('/products/'+product.slug);await page.getByRole('link',{name:'Report this project',exact:true}).click();
  await expect(page.getByLabel('Record ID',{exact:true})).toHaveValue(product.id);
  await choose(page,page.locator('main'),'Reason','MISLEADING CLAIM');
  await page.getByLabel('What happened?',{exact:true}).fill(detail);
  const report=await (await mutation(page,'/reports',()=>page.getByRole('button',{name:'Submit report',exact:true}).click(),201)).json();
  assert.match(report.reference,/^[a-f0-9-]{36}$/);await expect(page.getByText('Your report has been saved for review.',{exact:true})).toBeVisible();
  await login(admin.page,'admin');
  await admin.page.getByText('Report priority and triage',{exact:true}).click();
  const triage=admin.page.locator('article.panel').filter({has:admin.page.getByText(detail,{exact:true})});
  await triage.getByLabel('Priority',{exact:true}).selectOption('HIGH');
  await triage.getByLabel('Review stage',{exact:true}).selectOption('UNDER_REVIEW');
  await triage.getByLabel('Investigation note',{exact:true}).fill('Inspected this synthetic report, current public listing and builder contribution.');
  await mutation(admin.page,'/admin/reports/'+report.reference+'/triage',()=>triage.getByRole('button',{name:'Record triage',exact:true}).click());
  await expect(triage).toContainText('UNDER_REVIEW');
  // Refresh the primary report queue after the independent triage panel saves its decision.
  await admin.page.reload();
  const queue=admin.page.locator('.row').filter({has:admin.page.getByText(detail,{exact:true})});
  await expect(queue).toContainText('Under Review');await queue.getByRole('button',{name:'Review report',exact:true}).click();
  const decision=admin.page.getByRole('dialog');
  await expect(decision.getByText(product.title,{exact:true})).toBeVisible();
  await decision.getByLabel('Action on reported content',{exact:true}).selectOption('SUSPEND');
  await decision.getByLabel('Reason',{exact:true}).fill(reason);
  await mutation(admin.page,'/admin/reports/'+report.reference+'/resolve',()=>decision.getByRole('button',{name:'Resolve',exact:true}).click());
  await expect(decision).not.toBeVisible();
  await search(page,product.title);await expect(page.getByLabel('Project demos',{exact:true}).locator('article')).toHaveCount(0);
  await page.goto('/products/'+product.slug);await expect(page.getByRole('heading',{name:product.title,exact:true})).toHaveCount(0);
  await expect(page.getByRole('heading',{name:'That project isn’t available.',exact:true})).toBeVisible();
  await login(owner.page,state.builder);await workspace(owner.page,'showcases');
  await owner.page.getByRole('tab',{name:'Restricted',exact:true}).click();
  await expect(card(owner.page,product.title)).toContainText(reason);
  await expect(card(owner.page,product.title).getByRole('button',{name:'Edit showcase',exact:true})).toBeDisabled();
  await expect(card(owner.page,product.title).getByRole('link',{name:'Request a moderation review',exact:true})).toHaveAttribute('href','/report?product='+product.id);
  await reflow(owner.page);await owner.page.screenshot({path:info.outputPath('v1-moderation-reason.png'),fullPage:true});expect(failures).toEqual([]);
 }finally{await Promise.allSettled([admin.context.close(),owner.context.close()]);}
});
