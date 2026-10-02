import {test,expect,type Page} from '@playwright/test';

async function fits(page:Page){await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(page.viewportSize()!.width+1);}
test('Spectral discovery keeps genuine project navigation, search and readable glass chrome',async({page},info)=>{
 const errors:string[]=[];page.on('pageerror',error=>errors.push(error.message));
 await page.goto('/');await expect(page.getByRole('heading',{name:'Great work deserves to be seen.'})).toBeVisible();
 await expect(page.locator('.project-stack .stack-card')).toHaveCount(3);
 await expect(page.locator('.spectral-studio-background')).toHaveAttribute('alt','');
 await expect(page.locator('.spectral-builder,.spectral-inquiry')).toHaveCount(0);
 await expect(page.locator('.site-header img')).toHaveAttribute('src','/brand/getlancer-logo.svg');
 await fits(page);await page.screenshot({path:info.outputPath('spectral-home.png'),fullPage:true});
 const project=page.getByRole('link',{name:'Explore Stockroom',exact:true});
 await project.focus();await expect(page.locator('.stack-float').first()).toHaveCSS('animation-play-state','paused');
 await project.click();await expect(page.getByRole('heading',{name:'Stockroom',exact:true})).toBeVisible();
 await page.goto('/');await page.getByRole('textbox',{name:'Search projects',exact:true}).fill('Stockroom');await page.locator('.market-search').getByRole('button',{name:'Search',exact:true}).click();
 await expect(page).toHaveURL(/q=Stockroom/);await expect(page.locator('.demo-gallery .project')).toHaveCount(1);await fits(page);
 await page.getByRole('link',{name:'Start a project request',exact:true}).click();await expect(page.getByRole('button',{name:'New project request',exact:true})).toBeVisible();
 expect(errors).toEqual([]);
});
test('original auth artwork stays animated during form use without pause controls',async({page})=>{
 await page.goto('/login');
 const scene=page.locator('.auth-scene'),art=page.locator('.auth-orbit-freelancers');
 await expect(page.getByRole('button',{name:/^(Pause|Play|Resume) animation$/})).toHaveCount(0);
 if(page.viewportSize()!.width<=760){await expect(scene).toBeHidden();await expect(page.getByLabel('Email address',{exact:true})).toBeVisible();return;}
 await expect(art).toHaveAttribute('src','/auth/freelancers.webp');
 expect(await art.evaluate((node:HTMLImageElement)=>node.complete&&node.naturalWidth===1254)).toBe(true);
 await expect(art).toHaveCSS('animation-play-state','running');
 const start=await art.boundingBox();
 await expect.poll(async()=>Math.abs((await art.boundingBox())!.y-start!.y)).toBeGreaterThan(.1);
 expect(Math.abs((await art.boundingBox())!.y-start!.y)).toBeLessThan(7);
 await page.getByLabel('Email address',{exact:true}).fill('client@example.test');
 await expect(art).toHaveCSS('animation-play-state','running');
 const focused=await art.boundingBox();
 await expect.poll(async()=>Math.abs((await art.boundingBox())!.y-focused!.y)).toBeGreaterThan(.1);
 await page.emulateMedia({reducedMotion:'reduce'});await expect(art).toHaveCSS('animation-name','none');
 await fits(page);
});
test('authentication and public detail screens reflow and retain protected external links',async({page},info)=>{
 for(const route of ['/login','/signup','/products/stockroom','/builders/leah-morgan','/teams','/templates','/report']){
  await page.goto(route);await expect(page.locator('main:not([aria-busy="true"]):visible')).toHaveCount(1);await fits(page);
  const links=await page.locator('a[target="_blank"]').evaluateAll(elements=>elements.map(el=>({href:el.getAttribute('href'),rel:el.getAttribute('rel')})));
  for(const link of links){expect(link.href).toMatch(/^https:\/\//);expect(link.rel).toContain('noopener');expect(link.rel).toContain('noreferrer');}
  if(route==='/login') {await expect(page.getByLabel('Email address',{exact:true})).toBeVisible();await page.screenshot({path:info.outputPath('spectral-login.png'),fullPage:true});}
 }
});
test('phone navigation and scroll search remain reachable at narrow widths',async({page})=>{
 await page.setViewportSize({width:320,height:812});await page.goto('/');await fits(page);
 const menuButton=page.getByRole('button',{name:'Open navigation menu',exact:true});
 await menuButton.click();await expect(page.getByRole('menu')).toBeVisible();await page.keyboard.press('Escape');
 await expect(page.getByRole('menu')).toBeHidden();await expect(menuButton).toBeFocused();
 await page.evaluate(()=>{const search=document.querySelector('.market-search')!;window.scrollTo({top:search.getBoundingClientRect().bottom+window.scrollY+120,behavior:'instant'});});
 await expect(page.getByRole('textbox',{name:'Search projects while browsing'})).toBeVisible();await fits(page);
 await page.emulateMedia({reducedMotion:'reduce'});expect(await page.locator('.stack-card').first().evaluate(el=>getComputedStyle(el).animationName)).toBe('none');
});
test('account controls stay readable across short laptops and narrow phones',async({page},info)=>{
 test.skip(info.project.name!=='desktop','The desktop project covers the complete viewport matrix.');
 for(const [width,height] of [[1920,1080],[1440,1000],[1366,768],[1024,768],[768,1024],[720,900],[375,812],[320,812]]){
  await page.setViewportSize({width,height});
  for(const route of ['/login','/signup']){
   await page.goto(route);await expect(page.locator('.auth-connection')).toContainText('Sign-in is unavailable');
   await fits(page);
   const logo=page.locator('.icy-logo img');await expect(logo).toHaveAttribute('src','/brand/getlancer-logo.svg');
   expect(await logo.evaluate((node:HTMLImageElement)=>node.complete&&node.naturalWidth>0)).toBe(true);
   const controls=await page.locator('.icy-input input').evaluateAll(nodes=>nodes.map(node=>({font:parseFloat(getComputedStyle(node).fontSize),height:Math.round(node.getBoundingClientRect().height)})));
   for(const control of controls){expect(control.font).toBeGreaterThanOrEqual(16);expect(control.height).toBeGreaterThanOrEqual(42);}
   const form=await page.locator('.icy-form-content').boundingBox();expect(form!.height).toBeLessThan(width<=760?640:600);expect(form!.width).toBeGreaterThanOrEqual(width<=375?250:290);
   expect(form!.width).toBeLessThanOrEqual(320);
   if(width>760){
    await expect(page.locator('.icy-story')).toHaveCSS('background-color','rgb(238, 240, 244)');
    const shell=await page.locator('.icy-shell').boundingBox(),story=await page.locator('.icy-story').boundingBox();
    expect(story!.width/shell!.width).toBeCloseTo(width>1000?.65:.55,2);
    expect(story!.height).toBeLessThanOrEqual(height+1);
    const bounds=await page.locator('.auth-scene').evaluate(node=>{const scene=node.getBoundingClientRect();return [...node.querySelectorAll('img')].map(image=>{const box=image.getBoundingClientRect();return {left:box.left-scene.left,top:box.top-scene.top,right:scene.right-box.right,bottom:scene.bottom-box.bottom};});});
    for(const bound of bounds)for(const margin of Object.values(bound))expect(margin).toBeGreaterThanOrEqual(-1);
   }
   const input=page.getByLabel('Password',{exact:true});await input.fill('layout-check-123');
   await page.getByRole('button',{name:'Show password',exact:true}).click();await expect(input).toHaveAttribute('type','text');
   await page.getByRole('button',{name:'Hide password',exact:true}).click();await expect(input).toHaveAttribute('type','password');
   await page.getByRole('button',{name:route==='/signup'?'Create account':'Log in',exact:true}).scrollIntoViewIfNeeded();
   await page.screenshot({path:info.outputPath(`${route.slice(1)}-${width}x${height}.png`),fullPage:true});
  }
 }
});
test('larger original assets remain contained and separated over their motion cycles',async({page},info)=>{
 test.skip(info.project.name!=='desktop','One complete desktop cycle covers the shared animation.');
 await page.goto('/login');
 const scene=page.locator('.auth-scene');
 await expect(scene.locator('.auth-orbit-card')).toHaveCount(4);
 await expect(scene.locator('.auth-scene-frame')).toHaveAttribute('src','/auth/pearlescent-frame-complete.webp');
 await expect(scene.locator('.auth-scene-laptop')).toHaveAttribute('src','/auth/laptop.webp');
 for(let phase=0;phase<=24;phase++){
  await scene.evaluate((node,progress)=>{for(const image of node.querySelectorAll('img')){const animation=image.getAnimations()[0];animation.pause();animation.currentTime=Number(animation.effect!.getTiming().duration)*progress;}},phase/24);
  const bounds=await scene.evaluate(node=>{const scene=node.getBoundingClientRect();return [...node.querySelectorAll('img')].map(image=>{const box=image.getBoundingClientRect();return {left:box.left-scene.left,top:box.top-scene.top,right:scene.right-box.right,bottom:scene.bottom-box.bottom};});});
  for(const bound of bounds)for(const margin of Object.values(bound))expect(margin).toBeGreaterThanOrEqual(8);
  const cards=await scene.locator('.auth-orbit-card').evaluateAll(nodes=>nodes.map(node=>{const r=node.getBoundingClientRect();return {left:r.left,top:r.top,right:r.right,bottom:r.bottom};}));
  for(let a=0;a<cards.length;a++)for(let b=a+1;b<cards.length;b++){const x=cards[a],y=cards[b];expect(x.right<=y.left||y.right<=x.left||x.bottom<=y.top||y.bottom<=x.top).toBe(true);}
 }
 await expect(page.locator('.auth-orbit-toggle')).toHaveCount(0);
 const box=await scene.boundingBox(),laptop=await scene.locator('.auth-scene-laptop').boundingBox();
 expect(laptop!.width/box!.width).toBeGreaterThanOrEqual(.5);
 for(const card of await scene.locator('.auth-orbit-card').all())expect((await card.boundingBox())!.width/box!.width).toBeGreaterThanOrEqual(.3);
 const logo=await page.locator('.icy-logo').boundingBox(),copy=await page.locator('.icy-story-copy').boundingBox();
 expect(box!.y-(logo!.y+logo!.height)).toBeGreaterThanOrEqual(16);
 expect(copy!.y-(box!.y+box!.height)).toBeGreaterThanOrEqual(12);
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
