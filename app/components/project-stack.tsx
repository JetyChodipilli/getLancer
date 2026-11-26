import Link from 'next/link';
import {ArrowUpRight} from 'lucide-react';
import Preview from './preview';
import type {Product} from '@/lib/catalog';

export default function ProjectStack({products}:{products:Product[]}) {
 return <section aria-label="Featured project demos">
  <h2 className="paper-featured-label">Featured projects</h2>
  <div className="paper-featured-projects">{products.slice(0,3).map(product=><article className="paper-feature" key={product.id}>
   <Link prefetch={false} className="paper-feature-media" data-ui="project-preview" href={'/products/'+product.slug} aria-label={'Explore '+product.title}><Preview product={product} hoverVideo/></Link>
   <div className="paper-feature-caption"><div><h3><Link prefetch={false} href={'/products/'+product.slug}>{product.title}</Link></h3><Link prefetch={false} className="paper-feature-maker" href={'/builders/'+product.builderSlug}>by {product.builder}</Link><span className="paper-feature-stack">{product.category} · {product.technology.split(',').join(' · ')}</span></div><Link prefetch={false} className="paper-open-project" href={'/products/'+product.slug} aria-label="Open project"><ArrowUpRight size={18} aria-hidden="true"/></Link></div>
  </article>)}</div>
 </section>;
}
