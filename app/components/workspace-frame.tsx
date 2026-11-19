import type { ReactNode } from 'react';
import Link from 'next/link';
import { ChevronDown, FlaskConical, type LucideIcon } from 'lucide-react';
import { Card, CardContent, CardHeader } from '@/components/ui/card';
import { cn } from '@/lib/utils';

type Section = 'personal' | 'clients' | 'trust-directory' | 'trust' | 'teams' | 'business' | 'delivery' | 'templates' | 'maintenance' | 'hosting' | 'components' | 'college' | 'slots' | 'labs' | 'operations';
export function WorkspaceFrame({ children, title, description, section, preview = false, actions, className }: {
  children: ReactNode; title: string; description: string; section: Section;
  preview?: boolean; actions?: ReactNode; className?: string;
}) {
  const home = preview ? '/preview/workspace' : '/workspace';
  return <main id="main" className={cn('studio-shell paper-studio', className)} data-workspace={section}>
    <div className="studio-body">
      <header className="studio-page-head"><div><nav className="studio-breadcrumb" aria-label="Breadcrumb"><Link prefetch={false} href={home}>Workspace</Link><span aria-hidden="true">/</span><span aria-current="page">{section === 'personal' ? 'Personal' : title}</span></nav><h1>{title}</h1><p className="studio-description">{description}</p></div>{actions && <div className="studio-head-actions">{actions}</div>}</header>
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
