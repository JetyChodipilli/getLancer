'use client';
import { useEffect, useRef, useState, useTransition } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { Search, Bookmark, ArrowUpRight, X } from 'lucide-react';
import BrowseToolbar from './browse-toolbar';
import Preview from './preview';
import ProjectStack from './project-stack';
import DiscoveryDock from './discovery-dock';
import { availabilityLabel } from '@/lib/catalog';
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
  return <main id="main" className="wrap">
    <DiscoveryDock anchor={heroSearch} q={q} setQ={setQ} filters={filters} pending={pending} update={update} reset={reset}/>
    <section className="discovery-hero quiet-hero">
      <div className="hero-copy"><span className="eyebrow">INDEPENDENT BUILDERS. EXCEPTIONAL WORK.</span><h1>Find your next<br/><span>great build.</span></h1><p>Explore working software. Discover the thinking behind it. Connect with the builder who can bring your idea to life.</p><form ref={heroSearch} className="search" onSubmit={e => { e.preventDefault(); update({ q }); }}><Search size={21} aria-hidden="true"/><input aria-label="Search projects" maxLength={200} value={q} onChange={e => setQ(e.target.value)} placeholder="What do you want to build? Try inventory, booking, or CRM…"/><button className="button primary" disabled={pending}>Search</button></form><div className="hero-suggestions"><span>Explore a possibility</span>{['Inventory','Booking','CRM'].map(c=><button key={c} onClick={()=>update({category:c})}>{c}<ArrowUpRight size={13}/></button>)}</div><a className="hero-explore" href="#project-collection">Explore all projects <span aria-hidden="true">↓</span></a><p className="hero-scroll-note">Real ideas, brought to life by independent builders.</p></div>
      <ProjectStack products={featured}/>
    </section>
    <div className="discovery-heading" id="project-collection" tabIndex={-1}><div><span className="eyebrow">BUILT TO BE DISCOVERED</span><h2>Good work speaks for itself.</h2></div><p>Find the right builder through what they’ve built.</p></div>
    <nav className="categoryrow" aria-label="Business categories"><button className={!filters.category ? 'selected' : ''} aria-pressed={!filters.category} onClick={() => update({ category: '' })}>All projects</button>{categories.slice(0, 7).map(c => <button key={c} className={filters.category === c ? 'selected' : ''} aria-pressed={filters.category === c} onClick={() => update({ category: c })}>{c}</button>)}</nav>
    <BrowseToolbar filters={filters} query={q} onQuery={setQ} onChange={update} onReset={reset} count={totalItems} categories={categories} technologies={technologies} pending={pending} illustrative={preview}/>
    {preview && <div className="samplebar">Design preview · Projects are illustrative. <Link href="/preview/workspace">Try the interactive workspace →</Link></div>}
    {error && <p role="alert" className="error">{error}</p>}
    <div className="grid" aria-busy={pending}>{items.map((p, i) => <article className="project" key={p.id}>
      <Link href={'/products/' + p.slug} className={'thumbnail tone-' + i % 6}><Preview product={p}/></Link>
      <div className="cardtitle"><h2><Link href={'/products/' + p.slug}>{p.title}</Link></h2><button className="iconbutton" disabled={saving === p.id||!savedReady} aria-label={(saved.includes(p.id) ? 'Unsave ' : 'Save ') + p.title} aria-pressed={saved.includes(p.id)} onClick={() => save(p.id)}><Bookmark size={18} fill={saved.includes(p.id) ? '#2563eb' : 'none'}/></button></div>
      <p className="summary">{p.summary}</p><div className="byline"><span className="avatar">{p.builder.split(' ').map(x => x[0]).join('')}</span><Link href={'/builders/' + p.builderSlug}>{p.builder}</Link><span className="availability">{availabilityLabel(p.availability, p.bookedUntil)}</span></div><div className="tags" style={{ marginTop: 12 }}><span className="tag">{p.category}</span>{p.technology.split(',').filter(Boolean).slice(0,1).map(t => <span className="tag" key={t}>{t}</span>)}</div>
    </article>)}</div>
    {!items.length && <div className="empty"><Search size={32} style={{ margin: 'auto' }}/><h2>We couldn’t find an exact match.</h2><p>Try a broader business need or remove a filter.</p><button className="button" onClick={reset}>Clear search and filters <X size={15}/></button></div>}
    {!preview && totalPages > 1 && <nav className="pagination" aria-label="Results pages"><button className="button" disabled={pending || filters.page === 0} onClick={() => update({ page: filters.page - 1 })}>Previous</button><span>Page {filters.page + 1} of {totalPages}</span><button className="button" disabled={pending || filters.page + 1 >= totalPages} onClick={() => update({ page: filters.page + 1 })}>Next</button></nav>}
  </main>;
}
