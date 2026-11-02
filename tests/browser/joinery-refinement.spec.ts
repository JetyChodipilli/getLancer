import {expect,test,type Page} from '@playwright/test';
import {buildSync} from 'esbuild';

function contrast(a:string,b:string){
 const luminance=(value:string)=>{const c=value.match(/[\d.]+/g)!.slice(0,3).map(Number).map(x=>x/255).map(x=>x<=.04045?x/12.92:((x+.055)/1.055)**2.4);return c[0]*.2126+c[1]*.7152+c[2]*.0722;};
 const x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);
}
async function fits(page:Page){await page.evaluate(()=>document.fonts.ready);await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(page.viewportSize()!.width+1);}
const providers=(page:Page)=>page.route('**/api/v1/auth/providers',route=>route.fulfill({json:{google:false,github:false}}));

test('@cross-browser Joinery local fonts and blocked-font layouts remain usable',async({page})=>{
 await page.goto('/');await expect(page.locator('.site-header [role=img][aria-label=getLancer]')).toHaveCount(1);
 expect(await page.evaluate(async()=>{const fonts=await document.fonts.load('400 16px "getLancer Sans"');return fonts.length>0&&fonts.every(font=>font.status==='loaded');})).toBe(true);
 await expect(page.locator('body')).toHaveCSS('font-family',/getLancer Sans/);await fits(page);
 await page.route('**/fonts/**',route=>route.abort());await page.setViewportSize({width:320,height:812});
 for(const path of ['/?q=Stockroom','/login','/signup','/preview/workspace']){await page.goto(path);await expect(page.locator('main h1')).toBeVisible();await fits(page);}
 await page.setViewportSize({width:768,height:1024});await page.goto('/login');
 await page.evaluate(()=>{const sizes=Array.from(document.querySelectorAll<HTMLElement>('main,main *')).map(element=>[element,parseFloat(getComputedStyle(element).fontSize)] as const);for(const [element,size]of sizes)element.style.fontSize=`${size*2}px`;});
 await expect(page.getByLabel('Email address',{exact:true})).toBeVisible();await fits(page);
});

test('@cross-browser auth field boundaries remain visible through validation and pending',async({page})=>{
 expect(contrast('rgb(214,217,213)','rgb(255,255,255)')).toBeLessThan(3); // Previous edge fails the same oracle.
 await providers(page);let release!:()=>void;const held=new Promise<void>(resolve=>{release=resolve;});
 await page.route('**/api/v1/auth/login',async route=>{await held;await route.fulfill({status:401,json:{error:{code:'INVALID_CREDENTIALS',message:'Check your email and password.'}}});});
 await page.goto('/login');const email=page.getByLabel('Email address',{exact:true}),field=email.locator('..');
 async function boundary(){const c=await field.evaluate(node=>{const s=getComputedStyle(node);return{border:s.borderTopColor,background:s.backgroundColor,width:parseFloat(s.borderTopWidth),opacity:s.opacity};});expect(c.width).toBeGreaterThanOrEqual(1);expect(c.opacity).toBe('1');expect(contrast(c.border,c.background)).toBeGreaterThanOrEqual(3);}
 await boundary();await email.focus();await boundary();await page.getByRole('button',{name:'Log in',exact:true}).click();
 await expect(page.getByRole('alert')).toBeFocused();await expect(email).toHaveAttribute('aria-invalid','true');await boundary();
 await page.getByRole('alert').getByRole('link',{name:'Email address',exact:true}).click();await expect(email).toBeFocused();
 await page.getByRole('button',{name:'Log in',exact:true}).click();await expect(page.getByRole('alert')).toBeFocused();
 await email.fill('builder@example.test');await page.getByLabel('Password',{exact:true}).fill('synthetic-password-123');await page.getByRole('button',{name:'Log in',exact:true}).click();
 await expect(page.getByRole('button',{name:'Logging in…',exact:true})).toBeDisabled();await expect(email).toBeDisabled();await boundary();release();
 await expect(page.getByRole('alert')).toContainText('Check your email and password.');await fits(page);
});

test('MFA pending copy, readable OTP and expiry recovery focus match the real state',async({page})=>{
 await providers(page);await page.route('**/api/v1/auth/login',route=>route.fulfill({json:{mfaRequired:true}}));
 let release!:()=>void;const held=new Promise<void>(resolve=>{release=resolve;});
 await page.route('**/api/v1/auth/login/mfa',async route=>{await held;await route.fulfill({status:401,json:{error:{code:'MFA_EXPIRED',message:'Sign in again.'}}});});
 await page.goto('/login');await page.getByLabel('Email address',{exact:true}).fill('operator@example.test');await page.getByLabel('Password',{exact:true}).fill('synthetic-password-123');await page.getByRole('button',{name:'Log in',exact:true}).click();
 await expect(page.getByRole('heading',{name:'One more step',exact:true})).toBeVisible();await page.getByLabel('Authenticator code',{exact:true}).fill('123456');await page.getByRole('button',{name:'Verify and log in',exact:true}).click();
 await expect(page.getByRole('button',{name:'Verifying…',exact:true})).toBeDisabled();
 expect(await page.locator('.paper-auth-otp').evaluate(node=>{let opacity=1;for(let current:Element|null=node;current;current=current.parentElement)opacity*=Number(getComputedStyle(current).opacity);return opacity;})).toBe(1);
 await fits(page);release();await expect(page.getByRole('alert')).toHaveText('Your verification step expired. Please sign in again.');await expect(page.getByRole('alert')).toBeFocused();
 await expect(page.getByLabel('Password',{exact:true})).toHaveValue('');await expect(page.getByLabel('Email address',{exact:true})).toHaveValue('operator@example.test');
});

test('pending replacement search keeps confirmed catalogue content fully readable',async({page})=>{
 await page.goto('/');const grid=page.locator('.paper-project-grid');await expect(grid.locator('.paper-project')).toHaveCount(6);
 let release!:()=>void;const held=new Promise<void>(resolve=>{release=resolve;});
 await page.route('**/*',async route=>{const url=new URL(route.request().url());if(url.searchParams.get('q')==='Stockroom'&&url.searchParams.has('_rsc'))await held;await route.continue();});
 await page.getByRole('textbox',{name:'Search projects',exact:true}).fill('Stockroom');await page.locator('.paper-home-search').getByRole('button',{name:'Search',exact:true}).click();
 await expect(grid).toHaveAttribute('aria-busy','true');await expect(grid).toHaveCSS('opacity','1');await expect(grid.locator('.paper-project')).toHaveCount(6);
 release();await expect(grid).toHaveAttribute('aria-busy','false');await expect(grid.locator('.paper-project')).toHaveCount(1);
});

test('@cross-browser keyboard focus stays inside clipped media and separate from Save',async({page})=>{
 await page.goto('/');const preview=page.locator('.paper-project-media>.paper-project-preview').first();await preview.focus();await page.keyboard.press('Tab');await page.keyboard.press('Shift+Tab');await expect(preview).toBeFocused();
 const ring=await preview.evaluate(node=>{const media=node.parentElement!,s=getComputedStyle(media,'::after'),box=media.getBoundingClientRect();return{visible:node.matches(':focus-visible'),content:s.content,inset:parseFloat(s.top),border:parseFloat(s.borderTopWidth),shadow:s.boxShadow,width:box.width,height:box.height};});
 expect(ring.visible).toBe(true);expect(ring.content).not.toBe('none');expect(ring.border).toBeGreaterThanOrEqual(3);expect(ring.inset).toBeGreaterThanOrEqual(2);expect(ring.inset*2+ring.border*2).toBeLessThan(Math.min(ring.width,ring.height));expect(ring.shadow).not.toBe('none');
 await expect(preview).toHaveAttribute('href',/^\/products\//);await expect(preview.locator('..').getByRole('button',{name:/^(?:Save|Unsave) /})).toHaveCount(1);
});

test('shared form confirmation distinguishes pending, neutral and failed outcomes without fading',async({page})=>{
 const bundle=buildSync({stdin:{contents:`import {createRoot}from'react-dom/client';import{Field,TeamForm}from'./app/components/workspace-form';function Harness(){return <TeamForm label="Search builders" pendingLabel="Searching…" pendingMessage="Finding matching builders…" successMessage="Search complete." onSave={()=>new Promise((resolve,reject)=>{window.finishJoinery=outcome=>outcome==='error'?reject(new Error('Search unavailable. Try again.')):resolve(outcome==='neutral'?false:{items:[]});})}><Field name="q" label="Builder name or skill" value="React"/></TeamForm>}createRoot(document.getElementById('root')).render(<Harness/>);`,loader:'tsx',resolveDir:process.cwd()},bundle:true,write:false,platform:'browser',format:'iife',jsx:'automatic',define:{'process.env.NODE_ENV':'"test"'}}).outputFiles[0].text;
 // Use the real compiled cascade, including the global box-sizing reset and package imports.
 await page.goto('/');
 const styles=await page.locator('link[rel="stylesheet"]').evaluateAll(links=>links.map(link=>link.getAttribute('href')).filter((href):href is string=>Boolean(href?.startsWith('/_next/static/css/'))));
 expect(styles.length).toBeGreaterThan(0);
 const styleLinks=styles.map(href=>`<link rel="stylesheet" href="${href.replaceAll('&','&amp;').replaceAll('"','&quot;')}">`).join('');
 await page.route('**/joinery-form-fixture',route=>route.fulfill({contentType:'text/html',body:`<!doctype html><html lang="en"><head><meta name="viewport" content="width=device-width,initial-scale=1">${styleLinks}</head><body><div id="root"></div></body></html>`}));await page.goto('/joinery-form-fixture');
 await page.addScriptTag({content:bundle});
 const submit=page.getByRole('button',{name:'Search builders',exact:true}),status=page.getByRole('status');
 const finish=(outcome:string)=>page.evaluate(value=>(window as unknown as {finishJoinery:(outcome:string)=>void}).finishJoinery(value),outcome);
 await submit.click();await expect(page.getByRole('button',{name:'Searching…',exact:true})).toBeDisabled();await expect(status).toHaveText('Finding matching builders…');await expect(status).not.toContainText('Search complete.');
 await finish('success');await expect(status).toHaveText('Search complete.');await submit.click();await finish('neutral');await expect(status).toBeEmpty();await submit.click();await finish('error');
 await expect(page.getByRole('alert')).toContainText('Search unavailable. Try again.');await expect(page.getByRole('alert')).toBeFocused();await expect(page.getByLabel('Builder name or skill',{exact:true})).toHaveValue('React');await expect(status).toBeEmpty();await fits(page);
 expect(await page.getByRole('alert').evaluate(node=>{for(const a of node.getAnimations()){a.pause();a.currentTime=0;}return getComputedStyle(node).opacity;})).toBe('1');
});
