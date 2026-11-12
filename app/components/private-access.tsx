'use client';
import {useCallback,useEffect,useRef,useState} from 'react';
import {api,ApiError} from '@/lib/api';
import MoreRecords,{type PageInfo} from './more-records';

function uncertain(problem:unknown){return !(problem instanceof ApiError)||problem.status===0||problem.status>=500;}
type Grant={client_email:string;expires_at:string};
type GrantPage=PageInfo&{items:Grant[];size:number};
function unique(items:Grant[]){return [...new Map(items.map(item=>[item.client_email.toLowerCase(),item])).values()]}
function unavailable(error:unknown){return error instanceof ApiError&&[401,403,404].includes(error.status)}
async function readGrants(path:string,request:typeof api):Promise<GrantPage>{
 const data:GrantPage=await request(path);
 if(!Array.isArray(data?.items)||!data.items.every((item:Grant)=>item&&typeof item.client_email==='string'&&item.client_email.length>0&&typeof item.expires_at==='string'&&Number.isFinite(Date.parse(item.expires_at)))||!Number.isInteger(data.page)||data.page<0||!Number.isInteger(data.size)||data.size<1||data.size>100||typeof data.hasMore!=='boolean')throw new Error('Private client access could not be verified. Please retry.');
 return data;
}

export default function PrivateAccess({product,request=api,reload}:{product:{id:string};request?:typeof api;reload?:()=>Promise<boolean>}){return <AccessGrants key={product.id} productId={product.id} request={request} reload={reload}/>}
function AccessGrants({productId,request,reload}:{productId:string;request:typeof api;reload?:()=>Promise<boolean>}){
 const base='/developer/products/'+productId+'/access';
 const [grants,setGrants]=useState<Grant[]>([]),[page,setPage]=useState<PageInfo>(),[version,setVersion]=useState(0),[error,setError]=useState(''),[busy,setBusy]=useState(false),[loading,setLoading]=useState(true),[path,setPath]=useState(''),[notice,setNotice]=useState('');
 const [clientEmail,setClientEmail]=useState(''),[needsReload,setNeedsReload]=useState(false);
 const grantRequest=useCallback((path:string)=>readGrants(path,request),[request]);
 const generation=useRef(0),alive=useRef(true),mutation=useRef(false),status=useRef<HTMLParagraphElement>(null),focusStatus=useRef(false);
 const fail=useCallback((error:unknown)=>{if(unavailable(error)){generation.current++;setLoading(false);setClientEmail('');setNotice('');setGrants([]);setPage(undefined);setPath('')}setError(error instanceof Error?error.message:'Private client access could not be loaded. Please retry.')},[]);
 const load=useCallback(async()=>{
  const run=++generation.current;setLoading(true);setPage(undefined);setVersion(value=>value+1);setError('');
  try{const data=await grantRequest(base);if(!alive.current||run!==generation.current)return false;if(data.page!==0)throw new Error('Private client access could not be verified. Please retry.');setGrants(unique(data.items));setPage(data);return true;}
  catch(error){if(alive.current&&run===generation.current)fail(error);return false;}
  finally{if(alive.current&&run===generation.current)setLoading(false)}
 },[base,fail,grantRequest]);
 useEffect(()=>{let live=true;alive.current=true;queueMicrotask(()=>{if(live)void load();});return()=>{live=false;alive.current=false;generation.current++}},[load]);
 useEffect(()=>{if(notice&&focusStatus.current){status.current?.focus({preventScroll:true});focusStatus.current=false}},[notice]);
 async function reconcile(){const refreshed=await load();if(!refreshed||!alive.current)return false;setNeedsReload(true);if(reload&&!await reload()){if(alive.current)setError('Current workspace details could not be refreshed. Reload access before another change.');return false;}if(!alive.current)return false;setNeedsReload(false);return true;}
 async function mutate(method:'POST'|'DELETE',body:object,button?:HTMLButtonElement){
  if(mutation.current||needsReload||loading)return;mutation.current=true;generation.current++;setLoading(false);setBusy(true);setError('');setNotice('');
  const startedWithFocus=button&&document.activeElement===button;
  try{
   const result:{path:string}=await request(base,{method,body:JSON.stringify(body)});if(!alive.current)return;
   if(method==='POST'){setPath(result.path);setClientEmail('');}
   else setGrants(current=>current.filter(grant=>grant.client_email!==(body as {email:string}).email));
   setNeedsReload(true);const refreshed=await reconcile();if(!alive.current)return;if(!refreshed){setNotice('The change was recorded, but current access could not be refreshed. Reload access before repeating it.');return;}
   if(method==='DELETE'){focusStatus.current=!!startedWithFocus&&(document.activeElement===button||document.activeElement===document.body);setNotice('Client access revoked.')}
  }catch(error){if(alive.current){fail(error);if(uncertain(error)){setNeedsReload(true);setNotice('This access change could not be confirmed. Reload access before trying again.');}}}
  finally{mutation.current=false;if(alive.current)setBusy(false)}
 }
 return <section className="panel form"><h3>Private client access</h3><p>Access lasts seven days and requires the client to verify this email. Only approved, active private projects can be previewed.</p>
  <form className="form" aria-busy={busy} onSubmit={event=>{event.preventDefault();void mutate('POST',Object.fromEntries(new FormData(event.currentTarget)))}}><label htmlFor={'private-email-'+productId}>Client email</label><input id={'private-email-'+productId} name="email" type="email" value={clientEmail} onChange={event=>setClientEmail(event.target.value)} disabled={busy||needsReload||loading} required/><button className="button" disabled={busy||needsReload||loading}>Grant access for seven days</button></form>
  {path&&<p role="status">Share this preview link with your client: <a href={path}>{typeof location==='undefined'?path:location.origin+path}</a></p>}
  <div aria-busy={loading||busy}>{loading?<p role="status">Loading private client access…</p>:busy?<p role="status">Updating private client access…</p>:page&&!grants.length?<p>No client access grants yet.</p>:null}
   {grants.map(grant=><div className="row" key={grant.client_email.toLowerCase()}><p style={{minWidth:0,overflowWrap:'anywhere'}}>{grant.client_email} · expires {new Date(grant.expires_at).toLocaleDateString()}</p><button type="button" className="button" disabled={busy||needsReload||loading} aria-label={'Revoke access for '+grant.client_email} onClick={event=>void mutate('DELETE',{email:grant.client_email},event.currentTarget)}>Revoke access</button></div>)}
   {!loading&&!busy&&!needsReload&&page&&<MoreRecords<Grant> key={version} path={base} request={grantRequest} firstPage={page} label="Load more client access" onItems={items=>setGrants(current=>unique([...current,...items]))} onPage={setPage} onError={error=>{if(unavailable(error))fail(error)}}/>}
  </div>
  {notice&&<p ref={status} role="status" tabIndex={-1}>{notice}</p>}
  {error&&<p className="error" role="alert">{error}</p>}{error&&(needsReload||!page)&&!loading&&!busy&&<button type="button" className="button" onClick={()=>void reconcile()}>Retry loading access</button>}
 </section>;
}
