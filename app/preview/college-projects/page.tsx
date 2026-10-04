import CollegeWorkspace from '@/app/components/college-workspace';
import {backendOrigin} from '@/lib/server';
import {redirect} from 'next/navigation';
export const metadata={title:'College context preview',robots:{index:false,follow:false}};
export default async function Page(){if(await backendOrigin())redirect('/workspace/college-projects');return <CollegeWorkspace demo/>;}
