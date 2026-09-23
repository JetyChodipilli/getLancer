'use client';
import { useEffect, useState, type RefObject } from 'react';
import { createPortal } from 'react-dom';
import { Search, SlidersHorizontal, X } from 'lucide-react';
import { Popover, PopoverTrigger, PopoverContent } from '@/components/ui/popover';
import { Checkbox } from '@/components/ui/checkbox';
import { Picker } from './ui';
import {useTaxonomy} from '@/lib/use-taxonomy';
import type { Filters } from '@/lib/discovery';

type Props={anchor:RefObject<HTMLFormElement|null>;q:string;setQ:(q:string)=>void;filters:Filters;pending:boolean;update:(change:Partial<Filters>)=>void;reset:()=>void};
export default function DiscoveryDock({anchor,q,setQ,filters,pending,update,reset}:Props) {
 const {categories,technologies}=useTaxonomy();
 const [target,setTarget]=useState<HTMLElement|null>(null);
 const [passed,setPassed]=useState(false);
 const [focused,setFocused]=useState(false);
 const [open,setOpen]=useState(false);
 useEffect(()=>{
  setTarget(document.getElementById('discovery-nav-slot'));
  const search=anchor.current;
  const nav=document.querySelector('.site-header .topbar');
  if(!search||!nav)return;
  let observer:IntersectionObserver|undefined;
  function watch(){
   const height=nav!.getBoundingClientRect().height;
   observer?.disconnect();
   setPassed(search!.getBoundingClientRect().bottom<=height+8);
   observer=new IntersectionObserver(([entry])=>setPassed(entry.boundingClientRect.bottom<=height+8),{rootMargin:`-${height+8}px 0px 0px 0px`,threshold:0});
   observer.observe(search!);
  }
  const resize=new ResizeObserver(watch);resize.observe(nav);watch();
  return()=>{resize.disconnect();observer?.disconnect()};
 },[anchor]);
 const count=[filters.category,filters.technology,filters.projectType,filters.availability,filters.liveDemo].filter(Boolean).length;
 const visible=passed||focused||open;
 useEffect(()=>{if(target){target.dataset.visible=String(visible);return()=>{delete target.dataset.visible}}},[target,visible]);
 if(!target)return null;
 return createPortal(<div className="scroll-discovery" data-visible={visible} inert={!visible} aria-hidden={!visible} onFocusCapture={()=>setFocused(true)} onBlurCapture={e=>{if(!e.currentTarget.contains(e.relatedTarget as Node))setFocused(false)}}>
  <form className="dock-search" role="search" aria-label="Browse projects" onSubmit={e=>{e.preventDefault();update({q})}}>
   <Search size={17} aria-hidden="true"/>
   <input aria-label="Search projects while browsing" placeholder="Search projects…" value={q} maxLength={200} onChange={e=>setQ(e.target.value)}/>
   <button type="submit" className="dock-submit" aria-label="Submit project search" disabled={pending}><Search size={17}/></button>
  </form>
  <Popover open={open} onOpenChange={setOpen}><PopoverTrigger asChild><button className="button dock-filter" aria-label={count?`Filter projects (${count} active)`:'Filter projects'}><SlidersHorizontal size={18}/><span>Filters</span>{count>0&&<b>{count}</b>}</button></PopoverTrigger>
   <PopoverContent className="dock-options" align="end" sideOffset={14} aria-label="Project filters">
    <div className="dock-options-heading"><h2>Refine your discovery</h2><button className="iconbutton" aria-label="Close project filters" onClick={()=>setOpen(false)}><X size={18}/></button></div>
    <div className="dock-options-grid">
     <label>Business category<Picker label="Business category" value={filters.category} onChange={category=>update({category})} options={categories}/></label>
     <label>Technology<Picker label="Technology" value={filters.technology} onChange={technology=>update({technology})} options={technologies}/></label>
     <label>Project type<Picker label="Project type" value={filters.projectType} onChange={projectType=>update({projectType})} options={['SAAS','CLIENT','COMMERCIAL','OPEN_SOURCE','PERSONAL','PROTOTYPE','HACKATHON','LEARNING']}/></label>
     <label>Sort results<Picker label="Sort" value={filters.sort} onChange={sort=>update({sort})} options={['relevance','newest','updated']}/></label>
    </div>
    <label className="filterlabel"><Checkbox checked={!!filters.availability} onCheckedChange={v=>update({availability:v===true?'AVAILABLE_NOW':''})}/>Available for work</label>
    <label className="filterlabel"><Checkbox checked={filters.liveDemo} onCheckedChange={v=>update({liveDemo:v===true})}/>Live demo</label>
    <div className="dock-options-footer"><button className="link" onClick={reset}>Clear all</button><button className="button primary" onClick={()=>setOpen(false)}>View results</button></div>
   </PopoverContent>
  </Popover>
 </div>,target);
}
