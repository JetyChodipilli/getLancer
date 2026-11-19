import {notFound, redirect} from 'next/navigation';
import {backendOrigin} from '@/lib/server';
import WorkspaceDirectory from '@/app/components/workspace-directory';

export default async function PreviewWorkspaceAreaPage({params}:{params:Promise<{area:string}>}) {
  const {area}=await params;
  if(area!=='clients'&&area!=='trust')notFound();
  if(await backendOrigin())redirect('/workspace/areas/'+area);
  return <WorkspaceDirectory key={area} area={area} preview/>;
}
