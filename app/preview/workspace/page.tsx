import WorkspacePreview from './workspace-preview';
import { backendOrigin } from '@/lib/server';
import { redirect } from 'next/navigation';

export const metadata = { title: 'Interactive workspace preview', robots: { index: false, follow: false }, alternates: { canonical: null } };

export default async function Page({ searchParams }: {
  searchParams: Promise<{ view?: string | string[]; tab?: string | string[]; project?: string | string[] }>;
}) {
  if (await backendOrigin()) redirect('/workspace');
  const {view, project, tab} = await searchParams;
  const startView = view === 'moderation' || view === 'publishing' || view === 'saved' || view === 'inquiry' || view === 'report' || view === 'project'
    ? view : view === undefined && tab === 'projects' ? 'publishing' : undefined;
  const projectSlug = (startView === 'inquiry' || startView === 'report' || startView === 'project') && typeof project === 'string' ? project : undefined;
  return <WorkspacePreview key={JSON.stringify([startView, projectSlug])} startView={startView} projectSlug={projectSlug} />;
}
