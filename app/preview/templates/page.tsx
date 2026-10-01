import TemplateWorkspace from '@/app/components/template-workspace';
import {backendOrigin} from '@/lib/server';
import {redirect} from 'next/navigation';
export const metadata={title:'Source marketplace preview',robots:{index:false,follow:false}};
export default async function Page(){if(await backendOrigin())redirect('/workspace/templates');return <TemplateWorkspace demo/>;}
