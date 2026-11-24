'use client';

import { createContext, useContext, useState, type ReactNode } from 'react';
import { ArrowUpRight } from 'lucide-react';

type ModerationTotals = {
 projects: number | null;
 profiles: number | null;
 reports: number | null;
 reviews: number | null;
};
const ModerationTotalsContext = createContext<{
 totals: ModerationTotals | null | undefined;
 setTotals: (totals: ModerationTotals | null | undefined) => void;
} | null>(null);

export function WorkspaceModerationTotals({children}: {children: ReactNode}) {
 const [totals, setTotals] = useState<ModerationTotals | null>();
 return <ModerationTotalsContext.Provider value={{totals, setTotals}}>{children}</ModerationTotalsContext.Provider>;
}

export function useWorkspaceModerationTotals() {
 return useContext(ModerationTotalsContext);
}

export function readModerationTotals(value: unknown): ModerationTotals | null {
 if (!value || typeof value !== 'object') return null;
 const totals = value as Partial<Record<keyof ModerationTotals, unknown>>;
 function count(item: unknown) {
  return typeof item === 'number' && Number.isSafeInteger(item) && item >= 0 ? item : null;
 }
 return {projects:count(totals.projects), profiles:count(totals.profiles), reports:count(totals.reports), reviews:count(totals.reviews)};
}
export default function WorkspaceOverview({active,capacity,inquiries,completed,onSelect,moderation,saves=0}:{active:number;capacity:number;inquiries:number;completed:number;onSelect:(tab:string)=>void;saves?:number;moderation?:{projects:number;profiles:number;reports:number;reviews?:number}}) {
 const overview = useWorkspaceModerationTotals();
 const totals = overview === null ? moderation : overview.totals;
 const items = moderation ? [
  {name:'Projects to review',count:totals?.projects ?? null,note:'Inspect proof and contribution',action:'Review projects',tab:'admin'},
  {name:'Builder profiles',count:totals?.profiles ?? null,note:'Review community applications',action:'Review profiles',tab:'admin'},
  {name:'Reviews to publish',count:totals?.reviews ?? null,note:'From completed engagements',action:'Review feedback',tab:'admin'},
  {name:'Open reports',count:totals?.reports ?? null,note:'Keep the community trustworthy',action:'Review reports',tab:'admin'},
 ] : [
  {name:'Active showcases',count:active,limit:capacity,note:'Your work in the spotlight',action:'Manage showcases',tab:'showcases'},
  {name:'Qualified inquiries',count:inquiries,note:'From email-confirmed clients',action:'View inquiries',tab:'inquiries'},
  {name:'Completed engagements',count:completed,note:'Confirmed by your clients',action:'View engagements',tab:'analytics'},
  {name:'Project saves',count:saves,note:'Your work on client shortlists',action:'View save activity',tab:'analytics'},
 ];
 return <div className="workspace-overview" role="group" aria-label={moderation?'Moderation summary':'Workspace summary'}>{items.map(item=><button type="button" className="overview-card" key={item.name} onClick={()=>onSelect(item.tab)}>
  <span className="overview-card-head"><span className="overview-label">{item.name}</span></span>
  <strong>{item.count === null ? (overview?.totals === undefined && overview ? 'Loading…' : 'Unavailable') : item.count.toLocaleString('en')}{'limit' in item&&<small> / {item.limit?.toLocaleString('en')}</small>}</strong>
  <span className="overview-note">{item.note}</span>
  <span className="overview-card-action">{item.action}<ArrowUpRight size={15} aria-hidden="true" /></span>
 </button>)}</div>;
}
