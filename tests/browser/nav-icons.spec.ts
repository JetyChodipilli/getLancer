import {expect,test,type Locator,type Page} from '@playwright/test';

const destinations=[['Projects','/','home'],['Components','/components','components'],['College projects','/college-projects','college'],['Learning','/labs','learning']] as const;
const menuButton=(page:Page)=>page.getByRole('button',{name:'Open navigation menu',exact:true});
// The modal menu deliberately aria-hides its background; keep style inspection
// anchored to the same DOM icon while accessibility-based actions remain scoped.
const menuIcon=(page:Page)=>page.locator('.site-header button[aria-label="Open navigation menu"] > .paper-nav-icon');
// Observe the rendered path/group values, independently of whole-SVG CSS.
async function partState(icon:Locator){
 // Radix removes background aria-hidden after its exit. Role-based chains must
 // resolve the actual visible SVG before an empty evaluateAll can look static.
 await expect(icon).toBeVisible();
 const parts=icon.locator('[data-animated-part]');await expect(parts.first()).toBeAttached();
 return parts.evaluateAll(nodes=>nodes.map(node=>{
  const style=(node as SVGElement).style;return JSON.stringify([style.transform,style.opacity,node.getAttribute('stroke-dasharray'),node.getAttribute('stroke-dashoffset')]);
 }));
}
async function afterPreferenceFrame(icon:Locator){
 // Capture inside the second frame, before another host round trip can mask work.
 return icon.evaluate(node=>new Promise<string[]>(resolve=>requestAnimationFrame(()=>requestAnimationFrame(()=>{
  resolve(Array.from(node.querySelectorAll<SVGElement>('[data-animated-part]')).map(part=>JSON.stringify([part.style.transform,part.style.opacity,part.getAttribute('stroke-dasharray'),part.getAttribute('stroke-dashoffset')])));
 }))));
}
async function partsAreStatic(icon:Locator){
 await expect(icon).toBeVisible();await expect(icon.locator('[data-animated-part]').first()).toBeAttached();
 const states=await icon.locator('[data-animated-part]').evaluateAll(nodes=>nodes.map(node=>{
  const style=getComputedStyle(node);return{transform:style.transform,opacity:style.opacity,animation:style.animationName,transition:style.transitionDuration};
 }));
 expect(states.length).toBeGreaterThan(0);
 for(const state of states){expect(state.transform).toBe('none');expect(state.opacity).toBe('1');expect(state.animation).toBe('none');expect(state.transition).toBe('0s');}
}
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
  expect(await item.locator('[data-animated-part]').count()).toBeGreaterThan(0);
 }
 await page.keyboard.press('Escape');await expect(trigger).toBeFocused();
 for(const width of [320,768,1024,1100,1101,1280,1440]){
  await page.setViewportSize({width,height:900});await fits(page);
  // Firefox DOMRects may round 44px to 43.999969; CSS minima stay exactly 44px.
  const box=await trigger.boundingBox();expect(box).not.toBeNull();expect(box!.width).toBeGreaterThanOrEqual(44-.001);expect(box!.height).toBeGreaterThanOrEqual(44-.001);
  expect(await trigger.evaluate(node=>parseFloat(getComputedStyle(node).minWidth))).toBeGreaterThanOrEqual(44);expect(await trigger.evaluate(node=>parseFloat(getComputedStyle(node).minHeight))).toBeGreaterThanOrEqual(44);
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
 await page.emulateMedia({reducedMotion:'no-preference'});await page.goto('/preview/workspace');await page.evaluate(()=>document.fonts.ready);await expect(page.locator('main h1')).toBeVisible();
 await menuButton(page).click();await expect(page.getByRole('menu')).toBeVisible();await page.keyboard.press('Escape');
 const mobile=page.locator('.studio-mobile-navigation');
 if(await mobile.isVisible())await mobile.locator('summary').click();
 const navigation=page.locator('.paper-studio nav[aria-label="Workspace areas"]:visible');
 const personal=navigation.getByRole('link',{name:'Personal workspace',exact:true}),components=navigation.getByRole('link',{name:'Components & slots',exact:true});
 const icon=components.locator(':scope > svg').first(),label=components.locator('span');
 const before=await label.boundingBox(),normal=await partState(icon);expect(before).not.toBeNull();expect(normal.length).toBeGreaterThan(0);
 await personal.focus();await page.keyboard.press('Tab');await expect(components).toBeFocused();
 await expect(icon).toHaveAttribute('aria-hidden','true');await expect.poll(()=>partState(icon)).not.toEqual(normal);await expect(icon).toHaveCSS('transform','none');
 expect(await label.boundingBox()).toEqual(before);
 await page.emulateMedia({reducedMotion:'reduce'});await partsAreStatic(icon);expect(await afterPreferenceFrame(icon)).toEqual(normal);
 await components.hover();await partsAreStatic(icon);
 await components.click();await expect(page).toHaveURL(/\/preview\/components$/);await expect(page.locator('main h1')).toBeVisible();await fits(page);
});

test('@cross-browser navigation icons respond to focus, hover and press without moving labels',async({page})=>{
 await page.emulateMedia({reducedMotion:'no-preference'});await page.setViewportSize({width:1440,height:1000});await page.goto('/');await page.evaluate(()=>document.fonts.ready);
 const trigger=menuButton(page);await trigger.click();await expect(page.getByRole('menu')).toBeVisible();await page.keyboard.press('Escape');
 const nav=page.getByRole('navigation',{name:'Main navigation',exact:true}),home=nav.getByRole('link',{name:'Projects',exact:true}),components=nav.getByRole('link',{name:'Components',exact:true});
 const icon=components.locator('.paper-nav-icon'),label=components.locator('span'),homeIcon=home.locator('.paper-nav-icon');
 const normal=await partState(icon),homeNormal=await partState(homeIcon);expect(normal.length).toBeGreaterThan(0);
 const before=await label.boundingBox();expect(before).not.toBeNull();
 await home.focus();await expect.poll(()=>partState(homeIcon)).not.toEqual(homeNormal);
 // Live reduction must reset drawing/opacity too, before the finite animation ends.
 await page.emulateMedia({reducedMotion:'reduce'});expect(await afterPreferenceFrame(homeIcon)).toEqual(homeNormal);await partsAreStatic(homeIcon);
 await page.emulateMedia({reducedMotion:'no-preference'});
 await page.keyboard.press('Tab');await expect(components).toBeFocused();
 await expect.poll(()=>partState(icon)).not.toEqual(normal);await expect(icon).toHaveCSS('transform','none');
 expect(await label.boundingBox()).toEqual(before);
 expect(await components.evaluate(node=>parseFloat(getComputedStyle(node).outlineWidth))).toBeGreaterThanOrEqual(2);
 await expect.poll(()=>partState(icon)).toEqual(normal); // Holding focus does not loop.
 if(await page.evaluate(()=>matchMedia('(hover:hover) and (pointer:fine)').matches)){
  await trigger.focus();await components.hover();
  await expect.poll(()=>partState(icon)).not.toEqual(normal);await expect.poll(()=>partState(icon)).toEqual(normal);
  expect(await label.boundingBox()).toEqual(before);
 }
 const learning=nav.getByRole('link',{name:'Learning',exact:true}),flask=learning.locator('.paper-nav-icon'),flaskNormal=await partState(flask);
 await components.focus();await page.keyboard.press('Tab');await page.keyboard.press('Tab');await expect(learning).toBeFocused();
 await expect.poll(()=>partState(flask)).not.toEqual(flaskNormal);await expect(flask).toHaveCSS('transform','none');await expect.poll(()=>partState(flask)).toEqual(flaskNormal);
 // Press the native button directly, without dragging a navigable anchor.
 const menuNormal=await partState(menuIcon(page));
 if(await page.evaluate(()=>navigator.maxTouchPoints>0)){
  await trigger.tap();await expect.poll(()=>partState(menuIcon(page))).not.toEqual(menuNormal);
 }else{
  const menuBox=await trigger.boundingBox();expect(menuBox).not.toBeNull();
  await page.mouse.move(menuBox!.x+menuBox!.width/2,menuBox!.y+menuBox!.height/2);await page.mouse.down();
  await expect.poll(()=>partState(menuIcon(page))).not.toEqual(menuNormal);await page.mouse.up();
 }
 await expect(menuIcon(page)).toHaveCSS('transform','none');await expect(page.getByRole('menu')).toBeVisible();
 await page.keyboard.press('Escape');await expect(trigger).toBeFocused();await fits(page);
});

test('@cross-browser reduced motion keeps navigation symbols static during interaction',async({page})=>{
 await page.emulateMedia({reducedMotion:'reduce'});await page.goto('/');await page.evaluate(()=>document.fonts.ready);
 const trigger=menuButton(page),icon=menuIcon(page),normal=await partState(icon);
 await trigger.click();await expect(page.getByRole('menu')).toBeVisible();
 await partsAreStatic(icon);await expect.poll(()=>partState(icon)).toEqual(normal);
 await page.keyboard.press('Escape');await expect(trigger).toBeFocused();await partsAreStatic(icon);
 await trigger.hover();await partsAreStatic(icon);
 await page.keyboard.down('Space');await partsAreStatic(icon);await page.keyboard.up('Space');
 await expect(page.getByRole('menu')).toBeVisible();await page.keyboard.press('Escape');await fits(page);
 await page.setViewportSize({width:1440,height:1000});
 const nav=page.getByRole('navigation',{name:'Main navigation',exact:true});
 await nav.getByRole('link',{name:'Projects',exact:true}).focus();await page.keyboard.press('Tab');
 const focused=nav.getByRole('link',{name:'Components',exact:true});await expect(focused).toBeFocused();await partsAreStatic(focused.locator('.paper-nav-icon'));
});
