import {test,expect,type Page,type Frame,type TestInfo} from '@playwright/test';
import {readFileSync} from 'node:fs';
import {createRequire} from 'node:module';
import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';
const axe=readFileSync(createRequire(import.meta.url).resolve('axe-core/axe.min.js'),'utf8');
const index=JSON.parse(readFileSync('app/components/frameworks/index.json','utf8'));
async function reflow(page:Page){expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1)).toBe(true);}
async function scan(surface:Page|Frame){await surface.evaluate(axe);const findings=await surface.evaluate(async()=> (window as any).axe.run(document.querySelector('main'),{runOnly:{type:'tag',values:['wcag2a','wcag2aa','wcag21aa','wcag22aa']}}));expect(findings.violations.filter((finding:any)=>['serious','critical'].includes(finding.impact)).map((finding:any)=>({id:finding.id,targets:finding.nodes.map((node:any)=>node.target)}))).toEqual([]);}
async function screenshot(page:Page,info:TestInfo,name:string){await info.attach(name+'-'+info.project.name,{body:await page.screenshot({fullPage:true,animations:'disabled'}),contentType:'image/png'});}
async function iot(page:Page){await page.goto('/labs/iot');await expect(page.getByTestId('iot-run-id')).toHaveText(/^[a-f0-9]{8}-/);}
async function readings(page:Page,temperature:string,humidity='45',threshold='30'){await page.getByRole('spinbutton',{name:'Temperature',exact:true}).fill(temperature);await page.getByRole('spinbutton',{name:'Humidity',exact:true}).fill(humidity);await page.getByRole('spinbutton',{name:'Alarm threshold',exact:true}).fill(threshold);await page.getByRole('button',{name:'Publish readings',exact:true}).click();}

test('IoT runs actual browser-local state with raw per-run messages and honest hardware disclosure',async({page},info)=>{
 const mutations:string[]=[];page.on('request',request=>{if(!['GET','HEAD'].includes(request.method()))mutations.push(request.url());});await iot(page);
 await expect(page.getByText('Device / protocol simulation.',{exact:true})).toBeVisible();await expect(page.getByTestId('iot-actuator')).toHaveText('OFF');
 await readings(page,'37.5','62','35');await expect(page.getByTestId('iot-state-temperatureC')).toContainText('37.5');await expect(page.getByTestId('iot-state-humidityPercent')).toContainText('62');await expect(page.getByTestId('iot-actuator')).toHaveText('ON');
 const topic=await page.getByTestId('iot-assigned-topic').innerText();await page.getByText('Raw message',{exact:true}).click();await expect(page.locator('.iot-message-list pre')).toContainText(topic);await expect(page.locator('.iot-message-list pre')).toContainText('"actuatorOn": true');
 await readings(page,'20','40','35');await expect(page.getByTestId('iot-actuator')).toHaveText('OFF');await expect(page.locator('.iot-message-list>li')).toHaveCount(2);await reflow(page);await scan(page);await screenshot(page,info,'iot-changed-inputs');expect(mutations).toEqual([]);
});

test('IoT disconnection rejects publication and recovery/reset preserve the active run boundary',async({page})=>{
 await iot(page);const run=await page.getByTestId('iot-run-id').innerText();await readings(page,'40');await page.getByRole('button',{name:'Disconnect',exact:true}).click();await expect(page.getByTestId('iot-sequence')).toHaveText('2');
 await readings(page,'10');await expect(page.getByRole('alert')).toContainText('Connect the simulated device before publishing.');await expect(page.getByTestId('iot-state-temperatureC')).toContainText('40');await expect(page.locator('.iot-message-list>li')).toHaveCount(1);
 await page.getByRole('button',{name:'Reconnect',exact:true}).click();await readings(page,'10');await expect(page.getByTestId('iot-state-temperatureC')).toContainText('10');await expect(page.getByTestId('iot-actuator')).toHaveText('OFF');
 await page.getByRole('button',{name:'Reset device',exact:true}).click();await expect(page.getByTestId('iot-run-id')).toHaveText(run);await expect(page.getByTestId('iot-sequence')).toHaveText('5');await expect(page.locator('.iot-message-list>li')).toHaveCount(0);await expect(page.getByTestId('iot-state-temperatureC')).toContainText('24');await scan(page);
});

test('IoT invalid input has linked inline errors and a foreign topic cannot modify session state',async({page})=>{
 await iot(page);await readings(page,'100','101','-1');const alert=page.getByRole('alert');await expect(alert).toBeFocused();await expect(page.getByRole('spinbutton',{name:'Temperature',exact:true})).toHaveAttribute('aria-invalid','true');await expect(page.getByRole('spinbutton',{name:'Humidity',exact:true})).toHaveAttribute('aria-invalid','true');await expect(page.getByRole('spinbutton',{name:'Alarm threshold',exact:true})).toHaveAttribute('aria-invalid','true');await expect(page.getByTestId('iot-sequence')).toHaveText('0');await reflow(page);await scan(page);
 await page.getByText('Session topic',{exact:true}).click();await page.getByLabel('Publish topic',{exact:true}).fill('getlancer/foreign/telemetry');await readings(page,'24');await expect(alert).toContainText('Use the exact topic assigned to this session.');await expect(page.getByLabel('Publish topic',{exact:true})).toHaveAttribute('aria-invalid','true');await expect(page.getByTestId('iot-sequence')).toHaveText('0');
 const previous=await page.getByTestId('iot-run-id').innerText();await page.getByRole('button',{name:'New isolated session',exact:true}).click();await expect(page.getByTestId('iot-run-id')).not.toHaveText(previous);await expect(page.getByLabel('Publish topic',{exact:true})).toHaveValue(await page.getByTestId('iot-assigned-topic').innerText());await expect(page.getByRole('alert')).toHaveCount(0);
});

test('IoT independently opened pages use unique topics and bounded message histories',async({page,context})=>{
 await iot(page);const sibling=await context.newPage();try{await iot(sibling);expect(await sibling.getByTestId('iot-run-id').innerText()).not.toBe(await page.getByTestId('iot-run-id').innerText());await readings(sibling,'50');await expect(page.getByTestId('iot-sequence')).toHaveText('0');await expect(page.getByTestId('iot-actuator')).toHaveText('OFF');}finally{await sibling.close();}
 await readings(page,'24.1234567');await expect(page.getByRole('alert')).toContainText(/six decimal places/);await expect(page.getByTestId('iot-sequence')).toHaveText('0');
 for(let position=0;position<21;position++)await readings(page,String(position));await expect(page.locator('.iot-message-list>li')).toHaveCount(20);await expect(page.getByRole('list',{name:'Published messages',exact:true})).toHaveAttribute('tabindex','0');await expect(page.getByRole('list',{name:'Session events',exact:true})).toHaveAttribute('tabindex','0');await expect(page.locator('.iot-event-list>li')).toHaveCount(20);await expect(page.getByTestId('iot-sequence')).toHaveText('21');await reflow(page);
});

test('all three real native runtimes execute their distinct original controls under actual hash CSP',async({page,request},info)=>{
 test.setTimeout(120000);const errors:string[]=[];page.on('pageerror',error=>errors.push(error.message));await page.goto('/components/frameworks');await expect(page.getByRole('heading',{name:'Three frameworks. Real native components.',exact:true})).toBeVisible();
 for(const entry of index.items){await page.locator('.framework-card').filter({hasText:entry.label}).click();const iframe=page.locator('.framework-stage iframe'),frame=page.frameLocator('.framework-stage iframe');await expect(iframe).toHaveAttribute('sandbox','allow-scripts');await expect(frame.locator('main h1')).toBeVisible();const response=await request.get(entry.previewUrl),policy=response.headers()['content-security-policy'];expect(response.status()).toBe(200);expect(policy).toContain('sandbox allow-scripts');expect(policy).toContain("connect-src 'none'");expect(policy).not.toContain('unsafe-inline');expect(policy).not.toContain('unsafe-eval');expect(policy).not.toContain('allow-same-origin');expect(createHash('sha256').update(await response.body()).digest('hex')).toBe(entry.previewSha256);
  if(entry.id==='vue'){await frame.getByRole('button',{name:'Save brief',exact:true}).click();await expect(frame.getByRole('alert')).toContainText('3–60');await frame.getByLabel('Project name',{exact:true}).fill('Original Vue lab');await frame.getByRole('button',{name:'Save brief',exact:true}).click();await expect(frame.locator('output')).toContainText('Brief saved: Original Vue lab · 1 local save');}
  if(entry.id==='svelte'){await frame.getByLabel('Filter queue',{exact:true}).selectOption('Backend');await expect(frame.locator('li')).toHaveCount(1);await frame.getByRole('button',{name:'Complete Queue worker',exact:true}).click();await expect(frame.locator('output')).toContainText('1 of 3 complete · 1 visible');await expect(frame.getByRole('button',{name:'Reopen Queue worker',exact:true})).toHaveAttribute('aria-pressed','true');}
  if(entry.id==='angular'){await frame.getByLabel('Review interface',{exact:true}).check();await frame.getByLabel('Check keyboard flow',{exact:true}).check();await frame.getByLabel('Inspect source rights',{exact:true}).check();await expect(frame.locator('output')).toContainText('Ready for a local review.');}
  const inner=page.frames().find(candidate=>candidate.url().endsWith(entry.previewUrl))!;await scan(inner);await reflow(page);await screenshot(page,info,'native-'+entry.id);
 }
 await scan(page);expect(errors).toEqual([]);
});

test('native preview sandbox rejects account, network, navigation and unreviewed scripts',async({page})=>{
 await page.goto('/components/frameworks');const iframe=page.locator('.framework-stage iframe');await expect(page.frameLocator('.framework-stage iframe').getByRole('heading',{name:'Project brief',exact:true})).toBeVisible();expect(await iframe.evaluate((element:HTMLIFrameElement)=>{try{return element.contentWindow!.document.body.textContent;}catch{return 'opaque';}})).toBe('opaque');
 const frame=page.frames().find(candidate=>candidate.url().endsWith('/components/frameworks/preview/vue'))!;
 const results=await frame.evaluate(async()=>{
  const probe:{cookies:boolean;storage:boolean;parent:boolean;network:boolean;popup:boolean;script:boolean;violations:string[]}={cookies:false,storage:false,parent:false,network:false,popup:false,script:false,violations:[]};document.addEventListener('securitypolicyviolation',event=>probe.violations.push(event.effectiveDirective));
  try{document.cookie='sandbox_probe=1';probe.cookies=document.cookie.includes('sandbox_probe');}catch{}
  try{localStorage.setItem('sandbox_probe','1');probe.storage=true;}catch{}
  try{probe.parent=Boolean(parent.document.body);}catch{}
  try{await fetch('/api/v1/security-probe');probe.network=true;}catch{}
  try{probe.popup=Boolean(window.open('/','_blank'));}catch{}
  const script=document.createElement('script');script.textContent='window.unreviewedPreviewExecuted=true';document.body.append(script);await new Promise(resolve=>setTimeout(resolve,100));probe.script=Boolean((window as any).unreviewedPreviewExecuted);return probe;
 });
 expect({...results,violations:undefined}).toEqual({cookies:false,storage:false,parent:false,network:false,popup:false,script:false,violations:undefined});expect(results.violations).toContain('connect-src');expect(results.violations).toContain('script-src-elem');await reflow(page);
});

test('framework free downloads preserve the exact original lockfiles and reject unknown routes',async({page,request})=>{
 await page.goto('/components/frameworks');
 for(const entry of index.items){await page.locator('.framework-card').filter({hasText:entry.label}).click();const downloadWait=page.waitForEvent('download');await page.getByRole('link',{name:'Download '+entry.label+' source',exact:true}).click();const download=await downloadWait,bytes=readFileSync((await download.path())!);expect(createHash('sha256').update(bytes).digest('hex')).toBe(entry.archiveSha256);const files=JSON.parse(execFileSync('python3',['-c','import sys,tarfile,io,json; z=tarfile.open(fileobj=io.BytesIO(sys.stdin.buffer.read()),mode="r:gz"); print(json.dumps({m.name:z.extractfile(m).read().decode() for m in z.getmembers()}))'],{input:bytes,encoding:'utf8'}));expect(JSON.parse(files['package-lock.json']).lockfileVersion).toBe(3);expect(files[entry.primaryFile]).toBe(readFileSync('examples/frameworks/'+entry.id+'/'+entry.primaryFile,'utf8'));expect(files.LICENSE).toContain('MIT License');}
 expect((await request.get('/components/frameworks/preview/react')).status()).toBe(404);expect((await request.get('/components/frameworks/source/react')).status()).toBe(404);expect((await request.post('/components/frameworks/preview/vue',{data:'uploaded'})).status()).toBe(405);
});

test('new pages and native phone preview retain usable targets, labels and responsive layout',async({page})=>{
 await iot(page);for(const label of ['Temperature','Humidity','Alarm threshold']){const input=page.getByRole('spinbutton',{name:label,exact:true});expect(Number(await input.evaluate(element=>getComputedStyle(element).fontSize.replace('px','')))).toBeGreaterThanOrEqual(16);expect((await input.boundingBox())!.height).toBeGreaterThanOrEqual(44);}
 await page.getByRole('spinbutton',{name:'Temperature',exact:true}).focus();await page.keyboard.press('Tab');await expect(page.getByRole('spinbutton',{name:'Humidity',exact:true})).toBeFocused();await reflow(page);await scan(page);
 await page.goto('/components/frameworks');await page.getByRole('button',{name:'Phone',exact:true}).click();const iframe=page.locator('.framework-stage iframe');await expect(iframe).toHaveAttribute('sandbox','allow-scripts');expect((await iframe.boundingBox())!.width).toBeLessThanOrEqual(377);await reflow(page);await scan(page);
 const frame=page.frames().find(candidate=>candidate.url().endsWith('/components/frameworks/preview/vue'))!;await expect(page.frameLocator('.framework-stage iframe').getByLabel('Project name',{exact:true})).toBeVisible();expect(await frame.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1)).toBe(true);
 await page.getByRole('button',{name:'Reset preview',exact:true}).click();await expect(page.frameLocator('.framework-stage iframe').locator('output')).toContainText('No brief saved yet.');
});
