import Link from 'next/link';
import Preview from './preview';
import type {Product} from '@/lib/catalog';

export default function ProjectStack({products=[]}:{products:Product[]}) {
 const portrait=products.find(p=>p.illustrative&&p.previewFormat==='portrait');
 const selected=portrait?[products[0],portrait,products.find(p=>p.id!==products[0]?.id&&p.id!==portrait.id)].filter((p):p is Product=>!!p):products.slice(0,3);
 return <div className="project-stack" aria-label="Featured project demos">{selected.map((product,index)=><article className={`stack-card stack-card-${index}${product.previewFormat==='portrait'?' stack-portrait':''}`} key={product.id}>
  <Link className="stack-card-media" href={'/products/'+product.slug} aria-label={'Explore '+product.title}><Preview product={product} naturalAspect/></Link>
  <div className="spectral-stack-caption"><strong>{product.title}</strong><span>{product.category} · {product.builder}</span></div>
 </article>)}</div>;
}
