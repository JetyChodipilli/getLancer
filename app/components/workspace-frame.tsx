import type { ReactNode } from 'react';
import Link from 'next/link';
import { ArrowUpRight, BriefcaseBusiness, ChevronDown, Code2, Compass, FlaskConical, Layers, LifeBuoy, ShieldCheck, Users, type LucideIcon } from 'lucide-react';
import { Card, CardContent, CardHeader } from '@/components/ui/card';
import { cn } from '@/lib/utils';

type Section = 'personal' | 'trust' | 'teams' | 'business' | 'delivery' | 'templates' | 'maintenance' | 'hosting' | 'components' | 'college' | 'slots' | 'labs';

export function WorkspaceFrame({ children, title, description, section, preview = false, actions, className }: {
  children: ReactNode; title: string; description: string; section: Section;
  preview?: boolean; actions?: ReactNode; className?: string;
}) {
  const root = preview ? '/preview' : '/workspace';
  const links = [
    { section: 'personal', title: 'Personal workspace', href: preview ? root + '/workspace' : root, Icon: BriefcaseBusiness },
    { section: 'components', title: 'Components & slots', href: root + '/components', Icon: Code2 },
    { section: 'slots', title: 'Publishing slots', href: root + '/slots', Icon: Layers },
    { section: 'college', title: 'College projects', href: root + '/college-projects', Icon: Layers },
    ...(!preview ? [{ section: 'labs', title: 'Lab operations', href: root + '/labs', Icon: FlaskConical }] : []),
    { section: 'trust', title: 'Trust & reliability', href: root + '/trust', Icon: ShieldCheck },
    { section: 'teams', title: 'Teams & studios', href: root + '/teams', Icon: Users },
    { section: 'delivery', title: 'Delivery & payments', href: root + '/delivery', Icon: Layers },
    { section: 'maintenance', title: 'Maintenance & support', href: root + '/maintenance', Icon: LifeBuoy },
    { section: 'hosting', title: 'Hosted frontend demos', href: root + '/hosting', Icon: Compass },
    { section: 'templates', title: 'Templates & purchases', href: root + '/templates', Icon: Code2 },
    { section: 'business', title: 'Business hiring', href: root + '/business', Icon: BriefcaseBusiness },
  ];
  const current = links.find(link => link.section === section)!;
  return <main id="main" className={cn('studio-shell', className)}>
    <aside className="studio-rail" aria-label="Workspace navigation">
      <div className="studio-rail-title"><span><Layers size={18} aria-hidden="true" /></span><div><strong>Your workspace</strong><small>Build. Deliver. Grow.</small></div></div>
      <nav aria-label="Workspace areas"><span className="studio-rail-label">Manage your work</span>{links.map(({ section: key, title: label, href, Icon }) => <Link key={key} href={href} aria-current={section === key ? 'page' : undefined}><Icon size={18} aria-hidden="true" /><span>{label}</span></Link>)}</nav>
      <div className="studio-rail-secondary"><span className="studio-rail-label">Discover</span><Link href="/"><Compass size={18} aria-hidden="true" />Explore projects<ArrowUpRight size={14} aria-hidden="true" /></Link><Link href="/templates"><Code2 size={18} aria-hidden="true" />Explore templates<ArrowUpRight size={14} aria-hidden="true" /></Link><Link href="/teams"><Users size={18} aria-hidden="true" />Find a team<ArrowUpRight size={14} aria-hidden="true" /></Link></div>
      <div className="studio-rail-foot"><LifeBuoy size={18} aria-hidden="true" /><div><strong>A little guidance?</strong><Link href="/how-it-works">See how getLancer works <ArrowUpRight size={12} aria-hidden="true" /></Link></div></div>
    </aside>
    <div className="studio-body">
      <header className="studio-page-head"><div><nav className="studio-breadcrumb" aria-label="Breadcrumb"><Link href={preview ? '/preview/workspace' : '/workspace'}>Workspace</Link><span aria-hidden="true">/</span><span aria-current="page">{section === 'personal' ? 'Personal' : current.title}</span></nav><h1>{title}</h1><p className="studio-description">{description}</p></div>{actions && <div className="studio-head-actions">{actions}</div>}</header>
      {children}
    </div>
  </main>;
}

export function PreviewControls({ title, children, note }: { title: string; children: ReactNode; note?: string }) {
  return <details className="studio-preview"><summary><span className="studio-preview-label"><FlaskConical size={16} aria-hidden="true" /><strong>{title}</strong><span>Sample data only</span></span><span className="studio-preview-toggle">Preview controls <ChevronDown size={15} aria-hidden="true" /></span></summary><div className="studio-preview-content"><p>{note || 'Changes stay in this preview. No live accounts or external services are changed.'}</p><div className="studio-preview-actions">{children}</div></div></details>;
}

export function WorkspaceMetrics({ items }: { items: { label: string; value: ReactNode; detail: string; Icon: LucideIcon }[] }) {
  return <div className="studio-metrics">{items.map(({ label, value, detail, Icon }) => <Card className="studio-metric" key={label}><CardHeader><span>{label}</span><Icon size={18} aria-hidden="true" /></CardHeader><CardContent><strong>{value}</strong><p>{detail}</p></CardContent></Card>)}</div>;
}
