import TeamWorkspace from '@/app/components/team-workspace';
export default async function TeamsWorkspacePage({searchParams}:{searchParams:Promise<{tab?:string|string[]}>}) {
  const {tab}=await searchParams;
  const initialTab=typeof tab==='string'?tab:undefined;
  return <TeamWorkspace key={initialTab} initialTab={initialTab}/>;
}
