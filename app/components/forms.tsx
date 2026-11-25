'use client';
import {useState,useRef,useEffect,type FormEvent} from 'react';
import {useSearchParams} from 'next/navigation';
import Link from 'next/link';
import {ArrowRight,CheckCircle2,ShieldCheck,Send} from 'lucide-react';
import FormErrors from './form-errors';
import {api,ApiError} from '@/lib/api';
import type {Product} from '@/lib/catalog';
import {takeInquiryDraft} from '@/lib/inquiry-draft';
import {demoProjectPath} from '@/lib/demo-links';
import {track,attribution} from '@/lib/analytics';
import {Picker} from './ui';

const requestTypes = {
 SIMILAR_BUILD: 'Build something similar',
 CUSTOMIZE: 'Customize this project',
 NEW_BUILD: 'Build something new',
 CONSULTATION: 'Talk through an idea',
};
const budgetBands = {
 UNDER_1K: 'Under $1,000 USD',
 USD_1K_3K: '$1,000–$3,000 USD',
 USD_3K_10K: '$3,000–$10,000 USD',
 USD_10K_25K: '$10,000–$25,000 USD',
 USD_25K_PLUS: '$25,000 or more USD',
 NEED_ESTIMATE: 'I need an estimate',
};
const timelineBands = {
 WITHIN_MONTH: 'Within a month',
 ONE_TO_THREE_MONTHS: '1–3 months',
 THREE_TO_SIX_MONTHS: '3–6 months',
 FLEXIBLE: 'Flexible',
};
const reportTypes = { PRODUCT: 'Project', USER: 'Account', REVIEW: 'Review', INQUIRY: 'Inquiry' };
const reportReasons = {
 FAKE_PRODUCT: 'Fake project',
 STOLEN_WORK: 'Stolen work',
 IMPERSONATION: 'Impersonation',
 MALICIOUS_LINK: 'Malicious link',
 PHISHING: 'Phishing',
 SPAM: 'Spam',
 HARASSMENT: 'Harassment',
 COPYRIGHT_IP: 'Copyright or intellectual property',
 CONFIDENTIAL_DATA: 'Confidential information',
 MISLEADING_CLAIM: 'Misleading claim',
 FAKE_REVIEW: 'Fake review',
 CLIENT_SPAM: 'Spam from a client',
 APPEAL: 'Appeal a decision',
 OTHER: 'Other',
};

export function InquiryForm({initialProduct,preview=false}:{initialProduct:Product;preview?:boolean}){
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
   if(!preview){
    const draft=takeInquiryDraft(product.slug);
    if(draft){setDescription(draft.description);setClientEmail(draft.clientEmail);}
    track('inquiry_started',product.id);
   }
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
  <Link prefetch={false} href="/" className="link">← Explore projects</Link>
  <div className="detailgrid">
   <section className="panel">
    <header className="structured-form-header"><h1>Tell the builder what you need.</h1></header>
    {ok?<div ref={success} tabIndex={-1} className="success feedback-result" role="status"><CheckCircle2 size={28} aria-hidden="true"/><h2>Inquiry saved. Check your inbox.</h2><p>Confirm your email to send this inquiry to the builder.</p><Link prefetch={false} className="button" href={product?'/products/'+product.slug:'/'}>Back to the project <ArrowRight size={16}/></Link></div>:
    <form onSubmit={send} className="form" aria-busy={busy}>
     {preview&&<div className="samplebar inquiry-demo-start"><p>Preview the form below or try a simulated inquiry using a sample account.</p>
      <Link className="button primary" href={demoProjectPath('inquiry',product.slug)}>Try a sample inquiry <ArrowRight size={16} aria-hidden="true"/></Link>
     </div>}
     <FormErrors error={formError}/>
     {product&&<div className="inquiry-reference"><span>Inspired by</span><span><strong>{product.title}</strong> by {product.builder}</span></div>}
     <fieldset disabled={busy||preview} className="paper-form-fields">
      <div className="form-field" data-field="requestType"><label htmlFor="requestType">What do you need?</label><Picker id="requestType" invalid={!!fieldError('requestType')} describedBy={fieldError('requestType')?'requestType-error':undefined} label="What do you need?" value={requestType} onChange={setRequest} options={Object.keys(requestTypes)} optionLabels={requestTypes}/>{inlineError('requestType')}</div>
      <div className="form-field"><label htmlFor="description">Describe your project</label><span id="description-hint" className="field-hint">Include the problem, who will use it, and the features that matter most.</span><textarea id="description" aria-invalid={!!fieldError('description')} aria-describedby={'description-hint'+(fieldError('description')?' description-error':'')} name="description" value={description} onChange={e=>setDescription(e.target.value)} required minLength={20} maxLength={5000}/>{inlineError('description')}</div>
      <div className="split"><div className="form-field" data-field="budgetBand"><label htmlFor="budgetBand">Budget</label><Picker id="budgetBand" invalid={!!fieldError('budgetBand')} describedBy={fieldError('budgetBand')?'budgetBand-error':undefined} label="Choose budget" value={budget} onChange={setBudget} options={Object.keys(budgetBands)} optionLabels={budgetBands}/>{inlineError('budgetBand')}</div><div className="form-field" data-field="timelineBand"><label htmlFor="timelineBand">Timeline</label><Picker id="timelineBand" invalid={!!fieldError('timelineBand')} describedBy={fieldError('timelineBand')?'timelineBand-error':undefined} label="Choose timeline" value={timeline} onChange={setTimeline} options={Object.keys(timelineBands)} optionLabels={timelineBands}/>{inlineError('timelineBand')}</div></div>
      <div className="split"><div className="form-field"><label htmlFor="clientName">Your name</label><input id="clientName" aria-invalid={!!fieldError('clientName')} aria-describedby={fieldError('clientName')?'clientName-error':undefined} name="clientName" autoComplete="name" required minLength={2} maxLength={100}/>{inlineError('clientName')}</div><div className="form-field"><label htmlFor="clientEmail">Your email</label><input id="clientEmail" name="clientEmail" type="email" autoComplete="email" value={clientEmail} onChange={e=>setClientEmail(e.target.value)} aria-invalid={!!fieldError('clientEmail')} aria-describedby={fieldError('clientEmail')?'clientEmail-error':undefined} maxLength={254} required/>{inlineError('clientEmail')}</div></div>
      <div className="form-field"><label htmlFor="companyName">Company <span className="field-optional">Optional</span></label><input id="companyName" name="companyName" autoComplete="organization" maxLength={150} aria-invalid={!!fieldError('companyName')} aria-describedby={fieldError('companyName')?'companyName-error':undefined}/>{inlineError('companyName')}</div>
      <input name="website" tabIndex={-1} autoComplete="off" aria-hidden="true" style={{display:'none'}}/>
      {error&&!formError&&<div role="alert" className="error">{error}</div>}
      {!preview&&<button className="button primary" disabled={busy}>{busy?'Saving inquiry…':<>Send inquiry <Send size={16} aria-hidden="true"/></>}</button>}
     </fieldset>
     {!preview&&<p className="form-privacy"><ShieldCheck size={18} aria-hidden="true"/>No account required. Your inquiry stays private and reaches the builder after email confirmation.</p>}
    </form>}
   </section>
   <aside className="prose inquiry-guidance">
    <h2>Before you send</h2>
    <p>Mention any tools you already use. Call out features you need from day one.</p>
    <p>Agree on scope and next steps with the builder before work begins.</p>
   </aside>
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
 return <main id="main" className="wrap structured-form-page"><section className="panel structured-report">
  <header className="structured-form-header">
   <h1>Report a concern</h1><p>Report content or ask us to review a moderation decision.</p>
  </header>
  {message?<div className="success feedback-result" ref={success} role="status" tabIndex={-1}><CheckCircle2 size={28} aria-hidden="true"/><h2>Thank you for speaking up.</h2><p>{message}</p><Link prefetch={false} href="/" className="button">Explore projects <ArrowRight size={16}/></Link></div>:
  <form className="form" onSubmit={submit} aria-busy={busy}><FormErrors error={error}/><fieldset disabled={busy} className="paper-form-fields">
   <div className="form-field" data-field="targetType">{educationReleaseId?<p className="kit-notice">Exact education artifact <code>{educationReleaseId}</code>. Its frozen source hash and disclosures will be stored privately for operator review.</p>:<><label htmlFor="targetType">Report type</label><Picker id="targetType" invalid={!!error?.fieldErrors.targetType} describedBy={error?.fieldErrors.targetType?'targetType-error':undefined} label="Report type" value={targetType} onChange={setTargetType} options={Object.keys(reportTypes)} optionLabels={reportTypes}/></>} {error?.fieldErrors.targetType&&<span className="field-error" id="targetType-error">{error.fieldErrors.targetType}</span>}</div>
   <div className="form-field"><label htmlFor="targetId">Record ID</label><input id="targetId" name="targetId" readOnly={Boolean(educationReleaseId)} required value={targetId} onChange={e=>setTargetId(e.target.value)} aria-invalid={!!error?.fieldErrors.targetId} aria-describedby={'targetId-hint'+(error?.fieldErrors.targetId?' targetId-error':'')}/><span className="field-hint" id="targetId-hint">Use the ID of the content or decision you want reviewed.</span>{error?.fieldErrors.targetId&&<span className="field-error" id="targetId-error">{error.fieldErrors.targetId}</span>}</div>
   <div className="form-field" data-field="reason"><label htmlFor="reason">Reason</label><Picker id="reason" invalid={!!error?.fieldErrors.reason} describedBy={error?.fieldErrors.reason?'reason-error':undefined} label="Reason" value={reason} onChange={setReason} options={Object.keys(reportReasons)} optionLabels={reportReasons}/>{error?.fieldErrors.reason&&<span className="field-error" id="reason-error">{error.fieldErrors.reason}</span>}</div>
   <div className="form-field"><label htmlFor="detail">What happened?</label><span className="field-hint" id="detail-hint">Be specific. Share enough context to help us review your concern.</span><textarea id="detail" name="detail" minLength={10} maxLength={3000} required aria-invalid={!!error?.fieldErrors.detail} aria-describedby={'detail-hint'+(error?.fieldErrors.detail?' detail-error':'')}/>{error?.fieldErrors.detail&&<span className="field-error" id="detail-error">{error.fieldErrors.detail}</span>}</div>
   <button className="button primary" disabled={busy}>{busy?'Submitting…':'Submit report'}<ArrowRight size={16} aria-hidden="true"/></button>
  </fieldset></form>}
 </section></main>;
}
