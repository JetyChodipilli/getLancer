import { Layers, Inbox, CircleCheck, ArrowUpRight, ShieldCheck, UserRound, Flag, Bookmark, MessageSquare } from 'lucide-react';
export default function WorkspaceOverview({active,capacity,inquiries,completed,onSelect,moderation,saves=0}:{active:number;capacity:number;inquiries:number;completed:number;onSelect:(tab:string)=>void;saves?:number;moderation?:{projects:number;profiles:number;reports:number;reviews?:number}}) {
 const items = moderation ? [
  {name:'Projects to review',count:moderation.projects,note:'Inspect proof and contribution',action:'Review projects',tab:'admin',Icon:ShieldCheck},
  {name:'Builder profiles',count:moderation.profiles,note:'Review community applications',action:'Review profiles',tab:'admin',Icon:UserRound},
  {name:'Reviews to publish',count:moderation.reviews||0,note:'From completed engagements',action:'Review feedback',tab:'admin',Icon:MessageSquare},
  {name:'Open reports',count:moderation.reports,note:'Keep the community trustworthy',action:'Review reports',tab:'admin',Icon:Flag},
 ] : [
  {name:'Active showcases',count:active,limit:capacity,note:'Your work in the spotlight',action:'Manage showcases',tab:'showcases',Icon:Layers},
  {name:'Qualified inquiries',count:inquiries,note:'From email-confirmed clients',action:'View inquiries',tab:'inquiries',Icon:Inbox},
  {name:'Completed engagements',count:completed,note:'Confirmed by your clients',action:'View engagements',tab:'analytics',Icon:CircleCheck},
  {name:'Project saves',count:saves,note:'Your work on client shortlists',action:'View save activity',tab:'analytics',Icon:Bookmark},
 ];
 return <div className="workspace-overview" aria-label={moderation?'Moderation summary':'Workspace summary'}>{items.map(item=><button type="button" className="overview-card" key={item.name} onClick={()=>onSelect(item.tab)}>
  <span className="overview-card-head"><span className="overview-label">{item.name}</span><item.Icon size={18} aria-hidden="true" /></span>
  <strong>{item.count}{'limit' in item&&<small> / {item.limit}</small>}</strong>
  <span className="overview-note">{item.note}</span>
  <span className="overview-card-action">{item.action}<ArrowUpRight size={15} aria-hidden="true" /></span>
 </button>)}</div>;
}
