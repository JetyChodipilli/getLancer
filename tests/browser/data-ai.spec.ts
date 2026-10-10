import {test, expect, type Page} from '@playwright/test';
import {readFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {createRequire} from 'node:module';
import {buildSync} from 'esbuild';
import type {DataAiIndex, DataAiRecording} from '../../lib/data-ai';
import type {EducationDraft, EducationRelease, EducationDataAiEvidence} from '../../lib/education';

const index = JSON.parse(readFileSync('public/data-ai/material-index.json', 'utf8')) as DataAiIndex;
const recordings = Object.fromEntries(index.items.map(item => [item.id, JSON.parse(readFileSync('public' + item.recordingUrl, 'utf8')) as DataAiRecording]));
const seeds = JSON.parse(readFileSync('backend/src/main/resources/catalog/education.json', 'utf8')) as {release: EducationRelease}[];
const sample = seeds[0].release;
const axe = readFileSync(createRequire(import.meta.url).resolve('axe-core/axe.min.js'), 'utf8');
const evidence: EducationDataAiEvidence = {codeLicense: 'MIT', dataLicense: 'MIT', dataProvenance: 'Original synthetic fixture.', dataSha256: 'a'.repeat(64), modelLicense: 'MIT', modelProvenance: 'Original frozen JSON model.', modelSha256: 'b'.repeat(64), modelFormat: 'JSON', evaluationSplit: 'Held-out synthetic fixture.', evaluationProtocol: 'Frozen model without training.', outputSchema: 'Typed tables and numeric metrics with declared units.', limitations: 'Tiny synthetic fixture; no real-world accuracy claim.', redistributionAllowed: true, syntheticData: true, noRemoteCode: true};
async function reflow(page: Page) {expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);}
async function scan(page: Page) {
  await page.evaluate(axe);
  const result = await page.evaluate(async () => (window as unknown as {axe: {run: (element: Element | null, options: unknown) => Promise<{violations: {id: string; impact: string; nodes: {target: string[]}[]}[]}>}}).axe.run(document.querySelector('main'), {runOnly: {type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa']}}));
  expect(result.violations.filter(item => ['critical', 'serious'].includes(item.impact)).map(item => ({id: item.id, targets: item.nodes.map(node => node.target)}))).toEqual([]);
}
async function openRecording(page: Page, title?: string) {
  await page.goto('/labs/data-ai');
  await expect(page.getByText('Loading source versions and recording availability…', {exact: true})).toHaveCount(0);
  if (title) await page.getByRole('button', {name: title, exact: true}).click();
  await page.getByRole('button', {name: 'Recorded execution', exact: true}).click();
  await expect(page.getByRole('heading', {name: 'Rendered output · Recorded local result', exact: true})).toBeVisible();
}

test('source, replay and hosted disclosures stay distinct with zero execution requests', async ({page}) => {
  const mutations: string[] = []; page.on('request', request => {if (request.method() !== 'GET' && request.method() !== 'HEAD') mutations.push(request.method() + ' ' + request.url());});
  await page.goto('/labs/data-ai');
  await expect(page.getByRole('heading', {name: 'Read the source. Inspect the evidence.', exact: true})).toBeVisible();
  await expect(page.getByText('Reproduce the source locally', {exact: true})).toBeVisible();
  await expect(page.getByRole('heading', {name: 'Rendered output · Recorded local result', exact: true})).toHaveCount(0);
  await page.getByRole('button', {name: 'Recorded execution', exact: true}).click();
  await expect(page.getByText('Replay · Recorded local execution', {exact: true})).toBeVisible();
  await page.getByRole('button', {name: 'Hosted runtime', exact: true}).click();
  await expect(page.getByRole('heading', {name: 'Hosted runtime · Current admission', exact: true})).toBeVisible();
  await expect(page.getByRole('link', {name: 'Check hosted admission', exact: true})).toHaveAttribute('href', '/labs');
  await expect(page.getByLabel('Recorded case', {exact: true})).toHaveCount(0);
  expect(mutations).toEqual([]); await reflow(page); await scan(page);
});

test('free source download matches the build-pinned archive SHA-256', async ({page}) => {
  await page.goto('/labs/data-ai'); await expect(page.getByText('Loading source versions and recording availability…', {exact: true})).toHaveCount(0);
  await page.getByText('Source and artifact checksums', {exact: true}).click();
  await expect(page.getByText(index.archiveSha256, {exact: true})).toBeVisible();
  const downloaded = page.waitForEvent('download'); await page.getByRole('link', {name: 'Download free source & setup', exact: true}).click();
  const download = await downloaded; expect(download.suggestedFilename()).toBe('source.tar.gz');
  expect(createHash('sha256').update(readFileSync((await download.path())!)).digest('hex')).toBe(index.archiveSha256);
});

for (const item of index.items) test(`${item.id}: default and changed inspection render actual returned tables and identities`, async ({page}) => {
  await openRecording(page, item.title); const recording = recordings[item.id];
  for (let position = 0; position < 2; position++) {
    await page.getByLabel('Recorded case', {exact: true}).selectOption(String(position));
    await expect(page.getByText(new RegExp(`Case ${position + 1} of 2 ·`))).toBeVisible();
    const run = recording.runs[position];
    for (const table of run.result.tables) {
      const rendered = page.getByRole('table', {name: 'Actual returned ' + table.title.toLowerCase(), exact: true});
      await expect(rendered.locator('tbody tr')).toHaveCount(table.rows.length);
      for (const [rowIndex, row] of table.rows.entries()) for (const [columnIndex, column] of table.columns.entries()) await expect(rendered.locator('tbody tr').nth(rowIndex).locator('th,td').nth(columnIndex)).toHaveText(typeof row[column.key] === 'number' ? row[column.key].toLocaleString('en-IN', {maximumFractionDigits: 6}) : String(row[column.key]));
    }
    await page.getByText('Actual original result envelope', {exact: true}).click();
    const original = JSON.parse((await page.locator('.data-ai-raw pre').textContent())!); expect(original).toEqual(run);
    await page.getByText('Actual original result envelope', {exact: true}).click();
    if (item.kind === 'AI_ML') {await expect(page.getByRole('heading', {name: 'Held-out synthetic evaluation', exact: true})).toBeVisible(); await expect(page.getByRole('heading', {name: 'Recorded prediction: ' + run.result.prediction!.label, exact: true})).toBeVisible();}
  }
  await reflow(page); await scan(page);
});

test('native filters preserve source state and distinguish two analytics from two CPU inference labs', async ({page}) => {
  await page.goto('/labs/data-ai'); await page.getByLabel('Lab category', {exact: true}).selectOption('AI_ML');
  await expect(page.locator('.data-ai-card')).toHaveCount(2); await expect(page.getByText('2 source labs', {exact: true})).toBeVisible();
  await expect(page.getByText('Reproduce the source locally', {exact: true})).toBeVisible();
  await page.getByLabel('Lab category', {exact: true}).selectOption('DATA_ANALYTICS'); await expect(page.locator('.data-ai-card')).toHaveCount(2);
  await expect(page.getByRole('button', {name: index.items[0].title, exact: true})).toHaveAttribute('aria-pressed', 'true'); await reflow(page);
});

test('malformed units and a recomputed result hash fail closed, focus an error and support retry', async ({page}) => {
  let broken = true; const original = recordings['revenue-summary'], altered = structuredClone(original);
  altered.runs[0].result.metrics[0].unit = 'USD';
  const sorted = (value: unknown): unknown => Array.isArray(value) ? value.map(sorted) : value && typeof value === 'object' ? Object.fromEntries(Object.entries(value).sort(([a], [b]) => a < b ? -1 : 1).map(([key, child]) => [key, sorted(child)])) : value;
  altered.runs[0].resultSha256 = createHash('sha256').update(JSON.stringify(sorted(altered.runs[0].result))).digest('hex');
  await page.route('**/data-ai/recordings/revenue-summary.json', route => route.fulfill({contentType: 'application/json', body: JSON.stringify(broken ? altered : original)}));
  await page.goto('/labs/data-ai'); await page.getByRole('button', {name: 'Recorded execution', exact: true}).click();
  const error = page.getByRole('alert').filter({hasText: 'Recording unavailable or rejected'}); await expect(error).toBeFocused();
  await expect(page.locator('.data-ai-output')).toHaveCount(0); broken = false; await page.getByRole('button', {name: 'Retry recording', exact: true}).click();
  await expect(page.getByRole('heading', {name: 'Rendered output · Recorded local result', exact: true})).toBeVisible(); await scan(page);
});

test('index failure leaves free source available without claiming confirmed recordings', async ({page}) => {
  let failed = true; await page.route('**/data-ai/material-index.json', route => route.fulfill({status: failed ? 503 : 200, contentType: 'application/json', body: failed ? '{}' : JSON.stringify(index)}));
  await page.goto('/labs/data-ai'); await expect(page.getByRole('alert')).toContainText('Source index unavailable or rejected');
  await expect(page.getByRole('link', {name: 'Download free source & setup', exact: true})).toBeVisible();
  await page.getByRole('button', {name: 'Recorded execution', exact: true}).click(); await expect(page.getByRole('heading', {name: 'Recording version unconfirmed', exact: true})).toBeVisible();
  await expect(page.locator('.data-ai-output')).toHaveCount(0); failed = false; await page.getByRole('button', {name: 'Retry source index', exact: true}).click();
  await expect(page.getByRole('heading', {name: 'Rendered output · Recorded local result', exact: true})).toBeVisible();
});

test('delayed evidence from a previous scenario cannot replace a newly selected source', async ({page}) => {
  let release!: () => void; const ready = new Promise<void>(resolve => {release = resolve;}); let requested = false;
  await page.route('**/data-ai/recordings/revenue-summary.json', async route => {requested = true; await ready; await route.fulfill({contentType: 'application/json', body: JSON.stringify(recordings['revenue-summary'])}).catch(() => {});});
  await page.goto('/labs/data-ai'); await page.getByRole('button', {name: 'Recorded execution', exact: true}).click(); await expect.poll(() => requested).toBe(true);
  await page.getByRole('button', {name: index.items[2].title, exact: true}).click(); release();
  await expect(page.locator('#data-ai-inspector-heading')).toHaveText(index.items[2].title); await expect(page.getByText('Reproduce the source locally', {exact: true})).toBeVisible();
  await expect(page.locator('.data-ai-output')).toHaveCount(0); await page.getByRole('button', {name: 'Recorded execution', exact: true}).click();
  await expect(page.getByRole('heading', {name: 'Recorded prediction: ' + recordings['sentiment-inference'].runs[0].result.prediction!.label, exact: true})).toBeVisible();
});

test('320px, landscape, 200 percent text and reduced motion preserve keyboard access and tables', async ({page}) => {
  await page.setViewportSize({width: 320, height: 812}); await page.emulateMedia({reducedMotion: 'reduce'}); await openRecording(page);
  await page.getByLabel('Recorded case', {exact: true}).focus(); await page.keyboard.press('Tab'); await expect(page.getByRole('button', {name: 'Next case', exact: true})).toBeFocused(); await page.keyboard.press('Enter');
  await expect(page.getByLabel('Recorded case', {exact: true})).toHaveValue('1'); await reflow(page); await scan(page);
  expect(await page.locator('.data-ai-output').evaluate(node => getComputedStyle(node).animationName)).toBe('none');
  await page.addStyleTag({content: 'html{font-size:200%} .data-ai-shell{font-size:200%} .data-ai-shell h1,.data-ai-shell h2,.data-ai-shell h3,.data-ai-shell h4,.data-ai-shell p,.data-ai-shell label,.data-ai-shell button,.data-ai-shell select,.data-ai-shell dt,.data-ai-shell dd,.data-ai-shell td,.data-ai-shell th{font-size:inherit!important}'}); await reflow(page);
  await page.setViewportSize({width: 812, height: 375}); await reflow(page); await expect(page.getByRole('button', {name: 'Previous case', exact: true})).toBeEnabled();
});

function draftRelease(body: EducationDraft, revision = 1): EducationRelease {return {...sample, id: '00000000-0000-4000-8000-000000000020', revision, status: 'DRAFT', sourceHash: undefined, snapshot: {...sample.snapshot, ...body, dataAiEvidence: body.dataAiEvidence, sourceBinding: undefined}};}
async function educationWorkspace(page: Page, onSave: (body: EducationDraft) => void) {
  let record: EducationRelease | undefined;
  await page.route('**/api/v1/**', async route => {const request = route.request(), path = new URL(request.url()).pathname; let body: unknown = {};
    if (path.endsWith('/submit')) {body = {...record, status: 'PENDING', sourceHash: 'c'.repeat(64)};}
    else if (request.method() === 'POST' || request.method() === 'PATCH') {const input = request.postDataJSON() as EducationDraft; onSave(input); record = draftRelease(input, (record?.revision || 0) + 1); body = record;}
    else if (path.includes('/developer/products')) body = {items: [{id: sample.productId, title: 'Original data project'}], hasMore: false};
    else if (path.includes('/me/college-projects') || path.includes('/me/education-releases')) body = {items: [], hasMore: false};
    await route.fulfill({contentType: 'application/json', body: JSON.stringify(body)});
  }); await page.goto('/workspace/college-projects'); await page.getByRole('button', {name: 'New education release', exact: true}).click();
}

test('private creator drafts preserve partial evidence while new submissions remain incomplete', async ({page}) => {
  let saved: EducationDraft | undefined; await educationWorkspace(page, body => {saved = body;});
  await page.getByLabel('Release category', {exact: true}).selectOption('AI_ML'); await page.getByRole('button', {name: '2. Build / Evidence', exact: true}).click();
  await page.getByLabel('Code license', {exact: true}).fill('MI'); await page.getByLabel('Code license', {exact: true}).blur(); await expect(page.getByLabel('Code license', {exact: true})).toHaveAttribute('aria-invalid', 'true');
  await page.getByLabel('Model format', {exact: true}).selectOption('JSON'); await page.getByLabel('Data SHA-256', {exact: true}).fill('a'.repeat(64));
  await page.getByRole('button', {name: 'Save unfinished draft', exact: true}).click(); await expect(page.getByText(/Draft saved\. Unfinished fields/)).toBeVisible();
  expect(saved?.dataAiEvidence).toEqual({codeLicense: 'MI', modelFormat: 'JSON', dataSha256: 'a'.repeat(64)}); expect(saved?.categoryEvidence).not.toHaveProperty('dataAiEvidence');
  await page.getByRole('button', {name: '4. Review', exact: true}).click(); await expect(page.getByRole('button', {name: 'Submit exact revision for review', exact: true})).toBeDisabled();
  await expect(page.getByText('MI', {exact: true})).toBeVisible(); await reflow(page); await scan(page);
});

test('new AI submission saves the exact complete structured evidence before freezing a revision', async ({page}) => {
  const saved: EducationDraft[] = []; await educationWorkspace(page, body => {saved.push(body);});
  await page.getByLabel('Release category', {exact: true}).selectOption('AI_ML'); await page.getByRole('button', {name: '2. Build / Evidence', exact: true}).click();
  const textLabels: Record<string, string> = {codeLicense: 'Code license', dataLicense: 'Data license', dataProvenance: 'Data provenance', dataSha256: 'Data SHA-256', modelLicense: 'Model license', modelProvenance: 'Model provenance', modelSha256: 'Model SHA-256', evaluationSplit: 'Evaluation split', evaluationProtocol: 'Evaluation protocol', outputSchema: 'Output schema', limitations: 'Data and model limitations'};
  for (const [key, label] of Object.entries(textLabels)) await page.getByLabel(label, {exact: true}).fill(evidence[key as keyof EducationDataAiEvidence] as string);
  await page.getByLabel('Model format', {exact: true}).selectOption('JSON');
  for (const label of [/I have verified separate code/, /All included datasets and evaluation fixtures/, /The package uses no executable model loader/]) await page.getByRole('checkbox', {name: label}).check();
  for (const label of ['Model task','Dataset & model licenses','Training / evaluation splits','Evaluation method','Observed metrics & context','Inference entry point','Model limitations']) await page.getByLabel(label, {exact: true}).fill('Concrete original reproducibility evidence.');
  await page.getByRole('button', {name: '3. Access / License', exact: true}).click();
  for (const label of ['Included source & assets','Excluded source, data, models & hardware','Setup effort & reproduction steps','External dependencies & prerequisites','Limitations & responsibilities']) await page.getByLabel(label + ' · one item per line', {exact: true}).fill('Explicit original package disclosure.');
  await page.getByLabel('Support scope & duration', {exact: true}).fill('Self-guided source learning without maintenance.'); await page.getByLabel('License & attribution terms', {exact: true}).fill('MIT License. Preserve copyright and permission notices.');
  await page.getByRole('button', {name: 'Save unfinished draft', exact: true}).click(); await expect(page.getByText(/Draft saved\. Unfinished fields/)).toBeVisible();
  await page.getByRole('button', {name: '4. Review', exact: true}).click(); await page.getByRole('checkbox', {name: 'I confirm rights, contribution and the exact release license.', exact: true}).check();
  await page.getByRole('button', {name: 'Submit exact revision for review', exact: true}).click(); await expect(page.getByText(/Exact source and category evidence submitted/)).toBeVisible();
  expect(saved).toHaveLength(2); expect(saved[0].dataAiEvidence).toEqual(evidence); expect(saved[1].dataAiEvidence).toEqual(evidence); expect(saved[1].revision).toBe(1); await reflow(page);
});

async function componentHarness(page: Page, module: string, props: unknown) {
  const body = buildSync({stdin: {contents: `import {createRoot} from 'react-dom/client';import Component from './app/components/${module}';createRoot(document.getElementById('fixture')).render(<Component {...${JSON.stringify(props)}}/>);`, loader: 'tsx', resolveDir: process.cwd()}, bundle: true, write: false, format: 'iife', platform: 'browser', jsx: 'automatic', define: {'process.env.NODE_ENV': '"test"', 'process.env': '{}'}}).outputFiles[0].text;
  await page.route('**/data-ai-education-fixture', route => route.fulfill({contentType: 'text/html', body: '<main><h1>Frozen education disclosures</h1><div id="fixture"></div></main><script src="/data-ai-education-fixture.js"></script>'}));
  await page.route('**/data-ai-education-fixture.js', route => route.fulfill({contentType: 'application/javascript', body})); await page.goto('/data-ai-education-fixture');
}
async function packageHarness(page: Page, withEvidence: boolean) {await componentHarness(page, 'education-package', {pack: sample.snapshot.package, showcase: true, category: 'AI_ML', evidence: withEvidence ? evidence : undefined});}

test('frozen disclosures show separate dataset/model rights and actual evaluation context', async ({page}) => {await packageHarness(page, true); await expect(page.getByRole('heading', {name: 'Dataset, model and evaluation evidence', exact: true})).toBeVisible(); await expect(page.getByText(evidence.modelSha256!, {exact: true})).toBeVisible(); await expect(page.getByText(evidence.evaluationProtocol!, {exact: true})).toBeVisible(); await expect(page.getByText(evidence.limitations!, {exact: true})).toBeVisible(); await scan(page);});
test('historical missing structured evidence stays explicitly absent and readable', async ({page}) => {await packageHarness(page, false); await expect(page.getByText(/This historical release has no structured data\/AI evidence/)).toBeVisible(); await expect(page.getByText('Confirmed', {exact: true})).toHaveCount(0); await expect(page.getByRole('heading', {name: 'Project artifacts & license', exact: true})).toBeVisible(); await scan(page);});

test('private reviewer sees frozen provenance before the exact hash decision', async ({page}) => {
  const pending: EducationRelease = {...sample, status: 'PENDING', sourceHash: 'd'.repeat(64), snapshot: {...sample.snapshot, category: 'AI_ML', mode: 'SHOWCASE', dataAiEvidence: evidence, sourceBinding: undefined}};
  let decision: {revision: number; sourceHash: string; rightsReviewed: boolean; packageReviewed: boolean} | undefined;
  await page.route('**/api/v1/admin/education-releases**', async route => {if (route.request().method() === 'GET') await route.fulfill({contentType: 'application/json', body: JSON.stringify({items: [pending], hasMore: false})}); else {decision = route.request().postDataJSON(); await route.fulfill({contentType: 'application/json', body: JSON.stringify({...pending, status: 'APPROVED'})});}});
  await componentHarness(page, 'education-review', {}); await expect(page.getByText(evidence.modelProvenance!, {exact: true})).toBeVisible(); await expect(page.getByText(evidence.evaluationSplit!, {exact: true})).toBeVisible();
  const apply = page.getByRole('button', {name: 'Apply exact revision & hash decision', exact: true}); await expect(apply).toBeDisabled();
  await page.getByLabel('Private decision reason', {exact: true}).fill('Reviewed exact synthetic model, dataset, rights and evaluation.'); await page.getByRole('checkbox', {name: /I reviewed source rights/}).check(); await page.getByRole('checkbox', {name: /I inspected the package/}).check(); await apply.click();
  await expect(page.getByRole('status')).toContainText('now approved'); expect(decision).toMatchObject({revision: pending.revision, sourceHash: pending.sourceHash, rightsReviewed: true, packageReviewed: true});
});
test('accepted purchase disclosures preserve the frozen model and evaluation claims', async ({page}) => {
  const purchase = {educationReleaseId: sample.id, educationSnapshot: {...sample.snapshot, category: 'AI_ML', dataAiEvidence: evidence}};
  await componentHarness(page, 'education-purchase', {purchase}); await page.getByText('Inspect purchased education package & support', {exact: true}).click();
  await expect(page.getByText(evidence.modelSha256!, {exact: true})).toBeVisible(); await expect(page.getByText(evidence.limitations!, {exact: true})).toBeVisible(); await expect(page.getByText(/Source licensing does not transfer authorship/)).toBeVisible(); await reflow(page); await scan(page);
});
