'use client';
import {useState} from 'react';
import Link from 'next/link';
import {ArrowRight, Mail, Lightbulb, LockKeyhole} from 'lucide-react';
import {inquiryDraftKey, type InquiryDraft} from '@/lib/inquiry-draft';
import type {Product} from '@/lib/catalog';

export default function HeroInquiry({product,preview}:{product?:Product;preview:boolean}) {
 const [error,setError]=useState('');
 return <><div className="spectral-inquiry"><h2>Build something together</h2>
  {product&&<form onSubmit={event=>{
   event.preventDefault();setError('');
   const fields=new FormData(event.currentTarget);
   const draft:InquiryDraft={productSlug:product.slug,clientEmail:String(fields.get('clientEmail')).trim(),description:String(fields.get('description')).trim(),expiresAt:Date.now()+30*60*1000};
   if(draft.description.length<20){setError('Describe your idea in at least 20 characters.');return;}
   try{sessionStorage.setItem(inquiryDraftKey,JSON.stringify(draft));}catch{setError('Your browser could not keep this draft. Open a project request below to continue.');return;}
   window.location.assign('/inquiry?product='+encodeURIComponent(product.slug)+'&requestType=NEW_BUILD');
  }}>
   <label><Mail size={17} aria-hidden="true"/><span className="sr-only">Work email</span><input name="clientEmail" type="email" autoComplete="email" required maxLength={254} placeholder="Work email"/></label>
   <label><Lightbulb size={17} aria-hidden="true"/><span className="sr-only">Your project idea</span><input name="description" required minLength={20} maxLength={5000} placeholder="Your project idea"/></label>
   <button className="button primary" type="submit">Continue inquiry <ArrowRight size={17} aria-hidden="true"/></button>
   <p className="spectral-privacy"><LockKeyhole size={15} aria-hidden="true"/>{preview?'Preview only. Nothing is sent.':'Review your brief next. Email confirmation required.'}</p>
   {error&&<p className="error" role="alert">{error}</p>}
  </form>}
 </div><Link className="spectral-request-link" href={preview?'/preview/business':'/workspace/business'}>Start a project request <ArrowRight size={14} aria-hidden="true"/></Link></>;
}
