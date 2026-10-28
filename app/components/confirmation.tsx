'use client';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import Link from 'next/link';
import ReviewFields from './review-fields';
import { api, ApiError } from '@/lib/api';

export default function Confirmation() {
  const linkRevision = useRef(0);
  const [token, setToken] = useState('');
  const [context, setContext] = useState<{kind: string; used: boolean; inquiry?: {projectTitle: string; builder: string; description: string}} | null>(null);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [canRetry, setCanRetry] = useState(false);
  async function checkLink(value: string) {
    const current = ++linkRevision.current;
    setContext(null); setMessage(''); setError(''); setCanRetry(false); setBusy(false);
    try {
      const data = await api('/auth/confirmation', {method: 'POST', body: JSON.stringify({token: value})});
      if (current !== linkRevision.current) return;
      setContext(data); if (data.used) setMessage('This response has already been recorded.');
    } catch (e) {
      if (current !== linkRevision.current) return;
      setError((e as Error).message);
      setCanRetry(!(e instanceof ApiError) || e.status >= 500 || e.status === 429);
    }
  }
  useEffect(() => {
    function readLink(event?: Event) {
      const value = new URLSearchParams(location.hash.slice(1)).get('token') || new URLSearchParams(location.search).get('token') || '';
      // A fragment navigation emits both popstate and hashchange. The first
      // handler removes the bearer token; the second must preserve that result.
      if (event && !value) return;
      history.replaceState(null, '', '/confirm');
      setToken(value);
      if (!value) { linkRevision.current++; setContext(null); setMessage(''); setBusy(false); setCanRetry(false); setError('Open the confirmation link from your email.'); return; }
      void checkLink(value);
    }
    readLink();
    window.addEventListener('hashchange', readLink);
    window.addEventListener('popstate', readLink);
    return () => { linkRevision.current++; window.removeEventListener('hashchange', readLink); window.removeEventListener('popstate', readLink); };
  }, []);
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); if (busy) return; const current = linkRevision.current; setBusy(true); setError('');
    const form = new FormData(e.currentTarget);
    const submitter = (e.nativeEvent as SubmitEvent).submitter as HTMLButtonElement | null;
    try {
      await api('/auth/confirm', {method: 'POST', body: JSON.stringify({token, ...Object.fromEntries(form), decision: submitter?.value || 'ACCEPT'})});
      if (current !== linkRevision.current) return;
      setMessage(context?.kind === 'ACCOUNT_DELETION' ? 'Your account is closed. Public content is hidden and retained records are pending a privacy review.' : context?.kind === 'PASSWORD_RESET' ? 'Your password has been changed. You can log in now.' : context?.kind === 'REVIEW' ? 'Your review has been saved for moderation.' : 'Your response has been recorded.');
    } catch (e) { if (current === linkRevision.current) setError((e as Error).message); } finally { if (current === linkRevision.current) setBusy(false); }
  }
  const kind = context?.kind;
  const outcome = kind === 'HIRE_CONFIRMATION' || kind === 'COMPLETION_CONFIRMATION';
  const titles: Record<string,string> = {ACCOUNT_DELETION: 'Confirm account closure', EMAIL_VERIFICATION: 'Confirm your email', CLIENT_INQUIRY_CONFIRMATION: 'Send your inquiry to the builder', PASSWORD_RESET: 'Choose a new password', HIRE_CONFIRMATION: 'Confirm your hire', COMPLETION_CONFIRMATION: 'Confirm project completion', REVIEW: 'Review your experience'};
  return <main className="wrap confirmation-page" id="main"><div className="panel form" style={{margin: '20px auto'}}><h1 style={{fontSize:32}}>{titles[kind || ''] || 'Your confirmation'}</h1>
    {context?.inquiry && <div className="samplebar"><strong>{context.inquiry.projectTitle} · {context.inquiry.builder}</strong><p>{context.inquiry.description}</p></div>}
    {message ? <div role="status" className="success">{message}</div> : context && <form className="form" onSubmit={submit} aria-busy={busy}>
      <fieldset disabled={busy} style={{margin:0,padding:0,border:0}}>
      {kind === 'PASSWORD_RESET' && <><label htmlFor="new-password">New password</label><input name="password" id="new-password" type="password" autoComplete="new-password" minLength={12} maxLength={72} required/></>}
      {kind === 'REVIEW' && <ReviewFields prefix="confirmation"/>}
      {outcome && <p>{kind === 'HIRE_CONFIRMATION' ? 'Have you agreed to hire this builder for this request?' : 'Has this engagement been completed as agreed?'}</p>}
      {kind === 'ACCOUNT_DELETION' && <p>Closing your account signs you out of every device and hides your profile and showcases. Engagement and moderation records enter a retention review. Ignore this email if you did not request closure.</p>}
      <button className={kind === 'ACCOUNT_DELETION' ? 'button danger' : 'button primary'} value={kind === 'ACCOUNT_DELETION' ? 'DELETE' : 'ACCEPT'} disabled={busy}>{busy ? 'Saving…' : kind === 'ACCOUNT_DELETION' ? 'Close my account' : kind === 'REVIEW' ? 'Submit review' : 'Confirm'}</button>
      {outcome && <button className="button" value="REJECT" disabled={busy}>Not yet — {kind === 'HIRE_CONFIRMATION' ? 'continue discussion' : 'work is still in progress'}</button>}
      </fieldset>
    </form>}
    {!context && !error && <p role="status">Checking your link…</p>}
    {error && <p role="alert" className="error">{error}</p>}
    {canRetry && <button type="button" className="button" onClick={() => void checkLink(token)}>Retry checking link</button>}
    <div className="policy-links" style={{marginTop:24}}><Link href="/workspace" className="link">My workspace</Link><Link href="/login" className="link">Log in</Link><Link href="/" className="link">Explore projects</Link></div>
  </div></main>;
}
