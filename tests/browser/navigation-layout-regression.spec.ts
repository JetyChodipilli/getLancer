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

test('workspace directories put category headings above full-width links on phones', async ({page}) => {
  await page.setViewportSize({width: 375, height: 812});
  await page.goto('/preview/areas/trust');
  const heading = page.getByRole('heading', {name: 'Reliability', exact: true});
  const link = heading.locator('..').getByRole('link', {name: /Trust & reliability/});
  await expect(heading).toBeVisible();
  const [title, row] = await Promise.all([heading.boundingBox(), link.boundingBox()]);
  expect(title!.width).toBeGreaterThan(200);
  expect(row!.y).toBeGreaterThanOrEqual(title!.y + title!.height);
});

test('workspace pointer menus close off the navbar and keep the current area distinct', async ({page, hasTouch}) => {
  await page.emulateMedia({reducedMotion: 'reduce'});
  await page.goto('/preview/teams?tab=activity');
  const nav = page.getByRole('navigation', {name: 'Workspace navigation', exact: true});
  const teams = nav.getByRole('button', {name: 'Teams', exact: true});
  const clients = nav.getByRole('button', {name: 'Clients', exact: true});
  const trust = nav.getByRole('button', {name: 'Trust', exact: true});
  const panel = nav.getByRole('region', {name: 'Clients tools', exact: true});
  await expect(teams).toHaveAttribute('aria-current', 'page');
  await expect(teams).toHaveCSS('text-decoration-line', 'underline');
  await expect(teams).toHaveCSS('background-color', 'rgba(0, 0, 0, 0)');

  if (hasTouch) await clients.tap();
  else await clients.hover();
  await expect(panel).toBeVisible();
  await expect(clients).toHaveAttribute('aria-expanded', 'true');
  await expect(clients).not.toHaveCSS('background-color', 'rgba(0, 0, 0, 0)');
  await expect(teams).toHaveCSS('background-color', 'rgba(0, 0, 0, 0)');
  await expect(teams).toHaveAttribute('aria-current', 'page');
  await expect(teams).toHaveCSS('text-decoration-line', 'none');
  await expect(teams).toHaveCSS('color', await trust.evaluate(node => getComputedStyle(node).color));

  const viewport = page.viewportSize()!;
  if (hasTouch) {
    await page.touchscreen.tap(viewport.width - 8, viewport.height - 8);
  } else {
    await panel.getByRole('link', {name: 'Business hiring'}).hover();
    await expect(panel).toBeVisible();
    await page.mouse.move(viewport.width - 8, viewport.height - 8);
  }
  await expect(panel).toBeHidden();
  await expect(clients).toHaveAttribute('aria-expanded', 'false');
  await expect(clients).toHaveCSS('background-color', 'rgba(0, 0, 0, 0)');
  await expect(teams).toHaveCSS('text-decoration-line', 'underline');

  if (!hasTouch) {
    // Hovering another trigger must leave a click-opened panel as the only highlighted menu.
    await clients.click();
    await trust.hover();
    await expect(nav.locator('button[aria-expanded=true]')).toHaveCount(1);
    await expect(clients).toHaveAttribute('aria-expanded', 'true');
    await expect(trust).toHaveCSS('color', await teams.evaluate(node => getComputedStyle(node).color));
    await expect(trust).toHaveCSS('background-color', 'rgba(0, 0, 0, 0)');
  }
});

test('public navigation reserves the filled highlight for the current page', async ({page, hasTouch}) => {
  test.skip(hasTouch, 'Hover coverage requires a desktop pointer.');
  await page.setViewportSize({width: 1440, height: 900});
  await page.goto('/');
  const nav = page.getByRole('navigation', {name: 'Main navigation', exact: true});
  const projects = nav.getByRole('link', {name: 'Projects', exact: true});
  const components = nav.getByRole('link', {name: 'Components', exact: true});
  await components.hover();
  await expect(projects).toHaveAttribute('aria-current', 'page');
  await expect(projects).not.toHaveCSS('background-color', 'rgba(0, 0, 0, 0)');
  await expect(components).toHaveCSS('background-color', 'rgba(0, 0, 0, 0)');
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
