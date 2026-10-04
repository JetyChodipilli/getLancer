import {expect, test, type Locator, type Page, type TestInfo} from '@playwright/test';
import {createTeamDemo} from '../../lib/team-demo';

async function fits(page: Page) {
  const width = page.viewportSize()!.width;
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth))
    .toBeLessThanOrEqual(width + 1);
  const shell = await page.locator('main:visible').boundingBox();
  expect(shell!.x).toBeGreaterThanOrEqual(-1);
  expect(shell!.x + shell!.width).toBeLessThanOrEqual(width + 1);
}

async function settle(page: Page) {
  await page.evaluate(async () => {
    await document.fonts.ready;
    const finite = document.getAnimations().filter(animation => {
      const timing = animation.effect?.getComputedTiming();
      const target = animation.effect instanceof KeyframeEffect ? animation.effect.target : null;
      return timing && Number.isFinite(timing.endTime) && animation.playState === 'running'
        && target instanceof Element && target.checkVisibility({visibilityProperty: true});
    });
    await Promise.all(finite.map(animation => animation.finished.catch(() => {})));
    await new Promise<void>(resolve => requestAnimationFrame(() => resolve()));
  });
}

async function evidence(page: Page, info: TestInfo, name: string) {
  await settle(page);
  const path = info.outputPath(`liquid-${name}-${page.viewportSize()!.width}.jpg`);
  await page.screenshot({path, fullPage: true, type: 'jpeg', quality: 90});
  await info.attach(name, {path, contentType: 'image/jpeg'});
}

async function material(surface: Locator) {
  return surface.evaluate(node => {
    const css = getComputedStyle(node);
    return {
      fill: css.backgroundColor,
      border: css.borderTopColor,
      borderWidth: parseFloat(css.borderTopWidth),
      radius: parseFloat(css.borderTopLeftRadius),
      shadow: css.boxShadow,
      blur: css.backdropFilter,
    };
  });
}

function alpha(color: string) {
  if (color === 'transparent') return 0;
  const rgba = color.match(/^rgba\([^,]+,[^,]+,[^,]+,\s*([\d.]+)\)$/);
  return rgba ? Number(rgba[1]) : 1;
}

async function chrome(surface: Locator) {
  const css = await material(surface);
  expect(alpha(css.fill)).toBeGreaterThanOrEqual(.65);
  expect(alpha(css.fill)).toBeLessThanOrEqual(.85);
  expect(css.borderWidth).toBeGreaterThanOrEqual(1);
  expect(css.shadow).not.toBe('none');
  const blur = css.blur.match(/blur\(([\d.]+)px\)/);
  expect(blur, `Chrome needs a restrained blur, found ${css.blur}`).not.toBeNull();
  expect(Number(blur![1])).toBeGreaterThanOrEqual(12);
  expect(Number(blur![1])).toBeLessThanOrEqual(18);
}

async function glassCard(surface: Locator) {
  const css = await material(surface);
  expect(alpha(css.fill)).toBeGreaterThanOrEqual(.78);
  expect(alpha(css.fill)).toBeLessThanOrEqual(.9);
  expect(css.borderWidth).toBeGreaterThanOrEqual(1);
  expect(css.radius).toBeGreaterThanOrEqual(16);
  expect(css.shadow).not.toBe('none');
  expect(css.border).not.toBe('rgba(0, 0, 0, 0)');
}

async function fieldStyle(field: Locator) {
  return field.evaluate(node => {
    const css = getComputedStyle(node), box = node.getBoundingClientRect();
    // Layout offsets are independent of native document, dialog and visual-viewport scrolling.
    const field = node as HTMLElement;
    return {
      x: field.offsetLeft, y: field.offsetTop, width: box.width, height: box.height,
      fill: css.backgroundColor, font: parseFloat(css.fontSize), shadow: css.boxShadow,
      outline: css.outlineStyle, outlineWidth: css.outlineWidth,
      outlineColor: css.outlineColor, outlineOffset: css.outlineOffset,
      border: css.borderTopColor,
    };
  });
}

async function stableKeyboardFocus(page: Page, field: Locator, previousKey = 'Shift+Tab') {
  const before = await fieldStyle(field);
  await page.keyboard.press(previousKey);
  await expect(field).toBeFocused();
  const after = await fieldStyle(field);
  for (const key of ['x', 'y', 'width', 'height'] as const) {
    expect(after[key], `Focus must preserve field ${key}`).toBeCloseTo(before[key], 1);
  }
  expect(after.font).toBeGreaterThanOrEqual(16);
  expect(after.height).toBeGreaterThanOrEqual(44);
  expect(after.outline).toBe('solid');
  expect(after.outlineWidth).toBe('2px');
  expect(after.outlineOffset).toBe('2px');
  expect(after.outlineColor).toBe('rgb(156, 45, 15)');
  expect(after.fill).toBe(before.fill);
  // A second focus ring would change the shadow as well as the one outline.
  expect(after.shadow).toBe(before.shadow);
}

const previews = [
  {route: 'workspace', title: 'Discover projects', ready: '.demo-card', card: '.demo-card'},
  {route: 'teams', title: 'Teams & studios', ready: '.team-context', card: '.studio-metric'},
  {route: 'business', title: 'Business hiring', ready: '.business-brief-panel', card: '.business-brief-panel'},
  {route: 'templates', title: 'Templates & purchases', ready: '.commerce-template-detail', card: '.commerce-template-detail > .commerce-section'},
  {route: 'delivery', title: 'Delivery & payments', ready: '.delivery-detail', card: '.delivery-detail'},
  {route: 'maintenance', title: 'Maintenance & support', ready: '.care-current', card: '.care-current'},
  {route: 'hosting', title: 'Hosted frontend demos', ready: '.hosting-current', card: '.hosting-current'},
  {route: 'trust', title: 'Trust & availability', ready: '.trust-split', card: '.trust-split > .panel'},
] as const;

test('every sample workspace renders shared glass, readable records and contained responsive chrome', async ({page}, info) => {
  test.setTimeout(90000);
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  for (const preview of previews) {
    await page.goto('/preview/' + preview.route);
    await expect(page.locator('.studio-page-head h1')).toHaveText(preview.title);
    await expect(page.locator(preview.ready).first()).toBeVisible();
    await expect(page.locator('.studio-preview')).toContainText('Sample data only');
    await settle(page);
    await fits(page);
    if (preview.route === 'workspace') {
      const search = page.locator('.browse-search-submit');
      await expect(search).toBeVisible();
      await expect(search).toHaveCSS('background-color', 'rgb(198, 56, 16)');
      expect((await search.boundingBox())!.height).toBeGreaterThanOrEqual(44);
    }
    await chrome(page.locator('.studio-preview'));
    await glassCard(page.locator(preview.card).first());
    expect(await page.locator('.studio-shell').evaluate(node => node.getAnimations({subtree: true})
      .some(animation => animation.effect?.getTiming().iterations === Infinity)),
    'Dashboard content must settle without infinite record or text motion').toBe(false);
    const dense = page.locator('.studio-person-row, .team-row, .business-member, .commerce-version, .delivery-milestone, .care-request, .hosting-manifest li');
    for (const row of await dense.all()) {
      expect((await material(row)).blur, 'Nested records should not stack backdrop filters').toBe('none');
    }
    await evidence(page, info, `preview-${preview.route}`);
    if (preview.route === 'maintenance') {
      const create = page.getByRole('button', {name: 'Create support offer', exact: true});
      await create.click();
      const dialog = page.getByRole('dialog');
      const price = page.getByLabel('Monthly price (INR)', {exact: true});
      const quota = page.getByLabel('Requests per paid period', {exact: true});
      await expect(price).toBeVisible();
      await settle(page);
      const a = (await price.boundingBox())!, b = (await quota.boundingBox())!;
      if (page.viewportSize()!.width > 640) {
        expect(a.y).toBeCloseTo(b.y, 1);
        expect(b.x).toBeGreaterThan(a.x + a.width);
      } else {
        expect(a.x).toBeCloseTo(b.x, 1);
        expect(b.y).toBeGreaterThan(a.y + a.height);
      }
      const bounds = (await dialog.boundingBox())!;
      expect(bounds.y).toBeGreaterThanOrEqual(15);
      expect(bounds.y + bounds.height).toBeLessThanOrEqual(page.viewportSize()!.height - 15);
      await evidence(page, info, 'maintenance-compact-offer');
      await price.press('Escape');
      await expect(create).toBeFocused();
    }
  }
  expect(errors).toEqual([]);
});

async function provesEntrance(surface: Locator) {
  const measurements = await surface.evaluate(async node => {
    // Reapply the real stylesheet animation after it finishes; never create test keyframes.
    const element = node as HTMLElement;
    const inline = element.style.getPropertyValue('animation-name');
    const priority = element.style.getPropertyPriority('animation-name');
    element.style.setProperty('animation-name', 'none', 'important');
    void getComputedStyle(element).animationName;
    if (inline) element.style.setProperty('animation-name', inline, priority);
    else element.style.removeProperty('animation-name');
    const animation = node.getAnimations().find(item => item instanceof CSSAnimation);
    if (!animation) throw new Error('No rendered application entrance found.');
    const timing = animation.effect!.getTiming();
    const delay = timing.delay ?? 0, duration = Number(timing.duration);
    if (timing.iterations !== 1 || !Number.isFinite(duration) || duration < 80 || duration > 2000) {
      throw new Error('The application entrance must be finite and visibly measurable.');
    }
    animation.pause();
    await animation.ready;
    animation.currentTime = delay;
    const sample = () => {
      const css = getComputedStyle(node);
      return {
        time: Number(animation.currentTime), opacity: Number(css.opacity), translate: css.translate,
        y: css.translate === 'none' ? 0 : parseFloat(css.translate.split(/\s+/)[1] || '0'),
      };
    };
    const initial = sample();
    animation.play();
    await new Promise<void>((resolve, reject) => {
      const frame = () => {
        if (Number(animation.currentTime) > delay + 80) resolve();
        else if (animation.playState === 'idle') reject(new Error('The entrance was cancelled before it progressed.'));
        else requestAnimationFrame(frame);
      };
      requestAnimationFrame(frame);
    });
    const progressed = sample();
    await animation.finished;
    return {duration, delay, iterations: timing.iterations, initial, progressed, settled: sample()};
  });
  expect(measurements.duration).toBeGreaterThanOrEqual(420);
  expect(measurements.duration).toBeLessThanOrEqual(560);
  expect(measurements.delay).toBeGreaterThanOrEqual(0);
  expect(measurements.delay).toBeLessThanOrEqual(120);
  expect(measurements.iterations).toBe(1);
  expect(measurements.initial.opacity).toBeGreaterThan(0);
  expect(measurements.initial.opacity).toBeLessThan(.7);
  expect(measurements.initial.y).toBeCloseTo(8, 1);
  expect(measurements.progressed.time).toBeGreaterThan(measurements.initial.time + 80);
  expect(measurements.progressed.opacity).toBeGreaterThan(measurements.initial.opacity + .1);
  expect(measurements.progressed.y).toBeLessThan(measurements.initial.y - 1);
  expect(measurements.settled.opacity).toBe(1);
  expect(measurements.settled.translate).toBe('none');
}

test('page, tab and metric entrances visibly progress and reduced motion leaves settled content', async ({page}, info) => {
  await page.emulateMedia({reducedMotion: 'no-preference'});
  await page.goto('/preview/business');
  await expect(page.locator('.studio-metric')).toHaveCount(4);
  await provesEntrance(page.locator('.studio-page-head'));
  await provesEntrance(page.locator('.studio-metric').first());
  const delays = await page.locator('.studio-metric').evaluateAll(nodes => nodes.map(node =>
    Math.round(parseFloat(getComputedStyle(node).animationDelay) * 1000)));
  expect(delays).toEqual([0, 40, 80, 120]);
  const hiring = page.getByRole('tab', {name: 'Hiring team', exact: true});
  const durations = await hiring.evaluate(node => getComputedStyle(node).transitionDuration.split(',').map(value => value.trim()));
  expect(durations.length).toBeGreaterThan(0);
  expect(durations.every(value => value === '0.18s')).toBe(true);
  await hiring.click();
  const panel = page.locator('.studio-shell [role="tabpanel"][data-state="active"]');
  await provesEntrance(panel);
  await evidence(page, info, 'motion-settled-hiring-team');
  await page.emulateMedia({reducedMotion: 'reduce'});
  await page.getByRole('tab', {name: 'Requests', exact: true}).click();
  for (const surface of [page.locator('.studio-page-head'), page.locator('.studio-metric').first(), panel]) {
    await expect(surface).toBeVisible();
    await expect(surface).toHaveCSS('opacity', '1');
    await expect(surface).toHaveCSS('transform', 'none');
    await expect(surface).toHaveCSS('translate', 'none');
    expect(await surface.evaluate(node => node.getAnimations().length)).toBe(0);
  }
  const request = page.getByRole('button', {name: 'New project request', exact: true});
  await expect(request).toHaveCSS('transition-duration', '0s');
  await request.click();
  await expect(page.getByLabel('Project title', {exact: true})).toBeVisible();
  await fits(page);
});

test('keyboard brief fields have one stable focus outline and dialog dismissal restores its action', async ({page}, info) => {
  await page.goto('/preview/business');
  await settle(page);
  const opener = page.getByRole('button', {name: 'New project request', exact: true});
  await opener.focus();
  await page.keyboard.press('Enter');
  const dialog = page.getByRole('dialog'), title = dialog.getByLabel('Project title', {exact: true});
  await expect(title).toBeFocused();
  await settle(page);
  await page.keyboard.press('Tab');
  await stableKeyboardFocus(page, title);
  await evidence(page, info, 'business-keyboard-focus');
  await fits(page);
  const box = await dialog.boundingBox();
  expect(box!.width).toBeLessThanOrEqual(page.viewportSize()!.width - 16);
  expect(box!.height).toBeLessThanOrEqual(page.viewportSize()!.height - 16);
  await page.keyboard.press('Escape');
  await expect(dialog).not.toBeVisible();
  await expect(opener).toBeFocused();
});

test('public forms, project detail, source cards and team cards visibly settle with stable fields', async ({page}, info) => {
  test.setTimeout(60000);
  await page.goto('/report');
  const report = page.locator('main.wrap > .panel');
  await expect(page.getByRole('heading', {name: 'Report a concern', exact: true})).toBeVisible();
  await glassCard(report);
  await provesEntrance(report);
  const record = page.getByLabel('Record ID', {exact: true});
  await record.focus();
  await page.keyboard.press('Tab');
  await stableKeyboardFocus(page, record);
  await fits(page);
  await evidence(page, info, 'public-report-focused');
  await page.goto('/products/stockroom');
  await expect(page.getByRole('heading', {name: 'Stockroom', exact: true})).toBeVisible();
  await provesEntrance(page.locator('main.wrap > .intro'));
  await fits(page);
  await evidence(page, info, 'public-product-detail');
  await page.goto('/templates');
  await expect(page.getByRole('heading', {name: 'A head start, with the source.', exact: true})).toBeVisible();
  await provesEntrance(page.locator('.commerce-catalog-head'));
  await provesEntrance(page.locator('.commerce-cards > .commerce-card').first());
  await fits(page);
  await evidence(page, info, 'public-source-cards');
  const sample = createTeamDemo(), writes: string[] = [];
  await page.route('**/api/v1/**', async route => {
    const request = route.request(), url = new URL(request.url());
    if (request.method() !== 'GET') {
      writes.push(url.pathname);
      return route.fulfill({status: 405, json: {error: {code: 'READ_ONLY_FIXTURE', message: 'This visual fixture is read only.'}}});
    }
    const json = await sample.request(url.pathname.replace('/api/v1', '') + url.search);
    return route.fulfill({json});
  });
  await page.goto('/teams');
  await expect(page.getByRole('heading', {name: 'Northstar Studio', exact: true})).toBeVisible();
  await provesEntrance(page.locator('.team-hero'));
  await provesEntrance(page.locator('.team-grid > .team-card').first());
  await fits(page);
  await evidence(page, info, 'public-team-cards-fixture');
  expect(writes).toEqual([]);
});

test('reduced transparency resolves chrome to a solid surface and forced colors preserve field boundaries', async ({page}, info) => {
  const session = await page.context().newCDPSession(page);
  await session.send('Emulation.setEmulatedMedia', {features: [{name: 'prefers-reduced-transparency', value: 'reduce'}]});
  await page.goto('/preview/business');
  expect(await page.evaluate(() => matchMedia('(prefers-reduced-transparency: reduce)').matches)).toBe(true);
  await expect(page.locator('.business-brief-panel')).toBeVisible();
  for (const surface of [page.locator('.studio-preview'), page.locator('.business-brief-panel')]) {
    const css = await material(surface);
    expect(alpha(css.fill)).toBe(1);
    expect(css.blur).toBe('none');
    expect(css.borderWidth).toBeGreaterThanOrEqual(1);
  }
  await evidence(page, info, 'reduced-transparency-business');
  await session.detach();
  await page.emulateMedia({forcedColors: 'active', reducedMotion: 'reduce'});
  await page.getByRole('button', {name: 'New project request', exact: true}).click();
  const title = page.getByLabel('Project title', {exact: true});
  await expect(title).toBeFocused();
  const css = await fieldStyle(title);
  expect(css.outline).toBe('solid');
  expect(css.outlineWidth).toBe('2px');
  expect(css.border).not.toBe('rgba(0, 0, 0, 0)');
  await fits(page);
  await evidence(page, info, 'forced-colors-business-focused');
});

test('invalid package fields keep readable values, a visible error and their stable focus outline', async ({page}, info) => {
  await page.goto('/preview/hosting');
  await page.getByRole('button', {name: 'Copy bundled sample', exact: true}).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Current public proof', {exact: true}).selectOption('sample-approved-proof');
  const version = dialog.getByLabel('Package version', {exact: true});
  await version.fill('../private');
  await dialog.getByRole('checkbox').check();
  await dialog.getByRole('button', {name: 'Copy sample draft', exact: true}).click();
  await expect(version).toHaveAttribute('aria-invalid', 'true');
  await expect(version).toBeFocused();
  await expect(version).toHaveValue('../private');
  const describedBy = await version.getAttribute('aria-describedby');
  expect(describedBy).toBeTruthy();
  const messages = await version.evaluate(node => (node.getAttribute('aria-describedby') || '').split(/\s+/)
    .map(id => {
      const message = document.getElementById(id);
      return {text: message?.textContent?.trim() || '', visible: !!message && message.getBoundingClientRect().height > 0};
    }));
  expect(messages.map(message => message.text).join(' ')).toMatch(/version|letters|numbers|valid/i);
  expect(messages.some(message => message.visible && message.text.length > 0)).toBe(true);
  await expect(version).toHaveCSS('border-top-color', 'rgb(180, 35, 24)');
  const css = await fieldStyle(version);
  expect(css.border).toBe('rgb(180, 35, 24)');
  expect(css.outlineWidth).toBe('2px');
  expect(css.outlineColor).toBe('rgb(156, 45, 15)');
  expect(css.font).toBeGreaterThanOrEqual(16);
  await fits(page);
  await evidence(page, info, 'hosting-invalid-focused');
  await page.keyboard.press('Escape');
  await expect(page.getByRole('button', {name: 'Copy bundled sample', exact: true})).toBeFocused();
});

test('native account input wrappers show one focus indicator while original decorative assets stay present', async ({page}, info) => {
  await page.goto('/login');
  await expect(page.getByText('Checking account services…', {exact: true})).toHaveCount(0);
  await settle(page);
  const email = page.getByLabel('Email address', {exact: true}), wrapper = page.locator('.icy-input').filter({has: email});
  await email.fill('focus-fixture@example.test');
  await page.getByLabel('Password', {exact: true}).fill('Not-a-real-account-password');
  // Hold validation state constant while measuring the keyboard focus treatment.
  await email.focus();
  await page.keyboard.press('Tab');
  const before = await fieldStyle(email);
  await page.keyboard.press('Shift+Tab');
  await expect(email).toBeFocused();
  const after = await fieldStyle(email);
  for (const key of ['x', 'y', 'width', 'height'] as const) {
    expect(after[key], `Auth focus must preserve field ${key}`).toBeCloseTo(before[key], 1);
  }
  await expect(wrapper).toHaveCSS('outline-width', '2px');
  await expect(wrapper).toHaveCSS('outline-color', 'rgb(156, 45, 15)');
  await expect(wrapper).toHaveCSS('box-shadow', 'none');
  await expect(email).toHaveCSS('outline-style', 'none');
  await expect(email).toHaveCSS('border-top-width', '0px');
  await expect(email).toHaveCSS('box-shadow', 'none');
  if (page.viewportSize()!.width > 760) {
    await expect(page.locator('.auth-orbit-card')).toHaveCount(4);
    await expect(page.locator('.auth-scene-frame')).toHaveAttribute('src', '/auth/pearlescent-frame-complete.webp');
  }
  await fits(page);
  await evidence(page, info, 'account-login-focused');
});

test('an isolated account fixture renders the real settings and profile on the shared canvas', async ({page}, info) => {
  const writes: string[] = [];
  const empty = {items: [], page: 0, size: 12, hasMore: false, totalItems: 0, totalPages: 0, activeCount: 0};
  await page.route('**/api/v1/**', route => {
    const request = route.request(), path = new URL(request.url()).pathname.replace('/api/v1', '');
    if (request.method() !== 'GET') {
      writes.push(path);
      return route.fulfill({status: 405, json: {error: {code: 'READ_ONLY_FIXTURE', message: 'This visual fixture is read only.'}}});
    }
    if (path === '/me') return route.fulfill({json: {
      id: 'visual-fixture', email: 'visual-fixture@example.test', displayName: 'Visual fixture',
      roles: ['DEVELOPER'], emailVerified: true, activeSlotLimit: 3,
      profile: {approval_status: 'APPROVED', availability_status: 'AVAILABLE_NOW', display_name: 'Visual fixture',
        headline: 'Independent software builder', bio: 'Original work and approved proof belong in this profile.', technology: 'React', category: 'Business software'},
    }});
    if (path === '/developer/analytics') return route.fulfill({json: {}});
    return route.fulfill({json: empty});
  });
  await page.goto('/workspace?tab=account');
  await expect(page.getByRole('heading', {name: 'Account', exact: true})).toBeVisible();
  await expect(page.getByText('visual-fixture@example.test', {exact: true})).toBeVisible();
  await settle(page);
  const canvas = await page.locator('.studio-shell').evaluate(node => getComputedStyle(node).backgroundColor);
  expect(canvas).not.toBe('rgb(255, 255, 255)');
  expect(await page.locator('body').evaluate(node => getComputedStyle(node).backgroundColor)).toBe('rgb(244, 244, 242)');
  await glassCard(page.locator('.studio-shell .panel').filter({has: page.getByRole('heading', {name: 'Account', exact: true})}).first());
  await fits(page);
  await evidence(page, info, 'real-account-settings-fixture');
  await page.getByRole('tab', {name: 'Profile', exact: true}).click();
  await expect(page.getByRole('heading', {name: 'Your builder profile', exact: true})).toBeVisible();
  await settle(page);
  const displayName = page.getByLabel('Display name', {exact: true});
  await displayName.focus();
  await page.keyboard.press('Tab');
  await stableKeyboardFocus(page, displayName);
  await fits(page);
  await evidence(page, info, 'real-account-profile-focused-fixture');
  expect(writes).toEqual([]);
});
