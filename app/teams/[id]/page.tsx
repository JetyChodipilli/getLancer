import TeamPublic from '@/app/components/team-public';
export default async function TeamPage({params}:{params:Promise<{id:string}>}){const {id}=await params;return <TeamPublic id={id}/>}
