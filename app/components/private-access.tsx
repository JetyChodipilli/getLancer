'use client';
import {useCallback,useEffect,useRef,useState} from 'react';
import {api,ApiError} from '@/lib/api';
import MoreRecords,{type PageInfo} from './more-records';

type Grant={client_email:string;expires_at:string};
type GrantPage=PageInfo&{items:Grant[]};
function unique(items:Grant[]){return [...new Map(items.map(item=>[item.client_email.toLowerCase(),item])).values()]}
function unavailable(error:unknown){return error instanceof ApiError&&[401,403,404].includes(error.status)}
async function request(path:string):Promise<GrantPage>{
 const data=await api(path);
 if(!Array.isArray(data?.items)||!data.items.every((item:Grant)=>item&&typeof item.client_email==='string'&&item.client_email.length>0&&typeof item.expires_at==='string'&&Number.isFinite(Date.parse(item.expires_at)))||!Number.isInteger(data.page)||data.page<0||!Number.isInteger(data.size)||data.size<1||data.size>100||typeof data.hasMore!=='boolean')throw new Error('Private client access could not be verified. Please retry.');
 return data;
}

export default function PrivateAccess({product}:{product:{id:string}}){return <AccessGrants key={product.id} productId={product.id}/>}
function AccessGrants({productId}:{productId:string}){
 const base='/developer/products/'+productId+'/access';
 const [grants,setGrants]=useState<Grant[]>([]),[page,setPage]=useState<PageInfo>(),[version,setVersion]=useState(0),[error,setError]=useState(''),[busy,setBusy]=useState(false),[loading,setLoading]=useState(true),[path,setPath]=useState(''),[notice,setNotice]=useState('');
 const generation=useRef(0),alive=useRef(true),mutation=useRef(false),status=useRef<HTMLParagraphElement>(null),focusStatus=useRef(false);
 const fail=useCallback((error:unknown)=>{if(unavailable(error)){setGrants([]);setPage(undefined);setPath('')}setError(error instanceof Error?error.message:'Private client access could not be loaded. Please retry.')},[]);
 const load=useCallback(async()=>{
  const run=++generation.current;setLoading(true);setPage(undefined);setVersion(value=>value+1);setError('');
  try{const data=await request(base);if(!alive.current||run!==generation.current)return;if(data.page!==0)throw new Error('Private client access could not be verified. Please retry.');setGrants(unique(data.items));setPage(data)}
  catch(error){if(alive.current&&run===generation.current)fail(error)}
  finally{if(alive.current&&run===generation.current)setLoading(false)}
 },[base,fail]);
 useEffect(()=>{alive.current=true;void load();return()=>{alive.current=false;generation.current++}},[load]);
 useEffect(()=>{if(notice&&focusStatus.current){status.current?.focus({preventScroll:true});focusStatus.current=false}},[notice]);
 async function mutate(method:'POST'|'DELETE',body:object,button?:HTMLButtonElement){
  if(mutation.current)return;mutation.current=true;generation.current++;setLoading(false);setBusy(true);setError('');setNotice('');
  const startedWithFocus=button&&document.activeElement===button;
  try{
   const result=await api(base,{method,body:JSON.stringify(body)});if(!alive.current)return;
   if(method==='POST')setPath(result.path);
   else setGrants(current=>current.filter(grant=>grant.client_email!==(body as {email:string}).email));
   await load();if(!alive.current)return;
   if(method==='DELETE'){focusStatus.current=!!startedWithFocus&&(document.activeElement===button||document.activeElement===document.body);setNotice('Client access revoked.')}
  }catch(error){if(alive.current)fail(error)}
  finally{mutation.current=false;if(alive.current)setBusy(false)}
 }
 return <section className="panel form"><h3>Private client access</h3><p>Access lasts seven days and requires the client to verify this email. Only approved, active private projects can be previewed.</p>
  <form className="form" aria-busy={busy} onSubmit={event=>{event.preventDefault();void mutate('POST',Object.fromEntries(new FormData(event.currentTarget)))}}><label htmlFor={'private-email-'+productId}>Client email</label><input id={'private-email-'+productId} name="email" type="email" required/><button className="button" disabled={busy}>Grant access for seven days</button></form>
  {path&&<p role="status">Share this preview link with your client: <a href={path}>{typeof location==='undefined'?path:location.origin+path}</a></p>}
  <div aria-busy={loading||busy}>{loading?<p role="status">Loading private client access…</p>:busy?<p role="status">Updating private client access…</p>:page&&!grants.length?<p>No client access grants yet.</p>:null}
   {grants.map(grant=><div className="row" key={grant.client_email.toLowerCase()}><p style={{minWidth:0,overflowWrap:'anywhere'}}>{grant.client_email} · expires {new Date(grant.expires_at).toLocaleDateString()}</p><button type="button" className="button" disabled={busy||loading} aria-label={'Revoke access for '+grant.client_email} onClick={event=>void mutate('DELETE',{email:grant.client_email},event.currentTarget)}>Revoke access</button></div>)}
   {!loading&&!busy&&page&&<MoreRecords<Grant> key={version} path={base} request={request} firstPage={page} label="Load more client access" onItems={items=>setGrants(current=>unique([...current,...items]))} onPage={setPage} onError={error=>{if(unavailable(error))fail(error)}}/>}
  </div>
  {notice&&<p ref={status} role="status" tabIndex={-1}>{notice}</p>}
  {error&&<p className="error" role="alert">{error}</p>}{error&&!page&&!loading&&!busy&&<button type="button" className="button" onClick={()=>void load()}>Retry loading access</button>}
 </section>;
}
