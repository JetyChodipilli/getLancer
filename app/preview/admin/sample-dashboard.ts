import type { DashboardSnapshot, DashboardWeeks } from '@/lib/admin-dashboard';

const counts = [12, 16, 15, 18, 20, 23, 22, 26, 24, 29, 33, 38];
const lastMonday = Date.UTC(2026, 10, 16);
const weekMs = 604_800_000;

export function sampleDashboard(weeks: DashboardWeeks): DashboardSnapshot {
  const firstMonday = lastMonday - (weeks - 1) * weekMs;
  const from = new Date(firstMonday).toISOString();
  const trend = counts.slice(-weeks).map((qualified, index) => ({
    start: new Date(firstMonday + index * weekMs).toISOString().slice(0, 10),
    qualified,
  }));
  const qualified = trend.reduce((sum, week) => sum + week.qualified, 0);
  const [hired, completed, responded, eligible] = weeks === 4 ? [16, 9, 72, 100] : [34, 24, 175, 252];

  return {
    window: { weeks, from, asOf: '2026-11-22T12:00:00Z' },
    trend,
    outcomes: {
      qualified, hired, completed, responded, eligible,
      responsePercent: 100 * responded / eligible,
      medianResponseHours: weeks === 4 ? 11.4 : 13.2,
      inquiryToHirePercent: 100 * hired / qualified,
      hireToCompletionPercent: 100 * completed / hired,
    },
    queues: [
      { key: 'projects', count: 7, oldest: '2026-11-20T09:00:00Z', severity: null },
      { key: 'reports', count: 3, oldest: '2026-11-21T10:00:00Z', severity: 'HIGH' },
      { key: 'disputes', count: 2, oldest: '2026-11-19T09:00:00Z', severity: null },
      { key: 'payments', count: 4, oldest: '2026-11-21T13:00:00Z', severity: null },
      { key: 'email', count: 1, oldest: '2026-11-21T14:00:00Z', severity: null },
    ],
    catalog: { activeShowcases: 186, demosUnreachable: 5 },
    commerce: [
      { product: 'Milestone payments', mode: 'test', currency: 'INR', capturedMinor: weeks === 4 ? '13250000' : '34750000', refundedMinor: '650000' },
      { product: 'Source purchases', mode: 'test', currency: 'INR', capturedMinor: weeks === 4 ? '1840000' : '4610000', refundedMinor: '120000' },
    ],
    readiness: { runtimeEnabled: false, livePaymentsEnabled: false, productionCertified: false },
  };
}
