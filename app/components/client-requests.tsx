'use client';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import ReviewFields from './review-fields';
import MoreRecords from './more-records';
import { api } from '@/lib/api';
import { label } from '@/lib/catalog';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from '@/components/ui/dialog';

type RequestItem = { id: string; projectTitle: string; builder: string; description: string; budgetBand: string; timelineBand: string; status: string; moderationStatus: string; reviewSubmitted: boolean };
export default function ClientRequests({ verified }: { verified: boolean }) {
  const [items, setItems] = useState<RequestItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [selected, setSelected] = useState<RequestItem | null>(null);
  const [history, setHistory] = useState<{ event_type: string; actor_type: string; created_at: string }[]>([]);
  async function load() {
    if (!verified) { setLoading(false); return; }
    try { setItems((await api('/me/inquiries')).items); setError(''); }
    catch (e) { setError((e as Error).message); } finally { setLoading(false); }
  }
  useEffect(() => { load(); }, [verified]);
  async function act(item: RequestItem, action: string, body: unknown = {}) {
    setBusy(true); setError('');
    try {
      await api('/me/inquiries/' + item.id + '/' + action, { method: 'POST', body: JSON.stringify(body) });
      setMessage(action === 'confirmation-link' ? 'A fresh confirmation link has been sent to your email.' : action === 'review' ? 'Your review is saved for moderation.' : 'Your response has been recorded.');
      setSelected(null); await load();
    } catch (e) { setError((e as Error).message); } finally { setBusy(false); }
  }
  const kind = selected?.status === 'HIRE_PENDING_CONFIRMATION' ? 'HIRE_CONFIRMATION' : 'COMPLETION_CONFIRMATION';
  return <section>
    <div className="workspacehead"><div><h2>My requests</h2><p className="muted">Work you want to commission, alongside the work you showcase.</p></div><Link className="button" href="/">Find a builder</Link></div>
    {message && <p className="success" role="status">{message}</p>}
    {error && <p className="error" role="alert">{error}</p>}
    {!verified ? <p className="samplebar">Confirm your account email to view requests sent with that address.</p> : loading ? <p role="status">Loading your requests…</p> : items.length ? items.map(item => <article key={item.id} className="row">
      <div className="grow"><h3>{item.projectTitle} · {item.builder}</h3><p className="summary">{item.description}</p><p className="muted">{label(item.budgetBand)} · {label(item.timelineBand)}</p><span className="tag">{label(item.status)}</span>{item.moderationStatus!=='CLEAR'&&<p className="samplebar">This request is restricted pending moderation.</p>}</div>
      <button className="button" onClick={async () => { setHistory([]); setSelected(item); try { setHistory((await api('/me/inquiries/' + item.id)).events); } catch (e) { setError((e as Error).message); } }}>{['HIRE_PENDING_CONFIRMATION', 'COMPLETION_PENDING_CONFIRMATION'].includes(item.status) ? 'Review confirmation' : item.status === 'COMPLETED' && !item.reviewSubmitted ? 'Write a review' : 'View request'}</button>
      {item.moderationStatus==='CLEAR'&&['CREATED_UNVERIFIED','EXPIRED'].includes(item.status) && <button className="button" disabled={busy} onClick={() => act(item, 'confirmation-link')}>Resend confirmation</button>}
    </article>) : <div className="empty"><h2>Start with a project you like.</h2><p>Choose “Build something similar” on a project to contact its builder.</p><Link className="button primary" href="/">Explore projects</Link></div>}
    {verified&&<MoreRecords path="/me/inquiries" onItems={rows=>setItems(old=>[...old,...rows.filter(r=>!old.some(x=>x.id===r.id))])}/>}
    <Dialog open={!!selected} onOpenChange={open => !open && setSelected(null)}><DialogContent style={{ maxHeight: '88vh', overflowY: 'auto', maxWidth: 650 }}><DialogHeader><DialogTitle>{selected?.projectTitle}</DialogTitle><DialogDescription>Your request to {selected?.builder}. Confirm only the outcome you have agreed to.</DialogDescription></DialogHeader>
      {selected && <><p>{selected.description}</p><p className="tag">{label(selected.status)}</p>
        {selected.moderationStatus==='CLEAR'&&['HIRE_PENDING_CONFIRMATION', 'COMPLETION_PENDING_CONFIRMATION'].includes(selected.status) && <div className="stack"><p>{kind === 'HIRE_CONFIRMATION' ? 'Have you agreed to hire this builder for this request?' : 'Has this engagement been completed as agreed?'}</p><button className="button primary" disabled={busy} onClick={() => act(selected, 'decision', { kind, decision: 'ACCEPT' })}>{kind === 'HIRE_CONFIRMATION' ? 'Confirm hire' : 'Confirm completion'}</button><button className="button" disabled={busy} onClick={() => act(selected, 'decision', { kind, decision: 'REJECT' })}>{kind === 'HIRE_CONFIRMATION' ? 'Not yet — continue discussion' : 'Not yet — work is still in progress'}</button></div>}
        {selected.moderationStatus==='CLEAR'&&selected.status === 'COMPLETED' && !selected.reviewSubmitted && <form className="form" onSubmit={e => { e.preventDefault(); act(selected, 'review', Object.fromEntries(new FormData(e.currentTarget))); }}><ReviewFields prefix="client"/><button className="button primary" disabled={busy}>Submit review</button></form>}
        {selected.moderationStatus==='CLEAR'&&['INQUIRY_RECEIVED', 'RESPONDED', 'DISCUSSION', 'PROPOSAL_SENT'].includes(selected.status) && <button className="button" disabled={busy} onClick={() => act(selected, 'not-hired')}>Close request as not hired</button>}
        <h3>Request history</h3>{history.map((event, n) => <div className="row" key={n}><div><strong>{label(event.event_type)}</strong><p className="muted">{label(event.actor_type)} · {new Date(event.created_at).toLocaleString()}</p></div></div>)}
        {error && <p className="error" role="alert">{error}</p>}
      </>}
    </DialogContent></Dialog>
  </section>;
}
