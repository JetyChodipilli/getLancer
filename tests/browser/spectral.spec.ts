import {test,expect,type Page} from '@playwright/test';

async function fits(page:Page){await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(page.viewportSize()!.width+1);}
test('Spectral discovery keeps genuine project navigation, search and readable glass chrome',async({page},info)=>{
 const errors:string[]=[];page.on('pageerror',error=>errors.push(error.message));
 await page.goto('/');await expect(page.getByRole('heading',{name:'Great work deserves to be seen.'})).toBeVisible();
 await expect(page.locator('.project-stack .stack-card')).toHaveCount(3);
 await expect(page.locator('.spectral-studio-background')).toHaveAttribute('alt','');
 await expect(page.locator('.spectral-builder')).toContainText('Leah Morgan');
 await fits(page);await page.screenshot({path:info.outputPath('spectral-home.png'),fullPage:true});
 const project=page.getByRole('link',{name:'Explore Stockroom',exact:true});
 await project.focus();await expect(page.locator('.stack-float').first()).toHaveCSS('animation-play-state','paused');
 await project.click();await expect(page.getByRole('heading',{name:'Stockroom',exact:true})).toBeVisible();
 await page.goto('/');await page.getByRole('textbox',{name:'Search projects',exact:true}).fill('Stockroom');await page.locator('.market-search').getByRole('button',{name:'Search',exact:true}).click();
 await expect(page).toHaveURL(/q=Stockroom/);await expect(page.locator('.demo-gallery .project')).toHaveCount(1);await fits(page);
 await page.getByRole('link',{name:'Start a project request',exact:true}).click();await expect(page.getByRole('button',{name:'New project request',exact:true})).toBeVisible();
 expect(errors).toEqual([]);
});
test('hero inquiry carries a private draft into review without sending in demo mode',async({page})=>{
 await page.goto('/');
 await page.getByRole('textbox',{name:'Work email',exact:true}).fill('client@example.test');
 const idea='Build a private booking workspace for our small team.';
 await page.getByRole('textbox',{name:'Your project idea',exact:true}).fill(idea);
 await page.getByRole('button',{name:'Continue inquiry',exact:true}).click();
 await expect(page).toHaveURL(/\/inquiry\?product=stockroom&requestType=NEW_BUILD/);
 expect(page.url()).not.toContain('client@example');
 await expect(page.getByLabel('Describe your project',{exact:true})).toHaveValue(idea);
 await expect(page.getByLabel('Your email',{exact:true})).toHaveValue('client@example.test');
 await expect(page.getByRole('button',{name:'Send inquiry',exact:true})).toBeDisabled();
 await expect(page.getByText('Frontend preview. You can review the form; nothing is sent.')).toBeVisible();
 expect(await page.evaluate(()=>sessionStorage.getItem('getlancer:inquiry-draft'))).toBeNull();
 await fits(page);
});
test('authentication and public detail screens reflow and retain protected external links',async({page},info)=>{
 for(const route of ['/login','/signup','/products/stockroom','/builders/leah-morgan','/teams','/templates','/report']){
  await page.goto(route);await expect(page.locator('main')).toBeVisible();await fits(page);
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
