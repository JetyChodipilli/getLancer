'use client';
import { useEffect, useRef, useState, useTransition, useMemo } from 'react';
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
import { availabilityLabel } from '@/lib/catalog';

export default function Explore({ items, preview, totalItems, totalPages, filters, featured }: Catalog & {featured:Catalog['items']}) {
 const {categories,technologies}=useTaxonomy();
  const router = useRouter();
  // Curate the unfiltered sample collection; real search/sort order stays intact.
  const galleryItems=useMemo(()=>{
    if(!preview||filters.q||filters.category||filters.technology||filters.sort!=='relevance')return items;
    const portrait=items.find(p=>p.previewFormat==='portrait');
    if(!portrait)return items;
    const rest=items.filter(p=>p.id!==portrait.id);
    return [...rest.slice(0,1),portrait,...rest.slice(1)];
  },[items,preview,filters.q,filters.category,filters.technology,filters.sort]);
  useEffect(()=>{
    const root=gallery.current;
    if(!root)return;
    const cards=Array.from(root.querySelectorAll<HTMLElement>(':scope > .project'));
    let frame=0,lastWidth=-1;
    const layout=()=>{
      const columns=Number(getComputedStyle(root).getPropertyValue('--demo-columns'))||4;
      const gap=24,width=(root.clientWidth-gap*(columns-1))/columns;
      const heights=columns===1?[0]:columns===2?[0,48]:[0,64,24,96];
      root.dataset.layout='masonry';
      cards.forEach(card=>{card.style.width=`${width}px`;});
      cards.forEach(card=>{
        const column=heights.indexOf(Math.min(...heights));
        card.style.left=`${column*(width+gap)}px`;
        card.style.top=`${heights[column]}px`;
        heights[column]+=card.getBoundingClientRect().height+28;
      });
      root.style.height=`${cards.length?Math.max(...heights)-28:0}px`;
    };
    const schedule=()=>{cancelAnimationFrame(frame);frame=requestAnimationFrame(layout);};
    const observer=new ResizeObserver(entries=>{
      if(entries.some(e=>e.target!==root||e.contentRect.width!==lastWidth)){lastWidth=root.clientWidth;schedule();}
    });
    observer.observe(root);cards.forEach(card=>observer.observe(card));layout();
    return()=>{observer.disconnect();cancelAnimationFrame(frame);};
  },[galleryItems]);

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
  const [saving, setSaving] = useState('');
  const visibleIds=items.map(p=>p.id).join(',');
  const [loadedSavedIds,setLoadedSavedIds]=useState('');
  const savedReady=preview||loadedSavedIds===visibleIds;
  useEffect(() => { if (preview||!visibleIds)return;const controller=new AbortController();api('/me/saved-products?ids='+encodeURIComponent(visibleIds),{signal:controller.signal}).then(r=>{setSaved(r.items.map((p:{id:string})=>p.id));setLoadedSavedIds(visibleIds)}).catch(e=>{if(controller.signal.aborted)return;if(e.status===401){setSaved([]);setLoadedSavedIds(visibleIds)}else setError('Saved projects could not be loaded. Refresh the page to try again.')});return()=>controller.abort(); }, [preview,visibleIds]);
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
    setSaving(id); setError('');
    try {
      await api('/products/' + id + '/save', { method: saved.includes(id) ? 'DELETE' : 'POST' });
      setSaved(s => s.includes(id) ? s.filter(x => x !== id) : [...s, id]);
    } catch (e) { setError((e as Error).message); } finally { setSaving(''); }
  }
  return <main id="main" className="wrap marketplace">
    <DiscoveryDock anchor={heroSearch} q={q} setQ={setQ} filters={filters} pending={pending} update={update} reset={reset}/>
    <section className="marketplace-hero">
      <div className="hero-copy"><span className="eyebrow">INDEPENDENT TALENT. EXTRAORDINARY WORK.</span><h1>Great work deserves<br/>to be <span>seen.</span></h1><p>Show what you make. Find the people to build with.</p><a className="hero-explore" href="#project-collection">Explore projects <ArrowRight size={18}/></a></div>
      <div className="spectral-scene">
        <img className="spectral-ribbon" src="/spectral/glass-ribbon.webp" alt="" aria-hidden="true" width={1536} height={1024} fetchPriority="high"/>
        <ProjectStack products={featured}/>
        {featured[0]&&<div className="spectral-builder"><span className="avatar" aria-hidden="true">{featured[0].builder.split(' ').map(x=>x[0]).join('')}</span><div><strong>{featured[0].builder}</strong><span>{availabilityLabel(featured[0].availability,featured[0].bookedUntil)}</span></div><Link href={'/builders/'+featured[0].builderSlug}>View profile <ArrowUpRight size={16} aria-hidden="true"/></Link></div>}
        <div className="spectral-inquiry"><span className="spectral-card-kicker">LET’S MAKE SOMETHING GREAT</span><strong>A project in mind?</strong><p>Find the right people for your next idea.</p><Link className="button primary" href={preview?'/preview/business':'/workspace/business'}>Start a project request</Link></div>
      </div>
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
    <div ref={gallery} className="demo-gallery" aria-label="Project demos" aria-busy={pending}>{galleryItems.map((p) => <article className="project" key={p.id}>
      <div className="project-media"><Link href={'/products/'+p.slug} className={'thumbnail tone-'+p.category.toLowerCase()}><Preview product={p} naturalAspect/></Link><button className="iconbutton project-save" disabled={saving===p.id||!savedReady} aria-label={(saved.includes(p.id)?'Unsave ':'Save ')+p.title} aria-pressed={saved.includes(p.id)} onClick={()=>save(p.id)}><Bookmark size={18} fill={saved.includes(p.id)?'currentColor':'none'}/></button></div>
      <div className="cardtitle"><h2><Link href={'/products/'+p.slug}>{p.title}</Link></h2></div>
      <p className="project-meta">{p.category} · {p.technology.split(',')[0]}</p>
      <div className="byline"><span className="avatar" aria-hidden="true">{p.builder.split(' ').map(x=>x[0]).join('')}</span><Link href={'/builders/'+p.builderSlug}>{p.builder}</Link><Link className="project-view" href={'/products/'+p.slug}>View project <ArrowUpRight size={13}/></Link></div>
    </article>)}</div>
    {!items.length && <div className="empty"><Search size={32} style={{ margin: 'auto' }}/><h2>We couldn’t find an exact match.</h2><p>Try a broader business need or remove a filter.</p><button className="button" onClick={reset}>Clear search and filters <X size={15}/></button></div>}
    {!preview && totalPages > 1 && <nav className="pagination" aria-label="Results pages"><button className="button" disabled={pending || filters.page === 0} onClick={() => update({ page: filters.page - 1 })}>Previous</button><span>Page {filters.page + 1} of {totalPages}</span><button className="button" disabled={pending || filters.page + 1 >= totalPages} onClick={() => update({ page: filters.page + 1 })}>Next</button></nav>}
    <section className="market-steps" aria-label="How getLancer works">{[[Search,'Explore real work','Browse working projects built by independent developers.'],[Users,'Meet the builder','Learn about the developer behind the project.'],[MessageCircle,'Start a conversation','Reach out and hire to bring your ideas to life.']].map(([Icon,title,copy])=>{const I=Icon as typeof Search;return <div key={String(title)}><span className="step-icon"><I size={26}/></span><div><h3>{String(title)}</h3><p>{String(copy)}</p></div></div>})}</section>
  </main>;
}
