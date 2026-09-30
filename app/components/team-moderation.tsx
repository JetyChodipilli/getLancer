'use client';
import {useState} from 'react';
import {api} from '@/lib/api';

export default function TeamModeration({request=api}:{request?:typeof api}){
 const [teams,setTeams]=useState<any[]>([]),[loaded,setLoaded]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState('');
 async function load(){setError('');setBusy(true);try{setTeams((await request('/admin/teams')).items);setLoaded(true)}catch(e){setError((e as Error).message)}finally{setBusy(false)}}
 return <details className="panel"><summary>Teams and studios</summary><p>Suspended teams leave discovery and cannot receive new client requests. Every decision requires a reason and is audited.</p>
 <button type="button" className="button" disabled={busy} onClick={load}>{busy?'Loading…':loaded?'Refresh teams':'Load teams'}</button>
 {error&&<p className="error" role="alert">{error}</p>}{notice&&<p role="status">{notice}</p>}{loaded&&!teams.length&&<p>No teams to review.</p>}
 {teams.map(team=><form key={team.id} className="form panel" onSubmit={async e=>{e.preventDefault();const form=e.currentTarget;setError('');setNotice('');setBusy(true);try{await request('/admin/teams/'+team.id+'/moderate',{method:'POST',body:JSON.stringify(Object.fromEntries(new FormData(form)))});setTeams((await request('/admin/teams')).items);setNotice('Team decision recorded.');form.reset()}catch(e){setError((e as Error).message)}finally{setBusy(false)}}}>
 <h3>{team.name}</h3><p>{team.summary}</p><p>Current status: {team.status}</p><label>Decision<select name="status" defaultValue={team.status==='ACTIVE'?'SUSPENDED':'ACTIVE'}><option value="SUSPENDED">Suspend team</option><option value="ACTIVE">Restore team</option></select></label><label>Reason<textarea name="reason" required minLength={10} maxLength={3000}/></label><button className="button" disabled={busy}>Record decision</button>
 </form>)}</details>;
}
