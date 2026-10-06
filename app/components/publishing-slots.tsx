'use client';

import Link from 'next/link';
import { useEffect, useEffectEvent, useRef, useState, type FormEvent } from 'react';
import { flushSync } from 'react-dom';
import { BriefcaseBusiness, Code2, GraduationCap, Layers } from 'lucide-react';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { api, ApiError } from '@/lib/api';
import { componentLabel, type SlotPricing } from '@/lib/components';
import { loadTemplateCheckout, templateMinor, templatePrice } from '@/lib/templates';
import { samplePublishing, slotNames, slotPool, slotPools, type PublishingOverview, type PublishingPurchase, type SlotPool } from '@/lib/publishing';
import { WorkspaceFrame, WorkspaceMetrics, PreviewControls } from './workspace-frame';
import FormErrors from './form-errors';

type Member = { id: string; roles: string[] };
type Receipts = { items: PublishingPurchase[]; page: number; hasMore: boolean };
const emptyReceipts: Receipts = { items: [], page: 0, hasMore: false };
const settled = (receipt: PublishingPurchase) => ['CAPTURED', 'REFUNDED', 'DISPUTED', 'REJECTED'].includes(receipt.status);

export default function PublishingSlots({ demo = false }: { demo?: boolean }) {
  const [pool, setPool] = useState<SlotPool>('PROJECT'), [me, setMe] = useState<Member | null>(null);
  const [data, setData] = useState<PublishingOverview | null>(null), [receipts, setReceipts] = useState(emptyReceipts), [attention, setAttention] = useState(emptyReceipts);
  const [loading, setLoading] = useState(true), [busy, setBusy] = useState(false), [gatewayOpen, setGatewayOpen] = useState(false);
  const [error, setError] = useState<ApiError | null>(null), [message, setMessage] = useState('');
  const [checkout, setCheckout] = useState(false), [consent, setConsent] = useState(false), [resumed, setResumed] = useState<PublishingPurchase | null>(null);
  const [price, setPrice] = useState(''), [salesEnabled, setSalesEnabled] = useState(false);
  const generation = useRef(0), identity = useRef(''), keys = useRef<Partial<Record<SlotPool, string>>>({}), opener = useRef<HTMLElement | null>(null), mounted = useRef(true), running = useRef(false);
  const builder = me?.roles.includes('DEVELOPER'), admin = me?.roles.includes('ADMIN'), pricing = data?.prices[pool];
  const quote = resumed?.amountMinor ?? pricing?.amountMinor, quoteMode = resumed?.mode ?? pricing?.mode;

  function clearPrivate() { identity.current = ''; keys.current = {}; setMe(null); setData(null); setReceipts(emptyReceipts); setAttention(emptyReceipts); setResumed(null); setConsent(false); setCheckout(false); }
  async function load(selected = pool, ownPage = 0, adminPage = 0) {
    const current = ++generation.current; setLoading(true);
    try {
      if (demo) { setMe({ id: 'sample-builder', roles: ['DEVELOPER'] }); setData(samplePublishing()); setReceipts(emptyReceipts); setAttention(emptyReceipts); setError(null); return; }
      const user: Member = await api('/me');
      const isBuilder = user.roles.includes('DEVELOPER'), isAdmin = user.roles.includes('ADMIN');
      const [overview, own, review] = await Promise.all([
        isBuilder ? api('/me/publishing-slots') : api('/publishing-slots/pricing').then(prices => ({ capacities: samplePublishing().capacities, prices })),
        isBuilder ? api('/me/publishing-slot-purchases?pool=' + selected + '&page=' + ownPage) : Promise.resolve(emptyReceipts),
        isAdmin ? api('/admin/publishing-slot-purchases?pool=' + selected + '&page=' + adminPage) : Promise.resolve(emptyReceipts),
      ]);
      if (current !== generation.current || !mounted.current) return;
      if (identity.current && identity.current !== user.id) { keys.current = {}; setCheckout(false); setResumed(null); setConsent(false); }
      identity.current = user.id; setMe(user); setData(overview); setReceipts(own); setAttention(review);
      const next: SlotPricing = overview.prices[selected]; setPrice(next.amountMinor == null ? '' : String(next.amountMinor / 100)); setSalesEnabled(next.salesEnabled); setError(null);
    } catch (problem) {
      if (current !== generation.current || !mounted.current) return;
      if (problem instanceof ApiError && [401, 403].includes(problem.status)) clearPrivate();
      setError(problem instanceof ApiError ? problem : new ApiError((problem as Error).message, 'LOCAL_ERROR', 400));
    } finally { if (current === generation.current && mounted.current) setLoading(false); }
  }
  const initialLoad = useEffectEvent(() => { const value = slotPool(new URLSearchParams(window.location.search).get('pool')); setPool(value); void load(value); });
  useEffect(() => { mounted.current = true; const timer = window.setTimeout(() => initialLoad(), 0); return () => { window.clearTimeout(timer); mounted.current = false; }; }, [demo]);

  async function run(work: () => Promise<void>) {
    if (running.current) return; running.current = true; setBusy(true); setError(null); setMessage('');
    try { await work(); } catch (problem) { if (mounted.current) { if(problem instanceof ApiError && [401,403].includes(problem.status)) clearPrivate(); setError(problem instanceof ApiError ? problem : new ApiError((problem as Error).message, 'LOCAL_ERROR', 400)); } }
    finally { running.current = false; if (mounted.current) setBusy(false); }
  }
  function changePool(value: SlotPool) {
    generation.current++; setPool(value); setCheckout(false); setResumed(null); setConsent(false); setMessage(''); setReceipts(emptyReceipts); setAttention(emptyReceipts);
    const url = new URL(window.location.href); url.searchParams.set('pool', value); window.history.replaceState(null, '', url); void load(value);
  }
  function reviewPrice(receipt: PublishingPurchase | null = null) { opener.current = document.activeElement as HTMLElement; setResumed(receipt); setConsent(false); setCheckout(true); }
  async function buy() {
    await run(async () => {
      if (demo || !consent || quote == null || !identity.current) throw Error('Review the category and price before purchasing a slot.');
      const owner = identity.current, selected = pool, expectedAmount = quote, expectedMode = quoteMode;
      const currentUser: Member = await api('/me');
      if(currentUser.id !== owner) { clearPrivate(); throw Error('The account changed. Reload publishing slots before purchasing.'); }
      keys.current[selected] ??= crypto.randomUUID();
      let order: PublishingPurchase;
      try { order = await api('/me/publishing-slot-purchases', { method: 'POST', headers: { 'Idempotency-Key': keys.current[selected]! }, body: JSON.stringify({ pool: selected, amountMinor: expectedAmount, purchaseConsent: true }) }); }
      catch (problem) { if (problem instanceof ApiError && problem.code === 'PAYMENT_ORDER_REJECTED') delete keys.current[selected]; throw problem; }
      if (!mounted.current || identity.current !== owner) return;
      if (settled(order)) { delete keys.current[selected]; setCheckout(false); setResumed(null); await load(); setMessage(order.grantsSlot ? 'Your reusable ' + slotNames[order.pool].toLowerCase() + ' slot is available.' : 'This receipt is settled. No new live capacity was granted.'); return; }
      if (!order.orderId || !order.keyId || order.pool !== selected) throw Error('Order creation is unresolved. Reconcile this receipt before paying again.');
      if (order.amountMinor !== expectedAmount || order.mode !== expectedMode) { setResumed(order); setConsent(false); setMessage('This existing order keeps its original price and mode. Review and confirm the frozen price.'); return; }
      const Gateway = await loadTemplateCheckout();
      if (!mounted.current || identity.current !== owner) return;
      flushSync(() => { setCheckout(false); setGatewayOpen(true); });
      const gateway = new Gateway({ key: order.keyId, order_id: order.orderId, amount: order.amountMinor, currency: 'INR', name: 'getLancer', description: 'One reusable ' + slotNames[selected].toLowerCase() + ' publishing slot',
        handler: async (result: Record<string, string>) => {
          if (!mounted.current || identity.current !== owner) return;
          setGatewayOpen(false);
          await run(async () => { const verified: PublishingPurchase = await api('/me/publishing-slot-purchases/' + order.id + '/verify', { method: 'POST', body: JSON.stringify(result) }); if (!mounted.current || identity.current !== owner) return; if (settled(verified)) delete keys.current[selected]; setResumed(null); await load(selected); setMessage(verified.grantsSlot ? 'Payment verified. One reusable slot was added.' : 'Live capacity was not granted. Reconcile the receipt to check payment status.'); });
        }, modal: { ondismiss: () => { if (!mounted.current || identity.current !== owner) return; setGatewayOpen(false); setMessage('Checkout closed. Reconcile the existing receipt before starting another purchase.'); void load(selected); } },
      });
      gateway.on('payment.failed', () => { if (mounted.current && identity.current === owner) setMessage('Payment is not confirmed. Reconcile this receipt before paying again.'); });
      try { gateway.open(); } catch (problem) { setGatewayOpen(false); throw problem; }
    });
  }
  async function reconcile(receipt: PublishingPurchase, operator = false) {
    await run(async () => { const refreshed: PublishingPurchase = await api('/' + (operator ? 'admin' : 'me') + '/publishing-slot-purchases/' + receipt.id + '/reconcile', { method: 'POST' }); if (settled(refreshed)) delete keys.current[refreshed.pool]; await load(); setMessage('Authoritative payment status refreshed.'); });
  }
  async function savePrice(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); await run(async () => { await api('/admin/publishing-slots/' + pool + '/pricing', { method: 'PUT', body: JSON.stringify({ amountMinor: templateMinor(price), enabled: salesEnabled }) }); await load(); setMessage(slotNames[pool] + ' slot price saved. Existing orders keep their original category and price.'); });
  }
  const project = data?.capacities.PROJECT, template = data?.capacities.TEMPLATE, component = data?.capacities.COMPONENT;
  const blocked = busy || loading || gatewayOpen;
  return <WorkspaceFrame section="slots" preview={demo} title="Room for every kind of work." description="Three free regular projects, three college projects, three templates and three components. Add reusable capacity when you need it.">
    {demo && <PreviewControls title="Publishing slots preview" note="Sample capacity only. No payments or account changes are made."><button className="button" onClick={() => void load()}>Reset sample capacity</button></PreviewControls>}
    <FormErrors error={error} />{error&&<button className="button" disabled={busy||loading||gatewayOpen} onClick={()=>void load()}>Refresh publishing slots</button>}{message && <p className="kit-notice" role="status">{message}</p>}
    {loading && <p role="status">Loading publishing slots…</p>}
    {!loading && !me && <section className="kit-record"><h2>Your publishing allowances</h2><p>Log in to manage your builder capacity.</p><Link className="button primary" href="/login">Log in</Link><Link className="button" href="/preview/slots">Explore slot preview</Link></section>}
    {me && <>
      {builder && project && template && component && <><WorkspaceMetrics items={[
        { label: 'Regular projects', value: project.regular.used + ' active', detail: '3 free slots · ' + project.availableRegular + ' available including shared extras', Icon: BriefcaseBusiness },
        { label: 'College projects', value: project.college.used + ' active', detail: '3 free slots · ' + project.availableCollege + ' available including shared extras', Icon: GraduationCap },
        { label: 'Templates', value: template.used + ' / ' + template.limit, detail: '3 free slots · ' + template.purchased + ' purchased', Icon: Layers },
        { label: 'Components', value: component.used + ' / ' + component.limit, detail: '3 free slots · ' + component.purchased + ' purchased', Icon: Code2 },
      ]} /><p className="kit-notice">Project extras: {project.extraUsed} of {project.extraLimit} in use ({project.purchased} purchased, {project.earned} earned). Each extra serves one regular or college project at a time. Archive work to reuse its slot.</p>{!!template.legacy && <p className="kit-notice">Your {template.legacy} existing extra template places were retained when this policy changed.</p>}</>}
      {!builder && !admin && <section className="kit-record"><h2>Create your builder profile.</h2><p>Publishing and slot purchases require an approved builder account.</p><Link className="button primary" href="/workspace?tab=profile">Open builder profile</Link></section>}
      {(builder || admin) && <>
        <section className="kit-record"><label htmlFor="publishing-pool">Slot category</label><select id="publishing-pool" value={pool} disabled={blocked} onChange={e => changePool(e.target.value as SlotPool)}>{slotPools.map(value => <option key={value} value={value}>{value === 'PROJECT' ? 'Projects & college projects' : slotNames[value] + 's'}</option>)}</select></section>
        <div className="kit-workspace-grid"><section className="kit-record"><span className="eyebrow">Reusable capacity</span><h2>Add a {slotNames[pool].toLowerCase()} slot.</h2><p>{pool === 'PROJECT' ? 'Your first three regular projects and three college projects are free independently. An extra project slot serves either category.' : 'Your first three active ' + slotNames[pool].toLowerCase() + 's are free. Each extra slot belongs to this category.'}</p><p className="kit-price-line">{pricing?.amountMinor == null ? 'Price not set' : templatePrice(pricing.amountMinor)}<small> / extra slot</small></p><p>No recurring slot fee. Template selling prices are separate. Component previews and downloads stay free.</p>{pricing?.mode === 'test' && <p className="kit-notice">Provider test mode. Test payments add no live publishing capacity.</p>}{builder && <button className="button primary" disabled={blocked || !!error || !pricing?.enabled || demo} onClick={() => reviewPrice()}>Review price & buy one slot</button>}{(!pricing?.enabled || demo) && <p>{demo ? 'Purchases are disabled in this design preview.' : pricing?.reason}</p>}<div className="kit-actions"><Link className="button" href={demo ? '/preview/workspace' : '/workspace'}>Manage projects</Link><Link className="button" href={(demo ? '/preview' : '/workspace') + '/' + (pool === 'TEMPLATE' ? 'templates' : pool === 'COMPONENT' ? 'components' : 'college-projects')}>Manage {pool === 'PROJECT' ? 'college projects' : slotNames[pool].toLowerCase() + 's'}</Link></div></section>
        {builder && <section className="kit-record"><h2>{slotNames[pool]} slot purchase history</h2>{!receipts.items.length && <p>No additional slots purchased in this category.</p>}{receipts.items.map(receipt => <article className="kit-record" key={receipt.id}><h3>{templatePrice(receipt.amountMinor)} · {componentLabel(receipt.status)}</h3><p>{receipt.mode} · {receipt.grantsSlot ? 'One reusable slot available' : 'Live capacity not granted'}</p><p>Receipt <code>{receipt.id}</code></p><div className="kit-actions"><button className="button" disabled={blocked} onClick={() => void reconcile(receipt)}>Reconcile payment</button>{receipt.status === 'ORDER_CREATED' && <button className="button" disabled={blocked || !!error || demo} onClick={() => reviewPrice(receipt)}>Review existing order</button>}</div></article>)}<div className="kit-actions">{receipts.page > 0 && <button className="button" disabled={blocked} onClick={() => void load(pool, receipts.page - 1, attention.page)}>Previous purchases</button>}{receipts.hasMore && <button className="button" disabled={blocked} onClick={() => void load(pool, receipts.page + 1, attention.page)}>More purchases</button>}</div></section>}</div>
        {admin && <section className="kit-record"><h2>{slotNames[pool]} slot administration</h2><p>Set a separate price for project, template and component extras. The project price applies to either regular or college projects. Administrator MFA is required.</p><form className="form" onSubmit={e => void savePrice(e)}><label htmlFor="publishing-admin-price">Price per {slotNames[pool].toLowerCase()} slot (INR)</label><input id="publishing-admin-price" inputMode="decimal" required value={price} disabled={blocked} onChange={e => setPrice(e.target.value)} placeholder="Set a price" /><label className="kit-consent"><input type="checkbox" checked={salesEnabled} disabled={blocked} onChange={e => setSalesEnabled(e.target.checked)} />Enable new orders when merchant collection is configured</label><button className="button primary" disabled={blocked || demo}>Save {slotNames[pool].toLowerCase()} slot pricing</button></form><h3>Payment recovery</h3>{!attention.items.length && <p>No receipts in this category.</p>}{attention.items.map(receipt => <article className="kit-record" key={receipt.id}><h3>{templatePrice(receipt.amountMinor)} · {componentLabel(receipt.status)}</h3><p>Receipt <code>{receipt.id}</code> · {receipt.mode}</p><button className="button" disabled={blocked} onClick={() => void reconcile(receipt, true)}>Reconcile operator receipt</button>{!receipt.orderId && <form className="form" onSubmit={e => { e.preventDefault(); const fields = new FormData(e.currentTarget); void run(async () => { await api('/admin/publishing-slot-purchases/' + receipt.id + '/bind-order', { method: 'POST', body: JSON.stringify({ orderId: fields.get('orderId'), reason: fields.get('reason') }) }); await load(); setMessage('Verified provider order bound to its original receipt.'); }); }}><label htmlFor={'provider-order-' + receipt.id}>Recovered provider order</label><input id={'provider-order-' + receipt.id} name="orderId" required minLength={10} maxLength={40} /><label htmlFor={'recovery-reason-' + receipt.id}>Recovery evidence</label><textarea id={'recovery-reason-' + receipt.id} name="reason" required minLength={20} maxLength={2000} /><button className="button" disabled={blocked}>Bind verified order</button></form>}</article>)}<div className="kit-actions">{attention.page > 0 && <button className="button" disabled={blocked} onClick={() => void load(pool, receipts.page, attention.page - 1)}>Previous operator receipts</button>}{attention.hasMore && <button className="button" disabled={blocked} onClick={() => void load(pool, receipts.page, attention.page + 1)}>More operator receipts</button>}</div></section>}
      </>}
    </>}
    <Dialog open={checkout} onOpenChange={value => { if (!busy) setCheckout(value); }}><DialogContent className="workspace-dialog" onCloseAutoFocus={event => { event.preventDefault(); opener.current?.focus(); }}><DialogHeader><DialogTitle>One reusable {slotNames[pool].toLowerCase()} slot</DialogTitle><DialogDescription>{pool === 'PROJECT' ? 'Use this extra for one regular or college project at a time.' : 'This slot adds capacity only for ' + slotNames[pool].toLowerCase() + 's.'}</DialogDescription></DialogHeader><p className="kit-price-line">{quote == null ? 'Price not set' : templatePrice(quote)}</p><p>{quoteMode === 'test' ? 'Test checkout. No live publishing capacity is granted.' : 'One-time payment. Archive work to reuse the slot. Refunds or disputes can remove this extra capacity.'}</p>{resumed && <p className="kit-notice">Existing order: its original category, amount and provider mode remain fixed.</p>}<label className="kit-consent"><input type="checkbox" checked={consent} disabled={busy} onChange={e => setConsent(e.target.checked)} />I agree to buy one {slotNames[pool].toLowerCase()} slot for {quote == null ? 'the displayed price' : templatePrice(quote)}.</label><FormErrors error={error} /><div className="kit-actions"><button className="button primary" disabled={busy || !consent || quote == null || demo} onClick={() => void buy()}>{busy ? 'Preparing checkout…' : 'Continue to secure checkout'}</button><button className="button" disabled={busy} onClick={() => setCheckout(false)}>Cancel</button></div></DialogContent></Dialog>
  </WorkspaceFrame>;
}
