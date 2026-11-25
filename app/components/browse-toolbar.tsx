'use client';
import {useEffect,useId,useLayoutEffect,useRef,useState} from 'react';
import {createPortal} from 'react-dom';
import {Search,SlidersHorizontal,X,ArrowUpDown} from 'lucide-react';
import {Dialog,DialogTrigger,DialogContent,DialogHeader,DialogTitle,DialogDescription} from '@/components/ui/dialog';
import {label} from '@/lib/catalog';
import {emptyFilters,type Filters} from '@/lib/discovery';

type Props={filters:Filters;query:string;onQuery:(value:string)=>void;onChange:(value:Partial<Filters>)=>void;onReset:()=>void;count:number;categories:string[];technologies:string[];pending?:boolean;workspace?:boolean;compact?:boolean;saved?:boolean;illustrative?:boolean;liveDemo?:boolean;sortOptions?:{value:string;label:string}[]};
export default function BrowseToolbar({filters,query,onQuery,onChange,onReset,count,categories,technologies,pending=false,workspace=false,compact=false,saved=false,illustrative=false,liveDemo=true,sortOptions=[{value:'relevance',label:'Most relevant'},{value:'newest',label:'Newest first'},{value:'updated',label:'Recently updated'}]}:Props){
 const [open,setOpen]=useState(false),[draft,setDraft]=useState(filters);
 const filterId=useId();
 const anchor=useRef<HTMLDivElement>(null);
 const controls=useRef<HTMLDivElement>(null);
 const focusControl=useRef<string|undefined>(undefined);
 const [dock,setDock]=useState<{target:HTMLElement;height:number}|null>(null);
 useEffect(()=>{
  const inline=anchor.current;
  const target=document.getElementById('workspace-project-dock');
  const header=target?.parentElement?.querySelector('#site-navigation-bar');
  if(!workspace||open||!inline||!target||!header)return;
  let frame=0;
  const measure=()=>{
   frame=0;
   const box=inline.getBoundingClientRect();
   const passed=box.bottom<=header.getBoundingClientRect().bottom;
   if(passed===!!dock)return;
   const active=document.activeElement;
   focusControl.current=active instanceof HTMLElement&&controls.current?.contains(active)?active.dataset.dockControl:undefined;
   setDock(passed?{target,height:box.height}:null);
  };
  const schedule=()=>{if(!frame)frame=requestAnimationFrame(measure);};
  const resize=new ResizeObserver(schedule);
  resize.observe(inline);
  resize.observe(header);
  window.addEventListener('scroll',schedule,{passive:true});
  schedule();
  return()=>{resize.disconnect();window.removeEventListener('scroll',schedule);cancelAnimationFrame(frame);};
 },[workspace,open,dock]);
 useLayoutEffect(()=>{
  if(focusControl.current)controls.current?.querySelector<HTMLElement>(`[data-dock-control="${focusControl.current}"]`)?.focus({preventScroll:true});
  focusControl.current=undefined;
 },[dock]);
 const selected:[keyof Filters,string][]=[];
 if(filters.q)selected.push(['q',`Search: ${filters.q}`]);
 if(filters.category)selected.push(['category',filters.category]);
 if(filters.technology)selected.push(['technology',filters.technology]);
 if(filters.projectType)selected.push(['projectType',filters.projectType==='SAAS'?'SaaS':label(filters.projectType)]);
 if(filters.availability)selected.push(['availability','Available for work']);
 if(filters.liveDemo)selected.push(['liveDemo','Live demo']);
 const filterCount=selected.filter(([key])=>key!=='q').length;
 const actions=<div className="browse-controls" ref={controls}>
   {!compact&&<form className="browse-search" role="search" onSubmit={event=>{event.preventDefault();if(!pending)onChange({q:query});}}><Search size={19} aria-hidden="true"/><input data-dock-control="query" aria-label={illustrative?'Search demo projects':'Search the collection'} placeholder={saved?'Search saved projects…':dock?'Search projects…':'Search projects, builders or technology…'} maxLength={200} value={query} onChange={event=>onQuery(event.target.value)}/>{query&&<button type="button" className="iconbutton" data-dock-control="clear" aria-label="Clear search" disabled={pending} onClick={()=>{onQuery('');onChange({q:''});controls.current?.querySelector('input')?.focus({preventScroll:true});}}><X size={17} aria-hidden="true"/></button>}<button className="browse-search-submit" data-dock-control="submit" aria-label="Search projects" disabled={pending}><span>{pending?'Searching…':'Search'}</span>{workspace&&<Search className="dock-control-icon" size={17} aria-hidden="true"/>}</button></form>}
   <Dialog open={open} onOpenChange={value=>{if(value)setDraft(filters);setOpen(value)}}><DialogTrigger asChild><button className="button browse-filter" data-dock-control="filters" aria-label={filterCount?`Filters (${filterCount} active)`:'Filters'} disabled={pending}><SlidersHorizontal size={17} aria-hidden="true"/><span>Filters</span>{filterCount>0&&<span className="filter-count">{filterCount}</span>}</button></DialogTrigger><DialogContent className="browse-filter-sheet"><DialogHeader><DialogTitle>Find your next project</DialogTitle><DialogDescription>Choose what matters to you. Apply when you’re ready.</DialogDescription></DialogHeader>
    <form onSubmit={event=>{event.preventDefault();if(pending)return;onChange({category:draft.category,technology:draft.technology,projectType:draft.projectType,availability:draft.availability,liveDemo:draft.liveDemo});setOpen(false)}}>
     <div className="browse-filter-body">{([{key:'category',title:'Business category',options:categories},{key:'technology',title:'Technology',options:technologies},{key:'projectType',title:'Project type',options:['SAAS','CLIENT','COMMERCIAL','OPEN_SOURCE','PERSONAL','PROTOTYPE','HACKATHON','LEARNING']}] as const).map(field=><div className="browse-filter-field" key={field.key}><label htmlFor={filterId+'-'+field.key}>{field.title}</label><select id={filterId+'-'+field.key} value={draft[field.key]} onChange={event=>setDraft({...draft,[field.key]:event.target.value})}><option value="">All {field.key==='category'?'categories':field.key==='technology'?'technologies':'project types'}</option>{field.options.map(option=><option key={option} value={option}>{field.key==='projectType'?(option==='SAAS'?'SaaS':label(option)):option}</option>)}</select></div>)}
      <label className="browse-check"><input type="checkbox" checked={!!draft.availability} onChange={event=>setDraft({...draft,availability:event.target.checked?'AVAILABLE_NOW':''})}/><span>Available for work<small>Builders currently accepting client inquiries</small></span></label>
      {liveDemo&&<label className="browse-check"><input type="checkbox" checked={draft.liveDemo} onChange={event=>setDraft({...draft,liveDemo:event.target.checked})}/><span>Live demo<small>Projects with a live demo link</small></span></label>}
     </div><div className="browse-filter-footer"><button type="button" className="link" onClick={()=>setDraft({...emptyFilters,q:filters.q,sort:filters.sort})}>Clear filters</button><button className="button primary" type="submit" disabled={pending}>Apply filters</button></div>
    </form>
   </DialogContent></Dialog>
   <label className="browse-sort"><ArrowUpDown size={16} aria-hidden="true"/><span className="sr-only">Sort projects</span><select data-dock-control="sort" value={filters.sort} disabled={pending} onChange={event=>onChange({sort:event.target.value})}>{sortOptions.map(option=><option key={option.value} value={option.value}>{option.label}</option>)}</select></label>
  </div>;
 return <section className={`browse-toolbar${workspace?' workspace-browse-toolbar':''}${compact?' browse-toolbar-compact':''}`} aria-label="Project search and filters" aria-busy={pending}>
  {workspace?<div ref={anchor} style={dock?{height:dock.height}:undefined}>{!dock&&actions}</div>:actions}
  {dock&&createPortal(actions,dock.target)}
  <div className="browse-results"><span role="status" aria-live="polite">{pending?'Updating results…':`${count} ${saved?'saved ':''}project${count===1?'':'s'}`}{illustrative&&' · sample data'}</span>{selected.length>0&&<div className="browse-chips" aria-label="Active filters">{selected.map(([key,text])=><button key={key} className="browse-chip" disabled={pending} aria-label={`Remove ${text}`} onClick={()=>{if(key==='q')onQuery('');onChange({[key]:key==='liveDemo'?false:''})}}>{text}<X size={13} aria-hidden="true"/></button>)}<button className="link" onClick={onReset}>Clear all</button></div>}</div>
 </section>;
}
