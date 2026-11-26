import {expect,test} from '@playwright/test';
import {build} from 'esbuild';

test('homepage video is silent, has no controls and pauses when hover ends',async({page})=>{
  const bundle=await build({
    stdin:{
      contents:`
        import {createRoot} from 'react-dom/client';
        import Preview from './app/components/preview';
        window.previewCalls=[];
        const nativeMatchMedia=window.matchMedia.bind(window);
        window.motionNotice=false;
        window.matchMedia=query=>query==='(any-hover: hover)'
          ?{matches:true}
          :query==='(prefers-reduced-motion: reduce)'
            ?{get matches(){return window.motionNotice;},addEventListener(){},removeEventListener(){}}
            :nativeMatchMedia(query);
        HTMLMediaElement.prototype.play=function(){window.previewCalls.push({type:'play',muted:this.muted});return Promise.resolve();};
        HTMLMediaElement.prototype.pause=function(){window.previewCalls.push({type:'pause'});};
        createRoot(document.querySelector('main')).render(
          <Preview product={{title:'Appointment Service',imageUrl:'/poster.jpg',previewVideoUrl:'/sample.mp4'}} hoverVideo/>
        );
      `,
      loader:'tsx',resolveDir:process.cwd(),
    },
    bundle:true,write:false,platform:'browser',format:'iife',jsx:'automatic',
  });
  await page.route('**/homepage-hover-fixture',route=>route.fulfill({
    contentType:'text/html',body:'<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1"><main></main>',
  }));
  await page.route('**/sample.mp4',route=>route.fulfill({status:404}));
  await page.route('**/poster.jpg',route=>route.fulfill({
    contentType:'image/png',
    body:Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR4nGMAAQAABQABDQottAAAAABJRU5ErkJggg==','base64'),
  }));
  await page.goto('/homepage-hover-fixture');
  await page.addScriptTag({content:bundle.outputFiles[0].text});
  const video=page.getByLabel('Appointment Service video preview');
  await expect(video).toBeVisible();
  await expect(video).toHaveAttribute('poster','/poster.jpg');
  const still=page.locator('.listing-hover-poster');
  await expect(still).toBeVisible();
  expect(await video.evaluate((node:HTMLVideoElement)=>node.controls)).toBe(false);
  const calls=()=>page.evaluate(()=> (window as unknown as {previewCalls:unknown[]}).previewCalls);
  await video.dispatchEvent('pointerover',{pointerType:'touch'});
  expect(await calls()).toEqual([]);
  await video.dispatchEvent('pointerover',{pointerType:'mouse'});
  await expect.poll(calls).toEqual([{type:'play',muted:true}]);
  await video.dispatchEvent('playing');
  await expect(still).toHaveCount(0);
  await video.dispatchEvent('pointerout',{pointerType:'mouse'});
  await expect.poll(calls).toEqual([{type:'play',muted:true},{type:'pause'}]);
  await expect(still).toBeVisible();
  await page.evaluate(()=>{(window as unknown as {motionNotice:boolean}).motionNotice=true;});
  await video.dispatchEvent('pointerover',{pointerType:'mouse'});
  expect(await calls()).toEqual([{type:'play',muted:true},{type:'pause'}]);
});
