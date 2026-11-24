import { expect, test } from '@playwright/test';

const admin = '44444444-4444-4444-8444-444444444444';
const february = Date.UTC(2026, 1, 23);

function dashboard(weeks: number) {
  const start = february - (weeks - 1) * 604_800_000;
  return {
    window: { weeks, from: new Date(start).toISOString(), asOf: new Date(february + 3_600_000).toISOString() },
    outcomes: { qualified: weeks, hired: 1, completed: 1, responded: 1, eligible: 2, responsePercent: 50,
      medianResponseHours: 4, inquiryToHirePercent: 25, hireToCompletionPercent: 100 },
    trend: Array.from({ length: weeks }, (_, index) =>
      ({ start: new Date(start + index * 604_800_000).toISOString().slice(0, 10), qualified: 1 })),
    queues: ['projects', 'reports', 'disputes', 'payments', 'email'].map(key =>
      ({ key, count: key === 'reports' ? 7 : 0, oldest: null, severity: key === 'reports' ? 'HIGH' : null })),
    catalog: { activeShowcases: 7, demosUnreachable: 2 },
    commerce: [{ product: 'Source purchases', mode: 'test', currency: 'INR', capturedMinor: '160000', refundedMinor: '2000' }],
    readiness: { runtimeEnabled: false, livePaymentsEnabled: false, productionCertified: false },
  };
}

test('admin dashboard reuses the current account and keeps the trend, period and queue links usable', async ({ page }) => {
  let forbidden = false;
  let viewer = false;
  const observed: number[] = [];
  await page.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url());
    if (url.pathname === '/api/v1/me') {
      return route.fulfill({ json: { id: admin, roles: [viewer ? 'CLIENT' : 'ADMIN'] } });
    }
    if (url.pathname !== '/api/v1/admin/dashboard') return route.fulfill({ json: {} });
    expect(route.request().method()).toBe('GET');
    expect(route.request().headers()['x-getlancer-account']).toBe(admin);
    const weeks = Number(url.searchParams.get('weeks'));
    observed.push(weeks);
    return forbidden
      ? route.fulfill({ status: 403, json: { error: { code: 'FORBIDDEN', message: 'Administrator access was revoked.' } } })
      : route.fulfill({ json: dashboard(weeks) });
  });

  await page.goto('/admin');
  await expect(page.getByRole('heading', { name: 'Admin Dashboard' })).toBeVisible();
  await expect(page.getByRole('navigation', { name: 'Administration' })).toBeVisible();
  await expect(page.getByText('1 of 2 eligible')).toBeVisible();
  await expect(page.getByRole('link', { name: /Unresolved reports/ })).toHaveAttribute(
    'href', '/workspace?tab=admin&queue=reports#moderation-reports');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.getByText('Weekly counts', { exact: true }).focus();
  await page.keyboard.press('Space');
  await expect(page.getByRole('table', { name: 'Weeks starting Monday, UTC' }).getByRole('row')).toHaveCount(5);

  await page.getByLabel('Dashboard period').selectOption('12');
  await expect(page.locator('.admin-kpis')).toContainText('12');
  await expect(page.getByRole('link', { name: /Unresolved reports/ })).toContainText('7');
  expect(observed).toEqual([4, 12]);

  forbidden = true;
  await page.getByRole('button', { name: 'Refresh' }).click();
  await expect(page.getByRole('alert')).toContainText('Administrator access was revoked.');
  await expect(page.getByText('1 of 2 eligible')).toHaveCount(0);
  await expect(page.getByRole('navigation', { name: 'Administration' })).toHaveCount(0);

  viewer = true;
  await page.reload();
  await expect(page.getByRole('alert')).toContainText('Administrator access is required.');
  expect(observed).toEqual([4, 12, 12]);
});

test('dashboard shortcuts open the filtered moderation queue and existing recovery tools', async ({ page }) => {
  const requested: string[] = [];
  await page.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url());
    const empty = { page: 0, size: 50, hasMore: false, totalItems: 0, totalPages: 0, items: [] };
    if (url.pathname === '/api/v1/me') return route.fulfill({ json: {
      id: admin, roles: ['ADMIN'], email: 'operator@example.test', displayName: 'Operator', emailVerified: true,
      activeSlotLimit: 3, profile: { approval_status: 'APPROVED' },
    } });
    if (url.pathname === '/api/v1/admin/dashboard') return route.fulfill({ json: dashboard(4) });
    if (url.pathname === '/api/v1/admin/metrics') return route.fulfill({ json: { weekly: [], failedEmails: 0 } });
    if (url.pathname === '/api/v1/admin/reports' || url.pathname === '/api/v1/admin/payments/attention') {
      requested.push(url.pathname + url.search);
    }
    return route.fulfill({ json: { ...empty, totalOwned: 0, activeCount: 0 } });
  });
  await page.goto('/admin');
  await page.getByRole('link', { name: /Unresolved reports/ }).click();
  await expect(page.getByRole('heading', { name: 'Reports', exact: true })).toBeVisible();
  expect(requested).toContain('/api/v1/admin/reports?status=UNRESOLVED');
  await page.getByRole('link', { name: 'Admin Dashboard', exact: true }).last().click();
  await page.getByRole('link', { name: /Payment checks/ }).click();
  await expect(page.getByRole('heading', { name: 'Orders requiring attention' })).toBeVisible();
  await expect.poll(() => requested.some(path => path.startsWith('/api/v1/admin/payments/attention?'))).toBe(true);
});
