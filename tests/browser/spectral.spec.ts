import {test,expect,type Locator,type Page} from '@playwright/test';

async function fits(page:Page){await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(page.viewportSize()!.width+1);}
async function pointerTarget(link:Locator){
 await expect(link).toBeVisible();
 await link.scrollIntoViewIfNeeded();
 return link.evaluate(node=>{
  const box=node.getBoundingClientRect(),point={x:box.x+box.width/2,y:box.y+box.height/2};
  const target=document.elementFromPoint(point.x,point.y);
  if(!target||!node.contains(target))throw new Error('Project link has no unobstructed pointer target.');
  return point;
 });
}
async function noInfiniteMotion(surface:Locator){
 expect(await surface.evaluate(node=>node.getAnimations({subtree:true}).some(animation=>animation.playState==='running'&&animation.effect?.getTiming().iterations===Infinity))).toBe(false);
}

test('Paper & Pixel discovery keeps genuine project navigation, search and legible solid chrome',async({page},info)=>{
 const errors:string[]=[];page.on('pageerror',error=>errors.push(error.message));
 await page.goto('/');await expect(page.getByRole('heading',{name:'The craft behind the code.',exact:true})).toBeVisible();
 const exhibition=page.getByRole('region',{name:'Featured project demos',exact:true});
 await expect(exhibition.locator('.paper-feature')).toHaveCount(3);
 await expect(page.locator('.site-header').getByRole('img',{name:'getLancer',exact:true})).toBeVisible();
 await fits(page);await page.screenshot({path:info.outputPath('paper-home.png'),fullPage:true});
 const project=exhibition.getByRole('link',{name:'Explore Stockroom',exact:true});
 await project.focus();await expect(project).toBeFocused();
 await project.press('Enter');await expect(page).toHaveURL(/\/products\/stockroom$/);await expect(page.getByRole('heading',{level:1,name:'Stockroom',exact:true})).toBeVisible();
 await page.goto('/');
 const pointer=await pointerTarget(project);await page.mouse.click(pointer.x,pointer.y);
 await expect(page).toHaveURL(/\/products\/stockroom$/);await expect(page.getByRole('heading',{level:1,name:'Stockroom',exact:true})).toBeVisible();
 await page.goto('/');await page.getByRole('textbox',{name:'Search projects',exact:true}).fill('Stockroom');await page.locator('.paper-home-search').getByRole('button',{name:'Search',exact:true}).click();
 await expect(page).toHaveURL(/q=Stockroom/);await expect(page.locator('.paper-project-grid .paper-project')).toHaveCount(1);await fits(page);
 await page.getByRole('link',{name:'Start a project request',exact:true}).click();await expect(page.getByRole('button',{name:'New project request',exact:true})).toBeVisible();
 expect(errors).toEqual([]);
});

test('auth resources stay still during form use and honour reduced motion',async({page})=>{
 for(const reducedMotion of ['no-preference','reduce'] as const){
  await page.emulateMedia({reducedMotion});await page.goto('/login');
  const resources=page.getByRole('navigation',{name:'Helpful resources'});
  await expect(page.getByLabel('Email address',{exact:true})).toBeVisible();
  await expect(resources).toBeVisible();
  await expect(resources.locator('img, video, iframe, [role=img]')).toHaveCount(0);
  const start=await resources.boundingBox();
  await page.getByLabel('Email address',{exact:true}).fill('client@example.test');
  await expect(resources).toHaveCSS('transform','none');
  await noInfiniteMotion(resources);
  expect((await resources.boundingBox())!.height).toBe(start!.height);
 }
 await fits(page);
});

test('featured previews remain reachable during hover, keyboard focus and reduced motion',async({page})=>{
 for(const reducedMotion of ['no-preference','reduce'] as const){
  await page.emulateMedia({reducedMotion});await page.goto('/');
  const exhibition=page.getByRole('region',{name:'Featured project demos',exact:true});
  await expect(exhibition.locator('.paper-feature')).toHaveCount(3);
  const project=exhibition.getByRole('link',{name:'Explore Stockroom',exact:true});
  await project.hover();await expect.poll(()=>project.evaluate(node=>node.matches(':hover'))).toBe(true);
  await project.focus();await expect(project).toBeFocused();
  await noInfiniteMotion(exhibition);
  const box=await project.boundingBox();expect(box!.width).toBeGreaterThan(100);expect(box!.height).toBeGreaterThan(100);
  if(reducedMotion==='reduce')await expect(project).toHaveCSS('transition-duration','0s');
  await fits(page);
 }
});

test('authentication and public detail screens reflow and retain protected external links',async({page},info)=>{
 for(const route of ['/login','/signup','/products/stockroom','/builders/leah-morgan','/teams','/templates','/report']){
  await page.goto(route);await expect(page.locator('main:not([aria-busy="true"]):visible')).toHaveCount(1);await fits(page);
  const links=await page.locator('a[target="_blank"]').evaluateAll(elements=>elements.map(el=>({href:el.getAttribute('href'),rel:el.getAttribute('rel')})));
  for(const link of links){expect(link.href).toMatch(/^https:\/\//);expect(link.rel).toContain('noopener');expect(link.rel).toContain('noreferrer');}
  if(route==='/login'){await expect(page.getByLabel('Email address',{exact:true})).toBeVisible();await page.screenshot({path:info.outputPath('paper-login.png'),fullPage:true});}
 }
});

test('phone navigation and header search remain reachable at narrow widths',async({page})=>{
 await page.setViewportSize({width:320,height:812});await page.goto('/');await fits(page);
 const menu=page.getByRole('button',{name:'Open navigation menu',exact:true});
 await menu.click();await expect(page.getByRole('menu')).toBeVisible();await page.keyboard.press('Escape');
 await expect(page.getByRole('menu')).toBeHidden();await expect(menu).toBeFocused();
 await page.locator('#project-collection').scrollIntoViewIfNeeded();
 await page.locator('.site-header').getByRole('link',{name:'Search projects',exact:true}).click();
 const search=page.getByRole('textbox',{name:'Search projects',exact:true});await expect(search).toBeInViewport();
 await search.focus();await expect(search).toBeFocused();await fits(page);
});

test('account controls stay readable across short laptops and narrow phones',async({page},info)=>{
 test.setTimeout(120000);
 const sizes=info.project.name.endsWith('desktop')?[[1920,1080],[1440,1000],[1366,768],[1366,650],[1024,768],[768,1024],[720,900],[375,812],[320,812]]:[[page.viewportSize()!.width,page.viewportSize()!.height]];
 for(const [width,height] of sizes){
  await page.setViewportSize({width,height});
  for(const route of ['/login','/signup']){
   await page.goto(route);await expect(page.locator('.auth-connection')).toContainText('Sign-in is unavailable');await fits(page);
   const logo=page.locator('.icy-logo').getByRole('img',{name:'getLancer',exact:true});await expect(logo).toBeVisible();
   await expect(logo.locator('svg')).toHaveAttribute('viewBox',/\d/);
   const controls=await page.locator('.icy-input input').evaluateAll(nodes=>nodes.map(node=>({font:parseFloat(getComputedStyle(node).fontSize),height:Math.round(node.getBoundingClientRect().height)})));
   for(const control of controls){expect(control.font).toBeGreaterThanOrEqual(16);expect(control.height).toBeGreaterThanOrEqual(44);}
   const form=await page.locator('.icy-form-content').boundingBox();expect(form!.width).toBeGreaterThanOrEqual(width<=375?250:290);expect(form!.width).toBeLessThanOrEqual(440);
   if(width>760){
    const storyPanel=page.locator('.icy-story');
    const dark=await page.evaluate(()=>{const probe=document.createElement('span');probe.style.color='var(--paper-dark)';document.body.append(probe);const color=getComputedStyle(probe).color;probe.remove();return color;});
    await expect(storyPanel).toHaveCSS('background-color',dark);
    const shell=await page.locator('.icy-shell').boundingBox(),story=await page.locator('.icy-story').boundingBox();
    expect(story!.width/shell!.width).toBeGreaterThan(.4);expect(story!.width/shell!.width).toBeLessThan(.6);
    const resources=(await page.getByRole('navigation',{name:'Helpful resources'}).boundingBox())!;
    expect(resources.x).toBe(story!.x);expect(resources.width).toBe(story!.width);
   }
   const input=page.getByLabel('Password',{exact:true});await input.fill('layout-check-123');
   await page.getByRole('button',{name:'Show password',exact:true}).click();await expect(input).toHaveAttribute('type','text');
   await page.getByRole('button',{name:'Hide password',exact:true}).click();await expect(input).toHaveAttribute('type','password');
   await page.getByRole('button',{name:route==='/signup'?'Create account':'Log in',exact:true}).scrollIntoViewIfNeeded();
   await page.screenshot({path:info.outputPath(`${route.slice(1)}-${width}x${height}.png`),fullPage:true});
  }
 }
});

test('login and signup offer keyboard-accessible resources linked to real pages',async({page})=>{
 for(const path of ['/login','/signup']){
  await page.goto(path);
  const resources=page.getByRole('navigation',{name:'Helpful resources'});
  await expect(resources.getByRole('link',{name:/How getLancer works/})).toHaveAttribute('href','/how-it-works');
  await expect(resources.getByRole('link',{name:/Browse projects/})).toHaveAttribute('href','/#project-collection');
  const labs=resources.getByRole('link',{name:/Explore practical labs/});
  await expect(labs).toHaveAttribute('href','/labs');await labs.focus();await expect(labs).toBeFocused();
 }
 await page.getByRole('link',{name:/Explore practical labs/}).press('Enter');await expect(page).toHaveURL(/\/labs$/);
 await fits(page);
});

test('provider progress fits a narrow compact form and a failed start recovers',async({page})=>{
 await page.setViewportSize({width:320,height:812});
 await page.route('**/api/v1/auth/providers',route=>route.fulfill({json:{google:true,github:true}}));
 let release:()=>void=()=>{};
 const started=new Promise<void>(resolve=>{release=resolve});
 await page.route('**/api/v1/auth/google/start',async route=>{await started;await route.fulfill({status:503,json:{error:{message:'Please try again.',code:'UNAVAILABLE'}}});});
 await page.goto('/login');const google=page.getByRole('button',{name:'Continue with Google',exact:true});await expect(google).toBeEnabled();await google.click();
 const progress=page.getByRole('button',{name:'Opening sign-in with Google',exact:true});await expect(progress).toContainText('Opening…');
 expect(await progress.evaluate(node=>node.scrollWidth<=node.clientWidth+1)).toBe(true);await fits(page);
 release();await expect(page.getByRole('alert')).toContainText('Please try again.');await expect(google).toBeEnabled();
});
