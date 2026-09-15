'use client';
import {useEffect,useRef} from 'react';
import type {ApiError} from '@/lib/api';
export default function FormErrors({error,prefix=''}:{error:ApiError|null;prefix?:string}){
 const ref=useRef<HTMLDivElement>(null);
 useEffect(()=>{if(error)ref.current?.focus()},[error]);
 if(!error)return null;
 return <div className="error" role="alert" tabIndex={-1} ref={ref}><strong>Check these details</strong><p>{error.message}</p>{Object.entries(error.fieldErrors).length>0&&<ul>{Object.entries(error.fieldErrors).map(([field,message])=><li key={field}><a href={'#'+prefix+field}>{field}: {message}</a></li>)}</ul>}</div>;
}
