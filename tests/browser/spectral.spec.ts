import {test,expect,type Locator,type Page} from '@playwright/test';

async function fits(page:Page){await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(page.viewportSize()!.width+1);}
async function movingLinkPoint(link:Locator){
 await expect(link).toBeVisible();
 await link.evaluate(node=>node.scrollIntoView({block:'center',inline:'center',behavior:'instant'}));
 // Always-on artwork never meets locator actions' stationary-element check.
 // Use a real, unobstructed browser hit target; do not force or dispatch a click.
 return link.evaluate(node=>{
  const box=node.getBoundingClientRect();
  for(const y of [.5,.25,.75,.1,.9])for(const x of [.5,.25,.75,.1,.9]){
   const point={x:box.x+box.width*x,y:box.y+box.height*y};
   if(point.x<0||point.y<0||point.x>=innerWidth||point.y>=innerHeight)continue;
   const target=document.elementFromPoint(point.x,point.y);
   if(target&&node.contains(target))return point;
  }
  throw new Error('Moving project link has no visible, unobstructed pointer target.');
 });
}
test('Spectral discovery keeps genuine project navigation, search and readable glass chrome',async({page},info)=>{
 const errors:string[]=[];page.on('pageerror',error=>errors.push(error.message));
 await page.goto('/');await expect(page.getByRole('heading',{name:'Great work deserves to be seen.'})).toBeVisible();
 await expect(page.locator('.project-stack .stack-card')).toHaveCount(3);
 await expect(page.locator('.spectral-studio-background')).toHaveAttribute('alt','');
 await expect(page.locator('.spectral-builder,.spectral-inquiry')).toHaveCount(0);
 await expect(page.locator('.site-header img')).toHaveAttribute('src','/brand/getlancer-logo.svg');
 await fits(page);await page.screenshot({path:info.outputPath('spectral-home.png'),fullPage:true});
 const project=page.getByRole('link',{name:'Explore Stockroom',exact:true});
 await project.focus();await expect(page.locator('.stack-float').first()).toHaveCSS('animation-play-state','running');
 await project.press('Enter');await expect(page.getByRole('heading',{name:'Stockroom',exact:true})).toBeVisible();
 await page.goto('/');
 const pointer=await movingLinkPoint(project);await page.mouse.click(pointer.x,pointer.y);
 await expect(page).toHaveURL(/\/products\/stockroom$/);await expect(page.getByRole('heading',{name:'Stockroom',exact:true})).toBeVisible();
 await page.goto('/');await page.getByRole('textbox',{name:'Search projects',exact:true}).fill('Stockroom');await page.locator('.market-search').getByRole('button',{name:'Search',exact:true}).click();
 await expect(page).toHaveURL(/q=Stockroom/);await expect(page.locator('.demo-gallery .project')).toHaveCount(1);await fits(page);
 await page.getByRole('link',{name:'Start a project request',exact:true}).click();await expect(page.getByRole('button',{name:'New project request',exact:true})).toBeVisible();
 expect(errors).toEqual([]);
});
test('original auth cards rotate continuously during form use without pause controls',async({page})=>{
 await page.goto('/login');
 const scene=page.locator('.auth-scene'),card=page.locator('.auth-orbit-freelancers');
 await expect(page.getByRole('button',{name:/^(Pause|Play|Resume) animation$/})).toHaveCount(0);
 if(page.viewportSize()!.width<=760){await expect(scene).toBeHidden();await expect(page.getByLabel('Email address',{exact:true})).toBeVisible();return;}
 await expect(scene).toHaveAttribute('data-running','true');
 await expect(card).toHaveAttribute('src','/auth/freelancers.webp');
 expect(await card.evaluate((node:HTMLImageElement)=>node.complete&&node.naturalWidth===1254)).toBe(true);
 const start=await card.boundingBox();
 await expect.poll(async()=>Math.abs((await card.boundingBox())!.x-start!.x)).toBeGreaterThan(.1);
 await page.getByLabel('Email address',{exact:true}).fill('client@example.test');
 await expect(scene).toHaveAttribute('data-running','true');
 const focused=await card.boundingBox();
 await expect.poll(async()=>Math.abs((await card.boundingBox())!.x-focused!.x)).toBeGreaterThan(.1);
 await page.emulateMedia({reducedMotion:'reduce'});await expect(scene).toHaveAttribute('data-running','true');
 const reduced=await card.boundingBox();
 await expect.poll(async()=>Math.abs((await card.boundingBox())!.x-reduced!.x)).toBeGreaterThan(.1);
 await page.goto('/signup');await expect(scene).toHaveAttribute('data-running','true');
 const signup=await card.boundingBox();
 await expect.poll(async()=>Math.abs((await card.boundingBox())!.x-signup!.x)).toBeGreaterThan(.1);
 await fits(page);
});
test('hero cards keep visibly moving through hover, keyboard focus and reduced-motion settings',async({page})=>{
 for(const reducedMotion of ['no-preference','reduce'] as const){
  await page.emulateMedia({reducedMotion});await page.goto('/');
  const scene=page.locator('.spectral-scene'),floats=page.locator('.stack-float');
  await expect(scene).toHaveAttribute('data-motion','running');
  await expect(floats).toHaveCount(3);
  const project=page.getByRole('link',{name:'Explore Stockroom',exact:true});
  const pointer=await movingLinkPoint(project);await page.mouse.move(pointer.x,pointer.y);
  await expect.poll(()=>project.evaluate(node=>node.matches(':hover'))).toBe(true);
  await project.focus();await expect(project).toBeFocused();
  for(const card of await floats.all()){
   await expect(card).toHaveCSS('animation-play-state','running');
   await expect(card).toHaveCSS('animation-name','spectral-float');
   const start=await card.boundingBox();
   await expect.poll(async()=>Math.abs((await card.boundingBox())!.y-start!.y),{timeout:7000}).toBeGreaterThan(.5);
  }
  await fits(page);
 }
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
 for(const [width,height] of [[1920,1080],[1440,1000],[1366,768],[1366,650],[1024,768],[768,1024],[720,900],[375,812],[320,812]]){
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
    const sceneBox=await page.locator('.auth-scene').boundingBox();expect(sceneBox!.width/story!.width).toBeGreaterThanOrEqual(.9);
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
test('large cards complete a full orbit with visible artwork separated and contained',async({page},info)=>{
 test.skip(info.project.name!=='desktop','One complete desktop orbit covers the shared animation.');
 test.setTimeout(120000);await page.clock.install();await page.goto('/login');
 const scene=page.locator('.auth-scene');await expect(scene).toHaveAttribute('data-running','true');
 await expect(scene.locator('.auth-orbit-card')).toHaveCount(4);
 await expect(scene.locator('.auth-scene-frame')).toHaveAttribute('src','/auth/pearlescent-frame-complete.webp');
 await expect(scene.locator('.auth-scene-laptop')).toHaveAttribute('src','/auth/laptop.webp');
 // Measure the visible alpha silhouette independently; supplied images include transparent padding.
 const masks=await scene.locator('.auth-orbit-card').evaluateAll(nodes=>nodes.map(node=>{
  const image=node as HTMLImageElement,canvas=document.createElement('canvas');canvas.width=image.naturalWidth;canvas.height=image.naturalHeight;
  const ctx=canvas.getContext('2d')!;ctx.drawImage(image,0,0);const rgba=ctx.getImageData(0,0,canvas.width,canvas.height).data;
  let left=canvas.width,top=canvas.height,right=0,bottom=0;
  for(let y=0;y<canvas.height;y++)for(let x=0;x<canvas.width;x++)if(rgba[(y*canvas.width+x)*4+3]>32){left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x);bottom=Math.max(bottom,y);}
  return {left:left/canvas.width,top:top/canvas.height,right:(right+1)/canvas.width,bottom:(bottom+1)/canvas.height};
 }));
 const laptop=await scene.locator('.auth-scene-laptop').boundingBox(),initial=await scene.locator('.auth-orbit-card').first().boundingBox();
 let movedAbove=false,movedBelow=false;
 for(let phase=0;phase<24;phase++){
  await page.clock.runFor(3000);
  const bounds=await scene.evaluate(node=>{const scene=node.getBoundingClientRect();return [...node.querySelectorAll('img')].map(image=>{const box=image.getBoundingClientRect();return {left:box.left-scene.left,top:box.top-scene.top,right:scene.right-box.right,bottom:scene.bottom-box.bottom};});});
  for(const bound of bounds)for(const margin of Object.values(bound))expect(margin).toBeGreaterThanOrEqual(8);
  const boxes=await scene.locator('.auth-orbit-card').evaluateAll(nodes=>nodes.map(node=>{const r=node.getBoundingClientRect();return {x:r.x,y:r.y,width:r.width,height:r.height};}));
  const visible=boxes.map((box,index)=>({left:box.x+masks[index].left*box.width,top:box.y+masks[index].top*box.height,right:box.x+masks[index].right*box.width,bottom:box.y+masks[index].bottom*box.height}));
  for(let a=0;a<visible.length;a++)for(let b=a+1;b<visible.length;b++){const x=visible[a],y=visible[b];expect(x.right<=y.left||y.right<=x.left||x.bottom<=y.top||y.bottom<=x.top).toBe(true);}
  movedAbove ||= boxes[0].y<initial!.y-20;movedBelow ||= boxes[0].y>initial!.y+20;
 }
 expect(movedAbove&&movedBelow).toBe(true);
 expect(await scene.locator('.auth-scene-laptop').boundingBox()).toEqual(laptop);
 await expect(page.locator('.auth-orbit-toggle')).toHaveCount(0);
 const box=await scene.boundingBox();expect(laptop!.width/box!.width).toBeGreaterThanOrEqual(.5);
 for(let index=0;index<masks.length;index++)expect((masks[index].right-masks[index].left)*.255).toBeGreaterThanOrEqual(.18);
 const copy=await page.locator('.icy-story-copy').boundingBox();expect(copy!.y-(box!.y+box!.height)).toBeGreaterThanOrEqual(12);
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
