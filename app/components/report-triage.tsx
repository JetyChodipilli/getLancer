'use client';
import {useEffect,useRef,useState} from 'react';
import {api,ApiError} from '@/lib/api';
import EducationPackage from './education-package';
import type {EducationBinding,EducationPackage as EducationPackageData} from '@/lib/education';
type FrozenEducation={package?:EducationPackageData;sourceBinding?:EducationBinding;mode?:string};
type ReportRecord={id:string;severity:string;reason:string;detail:string;status:string;target_type:string;education_snapshot?:FrozenEducation&{snapshot?:FrozenEducation};education_release_id?:string;source_hash?:string;component_version?:string;component_revision?:number;component_source_hash?:string;component_snapshot?:unknown};
export default function ReportTriage({request=api,reload}:{request?:typeof api;reload?:()=>Promise<boolean>}){
 const [filter,setFilter]=useState('?status=OPEN'),[items,setItems]=useState<ReportRecord[]>([]),[error,setError]=useState(''),[busy,setBusy]=useState(false),[page,setPage]=useState(0),[hasMore,setHasMore]=useState(false);
 const generation=useRef(0),pending=useRef(false),summary=useRef<HTMLParagraphElement>(null);
 useEffect(()=>{if(error)summary.current?.focus();},[error]);
 function failed(problem:unknown){setError((problem as Error).message);if(problem instanceof ApiError&&[401,403].includes(problem.status)){setItems([]);setHasMore(false);}}
 async function load(next=0,failureContext=''){const current=++generation.current;setBusy(true);setError('');if(!next){setItems([]);setHasMore(false);}try{const data:{items:ReportRecord[];hasMore:boolean}=await request('/admin/reports'+filter+'&page='+next);if(current!==generation.current)return false;setItems(old=>Array.from(new Map([...(next?old:[]),...data.items].map(item=>[item.id,item])).values()));setPage(next);setHasMore(data.hasMore===true);return true;}catch(problem){if(current===generation.current){if(!next){setItems([]);setHasMore(false);}failed(problem);if(failureContext)setError(failureContext+(problem as Error).message);}return false;}finally{if(current===generation.current)setBusy(false);}}
 async function update(id:string,action:string,form:HTMLFormElement){
  if(pending.current||busy||!items.some(item=>item.id===id))return;pending.current=true;let current=++generation.current;setBusy(true);setError('');
  try{await request('/admin/reports/'+id+'/'+action,{method:'POST',body:JSON.stringify(Object.fromEntries(new FormData(form)))});if(current!==generation.current)return;const refreshing=load(0,'The report decision was recorded, but its refreshed status is unavailable. Refresh report triage to verify it. ');current=generation.current;const refreshed=await refreshing;if(current!==generation.current||!refreshed)return;setBusy(true);if(reload&&!await reload().catch(()=>false)){if(current===generation.current){setItems([]);setHasMore(false);setError('The report decision was recorded, but your workspace could not be verified. Refresh your workspace before another decision.')}return;}}
  catch(problem){if(current===generation.current){if(!(problem instanceof ApiError)||problem.status===0||problem.status>=500){setItems([]);setHasMore(false);setError('The report decision could not be confirmed. Refresh report triage before repeating it. '+(problem as Error).message)}else failed(problem);}}
  finally{pending.current=false;if(current===generation.current)setBusy(false);}
 }
 useEffect(()=>{let active=true;queueMicrotask(()=>{if(active)void load()});return()=>{active=false;generation.current++;};},[filter]);
 return <details className="panel"><summary style={{minHeight:44}}>Report priority and triage</summary>
  <form className="form" onSubmit={event=>{event.preventDefault();const query=new URLSearchParams();new FormData(event.currentTarget).forEach((value,key)=>{if(value)query.set(key,String(value));});setFilter('?'+query)}}>
   <label htmlFor="triage-filter-state">Stage</label><select id="triage-filter-state" name="status" defaultValue="OPEN"><option value="">All stages</option>{['OPEN','TRIAGED','UNDER_REVIEW','RESOLVED'].map(value=><option key={value}>{value}</option>)}</select>
   <label htmlFor="triage-filter-priority">Priority</label><select id="triage-filter-priority" name="severity"><option value="">All priorities</option>{['CRITICAL','HIGH','MEDIUM','LOW'].map(value=><option key={value}>{value}</option>)}</select><button className="button" disabled={busy}>Filter reports</button>
  </form>
  <button type="button" className="button" disabled={busy} onClick={()=>void load()}>Refresh report triage</button>{busy&&<p role="status">Checking current reports…</p>}
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
