import Link from 'next/link';
import {ArrowRight, ArrowUpRight, Search} from 'lucide-react';
import Preview from './preview';
import {availabilityLabel} from '@/lib/catalog';
import {taxonomyPage} from '@/lib/taxonomy-page';
import {notFound} from 'next/navigation';

export default async function TaxonomyGallery({kind,slug,page}:{kind:'categories'|'technologies';slug:string;page:number}) {
 const data=await taxonomyPage(kind,slug,page);
 if(!data)notFound();
 const {item,catalog}=data;
 const base=(kind==='categories'?'/solutions/':'/technologies/')+slug;
 return <main className="paper-taxonomy paper-marketplace" id="main">
  <header className="paper-taxonomy-head"><div className="paper-home-frame"><Link href="/" className="paper-taxonomy-back">← Explore all projects</Link><h1>{item.name}</h1><p>{kind==='categories'?'Software for your business, with the work to show for it.':'Discover what independent builders are making with '+item.name+'.'}</p><span>{catalog.totalItems} project{catalog.totalItems===1?'':'s'}{catalog.preview?' · illustrative sample data':''}</span></div></header>
  <section className="paper-home-frame paper-taxonomy-collection" aria-label={item.name+' projects'}>
   <div className="paper-project-grid">{catalog.items.map(product=><article className="paper-project" key={product.id}>
    <Link className="paper-project-preview" data-ui="project-preview" href={'/products/'+product.slug} aria-label={'Explore '+product.title}><Preview product={product}/></Link>
    <div className="paper-project-title"><h2><Link href={'/products/'+product.slug}>{product.title}</Link></h2><span>{product.technology.split(',')[0]}</span></div><p>{product.summary}</p>
    <div className="paper-project-byline"><Link href={'/builders/'+product.builderSlug}><span className="paper-maker-avatar" aria-hidden="true">{product.builder.split(' ').map(value=>value[0]).slice(0,2).join('')}</span>{product.builder}</Link><Link href={'/products/'+product.slug} aria-label={'Open '+product.title}><ArrowUpRight size={20} aria-hidden="true"/></Link></div><p className="paper-taxonomy-availability">{availabilityLabel(product.availability,product.bookedUntil)}</p>
   </article>)}</div>
   {!catalog.items.length&&<div className="paper-no-results"><Search size={30} aria-hidden="true"/><h2>No projects here yet.</h2><p>Explore the full collection for another starting point.</p><Link href="/" className="button primary">Explore projects <ArrowRight size={16} aria-hidden="true"/></Link></div>}
   {catalog.totalPages>1&&<nav aria-label="Project pages" className="pagination">{page>0&&<Link className="button" href={base+(page>1?'?page='+(page-1):'')}>Previous</Link>}<span>Page {page+1} of {catalog.totalPages}</span>{page+1<catalog.totalPages&&<Link className="button" href={base+'?page='+(page+1)}>Next <ArrowRight size={16} aria-hidden="true"/></Link>}</nav>}
  </section>
 </main>;
}
