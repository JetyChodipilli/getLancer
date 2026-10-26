'use client';
import {useState} from 'react';
import {api} from '@/lib/api';
import MoreRecords,{type PageInfo} from './more-records';

export default function TeamModeration({request=api}:{request?:typeof api}){
 const [teams,setTeams]=useState<any[]>([]),[loaded,setLoaded]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState(''),[page,setPage]=useState<PageInfo>(),[generation,setGeneration]=useState(0);
 async function load(){setError('');setNotice('');setBusy(true);try{const result=await request('/admin/teams');setTeams(result.items);setPage(result);setLoaded(true);setGeneration(n=>n+1);return true}catch(e){setTeams([]);setPage(undefined);setLoaded(false);setError((e as Error).message);return false}finally{setBusy(false)}}
 return <details className="panel"><summary>Teams and studios</summary><p>Suspended teams leave discovery and cannot receive new client requests. Every decision requires a reason and is audited.</p>
 <button type="button" className="button" disabled={busy} onClick={load}>{busy?'Loading…':loaded?'Refresh teams':'Load teams'}</button>
 {error&&<p className="error" role="alert">{error}</p>}{notice&&<p role="status">{notice}</p>}{loaded&&!teams.length&&<p>No teams to review.</p>}
 {teams.map(team=><form key={team.id} className="form panel" onSubmit={async e=>{e.preventDefault();const form=e.currentTarget;setError('');setNotice('');setBusy(true);try{await request('/admin/teams/'+team.id+'/moderate',{method:'POST',body:JSON.stringify(Object.fromEntries(new FormData(form)))});if(!await load())throw new Error('The decision was recorded, but the team list could not be refreshed. Load teams to retry.');setNotice('Team decision recorded.');form.reset()}catch(e){setTeams([]);setPage(undefined);setLoaded(false);setError((e as Error).message)}finally{setBusy(false)}}}>
 <h3>{team.name}</h3><p>{team.summary}</p><p>Current status: {team.status}</p><label>Decision<select name="status" defaultValue={team.status==='ACTIVE'?'SUSPENDED':'ACTIVE'}><option value="SUSPENDED">Suspend team</option><option value="ACTIVE">Restore team</option></select></label><label>Reason<textarea name="reason" required minLength={10} maxLength={3000}/></label><button className="button" disabled={busy}>Record decision</button>
 </form>)}{loaded&&page&&<MoreRecords key={generation} path="/admin/teams" firstPage={page} request={request} label="Load more teams for review" onItems={rows=>setTeams(old=>[...old,...rows.filter(row=>!old.some(item=>item.id===row.id))])} onError={error=>{setTeams([]);setLoaded(false);setPage(undefined);setError(error instanceof Error?error.message:"Team access could not be refreshed.")}}/>}</details>;
}
