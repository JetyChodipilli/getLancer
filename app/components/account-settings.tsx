'use client';
import ModerationWorkflow from './moderation-workflow';
import { useEffect, useState } from 'react';
import MoreRecords from './more-records';
import { api } from '@/lib/api';
export default function AccountSettings({email,isAdmin=false}: {email: string;isAdmin?:boolean}) {
  const [notifications,setNotifications] = useState<{id:string;title:string;read_at?:string;created_at:string}[]>([]);
  const [error,setError] = useState('');
  const [message,setMessage] = useState('');
  const [busy,setBusy] = useState(false);
  const [confirm,setConfirm] = useState('');
  useEffect(()=>{api('/notifications').then(r=>setNotifications(r.items)).catch(e=>setError(e.message));},[]);
  async function exportData(){setBusy(true);try{const result=await api('/me/export');const url=URL.createObjectURL(new Blob([JSON.stringify(result,null,2)],{type:'application/json'}));const link=document.createElement('a');link.href=url;link.download='getlancer-account.json';link.click();setTimeout(()=>URL.revokeObjectURL(url),1000);setMessage('Your account export is ready.');}catch(e){setError((e as Error).message)}finally{setBusy(false)}}
  return <section className="stack"><div className="panel"><h2>Account</h2><p>{email}</p><p>One account lets you explore, request work, and publish approved showcases.</p><button className="button" disabled={busy} onClick={exportData}>Download my account data</button></div>
    <div className="panel"><h2>Notifications</h2>{notifications.length?notifications.map(n=><div className="row" key={n.id}><p className="grow">{n.title}</p>{!n.read_at&&<button className="button" onClick={async()=>{try{await api('/notifications/'+n.id+'/read',{method:'PATCH'});setNotifications(items=>items.map(x=>x.id===n.id?{...x,read_at:new Date().toISOString()}:x));}catch(e){setError((e as Error).message)}}}>Mark read</button>}</div>):<p className="muted">No notifications yet.</p>}<MoreRecords path="/notifications" onItems={rows=>setNotifications(old=>[...old,...rows.filter(r=>!old.some(x=>x.id===r.id))])}/></div>
    {!isAdmin&&<details className="panel"><summary>Close my account</summary><p>Your public profile and showcases will be removed from discovery. Engagement and moderation records enter a retention review.</p><form className="form" onSubmit={async e=>{e.preventDefault();if(confirm!==email)return;setBusy(true);try{await api('/me',{method:'DELETE'});setMessage('Check your email to confirm account closure. Your account stays active until you confirm.');setBusy(false);}catch(e){setError((e as Error).message);setBusy(false)}}}><label htmlFor="close-email">Type your email to request a confirmation link</label><input id="close-email" value={confirm} onChange={e=>setConfirm(e.target.value)} type="email" autoComplete="off" required/><button className="button danger" disabled={busy||confirm!==email}>Email me a closure confirmation</button></form></details>}{isAdmin&&<p className="muted">The sole administrator account is protected from self-service closure.</p>}
    {error&&<p className="error" role="alert">{error}</p>}{message&&<p className="success" role="status">{message}</p>}
  <ModerationWorkflow/></section>;
}
