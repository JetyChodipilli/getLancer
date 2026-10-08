import {test,expect} from '@playwright/test';

test('component shell records mobile 4G lab paint, layout and interaction timing',async({page},info)=>{
 test.setTimeout(180000);await page.setViewportSize({width:390,height:844});
 const session=await page.context().newCDPSession(page);
 await session.send('Emulation.setCPUThrottlingRate',{rate:4});await session.send('Network.enable');
 await session.send('Network.setCacheDisabled',{cacheDisabled:true});
 await session.send('Network.emulateNetworkConditions',{offline:false,latency:150,downloadThroughput:1_600_000/8,uploadThroughput:750_000/8});
 await page.addInitScript(()=>{
  const metrics={lcp:0,cls:0,interaction:0};(window as any).shellMetrics=metrics;
  new PerformanceObserver(list=>{for(const entry of list.getEntries())metrics.lcp=entry.startTime;}).observe({type:'largest-contentful-paint',buffered:true});
  new PerformanceObserver(list=>{for(const entry of list.getEntries() as any){if(!entry.hadRecentInput)metrics.cls+=entry.value;}}).observe({type:'layout-shift',buffered:true});
  new PerformanceObserver(list=>{for(const entry of list.getEntries() as any){if(entry.interactionId)metrics.interaction=Math.max(metrics.interaction,entry.duration);}}).observe({type:'event',buffered:true,durationThreshold:16} as any);
 });
 const routes:Record<string,any[]>={};
 for(const path of ['/components','/components/feedback-form']){
  routes[path]=[];
  for(let sample=0;sample<3;sample++){
   await page.goto(path);await expect(page.locator('main h1')).toBeVisible();await page.evaluate(()=>document.fonts.ready);await page.waitForTimeout(500);
   if(path==='/components')await page.getByLabel('Search components',{exact:true}).pressSequentially('form',{delay:50});
   else {await page.getByRole('button',{name:'Phone',exact:true}).click();await expect(page.getByRole('button',{name:'Phone',exact:true})).toHaveAttribute('aria-pressed','true');}
   await page.waitForTimeout(200);routes[path].push(await page.evaluate(()=>(window as any).shellMetrics));
  }
 }
 const report={profile:{viewport:'390x844',cpuSlowdown:4,latencyMs:150,downloadKbps:1600,uploadKbps:750,cache:'disabled',samplesPerRoute:3},scope:'App shell only; separate iframe execution. Lab proxy, not field p75 or measured full-session INP.',routes};
 await info.attach('v46-mobile-lab',{body:JSON.stringify(report,null,2),contentType:'application/json'});
 console.log('V4.6 mobile lab '+JSON.stringify(report));
 for(const samples of Object.values(routes))for(const sample of samples){expect(sample.lcp).toBeGreaterThan(0);expect(Number.isFinite(sample.cls)).toBe(true);}
 await session.detach();
});
