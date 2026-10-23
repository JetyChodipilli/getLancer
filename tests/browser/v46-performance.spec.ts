import {test,expect} from '@playwright/test';
import {verifyPerformanceSamples} from '../acceptance-metrics.mjs';
type ShellMetrics={lcp:number;cls:number;interactionMs:number;inputSamples:number};
type MetricWindow=Window & {shellMetrics:ShellMetrics};
type PreviewWindow=Window & {previewOperationMs?:number};
type LayoutShiftEntry=PerformanceEntry & {hadRecentInput:boolean;value:number};

test('new component and college shells enforce sampled mobile 4G paint, layout and interaction thresholds',async({page},info)=>{
 test.setTimeout(240000);await page.setViewportSize({width:390,height:844});
 const session=await page.context().newCDPSession(page);
 await session.send('Emulation.setCPUThrottlingRate',{rate:4});await session.send('Network.enable');
 await session.send('Network.setCacheDisabled',{cacheDisabled:true});
 await session.send('Network.emulateNetworkConditions',{offline:false,latency:150,downloadThroughput:1_600_000/8,uploadThroughput:750_000/8});
 await page.addInitScript(()=>{
  const metrics={lcp:0,cls:0,interactionMs:0,inputSamples:0};(window as unknown as MetricWindow).shellMetrics=metrics;
  new PerformanceObserver(list=>{for(const entry of list.getEntries())metrics.lcp=entry.startTime;}).observe({type:'largest-contentful-paint',buffered:true});
  new PerformanceObserver(list=>{for(const entry of list.getEntries() as LayoutShiftEntry[]){if(!entry.hadRecentInput)metrics.cls+=entry.value;}}).observe({type:'layout-shift',buffered:true});
  // Trusted input through two animation frames is an explicit paint proxy, not full-session INP.
  for(const kind of ['keydown','pointerup'])addEventListener(kind,event=>{
   if(!event.isTrusted)return;const started=performance.now();
   requestAnimationFrame(()=>requestAnimationFrame(()=>{metrics.inputSamples++;metrics.interactionMs=Math.max(metrics.interactionMs,performance.now()-started);}));
  },{capture:true});
 });
 const routes:Record<string,ShellMetrics[]>={},previewCosts:unknown[]=[];
 try{
  for(const path of ['/components','/components/feedback-form','/college-projects','/college-projects/stockroom']){
   routes[path]=[];
   for(let sample=0;sample<3;sample++){
    const navigationStarted=performance.now();await page.goto(path);await expect(page.locator('main h1')).toBeVisible();await page.evaluate(()=>document.fonts.ready);
    await expect.poll(()=>page.evaluate(()=>(window as unknown as MetricWindow).shellMetrics.lcp)).toBeGreaterThan(0);
    if(path==='/components')await page.getByLabel('Search components',{exact:true}).pressSequentially('form',{delay:50});
    else if(path==='/college-projects')await page.getByLabel('Search college projects',{exact:true}).pressSequentially('stock',{delay:50});
    else if(path==='/components/feedback-form'){
     await page.getByRole('button',{name:'Phone',exact:true}).click();await expect(page.getByRole('button',{name:'Phone',exact:true})).toHaveAttribute('aria-pressed','true');
     const iframe=page.locator('main iframe').first();await expect(iframe).toBeVisible();
     const handle=await iframe.elementHandle(),frame=await handle!.contentFrame();expect(frame).not.toBeNull();
     await expect(frame!.getByLabel('Project name',{exact:true})).toBeVisible();
     await frame!.waitForFunction(()=>performance.getEntriesByType('paint').some(entry=>entry.name==='first-contentful-paint'));
     const readyAfterNavigationMs=performance.now()-navigationStarted;
     await frame!.evaluate(()=>addEventListener('submit',()=>{const started=performance.now();requestAnimationFrame(()=>requestAnimationFrame(()=>{(window as PreviewWindow).previewOperationMs=performance.now()-started;}));},{capture:true,once:true}));
     await frame!.getByLabel('Project name',{exact:true}).fill('Measured synthetic project');
     await frame!.getByLabel('What should it solve?',{exact:true}).fill('Measure the isolated original form interaction.');
     await frame!.getByRole('button',{name:'Save brief',exact:true}).click();await expect(frame!.locator('output')).toContainText('Brief saved');
     await frame!.waitForFunction(()=>((window as PreviewWindow).previewOperationMs||0)>0);
     previewCosts.push({route:path,sample,readyAfterNavigationMs,scope:'Separate isolated iframe navigation, paint and submit-to-paint proxy. Parent shell readiness is included in readyAfterNavigationMs.',timing:await frame!.evaluate(()=>({operationToPaintProxyMs:(window as PreviewWindow).previewOperationMs,navigation:performance.getEntriesByType('navigation').map(entry=>({duration:entry.duration,domContentLoadedEventEnd:(entry as PerformanceNavigationTiming).domContentLoadedEventEnd})),paint:performance.getEntriesByType('paint').map(entry=>({name:entry.name,startTime:entry.startTime}))}))});
    }else await page.getByRole('link',{name:'Overview',exact:true}).click();
    await page.evaluate(()=>new Promise<void>(resolve=>requestAnimationFrame(()=>requestAnimationFrame(()=>resolve()))));
    await expect.poll(()=>page.evaluate(()=>(window as unknown as MetricWindow).shellMetrics.inputSamples)).toBeGreaterThan(0);
    const measured=await page.evaluate(()=>(window as unknown as MetricWindow).shellMetrics);expect(measured.interactionMs).toBeGreaterThan(0);routes[path].push(measured);
   }
  }
  const report={profile:{viewport:'390x844',cpuSlowdown:4,latencyMs:150,downloadKbps:1600,uploadKbps:750,cache:'disabled',samplesPerRoute:3},thresholds:{lcpP75Ms:2500,clsP75:.1,inputToNextPaintProxyP75Ms:200},scope:'Automated browser lab samples on CI hardware. Interaction is an input-to-next-paint proxy, not field p75 INP or launch-device acceptance.',routes,previewCosts};
  await info.attach('new-route-mobile-lab',{body:JSON.stringify(report,null,2),contentType:'application/json'});
  for(const [path,samples] of Object.entries(routes))console.log('Measured mobile shell '+path+' '+JSON.stringify(verifyPerformanceSamples(samples)));
 }finally{await session.detach();}
});
