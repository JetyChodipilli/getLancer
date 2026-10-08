import {test,expect} from '@playwright/test';
import {componentPreviewDocument} from '../../lib/component-preview';
import {COMPONENT_SECURITY_POLICY} from '../../ops/demo-publisher/server.mjs';

test('uploaded preview runs in an opaque child and blocks app access, network and navigation',async({page})=>{
 const origin='https://player.vimeo.com',attempts:string[]=[];
 await page.route(origin+'/**',route=>route.fulfill({contentType:'text/html',headers:{'Content-Security-Policy':COMPONENT_SECURITY_POLICY},body:`<!doctype html><button id="check">Check isolation</button><output id="result"></output><button id="navigate">Navigate to application</button><script>
 document.getElementById('check').onclick=async()=>{let parentBlocked=false,storageBlocked=false,networkBlocked=false;try{parent.document.body}catch{parentBlocked=true}try{localStorage.setItem('attack','1')}catch{storageBlocked=true}try{await fetch('http://127.0.0.1:3000/api/v1/me')}catch{networkBlocked=true}document.getElementById('result').textContent=JSON.stringify({parentBlocked,storageBlocked,networkBlocked})};document.getElementById('navigate').onclick=()=>location.href='http://127.0.0.1:3000/forbidden-preview-navigation';
 </script>`}));
 await page.goto('/components/portfolio-card');await page.getByRole('button',{name:'Phone',exact:true}).click();await expect(page.getByRole('button',{name:'Phone',exact:true})).toHaveAttribute('aria-pressed','true');
 await page.route('**/forbidden-preview-navigation',route=>{attempts.push(route.request().url());return route.abort();});
 const document=componentPreviewDocument(origin)!;
 await page.evaluate(html=>{const container=window.document.createElement('section');container.style.cssText='position:fixed;top:80px;left:0;right:0;max-width:720px;margin:auto;z-index:9999;background:white';const frame=window.document.createElement('iframe');frame.id='uploaded-boundary';frame.title='Uploaded boundary';frame.setAttribute('sandbox','allow-scripts allow-forms');frame.style.cssText='display:block;width:100%;height:300px;border:0';frame.srcdoc=html;container.append(frame);window.document.body.append(container);},document);
 const outer=await (await page.locator('#uploaded-boundary').elementHandle())!.contentFrame();await outer!.evaluate(()=>{(window as any).blockedNavigations=0;addEventListener('securitypolicyviolation',e=>{if(e.violatedDirective==='frame-src')(window as any).blockedNavigations++;});});
 const child=page.frameLocator('#uploaded-boundary').frameLocator('iframe');
 await child.getByRole('button',{name:'Check isolation'}).click();await expect(child.locator('output')).toHaveText('{"parentBlocked":true,"storageBlocked":true,"networkBlocked":true}');
 await child.getByRole('button',{name:'Navigate to application'}).click();await expect.poll(()=>outer!.evaluate(()=>(window as any).blockedNavigations)).toBeGreaterThan(0);expect(attempts).toEqual([]);
 expect(await page.evaluate(()=>localStorage.getItem('attack'))).toBeNull();await expect(page).toHaveURL(/\/components\/portfolio-card$/);
});
