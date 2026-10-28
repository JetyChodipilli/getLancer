'use client';
import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import ReviewFields from './review-fields';
import MoreRecords, {type PageInfo} from './more-records';
import { api, ApiError } from '@/lib/api';
import { label } from '@/lib/catalog';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from '@/components/ui/dialog';

type RequestItem = { id: string; projectTitle: string; builder: string; description: string; budgetBand: string; timelineBand: string; status: string; moderationStatus: string; reviewSubmitted: boolean };
export default function ClientRequests({ verified }: { verified: boolean }) {
  return <RequestsForIdentity key={String(verified)} verified={verified}/>;
}
function RequestsForIdentity({verified}: {verified:boolean}) {
  const [items, setItems] = useState<RequestItem[]>([]);
  const [page, setPage] = useState<PageInfo|null>(null);
  const [epoch, setEpoch] = useState(0);
  const [refreshNeeded, setRefreshNeeded] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [selected, setSelected] = useState<RequestItem | null>(null);
  const [history, setHistory] = useState<{ event_type: string; actor_type: string; created_at: string }[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState('');
  const historyGeneration = useRef(0), listGeneration = useRef(0), active = useRef(true), mutation = useRef(false);
  function closeRequest() { historyGeneration.current++; setSelected(null); setHistory([]); setHistoryError(''); setHistoryLoading(false); }
  function failed(problem:unknown) {
    if(problem instanceof ApiError&&[401,403].includes(problem.status)) {
      listGeneration.current++; setItems([]); setPage(null); setEpoch(value=>value+1); closeRequest(); setMessage(''); setRefreshNeeded(true); setLoading(false);
    }
    setError((problem as Error).message);
  }
  async function openRequest(item: RequestItem) {
    const current=++historyGeneration.current;
    setSelected(item); setHistory([]); setHistoryError(''); setHistoryLoading(true);
    try { const detail=await api('/me/inquiries/'+item.id); if(active.current&&current===historyGeneration.current)setHistory(detail.events); }
    catch(problem) { if(active.current&&current===historyGeneration.current){if(problem instanceof ApiError&&[401,403].includes(problem.status))failed(problem);else setHistoryError((problem as Error).message);} }
    finally { if(active.current&&current===historyGeneration.current)setHistoryLoading(false); }
  }
  useEffect(() => { active.current=true; return () => { active.current=false; listGeneration.current++; historyGeneration.current++; }; }, []);
  async function load() {
    if (!verified) { setLoading(false); return false; }
    const current=++listGeneration.current; setLoading(true); setPage(null); setEpoch(value=>value+1); setError('');
    try {
      const next=await api('/me/inquiries'); if(!active.current||current!==listGeneration.current)return false;
      if(!Array.isArray(next.items)||(next.page!==undefined&&next.page!==0)||typeof next.hasMore!=='boolean')throw new Error('Your requests could not be verified. Please retry.');
      setItems(next.items); setPage({page:0,hasMore:next.hasMore,size:next.size}); setRefreshNeeded(false); return true;
    }
    catch (e) { if(active.current&&current===listGeneration.current)failed(e); return false; }
    finally { if(active.current&&current===listGeneration.current)setLoading(false); }
  }
  useEffect(() => { load(); }, [verified]);
  async function act(item: RequestItem, action: string, body: unknown = {}) {
    if(mutation.current||refreshNeeded||!verified)return;
    mutation.current=true; listGeneration.current++; setPage(null); setEpoch(value=>value+1); setLoading(false);
    const current=historyGeneration.current;
    setBusy(true); setError(''); setMessage('');
    try {
      await api('/me/inquiries/' + item.id + '/' + action, { method: 'POST', body: JSON.stringify(body) });
      if(!active.current)return;
      const notice=action === 'confirmation-link' ? 'A fresh confirmation link has been sent to your email.' : action === 'review' ? 'Your review is saved for moderation.' : 'Your response has been recorded.';
      setRefreshNeeded(true); if(current===historyGeneration.current)closeRequest();
      const refreshed=await load(); if(active.current)setMessage(refreshed?notice:notice+' Current requests could not be refreshed. Retry loading requests before making another change.');
    } catch (e) {
      if(!active.current)return;
      failed(e);
      if(!(e instanceof ApiError)||e.status>=500){setRefreshNeeded(true);setMessage('This change could not be confirmed. Refresh your requests before trying again.');}
      else if(!(e instanceof ApiError&&[401,403].includes(e.status))){await load(); if(active.current)setError((e as Error).message);}
    } finally { mutation.current=false; if(active.current)setBusy(false); }
  }
  const kind = selected?.status === 'HIRE_PENDING_CONFIRMATION' ? 'HIRE_CONFIRMATION' : 'COMPLETION_CONFIRMATION';
  return <section>
    <div className="workspacehead"><div><h2>My requests</h2><p className="muted">Work you want to commission, alongside the work you showcase.</p></div><Link className="button" href="/">Find a builder</Link></div>
    {message && <p className="success" role="status">{message}</p>}
    {error && <p className="error" role="alert">{error}</p>}
    {verified&&!loading&&!busy&&!page&&<button type="button" className="button" onClick={()=>void load()}>Retry loading requests</button>}
    {!verified ? <p className="samplebar">Confirm your account email to view requests sent with that address.</p> : loading ? <p role="status">Loading your requests…</p> : items.length ? items.map(item => <article key={item.id} className="row">
      <div className="grow"><h3>{item.projectTitle} · {item.builder}</h3><p className="summary">{item.description}</p><p className="muted">{label(item.budgetBand)} · {label(item.timelineBand)}</p><span className="tag">{label(item.status)}</span>{item.moderationStatus!=='CLEAR'&&<p className="samplebar">This request is restricted pending moderation.</p>}</div>
      {item.moderationStatus==='CLEAR'&&! ['CREATED_UNVERIFIED','EXPIRED','NOT_HIRED'].includes(item.status)&&<Link className="button" href={'/workspace/delivery?inquiryId='+item.id}>Discuss a proposal</Link>}
      <button className="button" disabled={busy||refreshNeeded} onClick={() => void openRequest(item)}>{['HIRE_PENDING_CONFIRMATION', 'COMPLETION_PENDING_CONFIRMATION'].includes(item.status) ? 'Review confirmation' : item.status === 'COMPLETED' && !item.reviewSubmitted ? 'Write a review' : 'View request'}</button>
      {item.moderationStatus==='CLEAR'&&['CREATED_UNVERIFIED','EXPIRED'].includes(item.status) && <button className="button" disabled={busy||refreshNeeded} onClick={() => act(item, 'confirmation-link')}>Resend confirmation</button>}
    </article>) : page&&<div className="empty"><h2>Start with a project you like.</h2><p>Choose “Build something similar” on a project to contact its builder.</p><Link className="button primary" href="/">Explore projects</Link></div>}
    {verified&&page&&!loading&&!busy&&<MoreRecords<RequestItem> key={epoch} path="/me/inquiries" firstPage={page} onItems={rows=>setItems(old=>[...old,...rows.filter(r=>!old.some(x=>x.id===r.id))])} onPage={setPage} onError={problem=>{if(problem instanceof ApiError&&[401,403].includes(problem.status))failed(problem)}}/>}
    <Dialog open={!!selected} onOpenChange={open => !open && closeRequest()}><DialogContent style={{ maxHeight: '88vh', overflowY: 'auto', maxWidth: 650 }}><DialogHeader><DialogTitle>{selected?.projectTitle}</DialogTitle><DialogDescription>Your request to {selected?.builder}. Confirm only the outcome you have agreed to.</DialogDescription></DialogHeader>
      {selected && <><p>{selected.description}</p><p className="tag">{label(selected.status)}</p>
        {selected.moderationStatus==='CLEAR'&&['HIRE_PENDING_CONFIRMATION', 'COMPLETION_PENDING_CONFIRMATION'].includes(selected.status) && <div className="stack"><p>{kind === 'HIRE_CONFIRMATION' ? 'Have you agreed to hire this builder for this request?' : 'Has this engagement been completed as agreed?'}</p><button className="button primary" disabled={busy||refreshNeeded} onClick={() => act(selected, 'decision', { kind, decision: 'ACCEPT' })}>{kind === 'HIRE_CONFIRMATION' ? 'Confirm hire' : 'Confirm completion'}</button><button className="button" disabled={busy||refreshNeeded} onClick={() => act(selected, 'decision', { kind, decision: 'REJECT' })}>{kind === 'HIRE_CONFIRMATION' ? 'Not yet — continue discussion' : 'Not yet — work is still in progress'}</button></div>}
        {selected.moderationStatus==='CLEAR'&&selected.status === 'COMPLETED' && !selected.reviewSubmitted && <form className="form" onSubmit={e => { e.preventDefault(); act(selected, 'review', Object.fromEntries(new FormData(e.currentTarget))); }}><fieldset className="form" disabled={busy||refreshNeeded} style={{border:0,padding:0,margin:0,minWidth:0}}><ReviewFields prefix="client"/><button className="button primary" disabled={busy||refreshNeeded}>Submit review</button></fieldset></form>}
        {selected.moderationStatus==='CLEAR'&&['INQUIRY_RECEIVED', 'RESPONDED', 'DISCUSSION', 'PROPOSAL_SENT'].includes(selected.status) && <button className="button" disabled={busy||refreshNeeded} onClick={() => act(selected, 'not-hired')}>Close request as not hired</button>}
        <h3>Request history</h3>{historyLoading ? <p role="status">Loading request history…</p> : historyError ? <p className="error" role="alert">{historyError}</p> : history.length ? history.map((event, n) => <div className="row" key={n}><div><strong>{label(event.event_type)}</strong><p className="muted">{label(event.actor_type)} · {new Date(event.created_at).toLocaleString()}</p></div></div>) : <p className="muted">No history events yet.</p>}
        {error && <p className="error" role="alert">{error}</p>}
      </>}
    </DialogContent></Dialog>
  </section>;
}
