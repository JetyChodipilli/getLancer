import Link from 'next/link';
import {ArrowRight} from 'lucide-react';
import Preview from './preview';
import type {Product} from '@/lib/catalog';

export default function ProjectStack({products=[]}:{products:Product[]}) {
 const first=products[0];
 const portrait=products.find(product=>product.id!==first?.id&&product.previewFormat==='portrait');
 const selected=[first,portrait??products[1],products.find(product=>product.id!==first?.id&&product.id!==(portrait??products[1])?.id)].filter((product):product is Product=>!!product);
 if(!selected.length)return null;
 return <section className="paper-featured-projects" aria-label="Featured project demos">{selected.map((product,index)=><article className={'paper-feature paper-feature-'+index} key={product.id}>
  <Link prefetch={false} className={'paper-feature-media'+(product.previewFormat==='portrait'?' paper-feature-portrait':'')} data-ui="project-preview" href={'/products/'+product.slug} aria-label={'Explore '+product.title}><Preview product={product} naturalAspect/></Link>
  <div className="paper-feature-caption"><div><h2><Link prefetch={false} href={'/products/'+product.slug}>{product.title}</Link></h2><Link prefetch={false} className="paper-feature-maker" href={'/builders/'+product.builderSlug}><span className="paper-maker-avatar" aria-hidden="true">{product.builder.split(' ').map(word=>word[0]).slice(0,2).join('')}</span>by {product.builder}</Link></div><p>{product.summary}</p><Link prefetch={false} className="paper-open-project" href={'/products/'+product.slug}>Open project <ArrowRight size={16} aria-hidden="true"/></Link><span className="paper-feature-stack">{product.technology.split(',').join(' · ')}{product.repositoryUrl?' · Source available':''}</span></div>
 </article>)}</section>;
}
