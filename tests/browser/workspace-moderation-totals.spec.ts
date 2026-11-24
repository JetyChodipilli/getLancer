import { expect, test } from '@playwright/test';

test('live moderation cards use complete account-bound queue totals', async ({ page }) => {
  const admin = '44444444-4444-4444-8444-444444444444';
  const empty = { items: [], page: 0, size: 50, hasMore: false, totalItems: 0, totalPages: 0 };
  let countUnavailable = false;
  let isAdmin = true;
  const countRequests: string[] = [];

  await page.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace('/api/v1', '');
    if (path === '/me') return route.fulfill({ json: {
      id: admin, email: 'operator@example.test', displayName: 'Operator', roles: isAdmin ? ['ADMIN', 'DEVELOPER'] : ['DEVELOPER'],
      emailVerified: true, profile: { approval_status: 'APPROVED' },
    } });
    if (path === '/admin/metrics' && url.searchParams.get('section') === 'moderation') {
      expect(route.request().headers()['x-getlancer-account']).toBe(admin);
      countRequests.push(path);
      return countUnavailable
        ? route.fulfill({ status: 503, json: { error: { code: 'UNAVAILABLE', message: 'Moderation totals are temporarily unavailable.' } } })
        : route.fulfill({ json: { projects: 87, profiles: 62, reviews: 71, reports: 13 } });
    }
    if (path === '/admin/metrics') return route.fulfill({ json: { weekly: [], failedEmails: 0 } });
    if (path === '/admin/reports' && !url.searchParams.has('status')) return route.fulfill({ json: {
      ...empty,
      items: Array.from({ length: 50 }, (_, i) => ({ id: 'closed-' + i, status: 'RESOLVED', reason: 'SPAM', detail: 'Closed report' })),
      totalItems: 63, totalPages: 2, hasMore: true,
    } });
    if (path === '/admin/products/pending') return route.fulfill({ json: {
      ...empty,
      items: Array.from({ length: 50 }, (_, i) => ({ id: 'project-' + i, title: 'Project ' + i, approvalStatus: 'PENDING_REVIEW' })),
      totalItems: 87, totalPages: 2, hasMore: true,
    } });
    if (path === '/admin/profiles/pending') return route.fulfill({ json: { ...empty, totalItems: 62, totalPages: 2, hasMore: true } });
    if (path === '/admin/reviews') return route.fulfill({ json: { ...empty, totalItems: 71, totalPages: 2, hasMore: true } });
    return route.fulfill({ json: empty });
  });

  await page.goto('/workspace');
  const summary = page.getByRole('group', { name: 'Moderation summary' });
  const count = (name: RegExp) => summary.getByRole('button', { name }).locator('strong');
  await expect(count(/^Projects to review/)).toHaveText('87');
  await expect(count(/^Builder profiles/)).toHaveText('62');
  await expect(count(/^Reviews to publish/)).toHaveText('71');
  await expect(count(/^Open reports/)).toHaveText('13');
  expect(countRequests).toHaveLength(1);

  countUnavailable = true;
  await page.reload();
  await expect(count(/^Projects to review/)).toHaveText('Unavailable');
  await expect(count(/^Open reports/)).toHaveText('Unavailable');
  expect(countRequests).toHaveLength(2);

  isAdmin = false;
  await page.reload();
  await expect(page.getByRole('group', { name: 'Workspace summary' })).toBeVisible();
  await expect(summary).toHaveCount(0);
  expect(countRequests).toHaveLength(2);
});
