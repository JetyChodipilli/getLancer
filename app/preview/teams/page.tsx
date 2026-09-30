import TeamWorkspace from '@/app/components/team-workspace';
import {backendOrigin} from '@/lib/server';
import {redirect} from 'next/navigation';
export default async function TeamsPreviewPage(){if(await backendOrigin())redirect('/workspace/teams');return <TeamWorkspace demo/>}
