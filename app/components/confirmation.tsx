'use client';
import { useEffect, useState, type FormEvent } from 'react';
import Link from 'next/link';
import ReviewFields from './review-fields';
import { api } from '@/lib/api';

export default function Confirmation() {
  const [token, setToken] = useState('');
  const [context, setContext] = useState<{kind: string; used: boolean; inquiry?: {projectTitle: string; builder: string; description: string}} | null>(null);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    const value = new URLSearchParams(location.hash.slice(1)).get('token') || new URLSearchParams(location.search).get('token') || '';
    history.replaceState(null, '', '/confirm'); setToken(value);
    if (!value) { setError('Open the confirmation link from your email.'); return; }
    api('/auth/confirmation', {method: 'POST', body: JSON.stringify({token: value})}).then(data => {setContext(data); if (data.used) setMessage('This response has already been recorded.');}).catch(e => setError(e.message));
  }, []);
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); setBusy(true); setError('');
    const form = new FormData(e.currentTarget);
    const submitter = (e.nativeEvent as SubmitEvent).submitter as HTMLButtonElement | null;
    try {
      await api('/auth/confirm', {method: 'POST', body: JSON.stringify({token, ...Object.fromEntries(form), decision: submitter?.value || 'ACCEPT'})});
      setMessage(context?.kind === 'ACCOUNT_DELETION' ? 'Your account is closed. Public content is hidden and retained records are pending a privacy review.' : context?.kind === 'PASSWORD_RESET' ? 'Your password has been changed. You can log in now.' : context?.kind === 'REVIEW' ? 'Your review has been saved for moderation.' : 'Your response has been recorded.');
    } catch (e) { setError((e as Error).message); } finally { setBusy(false); }
  }
  const kind = context?.kind;
  const outcome = kind === 'HIRE_CONFIRMATION' || kind === 'COMPLETION_CONFIRMATION';
  const titles: Record<string,string> = {ACCOUNT_DELETION: 'Confirm account closure', EMAIL_VERIFICATION: 'Confirm your email', CLIENT_INQUIRY_CONFIRMATION: 'Send your inquiry to the builder', PASSWORD_RESET: 'Choose a new password', HIRE_CONFIRMATION: 'Confirm your hire', COMPLETION_CONFIRMATION: 'Confirm project completion', REVIEW: 'Review your experience'};
  return <main className="wrap" id="main"><div className="panel form" style={{margin: '20px auto'}}><h1 style={{fontSize:32}}>{titles[kind || ''] || 'Your confirmation'}</h1>
    {context?.inquiry && <div className="samplebar"><strong>{context.inquiry.projectTitle} · {context.inquiry.builder}</strong><p>{context.inquiry.description}</p></div>}
    {message ? <div role="status" className="success">{message}</div> : context && <form className="form" onSubmit={submit}>
      {kind === 'PASSWORD_RESET' && <><label htmlFor="new-password">New password</label><input name="password" id="new-password" type="password" autoComplete="new-password" minLength={12} maxLength={72} required/></>}
      {kind === 'REVIEW' && <ReviewFields prefix="confirmation"/>}
      {outcome && <p>{kind === 'HIRE_CONFIRMATION' ? 'Have you agreed to hire this builder for this request?' : 'Has this engagement been completed as agreed?'}</p>}
      {kind === 'ACCOUNT_DELETION' && <p>Closing your account signs you out of every device and hides your profile and showcases. Engagement and moderation records enter a retention review. Ignore this email if you did not request closure.</p>}
      <button className={kind === 'ACCOUNT_DELETION' ? 'button danger' : 'button primary'} value={kind === 'ACCOUNT_DELETION' ? 'DELETE' : 'ACCEPT'} disabled={busy}>{busy ? 'Saving…' : kind === 'ACCOUNT_DELETION' ? 'Close my account' : kind === 'REVIEW' ? 'Submit review' : 'Confirm'}</button>
      {outcome && <button className="button" value="REJECT" disabled={busy}>Not yet — {kind === 'HIRE_CONFIRMATION' ? 'continue discussion' : 'work is still in progress'}</button>}
    </form>}
    {!context && !error && <p role="status">Checking your link…</p>}
    {error && <p role="alert" className="error">{error}</p>}
    <div className="policy-links" style={{marginTop:24}}><Link href="/workspace" className="link">My workspace</Link><Link href="/login" className="link">Log in</Link><Link href="/" className="link">Explore projects</Link></div>
  </div></main>;
}
