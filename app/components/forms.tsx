'use client';
import {useState,useRef,useEffect,type FormEvent} from 'react';
import {useSearchParams} from 'next/navigation';
import Link from 'next/link';
import {ArrowRight,CheckCircle2,ShieldCheck,Send} from 'lucide-react';
import FormErrors from './form-errors';
import {api,ApiError} from '@/lib/api';
import type {Product} from '@/lib/catalog';
import {takeInquiryDraft} from '@/lib/inquiry-draft';
import {track,attribution} from '@/lib/analytics';
import {Picker} from './ui';

export function InquiryForm({initialProduct,preview=false}:{initialProduct?:Product;preview?:boolean}){
 const product=initialProduct;
 const [formError,setFormError]=useState<ApiError|null>(null);
 const [description,setDescription]=useState(''),[clientEmail,setClientEmail]=useState('');
 const [requestType,setRequest]=useState('SIMILAR_BUILD'),[budget,setBudget]=useState(''),[timeline,setTimeline]=useState('');
 const [error,setError]=useState(''),[ok,setOk]=useState(false),[busy,setBusy]=useState(false);
 const inFlight=useRef(false),intentKey=useRef(''),success=useRef<HTMLDivElement>(null);
 useEffect(()=>{
  // Restore a browser-stored brief after hydration, with cancellation on navigation.
  const frame=requestAnimationFrame(()=>{
   const params=new URLSearchParams(location.search);
   if(params.get('requestType')==='NEW_BUILD')setRequest('NEW_BUILD');
   if(product){const draft=takeInquiryDraft(product.slug);if(draft){setDescription(draft.description);setClientEmail(draft.clientEmail)}if(!preview)track('inquiry_started',product.id);}
   else setError('Choose a project before sending an inquiry.');
  });
  return()=>cancelAnimationFrame(frame);
 },[product,preview]);
 useEffect(()=>{if(ok)success.current?.focus()},[ok]);
 const fieldError=(field:string)=>formError?.fieldErrors[field];
 const inlineError=(field:string)=>fieldError(field)?<span className="field-error" id={field+'-error'}>{fieldError(field)}</span>:null;
 async function send(e:FormEvent<HTMLFormElement>){
  e.preventDefault();if(inFlight.current)return;
  setFormError(null);setError('');
  if(preview){setError('This is a sample inquiry. Nothing is sent.');return;}
  if(!product){setError('Choose a project before sending an inquiry.');return;}
  if(!budget||!timeline){setFormError(new ApiError('Choose a budget and timeline to continue.','VALIDATION_ERROR',400,undefined,{...(!budget?{budgetBand:'Choose the budget range that fits your project.'}:{}),...(!timeline?{timelineBand:'Choose when you would like to begin.'}:{})}));return;}
  if(!intentKey.current){
   if(!globalThis.crypto?.randomUUID){setError('Open getLancer over a secure connection to send your inquiry.');return;}
   intentKey.current=crypto.randomUUID();
  }
  const body={...Object.fromEntries(new FormData(e.currentTarget)),referenceProductId:product.id,acquisitionSource:attribution(),requestType,budgetBand:budget,timelineBand:timeline};
  inFlight.current=true;setBusy(true);
  try{await api('/inquiries',{method:'POST',headers:{'Idempotency-Key':intentKey.current},body:JSON.stringify(body)});setOk(true);}
  catch(problem){setError((problem as Error).message);if(problem instanceof ApiError)setFormError(problem);}
  finally{inFlight.current=false;setBusy(false);}
 }
 return <main className="wrap structured-form-page paper-inquiry" id="main">
  <Link href="/" className="link">← Explore projects</Link>
  <div className="detailgrid">
   <section className="panel form"><span className="eyebrow">Start a conversation</span><h1>Tell the builder what you need.</h1><p className="form-intro">A clear brief is the first step toward something useful.</p>
    {ok?<div ref={success} tabIndex={-1} className="success feedback-result" role="status"><CheckCircle2 size={28} aria-hidden="true"/><h2>Inquiry saved. Check your inbox.</h2><p>Confirm your email to send this inquiry to the builder.</p><Link className="button" href={product?'/products/'+product.slug:'/'}>Back to the project <ArrowRight size={16}/></Link></div>:
    <form onSubmit={send} className="form" aria-busy={busy}>
     {preview&&<p className="samplebar">Sample inquiry form. You can review the fields; nothing is sent.</p>}
     <FormErrors error={formError}/>
     {product&&<div className="inquiry-reference"><span>Inspired by</span><strong>{product.title}</strong><span>by {product.builder}</span></div>}
     <fieldset disabled={busy} className="paper-form-fields">
      <div data-field="requestType"><label htmlFor="requestType">What do you need?</label><Picker id="requestType" invalid={!!fieldError('requestType')} describedBy={fieldError('requestType')?'requestType-error':undefined} label="Request type" value={requestType} onChange={setRequest} options={['SIMILAR_BUILD','CUSTOMIZE','NEW_BUILD','CONSULTATION']}/>{inlineError('requestType')}</div>
      <div><label htmlFor="description">Describe your project</label><textarea id="description" aria-invalid={!!fieldError('description')} aria-describedby={'description-hint'+(fieldError('description')?' description-error':'')} name="description" value={description} onChange={e=>setDescription(e.target.value)} required minLength={20} maxLength={5000} placeholder="What should the software help you do?"/><span id="description-hint" className="field-hint">Include the problem, who will use it, and the features that matter most.</span>{inlineError('description')}</div>
      <div className="split"><div data-field="budgetBand"><label htmlFor="budgetBand">Budget</label><Picker id="budgetBand" invalid={!!fieldError('budgetBand')} describedBy={fieldError('budgetBand')?'budgetBand-error':undefined} label="Choose budget" value={budget} onChange={setBudget} options={['UNDER_1K','USD_1K_3K','USD_3K_10K','USD_10K_25K','USD_25K_PLUS','NEED_ESTIMATE']}/>{inlineError('budgetBand')}</div><div data-field="timelineBand"><label htmlFor="timelineBand">Timeline</label><Picker id="timelineBand" invalid={!!fieldError('timelineBand')} describedBy={fieldError('timelineBand')?'timelineBand-error':undefined} label="Choose timeline" value={timeline} onChange={setTimeline} options={['WITHIN_MONTH','ONE_TO_THREE_MONTHS','THREE_TO_SIX_MONTHS','FLEXIBLE']}/>{inlineError('timelineBand')}</div></div>
      <div className="split"><div><label htmlFor="clientName">Your name</label><input id="clientName" aria-invalid={!!fieldError('clientName')} aria-describedby={fieldError('clientName')?'clientName-error':undefined} name="clientName" autoComplete="name" required minLength={2} maxLength={100}/>{inlineError('clientName')}</div><div><label htmlFor="clientEmail">Your email</label><input id="clientEmail" name="clientEmail" type="email" autoComplete="email" value={clientEmail} onChange={e=>setClientEmail(e.target.value)} aria-invalid={!!fieldError('clientEmail')} aria-describedby={fieldError('clientEmail')?'clientEmail-error':undefined} maxLength={254} required/>{inlineError('clientEmail')}</div></div>
      <div><label htmlFor="companyName">Company <span className="field-optional">Optional</span></label><input id="companyName" name="companyName" autoComplete="organization" maxLength={150} aria-invalid={!!fieldError('companyName')} aria-describedby={fieldError('companyName')?'companyName-error':undefined}/>{inlineError('companyName')}</div>
      <input name="website" tabIndex={-1} autoComplete="off" aria-hidden="true" style={{display:'none'}}/>
      {error&&!formError&&<div role="alert" className="error">{error}</div>}
      <button className="button primary" disabled={busy||!product||preview}>{busy?'Saving inquiry…':<>Send inquiry <Send size={16} aria-hidden="true"/></>}</button>
     </fieldset>
     <p className="form-privacy"><ShieldCheck size={18} aria-hidden="true"/>No account required. Your inquiry stays private and reaches the builder after email confirmation.</p>
    </form>}
   </section>
   <aside className="prose inquiry-guidance"><span className="eyebrow">A better brief</span><h2>Good work starts with a good conversation.</h2><ol><li><strong>The problem</strong><p>What are you trying to make easier?</p></li><li><strong>The people</strong><p>Who will use it, and what do they need?</p></li><li><strong>The essentials</strong><p>Share your must-have features and any existing tools.</p></li></ol><p>Agree on scope and next steps with your builder before work begins.</p></aside>
  </div>
 </main>;
}

export function ReportForm(){
 const params=useSearchParams();
 const initialTargetId=params.get('product')||'',educationReleaseId=params.get('educationReleaseId')||'';
 return <ReportForTarget key={initialTargetId+':'+educationReleaseId} initialTargetId={initialTargetId} educationReleaseId={educationReleaseId}/>;
}
function ReportForTarget({initialTargetId,educationReleaseId}:{initialTargetId:string;educationReleaseId:string}){
 const [targetId,setTargetId]=useState(initialTargetId),[targetType,setTargetType]=useState('PRODUCT'),[reason,setReason]=useState('OTHER');
 const [busy,setBusy]=useState(false),[error,setError]=useState<ApiError|null>(null),[message,setMessage]=useState('');
 const inFlight=useRef(false),success=useRef<HTMLDivElement>(null);
 useEffect(()=>{if(message)success.current?.focus()},[message]);
 async function submit(e:FormEvent<HTMLFormElement>){
  e.preventDefault();if(inFlight.current)return;
  const data=Object.fromEntries(new FormData(e.currentTarget));
  inFlight.current=true;setBusy(true);setError(null);
  try{await api('/reports',{method:'POST',body:JSON.stringify({...data,reason,targetType:educationReleaseId?'PRODUCT':targetType,...(educationReleaseId?{educationReleaseId}:{})})});setMessage('Your report has been saved for review.');}
  catch(problem){setError(problem instanceof ApiError?problem:new ApiError('Your report could not be confirmed. Check its status before retrying.','REQUEST_FAILED',0));}
  finally{inFlight.current=false;setBusy(false);}
 }
 return <main id="main" className="wrap structured-form-page"><section className="panel form structured-report"><span className="eyebrow">Help keep the community safe</span><h1>Report a concern</h1><p>Report content or choose Appeal to request another look at a moderation decision.</p>
  {message?<div className="success feedback-result" ref={success} role="status" tabIndex={-1}><CheckCircle2 size={28} aria-hidden="true"/><h2>Thank you for speaking up.</h2><p>{message}</p><Link href="/" className="button">Explore projects <ArrowRight size={16}/></Link></div>:
  <form className="form" onSubmit={submit} aria-busy={busy}><FormErrors error={error}/><fieldset disabled={busy} className="paper-form-fields">
   <div data-field="targetType"><label htmlFor="targetType">Report type</label>{educationReleaseId?<p className="kit-notice">Exact education artifact <code>{educationReleaseId}</code>. Its frozen source hash and disclosures will be stored privately for operator review.</p>:<Picker id="targetType" invalid={!!error?.fieldErrors.targetType} describedBy={error?.fieldErrors.targetType?'targetType-error':undefined} label="Report type" value={targetType} onChange={setTargetType} options={['PRODUCT','USER','REVIEW','INQUIRY']}/>} {error?.fieldErrors.targetType&&<span className="field-error" id="targetType-error">{error.fieldErrors.targetType}</span>}</div>
   <div><label htmlFor="targetId">Record ID</label><input id="targetId" name="targetId" readOnly={Boolean(educationReleaseId)} required value={targetId} onChange={e=>setTargetId(e.target.value)} aria-invalid={!!error?.fieldErrors.targetId} aria-describedby={'targetId-hint'+(error?.fieldErrors.targetId?' targetId-error':'')}/><span className="field-hint" id="targetId-hint">Use the ID of the content or decision you want reviewed.</span>{error?.fieldErrors.targetId&&<span className="field-error" id="targetId-error">{error.fieldErrors.targetId}</span>}</div>
   <div data-field="reason"><label htmlFor="reason">Reason</label><Picker id="reason" invalid={!!error?.fieldErrors.reason} describedBy={error?.fieldErrors.reason?'reason-error':undefined} label="Reason" value={reason} onChange={setReason} options={['FAKE_PRODUCT','STOLEN_WORK','IMPERSONATION','MALICIOUS_LINK','PHISHING','SPAM','HARASSMENT','COPYRIGHT_IP','CONFIDENTIAL_DATA','MISLEADING_CLAIM','FAKE_REVIEW','CLIENT_SPAM','APPEAL','OTHER']}/>{error?.fieldErrors.reason&&<span className="field-error" id="reason-error">{error.fieldErrors.reason}</span>}</div>
   <div><label htmlFor="detail">What happened?</label><textarea id="detail" name="detail" minLength={10} maxLength={3000} required aria-invalid={!!error?.fieldErrors.detail} aria-describedby={'detail-hint'+(error?.fieldErrors.detail?' detail-error':'')}/><span className="field-hint" id="detail-hint">Be specific. Share enough context to help us review your concern.</span>{error?.fieldErrors.detail&&<span className="field-error" id="detail-error">{error.fieldErrors.detail}</span>}</div>
   <button className="button primary" disabled={busy}>{busy?'Submitting…':'Submit report'}<ArrowRight size={16} aria-hidden="true"/></button>
  </fieldset></form>}
 </section></main>;
}
