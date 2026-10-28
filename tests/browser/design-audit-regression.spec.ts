import {expect, test, type Locator, type Page, type TestInfo} from '@playwright/test';
import {writeFileSync} from 'node:fs';
import {percentile} from '../acceptance-metrics.mjs';

async function attachJson(info: TestInfo, name: string, value: unknown) {
  const path = info.outputPath(name + '.json');
  writeFileSync(path, JSON.stringify(value, null, 2));
  await info.attach(name, {path, contentType: 'application/json'});
}

async function fits(page: Page, scope?: Locator) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(page.viewportSize()!.width + 1);
  if (scope) expect(await scope.evaluate(node => node.scrollWidth <= node.clientWidth + 1)).toBe(true);
}

async function wrappedText(node: Locator) {
  // scrollWidth catches clipping even when overflow:hidden keeps the document in bounds.
  expect(await node.evaluate(element => element.scrollWidth <= element.clientWidth + 1)).toBe(true);
  expect(await node.evaluate(element => {
    const range = document.createRange(); range.selectNodeContents(element);
    return new Set([...range.getClientRects()].map(rect => Math.round(rect.top))).size;
  })).toBeGreaterThan(1);
}

test('accepted unbroken community metadata wraps in actual component cards and detail headers', async ({page}) => {
  await page.emulateMedia({reducedMotion: 'reduce'});
  await page.goto('/components');
  await page.evaluate(() => document.fonts.ready);
  const card = page.locator('.kit-card').first();
  await expect(card).toBeVisible();
  // Adversarial display text is within ComponentService's title/summary limits.
  // Keep the actual production DOM, stylesheet cascade, links and preview intact.
  await card.locator('h2 a').evaluate(node => { node.textContent = 'W'.repeat(100); });
  await card.locator('.kit-card-copy > p').evaluate(node => { node.textContent = 'W'.repeat(240); });
  await card.locator('.kit-card-foot > span').first().evaluate(node => { node.textContent = 'W'.repeat(80); });
  await wrappedText(card.locator('h2'));
  await fits(page, card.locator('.kit-card-copy'));
  await fits(page, card.locator('.kit-card-foot'));
  await page.goto('/components/feedback-form');
  await page.evaluate(() => document.fonts.ready);
  const head = page.locator('.kit-detail-head');
  await head.locator('h1').evaluate(node => { node.textContent = 'W'.repeat(100); });
  await head.locator('p').first().evaluate(node => { node.textContent = 'W'.repeat(240); });
  await head.locator('.kit-credit').evaluate(node => { node.textContent = 'W'.repeat(80); });
  await wrappedText(head.locator('h1'));
  await fits(page, head);
  await expect(page.getByRole('button', {name: 'Download source', exact: true})).toBeVisible();
  // Wrapping prose must not remove the code pane's independent scrolling.
  await page.getByRole('tab', {name: 'Source', exact: true}).click();
  const code = page.locator('.kit-source-panel pre');
  await expect(code).toBeVisible();
  await expect(code).toHaveCSS('overflow-x', 'auto');
  await fits(page);
});

test('account menu preserves a complete unbroken display name inside its clipping boundary', async ({page}) => {
  await page.emulateMedia({reducedMotion: 'reduce'});
  await page.goto('/preview/workspace');
  const trigger = page.getByRole('button', {name: /^Account menu for /});
  await expect(trigger).toBeVisible();
  await trigger.click();
  const menu = page.locator('.account-popover');
  await expect(menu).toBeVisible();
  const name = menu.locator('.account-menu-heading strong');
  await name.evaluate(node => { node.textContent = 'W'.repeat(80); });
  await wrappedText(name);
  await fits(page, menu);
  await page.keyboard.press('Escape');
  await expect(menu).not.toBeVisible();
  await expect(trigger).toBeFocused();
});

test('public and workspace shells reflow at 320px with bounded local navigation strips', async ({page}, info) => {
  test.skip(info.project.name !== 'desktop', 'One dedicated narrow-width matrix covers all shells.');
  test.setTimeout(180000);
  await page.setViewportSize({width: 320, height: 812});
  await page.emulateMedia({reducedMotion: 'reduce'});
  const routes = ['/', '/components', '/college-projects', '/templates', '/report', '/login',
    '/preview/workspace', '/preview/components', '/preview/college-projects', '/preview/slots',
    '/preview/teams', '/preview/business', '/preview/delivery', '/preview/maintenance',
    '/preview/hosting', '/preview/templates', '/preview/trust', '/preview/operations'];
  const samples: unknown[] = [];
  for (const path of routes) {
    await test.step(path, async () => {
      await page.goto(path);
      await expect(page.locator('main h1')).toBeVisible();
      await page.evaluate(() => document.fonts.ready);
      // Confirm the real document has viewport metadata; fixtures must not silently
      // exercise a browser's wide default mobile layout viewport.
      await expect(page.locator('meta[name="viewport"]')).toHaveAttribute('content', /width=device-width/);
      await fits(page);
      samples.push({path, viewport: 320, documentWidth: await page.evaluate(() => document.documentElement.scrollWidth)});
      const rail = page.locator('.studio-rail nav');
      if (await rail.count()) {
        await expect(rail).toHaveCSS('overflow-x', 'auto');
        await rail.getByRole('link').last().focus();
        await expect(rail.getByRole('link').last()).toBeFocused();
        await fits(page);
      }
    });
  }
  await attachJson(info, 'cross-app-320px-reflow', samples);
});

type LabMetrics = {lcp: number; cls: number; interactionMs: number; inputSamples: number};
type LabWindow = Window & {designLab: LabMetrics};
type Shift = PerformanceEntry & {hadRecentInput: boolean; value: number};

test('collects repeatable mobile lab paint, layout and action timing across three workspace shells', async ({page}, info) => {
  test.skip(info.project.name !== 'desktop', 'Run the throttled sampling profile once.');
  test.setTimeout(240000);
  await page.setViewportSize({width: 390, height: 844});
  const session = await page.context().newCDPSession(page);
  await session.send('Emulation.setCPUThrottlingRate', {rate: 4});
  await session.send('Network.enable');
  await session.send('Network.setCacheDisabled', {cacheDisabled: true});
  await session.send('Network.emulateNetworkConditions', {offline: false, latency: 150, downloadThroughput: 1_600_000 / 8, uploadThroughput: 750_000 / 8});
  await page.addInitScript(() => {
    // Each navigation samples the same initial persona, including after the
    // previous sample switched to Leah. Other application storage is untouched.
    sessionStorage.removeItem('getlancer-uat-v1');
    const metrics: LabMetrics = {lcp: 0, cls: 0, interactionMs: 0, inputSamples: 0};
    (window as unknown as LabWindow).designLab = metrics;
    new PerformanceObserver(list => { for (const entry of list.getEntries()) metrics.lcp = entry.startTime; })
      .observe({type: 'largest-contentful-paint', buffered: true});
    new PerformanceObserver(list => { for (const entry of list.getEntries() as Shift[]) if (!entry.hadRecentInput) metrics.cls += entry.value; })
      .observe({type: 'layout-shift', buffered: true});
    // A trusted input through two animation frames measures a paint proxy. It is
    // deliberately not called INP, which requires a complete field session.
    for (const kind of ['keydown', 'pointerup']) addEventListener(kind, event => {
      if (!event.isTrusted) return;
      const start = performance.now();
      requestAnimationFrame(() => requestAnimationFrame(() => {
        metrics.inputSamples++;
        metrics.interactionMs = Math.max(metrics.interactionMs, performance.now() - start);
      }));
    }, {capture: true});
  });
  const samples: Record<string, LabMetrics[]> = {};
  try {
    for (const path of ['/preview/workspace', '/preview/business', '/preview/hosting']) {
      samples[path] = [];
      for (let sample = 0; sample < 3; sample++) {
        await page.goto(path);
        await expect(page.locator('main h1')).toBeVisible();
        await page.evaluate(() => document.fonts.ready);
        await expect.poll(() => page.evaluate(() => (window as unknown as LabWindow).designLab.lcp)).toBeGreaterThan(0);
        if (path === '/preview/workspace') {
          await page.getByRole('group', {name: 'Demo roles', exact: true}).getByRole('button', {name: /Builder Leah/}).click();
          await expect(page.getByRole('tab', {name: 'Showcases', exact: true})).toHaveAttribute('aria-selected', 'true');
        } else {
          const name = path === '/preview/business' ? 'New project request' : 'Copy bundled sample';
          await page.getByRole('button', {name, exact: true}).click();
          await expect(page.getByRole('dialog')).toBeVisible();
        }
        await expect.poll(() => page.evaluate(() => (window as unknown as LabWindow).designLab.inputSamples)).toBeGreaterThan(0);
        const measured = await page.evaluate(() => (window as unknown as LabWindow).designLab);
        expect(Number.isFinite(measured.lcp) && measured.lcp > 0).toBe(true);
        expect(Number.isFinite(measured.cls) && measured.cls >= 0).toBe(true);
        expect(Number.isFinite(measured.interactionMs) && measured.interactionMs > 0).toBe(true);
        samples[path].push(measured);
      }
    }
  } finally {
    await session.detach();
    const summary = Object.fromEntries(Object.entries(samples).filter(([, rows]) => rows.length).map(([path, rows]) => [path, {
      samples: rows.length, lcpP75Ms: percentile(rows.map(row => row.lcp)), clsP75: percentile(rows.map(row => row.cls)),
      inputToPaintProxyP75Ms: percentile(rows.map(row => row.interactionMs)),
    }]));
    await attachJson(info, 'cross-app-mobile-lab', {
      profile: {viewport: '390x844', cpuSlowdown: 4, latencyMs: 150, downloadKbps: 1600, uploadKbps: 750, cache: 'disabled', samplesPerRoute: 3},
      scope: 'CI browser lab evidence using local sample data. Input-to-paint proxy is not field INP. No before/after improvement is asserted; these additional shells establish a baseline, not a new performance acceptance gate.',
      samples, summary,
    });
  }
});
