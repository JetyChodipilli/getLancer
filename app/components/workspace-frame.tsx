import type { ReactNode } from 'react';
import Link from 'next/link';
import { ArrowUpRight, BriefcaseBusiness, ChevronDown, Code2, Compass, FlaskConical, Layers, LifeBuoy, ShieldCheck, Users, type LucideIcon } from 'lucide-react';
import { Card, CardContent, CardHeader } from '@/components/ui/card';
import { cn } from '@/lib/utils';
import NavIcon,{type NavIconName} from './nav-icon';

type Section = 'personal' | 'clients' | 'trust-directory' | 'trust' | 'teams' | 'business' | 'delivery' | 'templates' | 'maintenance' | 'hosting' | 'components' | 'college' | 'slots' | 'labs' | 'operations';
const clientSections: Section[] = ['clients', 'business', 'delivery', 'templates', 'maintenance'];
const trustSections: Section[] = ['trust-directory', 'trust', 'operations', 'labs'];
const animatedAreas:Partial<Record<Section,NavIconName>>={personal:'workspace',components:'components',slots:'components',college:'college',labs:'learning',teams:'teams',templates:'templates'};

export function WorkspaceFrame({ children, title, description, section, preview = false, operator = false, actions, className }: {
  children: ReactNode; title: string; description: string; section: Section;
  preview?: boolean; operator?: boolean; actions?: ReactNode; className?: string;
}) {
  const root = preview ? '/preview' : '/workspace';
  const links = [
    { section: 'personal', title: 'Personal workspace', href: preview ? root + '/workspace' : root, Icon: BriefcaseBusiness },
    { section: 'components', title: 'Components & slots', href: root + '/components', Icon: Code2 },
    { section: 'slots', title: 'Publishing slots', href: root + '/slots', Icon: Layers },
    { section: 'college', title: 'College projects', href: root + '/college-projects', Icon: Layers },
    ...(!preview ? [{ section: 'labs', title: 'Lab operations', href: root + '/labs', Icon: FlaskConical }] : []),
    ...(operator || section === 'operations' || preview ? [{ section: 'operations', title: 'Operations & release readiness', href: root + '/operations', Icon: ShieldCheck }] : []),
    { section: 'trust', title: 'Trust & reliability', href: root + '/trust', Icon: ShieldCheck },
    { section: 'teams', title: 'Teams & studios', href: root + '/teams', Icon: Users },
    { section: 'delivery', title: 'Delivery & payments', href: root + '/delivery', Icon: Layers },
    { section: 'maintenance', title: 'Maintenance & support', href: root + '/maintenance', Icon: LifeBuoy },
    { section: 'hosting', title: 'Hosted frontend demos', href: root + '/hosting', Icon: Compass },
    { section: 'templates', title: 'Templates & purchases', href: root + '/templates', Icon: Code2 },
    { section: 'business', title: 'Business hiring', href: root + '/business', Icon: BriefcaseBusiness },
  ];
  const area=section==='teams'?'teams':clientSections.includes(section)?'clients':trustSections.includes(section)?'trust':'work';
  const home=preview?'/preview/workspace':'/workspace';
  const areaLanding=section==='personal'||section==='clients'||section==='trust-directory'||section==='teams';
  const areaTabs=[
    {key:'work', title:'Work', href:home},
    {key:'clients', title:'Clients', href:root+'/areas/clients'},
    {key:'trust', title:'Trust', href:root+'/areas/trust'},
    {key:'teams', title:'Teams', href:root+'/teams'},
  ];
  const current = links.find(link => link.section === section)?.title ?? (section==='clients'?'Clients':'Trust');
  // Keep links in the keyboard sequence on WebKit when its default skips anchors.
  const areaLinks = links.map(({ section: key, title: label, href, Icon }) => {const animated=animatedAreas[key as Section];return <Link prefetch={false} tabIndex={0} key={key} href={href} aria-current={section === key ? 'page' : undefined}>{animated?<NavIcon name={animated}/>:<Icon size={18} aria-hidden="true" />}<span>{label}</span></Link>;});
  return <main id="main" className={cn('studio-shell paper-studio', className)} data-workspace={section}>
    <div className="studio-navigation">
      <nav className="studio-area-tabs" aria-label="Workspace navigation">{areaTabs.map(({key,title,href})=><Link key={key} prefetch={false} tabIndex={0} href={href} aria-current={area===key?(areaLanding?'page':'location'):undefined}>{title}</Link>)}</nav>
      <details className="studio-tool-menu">
        <summary>All tools <ChevronDown size={16} aria-hidden="true" /></summary>
        <div className="studio-tool-panel">
          <nav aria-label="Workspace areas">{areaLinks}</nav>
          <nav aria-label="Discover">
            <Link prefetch={false} tabIndex={0} href="/">Explore projects <ArrowUpRight size={14} aria-hidden="true" /></Link>
            <Link prefetch={false} tabIndex={0} href="/templates">Explore templates <ArrowUpRight size={14} aria-hidden="true" /></Link>
            <Link prefetch={false} tabIndex={0} href="/labs/iot">IoT simulation <ArrowUpRight size={14} aria-hidden="true" /></Link>
            <Link prefetch={false} tabIndex={0} href="/components/frameworks">Framework previews <ArrowUpRight size={14} aria-hidden="true" /></Link>
            <Link prefetch={false} tabIndex={0} href="/teams">Find a team <ArrowUpRight size={14} aria-hidden="true" /></Link>
            <Link prefetch={false} tabIndex={0} href="/how-it-works">How getLancer works <ArrowUpRight size={14} aria-hidden="true" /></Link>
          </nav>
        </div>
      </details>
    </div>
    <div className="studio-body">
      <header className="studio-page-head"><div><nav className="studio-breadcrumb" aria-label="Breadcrumb"><Link prefetch={false} href={home}>Workspace</Link><span aria-hidden="true">/</span><span aria-current="page">{section === 'personal' ? 'Personal' : current}</span></nav><h1>{title}</h1><p className="studio-description">{description}</p></div>{actions && <div className="studio-head-actions">{actions}</div>}</header>
      {children}
    </div>
  </main>;
}

export function PreviewControls({ title, children, note }: { title: string; children: ReactNode; note?: string }) {
  return <details className="studio-preview"><summary><span className="studio-preview-label"><FlaskConical size={16} aria-hidden="true" /><strong>{title}</strong><span>Sample data only</span></span><span className="studio-preview-toggle">Preview controls <ChevronDown size={15} aria-hidden="true" /></span></summary><div className="studio-preview-content"><p>{note || 'Changes stay in this preview. No live accounts or external services are changed.'}</p><div className="studio-preview-actions">{children}</div></div></details>;
}

export function WorkspaceMetrics({ items }: { items: { label: string; value: ReactNode; detail?: string; Icon: LucideIcon }[] }) {
  return <div className="studio-metrics" role="group" aria-label="Workspace metrics">{items.map(({ label, value, detail }) => <Card className="studio-metric" key={label}><CardHeader><span>{label}</span></CardHeader><CardContent><strong>{value}</strong>{detail&&<p>{detail}</p>}</CardContent></Card>)}</div>;
}
