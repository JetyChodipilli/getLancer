import {expect, test} from '@playwright/test';

test('the Saved projects page offers a working demo shortlist when accounts are unavailable', async ({page}) => {
  await page.goto('/saved');
  const notice = page.getByRole('alert');
  await expect(notice).toContainText('Saved projects are unavailable in this preview.');
  await expect(page.getByRole('button', {name: 'Retry loading saved projects'})).toHaveCount(0);
  await notice.getByRole('link', {name: 'Open the demo shortlist'}).click();
  await expect(page.getByRole('tab', {name: /^Saved \(/})).toHaveAttribute('aria-selected', 'true');
});

for (const path of ['/login', '/signup']) {
  test(`${path} provides a demo link when account services are unavailable`, async ({page}) => {
    await page.goto(path);
    const notice = page.getByRole('status').filter({hasText: 'Sign-in is unavailable in this preview.'});
    await expect(notice).toBeVisible();
    await expect(notice.getByRole('button', {name: 'Check again'})).toHaveCount(0);
    await notice.getByRole('link', {name: 'Explore the demo'}).click();
    await expect(page).toHaveURL(/\/preview\/workspace$/);
  });
}

test('a temporary sign-in outage can be retried', async ({page}) => {
  let attempts = 0;
  await page.route('**/api/v1/auth/providers', route => {
    if (++attempts === 1) return route.fulfill({status: 503, json: {error: {code: 'UNAVAILABLE', message: 'Unavailable'}}});
    return route.fulfill({json: {google: false, github: false}});
  });
  await page.goto('/login');
  const notice = page.getByRole('status').filter({hasText: 'Account services are temporarily unavailable.'});
  await notice.getByRole('button', {name: 'Check again'}).click();
  await expect(page.getByRole('button', {name: 'Log in', exact: true})).toBeEnabled();
  expect(attempts).toBe(2);
});

test('a saved Publish link opens Showcases for a connected account', async ({page}) => {
  await page.route('**/api/v1/**', route => {
    if (new URL(route.request().url()).pathname === '/api/v1/me') return route.fulfill({json: {
      id: '44444444-4444-4444-8444-444444444444', email: 'builder@example.test', displayName: 'Builder',
      roles: ['DEVELOPER'], emailVerified: true, profile: {approval_status: 'APPROVED'},
    }});
    return route.fulfill({json: {items: [], page: 0, size: 50, hasMore: false, totalItems: 0, totalPages: 0}});
  });
  await page.goto('/workspace?tab=projects');
  await expect(page.getByRole('tab', {name: 'Showcases', exact: true})).toHaveAttribute('aria-selected', 'true');
  await page.getByRole('button', {name: 'New draft', exact: true}).click();
  await expect(page.getByRole('dialog').getByRole('heading', {name: 'Create a showcase'})).toBeVisible();
});

test('workspace tool search applies input entered before scripts are ready', async ({page}) => {
  let releaseScripts!: () => void;
  const scriptsReady = new Promise<void>(resolve => { releaseScripts = resolve; });
  await page.route('**/*', async route => {
    if (route.request().resourceType() === 'script') await scriptsReady;
    await route.continue();
  });

  await page.goto('/preview/areas/clients', {waitUntil:'commit'});
  const main = page.getByRole('main');
  const search = main.getByRole('searchbox', {name:'Find a tool in Clients'});
  try {
    await search.fill('missing-tool');
  } finally {
    releaseScripts();
  }
  await expect(main.getByRole('status')).toHaveText('No matching tools.');
  await main.getByRole('button', {name:'Clear search'}).click();
  await expect(search).toBeFocused();
  await expect(main.getByRole('link', {name:/Delivery & payments/})).toBeVisible();
});
