import BusinessWorkspace from '@/app/components/business-workspace';
import {backendOrigin} from '@/lib/server';
import {redirect} from 'next/navigation';
export const metadata={title:'Business hiring preview',robots:{index:false,follow:false}};
export default async function Page(){if(await backendOrigin())redirect('/workspace/business');return <BusinessWorkspace demo/>;}
