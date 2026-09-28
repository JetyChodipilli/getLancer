import Link from 'next/link';
import Preview from './preview';
import type {Product} from '@/lib/catalog';

export default function ProjectStack({products=[]}:{products:Product[]}) {
 return <div className="project-stack" aria-label="Featured project demos">{products.slice(0,2).map((product,index)=><article className={`stack-card stack-card-${index}`} key={product.id}>
  <Link className="stack-card-media" href={'/products/'+product.slug} aria-label={'Explore '+product.title}><Preview product={product}/></Link>
 </article>)}</div>;
}
