'use client';
import {useEffect,useState} from 'react';
import {ArrowUpRight} from 'lucide-react';
import {api} from '@/lib/api';
import {safeHostedUrl} from '@/lib/hosting';

type PublicDemo={id:string;title:string;version:string;url:string;expiresAt:string};
export default function HostedDemos({productId}:{productId:string}){
 const [items,setItems]=useState<PublicDemo[]>([]);
 useEffect(()=>{let current=true;setItems([]);api('/hosting/products/'+encodeURIComponent(productId)).then(result=>{if(current)setItems(result.items);}).catch(()=>{if(current)setItems([]);});return()=>{current=false;};},[productId]);
 const eligible=items.filter(item=>safeHostedUrl(item.url)&&Number.isFinite(Date.parse(item.expiresAt))&&Date.parse(item.expiresAt)>Date.now());
 if(!eligible.length)return null;
 return <section className="panel" aria-label="Hosted static frontends"><h2>Hosted frontend demos</h2><p>Reviewed built frontend files on a separate demo origin.</p>{eligible.map(item=><div className="stack" key={item.id}><a className="button" href={safeHostedUrl(item.url)} target="_blank" rel="noopener noreferrer" referrerPolicy="no-referrer">Open {item.title} <ArrowUpRight size={16} aria-hidden="true"/></a><p className="muted">Version {item.version} · Expires {new Date(item.expiresAt).toLocaleDateString('en-GB')}</p></div>)}</section>;
}
