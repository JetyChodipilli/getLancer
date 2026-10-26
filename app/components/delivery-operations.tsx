'use client';
import {useCallback,useEffect,useRef,useState} from 'react';
import {api,ApiError} from '@/lib/api';
import MoreRecords from './more-records';

type RecordRow=Record<string,any>;
const paths={disputes:'/admin/delivery/disputes',accounts:'/admin/payments/accounts',attention:'/admin/payments/attention'} as const;
type Collection=keyof typeof paths;
type OperationPage={items:RecordRow[];page:number;size:number;hasMore:boolean;totalItems:number;totalPages:number};
export function operationPage(value:unknown,expectedPage:number):OperationPage{
 const page=value as OperationPage|null;
 if(!page||!Array.isArray(page.items)||page.page!==expectedPage||!Number.isInteger(page.size)||page.size<1||page.size>100||typeof page.hasMore!=='boolean'||!Number.isSafeInteger(page.totalItems)||page.totalItems<0||!Number.isSafeInteger(page.totalPages)||page.totalPages<0||page.items.length>page.size||page.items.some(row=>!row||typeof row!=='object'||typeof row.id!=='string'||!row.id))throw Error('Operations records could not be verified. Please retry.');
 return page;
}
export function appendOperationRecords(current:RecordRow[],next:RecordRow[]):RecordRow[]{return Array.from(new Map([...current,...next].map(row=>[row.id,row])).values())}
export default function DeliveryOperations(){
 const [open,setOpen]=useState(false),[busy,setBusy]=useState(false),[blocked,setBlocked]=useState(false),[error,setError]=useState(''),[note,setNote]=useState(''),[pages,setPages]=useState<Partial<Record<Collection,OperationPage>>>({}),[loading,setLoading]=useState<Partial<Record<Collection,boolean>>>({}),[errors,setErrors]=useState<Partial<Record<Collection,string>>>({}),[epoch,setEpoch]=useState(0);
 const generation=useRef(0),mounted=useRef(true),mutating=useRef(false),versions=useRef({disputes:0,accounts:0,attention:0});
 useEffect(()=>{mounted.current=true;return()=>{mounted.current=false;generation.current++}},[]);
 const clear=useCallback(()=>{generation.current++;setPages({});setLoading({});setErrors({});setEpoch(value=>value+1)},[]);
 const denied=useCallback((problem:unknown)=>{if(problem instanceof ApiError&&[401,403,404].includes(problem.status)){clear();setBlocked(true);setError(problem.message);return true}return false},[clear]);
 async function loadCollection(key:Collection,run=generation.current){
  const version=++versions.current[key];setLoading(old=>({...old,[key]:true}));setErrors(old=>({...old,[key]:''}));setPages(old=>({...old,[key]:undefined}));
  try{const response=operationPage(await api(paths[key]+'?page=0&size=50'),0);if(mounted.current&&run===generation.current&&version===versions.current[key])setPages(old=>({...old,[key]:response}))}
  catch(e){if(mounted.current&&run===generation.current&&version===versions.current[key]&&!denied(e))setErrors(old=>({...old,[key]:(e as Error).message}))}
  finally{if(mounted.current&&run===generation.current&&version===versions.current[key])setLoading(old=>({...old,[key]:false}))}
 }
 async function load(){clear();setBlocked(false);setError('');setNote('');const run=generation.current;await Promise.all((Object.keys(paths) as Collection[]).map(key=>loadCollection(key,run)))}
 const pageRequest=useCallback(async(path:string)=>{
  const run=generation.current,key=(Object.keys(paths) as Collection[]).find(key=>paths[key]===path.split('?')[0]),version=key?versions.current[key]:0;
  try{const response=await api(path);if(!mounted.current||run!==generation.current||!key||version!==versions.current[key])throw Error('Operations changed. Please refresh the records.');return operationPage(response,Number(new URLSearchParams(path.split('?')[1]).get('page')))}
  catch(e){if(mounted.current&&run===generation.current)denied(e);throw e}
 },[denied]);
 async function save(path:string,body:unknown){
  if(mutating.current)return;mutating.current=true;setBusy(true);setError('');setNote('');generation.current++;setLoading({});setErrors({});setEpoch(value=>value+1);const run=generation.current;
  try{await api(path,{method:'POST',body:JSON.stringify(body)});if(!mounted.current||run!==generation.current)return;await load();if(mounted.current)setNote('Decision saved.')}
  catch(e){if(mounted.current&&run===generation.current){denied(e);setError((e as Error).message)}}
  finally{mutating.current=false;if(mounted.current)setBusy(false)}
 }
 function more(key:Collection,label:string){const page=pages[key];return page&&!busy&&<MoreRecords<RecordRow> key={key+':'+epoch} path={paths[key]} firstPage={page} request={pageRequest} label={label} onItems={items=>setPages(old=>old[key]?{...old,[key]:{...old[key]!,items:appendOperationRecords(old[key]!.items,items)}}:old)} onPage={info=>setPages(old=>old[key]?{...old,[key]:{...old[key]!,...info}}:old)}/>}
 function feedback(key:Collection){return <>{loading[key]&&<p role="status">Loading records…</p>}{errors[key]&&<p className="error" role="alert">{errors[key]} <button className="link" type="button" disabled={busy||loading[key]} onClick={()=>void loadCollection(key)}>Retry {key==='accounts'?'seller accounts':key==='attention'?'payment attention':'delivery disputes'}</button></p>}</>}
 const disabled=busy||Object.values(loading).some(Boolean),disputes=pages.disputes?.items||[],accounts=pages.accounts?.items||[],attention=pages.attention?.items||[];
 return <details className="panel" open={open} onToggle={e=>{const expanded=e.currentTarget.open;if(expanded!==open){setOpen(expanded);if(expanded)void load();else clear()}}}><summary>Delivery disputes and Razorpay operations</summary>
 <p>These controls require an administrator session with MFA. Capture, transfer and bank settlement are separate outcomes.</p>
 <button className="button" type="button" disabled={disabled} onClick={()=>void load()}>Refresh payment operations</button>
 {error&&<p className="error" role="alert">{error}</p>}{note&&<p className="success" role="status">{note}</p>}
 {!blocked&&<><section aria-labelledby="delivery-disputes-title"><h3 id="delivery-disputes-title">Delivery disputes</h3>{feedback('disputes')}{pages.disputes&&!disputes.some(d=>d.status==='OPEN')&&<p>No open disputes in loaded records.</p>}{disputes.filter(d=>d.status==='OPEN').map(d=><form className="form panel" key={d.id} onSubmit={e=>{e.preventDefault();void save('/admin/delivery/disputes/'+d.id+'/resolve',Object.fromEntries(new FormData(e.currentTarget)))}}><strong>{d.title||d.engagementId}</strong><p>{d.reason}</p><label htmlFor={'dispute-resolution-'+d.id}>Resolution</label><select id={'dispute-resolution-'+d.id} name="resolution"><option value="RESUME">Resume the engagement</option><option value="CANCEL">Cancel the engagement</option></select><label>Decision reason<textarea name="reason" minLength={10} maxLength={2000} required/></label><p>Record any refund or Route reversal separately in Razorpay, then reconcile the payment. This decision does not move money.</p><button className="button" disabled={disabled}>Record dispute resolution</button></form>)}{more('disputes','Load more delivery disputes')}</section>
 <section aria-labelledby="seller-accounts-title"><h3 id="seller-accounts-title">Seller linked accounts</h3><p>Complete seller onboarding in Razorpay Route first. Map its verified account identifier here; no bank details are stored in getLancer.</p>{feedback('accounts')}
 <form className="form" onSubmit={e=>{e.preventDefault();const f=new FormData(e.currentTarget),kind=String(f.get('kind'));void save('/admin/payments/accounts',{[kind]:f.get('seller'),accountId:f.get('accountId'),activationConfirmed:f.get('activationConfirmed')==='on'})}}><label>Seller type<select name="kind"><option value="builderUserId">Builder</option><option value="teamId">Team</option></select></label><label>Seller ID<input name="seller" required pattern="[0-9a-fA-F-]{36}"/></label><label>Razorpay linked account ID<input name="accountId" required pattern="acc_[A-Za-z0-9]+" maxLength={80}/></label><label><input type="checkbox" name="activationConfirmed" required/> I verified completed seller KYC and active Route configuration in Razorpay.</label><button className="button" disabled={disabled}>Verify and map linked account</button></form>
 {accounts.map(a=><p key={a.id}>{a.builderUserId||a.teamId} · {a.accountId}</p>)}{pages.accounts&&!accounts.length&&<p>No seller accounts.</p>}{more('accounts','Load more seller accounts')}</section>
 <section aria-labelledby="payment-attention-title"><h3 id="payment-attention-title">Orders requiring attention</h3><p>An interrupted order creation must be reconciled before another checkout. Find the order in Razorpay using the payment attempt's receipt, and enter its order ID. The server checks receipt, amount, currency and seller before accepting it.</p>{feedback('attention')}
 {pages.attention&&!attention.length&&<p>No orders require attention.</p>}{attention.map(p=><form className="form panel" key={p.id} onSubmit={e=>{e.preventDefault();void save('/admin/payments/'+p.id+'/bind-order',Object.fromEntries(new FormData(e.currentTarget)))}}><strong>Attempt {p.id}</strong><p>{p.status} · {p.currency} {Number(p.amountMinor||0)/100}</p>{p.attentionReason&&<p>{p.attentionReason}</p>}{p.orderId?<><p>Provider order {p.orderId}</p><button type="button" className="button" disabled={disabled} onClick={()=>void save('/admin/payments/'+p.id+'/reconcile',{})}>Reconcile provider state</button></>:<><label>Provider order ID<input name="orderId" required pattern="order_[A-Za-z0-9]+" maxLength={80}/></label><button className="button" disabled={disabled}>Verify provider order</button></>}</form>)}{more('attention','Load more payment attention')}</section></>}
 </details>;
}
