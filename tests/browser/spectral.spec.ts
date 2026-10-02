import {test,expect,type Page} from '@playwright/test';

async function fits(page:Page){await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(page.viewportSize()!.width+1);}
test('Spectral discovery keeps genuine project navigation, search and readable glass chrome',async({page},info)=>{
 const errors:string[]=[];page.on('pageerror',error=>errors.push(error.message));
 await page.goto('/');await expect(page.getByRole('heading',{name:'Great work deserves to be seen.'})).toBeVisible();
 await expect(page.locator('.project-stack .stack-card')).toHaveCount(3);
 await expect(page.locator('.spectral-studio-background')).toHaveAttribute('alt','');
 await expect(page.locator('.spectral-builder,.spectral-inquiry')).toHaveCount(0);
 await expect(page.locator('.site-header img')).toHaveAttribute('src','/brand/getlancer-liquid-glass.svg');
 await fits(page);await page.screenshot({path:info.outputPath('spectral-home.png'),fullPage:true});
 const project=page.getByRole('link',{name:'Explore Stockroom',exact:true});
 await project.focus();await expect(page.locator('.stack-float').first()).toHaveCSS('animation-play-state','paused');
 await project.click();await expect(page.getByRole('heading',{name:'Stockroom',exact:true})).toBeVisible();
 await page.goto('/');await page.getByRole('textbox',{name:'Search projects',exact:true}).fill('Stockroom');await page.locator('.market-search').getByRole('button',{name:'Search',exact:true}).click();
 await expect(page).toHaveURL(/q=Stockroom/);await expect(page.locator('.demo-gallery .project')).toHaveCount(1);await fits(page);
 await page.getByRole('link',{name:'Start a project request',exact:true}).click();await expect(page.getByRole('button',{name:'New project request',exact:true})).toBeVisible();
 expect(errors).toEqual([]);
});
test('auth artwork revolves slowly around a stationary laptop and pauses for focus and reduced motion',async({page})=>{
 await page.goto('/login');
 const scene=page.locator('.auth-scene'),card=page.locator('.auth-orbit-freelancers');
 if(page.viewportSize()!.width<=760){await expect(scene).toBeHidden();await expect(page.getByLabel('Email address',{exact:true})).toBeVisible();return;}
 await expect(scene).toHaveAttribute('data-running','true');
 const laptop=await page.locator('.auth-scene-laptop').boundingBox();
 const start=await card.boundingBox();
 await expect.poll(async()=>Math.abs((await card.boundingBox())!.x-start!.x)).toBeGreaterThan(.5);
 expect(Math.abs((await card.boundingBox())!.x-start!.x)).toBeLessThan(30);
 expect(await page.locator('.auth-scene-laptop').boundingBox()).toEqual(laptop);
 await page.getByRole('button',{name:'Pause animation',exact:true}).click();await expect(scene).toHaveAttribute('data-running','false');
 await page.getByRole('button',{name:'Play animation',exact:true}).click();await expect(scene).toHaveAttribute('data-running','true');
 await page.getByLabel('Email address',{exact:true}).fill('client@example.test');await expect(scene).toHaveAttribute('data-running','false');
 await page.getByRole('heading',{name:'Welcome back',exact:true}).focus();await expect(scene).toHaveAttribute('data-running','true');
 await page.emulateMedia({reducedMotion:'reduce'});await expect(scene).toHaveAttribute('data-running','false');
 const positions=await scene.evaluate(node=>{const b=node.getBoundingClientRect();return [...node.querySelectorAll('.auth-orbit-card')].map(card=>{const r=card.getBoundingClientRect();return {x:(r.x+r.width/2-b.x)/b.width,y:(r.y+r.height/2-b.y)/b.height};});});
 for(const [index,pose] of [[.74,.19],[.19,.43],[.19,.74],[.78,.64]].entries()){expect(positions[index].x).toBeCloseTo(pose[0],2);expect(positions[index].y).toBeCloseTo(pose[1],2);}
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
 await page.getByRole('button',{name:'Open navigation menu',exact:true}).click();await expect(page.getByRole('menu')).toBeVisible();await page.keyboard.press('Escape');
 await page.evaluate(()=>{const search=document.querySelector('.market-search')!;window.scrollTo({top:search.getBoundingClientRect().bottom+window.scrollY+120,behavior:'instant'});});
 await expect(page.getByRole('textbox',{name:'Search projects while browsing'})).toBeVisible();await fits(page);
 await page.emulateMedia({reducedMotion:'reduce'});expect(await page.locator('.stack-card').first().evaluate(el=>getComputedStyle(el).animationName)).toBe('none');
});
