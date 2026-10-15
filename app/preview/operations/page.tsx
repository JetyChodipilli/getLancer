import OperationsWorkspace from '@/app/components/operations-workspace';
import {backendOrigin} from '@/lib/server';
import {redirect} from 'next/navigation';
export const metadata={title:'Operations interface preview',robots:{index:false,follow:false}};
export default async function Page(){if(await backendOrigin())redirect('/workspace/operations');return <OperationsWorkspace sample/>;}
