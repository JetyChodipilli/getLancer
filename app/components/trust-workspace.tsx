'use client';
import ExternalLink from '@/app/components/external-link';

import {WorkspaceFrame,PreviewControls,WorkspaceMetrics} from './workspace-frame';
import {useCallback,useEffect,useRef,useState} from 'react';
import Link from 'next/link';
import {ShieldCheck,Activity,CalendarDays,Layers} from 'lucide-react';
import {api,ApiError} from '@/lib/api';
import {label} from '@/lib/catalog';
import {Tabs,TabsList,TabsTrigger,TabsContent} from '@/components/ui/tabs';
import MoreRecords,{type PageInfo} from './more-records';

type Project={id:string;title:string;repository_url:string|null;demo_health:string;demo_checked_at:string|null;verification_status:string|null;challenge?:string;expires_at?:string;reason?:string};
type Award={inquiry_id:string;reason:string;created_at:string};
type Verified={product_id:string;title:string;repository_url:string};
type Verification=Verified&{challenge:string};
type Outcome={id:string;display_name:string;title:string};
const trustKeys={projects:'id',awards:'inquiry_id',verified:'product_id',verifications:'product_id',eligibleOutcomes:'id'} as const;
type TrustCollection=keyof typeof trustKeys;
type TrustPages=Partial<Record<TrustCollection,PageInfo>>;
type Data={publishingCapacity?:import('@/lib/publishing').ProjectCapacity;profile:{availability_status:string;booked_until:string|null;availability_confirmed_at:string|null};activeSlotLimit:number;projects:Project[];awards:Award[];pages?:TrustPages};
type Queue={verified?:Verified[];verifications:Verification[];eligibleOutcomes:Outcome[];pages?:TrustPages};
const emptyQueue=():Queue=>({verified:[],verifications:[],eligibleOutcomes:[]});
export function trustCollectionPage(response:unknown,collection:TrustCollection,expectedPage:number):PageInfo{
 const data=response as ({pages?:TrustPages}&Partial<Record<TrustCollection,unknown>>)|null;
 const page=data?.pages?.[collection],items=data?.[collection];
 if(!page||!Array.isArray(items)||page.page!==expectedPage||!Number.isInteger(page.size)||page.size!<1||page.size!>100||typeof page.hasMore!=='boolean'||items.length>page.size!||items.some(row=>!row||typeof row!=='object'||typeof row[trustKeys[collection]]!=='string'||!row[trustKeys[collection]]))
  throw new Error('Trust records could not be verified. Please retry.');
 return page;
}
export function appendTrustRecords<T extends object>(current:T[],items:T[],key:string):T[]{
 const records=new Map(current.map(row=>[(row as Record<string,unknown>)[key],row]));
 for(const row of items)records.set((row as Record<string,unknown>)[key],row);
 return Array.from(records.values());
}
const sample:Data={profile:{availability_status:'AVAILABLE_NOW',booked_until:null,availability_confirmed_at:null},activeSlotLimit:3,projects:[{id:'stockroom',title:'Stockroom',repository_url:'https://github.com/example/stockroom',demo_health:'UNKNOWN',demo_checked_at:null,verification_status:null},{id:'booklane',title:'Booklane',repository_url:null,demo_health:'UNKNOWN',demo_checked_at:null,verification_status:null}],awards:[]};
const date=(value:string|null|undefined)=>value?new Date(value).toLocaleDateString():'Not yet confirmed';
export default function TrustWorkspace({demo=false}:{demo?:boolean}){
 const [data,setData]=useState<Data|null>(demo?sample:null),[queue,setQueue]=useState<Queue>(emptyQueue),[admin,setAdmin]=useState(false),[busy,setBusy]=useState(false),[loading,setLoading]=useState(false),[revision,setRevision]=useState(0),[error,setError]=useState(''),[message,setMessage]=useState('');
 const generation=useRef(0),mounted=useRef(true),mutating=useRef(false);
 const [status,setStatus]=useState('AVAILABLE_NOW'),[until,setUntil]=useState('');
 const denied=useCallback((problem:unknown)=>{if(problem instanceof ApiError&&[401,403,404].includes(problem.status)){generation.current++;setData(null);setQueue(emptyQueue());setAdmin(false);setLoading(false);setRevision(value=>value+1)}},[]);
 const load=useCallback(async()=>{
  const current=++generation.current;setLoading(true);setError('');setMessage('');
  try{
   const me=await api('/me'),isAdmin=me.roles.includes('ADMIN');
   const response=await api(isAdmin?'/admin/trust':'/developer/trust');
   if(!mounted.current||current!==generation.current)return false;
   for(const collection of (isAdmin?['verified','verifications','eligibleOutcomes']:['projects','awards']) as TrustCollection[])trustCollectionPage(response,collection,0);
   setAdmin(isAdmin);setQueue(isAdmin?response:emptyQueue());
   setData(isAdmin?{profile:{availability_status:'NOT_ACCEPTING',booked_until:null,availability_confirmed_at:null},activeSlotLimit:0,projects:[],awards:[]}:response);
   if(!isAdmin){setStatus(response.profile.availability_status);setUntil(response.profile.booked_until||'')}
   setRevision(value=>value+1);return true;
  }catch(e){if(mounted.current&&current===generation.current){denied(e);setError((e as Error).message)}return false}
  finally{if(mounted.current&&current===generation.current)setLoading(false)}
 },[denied]);
 // Start browser account loading after hydration; cancellation prevents a Strict Mode replay from starting a stale request.
 useEffect(()=>{let active=true;mounted.current=true;queueMicrotask(()=>{if(active&&!demo)void load()});return()=>{active=false;mounted.current=false;generation.current++}},[demo,load]);
 const pageRequest=useCallback(async(path:string)=>{
  const current=generation.current;
  try{const response=await api(path);if(!mounted.current||current!==generation.current)throw new Error('Trust details changed. Please load this page again.');const params=new URLSearchParams(path.split('?')[1]);trustCollectionPage(response,params.get('collection') as TrustCollection,Number(params.get('page')));return response}
  catch(e){if(mounted.current&&current===generation.current){denied(e);if(e instanceof ApiError&&[401,403,404].includes(e.status))setError(e.message)}throw e}
 },[denied]);
 function ownerMore(collection:'projects'|'awards',title:string){
  const page=data?.pages?.[collection];
  return !demo&&!busy&&!loading&&page&&<MoreRecords<Project|Award> key={collection+':'+revision} path={'/developer/trust?collection='+collection} collection={collection} firstPage={page} request={pageRequest} label={title} onItems={items=>setData(current=>current?{...current,[collection]:appendTrustRecords<Project|Award>(current[collection],items,collection==='projects'?'id':'inquiry_id')}:current)} onPage={value=>setData(current=>current?{...current,pages:{...current.pages,[collection]:{...current.pages?.[collection],...value}}}:current)}/>;
 }
 function queueMore(collection:'verified'|'verifications'|'eligibleOutcomes',title:string){
  const page=queue.pages?.[collection];
  return !demo&&!busy&&!loading&&page&&<MoreRecords<Verified|Verification|Outcome> key={collection+':'+revision} path={'/admin/trust?collection='+collection} collection={collection} firstPage={page} request={pageRequest} label={title} onItems={items=>setQueue(current=>({...current,[collection]:appendTrustRecords<Verified|Verification|Outcome>(current[collection]||[],items,collection==='eligibleOutcomes'?'id':'product_id')}))} onPage={value=>setQueue(current=>({...current,pages:{...current.pages,[collection]:{...current.pages?.[collection],...value}}}))}/>;
 }
 async function run(action:()=>Promise<void>){
  if(mutating.current)return;mutating.current=true;const current=++generation.current;setBusy(true);setError('');setMessage('');
  try{await action();if(!mounted.current||current!==generation.current)return;if(!demo&&!await load())return;setMessage(demo?'Simulation updated. No live account or external service changed.':'Your changes have been saved.')}
  catch(e){if(mounted.current&&current===generation.current){denied(e);setError((e as Error).message)}}
  finally{mutating.current=false;if(mounted.current)setBusy(false)}
 }
 async function decision(id:string,action:string,reason:string){await run(async()=>{if(demo){setData(old=>old&&({...old,projects:old.projects.map(p=>p.id===id?{...p,verification_status:action==='approve'?'VERIFIED':'REJECTED',reason}:p)}));setQueue(old=>({...old,verifications:old.verifications.filter(v=>v.product_id!==id)}));}else await api('/admin/verifications/'+id+'/'+action,{method:'POST',body:JSON.stringify({reason})});});}
 return <WorkspaceFrame section="trust" preview={demo} className="trust-workspace" title="Trust & availability" description="Keep your availability current, show evidence and help clients choose confidently.">
 {demo&&<PreviewControls title="Trust & availability preview" note="Sample records reset when you leave this page. These controls never change a live account."><button className="button" onClick={()=>setAdmin(!admin)}>{admin?'View as builder':'Preview administrator tools'}</button></PreviewControls>}
 {error&&<p className="error" role="alert">{error} {!data&&<Link className="link" href="/login">Log in</Link>} {!demo&&<button type="button" className="link" disabled={loading||busy} onClick={()=>void load()}>Retry trust details</button>}</p>}{message&&<p className="success" role="status" aria-atomic="true">{message}</p>}
 {!data&&!error&&<p role="status">Loading trust details…</p>}
 {data&&<><WorkspaceMetrics items={[{label:'Client availability',value:label(data.profile.availability_status),detail:'Separate from showcase capacity',Icon:CalendarDays},{label:'Loaded repository evidence',value:data.projects.filter(p=>p.verification_status==='VERIFIED').length,detail:'Reviewed repositories in loaded projects',Icon:ShieldCheck},{label:'Loaded live link checks',value:data.projects.filter(p=>p.demo_checked_at).length,detail:'Recorded checks in loaded projects',Icon:Activity},{label:'Showcase capacity',value:data.publishingCapacity?.limit??(data.activeSlotLimit===0?0:6+Math.max(0,data.activeSlotLimit-3)),detail:'Regular + college capacity',Icon:Layers}]}/>
 <Tabs key={admin?'admin':'builder'} defaultValue={admin?'review':'availability'}><TabsList className="studio-tabs" variant="line">{(!admin||demo)&&<><TabsTrigger value="availability">Availability</TabsTrigger><TabsTrigger value="evidence">Project evidence</TabsTrigger><TabsTrigger value="capacity">Earned capacity</TabsTrigger></>}{admin&&<TabsTrigger value="review">Administrator review</TabsTrigger>}</TabsList>
 <TabsContent value="availability"><section className="trust-split"><div><h2>Ready for your next project?</h2><p>Availability describes client work. It does not change your showcase capacity.</p><p className="muted">Last confirmed: {date(data.profile.availability_confirmed_at)}</p><h3>Response reliability</h3><p className="muted">Your public profile shows response statistics only after the minimum qualified inquiry sample is reached. Unconfirmed and restricted inquiries are excluded.</p></div><form className="form panel" onSubmit={e=>{e.preventDefault();run(async()=>{if(demo)setData({...data,profile:{availability_status:status,booked_until:status==='BOOKED_UNTIL'?until:null,availability_confirmed_at:new Date().toISOString()}});else await api('/developer/availability',{method:'PUT',body:JSON.stringify({status,bookedUntil:until})});});}}><label htmlFor="availability-status">Current availability</label><select id="availability-status" value={status} onChange={e=>setStatus(e.target.value)}>{['AVAILABLE_NOW','ONE_SLOT_LEFT','LIMITED','BOOKED_UNTIL','NOT_ACCEPTING'].map(s=><option key={s} value={s}>{label(s)}</option>)}</select>{status==='BOOKED_UNTIL'&&<><label htmlFor="booked-until">Booked until</label><input id="booked-until" type="date" required min={new Date().toISOString().slice(0,10)} value={until} onChange={e=>setUntil(e.target.value)}/></>}<button disabled={busy} className="button primary">{busy?'Saving…':'Confirm availability'}</button></form></section></TabsContent>
 <TabsContent value="evidence"><h2>Evidence, with clear boundaries.</h2><p className="muted">Repository review checks your demonstrated control of one repository. Demo health records reachability, not ownership, security or uptime guarantees.</p>{!data.projects.length&&<p className="empty">Create a showcase to add repository evidence.</p>}{data.projects.map(p=><article className="trust-project panel" key={p.id}><div><h3>{p.title}</h3><p>Repository: {label(p.verification_status||'NOT_REQUESTED')}</p><p className="muted">Demo: {label(p.demo_health)} · {p.demo_checked_at?'Last check '+date(p.demo_checked_at):'No check recorded'}</p>{p.reason&&<p>{p.reason}</p>}{p.challenge&&p.verification_status==='PENDING'&&<div className="trust-challenge"><p>Add this exact challenge to your repository README before the administrator reviews it.</p><code>{p.challenge}</code><p className="muted">Expires: {date(p.expires_at)}</p></div>}</div><div className="stack"><button className="button" disabled={busy||!p.repository_url||p.verification_status==='VERIFIED'} onClick={()=>run(async()=>{if(demo){const challenge='getlancer-proof-demo-'+p.id;setData({...data,projects:data.projects.map(x=>x.id===p.id?{...x,challenge,verification_status:'PENDING',expires_at:new Date(Date.now()+604800000).toISOString()}:x)});setQueue(q=>({...q,verifications:[...q.verifications.filter(x=>x.product_id!==p.id),{product_id:p.id,title:p.title,repository_url:p.repository_url!,challenge}]}));}else await api('/developer/products/'+p.id+'/verification',{method:'POST'});})}>Request repository review</button>{!p.repository_url&&<small>Add a GitHub repository in your showcase editor first.</small>}{demo&&<button className="button" disabled={busy} onClick={()=>run(async()=>setData({...data,projects:data.projects.map(x=>x.id===p.id?{...x,demo_health:x.demo_health==='REACHABLE'?'UNREACHABLE':'REACHABLE',demo_checked_at:new Date().toISOString()}:x)}))}>Simulate demo health change</button>}</div></article>)}{ownerMore('projects','Load more project evidence')}</TabsContent>
 <TabsContent value="capacity"><h2>Capacity earned through real outcomes.</h2><p>You have three free regular-project slots and three free college-project slots. Extra purchased or earned project slots can serve either category. Drafts and archives do not use slots. <Link href={(demo?'/preview':'/workspace')+'/slots?pool=PROJECT'}>View capacity and buy extra slots</Link>.</p><p className="muted">Administrators may award capacity after reviewing a client-confirmed completion. Awards are recorded, cannot be duplicated for the same engagement, and do not buy verification or search position.</p>{data.awards.length?data.awards.map(a=><div className="row" key={a.inquiry_id}><strong>+1 showcase slot</strong><p>{a.reason}</p><span>{date(a.created_at)}</span></div>):<div className="empty">No earned-capacity awards yet.</div>}{ownerMore('awards','Load more earned-capacity awards')}</TabsContent>
 {admin&&<TabsContent value="review"><h2>Review evidence</h2><p className="muted">Inspect the exact repository README and challenge. Record what you checked before approving. Do not approve your own work.</p>{!queue.verifications.length&&<p>No repository requests waiting.</p>}{queue.verifications.map(v=><form className="form panel" key={v.product_id} onSubmit={e=>{e.preventDefault();const f=new FormData(e.currentTarget);decision(v.product_id,String(f.get('action')),String(f.get('reason')));}}><h3>{v.title}</h3><ExternalLink className="link" href={v.repository_url} target="_blank" rel="noopener noreferrer">Inspect repository ↗</ExternalLink><code className="trust-challenge">{v.challenge}</code><label>Review evidence<textarea name="reason" required minLength={10} maxLength={2000}/></label><label>Decision<select name="action"><option value="approve">Approve repository evidence</option><option value="reject">Reject evidence</option></select></label><button className="button primary" disabled={busy}>Save decision</button></form>)}{queueMore('verifications','Load more repository requests')}<h2>Reviewed repository evidence</h2>{(demo?data.projects.filter(p=>p.verification_status==='VERIFIED').map(p=>({product_id:p.id,title:p.title,repository_url:p.repository_url!})):queue.verified||[]).map(v=><form className="form panel" key={'verified-'+v.product_id} onSubmit={e=>{e.preventDefault();decision(v.product_id,'revoke',String(new FormData(e.currentTarget).get('reason')));}}><h3>{v.title}</h3><ExternalLink className="link" href={v.repository_url} target="_blank" rel="noopener noreferrer">Inspect repository ↗</ExternalLink><label>Reason for revoking evidence<textarea name="reason" required minLength={10} maxLength={2000}/></label><p className="muted">This removes the repository-review label. The showcase remains subject to its existing approval status.</p><button className="button danger" disabled={busy}>Revoke repository evidence</button></form>)}{queueMore('verified','Load more reviewed repository evidence')}<h2>Earned capacity review</h2>{demo&&<button className="button" onClick={()=>setQueue(q=>({...q,eligibleOutcomes:[{id:'demo-completion',display_name:'Leah Morgan',title:'Stockroom'}]}))}>Load sample client-confirmed completion</button>}{!queue.eligibleOutcomes.length&&<p>No eligible completions awaiting an award.</p>}{queue.eligibleOutcomes.map(o=><form className="form panel" key={o.id} onSubmit={e=>{e.preventDefault();const reason=String(new FormData(e.currentTarget).get('reason'));run(async()=>{if(demo){if(!data.awards.some(a=>a.inquiry_id===o.id))setData({...data,activeSlotLimit:data.activeSlotLimit+1,awards:[...data.awards,{inquiry_id:o.id,reason,created_at:new Date().toISOString()}]});setQueue(q=>({...q,eligibleOutcomes:q.eligibleOutcomes.filter(x=>x.id!==o.id)}));}else await api('/admin/earned-capacity/'+o.id,{method:'POST',body:JSON.stringify({reason})});});}}><h3>{o.display_name} · {o.title}</h3><label>Award reason<textarea name="reason" required minLength={10} maxLength={2000}/></label><button className="button" disabled={busy}>Award one showcase slot</button></form>)}{queueMore('eligibleOutcomes','Load more eligible completions')}</TabsContent>}
 </Tabs></>}
 </WorkspaceFrame>;
}
