'use client';
import { useEffect, useRef, useState, useTransition } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { Search, Bookmark, ArrowUpRight, ArrowRight, X, Grid2X2, Layers, Users, MessageCircle } from 'lucide-react';
import BrowseToolbar from './browse-toolbar';
import Preview from './preview';
import ProjectStack from './project-stack';
import DiscoveryDock from './discovery-dock';

import {useTaxonomy} from '@/lib/use-taxonomy';
import { api } from '@/lib/api';
import { track } from '@/lib/analytics';
import { emptyFilters, filterParams, type Filters } from '@/lib/discovery';
import type { Catalog } from '@/lib/server';

export default function Explore({ items, preview, totalItems, totalPages, filters, featured }: Catalog & {featured:Catalog['items']}) {
 const {categories,technologies}=useTaxonomy();
  const router = useRouter();
  const heroSearch = useRef<HTMLFormElement>(null);
  const [q, setQ] = useState(filters.q);
  const [category,setCategory]=useState(filters.category),[technology,setTechnology]=useState(filters.technology);
  useEffect(()=>{setCategory(filters.category);setTechnology(filters.technology)},[filters.category,filters.technology]);
  const [pending, startTransition] = useTransition();
  const [error, setError] = useState('');
  const [saved, setSaved] = useState<string[]>([]);
  const [saving, setSaving] = useState('');
  const [savedReady,setSavedReady]=useState(preview);
  const visibleIds=items.map(p=>p.id).join(',');
  useEffect(() => { setQ(filters.q); }, [filters.q]);
  useEffect(() => { if (preview||!visibleIds)return;const controller=new AbortController();setSavedReady(false);api('/me/saved-products?ids='+encodeURIComponent(visibleIds),{signal:controller.signal}).then(r=>{setSaved(r.items.map((p:{id:string})=>p.id));setSavedReady(true)}).catch(e=>{if(controller.signal.aborted)return;if(e.status===401){setSaved([]);setSavedReady(true)}else setError('Saved projects could not be loaded. Refresh the page to try again.')});return()=>controller.abort(); }, [preview,visibleIds]);
  useEffect(()=>{if(!preview){track(filters.q?'search_performed':'home_view');items.forEach(p=>track('product_impression',p.id));}},[items,preview,filters.q]);
  function update(change: Partial<Filters>) {
    const params = filterParams({ ...filters, page: 0, ...change });
    const target='/?'+params+'#project-collection';
    // Vinext client navigation requires Web Crypto (HTTPS). Keep HTTP previews navigable.
    if(!window.crypto?.subtle){window.location.assign(target);return;}
    startTransition(() => router.push(target, { scroll: true }));
  }
  function reset() { setQ(''); update(emptyFilters); }
  async function save(id: string) {
    setSaving(id); setError('');
    try {
      await api('/products/' + id + '/save', { method: saved.includes(id) ? 'DELETE' : 'POST' });
      setSaved(s => s.includes(id) ? s.filter(x => x !== id) : [...s, id]);
    } catch (e) { setError((e as Error).message); } finally { setSaving(''); }
  }
  return <main id="main" className="wrap marketplace">
    <DiscoveryDock anchor={heroSearch} q={q} setQ={setQ} filters={filters} pending={pending} update={update} reset={reset}/>
    <section className="marketplace-hero">
      <div className="hero-copy"><span className="eyebrow">REAL SOFTWARE. INDEPENDENT BUILDERS.</span><h1>Find the build.<br/>Meet the <span>builder.</span></h1><p>Explore working software and connect<br className="desktop-break"/> with the people who built it.</p><a className="hero-explore" href="#project-collection">Explore projects <ArrowRight size={20}/></a></div>
      <ProjectStack products={featured}/>
    </section>
    <form ref={heroSearch} className="market-search" role="search" onSubmit={e=>{e.preventDefault();update({q,category,technology})}}>
      <label className="market-query"><Search size={23} aria-hidden="true"/><span><strong>What do you need?</strong><input aria-label="Search projects" maxLength={200} value={q} onChange={e=>setQ(e.target.value)} placeholder="Search projects or builders"/></span></label>
      <label className="market-segment"><Grid2X2 size={23} aria-hidden="true"/><span><strong>Category</strong><select aria-label="Search category" value={category} onChange={e=>setCategory(e.target.value)}><option value="">All categories</option>{categories.map(c=><option key={c}>{c}</option>)}</select></span></label>
      <label className="market-segment"><Layers size={23} aria-hidden="true"/><span><strong>Technology</strong><select aria-label="Search technology" value={technology} onChange={e=>setTechnology(e.target.value)}><option value="">Any stack</option>{technologies.map(t=><option key={t}>{t}</option>)}</select></span></label>
      <button className="button primary" disabled={pending}><Search size={22}/>{pending?'Searching…':'Search'}</button>
    </form>
    <div className="market-controls" id="project-collection" tabIndex={-1}>
      <nav className="categoryrow" aria-label="Business categories"><button className={!filters.category?'selected':''} aria-pressed={!filters.category} onClick={()=>update({category:''})}>All projects</button>{categories.slice(0,5).map(c=><button key={c} className={filters.category===c?'selected':''} aria-pressed={filters.category===c} onClick={()=>update({category:c})}>{c}</button>)}</nav>
      <BrowseToolbar compact filters={filters} query={q} onQuery={setQ} onChange={update} onReset={reset} count={totalItems} categories={categories} technologies={technologies} pending={pending} illustrative={preview}/>
    </div>
    <div className="discovery-heading"><div><h2>Explore independent work</h2><p>Real projects, ready to explore.</p></div>{preview&&<p className="market-sample">Projects are illustrative. <Link href="/preview/workspace">Try the workspace →</Link></p>}</div>
    {error && <p role="alert" className="error">{error}</p>}
    <div className="grid" aria-busy={pending}>{items.map((p, i) => <article className="project" key={p.id}>
      <div className="project-media"><Link href={'/products/'+p.slug} className={'thumbnail tone-'+p.category.toLowerCase()}><Preview product={p}/></Link><button className="iconbutton project-save" disabled={saving===p.id||!savedReady} aria-label={(saved.includes(p.id)?'Unsave ':'Save ')+p.title} aria-pressed={saved.includes(p.id)} onClick={()=>save(p.id)}><Bookmark size={18} fill={saved.includes(p.id)?'currentColor':'none'}/></button></div>
      <div className="cardtitle"><h2><Link href={'/products/'+p.slug}>{p.title}</Link></h2></div>
      <p className="project-meta">{p.category} · {p.technology.split(',')[0]}</p>
      <div className="byline"><span className="avatar" aria-hidden="true">{p.builder.split(' ').map(x=>x[0]).join('')}</span><Link href={'/builders/'+p.builderSlug}>{p.builder}</Link><Link className="project-view" href={'/products/'+p.slug}>View project <ArrowUpRight size={13}/></Link></div>
    </article>)}</div>
    {!items.length && <div className="empty"><Search size={32} style={{ margin: 'auto' }}/><h2>We couldn’t find an exact match.</h2><p>Try a broader business need or remove a filter.</p><button className="button" onClick={reset}>Clear search and filters <X size={15}/></button></div>}
    {!preview && totalPages > 1 && <nav className="pagination" aria-label="Results pages"><button className="button" disabled={pending || filters.page === 0} onClick={() => update({ page: filters.page - 1 })}>Previous</button><span>Page {filters.page + 1} of {totalPages}</span><button className="button" disabled={pending || filters.page + 1 >= totalPages} onClick={() => update({ page: filters.page + 1 })}>Next</button></nav>}
    <section className="market-steps" aria-label="How getLancer works">{[[Search,'Explore real work','Browse working projects built by independent developers.'],[Users,'Meet the builder','Learn about the developer behind the project.'],[MessageCircle,'Start a conversation','Reach out and hire to bring your ideas to life.']].map(([Icon,title,copy])=>{const I=Icon as typeof Search;return <div key={String(title)}><span className="step-icon"><I size={26}/></span><div><h3>{String(title)}</h3><p>{String(copy)}</p></div></div>})}</section>
  </main>;
}
