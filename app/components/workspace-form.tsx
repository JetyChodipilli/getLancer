'use client';
import {createContext,useContext,useId,useRef,useState,type FormEvent,type ReactNode} from 'react';
const FieldErrors=createContext<Record<string,string>>({});
const limits:Record<string,[number,number]>={name:[2,120],summary:[10,3000],availability:[1,60],projectRange:[0,200],message:[1,3000],title:[2,120],description:[10,5000],skills:[0,500],compensationBand:[0,200],budget:[0,200],timeline:[0,200],projectLabel:[0,200],note:[0,3000],reason:[10,3000]};

export function Field({name,label,value='',type='text',options,required=true,minLength,maxLength}:{name:string;label:string;value?:any;type?:string;options?:{value:string;label:string}[];required?:boolean;minLength?:number;maxLength?:number}){
 const id=useId(),error=useContext(FieldErrors)[name],range=limits[name];
 const shared={id,name,defaultValue:value||'',required,'aria-invalid':error?true:undefined,'aria-describedby':error?id+'-error':undefined};
 return <label className="team-field" htmlFor={id}>{label}{options?<select {...shared}>{options.map(o=><option key={o.value} value={o.value}>{o.label}</option>)}</select>:type==='textarea'?<textarea {...shared} minLength={minLength??range?.[0]} maxLength={maxLength??range?.[1]??4000}/>:<input {...shared} type={type} minLength={minLength??range?.[0]} maxLength={maxLength??range?.[1]??(type==='text'?500:undefined)}/>} {error&&<span id={id+'-error'} className="team-error">{error}</span>}</label>;
}
export function TeamForm({children,onSave,label='Save changes',busy=false}:{children:ReactNode;onSave:(data:Record<string,string>)=>Promise<unknown>;label?:string;busy?:boolean}){
 const [error,setError]=useState(''),[fields,setFields]=useState<Record<string,string>>({}),[pending,setPending]=useState(false),summary=useRef<HTMLParagraphElement>(null);
 return <FieldErrors.Provider value={fields}><form className="team-form" onSubmit={async(e:FormEvent<HTMLFormElement>)=>{e.preventDefault();const form=e.currentTarget;setError('');setFields({});setPending(true);try{await onSave(Object.fromEntries([...new FormData(form)].map(([key,value])=>[key,String(value)])))}catch(e){const problem=e as Error&{fieldErrors?:Record<string,string>};setError(problem.message);setFields(problem.fieldErrors||{});requestAnimationFrame(()=>{const invalid=[...form.querySelectorAll<HTMLInputElement|HTMLSelectElement|HTMLTextAreaElement>('input,select,textarea')].find(input=>problem.fieldErrors?.[input.name]);(invalid||summary.current)?.focus()});}finally{setPending(false)}}}><fieldset disabled={pending||busy}>{children}<div>{error&&<p ref={summary} tabIndex={-1} role="alert" className="team-error">{error}</p>}<button className="button primary" disabled={pending||busy}>{pending?'Saving…':label}</button></div></fieldset></form></FieldErrors.Provider>;
}
