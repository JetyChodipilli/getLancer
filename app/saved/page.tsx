'use client';
import {useCallback,useEffect,useRef,useState} from 'react';
import Link from 'next/link';
import {api,accountApi,ApiError} from '@/lib/api';
import {recordPage,type RecordPage} from '@/lib/record-page';
import MoreRecords from '../components/more-records';

type SavedProject={id:string;available:boolean;slug?:string;title?:string;summary?:string};
type SavedPage=RecordPage<SavedProject>&{generation:number};
const path='/me/saved-products';
export default function Saved(){
 const [items,setItems]=useState<SavedProject[]>([]),[page,setPage]=useState<SavedPage|null>(null),[loading,setLoading]=useState(true),[error,setError]=useState(''),[rowErrors,setRowErrors]=useState<Record<string,string>>({}),[removing,setRemoving]=useState<string[]>([]),[authorizationLost,setAuthorizationLost]=useState(false),[epoch,setEpoch]=useState(0);
 const mounted=useRef(true),generation=useRef(0),accessEpoch=useRef(0),controller=useRef<AbortController|null>(null),pending=useRef(new Set<string>()),reading=useRef(false),account=useRef<string|null>(null);
 const privateRequest=useCallback((path:string,init?:RequestInit)=>accountApi(account.current,path,init),[]);
 const denied=useCallback((problem:unknown)=>{
  if(!(problem instanceof ApiError)||![401,403].includes(problem.status))return false;
  accessEpoch.current++;generation.current++;account.current=null;controller.current?.abort();reading.current=false;pending.current.clear();setRemoving([]);setItems([]);setPage(null);setLoading(false);setRowErrors({});setAuthorizationLost(true);setError(problem.message);setEpoch(value=>value+1);return true;
 },[]);
 const load=useCallback(async()=>{
  const current=++generation.current,authority=accessEpoch.current;controller.current?.abort();const request=new AbortController();controller.current=request;reading.current=true;setLoading(true);setError('');setPage(null);setEpoch(value=>value+1);
  try{const user=await api('/me',{signal:request.signal});if(!mounted.current||current!==generation.current||authority!==accessEpoch.current)return;if(account.current&&account.current!==user.id){denied(new ApiError('Your account changed. Reload saved projects.','ACCOUNT_CHANGED',401));return;}const first=recordPage<SavedProject>(await accountApi(user.id,path+'?page=0',{signal:request.signal}));if(!mounted.current||current!==generation.current||authority!==accessEpoch.current)return;account.current=user.id;setItems(first.items);setPage({...first,generation:current});setAuthorizationLost(false);}
  catch(problem){if(mounted.current&&current===generation.current&&authority===accessEpoch.current&&!request.signal.aborted&&!denied(problem))setError((problem as Error).message);}
  finally{if(mounted.current&&current===generation.current&&authority===accessEpoch.current){reading.current=false;setLoading(false);}}
 },[denied]);
 useEffect(()=>{let active=true;mounted.current=true;queueMicrotask(()=>{if(active)void load();});return()=>{active=false;mounted.current=false;generation.current++;accessEpoch.current++;controller.current?.abort();};},[load]);
 async function remove(id:string){
  if(pending.current.has(id)||reading.current||authorizationLost)return;
  const authority=accessEpoch.current;pending.current.add(id);setRemoving([...pending.current]);setRowErrors(old=>{const next={...old};delete next[id];return next;});
  // DELETE shifts the server's OFFSETs. Discard old continuations immediately and
  // restart page0 after every concurrent removal has settled.
  generation.current++;controller.current?.abort();setPage(null);setEpoch(value=>value+1);
  try{await accountApi(account.current,'/products/'+encodeURIComponent(id)+'/save',{method:'DELETE'});if(mounted.current&&authority===accessEpoch.current)setItems(old=>old.filter(item=>item.id!==id));}
  catch(problem){if(mounted.current&&authority===accessEpoch.current&&!denied(problem))setRowErrors(old=>({...old,[id]:(problem as Error).message}));}
  finally{if(mounted.current&&authority===accessEpoch.current){pending.current.delete(id);setRemoving([...pending.current]);if(!pending.current.size)void load();}}
 }
 return <main className="wrap" id="main"><div className="intro"><div><span className="eyebrow">Your shortlist</span><h1>Saved projects</h1><p>A place for work you want to return to.</p><Link href="/saved/components" className="button">Saved components</Link></div></div>
  {loading&&<p role="status">{items.length?'Refreshing saved projects…':'Loading saved projects…'}</p>}
  {error&&<div className="error" role="alert"><p>{error}</p>{authorizationLost&&<Link href="/login" className="link">Log in</Link>}<button type="button" className="button" disabled={loading||removing.length>0} onClick={()=>void load()}>Retry loading saved projects</button></div>}
  {items.map(project=><div className="row" key={project.id}><div className="grow"><h3>{project.available?<Link href={'/products/'+project.slug}>{project.title}</Link>:'Project unavailable'}</h3><span className="muted">{project.available?project.summary:'This project is no longer publicly available.'}</span>{rowErrors[project.id]&&<p className="error" role="alert">{rowErrors[project.id]}</p>}</div><button type="button" className="button" aria-label={'Remove '+(project.title||'unavailable project')} disabled={loading||removing.includes(project.id)} onClick={()=>void remove(project.id)}>{removing.includes(project.id)?'Removing…':'Remove'}</button></div>)}
  {page&&!loading&&!items.length&&<div className="empty"><h2>Your next idea could be here.</h2><p>Save a project while exploring to add it to your shortlist.</p><Link className="button" href="/">Explore projects</Link></div>}
  {page&&!loading&&!removing.length&&<MoreRecords<SavedProject> key={epoch} path={path} firstPage={page} request={privateRequest} label="Load more saved projects" onItems={rows=>{if(page.generation===generation.current)setItems(old=>[...old,...rows.filter(row=>!old.some(item=>item.id===row.id))]);}} onError={problem=>{if(page.generation===generation.current)denied(problem);}}/>}
 </main>;
}
