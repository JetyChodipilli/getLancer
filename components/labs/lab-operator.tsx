'use client';

import {useEffect,useRef,useState} from 'react';
import Link from 'next/link';
import {Pause,Play,RefreshCw,ShieldCheck} from 'lucide-react';
import {Card,CardContent,CardHeader} from '@/components/ui/card';
import {api,ApiError} from '@/lib/api';
import {labMoney,type LabOperator} from '@/lib/labs';
import {WorkspaceFrame} from '@/app/components/workspace-frame';
import './labs.css';

const labApi=(path:string,init:RequestInit={})=>api(path,{...init,signal:init.signal??AbortSignal.timeout(15000)});
export default function LabOperator(){
 const [record,setRecord]=useState<LabOperator|null>(null),[loading,setLoading]=useState(true),[busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState(''),[reason,setReason]=useState(''),[reasonError,setReasonError]=useState('');
 const alive=useRef(true),generation=useRef(0),summary=useRef<HTMLParagraphElement>(null);
 useEffect(()=>{alive.current=true;void load();return()=>{alive.current=false;generation.current++;};},[]);
 useEffect(()=>{if(error)summary.current?.focus();},[error]);
 async function load(){const token=++generation.current;setLoading(true);setError('');try{const next=await labApi('/admin/labs') as LabOperator;if(alive.current&&token===generation.current)setRecord(next);}catch(problem){if(alive.current&&token===generation.current){setRecord(null);setError((problem as Error).message);}}finally{if(alive.current&&token===generation.current)setLoading(false);}}
 async function change(event:React.FormEvent){event.preventDefault();if(!record)return;const value=reason.trim();setReasonError('');setError('');setNotice('');if(value.length<10||value.length>500){setReasonError('Explain the admission decision in 10–500 characters.');setError('Check the decision reason.');return;}
  const token=generation.current;setBusy(true);const paused=!record.paused;
  try{await labApi('/admin/labs/pause-admissions',{method:'POST',body:JSON.stringify({paused,reason:value})});if(!alive.current||token!==generation.current)return;const refreshed=await labApi('/admin/labs') as LabOperator;if(!alive.current||token!==generation.current)return;setRecord(refreshed);setNotice(paused?'Admission pause recorded. Existing runs retain their cleanup obligations.':'Pause removed. Admission still requires current isolation and certification evidence.');setReason('');}
  catch(problem){if(alive.current&&token===generation.current){if(problem instanceof ApiError&&[401,403].includes(problem.status))setRecord(null);setError((problem as Error).message+' Refresh admission status to verify this decision before retrying. The reason has been kept.');}}
  finally{if(alive.current&&token===generation.current)setBusy(false);}
 }
 const metrics=record?[{value:record.queued,label:'Queued'},{value:record.starting,label:'Starting'},{value:record.running,label:'Running'},{value:record.cancelling,label:'Cancelling'},{value:record.attention,label:'Cleanup attention'},{value:record.activeReservations,label:'Active reservations'},{value:record.memoryReservedMiB+' MiB',label:'Reserved memory'}]:[];
 return <WorkspaceFrame section="labs" title="Lab operations" description="Inspect admission, bounded reservations and unresolved cleanup through the real control plane." actions={<Link className="button" href="/labs">Open lab workbench</Link>}><div className="lab-operator">
  {error&&<p ref={summary} tabIndex={-1} role="alert" className="lab-notice lab-error">{error}</p>}{notice&&<p className="lab-notice" role="status">{notice}</p>}{loading&&<p className="lab-notice" role="status">Checking current operator authority…</p>}
  {!loading&&!record?<Card><CardContent className="lab-empty"><ShieldCheck size={26} aria-hidden="true"/><h2>Operator access required</h2><p>Use the current administrator account with multi-factor verification. Admission controls and resource counts are unavailable until that authority is checked.</p><div className="lab-actions"><Link className="button primary" href="/login">Sign in</Link><button className="button" onClick={()=>void load()}>Retry operator connection</button></div></CardContent></Card>:record&&<>
   <Card><CardHeader><h2>Current admission</h2></CardHeader><CardContent><div className="lab-control-state"><strong>{record.runtime.enabled?'Runtime admission available':'Runtime admission unavailable'}</strong><p>{record.runtime.reason||'Current operator admission checks permit bounded lab requests.'}</p><p>{record.paused?'Admissions are paused. '+record.pauseReason:'No operator pause is active.'}</p></div><div className="lab-actions"><button className="button" disabled={busy||loading} onClick={()=>void load()}><RefreshCw size={16} aria-hidden="true"/>Refresh operator status</button></div></CardContent></Card>
   <Card><CardHeader><h2>Queue & resource reservations</h2><p className="lab-muted">Unacknowledged cleanup retains its reservations. These counts come from the current control plane.</p></CardHeader><CardContent><div className="lab-operator-grid">{metrics.map(item=><div className="lab-operator-metric" key={item.label}><strong>{item.value}</strong><span>{item.label}</span></div>)}</div><dl className="lab-costs"><div><dt>Daily cost reservation</dt><dd>{labMoney(record.dailyReservedMicros)} configured budget units</dd></div><div><dt>Monthly cost reservation</dt><dd>{labMoney(record.monthlyReservedMicros)} configured budget units</dd></div></dl></CardContent></Card>
   <Card><CardHeader><h2>{record.paused?'Remove the operator pause':'Pause new admissions'}</h2><p className="lab-muted">Removing a pause cannot enable uncertified execution. Every admission decision requires current recent administrator MFA and is audited.</p></CardHeader><CardContent><form className="lab-form lab-operator-form" onSubmit={change} noValidate><div className="lab-field"><label htmlFor="lab-admission-reason">Decision reason</label><textarea id="lab-admission-reason" value={reason} maxLength={500} disabled={busy||loading} aria-invalid={reasonError?'true':undefined} aria-describedby={'lab-admission-hint'+(reasonError?' lab-admission-error':'')} onChange={event=>setReason(event.target.value)}/><span id="lab-admission-hint" className="lab-hint">Use 10–500 characters. Keep credentials and private customer data out of the audit reason.</span>{reasonError&&<span id="lab-admission-error" className="lab-field-error">{reasonError}</span>}</div><div className="lab-actions"><button className="button primary" disabled={busy||loading} type="submit">{record.paused?<Play size={16} aria-hidden="true"/>:<Pause size={16} aria-hidden="true"/>}{busy?'Checking decision…':record.paused?'Remove admission pause':'Pause new admissions'}</button></div></form></CardContent></Card>
  </>}
 </div></WorkspaceFrame>;
}
