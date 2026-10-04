import ComponentWorkspace from '@/app/components/component-workspace';
import {backendOrigin} from '@/lib/server';
import {redirect} from 'next/navigation';
export const metadata={title:'Component publishing preview',robots:{index:false,follow:false}};
export default async function Page(){if(await backendOrigin())redirect('/workspace/components');return <ComponentWorkspace demo/>;}
