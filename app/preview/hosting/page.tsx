import HostingWorkspace from '@/app/components/hosting-workspace';
import {backendOrigin} from '@/lib/server';
import {redirect} from 'next/navigation';
export const metadata={title:'Hosted frontend demo sample',robots:{index:false,follow:false}};
export default async function Page(){if(await backendOrigin())redirect('/workspace/hosting');return <HostingWorkspace demo/>;}
