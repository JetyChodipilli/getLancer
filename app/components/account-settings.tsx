'use client';
import ModerationWorkflow from './moderation-workflow';
import { useCallback, useEffect, useRef, useState } from 'react';
import MoreRecords, {type PageInfo} from './more-records';
import { api, ApiError } from '@/lib/api';
function uncertain(problem:unknown){return !(problem instanceof ApiError)||problem.status===0||problem.status>=500;}
type Notification={id:string;title:string;read_at?:string;created_at:string};
type AccountRequest=typeof api&{isCurrent?:()=>boolean};
type SettingsProps={email:string;isAdmin?:boolean;request?:AccountRequest;reload?:()=>Promise<boolean>};
export default function AccountSettings({email,isAdmin=false,request=api,reload}:SettingsProps) {
  return <AccountSettingsForIdentity key={email+':'+isAdmin} email={email} isAdmin={isAdmin} request={request} reload={reload}/>;
}
function AccountSettingsForIdentity({email,isAdmin,request=api,reload}:SettingsProps) {
  const [notifications,setNotifications] = useState<Notification[]>([]);
  const [notificationPage,setNotificationPage] = useState<PageInfo|null>(null);
  const [notificationError,setNotificationError] = useState('');
  const [notificationAttempt,setNotificationAttempt] = useState(0);
  const [authorityVersion,setAuthorityVersion] = useState(0);
  const [reading,setReading] = useState<string[]>([]);
  const [error,setError] = useState('');
  const [message,setMessage] = useState('');
  const [busy,setBusy] = useState(false);
  const [confirm,setConfirm] = useState('');
  const [needsReload,setNeedsReload] = useState(false);
  const exportURLs=useRef(new Map<string,number>());
  const revokeExports=useCallback(()=>{for(const [url,timer] of exportURLs.current){window.clearTimeout(timer);URL.revokeObjectURL(url);}exportURLs.current.clear();},[]);
  const active=useRef(true),mutation=useRef(false),reads=useRef(new Set<string>()),generation=useRef(0);
  useEffect(()=>{active.current=true;return()=>{active.current=false;generation.current++;revokeExports();}},[revokeExports]);
  function failed(problem:unknown){if(problem instanceof ApiError&&[401,403].includes(problem.status)){revokeExports();generation.current++;setAuthorityVersion(generation.current);reads.current.clear();setReading([]);setConfirm('');setNotifications([]);setNotificationPage(null);setNotificationError(problem.message);setMessage('');}setError((problem as Error).message);}
  useEffect(()=>{
    const controller=new AbortController(),run=generation.current;
    queueMicrotask(()=>{if(controller.signal.aborted)return;setNotificationError('');setNotificationPage(null);setError('');
    void request('/notifications',{signal:controller.signal}).then((r:PageInfo&{items:Notification[]})=>{
      if(controller.signal.aborted||run!==generation.current)return;
      if(!Array.isArray(r.items)||(r.page!==undefined&&r.page!==0))throw new Error('Notifications could not be verified. Please retry.');
      setNotifications(r.items);setNotificationPage({page:0,hasMore:r.hasMore===true,size:r.size});
    }).catch(e=>{if(!controller.signal.aborted&&run===generation.current){setNotificationError((e as Error).message);if(e instanceof ApiError&&[401,403].includes(e.status))failed(e);}});});
    return()=>controller.abort();
  },[notificationAttempt,request]);
  async function exportData(){if(mutation.current)return;const run=generation.current;mutation.current=true;setBusy(true);setError('');setMessage('');try{const result=await request('/me/export');if(!active.current||run!==generation.current||request.isCurrent?.()===false)return;const url=URL.createObjectURL(new Blob([JSON.stringify(result,null,2)],{type:'application/json'}));exportURLs.current.set(url,window.setTimeout(()=>{exportURLs.current.delete(url);URL.revokeObjectURL(url);},1000));const link=document.createElement('a');link.href=url;link.download='getlancer-account.json';if(!active.current||run!==generation.current||request.isCurrent?.()===false){revokeExports();return;}link.click();setMessage('Your account export is ready.');}catch(e){if(active.current&&run===generation.current)failed(e)}finally{mutation.current=false;if(active.current)setBusy(false)}}
  async function refreshAccount(){if(mutation.current)return;const run=generation.current;mutation.current=true;setBusy(true);setError('');try{let refreshed=false;if(reload)refreshed=await reload();else{await request('/me');refreshed=true;}if(!active.current||run!==generation.current||request.isCurrent?.()===false)return;if(refreshed){setNeedsReload(false);setMessage('Current account details refreshed.');}else setError('Current account details could not be refreshed. Reload before another closure request.');}catch(problem){if(active.current&&run===generation.current)failed(problem);}finally{mutation.current=false;if(active.current)setBusy(false);}}
  async function markRead(id:string){if(reads.current.has(id))return;const run=generation.current;reads.current.add(id);setReading([...reads.current]);setError('');setMessage('');try{await request('/notifications/'+id+'/read',{method:'PATCH'});if(active.current&&run===generation.current){setNotifications(items=>items.map(x=>x.id===id?{...x,read_at:new Date().toISOString()}:x));if(reload&&!await reload()&&active.current)setError('The read update was recorded. Reload workspace details before another change.');}}catch(e){if(active.current&&run===generation.current)failed(e)}finally{reads.current.delete(id);if(active.current)setReading([...reads.current])}}
  const context=authorityVersion;
  return <section className="stack account-settings"><div className="panel account-profile"><h2>Account</h2><p className="account-email">{email}</p><p>One account lets you explore, request work, and publish approved showcases.</p><div className="account-actions"><button className="button" disabled={busy} onClick={exportData}>Download my account data</button></div></div>
    <div className="panel account-notifications" aria-busy={!notificationPage&&!notificationError}><h2>Notifications</h2>{notificationError?<><p className="error" role="alert">{notificationError}</p><button className="button" onClick={()=>setNotificationAttempt(n=>n+1)}>Retry notifications</button></>:!notificationPage?<p role="status">Loading notifications…</p>:<>{notifications.length?notifications.map(n=><div className="row account-notification" key={n.id}><p className="grow">{n.title}</p>{!n.read_at&&<button className="button" disabled={reading.includes(n.id)} onClick={()=>markRead(n.id)}>{reading.includes(n.id)?'Marking read…':'Mark read'}</button>}</div>):<p className="muted">No notifications yet.</p>}<MoreRecords<Notification> request={request} path="/notifications" firstPage={notificationPage} label="Load more notifications" onItems={rows=>{if(context===generation.current)setNotifications(old=>[...old,...rows.filter(r=>!old.some(x=>x.id===r.id))]);}} onError={problem=>{if(context===generation.current&&problem instanceof ApiError&&[401,403].includes(problem.status))failed(problem);}}/></>}</div>
    {!isAdmin&&<details className="panel account-closure"><summary>Close my account</summary><p>Your public profile and showcases will be removed from discovery. Engagement and moderation records enter a retention review.</p><form className="form" onSubmit={async e=>{e.preventDefault();if(confirm!==email||mutation.current||needsReload)return;const run=generation.current;mutation.current=true;setBusy(true);setError('');setMessage('');try{await request('/me',{method:'DELETE'});if(active.current&&run===generation.current){setMessage('Check your email to confirm account closure. Your account stays active until you confirm.');if(reload&&!await reload()&&active.current){setNeedsReload(true);setError('The closure request was recorded, but current account details could not be refreshed. Reload before another closure request.');}}}catch(e){if(active.current&&run===generation.current){failed(e);if(uncertain(e)){setNeedsReload(true);setMessage('This closure request could not be confirmed. Reload current account details before trying again.');}}}finally{mutation.current=false;if(active.current)setBusy(false)}}}><label htmlFor="close-email">Type your email to request a confirmation link</label><input id="close-email" value={confirm} onChange={e=>setConfirm(e.target.value)} disabled={busy} type="email" autoComplete="off" required/><button className="button danger" disabled={busy||needsReload||confirm!==email}>Email me a closure confirmation</button></form></details>}{isAdmin&&<p className="muted">The sole administrator account is protected from self-service closure.</p>}
    {error&&<p className="error" role="alert">{error}</p>}{message&&<p className="success" role="status">{message}</p>}
  {needsReload&&<button type="button" className="button" disabled={busy} onClick={()=>void refreshAccount()}>Reload account details</button>}<ModerationWorkflow request={request} reload={reload}/></section>;
}
