import WorkspacePreview from './workspace-preview';
import {backendOrigin} from '@/lib/server';
import {redirect} from 'next/navigation';
export const metadata={title:'Interactive V1 demo',robots:{index:false,follow:false},alternates:{canonical:null}};
export default async function Page(){if(await backendOrigin())redirect('/workspace');return <WorkspacePreview/>}
