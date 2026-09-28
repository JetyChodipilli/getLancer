'use client';
import {useState} from 'react';
import {Search,SlidersHorizontal,X,ArrowUpDown} from 'lucide-react';
import {Dialog,DialogTrigger,DialogContent,DialogHeader,DialogTitle,DialogDescription} from '@/components/ui/dialog';
import {label} from '@/lib/catalog';
import {emptyFilters,type Filters} from '@/lib/discovery';

type Props={filters:Filters;query:string;onQuery:(value:string)=>void;onChange:(value:Partial<Filters>)=>void;onReset:()=>void;count:number;categories:string[];technologies:string[];pending?:boolean;workspace?:boolean;compact?:boolean;saved?:boolean;illustrative?:boolean;liveDemo?:boolean;sortOptions?:{value:string;label:string}[]};
export default function BrowseToolbar({filters,query,onQuery,onChange,onReset,count,categories,technologies,pending=false,workspace=false,compact=false,saved=false,illustrative=false,liveDemo=true,sortOptions=[{value:'relevance',label:'Most relevant'},{value:'newest',label:'Newest first'},{value:'updated',label:'Recently updated'}]}:Props){
 const [open,setOpen]=useState(false),[draft,setDraft]=useState(filters);
 const selected:[keyof Filters,string][]=[];
 if(filters.q)selected.push(['q',`Search: ${filters.q}`]);
 if(filters.category)selected.push(['category',filters.category]);
 if(filters.technology)selected.push(['technology',filters.technology]);
 if(filters.projectType)selected.push(['projectType',filters.projectType==='SAAS'?'SaaS':label(filters.projectType)]);
 if(filters.availability)selected.push(['availability','Available for work']);
 if(filters.liveDemo)selected.push(['liveDemo','Live demo']);
 const filterCount=selected.filter(([key])=>key!=='q').length;
 return <section className={`browse-toolbar${workspace?' browse-toolbar-sticky':''}${compact?' browse-toolbar-compact':''}`} aria-label="Project search and filters">
  <div className="browse-controls">
   {!compact&&<form className="browse-search" role="search" onSubmit={event=>{event.preventDefault();onChange({q:query});}}><Search size={19} aria-hidden="true"/><input aria-label={illustrative?'Search demo projects':'Search the collection'} placeholder={saved?'Search your saved projects…':'Search projects, builders or technology…'} maxLength={200} value={query} onChange={event=>onQuery(event.target.value)}/>{query&&<button type="button" className="iconbutton" aria-label="Clear search" onClick={()=>{onQuery('');onChange({q:''})}}><X size={17}/></button>}<button className="browse-search-submit" aria-label="Search projects">Search</button></form>}
   <Dialog open={open} onOpenChange={value=>{if(value)setDraft(filters);setOpen(value)}}><DialogTrigger asChild><button className="button browse-filter"><SlidersHorizontal size={17}/>Filters{filterCount>0&&<span className="filter-count">{filterCount}</span>}</button></DialogTrigger><DialogContent className="browse-filter-sheet"><DialogHeader><DialogTitle>Find your next project</DialogTitle><DialogDescription>Choose what matters to you. Apply when you’re ready.</DialogDescription></DialogHeader>
    <form onSubmit={event=>{event.preventDefault();onChange({category:draft.category,technology:draft.technology,projectType:draft.projectType,availability:draft.availability,liveDemo:draft.liveDemo});setOpen(false)}}>
     <div className="browse-filter-body">{([{key:'category',title:'Business category',options:categories},{key:'technology',title:'Technology',options:technologies},{key:'projectType',title:'Project type',options:['SAAS','CLIENT','COMMERCIAL','OPEN_SOURCE','PERSONAL','PROTOTYPE','HACKATHON','LEARNING']}] as const).map(field=><label key={field.key}>{field.title}<select value={draft[field.key]} onChange={event=>setDraft({...draft,[field.key]:event.target.value})}><option value="">All {field.key==='category'?'categories':field.key==='technology'?'technologies':'project types'}</option>{field.options.map(option=><option key={option} value={option}>{field.key==='projectType'?(option==='SAAS'?'SaaS':label(option)):option}</option>)}</select></label>)}
      <label className="browse-check"><input type="checkbox" checked={!!draft.availability} onChange={event=>setDraft({...draft,availability:event.target.checked?'AVAILABLE_NOW':''})}/><span>Available for work<small>Builders currently accepting client inquiries</small></span></label>
      {liveDemo&&<label className="browse-check"><input type="checkbox" checked={draft.liveDemo} onChange={event=>setDraft({...draft,liveDemo:event.target.checked})}/><span>Live demo<small>Projects with a live demo link</small></span></label>}
     </div><div className="browse-filter-footer"><button type="button" className="link" onClick={()=>setDraft({...emptyFilters,q:filters.q,sort:filters.sort})}>Clear filters</button><button className="button primary" type="submit">Apply filters</button></div>
    </form>
   </DialogContent></Dialog>
   <label className="browse-sort"><ArrowUpDown size={16} aria-hidden="true"/><span className="sr-only">Sort projects</span><select value={filters.sort} onChange={event=>onChange({sort:event.target.value})}>{sortOptions.map(option=><option key={option.value} value={option.value}>{option.label}</option>)}</select></label>
  </div>
  <div className="browse-results"><span role="status" aria-live="polite">{pending?'Updating results…':`${count} ${saved?'saved ':''}project${count===1?'':'s'}`}{illustrative&&' · sample data'}</span>{selected.length>0&&<div className="browse-chips" aria-label="Active filters">{selected.map(([key,text])=><button key={key} className="browse-chip" aria-label={`Remove ${text}`} onClick={()=>{if(key==='q')onQuery('');onChange({[key]:key==='liveDemo'?false:''})}}>{text}<X size={13} aria-hidden="true"/></button>)}<button className="link" onClick={onReset}>Clear all</button></div>}</div>
 </section>;
}
