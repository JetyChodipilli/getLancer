import {expect,test,type Page} from '@playwright/test';

const destinations=[['Projects','/','home'],['Components','/components','components'],['College projects','/college-projects','college'],['Learning','/labs','learning']] as const;
const menuButton=(page:Page)=>page.getByRole('button',{name:'Open navigation menu',exact:true});
async function fits(page:Page){
 await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(page.viewportSize()!.width+1);
}

test('@cross-browser navigation symbols keep labels, destinations and narrow menu access',async({page})=>{
 await page.goto('/');await page.evaluate(()=>document.fonts.ready);
 const trigger=menuButton(page);
 // Opening the real menu first also proves that client interaction is ready.
 await trigger.click();const menu=page.getByRole('menu');await expect(menu).toBeVisible();
 for(const [name,href,icon] of destinations){
  const item=menu.getByRole('menuitem',{name,exact:true});
  await expect(item).toBeVisible();
  await expect(item).toHaveAttribute('href',href);
  await expect(item.locator(`[data-nav-icon="${icon}"]`)).toHaveAttribute('aria-hidden','true');
 }
 await page.keyboard.press('Escape');await expect(trigger).toBeFocused();
 for(const width of [320,768,1024,1100,1101,1280,1440]){
  await page.setViewportSize({width,height:900});await fits(page);
  const box=await trigger.boundingBox();expect(box).not.toBeNull();expect(box!.width).toBeGreaterThanOrEqual(44);expect(box!.height).toBeGreaterThanOrEqual(44);
  if(width>1100){
   const nav=page.getByRole('navigation',{name:'Main navigation',exact:true});await expect(nav).toBeVisible();
   for(const [name,href] of destinations)await expect(nav.getByRole('link',{name,exact:true})).toHaveAttribute('href',href);
  }else{
   await expect(page.getByRole('navigation',{name:'Main navigation',exact:true})).toBeHidden();
   await trigger.click();await expect(menu).toBeVisible();
   for(const [name,href] of destinations){
    const item=menu.getByRole('menuitem',{name,exact:true});await expect(item).toBeVisible();await expect(item).toHaveAttribute('href',href);
   }
   if(width===1024){
    await menu.getByRole('menuitem',{name:'Components',exact:true}).click();
    await expect(page).toHaveURL(/\/components$/);await expect(page.locator('main h1')).toBeVisible();await fits(page);await page.goto('/');
   }else{await page.keyboard.press('Escape');await expect(trigger).toBeFocused();}
  }
 }
 await page.setViewportSize({width:320,height:812});await trigger.click();
 await menu.getByRole('menuitem',{name:'Components',exact:true}).click();
 await expect(page).toHaveURL(/\/components$/);await expect(page.locator('main h1')).toBeVisible();await fits(page);
});

test('@cross-browser workspace navigation shares icon feedback and respects reduced motion',async({page})=>{
 await page.emulateMedia({reducedMotion:'no-preference'});await page.goto('/preview/workspace');await expect(page.locator('main h1')).toBeVisible();
 const mobile=page.locator('.studio-mobile-navigation');
 if(await mobile.isVisible())await mobile.locator('summary').click();
 const navigation=page.locator('.paper-studio nav[aria-label="Workspace areas"]:visible');
 const personal=navigation.getByRole('link',{name:'Personal workspace',exact:true}),components=navigation.getByRole('link',{name:'Components & slots',exact:true});
 const icon=components.locator(':scope > svg').first(),label=components.locator('span');
 const before=await label.boundingBox();expect(before).not.toBeNull();
 await personal.focus();await page.keyboard.press('Tab');await expect(components).toBeFocused();
 await expect(icon).toHaveAttribute('aria-hidden','true');await expect.poll(()=>icon.evaluate(node=>getComputedStyle(node).transform)).not.toBe('none');
 expect(await label.boundingBox()).toEqual(before);
 await page.emulateMedia({reducedMotion:'reduce'});await expect(icon).toHaveCSS('transform','none');await expect(icon).toHaveCSS('transition-duration','0s');
 await components.hover();await expect(icon).toHaveCSS('transform','none');
 await components.click();await expect(page).toHaveURL(/\/preview\/components$/);await expect(page.locator('main h1')).toBeVisible();await fits(page);
});

test('@cross-browser navigation icons respond to focus, hover and press without moving labels',async({page})=>{
 await page.emulateMedia({reducedMotion:'no-preference'});await page.setViewportSize({width:1440,height:1000});await page.goto('/');
 const trigger=menuButton(page);await trigger.click();await expect(page.getByRole('menu')).toBeVisible();await page.keyboard.press('Escape');
 const nav=page.getByRole('navigation',{name:'Main navigation',exact:true}),home=nav.getByRole('link',{name:'Projects',exact:true}),components=nav.getByRole('link',{name:'Components',exact:true});
 const icon=components.locator('.paper-nav-icon'),label=components.locator('span');
 const before=await label.boundingBox();expect(before).not.toBeNull();
 await home.focus();await page.keyboard.press('Tab');await expect(components).toBeFocused();
 await expect.poll(()=>icon.evaluate(node=>getComputedStyle(node).transform)).not.toBe('none');
 expect(await label.boundingBox()).toEqual(before);
 expect(await components.evaluate(node=>parseFloat(getComputedStyle(node).outlineWidth))).toBeGreaterThanOrEqual(2);
 if(await page.evaluate(()=>matchMedia('(hover:hover) and (pointer:fine)').matches)){
  await trigger.focus();await components.hover();
  await expect.poll(()=>icon.evaluate(node=>getComputedStyle(node).transform)).not.toBe('none');
  expect(await label.boundingBox()).toEqual(before);
 }
 // A link presses before navigation; move away before release to cancel activation.
 const homeBox=await home.boundingBox();expect(homeBox).not.toBeNull();
 await page.mouse.move(homeBox!.x+homeBox!.width/2,homeBox!.y+homeBox!.height/2);await page.mouse.down();
 await expect.poll(()=>home.locator('.paper-nav-icon').evaluate(node=>getComputedStyle(node).transform)).toMatch(/^matrix\(0\.88[, ]/);
 await page.mouse.move(homeBox!.x+homeBox!.width/2,homeBox!.y+homeBox!.height+40);await page.mouse.up();
 await trigger.click();await expect(page.getByRole('menu')).toBeVisible();
 await page.keyboard.press('Escape');await expect(trigger).toBeFocused();await fits(page);
});

test('@cross-browser reduced motion keeps navigation symbols static during interaction',async({page})=>{
 await page.emulateMedia({reducedMotion:'reduce'});await page.goto('/');
 const trigger=menuButton(page),icon=trigger.locator('.paper-nav-icon');
 await trigger.click();await expect(page.getByRole('menu')).toBeVisible();
 await expect(icon).toHaveCSS('transform','none');await expect(icon).toHaveCSS('transition-duration','0s');await expect(icon).toHaveCSS('animation-name','none');
 await page.keyboard.press('Escape');await expect(trigger).toBeFocused();await expect(icon).toHaveCSS('transform','none');
 await trigger.hover();await expect(icon).toHaveCSS('transform','none');
 await page.keyboard.down('Space');await expect(icon).toHaveCSS('transform','none');await page.keyboard.up('Space');
 await expect(page.getByRole('menu')).toBeVisible();await page.keyboard.press('Escape');await fits(page);
 await page.setViewportSize({width:1440,height:1000});
 const nav=page.getByRole('navigation',{name:'Main navigation',exact:true});
 await nav.getByRole('link',{name:'Projects',exact:true}).focus();await page.keyboard.press('Tab');
 const focused=nav.getByRole('link',{name:'Components',exact:true});await expect(focused).toBeFocused();await expect(focused.locator('.paper-nav-icon')).toHaveCSS('transform','none');
});
