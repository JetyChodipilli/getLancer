import Link from 'next/link';
import Preview from './preview';
import type {Product} from '@/lib/catalog';
import {ArrowUpRight} from 'lucide-react';

export default function ProjectStack({products=[]}:{products:Product[]}) {
 const portrait=products.find(p=>p.illustrative&&p.previewFormat==='portrait');
 const selected=portrait?[products[0],portrait,products.find(p=>p.id!==products[0]?.id&&p.id!==portrait.id)].filter((p):p is Product=>!!p):products.slice(0,3);
 return <div className="featured-board" data-ui="true" aria-label="Featured project demos">{selected.map((product,index)=><article className={`work-feature work-feature-${index}${product.previewFormat==='portrait'?' stack-portrait':''}`} key={product.id}>
  <div className="work-feature-motion"><div className="work-feature-frame">
   <Link className="work-feature-media" href={'/products/'+product.slug} aria-label={'Explore '+product.title}><Preview product={product} naturalAspect/></Link>
   <div className="work-feature-caption"><div><strong>{product.title}</strong><span>{product.category} · by {product.builder}</span></div><ArrowUpRight size={17} aria-hidden="true"/></div>
  </div></div>
 </article>)}</div>;
}
