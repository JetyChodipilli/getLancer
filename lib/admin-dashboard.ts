export type DashboardWeeks = 4 | 12;

export type DashboardSnapshot = {
  window: { weeks: DashboardWeeks; from: string; asOf: string };
  outcomes: {
    qualified: number; hired: number; completed: number;
    responded: number; eligible: number; responsePercent: number | null;
    medianResponseHours: number | null; inquiryToHirePercent: number | null;
    hireToCompletionPercent: number | null;
  };
  trend: { start: string; qualified: number }[];
  queues: { key: string; count: number; oldest: string | null; severity: 'CRITICAL' | 'HIGH' | null }[];
  catalog: { activeShowcases: number; demosUnreachable: number };
  commerce: { product: string; mode: 'live' | 'test'; currency: string; capturedMinor: string; refundedMinor: string }[];
  readiness: { runtimeEnabled: boolean; livePaymentsEnabled: boolean; productionCertified: boolean } | null;
};

const count = (value: number) => Number.isSafeInteger(value) && value >= 0;
const amount = (value: string) => typeof value === 'string' && /^[0-9]{1,30}$/.test(value);
const date = (value: string) => typeof value === 'string' && Number.isFinite(Date.parse(value));
const measure = (value: number | null, maximum = Infinity) =>
  value === null || (Number.isFinite(value) && value >= 0 && value <= maximum);

export const queueKeys = ['projects', 'reports', 'disputes', 'payments', 'email'] as const;

export function dashboardSnapshot(value: unknown, weeks: DashboardWeeks): DashboardSnapshot {
  const data = value as DashboardSnapshot;
  const outcome = data?.outcomes;
  const readiness = data?.readiness;
  if (!data?.window || data.window.weeks !== weeks || !date(data.window.from) || !date(data.window.asOf)
      || !outcome || ![outcome.qualified, outcome.hired, outcome.completed, outcome.eligible, outcome.responded].every(count)
      || outcome.completed > outcome.hired || outcome.hired > outcome.qualified || outcome.eligible > outcome.qualified
      || outcome.responded > outcome.eligible || !measure(outcome.responsePercent, 100)
      || !measure(outcome.inquiryToHirePercent, 100) || !measure(outcome.hireToCompletionPercent, 100)
      || !measure(outcome.medianResponseHours)
      || !Array.isArray(data.trend) || data.trend.length !== weeks
      || !data.trend.every((week, index) => week && date(week.start) && count(week.qualified)
        && Date.parse(week.start) === Date.parse(data.window.from) + index * 604_800_000)
      || data.trend.reduce((total, week) => total + week.qualified, 0) !== outcome.qualified
      || !Array.isArray(data.queues) || data.queues.length !== queueKeys.length
      || !queueKeys.every(key => data.queues.some(queue => queue?.key === key))
      || !data.queues.every(queue => queue && count(queue.count) && (queue.oldest === null || date(queue.oldest))
        && [null, 'CRITICAL', 'HIGH'].includes(queue.severity))
      || !data.catalog || !count(data.catalog.activeShowcases) || !count(data.catalog.demosUnreachable)
      || !Array.isArray(data.commerce) || data.commerce.length > 4
      || !data.commerce.every(row => row && ['Milestone payments', 'Source purchases'].includes(row.product)
        && ['test', 'live'].includes(row.mode) && row.currency === 'INR' && amount(row.capturedMinor) && amount(row.refundedMinor))
      || !(readiness === null || (readiness && typeof readiness.runtimeEnabled === 'boolean'
        && typeof readiness.livePaymentsEnabled === 'boolean' && typeof readiness.productionCertified === 'boolean'))) {
    throw new Error('The dashboard response could not be verified. Refresh to try again.');
  }
  return data;
}
