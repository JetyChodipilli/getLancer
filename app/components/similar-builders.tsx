import Link from 'next/link';
import {backendOrigin} from '@/lib/server';
import {examples,type Product} from '@/lib/catalog';
export default async function SimilarBuilders({product}:{product:Product}){
 const origin=await backendOrigin();let items:Product[]=[];
 if(origin){try{const r=await fetch(origin+'/api/v1/products/'+encodeURIComponent(product.slug)+'/similar-builders',{cache:'no-store',signal:AbortSignal.timeout(10000)});if(!r.ok)return null;items=(await r.json()).items;}catch{return null;}}
 else items=examples.filter(p=>p.builderSlug!==product.builderSlug&&(p.category===product.category||p.technology===product.technology)).slice(0,4);
 return <section className="trust-similar"><h2>Other builders for a similar idea</h2><p className="muted">{origin?'Available builders with related project experience.':'Illustrative recommendations based on shared technology or category.'}</p>{items.length?<div className="grid">{items.map(p=><article className="panel" key={p.id}><h3>{p.builder}</h3><p>{p.title}</p><p className="muted">{p.category} · {p.technology}</p><Link className="button" href={'/products/'+p.slug}>Explore their work →</Link></article>)}</div>:<p>No suitable available builders yet. <Link className="link" href="/">Explore all projects</Link></p>}</section>;
}
