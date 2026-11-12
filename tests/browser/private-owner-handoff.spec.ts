import {test,expect,type Page} from '@playwright/test';
import {build} from 'esbuild';

const A='00000000-0000-4000-8000-000000000001',B='00000000-0000-4000-8000-000000000002';
const runA='11111111-1111-4111-8111-111111111111',runB='22222222-2222-4222-8222-222222222222';
type Call={path:string;method:string;account:string|null;key:string|null;body:unknown};
type Pending=Call&{resolve(body:unknown,status?:number,type?:string):void};
type Harness=Window&{calls:Call[];pending:Pending[]};
const info={page:0,size:50,hasMore:false,totalItems:0,totalPages:0};
const denied={error:{code:'ACCOUNT_CHANGED',message:'The account bound to this page is no longer current.'}};
const runtime={enabled:true,reason:'Synthetic admission fixture.'};
const sourceHash='a'.repeat(64),manifest={id:'private-handoff',title:'Account boundary lab',summary:'Synthetic lab source.',language:'Go',framework:'HTTP',sourceSha256:sourceHash,sourceUrl:'/components/reviewed-cache',setup:'Synthetic setup only.',executionMode:'ISOLATED',availability:runtime,scenarios:[{id:'inspect',title:'Inspect',description:'Inspect fixture state.',inputs:[{name:'message',label:'Message',type:'TEXT',required:true,maxLength:100,min:0,max:0,choices:[]}],operations:[{id:'inspect',label:'Inspect'}]}]};
const run=(id=runA)=>({id,manifestId:manifest.id,scenarioId:'inspect',status:'RUNNING',verified:true,executionMode:'ISOLATED',reason:'Private run '+id,requestedAt:new Date().toISOString(),expiresAt:new Date(Date.now()+300000).toISOString()});
const quota=(activeRunId:string|null=runA)=>({dailyLimit:5,remaining:4,resetAt:new Date(Date.now()+86400000).toISOString(),activeRunId,runtime});
const observation={runId:runA,sourceHash,requestId:'33333333-3333-4333-8333-333333333333',pattern:'cache',status:200,durationMs:3,state:{cache:'MISS',storeReads:1,ttlMs:2000},events:[{runId:runA,sourceHash,requestId:'33333333-3333-4333-8333-333333333333',sequence:1,type:'CACHE_MISS',edge:'service-redis',recordedAt:'2026-11-05T00:00:00Z',summary:'Private actor A cache observation'},{runId:runA,sourceHash,requestId:'33333333-3333-4333-8333-333333333333',sequence:2,type:'STORE_READ',edge:'service-store',recordedAt:'2026-11-05T00:00:00Z',summary:'Private actor A store observation'}]};
const frame=(sequence=1)=>'id: '+sequence+'\ndata: '+JSON.stringify({runId:runA,sequence,type:'STATE',recordedAt:new Date().toISOString(),data:{status:'RUNNING',reason:'Private actor A activity'}})+'\n\n';

async function mount(page:Page,kind:'lab'|'team'|'business'){
 const component=kind==='lab'?'./components/labs/lab-workbench':`./app/components/${kind}-workspace`;
 const script=(await build({stdin:{contents:`
  import {createRoot} from 'react-dom/client';import Workbench from '${component}';
  window.calls=[];window.pending=[];
  // Deliberately ignore AbortSignal so an already accepted response can cross cancellation.
  window.fetch=(path,init={})=>new Promise(resolve=>{const headers=new Headers(init.headers),call={path:String(path),method:init.method||'GET',account:headers.get('X-GetLancer-Account'),key:headers.get('Idempotency-Key'),body:init.body?JSON.parse(init.body):null};window.calls.push(call);window.pending.push({...call,resolve:(body,status=200,type='application/json')=>resolve(new Response(type==='text/event-stream'?body:JSON.stringify(body),{status,headers:{'Content-Type':type}}))});});
  createRoot(document.getElementById('root')).render(<Workbench/>);
 `,resolveDir:process.cwd(),loader:'tsx'},bundle:true,write:false,format:'iife',jsx:'automatic',loader:{'.css':'empty'},define:{'process.env.NODE_ENV':'"test"'},plugins:[{name:'owner-handoff-link',setup(plugin){
  plugin.onResolve({filter:/^next\/link$/},()=>({path:'link',namespace:'fixture'}));
  plugin.onLoad({filter:/.*/,namespace:'fixture'},()=>({contents:'export default function Link({children,prefetch,...props}){return <a {...props}>{children}</a>}',loader:'jsx',resolveDir:process.cwd()}));
 }}]})).outputFiles[0].text;
 // Native operation identities require the same trustworthy origin as the app.
 // An opaque about:blank document does not expose crypto.randomUUID().
 await page.route('**/private-owner-handoff-fixture',route=>route.fulfill({contentType:'text/html',body:'<!doctype html><html lang="en"><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>Private owner handoff</title></head><body><div id="root"></div></body></html>'}));
 await page.goto('/private-owner-handoff-fixture');
 expect(await page.evaluate(()=>isSecureContext&&typeof crypto.randomUUID==='function')).toBe(true);
 await page.addScriptTag({content:script});
}
async function pending(page:Page,path:string,method='GET',account?:string){await expect.poll(()=>page.evaluate(({path,method,account})=>(window as unknown as Harness).pending.some(call=>call.path===path&&call.method===method&&(account===undefined||call.account===account)),{path,method,account})).toBe(true);}
async function respond(page:Page,path:string,body:unknown,status=200,method='GET',type='application/json',account?:string){
 await pending(page,path,method,account);await page.evaluate(({path,body,status,method,type,account})=>{const queue=(window as unknown as Harness).pending,index=queue.findIndex(call=>call.path===path&&call.method===method&&(account===undefined||call.account===account));queue.splice(index,1)[0].resolve(body,status,type);},{path,body,status,method,type,account});
}
async function settle(page:Page){await page.evaluate(()=>new Promise<void>(done=>requestAnimationFrame(()=>requestAnimationFrame(()=>done()))));}
async function labLoad(page:Page,actor=A,activeRunId:string|null=runA){
 await Promise.all([respond(page,'/api/v1/lab-manifests',{items:[manifest],runtime}),respond(page,'/api/v1/me',{id:actor,roles:['CLIENT']})]);
 await respond(page,'/api/v1/lab-quota',quota(activeRunId),200,'GET','application/json',actor);if(activeRunId)await respond(page,'/api/v1/lab-runs/'+activeRunId,run(activeRunId));
 await expect(page.getByText('Loading current lab availability…',{exact:true})).toHaveCount(0);
}
async function revealPrivateLabState(page:Page){
 await page.getByLabel('Message',{exact:true}).nth(1).fill('Private actor A input');await page.getByRole('button',{name:'Send certified request',exact:true}).click();
 await respond(page,'/api/v1/lab-runs/'+runA+'/requests',{runId:runA,output:'Private actor A output',observation},200,'POST');await expect(page.getByLabel('Actual returned state',{exact:true})).toContainText('storeReads');
 await page.getByRole('button',{name:'Refresh status & activity',exact:true}).click();await respond(page,'/api/v1/lab-runs/'+runA,run());await respond(page,'/api/v1/lab-runs/'+runA+'/events',frame(),200,'GET','text/event-stream');
 await expect(page.getByText('Private actor A activity',{exact:true})).toHaveCount(2);
}

test('lab availability handoff clears previous run, output, activity, inputs and uncertain operation identity',async({page})=>{
 await mount(page,'lab');await labLoad(page);await revealPrivateLabState(page);
 // Leave a recoverable operation identity behind, then explicitly refresh as another account.
 await page.getByRole('button',{name:'Send certified request',exact:true}).click();await respond(page,'/api/v1/lab-runs/'+runA+'/requests',{error:{code:'UNAVAILABLE',message:'Operation result uncertain.'}},503,'POST');await expect(page.getByRole('button',{name:'Retry same operation',exact:true})).toBeVisible();
 await page.getByRole('button',{name:'Refresh availability',exact:true}).click();await labLoad(page,B,runB);
 await expect(page.getByText('Run '+runB,{exact:true})).toBeVisible();await expect(page.getByText('Run '+runA,{exact:true})).toHaveCount(0);await expect(page.getByText(/Private actor A/)).toHaveCount(0);await expect(page.getByLabel('Actual returned state',{exact:true})).toHaveCount(0);await expect(page.getByLabel('Message',{exact:true}).nth(1)).toHaveValue('');await expect(page.getByRole('button',{name:'Retry same operation',exact:true})).toHaveCount(0);
 await page.getByLabel('Message',{exact:true}).nth(1).fill('Actor B input');await page.getByRole('button',{name:'Send certified request',exact:true}).click();await respond(page,'/api/v1/lab-runs/'+runB+'/requests',{runId:runB,output:'Actor B output'},200,'POST');
 const writes=await page.evaluate(()=>(window as unknown as Harness).calls.filter(call=>call.method==='POST'));expect(writes.map(call=>call.account)).toEqual([A,A,B]);expect(writes[1].key).not.toBe(writes[2].key);
 const privateCalls=await page.evaluate(()=>(window as unknown as Harness).calls.filter(call=>/lab-quota|lab-runs/.test(call.path)));expect(privateCalls.every(call=>call.account===A||call.account===B)).toBe(true);
});

test('an uncertain lab start belongs only to its original account',async({page})=>{
 await mount(page,'lab');await labLoad(page,A,null);await page.getByLabel('Message',{exact:true}).fill('Private actor A start');await page.getByRole('button',{name:'Run isolated lab',exact:true}).click();await respond(page,'/api/v1/lab-runs',{error:{code:'UNAVAILABLE',message:'Start response uncertain.'}},503,'POST');await expect(page.getByRole('button',{name:'Retry same run request',exact:true})).toBeVisible();
 await page.getByRole('button',{name:'Refresh availability',exact:true}).click();await labLoad(page,B,null);await expect(page.getByRole('button',{name:'Retry same run request',exact:true})).toHaveCount(0);await expect(page.getByLabel('Message',{exact:true})).toHaveValue('');
 await page.getByLabel('Message',{exact:true}).fill('Actor B start');await page.getByRole('button',{name:'Run isolated lab',exact:true}).click();await pending(page,'/api/v1/lab-runs','POST',B);
 const writes=await page.evaluate(()=>(window as unknown as Harness).calls.filter(call=>call.path==='/api/v1/lab-runs'&&call.method==='POST'));expect(writes.map(call=>call.account)).toEqual([A,B]);expect(writes[0].key).not.toBe(writes[1].key);expect(writes[1].body).toEqual({manifestId:manifest.id,scenarioId:'inspect',inputs:{message:'Actor B start'}});
 await respond(page,'/api/v1/lab-runs',run(runB),200,'POST');await respond(page,'/api/v1/lab-runs/'+runB+'/events','',200,'GET','text/event-stream');await respond(page,'/api/v1/lab-quota',quota(runB));
});

test('a delayed lab quota refresh cannot restore another actor’s reservation after handoff',async({page})=>{
 await mount(page,'lab');await labLoad(page);await page.getByRole('button',{name:'Stop current run',exact:true}).click();await respond(page,'/api/v1/lab-runs/'+runA+'/stop',{...run(),status:'CANCELLED',verified:false},200,'POST');await pending(page,'/api/v1/lab-quota','GET',A);
 await page.getByRole('button',{name:'Refresh availability',exact:true}).click();await labLoad(page,B,runB);
 await respond(page,'/api/v1/lab-quota',{...quota(runA),remaining:0},200,'GET','application/json',A);await settle(page);
 await expect(page.getByText('Run '+runB,{exact:true})).toBeVisible();await expect(page.getByText('Run '+runA,{exact:true})).toHaveCount(0);await expect(page.getByText('4 of 5 free runs remain today',{exact:true})).toBeVisible();
});

for(const status of [401,403])test('lab activity '+status+' clears all cached private state and retry keys',async({page})=>{
 await mount(page,'lab');await labLoad(page);await revealPrivateLabState(page);await page.getByRole('button',{name:'Refresh status & activity',exact:true}).click();await respond(page,'/api/v1/lab-runs/'+runA,run());await respond(page,'/api/v1/lab-runs/'+runA+'/events',denied,status);
 await expect(page.getByRole('alert')).toContainText('no longer current');await expect(page.getByText(/Private actor A/)).toHaveCount(0);await expect(page.getByLabel('Actual returned state',{exact:true})).toHaveCount(0);await expect(page.getByRole('heading',{name:'No run has been started here',exact:true})).toBeVisible();await expect(page.getByLabel('Retained message',{exact:true})).toHaveCount(0);await expect(page.getByLabel('Message',{exact:true})).toHaveValue('');await expect(page.getByRole('link',{name:'Sign in to check allowance',exact:true})).toBeVisible();
});

const teams=[{id:'first',slug:'first',name:'A private team',summary:'Private summary',availability:'AVAILABLE_NOW',projectRange:'Private range',ownerId:A,status:'ACTIVE',myRole:'OWNER'},{id:'second',slug:'second',name:'A second private team',summary:'Private summary',availability:'AVAILABLE_NOW',projectRange:'Private range',ownerId:A,status:'ACTIVE',myRole:'OWNER'}];
const teamIndex={items:teams,invitations:[],applications:[],requests:[],canCreate:false};
const teamWorkspace=(index=0)=>({team:teams[index],myRole:'OWNER',members:[],invitations:[],roles:[],applications:[],leads:[],projects:[],staffing:[],activity:[]});
test('embedded team moderation denial invalidates a pending successful parent reload',async({page})=>{
 await mount(page,'team');await respond(page,'/api/v1/me',{id:A,roles:['ADMIN']});await respond(page,'/api/v1/me/teams',teamIndex);await respond(page,'/api/v1/teams/first/workspace',teamWorkspace());
 await page.getByText('Teams and studios',{exact:true}).click();await page.getByRole('button',{name:'Load teams',exact:true}).click();await pending(page,'/api/v1/admin/teams');
 await page.getByLabel('Your team',{exact:true}).selectOption('second');await respond(page,'/api/v1/me',{id:A,roles:['ADMIN']});await pending(page,'/api/v1/me/teams');await respond(page,'/api/v1/admin/teams',denied,401);await expect(page.getByRole('link',{name:'Log in',exact:true})).toBeVisible();
 await respond(page,'/api/v1/me/teams',teamIndex);await respond(page,'/api/v1/teams/second/workspace',teamWorkspace(1));await settle(page);
 await expect(page.getByLabel('Your team',{exact:true})).toHaveCount(0);await expect(page.getByText('Teams and studios',{exact:true})).toHaveCount(0);await expect(page.getByRole('heading',{name:/A .*private team/})).toHaveCount(0);
});

const businesses=[{id:'first',name:'Private business one',summary:'Private hiring',ownerId:A},{id:'second',name:'Private business two',summary:'Private hiring',ownerId:A}];
const businessIndex={items:businesses,invitations:[],itemsPage:info,invitationsPage:info};
async function businessRecords(page:Page,index=0){const root='/api/v1/businesses/'+businesses[index].id;await Promise.all([respond(page,root,{business:businesses[index],myRole:'OWNER',members:[],invitations:[],activity:[],membersPage:info,invitationsPage:info,activityPage:info}),respond(page,root+'/requests',{items:[],...info}),respond(page,root+'/talent-lists',{items:[],...info})]);}
for(const afterReload of [false,true])test('concierge denial clears parent business state'+(afterReload?' after child unmount during a pending reload':''),async({page})=>{
 await mount(page,'business');await respond(page,'/api/v1/me',{id:A,roles:['ADMIN']});await respond(page,'/api/v1/businesses',businessIndex);await businessRecords(page);await pending(page,'/api/v1/admin/concierge');
 if(afterReload){await page.getByLabel('Business workspace',{exact:true}).selectOption('second');await respond(page,'/api/v1/me',{id:A,roles:['ADMIN']});await pending(page,'/api/v1/businesses');}
 await respond(page,'/api/v1/admin/concierge',denied,401);await expect(page.getByRole('link',{name:'Log in',exact:true})).toBeVisible();
 if(afterReload){await respond(page,'/api/v1/businesses',businessIndex);await businessRecords(page,1);await settle(page);}
 await expect(page.getByLabel('Business workspace',{exact:true})).toHaveCount(0);await expect(page.getByRole('heading',{name:'Concierge requests',exact:true})).toHaveCount(0);await expect(page.getByRole('button',{name:'Create business',exact:true})).toHaveCount(0);
});

test('a previous concierge actor’s late denial cannot clear a successfully loaded new actor',async({page})=>{
 await mount(page,'business');await respond(page,'/api/v1/me',{id:A,roles:['ADMIN']});await respond(page,'/api/v1/businesses',businessIndex);await businessRecords(page);await pending(page,'/api/v1/admin/concierge');
 await page.getByLabel('Business workspace',{exact:true}).selectOption('second');await respond(page,'/api/v1/me',{id:B,roles:['ADMIN']});await respond(page,'/api/v1/businesses',businessIndex);await businessRecords(page,1);await expect(page.getByLabel('Business workspace',{exact:true})).toHaveValue('second');
 await respond(page,'/api/v1/admin/concierge',denied,401);await respond(page,'/api/v1/admin/concierge',{items:[],history:[],itemsPage:info,historyPage:info});await settle(page);
 await expect(page.getByLabel('Business workspace',{exact:true})).toHaveValue('second');await expect(page.getByRole('heading',{name:'Concierge requests',exact:true})).toBeVisible();await expect(page.getByRole('link',{name:'Log in',exact:true})).toHaveCount(0);
});
