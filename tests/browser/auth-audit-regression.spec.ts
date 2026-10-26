import {test,expect,type Page} from '@playwright/test';
import {buildSync} from 'esbuild';
import {readFileSync} from 'node:fs';

type RequestRow={path:string;method:string;body?:string;resolve:(response:Response)=>void};
type HarnessWindow=Window&{requests:Omit<RequestRow,'resolve'>[];pending:RequestRow[];reloadCalls:number;reloadPending:((ok:boolean)=>void)[]};
const profile={display_name:'First builder',headline:'A useful builder headline',bio:'A detailed description of the original work and experience.',technology:'Java',category:'Business',github_url:'',linkedin_url:'',website_url:'',country:'IN',time_zone:'Asia/Kolkata',languages:'English',availability_status:'BOOKED_UNTIL',booked_until:'2040-05-01',approval_status:'DRAFT'};

async function fixture(page:Page,kind:'account'|'profile'|'login'|'signup'){
 const component=kind==='account'?`
  function Harness(){const [email,setEmail]=React.useState('first@example.test');return <><button onClick={()=>setEmail('second@example.test')}>Switch identity</button><AccountSettings email={email}/></>;}`:kind==='profile'?`
  const first={id:'first',profile:${JSON.stringify(profile)}},second={id:'second',profile:{...first.profile,display_name:'Second builder',availability_status:'NOT_ACCEPTING',booked_until:null}};
  function Harness(){const [me,setMe]=React.useState(first),[error,setError]=React.useState('');return <><button onClick={()=>{setMe(second);setError('')}}>Switch identity</button><ProfileForm me={me} reload={()=>{window.reloadCalls++;return new Promise(resolve=>window.reloadPending.push(resolve));}} setError={setError}/>{error&&<p role="alert">{error}</p>}</>;}`:`function Harness(){return <AuthForm signup={${kind==='signup'}}/>;}`;
 const content=buildSync({stdin:{contents:`
  import React from 'react';import {createRoot} from 'react-dom/client';import AccountSettings from './app/components/account-settings';import ProfileForm from './app/components/profile-form';import {AuthForm} from './app/components/auth-form';
  window.requests=[];window.pending=[];window.reloadCalls=0;window.reloadPending=[];
  window.fetch=(path,init={})=>{window.requests.push({path,method:init.method||'GET',body:init.body});return new Promise(resolve=>window.pending.push({path,method:init.method||'GET',body:init.body,resolve}));};
  ${component}createRoot(document.getElementById('root')).render(<Harness/>);
 `,loader:'tsx',resolveDir:process.cwd()},bundle:true,write:false,platform:'browser',format:'iife',jsx:'automatic',define:{'process.env.NODE_ENV':'"test"'},plugins:[{name:'scoped-fixture',setup(plugin){
  plugin.onResolve({filter:/^next\/link$/},()=>({path:'link',namespace:'fixture'}));
  plugin.onResolve({filter:/moderation-workflow$/},()=>({path:'moderation',namespace:'fixture'}));
  plugin.onLoad({filter:/.*/,namespace:'fixture'},args=>({contents:args.path==='link'?'import React from "react";export default function Link({href,children,...props}){return <a href={href} {...props}>{children}</a>}':'export default function ModerationWorkflow(){return null}',loader:'jsx',resolveDir:process.cwd()}));
 }}]}).outputFiles[0].text;
 await page.route('**/auth-audit-fixture',route=>route.fulfill({contentType:'text/html',body:'<!doctype html><html lang="en"><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>Account audit regression</title></head><body><div id="root"></div></body></html>'}));
 await page.goto('/auth-audit-fixture');await page.addStyleTag({content:readFileSync('app/form-controls.css','utf8')});await page.addScriptTag({content});
}
async function respond(page:Page,path:string,body:unknown,status=200){
 await expect.poll(()=>page.evaluate(path=>(window as unknown as HarnessWindow).pending.some(row=>row.path===path),path)).toBe(true);
 await page.evaluate(({path,body,status})=>{const state=window as unknown as HarnessWindow,index=state.pending.findIndex(row=>row.path===path),row=state.pending.splice(index,1)[0];row.resolve(new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json'}}));},{path,body,status});
}
async function finishRefresh(page:Page,ok:boolean){await expect.poll(()=>page.evaluate(()=>(window as unknown as HarnessWindow).reloadPending.length)).toBeGreaterThan(0);await page.evaluate(ok=>(window as unknown as HarnessWindow).reloadPending.shift()!(ok),ok);}
const notice=(id:string,title:string)=>({id,title,created_at:'2026-01-01T00:00:00Z'});

test('notification continuation waits for page zero, retries safely and keeps verified rows',async({page})=>{
 await fixture(page,'account');await expect(page.getByText('Loading notifications…',{exact:true})).toBeVisible();await expect(page.getByText('No notifications yet.',{exact:true})).toHaveCount(0);await expect(page.getByRole('button',{name:'Load more notifications',exact:true})).toHaveCount(0);
 await respond(page,'/api/v1/notifications',{page:0,size:1,hasMore:true,items:[notice('first','First notification')]});
 await page.getByRole('button',{name:'Load more notifications',exact:true}).click();await respond(page,'/api/v1/notifications?page=1&size=1',{error:{message:'Continuation unavailable.',code:'UNAVAILABLE'}},503);
 await expect(page.getByRole('alert')).toHaveText('Continuation unavailable.');await expect(page.getByText('First notification',{exact:true})).toBeVisible();
 await page.getByRole('button',{name:'Load more notifications',exact:true}).click();await respond(page,'/api/v1/notifications?page=1&size=1',{page:1,size:1,hasMore:false,items:[notice('first','First notification'),notice('second','Second notification')]});
 await expect(page.locator('.account-notification')).toHaveCount(2);await expect(page.getByRole('button',{name:'Load more notifications',exact:true})).toHaveCount(0);
 const first=page.locator('.account-notification').filter({hasText:'First notification'});await first.getByRole('button',{name:'Mark read',exact:true}).click();await expect(first.getByRole('button',{name:'Marking read…',exact:true})).toBeDisabled();
 await respond(page,'/api/v1/notifications/first/read',{error:{message:'Read update failed.',code:'UNAVAILABLE'}},503);await expect(first.getByRole('button',{name:'Mark read',exact:true})).toBeEnabled();
 await first.getByRole('button',{name:'Mark read',exact:true}).click();await respond(page,'/api/v1/notifications/first/read',{ok:true});await expect(first.getByRole('button')).toHaveCount(0);await expect(page.getByText('Read update failed.',{exact:true})).toHaveCount(0);
 expect(await page.evaluate(()=>(window as unknown as HarnessWindow).requests.filter(row=>row.path.includes('page=1')).length)).toBe(2);
});

test('failed initial notification loads expose retry and old identities cannot populate a new account',async({page})=>{
 await fixture(page,'account');await respond(page,'/api/v1/notifications',{error:{code:'UNAVAILABLE',message:'Notifications unavailable.'}},503);await expect(page.getByRole('alert')).toHaveText('Notifications unavailable.');await expect(page.getByText('No notifications yet.',{exact:true})).toHaveCount(0);
 await page.getByRole('button',{name:'Retry notifications',exact:true}).click();await expect(page.getByText('Loading notifications…',{exact:true})).toBeVisible();await page.getByRole('button',{name:'Switch identity',exact:true}).click();
 // The fixture deliberately ignores AbortSignal, so the component must reject this late success itself.
 await respond(page,'/api/v1/notifications',{page:0,hasMore:true,items:[notice('old','Previous identity notification')]});await expect(page.getByText('Previous identity notification',{exact:true})).toHaveCount(0);
 await respond(page,'/api/v1/notifications',{page:0,hasMore:false,items:[]});await expect(page.getByText('No notifications yet.',{exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'Load more notifications',exact:true})).toHaveCount(0);
});

test('account closure clears stale success before a failed retry and retains confirmation input',async({page})=>{
 await fixture(page,'account');await respond(page,'/api/v1/notifications',{page:0,hasMore:false,items:[]});await page.getByText('Close my account',{exact:true}).click();const email=page.getByLabel('Type your email to request a confirmation link',{exact:true});await email.fill('first@example.test');const close=page.getByRole('button',{name:'Email me a closure confirmation',exact:true});await close.click();await expect(close).toBeDisabled();await expect(email).toBeDisabled();await respond(page,'/api/v1/me',{status:'EMAIL_CONFIRMATION_REQUIRED'},202);await expect(page.getByRole('status')).toContainText('Check your email to confirm account closure.');
 await close.click();await expect(page.getByText(/Check your email to confirm account closure/)).toHaveCount(0);await respond(page,'/api/v1/me',{error:{message:'Closure email unavailable.',code:'UNAVAILABLE'}},503);await expect(page.getByRole('alert')).toHaveText('Closure email unavailable.');await expect(email).toHaveValue('first@example.test');await expect(close).toBeEnabled();
});

test('account export downloads verified JSON and a failed retry clears the old ready notice',async({page})=>{
 await fixture(page,'account');await respond(page,'/api/v1/notifications',{page:0,hasMore:false,items:[]});const exportButton=page.getByRole('button',{name:'Download my account data',exact:true});await exportButton.click();await expect(exportButton).toBeDisabled();const downloaded=page.waitForEvent('download'),data={account:{id:'first',email:'first@example.test'},profile:{display_name:'First builder'}};await respond(page,'/api/v1/me/export',data);const download=await downloaded;expect(download.suggestedFilename()).toBe('getlancer-account.json');expect(JSON.parse(readFileSync((await download.path())!,'utf8'))).toEqual(data);await expect(page.getByRole('status')).toHaveText('Your account export is ready.');
 await exportButton.click();await expect(page.getByText('Your account export is ready.',{exact:true})).toHaveCount(0);await respond(page,'/api/v1/me/export',{error:{message:'Export unavailable.',code:'UNAVAILABLE'}},503);await expect(page.getByRole('alert')).toHaveText('Export unavailable.');await expect(exportButton).toBeEnabled();
});

test('profile save keeps fields on failure and retries refresh without repeating the successful mutation',async({page})=>{
 await fixture(page,'profile');const name=page.getByLabel('Display name',{exact:true}),country=page.getByLabel('Country code (optional)',{exact:true});await name.fill('Edited builder');await country.fill('ZZ');await expect(page.getByRole('button',{name:'Submit profile for approval',exact:true})).toBeDisabled();await page.getByRole('button',{name:'Save profile',exact:true}).click();await expect(page.getByRole('combobox',{name:'Availability',exact:true})).toBeDisabled();
 await respond(page,'/api/v1/developer/profile',{error:{message:'Correct your country.',code:'VALIDATION_ERROR',fieldErrors:{country:'Choose a valid country.'}}},400);await expect(name).toHaveValue('Edited builder');await expect(country).toHaveValue('ZZ');await expect(country).toHaveAttribute('aria-invalid','true');await expect(page.getByRole('button',{name:'Save profile',exact:true})).toBeEnabled();
 await country.fill('IN');await page.getByRole('button',{name:'Save profile',exact:true}).click();await respond(page,'/api/v1/developer/profile',{ok:true});await finishRefresh(page,false);await expect(page.getByRole('status')).toContainText('Profile saved. Current profile details could not be refreshed.');await expect(page.getByRole('button',{name:'Save profile',exact:true})).toBeDisabled();await expect(page.getByRole('button',{name:'Submit profile for approval',exact:true})).toBeDisabled();
 await page.getByRole('button',{name:'Retry profile refresh',exact:true}).click();await finishRefresh(page,true);await expect(page.getByRole('status')).toHaveText('Profile details refreshed.');await expect(page.getByRole('button',{name:'Submit profile for approval',exact:true})).toBeEnabled();
 expect(await page.evaluate(()=>(window as unknown as HarnessWindow).requests.filter(row=>row.path==='/api/v1/developer/profile').length)).toBe(2);
 await page.getByRole('button',{name:'Submit profile for approval',exact:true}).click();await respond(page,'/api/v1/developer/profile/submit',{ok:true});await finishRefresh(page,false);await expect(page.getByRole('status')).toContainText('Profile sent for approval. Current profile details could not be refreshed.');
});

test('profile identity changes reset uncontrolled fields and ignore late mutation failures',async({page})=>{
 await fixture(page,'profile');await page.getByLabel('Display name',{exact:true}).fill('Unsaved first identity');await page.getByRole('button',{name:'Save profile',exact:true}).click();await page.getByRole('button',{name:'Switch identity',exact:true}).click();await expect(page.getByLabel('Display name',{exact:true})).toHaveValue('Second builder');await expect(page.getByRole('combobox',{name:'Availability',exact:true})).toHaveText('NOT ACCEPTING');await expect(page.getByLabel('Booked until',{exact:true})).toHaveCount(0);
 await respond(page,'/api/v1/developer/profile',{error:{message:'Old identity failure.',code:'UNAVAILABLE'}},503);await expect(page.getByText('Old identity failure.',{exact:true})).toHaveCount(0);await expect(page.getByRole('button',{name:'Save profile',exact:true})).toBeEnabled();expect(await page.evaluate(()=>(window as unknown as HarnessWindow).reloadCalls)).toBe(0);
});

test('signup service recovery, UTF-8 password validation and failed submission retain user input',async({page})=>{
 await fixture(page,'signup');await respond(page,'/api/v1/auth/providers',{error:{message:'Service unavailable.',code:'UNAVAILABLE'}},503);await expect(page.getByRole('button',{name:'Create account',exact:true})).toBeDisabled();await page.getByRole('button',{name:'Check again',exact:true}).click();await respond(page,'/api/v1/auth/providers',{google:false,github:false});
 await page.getByLabel('Full name',{exact:true}).fill('Synthetic Builder');await page.getByLabel('Email address',{exact:true}).fill('builder@example.test');const password=page.getByLabel('Password',{exact:true});await password.fill('😀'.repeat(20));await page.getByRole('button',{name:'Create account',exact:true}).click();await expect(page.getByText('Use at least 12 characters and at most 72 bytes.',{exact:true})).toBeVisible();expect(await page.evaluate(()=>(window as unknown as HarnessWindow).requests.some(row=>row.path==='/api/v1/auth/signup'))).toBe(false);
 await password.fill('synthetic-password-123');await page.getByRole('button',{name:'Create account',exact:true}).click();await respond(page,'/api/v1/auth/signup',{error:{message:'Account creation unavailable.',code:'UNAVAILABLE'}},503);await expect(page.getByRole('alert')).toBeFocused();await expect(password).toHaveValue('synthetic-password-123');await page.getByRole('button',{name:'Create account',exact:true}).click();await respond(page,'/api/v1/auth/signup',{message:'Confirm your email to continue.'},201);await expect(page.getByRole('status')).toContainText('Your account is created.');
});

test('MFA retries stay in the challenge and expiry returns to a cleared password form',async({page})=>{
 await fixture(page,'login');await respond(page,'/api/v1/auth/providers',{google:false,github:false});await page.getByLabel('Email address',{exact:true}).fill('operator@example.test');await page.getByLabel('Password',{exact:true}).fill('synthetic-password-123');await page.getByRole('button',{name:'Log in',exact:true}).click();await respond(page,'/api/v1/auth/login',{mfaRequired:true});await expect(page.getByRole('heading',{name:'One more step',exact:true})).toBeVisible();
 const code=page.getByLabel('Authenticator code',{exact:true});await code.fill('123456');await page.getByRole('button',{name:'Verify and log in',exact:true}).click();await respond(page,'/api/v1/auth/login/mfa',{error:{message:'Use the current authenticator code.',code:'MFA_INVALID'}},401);await expect(page.getByRole('alert')).toHaveText('Use the current authenticator code.');await expect(code).toBeVisible();
 await code.fill('654321');await page.getByRole('button',{name:'Verify and log in',exact:true}).click();await respond(page,'/api/v1/auth/login/mfa',{error:{message:'Sign in again.',code:'MFA_EXPIRED'}},401);await expect(page.getByRole('heading',{name:'Welcome back',exact:true})).toBeVisible();await expect(page.getByLabel('Password',{exact:true})).toHaveValue('');await expect(page.getByLabel('Email address',{exact:true})).toHaveValue('operator@example.test');await expect(page.getByRole('alert')).toHaveText('Your verification step expired. Please sign in again.');
});

for(const mfa of [false,true])test(`successful ${mfa?'MFA':'password'} sign-in opens the authenticated workspace`,async({page})=>{
 await fixture(page,'login');await page.route('**/workspace',route=>route.fulfill({contentType:'text/html',body:'<!doctype html><h1>Workspace reached</h1>'}));await respond(page,'/api/v1/auth/providers',{google:false,github:false});await page.getByLabel('Email address',{exact:true}).fill('builder@example.test');await page.getByLabel('Password',{exact:true}).fill('synthetic-password-123');await page.getByRole('button',{name:'Log in',exact:true}).click();
 if(mfa){await respond(page,'/api/v1/auth/login',{mfaRequired:true});await page.getByLabel('Authenticator code',{exact:true}).fill('123456');await page.getByRole('button',{name:'Verify and log in',exact:true}).click();}
 await Promise.all([page.waitForURL('**/workspace'),respond(page,mfa?'/api/v1/auth/login/mfa':'/api/v1/auth/login',{id:'first'})]);await expect(page.getByRole('heading',{name:'Workspace reached',exact:true})).toBeVisible();
});

test('password reset retries preserve the address and use the non-enumerating success notice',async({page})=>{
 await fixture(page,'login');await respond(page,'/api/v1/auth/providers',{google:false,github:false});await page.getByLabel('Email address',{exact:true}).fill('unknown@example.test');await page.getByRole('button',{name:'Forgot password?',exact:true}).click();await page.getByRole('button',{name:'Send reset link',exact:true}).click();await respond(page,'/api/v1/auth/password-reset/request',{error:{message:'Reset email temporarily unavailable.',code:'UNAVAILABLE'}},503);await expect(page.getByLabel('Email address',{exact:true})).toHaveValue('unknown@example.test');await page.getByRole('button',{name:'Send reset link',exact:true}).click();await respond(page,'/api/v1/auth/password-reset/request',{message:'If an account exists, a reset email will be sent.'});await expect(page.getByRole('status')).toContainText('If an account exists for this email');
});
