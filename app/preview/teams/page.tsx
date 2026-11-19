import TeamWorkspace from '@/app/components/team-workspace';
import {backendOrigin} from '@/lib/server';
import {redirect} from 'next/navigation';
export default async function TeamsPreviewPage({searchParams}:{searchParams:Promise<{tab?:string|string[]}>}) {
  if(await backendOrigin())redirect('/workspace/teams');
  const {tab}=await searchParams;
  const initialTab=typeof tab==='string'?tab:undefined;
  return <TeamWorkspace key={initialTab} demo initialTab={initialTab}/>;
}
