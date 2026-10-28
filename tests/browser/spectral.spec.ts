import {test,expect,type Page} from '@playwright/test';

async function fits(page:Page){await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(page.viewportSize()!.width+1);}

test('Workboard discovery retains genuine project links, search and brand',async({page},info)=>{
 const errors:string[]=[];page.on('pageerror',error=>errors.push(error.message));
 await page.goto('/');
 await expect(page.getByRole('heading',{name:'Discover software. Meet its builder.'})).toBeVisible();
 await expect(page.locator('.featured-board .work-feature')).toHaveCount(3);
 await expect(page.locator('.site-header img')).toHaveAttribute('src','/brand/getlancer-logo.svg');
 await expect(page.locator('.hero-actions a').first()).toBeVisible();
 await expect(page.locator('.market-sample')).toContainText('Projects are illustrative');
 await fits(page);await page.screenshot({path:info.outputPath('workboard-home.png'),fullPage:true});
 const project=page.getByRole('link',{name:'Explore Stockroom',exact:true});
 await project.focus();await expect(project).toBeFocused();await project.press('Enter');
 await expect(page.getByRole('heading',{name:'Stockroom',exact:true})).toBeVisible();
 await page.goto('/');await project.click();await expect(page).toHaveURL(/\/products\/stockroom$/);
 await page.goto('/');await page.getByRole('textbox',{name:'Search projects',exact:true}).fill('Stockroom');
 await page.locator('.market-search').getByRole('button',{name:'Search',exact:true}).click();
 await expect(page).toHaveURL(/q=Stockroom/);await expect(page.locator('.demo-gallery .project')).toHaveCount(1);await fits(page);
 await page.getByRole('link',{name:'Start a project request',exact:true}).click();
 await expect(page.getByRole('button',{name:'New project request',exact:true})).toBeVisible();
 expect(errors).toEqual([]);
});

test('featured links settle and reduced motion cancels every decorative animation',async({page})=>{
 await page.goto('/');
 const project=page.getByRole('link',{name:'Explore Stockroom',exact:true});
 await project.focus();await expect(project).toBeFocused();
 await page.evaluate(async()=>{await Promise.all(document.getAnimations().filter(a=>Number.isFinite(a.effect?.getComputedTiming().endTime)).map(a=>a.finished.catch(()=>{})));});
 const before=await project.boundingBox();await page.waitForTimeout(200);expect(await project.boundingBox()).toEqual(before);
 await page.emulateMedia({reducedMotion:'reduce'});await page.reload();
 expect(await page.locator('main').evaluate(node=>node.getAnimations({subtree:true}).filter(a=>a.playState==='running').length)).toBe(0);
 await fits(page);
});

test('public details and auth reflow with safe external links',async({page},info)=>{
 for(const route of ['/login','/signup','/products/stockroom','/builders/leah-morgan','/teams','/templates','/report']){
  await page.goto(route);await expect(page.locator('main:not([aria-busy="true"]):visible')).toHaveCount(1);await fits(page);
  const links=await page.locator('a[target="_blank"]').evaluateAll(nodes=>nodes.map(node=>({href:node.getAttribute('href'),rel:node.getAttribute('rel')})));
  for(const link of links){expect(link.href).toMatch(/^https:\/\//);expect(link.rel).toContain('noopener');expect(link.rel).toContain('noreferrer');}
  if(route==='/login'){await expect(page.getByLabel('Email address',{exact:true})).toBeVisible();await page.screenshot({path:info.outputPath('workboard-login.png'),fullPage:true});}
 }
});

test('phone menu, scroll search and empty-results recovery stay reachable at 320px',async({page})=>{
 await page.setViewportSize({width:320,height:812});await page.goto('/');await fits(page);
 const menu=page.getByRole('button',{name:'Open navigation menu',exact:true});
 await menu.click();await expect(page.getByRole('menu')).toBeVisible();await page.keyboard.press('Escape');
 await expect(page.getByRole('menu')).toBeHidden();await expect(menu).toBeFocused();
 await page.evaluate(()=>{const search=document.querySelector('.market-search')!;window.scrollTo({top:search.getBoundingClientRect().bottom+window.scrollY+120,behavior:'instant'});});
 const search=page.getByRole('textbox',{name:'Search projects while browsing'});await expect(search).toBeVisible();
 await search.fill('no-such-workboard-project');await page.getByRole('button',{name:'Submit project search',exact:true}).click();
 await expect(page.getByRole('heading',{name:'We couldn’t find an exact match.'})).toBeVisible();
 await page.getByRole('button',{name:/Clear search and filters/}).click();await expect(page.locator('.demo-gallery .project')).toHaveCount(6);await fits(page);
});

test('account fields stay comfortable on short laptops, phones and 200 percent text',async({page},info)=>{
 test.skip(info.project.name!=='desktop','The desktop project covers the complete auth viewport matrix.');
 for(const [width,height] of [[1440,1000],[1366,650],[1024,768],[768,1024],[720,900],[390,844],[320,812]]){
  await page.setViewportSize({width,height});
  for(const route of ['/login','/signup']){
   await page.goto(route);await expect(page.locator('.auth-connection')).toContainText('Sign-in is unavailable');await fits(page);
   const logo=page.locator('.icy-logo img');expect(await logo.evaluate((node:HTMLImageElement)=>node.complete&&node.naturalWidth>0)).toBe(true);
   for(const input of await page.locator('.icy-input input').all()){
    expect(await input.evaluate(node=>parseFloat(getComputedStyle(node).fontSize))).toBeGreaterThanOrEqual(16);
    expect((await input.boundingBox())!.height).toBeGreaterThanOrEqual(44);
   }
   const form=await page.locator('.icy-form-content').boundingBox();expect(form!.width).toBeGreaterThanOrEqual(250);expect(form!.width).toBeLessThanOrEqual(360);
   if(width>760)await expect(page.locator('.auth-work-preview')).toBeVisible();else await expect(page.locator('.auth-work-preview')).toBeHidden();
   const password=page.getByLabel('Password',{exact:true});await password.fill('layout-check-123');
   await page.getByRole('button',{name:'Show password',exact:true}).click();await expect(password).toHaveAttribute('type','text');
   await page.getByRole('button',{name:'Hide password',exact:true}).click();await expect(password).toHaveAttribute('type','password');
   const submit=page.getByRole('button',{name:route==='/signup'?'Create account':'Log in',exact:true});await submit.scrollIntoViewIfNeeded();await expect(submit).toBeVisible();
  }
 }
 await page.addStyleTag({content:'html{font-size:200%}'});await fits(page);
});

test('workspace navigation folds without losing any destination or focus',async({page})=>{
 await page.goto('/preview/business');
 const nav=page.locator('.studio-navigation'),summary=page.locator('.studio-navigation-toggle');
 if(page.viewportSize()!.width<=1000){
  await expect(nav).not.toHaveAttribute('open','');
  await summary.focus();await summary.press('Enter');await expect(nav).toHaveAttribute('open','');
  const links=page.locator('.studio-rail nav a');expect(await links.count()).toBeGreaterThan(10);
  await links.last().focus();await expect(links.last()).toBeFocused();
  await summary.focus();await summary.press('Enter');await expect(nav).not.toHaveAttribute('open','');
  await expect(page.getByRole('button',{name:'New project request',exact:true})).toBeVisible();
 }else await expect(page.locator('.studio-rail')).toBeVisible();
 await fits(page);
});

test('provider progress fits a narrow form and a failed start recovers',async({page})=>{
 await page.setViewportSize({width:320,height:812});
 await page.route('**/api/v1/auth/providers',route=>route.fulfill({json:{google:true,github:true}}));
 let release:()=>void=()=>{};const started=new Promise<void>(resolve=>{release=resolve});
 await page.route('**/api/v1/auth/google/start',async route=>{await started;await route.fulfill({status:503,json:{error:{message:'Please try again.',code:'UNAVAILABLE'}}});});
 await page.goto('/login');const google=page.getByRole('button',{name:'Continue with Google',exact:true});await expect(google).toBeEnabled();await google.click();
 const progress=page.getByRole('button',{name:'Opening sign-in with Google',exact:true});await expect(progress).toContainText('Opening…');
 expect(await progress.evaluate(node=>node.scrollWidth<=node.clientWidth+1)).toBe(true);await fits(page);
 release();await expect(page.getByRole('alert')).toContainText('Please try again.');await expect(google).toBeEnabled();
});
