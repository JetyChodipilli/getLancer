'use client';
import {useEffect,useId,useRef} from 'react';
import {CircleAlert} from 'lucide-react';
import type {ApiError} from '@/lib/api';

function fieldLabel(field:string){return field.replace(/([a-z])([A-Z])/g,'$1 $2').replaceAll('_',' ');}
export default function FormErrors({error,prefix='',onField}:{error:ApiError|null;prefix?:string;onField?:(field:string)=>void}){
 const ref=useRef<HTMLDivElement>(null);
 const titleId=useId();
 useEffect(()=>{if(error)ref.current?.focus()},[error]);
 if(!error)return null;
 const fields=Object.entries(error.fieldErrors);
 function focusField(event:React.MouseEvent<HTMLAnchorElement>,field:string){
  if(onField){event.preventDefault();onField(field);return;}
  const scope=ref.current?.closest('form');
  const target=Array.from(scope?.querySelectorAll<HTMLElement>('input,select,textarea,button,[tabindex]')||[]).find(element=>element.id===prefix+field||element.getAttribute('name')===field);
  // Radix pickers live inside a field wrapper rather than a native named input.
  const wrapper=scope&&Array.from(scope.querySelectorAll<HTMLElement>('[data-field]')).find(element=>element.dataset.field===field);
  const control=target||wrapper?.querySelector<HTMLElement>('button,input,select,textarea,[tabindex]');
  if(control){event.preventDefault();control.focus();control.scrollIntoView({block:'nearest'});}
 }
 return <div className="error form-error-summary" role="alert" aria-labelledby={titleId} tabIndex={-1} ref={ref}>
  <div className="feedback-heading"><CircleAlert size={20} aria-hidden="true"/><strong id={titleId}>{fields.length?'Check these details':'This action needs your attention'}</strong></div>
  <p>{error.message}</p>
  {fields.length>0&&<ul>{fields.map(([field,message])=><li key={field}><a href={'#'+encodeURIComponent(prefix+field)} onClick={event=>focusField(event,field)}>{fieldLabel(field)}: {message}</a></li>)}</ul>}
 </div>;
}
