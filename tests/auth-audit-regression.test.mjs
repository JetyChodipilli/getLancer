import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import test from 'node:test';

const profile=readFileSync(new URL('../app/components/profile-form.tsx',import.meta.url),'utf8');
const account=readFileSync(new URL('../app/components/account-settings.tsx',import.meta.url),'utf8');
const auth=readFileSync(new URL('../app/components/auth-form.tsx',import.meta.url),'utf8');
const apiSource=stripTypeScriptTypes(readFileSync(new URL('../lib/api.ts',import.meta.url),'utf8'),{mode:'transform'});
const {ApiError}=await import('data:text/javascript;base64,'+Buffer.from(apiSource).toString('base64'));
function routine(source,start,end,context){
 const first=source.indexOf(start),last=source.indexOf(end,first);assert.ok(first>=0&&last>first,'Executable routine boundaries exist');
 const js=stripTypeScriptTypes(source.slice(first,last),{mode:'transform'});
 return Function(...Object.keys(context),js+';return '+start.match(/function (\w+)/)[1]+';')(...Object.values(context));
}
function deferred(){let resolve,reject;const promise=new Promise((yes,no)=>{resolve=yes;reject=no});return{promise,resolve,reject};}
function stateContext(extra={}){
 const values={},context={ApiError,...extra};
 for(const name of new Set((profile+account+auth).match(/\bset[A-Z]\w*/g)))context[name]=value=>{values[name]=typeof value==='function'?value(values[name]):value;};
 return{values,context};
}
function accountContext(extra={}){
 const fixture=stateContext({generation:{current:0},...extra});
 fixture.context.failed=routine(account,'function failed(','\n  useEffect(()=>{',fixture.context);
 return fixture;
}
const event={preventDefault(){},currentTarget:new Map([['displayName','Synthetic builder']])};
class FixtureFormData extends Map {constructor(form){super(form)}}

test('profile saves report an accepted write separately from a failed or throwing refresh',async()=>{
 for(const throws of [false,true]){
  let writes=0,reads=0;const {values,context}=stateContext({active:{current:true},inFlight:{current:false},refreshFailed:false,availability:'LIMITED',FormData:FixtureFormData,api:async(path,init)=>{writes++;assert.equal(path,'/developer/profile');assert.deepEqual(JSON.parse(init.body),{displayName:'Synthetic builder',availabilityStatus:'LIMITED'});},reload:async()=>{reads++;if(throws)throw new Error('Refresh unavailable');return false;}});
  context.refresh=routine(profile,'async function refresh(','\n  async function save',context);
  await routine(profile,'async function save(','\n  return <div',context)(event);
  assert.equal(writes,1);assert.equal(reads,1);assert.equal(values.setRefreshFailed,true);assert.match(values.setMessage,/Profile saved.*could not be refreshed/);assert.equal(values.setDirty,false);assert.equal(values.setBusy,false);assert.equal(values.setError,'');assert.equal(context.inFlight.current,false);
 }
});

test('profile duplicate submits share the pending mutation and late identity failures cannot set parent errors',async()=>{
 const pending=deferred();let writes=0,refreshes=0;
 const {values,context}=stateContext({active:{current:true},inFlight:{current:false},refreshFailed:false,availability:'NOT_ACCEPTING',FormData:FixtureFormData,api:()=>{writes++;return pending.promise;},refresh:async()=>{refreshes++;}});
 const save=routine(profile,'async function save(','\n  return <div',context),first=save(event);await save(event);assert.equal(writes,1);assert.equal(values.setBusy,true);
 context.active.current=false;pending.reject(new ApiError('Previous identity request failed','UNAVAILABLE',503));await first;
 assert.equal(values.setError,'');assert.equal(values.setFailure,null);assert.equal(refreshes,0);assert.equal(context.inFlight.current,false);
});

test('profile refresh-only retry never invokes the write endpoint',async()=>{
 let reads=0;const {values,context}=stateContext({active:{current:true},reload:async()=>{reads++;return true;},api:()=>assert.fail('A refresh must not write the profile')});
 await routine(profile,'async function refresh(','\n  async function save',context)('Profile details refreshed.');
 assert.equal(reads,1);assert.equal(values.setRefreshFailed,false);assert.equal(values.setMessage,'Profile details refreshed.');
});

test('notification reads prevent duplicate requests, retain failed rows, and retry without stale errors',async()=>{
 const pending=deferred();let writes=0;const {values,context}=accountContext({active:{current:true},reads:{current:new Set()},api:()=>{writes++;return writes===1?pending.promise:Promise.resolve({ok:true});}});
 values.setNotifications=[{id:'owned',title:'Owned notice'},{id:'other',title:'Another notice'}];
 const markRead=routine(account,'async function markRead(','\n  const context=',context),first=markRead('owned');await markRead('owned');assert.equal(writes,1);assert.deepEqual(values.setReading,['owned']);
 pending.reject(new Error('Temporary read failure'));await first;assert.equal(values.setNotifications[0].read_at,undefined);assert.equal(values.setError,'Temporary read failure');assert.deepEqual(values.setReading,[]);
 await markRead('owned');assert.equal(writes,2);assert.equal(values.setError,'');assert.ok(values.setNotifications[0].read_at);assert.equal(values.setNotifications[1].read_at,undefined);
});

test('account export ignores a previous identity response and clears stale outcome text on retry',async()=>{
 const pending=deferred();let requests=0;const {values,context}=accountContext({active:{current:true},mutation:{current:false},api:()=>{requests++;return pending.promise;},document:{createElement:()=>assert.fail('A stale identity must not trigger a download')},URL:{createObjectURL:()=>assert.fail('A stale export must not create a blob URL')}});
 const exportData=routine(account,'async function exportData(','\n  async function markRead',context),first=exportData();await exportData();assert.equal(requests,1);assert.equal(values.setMessage,'');assert.equal(values.setError,'');
 context.active.current=false;pending.resolve({account:{id:'previous'}});await first;assert.equal(context.mutation.current,false);assert.equal(values.setMessage,'');
});

test('signup validates UTF-8 byte limits and login does not apply the new-password minimum',()=>{
 for(const [mode,password,valid] of [['signup','😀'.repeat(20),false],['signup','synthetic-password-123',true],['login','old-pass',true]]){
  const values={};const validationMessage=routine(auth,'function validationMessage(','\n\nexport function AuthForm',{});const validate=routine(auth,'function validate(','\n async function submit',{mode,validationMessage,setErrors:fn=>{values.errors=fn(values.errors||{})}});
  assert.equal(validate('password',password),valid);assert.equal(Boolean(values.errors.password),!valid);
 }
});

test('an expired MFA challenge returns to login while an invalid code remains retryable',async()=>{
 for(const code of ['MFA_INVALID','MFA_EXPIRED']){
  const modes=[];const {values,context}=stateContext({busy:false,googleBusy:false,inFlight:{current:false},isMfa:true,isSignup:false,isReset:false,servicesReady:true,mode:'mfa',totp:'123456',switchMode:next=>modes.push(next),api:async(path,init)=>{assert.equal(path,'/auth/login/mfa');assert.deepEqual(JSON.parse(init.body),{totp:'123456'});throw new ApiError('Current code required',code,401);}});
  await routine(auth,'async function submit(','\n async function oauth',context)({preventDefault(){}});
  assert.deepEqual(modes,code==='MFA_EXPIRED'?['login']:[]);assert.equal(values.setBusy,false);assert.match(values.setError,code==='MFA_EXPIRED'?/verification step expired/:/Current code required/);
 }
});


test('auth submits share one pending login intent and do not send duplicate requests',async()=>{
 const pending=deferred(),modes=[];let writes=0;
 const validationMessage=routine(auth,'function validationMessage(','\n\nexport function AuthForm',{});
 const {values,context}=stateContext({inFlight:{current:false},servicesReady:true,isMfa:false,isSignup:false,isReset:false,mode:'login',email:'builder@example.test',name:'',password:'existing-password',remember:false,validationMessage,switchMode:mode=>modes.push(mode),api:()=>{writes++;return pending.promise;}});
 const submit=routine(auth,'async function submit(','\n async function oauth',context),first=submit(event);
 await submit(event);assert.equal(writes,1);assert.equal(values.setBusy,true);
 pending.resolve({mfaRequired:true});await first;assert.deepEqual(modes,['mfa']);assert.equal(values.setBusy,false);assert.equal(context.inFlight.current,false);
});

test('OAuth rejects external destinations and credential-bearing provider URLs',async()=>{
 for(const authorizationUrl of ['https://untrusted.example.test/signin','https://someone@accounts.google.com/signin']){
  const {values,context}=stateContext({inFlight:{current:false},googleReady:true,githubReady:true,isSignup:false,mode:'login',remember:false,URL,window:{location:{assign:()=>assert.fail('Unsafe OAuth destination navigated')}},api:async()=>({authorizationUrl})});
  await routine(auth,'async function oauth(','\n const title=',context)('google');
  assert.match(values.setError,/Sign-in could not be started/);assert.equal(context.inFlight.current,false);assert.equal(values.setGoogleBusy,false);
 }
});

test('malformed successful login responses do not navigate or claim sign-in succeeded',async()=>{
 const validationMessage=routine(auth,'function validationMessage(','\n\nexport function AuthForm',{});
 const {values,context}=stateContext({inFlight:{current:false},servicesReady:true,isMfa:false,isSignup:false,isReset:false,mode:'login',email:'builder@example.test',name:'',password:'existing-password',remember:false,validationMessage,window:{location:{assign:()=>assert.fail('Malformed response navigated')}},api:async()=>({})});
 await routine(auth,'async function submit(','\n async function oauth',context)(event);
 assert.match(values.setError,/Sign-in could not be confirmed/);assert.equal(values.setBusy,false);assert.equal(context.inFlight.current,false);
});
