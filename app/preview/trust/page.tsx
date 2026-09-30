import TrustWorkspace from '@/app/components/trust-workspace';
import {backendOrigin} from '@/lib/server';
import {redirect} from 'next/navigation';
export const metadata={title:'V1.5 trust preview',robots:{index:false,follow:false}};
export default async function Page(){if(await backendOrigin())redirect('/workspace/trust');return <TrustWorkspace demo/>;}
