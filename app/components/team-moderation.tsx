'use client';
import {useCallback,useEffect,useRef,useState} from 'react';
import {api,accountApi} from '@/lib/api';
import MoreRecords,{type PageInfo} from './more-records';

type TeamReview={id:string;name:string;summary:string;status:'ACTIVE'|'SUSPENDED'};
type TeamReviewPage=PageInfo&{items:TeamReview[]};

export default function TeamModeration({request=api,onDenied,reload}:{request?:typeof api;onDenied?:(problem:unknown)=>void;reload?:()=>Promise<boolean>}){
 const [teams,setTeams]=useState<TeamReview[]>([]),[loaded,setLoaded]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState(''),[page,setPage]=useState<PageInfo>(),[revision,setRevision]=useState(0),[pageGeneration,setPageGeneration]=useState(0);
 const generation=useRef(0),mounted=useRef(true),authority=useRef(0),account=useRef<string|null>(null),pending=useRef(false);
 useEffect(()=>{mounted.current=true;return()=>{mounted.current=false;generation.current++;authority.current++;};},[]);
 function clear(problem:unknown){generation.current++;authority.current++;account.current=null;setTeams([]);setPage(undefined);setLoaded(false);setBusy(false);setNotice('');setError(problem instanceof Error?problem.message:'Team access could not be refreshed.');setRevision(value=>value+1);onDenied?.(problem);}
 const call=useCallback((path:string,init?:RequestInit)=>request===api?accountApi(account.current,path,init):request(path,init),[request]);
 async function load(recorded=false){const current=++generation.current;setError('');setNotice('');setBusy(true);setLoaded(false);setPage(undefined);setTeams([]);setRevision(value=>value+1);try{
  let actor=account.current;if(request===api){const user=await request('/me');if(actor&&actor!==user.id)authority.current++;actor=user.id;}
  const result:TeamReviewPage=await (request===api?accountApi(actor,'/admin/teams'):request('/admin/teams'));
  if(!mounted.current||current!==generation.current)return false;
  account.current=actor;setTeams(result.items);setPage(result);setPageGeneration(current);setLoaded(true);return true;
 }catch(problem){if(mounted.current&&current===generation.current){clear(problem);const status=problem!==null&&typeof problem==='object'&&'status' in problem?Number(problem.status):0;if(recorded&&![401,403].includes(status))setError('The decision was recorded, but the team list could not be refreshed. Load teams to retry.');}return false;}finally{if(mounted.current&&current===generation.current)setBusy(false);}}
 const current=pageGeneration;
 return <details className="panel"><summary>Teams and studios</summary><p>Suspended teams leave discovery and cannot receive new client requests. Every decision requires a reason and is audited.</p>
 <button type="button" className="button" disabled={busy} onClick={()=>void load()}>{busy?'Loading…':loaded?'Refresh teams':'Load teams'}</button>
 {error&&<p className="error" role="alert">{error}</p>}{notice&&<p role="status">{notice}</p>}{loaded&&!teams.length&&<p>No teams to review.</p>}
 {teams.map(team=><form key={team.id} className="form panel" onSubmit={async event=>{event.preventDefault();if(busy||pending.current)return;pending.current=true;const form=event.currentTarget,epoch=authority.current;let operation=++generation.current;setPage(undefined);setRevision(value=>value+1);setError('');setNotice('');setBusy(true);try{await call('/admin/teams/'+team.id+'/moderate',{method:'POST',body:JSON.stringify(Object.fromEntries(new FormData(form)))});if(!mounted.current||operation!==generation.current)return;if(!await load(true))return;operation=generation.current;setBusy(true);if(reload&&!await reload().catch(()=>false)){if(mounted.current&&epoch===authority.current)clear(Error('The team decision was recorded, but your workspace could not be verified. Refresh your workspace before another decision.'));return;}if(mounted.current&&epoch===authority.current){setNotice('Team decision recorded.');form.reset();}}catch(problem){if(mounted.current&&operation===generation.current)clear(problem);}finally{pending.current=false;if(mounted.current&&operation===generation.current)setBusy(false);}}}>
 <h3>{team.name}</h3><p>{team.summary}</p><p>Current status: {team.status}</p><label>Decision<select name="status" defaultValue={team.status==='ACTIVE'?'SUSPENDED':'ACTIVE'}><option value="SUSPENDED">Suspend team</option><option value="ACTIVE">Restore team</option></select></label><label>Reason<textarea name="reason" required minLength={10} maxLength={3000}/></label><button className="button" disabled={busy}>Record decision</button>
 </form>)}{loaded&&!busy&&page&&<MoreRecords<TeamReview> key={revision} path="/admin/teams" firstPage={page} request={call} label="Load more teams for review" onItems={rows=>{if(current===generation.current)setTeams(old=>[...old,...rows.filter(row=>!old.some(item=>item.id===row.id))]);}} onError={problem=>{if(current===generation.current)clear(problem);}}/>}</details>;
}
