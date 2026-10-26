'use client';
import ModerationWorkflow from './moderation-workflow';
import { useEffect, useRef, useState } from 'react';
import MoreRecords, {type PageInfo} from './more-records';
import { api } from '@/lib/api';
export default function AccountSettings({email,isAdmin=false}: {email: string;isAdmin?:boolean}) {
  return <AccountSettingsForIdentity key={email+':'+isAdmin} email={email} isAdmin={isAdmin}/>;
}
function AccountSettingsForIdentity({email,isAdmin}: {email:string;isAdmin:boolean}) {
  const [notifications,setNotifications] = useState<{id:string;title:string;read_at?:string;created_at:string}[]>([]);
  const [notificationPage,setNotificationPage] = useState<PageInfo|null>(null);
  const [notificationError,setNotificationError] = useState('');
  const [notificationAttempt,setNotificationAttempt] = useState(0);
  const [reading,setReading] = useState<string[]>([]);
  const [error,setError] = useState('');
  const [message,setMessage] = useState('');
  const [busy,setBusy] = useState(false);
  const [confirm,setConfirm] = useState('');
  const active=useRef(true),mutation=useRef(false),reads=useRef(new Set<string>());
  useEffect(()=>{active.current=true;return()=>{active.current=false}},[]);
  useEffect(()=>{
    const controller=new AbortController();setNotificationError('');setNotificationPage(null);
    api('/notifications',{signal:controller.signal}).then(r=>{
      if(controller.signal.aborted)return;
      if(!Array.isArray(r.items)||(r.page!==undefined&&r.page!==0))throw new Error('Notifications could not be verified. Please retry.');
      setNotifications(r.items);setNotificationPage({page:0,hasMore:r.hasMore===true,size:r.size});
    }).catch(e=>{if(!controller.signal.aborted)setNotificationError((e as Error).message)});
    return()=>controller.abort();
  },[notificationAttempt]);
  async function exportData(){if(mutation.current)return;mutation.current=true;setBusy(true);setError('');setMessage('');try{const result=await api('/me/export');if(!active.current)return;const url=URL.createObjectURL(new Blob([JSON.stringify(result,null,2)],{type:'application/json'}));const link=document.createElement('a');link.href=url;link.download='getlancer-account.json';link.click();setTimeout(()=>URL.revokeObjectURL(url),1000);setMessage('Your account export is ready.');}catch(e){if(active.current)setError((e as Error).message)}finally{mutation.current=false;if(active.current)setBusy(false)}}
  async function markRead(id:string){if(reads.current.has(id))return;reads.current.add(id);setReading([...reads.current]);setError('');setMessage('');try{await api('/notifications/'+id+'/read',{method:'PATCH'});if(active.current)setNotifications(items=>items.map(x=>x.id===id?{...x,read_at:new Date().toISOString()}:x));}catch(e){if(active.current)setError((e as Error).message)}finally{reads.current.delete(id);if(active.current)setReading([...reads.current])}}
  return <section className="stack account-settings"><div className="panel account-profile"><h2>Account</h2><p className="account-email">{email}</p><p>One account lets you explore, request work, and publish approved showcases.</p><div className="account-actions"><button className="button" disabled={busy} onClick={exportData}>Download my account data</button></div></div>
    <div className="panel account-notifications" aria-busy={!notificationPage&&!notificationError}><h2>Notifications</h2>{notificationError?<><p className="error" role="alert">{notificationError}</p><button className="button" onClick={()=>setNotificationAttempt(n=>n+1)}>Retry notifications</button></>:!notificationPage?<p role="status">Loading notifications…</p>:<>{notifications.length?notifications.map(n=><div className="row account-notification" key={n.id}><p className="grow">{n.title}</p>{!n.read_at&&<button className="button" disabled={reading.includes(n.id)} onClick={()=>markRead(n.id)}>{reading.includes(n.id)?'Marking read…':'Mark read'}</button>}</div>):<p className="muted">No notifications yet.</p>}<MoreRecords path="/notifications" firstPage={notificationPage} label="Load more notifications" onItems={rows=>setNotifications(old=>[...old,...rows.filter(r=>!old.some(x=>x.id===r.id))])}/></>}</div>
    {!isAdmin&&<details className="panel account-closure"><summary>Close my account</summary><p>Your public profile and showcases will be removed from discovery. Engagement and moderation records enter a retention review.</p><form className="form" onSubmit={async e=>{e.preventDefault();if(confirm!==email||mutation.current)return;mutation.current=true;setBusy(true);setError('');setMessage('');try{await api('/me',{method:'DELETE'});if(active.current)setMessage('Check your email to confirm account closure. Your account stays active until you confirm.');}catch(e){if(active.current)setError((e as Error).message)}finally{mutation.current=false;if(active.current)setBusy(false)}}}><label htmlFor="close-email">Type your email to request a confirmation link</label><input id="close-email" value={confirm} onChange={e=>setConfirm(e.target.value)} disabled={busy} type="email" autoComplete="off" required/><button className="button danger" disabled={busy||confirm!==email}>Email me a closure confirmation</button></form></details>}{isAdmin&&<p className="muted">The sole administrator account is protected from self-service closure.</p>}
    {error&&<p className="error" role="alert">{error}</p>}{message&&<p className="success" role="status">{message}</p>}
  <ModerationWorkflow/></section>;
}
