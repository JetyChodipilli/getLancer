import {expect, test, type Locator, type Page} from '@playwright/test';
import {readFileSync} from 'node:fs';
import {buildSync} from 'esbuild';

async function fits(page: Page, scope: Locator) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(page.viewportSize()!.width + 1);
  expect(await scope.evaluate(node => node.scrollWidth <= node.clientWidth + 1)).toBe(true);
}

async function labelGap(label: Locator, field: Locator) {
  const a = await label.boundingBox(), b = await field.boundingBox();
  expect(a).not.toBeNull(); expect(b).not.toBeNull();
  expect(b!.y - a!.y - a!.height).toBeGreaterThanOrEqual(7);
  expect(b!.y - a!.y - a!.height).toBeLessThanOrEqual(13);
}

test('component wizard spaces fieldsets and keeps wrapping consent beside its checkbox', async ({page}, info) => {
  await page.emulateMedia({reducedMotion: 'reduce'});
  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/v1/me') return route.fulfill({json: {roles: ['DEVELOPER'], emailVerified: true, profile: {approval_status: 'APPROVED'}}});
    if (path === '/api/v1/me/components') return route.fulfill({json: {
      items: [], capacity: {free: 3, purchased: 0, used: 0, limit: 3},
      pricing: {amountMinor: null, enabled: false, salesEnabled: false, configured: false, currency: 'INR', mode: 'disabled'},
    }});
    if (path === '/api/v1/me/component-slot-purchases') return route.fulfill({json: {items: [], hasMore: false}});
    return route.fulfill({status: 404, json: {error: {code: 'NOT_FOUND', message: 'No unrelated fixture route.'}}});
  });
  await page.goto('/workspace/components');
  await page.getByRole('button', {name: 'New component', exact: true}).click();
  const dialog = page.getByRole('dialog');
  const title = dialog.getByLabel('Component title', {exact: true});
  const summary = dialog.getByLabel('Summary', {exact: true});
  await expect(title).toHaveCSS('font-size', '16px');
  await labelGap(dialog.locator('label[for="title"]'), title);
  await labelGap(dialog.locator('label[for="summary"]'), summary);
  await title.fill('An original accessible component');
  await summary.fill('A source contribution with readable fields and clear consent.');
  await dialog.getByRole('button', {name: 'Continue to contribution', exact: true}).click();
  const contribution = dialog.getByLabel('Your contribution & intended use', {exact: true});
  await expect(contribution).toBeVisible();
  await expect(title).not.toBeVisible();
  await labelGap(dialog.locator('label[for="contribution"]'), contribution);
  const consent = dialog.locator('label').filter({has: page.getByRole('checkbox', {name: /I preserve/})});
  const checkbox = consent.getByRole('checkbox');
  await expect(consent).toHaveCSS('display', 'flex');
  const box = await consent.boundingBox(), toggle = await checkbox.boundingBox();
  expect(box!.height).toBeGreaterThanOrEqual(44);
  expect(toggle!.x - box!.x).toBeLessThanOrEqual(1);
  expect(toggle!.y - box!.y).toBeLessThanOrEqual(12);
  expect(toggle!.width).toBeLessThanOrEqual(21);
  await fits(page, dialog);
  const screenshotPath = info.outputPath('component-consent-layout.png');
  await page.screenshot({path: screenshotPath, animations: 'disabled'});
  await info.attach('component-consent-layout', {path: screenshotPath, contentType: 'image/png'});
  await dialog.getByRole('button', {name: 'Back', exact: true}).click();
  await expect(contribution).not.toBeVisible();
  await expect(title).toHaveValue('An original accessible component');
});

test('shared form errors stay readable and linked at 200 percent text size', async ({page}) => {
  const fixture = buildSync({stdin: {contents: `
    import {createRoot} from 'react-dom/client';
    import FormErrors from './app/components/form-errors';
    import {ApiError} from './lib/api';
    const detail = 'The service returned an invalid source reference: ' + 'a'.repeat(128);
    createRoot(document.getElementById('root')).render(<form className="form">
      <label htmlFor="component-title">Component title</label>
      <input id="component-title" aria-invalid="true" aria-describedby="component-title-error" defaultValue="Original contribution" />
      <p className="error" id="component-title-error">Choose a descriptive title.</p>
      <FormErrors prefix="component-" error={new ApiError(detail, 'VALIDATION_ERROR', 422, undefined, {title: 'Choose a descriptive title.'})}/>
    </form>);
  `, loader: 'tsx', resolveDir: process.cwd()}, bundle: true, write: false, platform: 'browser', format: 'iife', jsx: 'automatic', define: {'process.env.NODE_ENV': '"test"'}}).outputFiles[0].text;
  const css = readFileSync('app/form-controls.css', 'utf8');
  await page.setContent('<meta name="viewport" content="width=device-width,initial-scale=1"><style>*{box-sizing:border-box}body{margin:16px;font-family:Arial}#root{max-width:660px;margin:auto}</style><div id="root"></div>');
  await page.addStyleTag({content: css});
  await page.addScriptTag({content: fixture});
  const error = page.getByRole('alert', {name: 'Check these details', exact: true});
  await expect(error).toBeFocused();
  await expect(error).toHaveCSS('font-size', '14px');
  await expect(page.locator('#component-title-error')).toHaveCSS('padding-top', '0px');
  await expect(page.locator('#component-title-error')).toHaveCSS('border-top-width', '0px');
  // Increase the actual rendered text sizes, not a transform of the completed page.
  await page.addStyleTag({content: '.form :is(label,input,p,strong,a){font-size:200% !important}'});
  await fits(page, page.locator('form'));
  const link = error.getByRole('link', {name: 'title: Choose a descriptive title.', exact: true});
  await link.click();
  await expect(page.getByLabel('Component title', {exact: true})).toBeFocused();
});
