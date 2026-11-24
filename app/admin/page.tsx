import type { Metadata } from 'next';
import AdminDashboard from '@/app/components/admin-dashboard';
import './dashboard.css';

export const metadata: Metadata = {
  title: 'Admin Dashboard',
  robots: { index: false, follow: false },
};

export default function AdminPage() {
  return <AdminDashboard />;
}
