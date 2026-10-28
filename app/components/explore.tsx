'use client';
import { useEffect, useRef, useState, useTransition } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { Search, Bookmark, ArrowUpRight, X, Grid2X2, Layers } from 'lucide-react';
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
  const gallery = useRef<HTMLDivElement>(null);
  const heroSearch = useRef<HTMLFormElement>(null);
  const draftKey=JSON.stringify([filters.q,filters.category,filters.technology]);
  const [draft,setDraft]=useState({key:draftKey,q:filters.q,category:filters.category,technology:filters.technology});
  if(draft.key!==draftKey)setDraft({key:draftKey,q:filters.q,category:filters.category,technology:filters.technology});
  const {q,category,technology}=draft;
  const setQ=(value:string)=>setDraft(previous=>({...previous,q:value}));
  const setCategory=(value:string)=>setDraft(previous=>({...previous,category:value}));
  const setTechnology=(value:string)=>setDraft(previous=>({...previous,technology:value}));
  const [pending, startTransition] = useTransition();
  const [error, setError] = useState('');
  const [saved, setSaved] = useState<string[]>([]);
  const [saving, setSaving] = useState<string[]>([]);
  const savingRef = useRef(new Set<string>());
  const savedRevision = useRef(0);
  const savedChanges = useRef(new Map<string,{revision:number;saved:boolean}>());
  const visibleIds=items.map(p=>p.id).join(',');
  const [loadedSavedIds,setLoadedSavedIds]=useState('');
  const savedReady=preview||loadedSavedIds===visibleIds;
  useEffect(() => { if (preview||!visibleIds)return;const controller=new AbortController(),revision=savedRevision.current;api('/me/saved-products?ids='+encodeURIComponent(visibleIds),{signal:controller.signal}).then(r=>{
    const next=new Set<string>(r.items.map((p:{id:string})=>p.id));
    // Preserve writes confirmed after this read began, in either response order.
    for(const [id,change] of savedChanges.current)if(change.revision>revision){if(change.saved)next.add(id);else next.delete(id);}
    setSaved([...next]);setLoadedSavedIds(visibleIds);
  }).catch(e=>{if(controller.signal.aborted)return;if(e.status===401){setSaved([]);setLoadedSavedIds(visibleIds)}else setError('Saved projects could not be loaded. Refresh the page to try again.')});return()=>controller.abort(); }, [preview,visibleIds]);
  useEffect(()=>{if(!preview){const searched=!!(filters.q||filters.category||filters.technology||filters.projectType||filters.availability);track(searched?'search_performed':'home_view',undefined,searched?{resultCount:totalItems,queryLength:filters.q.length}:{});items.forEach(p=>track('product_impression',p.id));}},[items,preview,filters.q,filters.category,filters.technology,filters.projectType,filters.availability,totalItems]);
  function update(change: Partial<Filters>) {
    const params = filterParams({ ...filters, page: 0, ...change });
    const target='/?'+params+'#project-collection';
    // Vinext client navigation requires Web Crypto (HTTPS). Keep HTTP previews navigable.
    if(!window.crypto?.subtle){window.location.assign(target);return;}
    startTransition(() => router.push(target, { scroll: true }));
  }
  function reset() { setQ(''); update(emptyFilters); }
  async function save(id: string) {
    if(savingRef.current.has(id))return;
    const wasSaved=saved.includes(id);
    savingRef.current.add(id);setSaving([...savingRef.current]);setError('');
    try {
      await api('/products/' + id + '/save', { method: wasSaved ? 'DELETE' : 'POST' });
      savedChanges.current.set(id,{revision:++savedRevision.current,saved:!wasSaved});
      setSaved(s => wasSaved ? s.filter(x => x !== id) : s.includes(id) ? s : [...s, id]);
    } catch (e) { setError((e as Error).message); } finally { savingRef.current.delete(id);setSaving([...savingRef.current]); }
  }
  return <main id="main" className="wrap marketplace">
    <DiscoveryDock anchor={heroSearch} q={q} setQ={setQ} filters={filters} pending={pending} update={update} reset={reset}/>
    <section className="marketplace-hero" aria-label="Discover software and its builders">
      <div className="hero-copy"><h1>Discover software.<br/>Meet its builder.</h1><p>Explore independent projects, try the work, and connect with the people who made it.</p>
       <div className="hero-actions"><a className="button primary" href="#project-collection">Explore projects <ArrowUpRight size={18} aria-hidden="true"/></a><Link className="hero-workspace-link" href={preview?'/preview/workspace':'/workspace'}>Show your work <ArrowUpRight size={16} aria-hidden="true"/></Link></div>
      </div>
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
    <div className="discovery-heading"><div><h2>Explore independent work</h2><p>Try the software. Get to know its creator.</p></div>{preview&&<p className="market-sample">Projects are illustrative. <Link href="/preview/workspace">Try the workspace →</Link></p>}</div>
    {error && <p role="alert" className="error">{error}</p>}
    <div ref={gallery} className="demo-gallery" aria-label="Project demos" aria-busy={pending}>{items.map((p) => <article className="project" key={p.id}>
      <div className="project-media"><Link href={'/products/'+p.slug} className={'thumbnail tone-'+p.category.toLowerCase()}><Preview product={p} naturalAspect/></Link><button className="iconbutton project-save" disabled={saving.includes(p.id)||!savedReady} aria-label={(saved.includes(p.id)?'Unsave ':'Save ')+p.title} aria-pressed={saved.includes(p.id)} onClick={()=>save(p.id)}><Bookmark size={18} fill={saved.includes(p.id)?'currentColor':'none'}/></button></div>
      <div className="cardtitle"><h2><Link href={'/products/'+p.slug}>{p.title}</Link></h2></div>
      <p className="project-meta">{p.category} · {p.technology.split(',')[0]}</p>
      <div className="byline"><span className="avatar" aria-hidden="true">{p.builder.split(' ').map(x=>x[0]).join('')}</span><Link href={'/builders/'+p.builderSlug}>{p.builder}</Link><Link className="project-view" href={'/products/'+p.slug}>View project <ArrowUpRight size={13}/></Link></div>
    </article>)}</div>
    {!items.length && <div className="empty"><Search size={32} style={{ margin: 'auto' }}/><h2>We couldn’t find an exact match.</h2><p>Try a broader business need or remove a filter.</p><button className="button" onClick={reset}>Clear search and filters <X size={15}/></button></div>}
    {!preview && totalPages > 1 && <nav className="pagination" aria-label="Results pages"><button className="button" disabled={pending || filters.page === 0} onClick={() => update({ page: filters.page - 1 })}>Previous</button><span>Page {filters.page + 1} of {totalPages}</span><button className="button" disabled={pending || filters.page + 1 >= totalPages} onClick={() => update({ page: filters.page + 1 })}>Next</button></nav>}
    <section className="work-next" aria-labelledby="work-next-title"><div><h2 id="work-next-title">Take your next step.</h2><p>A working idea is just the beginning.</p></div><div className="work-next-links"><Link href="/components"><strong>Build with components</strong><span>Find reusable pieces for your next release.</span><ArrowUpRight size={20} aria-hidden="true"/></Link><Link href="/college-projects"><strong>Learn by making</strong><span>Explore practical projects and their source.</span><ArrowUpRight size={20} aria-hidden="true"/></Link><Link href="/teams"><strong>Find your people</strong><span>Meet independent teams and studios.</span><ArrowUpRight size={20} aria-hidden="true"/></Link></div></section>
  </main>;
}
