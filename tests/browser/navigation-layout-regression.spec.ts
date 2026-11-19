import { expect, test } from '@playwright/test';

test('Labs card copy fills its column and stays readable on the public page', async ({ page }) => {
  await page.goto('/labs');
  const heading = page.getByRole('heading', { name: 'Certified labs', exact: true });
  const header = heading.locator('..');
  await expect(heading).toBeVisible();
  const layout = await header.evaluate(node => {
    const title = node.querySelector('h2')!;
    const description = node.querySelector('p')!;
    return { title: title.clientWidth, description: description.clientWidth, stacked: description.offsetTop > title.offsetTop };
  });
  expect(layout).toMatchObject({ stacked: true });
  expect(layout.title).toBeGreaterThan(150);
  expect(layout.description).toBeGreaterThan(150);
});

test('the public home pages offer one Back to top and retain search in the dock', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  for (const path of ['/', '/components', '/college-projects', '/labs']) {
    await page.goto(path);
    const control = page.getByRole('button', { name: 'Back to top', exact: true });
    await expect(control).toHaveCount(0);
    await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight));
    await expect(control).toHaveCount(1);
    await expect(control).toBeInViewport();
    if (path === '/') {
      await expect(page.getByRole('textbox', { name: 'Search projects while browsing' })).toBeInViewport();
      await expect(page.getByRole('link', { name: 'Search projects', exact: true })).toHaveCount(0);
      await expect(page.getByRole('button', { name: 'Submit project search' })).toBeInViewport();
    }
    await control.focus();
    await page.keyboard.press('Enter');
    await expect.poll(() => page.evaluate(() => window.scrollY)).toBe(0);
    await expect(control).toHaveCount(0);
  }
});
