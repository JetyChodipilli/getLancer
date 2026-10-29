'use client';

import {useEffect, useId, useRef, useState, useTransition} from 'react';
import {useRouter} from 'next/navigation';
import Link from 'next/link';
import {ArrowRight, ArrowUpRight, Bookmark, Check, CheckCircle2, Code2, Search, X} from 'lucide-react';
import BrowseToolbar from './browse-toolbar';
import Preview from './preview';
import ProjectStack from './project-stack';
import {useTaxonomy} from '@/lib/use-taxonomy';
import {api} from '@/lib/api';
import {track} from '@/lib/analytics';
import {emptyFilters, filterParams, type Filters} from '@/lib/discovery';
import type {Catalog} from '@/lib/server';

/** A small, local control preview. It never writes to an account or server. */
function ComponentSpecimen() {
 const id=useId();
 const [example,setExample]=useState('Fields');
 const [saved,setSaved]=useState(false);
 return <div className="paper-specimen" data-ui="component-preview">
  <div className="paper-specimen-top"><span><Code2 size={17} aria-hidden="true"/>Interface essentials</span><span>Interactive preview</span></div>
  <div className="paper-specimen-switch" role="group" aria-label="Component examples">{['Fields','Selection','Feedback'].map(name=><button key={name} type="button" aria-pressed={example===name} onClick={()=>{setExample(name);setSaved(false)}}>{name}</button>)}</div>
  <div className="paper-specimen-body">
   {example==='Fields'&&<><label htmlFor={id+'-name'}>Project name<input id={id+'-name'} defaultValue="Customer portal" maxLength={100}/></label><label htmlFor={id+'-email'}>Contact email<input id={id+'-email'} type="email" placeholder="you@example.com"/></label><p className="paper-specimen-hint">Try these fields. Changes stay in this preview.</p></>}
   {example==='Selection'&&<><div className="paper-specimen-field"><label htmlFor={id+'-kind'}>What are you building?</label><select id={id+'-kind'} defaultValue="Website"><option>Website</option><option>Web application</option><option>Component library</option></select></div><fieldset><legend>Project stage</legend><label><input type="radio" name={id+'-stage'} defaultChecked/> Exploring</label><label><input type="radio" name={id+'-stage'}/> Building</label></fieldset><p className="paper-specimen-hint">Choose a project type and stage.</p></>}
   {example==='Feedback'&&<><div className="paper-feedback-example"><CheckCircle2 size={25} aria-hidden="true"/><div><strong>{saved?'Preview saved.':'Ready to save your changes?'}</strong><p>{saved?'This is a local example. No data was sent.':'Try the button to see a sample confirmation.'}</p></div></div><button type="button" className="button primary" onClick={()=>setSaved(value=>!value)}>{saved?<><Check size={16} aria-hidden="true"/>Reset preview</>:'Try feedback'}</button><p role="status" className="sr-only">{saved?'Preview saved. No data was sent.':''}</p></>}
  </div>
  <div className="paper-specimen-foot"><span>Made to be made your own.</span><Link href="/components">Explore the library <ArrowRight size={15} aria-hidden="true"/></Link></div>
 </div>;
}

export default function Explore({items, preview, totalItems, totalPages, filters, featured}:Catalog & {featured:Catalog['items']}) {
 const {categories,technologies}=useTaxonomy();
 const router=useRouter();
 const draftKey=JSON.stringify([filters.q,filters.category,filters.technology]);
 const [draft,setDraft]=useState({key:draftKey,q:filters.q});
 if(draft.key!==draftKey)setDraft({key:draftKey,q:filters.q});
 const q=draft.q;
 const setQ=(value:string)=>setDraft(previous=>({...previous,q:value}));
 const [pending,startTransition]=useTransition();
 const [error,setError]=useState('');
 const [saveNotice,setSaveNotice]=useState('');
 const [saved,setSaved]=useState<string[]>([]);
 const [saving,setSaving]=useState<string[]>([]);
 const savingRef=useRef(new Set<string>());
 const savedRevision=useRef(0);
 const savedChanges=useRef(new Map<string,{revision:number;saved:boolean}>());
 const visibleIds=items.map(product=>product.id).join(',');
 const [loadedSavedIds,setLoadedSavedIds]=useState('');
 const savedReady=preview||loadedSavedIds===visibleIds;
 const refined=!!(filters.q||filters.category||filters.technology||filters.projectType||filters.availability||filters.liveDemo||filters.page||filters.sort!=='relevance');

 useEffect(()=>{
  if(preview||!visibleIds)return;
  const controller=new AbortController(),revision=savedRevision.current;
  api('/me/saved-products?ids='+encodeURIComponent(visibleIds),{signal:controller.signal}).then(result=>{
   const next=new Set<string>(result.items.map((product:{id:string})=>product.id));
   // A save acknowledged after the read began wins over this potentially older list.
   for(const [id,change] of savedChanges.current)if(change.revision>revision){if(change.saved)next.add(id);else next.delete(id);}
   setSaved([...next]);setLoadedSavedIds(visibleIds);
  }).catch(problem=>{if(controller.signal.aborted)return;if(problem.status===401){setSaved([]);setLoadedSavedIds(visibleIds);}else setError('Saved projects could not be loaded. Refresh the page to try again.');});
  return()=>controller.abort();
 },[preview,visibleIds]);
 useEffect(()=>{
  if(preview)return;
  const searched=!!(filters.q||filters.category||filters.technology||filters.projectType||filters.availability||filters.liveDemo);
  track(searched?'search_performed':'home_view',undefined,searched?{resultCount:totalItems,queryLength:filters.q.length}:{});
  items.forEach(product=>track('product_impression',product.id));
 },[items,preview,filters.q,filters.category,filters.technology,filters.projectType,filters.availability,filters.liveDemo,totalItems]);

 function update(change:Partial<Filters>) {
  const target='/?'+filterParams({...filters,page:0,...change})+'#project-collection';
  if(!window.crypto?.subtle){window.location.assign(target);return;}
  startTransition(()=>router.push(target,{scroll:true}));
 }
 function reset(){setQ('');update(emptyFilters);}
 async function save(id:string,title:string) {
  if(preview){setError('Sign in to save projects to your collection. These projects are illustrative.');return;}
  if(savingRef.current.has(id))return;
  const wasSaved=saved.includes(id);
  savingRef.current.add(id);setSaving([...savingRef.current]);setError('');setSaveNotice((wasSaved?'Removing ':'Saving ')+title+'…');
  try{
   await api('/products/'+encodeURIComponent(id)+'/save',{method:wasSaved?'DELETE':'POST'});
   savedChanges.current.set(id,{revision:++savedRevision.current,saved:!wasSaved});
   setSaved(current=>wasSaved?current.filter(value=>value!==id):current.includes(id)?current:[...current,id]);
   setSaveNotice(wasSaved?title+' removed from saved projects.':title+' saved to your collection.');
  }catch(problem){setSaveNotice('');setError((problem as Error).message);}
  finally{savingRef.current.delete(id);setSaving([...savingRef.current]);}
 }

 return <main id="main" className={'paper-marketplace'+(refined?' paper-marketplace-refined':'')}>
  <section className="paper-exhibition" aria-labelledby="paper-home-title">
   <div className="paper-home-frame">
    <div className="paper-home-intro"><h1 id="paper-home-title">The craft behind the code.</h1><p>Discover working software, reusable components, college projects and learning resources. Meet the people behind the work.</p></div>
    <div className="paper-discovery-row">
     <form id="paper-search" className="paper-home-search" role="search" tabIndex={-1} onSubmit={event=>{event.preventDefault();update({q});}}>
      <label className="sr-only" htmlFor="paper-project-search">Search projects</label><Search size={21} aria-hidden="true"/>
      <input id="paper-project-search" name="q" aria-label="Search projects" maxLength={200} value={q} onChange={event=>setQ(event.target.value)} placeholder="Search projects, builders or technology…"/>
      {q&&<button type="button" className="paper-search-clear" aria-label="Clear search" onClick={()=>{setQ('');if(filters.q)update({q:''});}}><X size={18} aria-hidden="true"/></button>}
      <button type="submit" className="paper-search-submit" disabled={pending}>{pending?'Searching…':'Search'}<ArrowRight size={17} aria-hidden="true"/></button>
     </form>
     <nav className="paper-discovery-paths" aria-label="Explore getLancer"><a href="#project-collection">Projects <ArrowRight size={16} aria-hidden="true"/></a><Link href="/components">Components <ArrowRight size={16} aria-hidden="true"/></Link><Link href="/college-projects">College projects <ArrowRight size={16} aria-hidden="true"/></Link><Link href="/labs">Learning <ArrowRight size={16} aria-hidden="true"/></Link></nav>
    </div>
    {!refined&&<><ProjectStack products={featured}/>{preview&&<p className="paper-sample-note">Illustrative projects and creators. <Link href="/preview/workspace">Explore the workspace preview <ArrowUpRight size={14} aria-hidden="true"/></Link></p>}</>}
   </div>
  </section>

  {!refined&&<div className="paper-chapters paper-home-frame">
   <section className="paper-component-chapter" aria-labelledby="paper-components-title"><div className="paper-chapter-copy"><span className="paper-chapter-label">Components</span><h2 id="paper-components-title">Small pieces.<br/>Better products.</h2><p>Thoughtful frontend components for whatever comes next. Try the details before you make them yours.</p><Link className="button primary" href="/components">View components <ArrowRight size={17} aria-hidden="true"/></Link></div><ComponentSpecimen/></section>
   <div className="paper-learning-chapters">
    <section className="paper-learning-chapter" aria-labelledby="paper-college-title"><div><span className="paper-chapter-label">College projects</span><h2 id="paper-college-title">Ideas in practice.</h2><p>Explore student work across full stack, analytics, AI and IoT. See the problem, the approach and the contribution.</p><Link href="/college-projects">Browse college projects <ArrowRight size={16} aria-hidden="true"/></Link></div><div className="paper-college-specimen" data-ui="college-preview"><span>Explore by discipline</span><Link href="/college-projects?category=FULL_STACK">Full stack <ArrowUpRight size={17} aria-hidden="true"/></Link><Link href="/college-projects?category=DATA_ANALYTICS">Data analytics <ArrowUpRight size={17} aria-hidden="true"/></Link><Link href="/college-projects?category=AI_ML">AI & machine learning <ArrowUpRight size={17} aria-hidden="true"/></Link><Link href="/college-projects?category=IOT">IoT <ArrowUpRight size={17} aria-hidden="true"/></Link></div></section>
    <section className="paper-learning-chapter" aria-labelledby="paper-learning-title"><div><span className="paper-chapter-label">Learning</span><h2 id="paper-learning-title">Build and grow.</h2><p>Learn by exploring the work. Open practical backend labs, source packages and framework previews.</p><Link href="/labs">Explore learning <ArrowRight size={16} aria-hidden="true"/></Link></div><div className="paper-lab-specimen" data-ui="learning-preview"><div><span>Hands-on learning</span><Code2 size={20} aria-hidden="true"/></div><strong>From a question<br/>to working code.</strong><Link href="/labs">Backend labs <ArrowRight size={16} aria-hidden="true"/></Link><Link href="/components/frameworks">Framework previews <ArrowRight size={16} aria-hidden="true"/></Link></div></section>
   </div>
  </div>}

  <section className="paper-collection paper-home-frame" id="project-collection" aria-labelledby="paper-collection-title" tabIndex={-1}>
   <div className="paper-collection-heading"><div><h2 id="paper-collection-title">Explore the collection</h2><p>{refined?'Find the work that fits your next idea.':'A closer look at what independent builders are making.'}</p></div><Link className="paper-publish-link" href="/workspace">Share your work <ArrowUpRight size={17} aria-hidden="true"/></Link></div>
   <BrowseToolbar filters={filters} query={q} onQuery={setQ} onChange={update} onReset={reset} count={totalItems} categories={categories} technologies={technologies} pending={pending} illustrative={preview}/>
   {error&&<p role="alert" className="error">{error} {preview&&<Link href="/login">Sign in <ArrowRight size={15} aria-hidden="true"/></Link>}</p>}
   <p role="status" className="paper-save-notice">{saveNotice}</p>
   <div className="paper-project-grid" aria-label="Project demos" aria-busy={pending}>{items.map(product=><article className="paper-project" key={product.id}>
    <div className="paper-project-media"><Link href={'/products/'+product.slug} className="paper-project-preview" data-ui="project-preview" aria-label={'Explore '+product.title}><Preview product={product}/></Link><button className="paper-project-save" type="button" disabled={saving.includes(product.id)||!savedReady} aria-label={(saved.includes(product.id)?'Unsave ':'Save ')+product.title} aria-busy={saving.includes(product.id)} aria-pressed={saved.includes(product.id)} onClick={()=>save(product.id,product.title)}><Bookmark size={19} fill={saved.includes(product.id)?'currentColor':'none'} aria-hidden="true"/></button></div>
    <div className="paper-project-title"><h3><Link href={'/products/'+product.slug}>{product.title}</Link></h3><span>{product.technology.split(',')[0]}</span></div><p>{product.summary}</p>
    <div className="paper-project-byline"><Link href={'/builders/'+product.builderSlug}><span className="paper-maker-avatar" aria-hidden="true">{product.builder.split(' ').map(value=>value[0]).slice(0,2).join('')}</span>{product.builder}</Link><Link href={'/products/'+product.slug} aria-label={'Open '+product.title}><ArrowUpRight size={20} aria-hidden="true"/></Link></div>
   </article>)}</div>
   {!items.length&&<div className="paper-no-results"><Search size={30} aria-hidden="true"/><h3>No projects found.</h3><p>Try a broader search or remove a filter.</p><button type="button" className="button primary" onClick={reset}>Clear search and filters <X size={16} aria-hidden="true"/></button></div>}
   {!preview&&totalPages>1&&<nav className="pagination" aria-label="Results pages"><button className="button" disabled={pending||filters.page===0} onClick={()=>update({page:filters.page-1})}>Previous</button><span>Page {filters.page+1} of {totalPages}</span><button className="button" disabled={pending||filters.page+1>=totalPages} onClick={()=>update({page:filters.page+1})}>Next</button></nav>}
  </section>
 </main>;
}
