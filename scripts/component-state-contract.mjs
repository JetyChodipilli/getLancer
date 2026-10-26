import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

export const stateNames = ['hover', 'focus', 'active', 'disabled', 'loading', 'empty', 'error', 'success'];
const synchronous = 'Synchronous local interaction; no asynchronous work or loading interval.';
const enabled = 'No disabled branch: all actions stay available during synchronous local interaction.';
const fixedContent = 'Fixed synthetic content; no empty collection or empty-entry workflow.';
const noFailure = 'No fallible operation or user-data validation in this local interaction.';
const defaults = {
  hover: 'Visible hover ring on a representative enabled control.',
  focus: 'Every initially visible enabled control receives visible keyboard focus.',
  active: 'Pointer-down ring differs from hover; persistent selected/pressed state is checked where present.',
  disabled: {notApplicable: enabled}, loading: {notApplicable: synchronous},
  empty: {notApplicable: fixedContent}, error: {notApplicable: noFailure},
  success: 'The component-specific local interaction produces its documented result.'
};
const entry = (slug, changes = {}) => ({slug, states: {...defaults, ...changes}});
export const componentStates = [
  entry('studio-navigation'), entry('focus-sidebar'),
  entry('project-brief', {empty: 'Blank required name/goal and empty confirmation.', error: 'Native required validation blocks a blank submission.'}),
  entry('portfolio-card'),
  entry('quiet-sign-in', {empty: 'Blank credentials are rejected by native required validation.', error: 'Wrong synthetic credentials display failure before valid credentials succeed.'}),
  entry('project-pulse'), entry('workspace-switcher'), entry('section-tabs'),
  entry('search-navigation', {empty: 'Unmatched search hides all results and announces no destinations.'}),
  entry('folder-rail'), entry('filter-rail'), entry('settings-rail'),
  entry('feedback-form', {disabled: 'Submit is disabled during the simulated save and re-enabled afterward.', loading: 'Sending label and saving status precede success.', empty: 'Blank note is rejected with an associated error.', error: 'Short note sets aria-invalid and focuses the field; corrected input clears the error.'}),
  entry('estimate-form', {empty: 'Blank required screen count is rejected.', error: 'Native minimum/maximum validation prevents invalid estimates.'}),
  entry('step-form', {empty: 'Blank required project name is rejected.', error: 'Invalid first step stays visible and receives native validation.'}),
  entry('comparison-card'), entry('expandable-case-study'), entry('task-card'),
  entry('access-code-form', {empty: 'Empty code is rejected with an associated visible error.', error: 'Wrong code sets aria-invalid; correct code clears the error.'}),
  entry('recovery-state', {disabled: 'Recovery action is disabled during preparation and re-enabled afterward.', loading: 'Preparing label and status precede the privacy-preserving recovery result.'}),
  entry('session-panel', {empty: 'Ended synthetic session shows the restart action and transfers focus.'}),
  entry('activity-dashboard'),
  entry('sortable-inventory', {empty: 'Unmatched search removes every row and announces no inventory items.'}),
  entry('release-checklist')
];

export const recipes = () => JSON.parse(readFileSync(resolve('backend/src/main/resources/catalog/components.json'), 'utf8'));
export function validateStateMatrix(catalog, matrix = componentStates) {
  assert.equal(new Set(matrix.map(row => row.slug)).size, matrix.length, 'Duplicate state matrix component');
  assert.deepEqual(matrix.map(row => row.slug).sort(), catalog.map(row => row.slug).sort(), 'State matrix must cover exactly the current catalog');
  for (const row of matrix) {
    assert.deepEqual(Object.keys(row.states).sort(), [...stateNames].sort(), row.slug + ' must decide all eight states');
    for (const [state, value] of Object.entries(row.states)) {
      assert.ok(typeof value === 'string' ? value.length > 15 : typeof value?.notApplicable === 'string' && value.notApplicable.length > 25, row.slug + ':' + state + ' needs a concrete assertion or N/A reason');
    }
  }
  return matrix.length;
}

/** Verify real sequential navigation; programmatic focus would hide tabindex defects. */
export async function verifyKeyboardFocus(page, slug) {
  const {expect} = await import('@playwright/test');
  const controls = page.locator('button:visible:not(:disabled),a[href]:visible,input:visible:not(:disabled),select:visible:not(:disabled),textarea:visible:not(:disabled),summary:visible');
  expect(await controls.count(), slug + ' needs an interactive control').toBeGreaterThan(0);
  for (const control of await controls.all()) {
    await page.keyboard.press('Tab');
    await expect(control, slug + ': keyboard traversal').toBeFocused();
    const focus = await control.evaluate(element => ({visible: element.matches(':focus-visible'), width: parseFloat(getComputedStyle(element).outlineWidth), style: getComputedStyle(element).outlineStyle}));
    expect(focus.visible, slug + ': keyboard focus indicator').toBe(true);
    expect(focus.width).toBeGreaterThanOrEqual(2);
    expect(focus.style).not.toBe('none');
  }
}

/** Used by both the ordinary browser suite and the build-independent runner. */
export async function verifyComponentStates(page, recipe) {
  const {expect} = await import('@playwright/test');
  const errors = [];
  const failed = error => errors.push(error.message);
  page.on('pageerror', failed);
  if (['feedback-form', 'recovery-state'].includes(recipe.slug)) await page.clock.install();
  const reset = async () => {await page.goto('about:blank'); await page.setContent(recipe.files['index.html']);};
  await reset();
  const controls = page.locator('button:visible:not(:disabled),a[href]:visible,input:visible:not(:disabled),select:visible:not(:disabled),textarea:visible:not(:disabled),summary:visible');
  expect(await controls.count(), recipe.slug + ' needs an interactive control').toBeGreaterThan(0);
  await verifyKeyboardFocus(page, recipe.slug);
  // Test actual computed pointer feedback rather than only finding selectors in CSS.
  const pointer = controls.first();
  await page.mouse.move(0, 0);
  const normal = await pointer.evaluate(element => getComputedStyle(element).boxShadow);
  await pointer.hover();
  const hover = await pointer.evaluate(element => getComputedStyle(element).boxShadow);
  expect(hover, recipe.slug + ': hover feedback').not.toBe(normal);
  await page.mouse.down();
  const active = await pointer.evaluate(element => getComputedStyle(element).boxShadow);
  expect(active, recipe.slug + ': active feedback').not.toBe(hover);
  await page.mouse.up();
  await reset();
  const text = async (selector, expected) => expect(page.locator(selector)).toHaveText(expected);
  const nativeInvalid = async (selector, trigger) => {
    await trigger();
    expect(await page.locator(selector).evaluate(input => !input.validity.valid && input.validationMessage.length > 0)).toBe(true);
    await expect(page.locator(selector)).toBeFocused();
  };
  switch (recipe.slug) {
    case 'studio-navigation':
      await page.locator('#menu').click(); await expect(page.locator('#menu')).toHaveAttribute('aria-expanded', 'true');
      await page.getByRole('link', {name: 'About', exact: true}).click(); await text('#selection', 'About selected. This demo stays in the preview.');
      await expect(page.getByRole('link', {name: 'About', exact: true})).toHaveAttribute('aria-current', 'page');
      await page.locator('#menu').click(); await expect(page.locator('#links')).toBeHidden(); break;
    case 'focus-sidebar':
      await page.getByRole('button', {name: 'Projects', exact: true}).click(); await text('#heading', 'Projects');
      await expect(page.getByRole('button', {name: 'Projects', exact: true})).toHaveAttribute('aria-current', 'page');
      await page.locator('#toggle').click(); await expect(page.locator('#rail')).toBeHidden();
      await page.locator('#toggle').click(); await expect(page.locator('#rail')).toBeVisible(); break;
    case 'project-brief':
      await expect(page.locator('#name')).toHaveValue(''); await text('#result', '');
      await nativeInvalid('#name', () => page.getByRole('button', {name: 'Save brief', exact: true}).click());
      await page.locator('#name').fill('Garden planner');
      await nativeInvalid('#goal', () => page.getByRole('button', {name: 'Save brief', exact: true}).click());
      await page.locator('#goal').fill('Coordinate the sample garden.');
      await page.getByRole('button', {name: 'Save brief', exact: true}).click(); await text('#result', 'Brief saved in this preview: Garden planner. No data was sent.');
      await page.getByRole('button', {name: 'Reset', exact: true}).click(); await text('#result', ''); await expect(page.locator('#name')).toHaveValue(''); break;
    case 'portfolio-card':
      await page.locator('#save').click(); await expect(page.locator('#save')).toHaveAttribute('aria-pressed', 'true'); await text('#saved', 'Added to sample saved projects.');
      await page.locator('summary').click(); await expect(page.locator('details')).toHaveAttribute('open', '');
      await page.locator('#save').click(); await expect(page.locator('#save')).toHaveAttribute('aria-pressed', 'false'); break;
    case 'quiet-sign-in':
      await page.locator('#email').fill(''); await nativeInvalid('#email', () => page.getByRole('button', {name: 'Try sign-in', exact: true}).click());
      await page.locator('#email').fill('wrong@example.test'); await page.getByRole('button', {name: 'Try sign-in', exact: true}).click(); await text('#result', 'Use the displayed synthetic demo credentials.');
      await page.locator('#email').fill('member@example.test'); await page.locator('#show').click(); await expect(page.locator('#password')).toHaveAttribute('type', 'text');
      await page.locator('#show').click(); await expect(page.locator('#password')).toHaveAttribute('type', 'password'); await expect(page.locator('#show')).toHaveAttribute('aria-pressed', 'false');
      await page.getByRole('button', {name: 'Try sign-in', exact: true}).click(); await text('#result', 'Demo signed in. No account service was contacted.'); break;
    case 'project-pulse':
      await expect(page.locator('#rows tr')).toHaveCount(3); await page.locator('#status').selectOption('Active'); await expect(page.locator('#rows tr')).toHaveCount(2);
      await page.locator('#sort').click(); await expect(page.locator('#sort')).toHaveAttribute('aria-pressed', 'true'); await expect(page.locator('#rows tr').first()).toContainText('Research portal'); break;
    case 'workspace-switcher':
      await page.locator('#space').selectOption('Research'); await text('#heading', 'Research workspace');
      await page.locator('#projects').click(); await text('#status', 'Research projects opened'); break;
    case 'section-tabs':
      await page.locator('[data-view="Files"]').click(); await text('#heading', 'Files'); await text('#content', 'README.md and LICENSE are available.');
      await expect(page.locator('[aria-pressed="true"]')).toHaveCount(1); await expect(page.locator('[data-view="Files"]')).toHaveAttribute('aria-pressed', 'true'); break;
    case 'search-navigation':
      await page.locator('#search').fill('nothing matches'); await expect(page.locator('[data-label]:visible')).toHaveCount(0); await text('#status', 'No destinations found');
      await page.locator('#search').fill('settings'); await expect(page.locator('[data-label]:visible')).toHaveCount(1);
      await page.locator('[data-label="Settings"]').click(); await text('#status', 'Settings opened'); break;
    case 'folder-rail':
      await page.getByText('Assets', {exact: true}).click(); await page.getByRole('button', {name: 'Icons', exact: true}).click(); await text('#heading', 'Icons'); await text('#status', 'Icons opened'); break;
    case 'filter-rail':
      await page.locator('#filter').selectOption('Archived'); await expect(page.locator('[data-state]:visible')).toHaveCount(1); await text('#status', '1 projects shown');
      await page.locator('#clear').click(); await expect(page.locator('[data-state]:visible')).toHaveCount(3); break;
    case 'settings-rail':
      await page.locator('[data-section="Notifications"]').click(); await expect(page.locator('[data-section="Notifications"]')).toHaveAttribute('aria-pressed', 'true');
      await expect(page.locator('#notifications')).toBeVisible(); await page.locator('#updates').uncheck(); await text('#status', 'Sample updates disabled'); break;
    case 'feedback-form':
      await page.locator('#submit').click(); await expect(page.locator('#note-error')).toBeVisible(); await expect(page.locator('#note')).toHaveAttribute('aria-invalid', 'true'); await expect(page.locator('#note')).toBeFocused();
      await page.locator('#note').fill('Useful synthetic feedback.'); await page.locator('#submit').click(); await expect(page.locator('#note-error')).toBeHidden(); await expect(page.locator('#note')).not.toHaveAttribute('aria-invalid', 'true');
      await expect(page.locator('#submit')).toBeDisabled(); await text('#submit', 'Sending…'); await text('#status', 'Saving synthetic feedback…');
      await page.clock.fastForward(700); await expect(page.locator('#submit')).toBeEnabled(); await text('#status', 'Sample feedback saved. No network request was made.'); break;
    case 'estimate-form':
      await page.locator('#screens').fill(''); await nativeInvalid('#screens', () => page.getByRole('button', {name: 'Calculate estimate', exact: true}).click());
      await page.locator('#screens').fill('0'); await nativeInvalid('#screens', () => page.getByRole('button', {name: 'Calculate estimate', exact: true}).click());
      await page.locator('#screens').fill('21'); await nativeInvalid('#screens', () => page.getByRole('button', {name: 'Calculate estimate', exact: true}).click());
      await page.locator('#screens').fill('4'); await page.locator('#api').check(); await page.getByRole('button', {name: 'Calculate estimate', exact: true}).click(); await text('#status', '13 sample effort days · illustrative estimate'); break;
    case 'step-form':
      await nativeInvalid('#name', () => page.getByRole('button', {name: 'Continue', exact: true}).click()); await expect(page.locator('#second')).toBeHidden();
      await page.locator('#name').fill('Garden planner'); await page.getByRole('button', {name: 'Continue', exact: true}).click(); await text('#review', 'Garden planner'); await expect(page.locator('#back')).toBeFocused();
      await page.locator('#save').click(); await text('#status', 'Sample brief saved in memory'); await page.locator('#back').click(); await expect(page.locator('#name')).toBeFocused(); break;
    case 'comparison-card':
      await page.locator('[data-plan="Studio"]').click(); await expect(page.locator('[data-plan="Studio"]')).toHaveAttribute('aria-pressed', 'true'); await text('#status', 'Studio selected · synthetic comparison only');
      await page.locator('[data-plan="Starter"]').click(); await expect(page.locator('[aria-pressed="true"]')).toHaveCount(1); break;
    case 'expandable-case-study':
      await page.locator('summary').click(); await expect(page.locator('details')).toHaveAttribute('open', ''); await page.locator('#like').click(); await expect(page.locator('#like')).toHaveAttribute('aria-pressed', 'true'); await text('#status', 'Feedback recorded in this preview'); break;
    case 'task-card':
      await page.locator('#toggle').click(); await text('#state', 'Review complete'); await expect(page.locator('#progress')).toHaveAttribute('value', '100'); await expect(page.locator('#toggle')).toHaveAttribute('aria-pressed', 'true');
      await page.locator('#toggle').click(); await expect(page.locator('#progress')).toHaveAttribute('value', '0'); break;
    case 'access-code-form':
      await page.getByRole('button', {name: 'Verify sample code', exact: true}).click(); await expect(page.locator('#code-error')).toBeVisible(); await expect(page.locator('#code')).toHaveAttribute('aria-invalid', 'true'); await expect(page.locator('#code')).toBeFocused();
      await page.locator('#code').fill('246810'); await page.getByRole('button', {name: 'Verify sample code', exact: true}).click(); await expect(page.locator('#code-error')).toBeHidden(); await expect(page.locator('#code')).toHaveAttribute('aria-invalid', 'false'); await text('#status', 'Sample access verified'); break;
    case 'recovery-state':
      await page.locator('#recover').click(); await expect(page.locator('#recover')).toBeDisabled(); await text('#recover', 'Preparing…'); await text('#status', 'Preparing a synthetic recovery state');
      await page.clock.fastForward(700); await expect(page.locator('#recover')).toBeEnabled(); await text('#status', 'If this sample identity existed, recovery instructions would be available. No email was sent.'); break;
    case 'session-panel':
      await page.locator('#signout').click(); await text('#session', 'Synthetic session ended'); await expect(page.locator('#signin')).toBeFocused(); await expect(page.locator('#signout')).toBeHidden();
      await page.locator('#signin').click(); await text('#session', 'Synthetic session active'); await expect(page.locator('#signout')).toBeFocused(); break;
    case 'activity-dashboard':
      await page.locator('#range').selectOption('30'); await text('#views', '168'); await text('#downloads', '48'); await text('#status', '30-day synthetic summary'); break;
    case 'sortable-inventory':
      await expect(page.locator('tbody tr')).toHaveCount(3); await page.locator('#search').fill('nothing matches'); await expect(page.locator('tbody tr')).toHaveCount(0); await text('#status', 'No inventory items found');
      await page.locator('#search').fill(''); await page.locator('#sort').click(); await expect(page.locator('tbody tr').first()).toContainText('Research journal'); break;
    case 'release-checklist':
      await expect(page.locator('#progress')).toHaveAttribute('value', '0');
      for (const box of await page.locator('input[type="checkbox"]').all()) await box.check();
      await expect(page.locator('#progress')).toHaveAttribute('value', '3'); await text('#status', 'Sample release ready');
      await page.locator('input[type="checkbox"]').first().uncheck(); await text('#status', 'Review progress: 2 of 3 checks'); break;
    default: throw Error('Missing behavioral oracle for ' + recipe.slug);
  }
  expect(errors, recipe.slug + ' script errors').toEqual([]);
  page.off('pageerror', failed);
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  validateStateMatrix(recipes());
  if (process.argv.includes('--browser')) {
    const {chromium} = await import('@playwright/test');
    const browser = await chromium.launch({headless: true, ...(process.env.COMPONENT_STATE_BROWSER_EXECUTABLE ? {executablePath: process.env.COMPONENT_STATE_BROWSER_EXECUTABLE} : {})});
    try {
      for (const recipe of recipes()) {
        const page = await browser.newPage({viewport: {width: 1280, height: 900}});
        try {await verifyComponentStates(page, recipe); console.log('STATE PASS ' + recipe.slug);} finally {await page.close();}
      }
      console.log('COMPONENT STATES VERIFIED: ' + recipes().length + ' components; Chromium ' + browser.version());
    } finally {await browser.close();}
  } else console.log('COMPONENT STATE MATRIX VERIFIED: ' + recipes().length);
}
