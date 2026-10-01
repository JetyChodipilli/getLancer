'use client';
import ExternalLink from '@/app/components/external-link';

import {useState,useEffect} from 'react';
import Link from 'next/link';
import {api} from '@/lib/api';
export default function PrivatePreview({id}:{id:string}){
 const [project,setProject]=useState<any>(null),[error,setError]=useState('');
 useEffect(()=>{api('/private/products/'+encodeURIComponent(id)).then(setProject).catch(e=>setError(e.message));},[id]);
 return <main id="main" className="wrap prose"><Link href="/workspace">← Workspace</Link><p className="eyebrow">Private preview</p>{error?<div role="alert"><p>{error}</p><Link href="/login">Log in with the email your builder authorized</Link></div>:!project?<p role="status">Checking access…</p>:<><h1>{project.title}</h1><p>{project.summary}</p><p>{project.description}</p><h2>Builder contribution</h2><p>{project.contribution}</p>{project.media.map((m:any)=><img key={m.id} src={m.url} alt={m.alt}/>)}{project.liveUrl&&<ExternalLink href={project.liveUrl} rel="noopener noreferrer" target="_blank">Open external demo ↗</ExternalLink>}<p className="muted">Shared privately for review. Access expires automatically. External demos have their own access controls.</p></>}</main>;
}
