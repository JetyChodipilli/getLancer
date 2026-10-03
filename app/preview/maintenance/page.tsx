import MaintenanceWorkspace from '@/app/components/maintenance-workspace';
import {backendOrigin} from '@/lib/server';
import {redirect} from 'next/navigation';
export const metadata={title:'Maintenance preview',robots:{index:false,follow:false}};
export default async function Page(){if(await backendOrigin())redirect('/workspace/maintenance');return <MaintenanceWorkspace demo/>;}
