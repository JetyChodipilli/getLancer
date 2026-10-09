import {test, expect, type Page, type Route} from '@playwright/test';
import {readFileSync} from 'node:fs';
import type {ScenarioEdge, ScenarioLanguage, ScenarioPattern} from '../../lib/scenario-evidence';

// Explicit browser protocol fixtures. These do not establish local or hosted execution evidence.
const runId = '11111111-1111-4111-8111-111111111111', sourceHash = 'a'.repeat(64), recordedAt = '2026-10-09T10:00:00.123Z';
const catalogue = JSON.parse(readFileSync(new URL('../../labs/catalogue.json', import.meta.url), 'utf8')) as {languages: Record<ScenarioLanguage, {label: string; requires: string}>; patterns: Record<ScenarioPattern, {scenarios: string[]; edges: ScenarioEdge[]}>};
const languages = ['java', 'typescript', 'python'] as const, patterns = ['cache', 'security', 'payment'] as const;
const material = (language: ScenarioLanguage, pattern: ScenarioPattern, recorded = false) => ({id: `${language}-${pattern}`, language, pattern, title: `Browser fixture ${catalogue.languages[language].label} ${pattern}`, summary: 'Browser protocol fixture only; not execution certification.', sourceHash, archiveHash: 'b'.repeat(64), sourceUrl: `/labs/sources/${language}.tar.gz`, recordingUrl: recorded ? `/labs/recordings/${language}-${pattern}.json` : null, setup: `Browser setup fixture: requires ${catalogue.languages[language].requires}.\nRead the source README.`, scenarios: catalogue.patterns[pattern].scenarios, edges: catalogue.patterns[pattern].edges});
function recording(pattern: ScenarioPattern = 'cache', language: ScenarioLanguage = 'java') {
  const states = pattern === 'cache' ? [{cache: 'MISS', storeReads: 1, ttlMs: 5000}, {cache: 'HIT', storeReads: 1, ttlMs: 4980}] : pattern === 'security' ? [{decision: 'ALLOW', reason: 'ALLOW', currentRole: 'editor'}, {decision: 'DENY', reason: 'ROLE', currentRole: 'viewer'}] : [{payment: 'PENDING', entitlements: 0, processedEvents: 0}, {payment: 'PAID', entitlements: 1, processedEvents: 1}];
  const status = pattern === 'security' ? [200, 403] : pattern === 'payment' ? [202, 200] : [200, 200];
  const eventSets = pattern === 'cache' ? [[['CACHE_MISS', 'service-redis'], ['STORE_READ', 'service-store']], [['CACHE_HIT', 'service-redis']]] : pattern === 'security' ? [[['AUTH_ALLOWED', 'service-policy']], [['AUTH_DENIED', 'policy-store']]] : [[['PAYMENT_PENDING', 'service-emulator']], [['PAYMENT_APPLIED', 'emulator-ledger']]];
  let sequence = 6;
  return {mode: 'REPLAY', labId: `${language}-${pattern}`, language, pattern, runId, sourceHash, recordedAt, responses: states.map((state, index) => {
    const requestId = `${index === 0 ? '22222222-2222-4222-8222-222222222222' : '33333333-3333-4333-8333-333333333333'}`;
    return {runId, sourceHash, requestId, pattern, status: status[index], durationMs: index + 2, state, events: eventSets[index].map(([type, edge]) => ({runId, sourceHash, requestId, sequence: ++sequence, type, edge, recordedAt, summary: `Browser protocol fixture observation: ${type}.`}))};
  })};
}
const json = (route: Route, body: unknown, status = 200) => route.fulfill({status, contentType: 'application/json', body: JSON.stringify(body)});
async function index(page: Page, recorded = false) {await page.route('**/labs/material-index.json', route => json(route, {items: languages.flatMap(language => patterns.map(pattern => material(language, pattern, recorded)))}));}
async function noExecution(page: Page) {
  const effects: string[] = [];
  await page.route('**/api/**', route => {if (route.request().method() !== 'GET') effects.push(route.request().url()); return json(route, {error: {message: 'Browser API denial fixture.'}}, 503);});
  return effects;
}
async function reflow(page: Page) {expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);}
async function noHighlight(page: Page) {await expect(page.locator('.scenario-edge[data-observed="true"]')).toHaveCount(0);}

test('source-only nine-lab catalogue filters with native keyboard controls and keeps free source when APIs fail', async ({page}) => {
  const effects = await noExecution(page); await index(page); await page.goto('/labs/scenarios');
  await expect(page.locator('.scenario-lab-card')).toHaveCount(9); await expect(page.locator('.scenario-lab-card').getByText('Source only', {exact: true})).toHaveCount(9);
  await expect(page.getByText('Hosted runtime unavailable', {exact: true})).toHaveCount(2);
  const language = page.getByLabel('Language', {exact: true}); await language.focus(); await expect(language).toBeFocused();
  expect(await language.evaluate(node => getComputedStyle(node).fontSize)).toBe('16px'); expect((await language.boundingBox())!.height).toBeGreaterThanOrEqual(44);
  expect(await language.evaluate(node => getComputedStyle(node).outlineStyle)).not.toBe('none');
  await language.selectOption('python'); await page.getByLabel('Pattern', {exact: true}).selectOption('payment'); await expect(page.locator('.scenario-lab-card')).toHaveCount(1);
  await expect(page.getByRole('heading', {name: 'Browser fixture Python payment', exact: true})).toHaveCount(2);
  await expect(page.getByRole('link', {name: 'Download free source & setup', exact: true})).toHaveAttribute('href', '/labs/sources/python.tar.gz');
  await expect(page.getByText('Browser setup fixture: requires Python 3.12.', {exact: false})).toBeVisible(); await noHighlight(page);
  await page.getByRole('button', {name: 'Recorded evidence', exact: true}).click(); await expect(page.getByRole('heading', {name: 'Source only', exact: true})).toBeVisible();
  await expect(page.getByRole('button', {name: 'Next response', exact: true})).toHaveCount(0); await expect(page.getByRole('button', {name: 'Run isolated lab', exact: true})).toHaveCount(0);
  await page.getByRole('button', {name: 'Clear filters', exact: true}).click(); await expect(page.locator('.scenario-lab-card')).toHaveCount(9); expect(effects).toEqual([]); await reflow(page);
});

for (const pattern of patterns) test(`${pattern} recorded browser fixture preserves original evidence and highlights only the selected observed transition`, async ({page}) => {
  const effects = await noExecution(page); await index(page, true); await page.route(`**/labs/recordings/java-${pattern}.json`, route => json(route, recording(pattern)));
  await page.goto('/labs/scenarios'); await page.getByLabel('Language', {exact: true}).selectOption('java'); await page.getByLabel('Pattern', {exact: true}).selectOption(pattern);
  await noHighlight(page); await page.getByRole('button', {name: 'Recorded evidence', exact: true}).click();
  await expect(page.getByText('Replay · Recorded local execution', {exact: true})).toBeVisible();
  await expect(page.locator('.scenario-metadata').getByText(recordedAt, {exact: true})).toBeVisible(); await expect(page.locator('.scenario-metadata').getByText(sourceHash, {exact: true})).toBeVisible();
  await expect(page.locator('.scenario-metadata').getByText(runId, {exact: true})).toBeVisible();
  await expect(page.locator('.scenario-edge[data-observed="true"]')).toHaveCount(1); await expect(page.locator('.scenario-transitions li')).toHaveCount(pattern === 'cache' ? 3 : 2);
  await expect(page.getByRole('button', {name: 'Previous response', exact: true})).toBeDisabled(); await expect(page.locator('.scenario-response-summary')).toContainText('Recorded duration: 2 ms');
  if (pattern === 'cache') {
    await expect(page.locator('.scenario-edge[data-observed="true"]')).toContainText('Redis'); await page.getByRole('button', {name: 'Next event', exact: true}).click();
    await expect(page.locator('.scenario-edge[data-observed="true"]')).toContainText('Fixture store'); await expect(page.locator('.scenario-observation')).toContainText('STORE_READ');
  }
  await page.getByRole('button', {name: 'Next response', exact: true}).click(); await expect(page.locator('.scenario-response-summary')).toContainText('Response 2 of 2');
  await expect(page.locator('.scenario-response-summary')).toContainText(pattern === 'security' ? 'HTTP 403' : 'HTTP 200'); await expect(page.locator('.scenario-response-summary')).toContainText('Recorded duration: 3 ms');
  await expect(page.getByRole('button', {name: 'Next response', exact: true})).toBeDisabled(); await expect(page.locator('.scenario-edge[data-observed="true"]')).toHaveCount(1);
  await expect(page.locator('.scenario-state')).toContainText(pattern === 'cache' ? 'HIT' : pattern === 'security' ? 'DENY' : 'PAID');
  await page.getByText('Actual recorded response JSON', {exact: true}).click(); await expect(page.locator('.scenario-raw pre')).toContainText('33333333-3333-4333-8333-333333333333');
  await page.getByRole('button', {name: 'Source & setup', exact: true}).click(); await noHighlight(page); await expect(page.getByText('Source architecture preview', {exact: true})).toBeVisible();
  expect(effects).toEqual([]); await reflow(page);
});

test('loading, failed index retry and empty index retain honest source/setup availability', async ({page}) => {
  await noExecution(page); let attempts = 0; let release: (() => void) | undefined;
  const pending = new Promise<void>(resolve => {release = resolve;});
  await page.route('**/labs/material-index.json', async route => {attempts++; if (attempts === 1) {await pending; return json(route, {error: 'Browser fixture unavailable'}, 503);} return json(route, {items: []});});
  await page.goto('/labs/scenarios'); await expect(page.getByText('Loading build-generated source versions and recording availability…', {exact: true})).toBeVisible(); release!();
  await expect(page.getByRole('alert')).toContainText('Material index unavailable'); await expect(page.locator('.scenario-lab-card')).toHaveCount(9);
  await expect(page.getByRole('link', {name: 'Download free source & setup', exact: true})).toHaveAttribute('href', '/labs/sources/java.tar.gz');
  await page.getByRole('button', {name: 'Retry material index', exact: true}).click(); await expect(page.getByText('No built materials are listed.', {exact: false})).toBeVisible();
  await expect(page.getByRole('alert')).toHaveCount(0); await noHighlight(page); await reflow(page);
});

test('a valid smaller index presents a useful empty filter state', async ({page}) => {
  await page.route('**/labs/material-index.json', route => json(route, {items: [material('java', 'cache')]})); await page.goto('/labs/scenarios');
  await expect(page.locator('.scenario-lab-card')).toHaveCount(1); await page.getByLabel('Language', {exact: true}).selectOption('python');
  await expect(page.getByRole('heading', {name: 'No labs match these filters', exact: true})).toBeVisible(); await noHighlight(page);
  await page.getByRole('button', {name: 'Clear filters', exact: true}).click(); await expect(page.locator('.scenario-lab-card')).toHaveCount(1); await reflow(page);
});

for (const defect of ['stale source', 'foreign event', 'non-monotonic events', 'live mode'] as const) test(`${defect} is rejected without highlights, then a recording retry recovers original fixture evidence`, async ({page}) => {
  await index(page, true); let attempts = 0;
  await page.route('**/labs/recordings/java-cache.json', route => {
    const value = recording(); attempts++;
    if (attempts === 1) {
      if (defect === 'stale source') value.sourceHash = 'c'.repeat(64);
      else if (defect === 'foreign event') value.responses[0].events[0].runId = '44444444-4444-4444-8444-444444444444';
      else if (defect === 'non-monotonic events') value.responses[0].events[1].sequence = value.responses[0].events[0].sequence;
      else value.mode = 'LIVE';
    }
    return json(route, value);
  });
  await page.goto('/labs/scenarios'); await expect(page.locator('.scenario-lab-card')).toHaveCount(9); await page.getByRole('button', {name: 'Recorded evidence', exact: true}).click();
  await expect(page.getByRole('alert')).toContainText('Recording rejected or unavailable'); await expect(page.getByRole('alert')).toBeFocused(); await noHighlight(page);
  await expect(page.getByRole('button', {name: 'Next response', exact: true})).toHaveCount(0); await expect(page.locator('.scenario-lab-card').first()).toContainText('Source only');
  await page.getByRole('button', {name: 'Retry recording', exact: true}).click(); await expect(page.getByText('Replay · Recorded local execution', {exact: true})).toBeVisible();
  await expect(page.getByRole('alert')).toHaveCount(0); await expect(page.locator('.scenario-edge[data-observed="true"]')).toHaveCount(1); await reflow(page);
});

test('switching labs while a recording loads cannot apply evidence to another lab', async ({page}) => {
  await index(page, true); let release: (() => void) | undefined; const pending = new Promise<void>(resolve => {release = resolve;});
  await page.route('**/labs/recordings/java-cache.json', async route => {await pending; await json(route, recording());});
  await page.goto('/labs/scenarios'); await expect(page.locator('.scenario-lab-card')).toHaveCount(9); await page.getByRole('button', {name: 'Recorded evidence', exact: true}).click();
  await expect(page.getByText('Loading original recorded evidence…', {exact: true})).toBeVisible(); await page.getByLabel('Pattern', {exact: true}).selectOption('payment'); release!();
  await expect(page.getByRole('heading', {name: 'Browser fixture Java payment', exact: true})).toHaveCount(2); await noHighlight(page);
  await expect(page.getByText('Replay · Recorded local execution', {exact: true})).toHaveCount(0); await reflow(page);
});

test('reduced motion and source preview contain no idle edge animation', async ({page}) => {
  await page.emulateMedia({reducedMotion: 'reduce'}); await index(page); await page.goto('/labs/scenarios'); await expect(page.locator('.scenario-edge')).toHaveCount(3); await noHighlight(page);
  expect(await page.locator('.scenario-edge').first().evaluate(node => getComputedStyle(node).animationName)).toBe('none');
  for (const button of await page.locator('.scenario-view-controls button').all()) expect((await button.boundingBox())!.height).toBeGreaterThanOrEqual(44);
  await reflow(page);
});
