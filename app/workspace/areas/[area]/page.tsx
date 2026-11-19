import {notFound} from 'next/navigation';
import WorkspaceDirectory from '@/app/components/workspace-directory';

export default async function WorkspaceAreaPage({params}:{params:Promise<{area:string}>}) {
  const {area}=await params;
  if(area!=='clients'&&area!=='trust')notFound();
  return <WorkspaceDirectory area={area}/>;
}
