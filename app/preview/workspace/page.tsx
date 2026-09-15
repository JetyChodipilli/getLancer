import {backendOrigin} from '@/lib/server';
import {notFound} from 'next/navigation';
import WorkspacePreview from './workspace-preview';
export const metadata={title:'Workspace design preview',robots:{index:false,follow:false},alternates:{canonical:null}};
export default async function Page(){if(await backendOrigin())notFound();return <WorkspacePreview/>}
