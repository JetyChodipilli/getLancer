'use client';
import {createContext,useContext,useEffect,useId,useRef,useState,type FormEvent,type ReactNode} from 'react';
import {CheckCircle2,CircleAlert} from 'lucide-react';
const FieldErrors=createContext<Record<string,string>>({});
const limits:Record<string,[number,number]>={name:[2,120],summary:[10,3000],availability:[1,60],projectRange:[0,200],message:[1,3000],title:[2,120],description:[10,5000],skills:[0,500],compensationBand:[0,200],budget:[0,200],timeline:[0,200],projectLabel:[0,200],note:[0,3000],reason:[10,3000]};

export function Field({name,label,value='',type='text',options,required=true,minLength,maxLength,hint}:{name:string;label:string;value?:string|number|readonly string[]|null;type?:string;options?:{value:string;label:string}[];required?:boolean;minLength?:number;maxLength?:number;hint?:string}){
 const id=useId(),error=useContext(FieldErrors)[name],range=limits[name];
 const describedBy=[hint?id+'-hint':'',error?id+'-error':''].filter(Boolean).join(' ')||undefined;
 const shared={id,name,defaultValue:value??'',required,'aria-label':label,'aria-invalid':error?true:undefined,'aria-describedby':describedBy};
 return <label className="team-field" htmlFor={id} data-field={name}>
  <span className="team-field-label">{label}{!required&&!label.toLowerCase().includes('optional')&&<small>Optional</small>}</span>
  {options?<select {...shared}>{options.map(o=><option key={o.value} value={o.value}>{o.label}</option>)}</select>:type==='textarea'?<textarea {...shared} minLength={minLength??range?.[0]} maxLength={maxLength??range?.[1]??4000}/>:<input {...shared} type={type} minLength={minLength??range?.[0]} maxLength={maxLength??range?.[1]??(type==='text'?500:undefined)}/>}
  {hint&&<span id={id+'-hint'} className="team-field-hint">{hint}</span>}
  {error&&<span id={id+'-error'} className="team-error">{error}</span>}
 </label>;
}
export function TeamForm({children,onSave,label='Save changes',busy=false}:{children:ReactNode;onSave:(data:Record<string,string>)=>Promise<unknown>;label?:string;busy?:boolean}){
 const [error,setError]=useState(''),[fields,setFields]=useState<Record<string,string>>({}),[pending,setPending]=useState(false),[completed,setCompleted]=useState(false);
 const summary=useRef<HTMLDivElement>(null),formRef=useRef<HTMLFormElement>(null),focusError=useRef(false),inFlight=useRef(false);
 useEffect(()=>{
  if(!error||!focusError.current||pending||busy)return;
  focusError.current=false;summary.current?.focus();
 },[error,fields,pending,busy]);
 function focusField(name:string){
  Array.from(formRef.current?.querySelectorAll<HTMLInputElement|HTMLSelectElement|HTMLTextAreaElement>('input,select,textarea')||[]).find(input=>input.name===name)?.focus();
 }
 async function submit(e:FormEvent<HTMLFormElement>){
  e.preventDefault();if(inFlight.current||busy)return;
  const data=Object.fromEntries([...new FormData(e.currentTarget)].map(([key,value])=>[key,String(value)]));
  inFlight.current=true;focusError.current=false;setError('');setFields({});setCompleted(false);setPending(true);
  try{const result=await onSave(data);if(result!==false)setCompleted(true);}
  catch(problem){
   const failure=problem as Error&{fieldErrors?:Record<string,string>};
   focusError.current=true;setError(failure.message||'This action could not be confirmed. Refresh the current record before retrying.');setFields(failure.fieldErrors||{});
  }finally{inFlight.current=false;setPending(false);}
 }
 return <FieldErrors.Provider value={fields}><form ref={formRef} className="team-form" aria-busy={pending||busy} onSubmit={submit} onChange={()=>setCompleted(false)}>
  {error&&<div ref={summary} tabIndex={-1} role="alert" className="error form-error-summary"><div className="feedback-heading"><CircleAlert size={20} aria-hidden="true"/><strong>{Object.keys(fields).length?'Check these details':'This action needs your attention'}</strong></div><p>{error}</p>{Object.entries(fields).length>0&&<ul>{Object.entries(fields).map(([name,message])=><li key={name}><button type="button" className="error-field-link" onClick={()=>focusField(name)}>{name.replace(/([a-z])([A-Z])/g,'$1 $2')}: {message}</button></li>)}</ul>}</div>}
  <fieldset disabled={pending||busy}>{children}<div className="team-form-actions"><button type="submit" className="button primary" disabled={pending||busy}>{pending?'Working…':label}</button><span className="team-form-status" role="status" aria-live="polite">{pending?'Your request is being processed…':completed?<><CheckCircle2 size={16} aria-hidden="true"/>Action completed.</>:''}</span></div></fieldset>
 </form></FieldErrors.Provider>;
}
