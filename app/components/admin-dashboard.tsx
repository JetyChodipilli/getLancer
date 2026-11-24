'use client';

import Link from 'next/link';
import {
  ArrowRight, Boxes, CalendarDays, ChevronRight, FileText, Gauge, LayoutDashboard, RefreshCw,
  ShieldCheck, ShoppingCart, UsersRound,
} from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { accountApi, api, ApiError } from '@/lib/api';
import { dashboardSnapshot, queueKeys, type DashboardSnapshot, type DashboardWeeks } from '@/lib/admin-dashboard';
import Brand from './brand';

const adminWorkspace = '/workspace?tab=admin';
const navigation = [
  { label: 'Overview', href: '/admin', icon: LayoutDashboard },
  { label: 'Moderation', href: `${adminWorkspace}#moderation-queue`, icon: ShieldCheck },
  { label: 'Commerce', href: '#admin-commerce', icon: ShoppingCart },
  { label: 'Catalog', href: '#admin-catalog', icon: Boxes },
  { label: 'Users & teams', href: `${adminWorkspace}#admin-accounts`, icon: UsersRound },
  { label: 'Operations', href: '/workspace/operations', icon: Gauge },
  { label: 'Audit log', href: `${adminWorkspace}#admin-audit`, icon: FileText },
];

const queueLinks = {
  projects: ['Projects to review', `${adminWorkspace}&queue=projects#moderation-projects`],
  reports: ['Unresolved reports', `${adminWorkspace}&queue=reports#moderation-reports`],
  disputes: ['Open disputes', `${adminWorkspace}#delivery-disputes-title`],
  payments: ['Payment checks', `${adminWorkspace}#payment-attention-title`],
  email: ['Failed email jobs', `${adminWorkspace}#admin-email-jobs`],
} as const;

const numbers = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 1 });
const dateLabel = new Intl.DateTimeFormat('en-GB', { month: 'short', day: 'numeric', timeZone: 'UTC' });
const snapshotLabel = new Intl.DateTimeFormat('en-GB', {
  month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', timeZone: 'UTC',
});
const percentage = (number: number | null) => number === null ? 'Unavailable' : numbers.format(number) + '%';

function timeLabel(value: string) {
  return snapshotLabel.format(new Date(value)) + ' UTC';
}

function rupees(minor: string) {
  const exact = BigInt(minor);
  const paisePerRupee = BigInt(100);
  const whole = (exact / paisePerRupee).toLocaleString('en-IN');
  return `₹${whole}.${String(exact % paisePerRupee).padStart(2, '0')}`;
}

function InquiryTrend({ trend }: { trend: DashboardSnapshot['trend'] }) {
  const max = Math.max(...trend.map(week => week.qualified), 4);
  const ceiling = Math.ceil(max / 4) * 4;
  const points = trend.map((week, index) => ({
    ...week,
    x: 49 + index * (529 / (trend.length - 1)),
    y: 183 - (week.qualified / ceiling) * 160,
  }));

  return <section className="admin-panel admin-trend" aria-labelledby="admin-trend-title">
    <h2 id="admin-trend-title">Weekly qualified inquiries</h2>
    <svg viewBox="0 0 610 218" role="img" aria-label="Qualified inquiries by UTC week. Open the table for exact weekly counts.">
      {[0, 1, 2, 3, 4].map(step => {
        const y = 183 - step * 40;
        return <g key={step}><line x1="47" x2="579" y1={y} y2={y} />
          <text x="42" y={y + 4} textAnchor="end">{numbers.format(ceiling * step / 4)}</text></g>;
      })}
      <polyline points={points.map(({ x, y }) => `${x},${y}`).join(' ')} />
      {points.map(({ start, qualified, x, y }, index) => <g key={start}>
        <circle cx={x} cy={y} r="4"><title>{dateLabel.format(new Date(start))}: {qualified}</title></circle>
        {(trend.length === 4 || index % 3 === 0 || index === trend.length - 1) &&
          <text x={x} y="206" textAnchor="middle">{dateLabel.format(new Date(start))}</text>}
      </g>)}
    </svg>
    <details>
      <summary>Weekly counts</summary>
      <table><caption>Weeks starting Monday, UTC</caption><thead><tr><th scope="col">Week</th><th scope="col">Qualified inquiries</th></tr></thead>
        <tbody>{trend.map(week => <tr key={week.start}><th scope="row"><time dateTime={week.start}>{dateLabel.format(new Date(week.start))}</time></th><td>{numbers.format(week.qualified)}</td></tr>)}</tbody>
      </table>
    </details>
  </section>;
}

function DashboardContent({ snapshot }: { snapshot: DashboardSnapshot }) {
  const { outcomes, trend, queues, catalog, commerce, readiness } = snapshot;
  const metrics = [
    ['Qualified inquiries', numbers.format(outcomes.qualified)],
    ['Confirmed hires', numbers.format(outcomes.hired)],
    ['Completed engagements', numbers.format(outcomes.completed)],
    ['Builder response rate', percentage(outcomes.responsePercent)],
  ];
  return <>
    <dl className="admin-kpis" aria-label="Marketplace KPIs">
      {metrics.map(([label, value], index) => <div key={label}>
        <dt>{label}</dt><dd>{value}</dd>
        {index === 3 && <dd className="admin-caption">{outcomes.eligible
          ? `${numbers.format(outcomes.responded)} of ${numbers.format(outcomes.eligible)} eligible`
          : 'No inquiries at least 48 hours old'}</dd>}
      </div>)}
    </dl>
    <div className="admin-grid">
      <InquiryTrend trend={trend} />
      <section className="admin-panel" aria-labelledby="admin-queue-title">
        <div className="admin-panel-head">
          <div><h2 id="admin-queue-title">Needs attention</h2><p className="admin-caption">Current backlog</p></div>
          <Link prefetch={false} href={`${adminWorkspace}#moderation-queue`}>View queues</Link>
        </div>
        <ul className="admin-queue">{queueKeys.map(key => {
          const queue = queues.find(item => item.key === key);
          const [label, href] = queueLinks[key];
          if (!queue) return null;
          return <li key={key}><Link prefetch={false} href={href}>
            <span>{label}{queue.severity && <span className="admin-severity"> · {queue.severity === 'HIGH' ? 'High priority' : 'Critical'}</span>}
              {queue.oldest && <small>Oldest <time dateTime={queue.oldest}>{timeLabel(queue.oldest)}</time></small>}</span>
            <strong>{numbers.format(queue.count)}</strong><ChevronRight size={17} aria-hidden="true" />
          </Link></li>;
        })}</ul>
      </section>
      <section className="admin-panel" aria-labelledby="admin-outcomes-title">
        <h2 id="admin-outcomes-title">Inquiry outcomes</h2><p className="admin-caption">Same inquiry cohort</p>
        <ol className="admin-funnel">
          {[['Qualified', outcomes.qualified], ['Hired', outcomes.hired], ['Completed', outcomes.completed]].map(([label, value], index) =>
            <li key={label}><span>{label}</span><strong>{numbers.format(Number(value))}</strong>
              {index < 2 && <ArrowRight size={18} aria-hidden="true" />}</li>
          )}
        </ol>
        <details><summary>Conversion and response</summary>
          <dl className="admin-facts">
            <div><dt>Inquiry to hire</dt><dd>{percentage(outcomes.inquiryToHirePercent)}</dd></div>
            <div><dt>Hire to completion</dt><dd>{percentage(outcomes.hireToCompletionPercent)}</dd></div>
            <div><dt>Median first reply</dt><dd>{outcomes.medianResponseHours === null ? 'Unavailable' : numbers.format(outcomes.medianResponseHours) + ' hours'}</dd></div>
          </dl>
          <p className="admin-caption">Qualified inquiries are email-confirmed and clear of moderation restrictions. Response metrics use inquiries at least 48 hours old.</p>
        </details>
      </section>
      <section className="admin-panel" aria-labelledby="admin-ready-title">
        <div className="admin-panel-head"><h2 id="admin-ready-title">Launch readiness</h2>
          <Link prefetch={false} href="/workspace/operations">View operations</Link></div>
        {readiness ? <dl className="admin-facts">
          <div><dt>Hosted runtime</dt><dd data-tone={readiness.runtimeEnabled ? 'positive' : 'muted'}>{readiness.runtimeEnabled ? 'Enabled' : 'Disabled'}</dd></div>
          <div><dt>Live payments</dt><dd data-tone={readiness.livePaymentsEnabled ? 'positive' : 'muted'}>{readiness.livePaymentsEnabled ? 'Configured' : 'Disabled'}</dd></div>
          <div><dt>Release gates</dt><dd data-tone={readiness.productionCertified ? 'positive' : 'warning'}>{readiness.productionCertified ? 'Certified' : 'Evidence pending'}</dd></div>
        </dl> : <p className="admin-caption">Recent administrator verification is required to see readiness.</p>}
      </section>
      <section className="admin-panel" id="admin-catalog" aria-labelledby="admin-catalog-title">
        <div className="admin-panel-head"><h2 id="admin-catalog-title">Catalog</h2>
          <Link prefetch={false} href={`${adminWorkspace}#operator-metrics-title`}>View publishing metrics</Link></div>
        <dl className="admin-facts">
          <div><dt>Active public showcases</dt><dd>{numbers.format(catalog.activeShowcases)}</dd></div>
          <div><dt>Unreachable showcase demos</dt><dd>{numbers.format(catalog.demosUnreachable)}</dd></div>
        </dl>
      </section>
      <section className="admin-panel" id="admin-commerce" aria-labelledby="admin-commerce-title">
        <div className="admin-panel-head"><h2 id="admin-commerce-title">Commerce</h2>
          <Link prefetch={false} href={queueLinks.payments[1]}>View payment checks</Link></div>
        {commerce.length ? <div className="admin-table-scroll"><table>
          <caption>Provider ledger entries in the selected period</caption>
          <thead><tr><th scope="col">Product</th><th scope="col">Mode</th><th scope="col">Captured</th><th scope="col">Refunded</th></tr></thead>
          <tbody>{commerce.map(row => <tr key={row.product + row.mode}>
            <th scope="row">{row.product}</th><td>{row.mode === 'live' ? 'Live' : 'Test'}</td>
            <td>{rupees(row.capturedMinor)}</td><td>{rupees(row.refundedMinor)}</td>
          </tr>)}</tbody>
        </table></div> : <p className="admin-caption">No milestone or source purchase ledger entries for this period.</p>}
      </section>
    </div>
  </>;
}

export default function AdminDashboard() {
  const [weeks, setWeeks] = useState<DashboardWeeks>(4);
  const [snapshot, setSnapshot] = useState<DashboardSnapshot | null>(null);
  const [error, setError] = useState('');
  const [signedOut, setSignedOut] = useState(false);
  const [restricted, setRestricted] = useState(false);
  const [busy, setBusy] = useState(true);
  const [refresh, setRefresh] = useState(0);
  const actor = useRef<string | null>(null);

  useEffect(() => {
    let active = true;
    let current: AbortController | null = null;
    let expiry: ReturnType<typeof setTimeout> | undefined;
    async function load() {
      current?.abort();
      clearTimeout(expiry);
      const request = new AbortController();
      current = request;
      const init = { cache: 'no-store' as const, signal: AbortSignal.any([request.signal, AbortSignal.timeout(15_000)]) };
      setSnapshot(null);
      setError('');
      setBusy(true);
      setRestricted(false);
      setSignedOut(false);
      try {
        if (!actor.current) {
          const user = await api('/me', init);
          if (request.signal.aborted || !active) return;
          if (typeof user.id !== 'string' || !Array.isArray(user.roles) || !user.roles.includes('ADMIN')) {
            throw new ApiError('Administrator access is required.', 'FORBIDDEN', 403);
          }
          actor.current = user.id;
        }
        const data = dashboardSnapshot(await accountApi(actor.current, `/admin/dashboard?weeks=${weeks}`, init), weeks);
        if (request.signal.aborted || !active) return;
        setSnapshot(data);
        expiry = setTimeout(() => {
          setSnapshot(null);
          setError('This snapshot expired. Refresh to check current data and administrator access.');
        }, 5 * 60_000);
      } catch (problem) {
        if (request.signal.aborted || !active) return;
        setSignedOut(problem instanceof ApiError && problem.status === 401);
        setRestricted(problem instanceof ApiError && [401, 403].includes(problem.status));
        setError(problem instanceof Error && problem.name === 'TimeoutError'
          ? 'The dashboard request timed out. Refresh to try again.'
          : problem instanceof Error ? problem.message : 'Dashboard data is unavailable. Try refreshing.');
      } finally {
        if (!request.signal.aborted && active) setBusy(false);
      }
    }
    function hide() {
      current?.abort();
      clearTimeout(expiry);
      if (active) setSnapshot(null);
    }
    function onVisibilityChange() {
      if (document.hidden) hide();
      else void load();
    }
    function onPageShow(event: PageTransitionEvent) {
      if (event.persisted) void load();
    }
    queueMicrotask(() => { if (active && !document.hidden) void load(); });
    document.addEventListener('visibilitychange', onVisibilityChange);
    window.addEventListener('pagehide', hide);
    window.addEventListener('pageshow', onPageShow);
    return () => {
      active = false;
      hide();
      document.removeEventListener('visibilitychange', onVisibilityChange);
      window.removeEventListener('pagehide', hide);
      window.removeEventListener('pageshow', onPageShow);
    };
  }, [weeks, refresh]);

  const visible = snapshot?.window.weeks === weeks ? snapshot : null;

  return <div className="admin-dashboard">
    <aside className="admin-sidebar">
      <Link prefetch={false} href="/admin" aria-label="getLancer admin overview"><Brand /></Link>
      {!restricted && visible && <nav aria-label="Administration">
        {navigation.map(({ href, label, icon: Icon }, index) =>
          <Link prefetch={false} href={href} key={label} aria-current={index === 0 ? 'page' : undefined}>
            <Icon size={19} aria-hidden="true" />{label}
          </Link>)}
      </nav>}
      {visible && <Link prefetch={false} className="admin-account" href="/workspace?tab=account">
        <span aria-hidden="true">AD</span>Administrator
      </Link>}
    </aside>
    <main id="main" className="admin-main">
      <header className="admin-heading">
        <div><h1>Admin Dashboard</h1>
          {visible && <p className="admin-caption">
            Updated <time dateTime={visible.window.asOf}>{timeLabel(visible.window.asOf)}</time> · Current week in progress
          </p>}
        </div>
        {!restricted && <div className="admin-controls">
          <label><CalendarDays size={18} aria-hidden="true" />
            <span className="sr-only">Dashboard period</span>
            <select value={weeks} onChange={event => {
              setSnapshot(null); setBusy(true); setWeeks(event.target.value === '12' ? 12 : 4);
            }}>
              <option value={4}>Last 4 weeks</option><option value={12}>Last 12 weeks</option>
            </select>
          </label>
          <button type="button" disabled={busy} onClick={() => { setSnapshot(null); setBusy(true); setRefresh(n => n + 1); }}>
            <RefreshCw size={17} aria-hidden="true" />Refresh
          </button>
        </div>}
      </header>
      <div aria-busy={busy}>
        {busy && <p role="status" className="admin-panel">Loading dashboard…</p>}
        {error && <div className="admin-panel" role="alert">
          <h2>Dashboard unavailable</h2><p>{error}</p>
          {restricted && <Link prefetch={false} href={signedOut ? '/login' : '/workspace'}>{signedOut ? 'Sign in' : 'Return to workspace'}</Link>}
        </div>}
        {visible && <DashboardContent snapshot={visible} />}
      </div>
    </main>
  </div>;
}
