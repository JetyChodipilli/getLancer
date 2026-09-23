import { Layers, Inbox, CircleCheck, ArrowUpRight, ShieldCheck, UserRound, Flag, Bookmark, MessageSquare } from 'lucide-react';
export default function WorkspaceOverview({active,capacity,inquiries,completed,onSelect,moderation,saves=0}:{active:number;capacity:number;inquiries:number;completed:number;onSelect:(tab:string)=>void;saves?:number;moderation?:{projects:number;profiles:number;reports:number;reviews?:number}}) {
 if(moderation)return <div className="workspace-overview" aria-label="Moderation summary">{[
  {name:'Projects to review',count:moderation.projects,note:'Inspect proof and contribution',Icon:ShieldCheck},
  {name:'Builder profiles',count:moderation.profiles,note:'Review community applications',Icon:UserRound},
  {name:'Reviews to publish',count:moderation.reviews||0,note:'From completed engagements',Icon:MessageSquare},
  {name:'Open reports',count:moderation.reports,note:'Keep the community trustworthy',Icon:Flag}
 ].map(({name,count,note,Icon})=><button className="overview-card" key={name} onClick={()=>onSelect('admin')}><span className="overview-icon"><Icon size={21}/></span><span><span className="overview-label">{name}</span><strong>{count}</strong><span className="overview-note">{note}</span></span><ArrowUpRight className="overview-arrow" size={18}/></button>)}</div>;
 return <div className="workspace-overview" aria-label="Workspace summary">
  <button className="overview-card" onClick={()=>onSelect('showcases')}><span className="overview-icon"><Layers size={21}/></span><span><span className="overview-label">Active showcases</span><strong>{active}<small> / {capacity}</small></strong><span className="overview-note">Your work in the spotlight</span></span><ArrowUpRight className="overview-arrow" size={18}/></button>
  <button className="overview-card" onClick={()=>onSelect('inquiries')}><span className="overview-icon"><Inbox size={21}/></span><span><span className="overview-label">Qualified inquiries</span><strong>{inquiries}</strong><span className="overview-note">From email-confirmed clients</span></span><ArrowUpRight className="overview-arrow" size={18}/></button>
  <button className="overview-card" onClick={()=>onSelect('analytics')}><span className="overview-icon"><CircleCheck size={21}/></span><span><span className="overview-label">Completed engagements</span><strong>{completed}</strong><span className="overview-note">Confirmed by your clients</span></span><ArrowUpRight className="overview-arrow" size={18}/></button>
  <button className="overview-card" onClick={()=>onSelect('analytics')}><span className="overview-icon"><Bookmark size={21}/></span><span><span className="overview-label">Project saves</span><strong>{saves}</strong><span className="overview-note">Your work on client shortlists</span></span><ArrowUpRight className="overview-arrow" size={18}/></button>
 </div>;
}
