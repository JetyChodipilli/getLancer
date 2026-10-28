'use client';
import {useEffect,useRef,useState} from 'react';
import {api,ApiError} from '@/lib/api';
import MoreRecords,{type PageInfo} from './more-records';
type Row=Record<string,any>;
const path='/admin/deletion-requests';
export default function PrivacyOperations(){
 const [items,setItems]=useState<Row[]>([]),[error,setError]=useState(''),[note,setNote]=useState(''),[busy,setBusy]=useState(false),[page,setPage]=useState<PageInfo|null>(null),[epoch,setEpoch]=useState(0);
 const generation=useRef(0),mounted=useRef(true),pending=useRef(false),summary=useRef<HTMLParagraphElement>(null);
 useEffect(()=>{if(error)summary.current?.focus()},[error]);
 function clear(){setItems([]);setPage(null);setEpoch(value=>value+1);setNote('');}
 function failed(problem:unknown){if(problem instanceof ApiError&&[401,403].includes(problem.status)){generation.current++;clear();}setError((problem as Error).message);}
 async function load(context=''){
  const run=++generation.current;setBusy(true);setError('');setNote('');
  try{const next=await api(path);if(!mounted.current||run!==generation.current)return false;setItems(next.items);setPage(next);setEpoch(value=>value+1);return true;}
  catch(problem){if(mounted.current&&run===generation.current){clear();setError(context+(problem as Error).message);}return false;}
  finally{if(mounted.current&&run===generation.current)setBusy(false);}
 }
 useEffect(()=>{mounted.current=true;void load();return()=>{mounted.current=false;generation.current++;};},[]);
 async function save(id:string,body:Record<string,FormDataEntryValue>){
  if(pending.current)return;pending.current=true;const run=++generation.current;setBusy(true);setError('');setNote('');
  try{await api(path+'/'+id+'/review',{method:'POST',body:JSON.stringify(body)});if(!mounted.current||run!==generation.current)return;if(await load('The decision may have been saved, but current status could not be refreshed. Refresh before repeating it. '))setNote('Privacy decision recorded and current records refreshed.');}
  catch(problem){if(mounted.current&&run===generation.current)failed(problem);}
  finally{pending.current=false;if(mounted.current)setBusy(false);}
 }
 const run=generation.current;
 return <details className="panel"><summary>Account deletion review</summary><p>Review the applicable retention policy before removing personal profile data. Engagement and moderation evidence require a separate documented retention decision.</p>
  <button type="button" className="button" disabled={busy} onClick={()=>void load()}>Refresh privacy decisions</button>{busy&&<p role="status">Checking current privacy records…</p>}{error&&<p ref={summary} tabIndex={-1} className="error" role="alert">{error}</p>}{note&&<p role="status" className="success">{note}</p>}
  {page&&!items.length&&<p>No account deletion requests.</p>}
  {items.map(item=><form className="form panel" key={item.id} onSubmit={event=>{event.preventDefault();void save(item.id,Object.fromEntries(new FormData(event.currentTarget)));}}><fieldset className="form" disabled={busy} style={{border:0,padding:0,margin:0,minWidth:0}}><p>{item.id} · {item.status}</p>{item.resolution&&<p>{item.resolution}</p>}<label htmlFor={'deletion-action-'+item.id}>Retention decision</label><select id={'deletion-action-'+item.id} name="action"><option value="HOLD">Hold pending retention review</option><option value="ANONYMIZE_PROFILE">Anonymize profile and revoke account access</option></select><label htmlFor={'deletion-reason-'+item.id}>Policy reference and reason</label><textarea id={'deletion-reason-'+item.id} name="reason" required minLength={10} maxLength={2000}/><button className="button" disabled={busy}>Record privacy decision</button></fieldset></form>)}
  {page&&!busy&&<MoreRecords<Row> key={epoch} path={path} firstPage={page} label="Load more privacy requests" onItems={rows=>{if(mounted.current&&run===generation.current)setItems(old=>Array.from(new Map([...old,...rows].map(row=>[row.id,row])).values()));}} onPage={next=>{if(mounted.current&&run===generation.current)setPage(next);}} onError={problem=>{if(mounted.current&&run===generation.current)failed(problem);}}/>}
 </details>;
}
