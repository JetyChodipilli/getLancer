import type { Metadata } from 'next';
import { redirect } from 'next/navigation';
import AdminDashboard from '@/app/components/admin-dashboard';
import { backendOrigin } from '@/lib/server';
import '../../admin/dashboard.css';
import { sampleDashboard } from './sample-dashboard';

export const metadata: Metadata = {
  title: 'Admin Dashboard Demo',
  robots: { index: false, follow: false },
};

export default async function AdminPreviewPage() {
  if (await backendOrigin()) redirect('/admin');
  return <AdminDashboard samples={{ 4: sampleDashboard(4), 12: sampleDashboard(12) }} />;
}
