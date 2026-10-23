'use client';
import {useEffect,useRef,useState} from 'react';
import {api,ApiError} from '@/lib/api';
import EducationPackage from './education-package';
export default function ReportTriage(){
 const [filter,setFilter]=useState('?status=OPEN'),[items,setItems]=useState<any[]>([]),[error,setError]=useState(''),[busy,setBusy]=useState(false),[page,setPage]=useState(0),[hasMore,setHasMore]=useState(false);
 const generation=useRef(0),summary=useRef<HTMLParagraphElement>(null);
 useEffect(()=>{if(error)summary.current?.focus();},[error]);
 function failed(problem:unknown){setError((problem as Error).message);if(problem instanceof ApiError&&[401,403].includes(problem.status)){setItems([]);setHasMore(false);}}
 async function load(next=0){const current=++generation.current;setBusy(true);setError('');try{const data=await api('/admin/reports'+filter+'&page='+next);if(current!==generation.current)return;setItems(old=>next?[...old,...data.items]:data.items);setPage(next);setHasMore(data.hasMore===true);}catch(problem){if(current===generation.current){setItems([]);setHasMore(false);failed(problem);}}finally{if(current===generation.current)setBusy(false);}}
 async function update(id:string,action:string,form:HTMLFormElement){const current=generation.current;if(busy)return;setBusy(true);setError('');try{await api('/admin/reports/'+id+'/'+action,{method:'POST',body:JSON.stringify(Object.fromEntries(new FormData(form)))});if(current===generation.current)await load();}catch(problem){if(current===generation.current)failed(problem);}finally{if(current===generation.current)setBusy(false);}}
 useEffect(()=>{setItems([]);void load();return()=>{generation.current++;};},[filter]);
 return <details className="panel"><summary style={{minHeight:44}}>Report priority and triage</summary>
  <form className="form" onSubmit={event=>{event.preventDefault();const query=new URLSearchParams();new FormData(event.currentTarget).forEach((value,key)=>{if(value)query.set(key,String(value));});setFilter('?'+query)}}>
   <label htmlFor="triage-filter-state">Stage</label><select id="triage-filter-state" name="status" defaultValue="OPEN"><option value="">All stages</option>{['OPEN','TRIAGED','UNDER_REVIEW','RESOLVED'].map(value=><option key={value}>{value}</option>)}</select>
   <label htmlFor="triage-filter-priority">Priority</label><select id="triage-filter-priority" name="severity"><option value="">All priorities</option>{['CRITICAL','HIGH','MEDIUM','LOW'].map(value=><option key={value}>{value}</option>)}</select><button className="button" disabled={busy}>Filter reports</button>
  </form>
  {error&&<p ref={summary} tabIndex={-1} role="alert" className="error">{error}</p>}
  {items.map(item=>{const frozen=item.education_snapshot?.snapshot||item.education_snapshot;return <article className="panel form" key={item.id}>
   <h3>{item.severity} · {item.reason}</h3><p>{item.detail}</p><p>{item.status}</p>
   {item.education_release_id&&<details><summary style={{minHeight:44}}>Exact reported education artifact</summary><p style={{overflowWrap:'anywhere'}}>Frozen release {item.education_release_id}</p><p className="kit-source-hash">Reported content SHA-256: {item.source_hash}</p><p>Private report evidence. This is the frozen artifact at reporting time; current access eligibility may differ.</p>{frozen?.package&&<EducationPackage pack={frozen.package} binding={frozen.sourceBinding} showcase={frozen.mode==='SHOWCASE'}/>}<pre style={{whiteSpace:'pre-wrap',overflowWrap:'anywhere'}}>{JSON.stringify(frozen,null,2)}</pre></details>}
   {item.target_type==='COMPONENT'&&<details><summary style={{minHeight:44}}>Exact reported component</summary><p>Version {item.component_version} · revision {item.component_revision}</p><p className="kit-source-hash">Reported source SHA-256: {item.component_source_hash}</p><p>Frozen public metadata at report time. Review this evidence before enforcing the current source.</p><pre style={{whiteSpace:'pre-wrap',overflowWrap:'anywhere'}}>{JSON.stringify(item.component_snapshot,null,2)}</pre></details>}
   {item.status!=='RESOLVED'&&<>
    <form className="form" onSubmit={event=>{event.preventDefault();void update(item.id,'triage',event.currentTarget);}}>
     <label htmlFor={'triage-priority-'+item.id}>Priority</label><select id={'triage-priority-'+item.id} name="severity" defaultValue={item.severity}>{['CRITICAL','HIGH','MEDIUM','LOW'].map(value=><option key={value}>{value}</option>)}</select>
     <label htmlFor={'triage-state-'+item.id}>Review stage</label><select id={'triage-state-'+item.id} name="status" defaultValue={item.status}>{['OPEN','TRIAGED','UNDER_REVIEW'].map(value=><option key={value}>{value}</option>)}</select>
     <label htmlFor={'triage-note-'+item.id}>Investigation note (private)</label><textarea id={'triage-note-'+item.id} name="reason" required minLength={3} maxLength={2000}/><button className="button" disabled={busy}>Record triage</button>
    </form>
    {item.target_type==='COMPONENT'&&<form className="form" onSubmit={event=>{event.preventDefault();void update(item.id,'resolve',event.currentTarget);}}>
     <label htmlFor={'report-action-'+item.id}>Component action</label><select id={'report-action-'+item.id} name="targetAction" defaultValue="NONE"><option value="NONE">Resolve without suspension</option><option value="SUSPEND">Suspend this reported component</option></select>
     <p>Suspension blocks new runs and closes current lab requests and events for this source. An independently published component keeps its own eligibility.</p>
     <label htmlFor={'report-resolution-'+item.id}>Resolution reason (shared with affected owner)</label><textarea id={'report-resolution-'+item.id} name="reason" required minLength={3} maxLength={2000}/><button className="button" disabled={busy}>Resolve component report</button>
    </form>}
   </>}
  </article>;})}
  {hasMore&&<button className="button" disabled={busy} onClick={()=>void load(page+1)}>Load more reports</button>}
 </details>;
}
