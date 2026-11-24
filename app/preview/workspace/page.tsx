import WorkspacePreview from './workspace-preview';
import { backendOrigin } from '@/lib/server';
import { redirect } from 'next/navigation';

export const metadata = { title: 'Interactive workspace preview', robots: { index: false, follow: false }, alternates: { canonical: null } };

export default async function Page({ searchParams }: { searchParams: Promise<{ view?: string | string[] }> }) {
  if (await backendOrigin()) redirect('/workspace');
  return <WorkspacePreview startInModeration={(await searchParams).view === 'moderation'} />;
}
