'use client';
import {useState,type FormEvent,type ReactNode} from 'react';

export function Field({name,label,value='',type='text',options,required=true,minLength,maxLength}:{name:string;label:string;value?:any;type?:string;options?:{value:string;label:string}[];required?:boolean;minLength?:number;maxLength?:number}){
 return <label className="team-field">{label}{options?<select name={name} defaultValue={value} required={required}>{options.map(o=><option key={o.value} value={o.value}>{o.label}</option>)}</select>:type==='textarea'?<textarea name={name} defaultValue={value||''} required={required} minLength={minLength} maxLength={maxLength??4000}/>:<input name={name} type={type} defaultValue={value||''} required={required} minLength={minLength} maxLength={maxLength??(type==='text'?500:undefined)}/>}</label>;
}
export function TeamForm({children,onSave,label='Save changes',busy=false}:{children:ReactNode;onSave:(data:Record<string,string>)=>Promise<unknown>;label?:string;busy?:boolean}){
 const [error,setError]=useState(''),[pending,setPending]=useState(false);
 return <form className="team-form" onSubmit={async(e:FormEvent<HTMLFormElement>)=>{e.preventDefault();setError('');setPending(true);try{await onSave(Object.fromEntries([...new FormData(e.currentTarget)].map(([key,value])=>[key,String(value)])))}catch(e){setError((e as Error).message)}finally{setPending(false)}}}><fieldset disabled={pending||busy}>{children}<div>{error&&<p role="alert" className="team-error">{error}</p>}<button className="button primary" disabled={pending||busy}>{pending?'Saving…':label}</button></div></fieldset></form>;
}
