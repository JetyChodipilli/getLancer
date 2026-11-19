import TeamWorkspace from '@/app/components/team-workspace';
export default async function TeamsWorkspacePage({searchParams}:{searchParams:Promise<{tab?:string|string[]}>}) {
  const {tab}=await searchParams;
  return <TeamWorkspace initialTab={typeof tab==='string'?tab:undefined}/>;
}
