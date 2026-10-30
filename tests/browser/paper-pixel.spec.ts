import {expect, test, type Page, type TestInfo} from '@playwright/test';

const pageErrors = new WeakMap<Page, string[]>();
test.beforeEach(async ({page}) => {
  const errors: string[] = [];
  pageErrors.set(page, errors);
  page.on('pageerror', error => errors.push(error.message));
});
test.afterEach(async ({page}) => expect(pageErrors.get(page)).toEqual([]));

async function fits(page: Page) {
  await page.evaluate(() => document.fonts.ready);
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth))
    .toBeLessThanOrEqual(page.viewportSize()!.width + 1);
}

async function capture(page: Page, info: TestInfo, name: string) {
  const path = info.outputPath(name + '.png');
  await page.screenshot({path, fullPage: true, animations: 'disabled'});
  await info.attach(name, {path, contentType: 'image/png'});
}

async function accountServices(page: Page, google = false, github = false) {
  await page.route('**/api/v1/auth/providers', route => route.fulfill({json: {google, github}}));
}

test('@cross-browser public exhibition exposes real projects and all four discovery paths', async ({page}, info) => {
  await page.goto('/');
  await expect(page.getByRole('heading', {level: 1, name: 'The craft behind the code.'})).toBeVisible();
  const paths = page.getByRole('navigation', {name: 'Explore getLancer', exact: true});
  for (const [name, href] of [['Projects', '#project-collection'], ['Components', '/components'], ['College projects', '/college-projects'], ['Learning', '/labs']]) {
    await expect(paths.getByRole('link', {name, exact: true})).toHaveAttribute('href', href);
  }
  const featured = page.getByRole('region', {name: 'Featured project demos', exact: true});
  await expect(featured).toBeVisible();
  const project = featured.getByRole('link', {name: 'Open project', exact: true}).first();
  await expect(project).toHaveAttribute('href', /^\/products\/[a-z0-9-]+$/);
  await expect(page.getByText(/illustrative|sample data/i).first()).toBeVisible();
  await expect(page.getByRole('heading', {name: 'Small pieces. Better products.', exact: true})).toBeVisible();
  await expect(page.getByRole('heading', {name: 'Ideas in practice.', exact: true})).toBeVisible();
  await expect(page.getByRole('heading', {name: 'Build and grow.', exact: true})).toBeVisible();
  await fits(page);
  const menu = page.getByRole('button', {name: 'Open navigation menu', exact: true});
  const menuBox = await menu.boundingBox();
  expect(menuBox).not.toBeNull();
  for (const action of await page.locator('.site-header .account > a').all()) {
    if (!await action.isVisible()) continue;
    const actionBox = await action.boundingBox();
    expect(actionBox).not.toBeNull();
    const intersects = menuBox!.x < actionBox!.x + actionBox!.width - 1
      && actionBox!.x < menuBox!.x + menuBox!.width - 1
      && menuBox!.y < actionBox!.y + actionBox!.height - 1
      && actionBox!.y < menuBox!.y + menuBox!.height - 1;
    expect(intersects, 'Navigation menu must not overlap account actions').toBe(false);
  }
  await capture(page, info, 'homepage');
  await project.click();
  await expect(page).toHaveURL(/\/products\/[a-z0-9-]+$/);
  await expect(page.locator('main h1')).toBeVisible();
  await fits(page);
});

test('@cross-browser discovery waits for navigation intent before requesting another route', async ({page}) => {
  const routeRequests: string[] = [];
  page.on('request', request => {
    const url = new URL(request.url());
    if (url.searchParams.has('_rsc') && url.pathname !== '/') routeRequests.push(url.pathname);
  });
  await page.goto('/');
  await expect(page.getByRole('heading', {level: 1, name: 'The craft behind the code.'})).toBeVisible();
  // A quiet network window lets viewport/idle prefetch work run if it is enabled.
  await page.waitForLoadState('networkidle');
  expect(routeRequests, 'Discovery must not start speculative route requests before a navigation choice').toEqual([]);
  await page.evaluate(() => {
    (window as Window & {paperNavigationProbe?: string}).paperNavigationProbe = 'retained-document';
  });
  const paths = page.getByRole('navigation', {name: 'Explore getLancer', exact: true});
  await paths.getByRole('link', {name: 'Components', exact: true}).click();
  await expect(page).toHaveURL(/\/components(?:[?#].*)?$/);
  await expect(page.locator('main h1')).toHaveText(/Build your next\s*good idea\./);
  await expect.poll(() => routeRequests.includes('/components')).toBe(true);
  expect(await page.evaluate(() => (window as Window & {paperNavigationProbe?: string}).paperNavigationProbe))
    .toBe('retained-document');
  await fits(page);
});

test('project search, filter removal and browser back retain confirmed URL state', async ({page}) => {
  await page.goto('/');
  const search = page.getByRole('textbox', {name: 'Search projects', exact: true});
  await search.fill('Stockroom');
  await page.locator('.paper-home-search').getByRole('button', {name: 'Search', exact: true}).click();
  await expect(page).toHaveURL(/q=Stockroom/);
  await expect(page.locator('.paper-project-grid .paper-project')).toHaveCount(1);
  await expect(page.locator('.paper-project-grid').getByRole('link', {name: 'Stockroom', exact: true})).toBeVisible();
  await page.getByRole('button', {name: /^Filters/}).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Business category', {exact: true}).selectOption('Inventory');
  await dialog.getByRole('button', {name: 'Apply filters', exact: true}).click();
  await expect(page).toHaveURL(/category=Inventory/);
  expect(new URL(page.url()).searchParams.get('q')).toBe('Stockroom');
  await expect(dialog).toBeHidden();
  await page.getByRole('button', {name: 'Remove Inventory', exact: true}).click();
  await expect.poll(() => new URL(page.url()).searchParams.has('category')).toBe(false);
  await page.goBack();
  await expect.poll(() => new URL(page.url()).searchParams.get('category')).toBe('Inventory');
  await expect(search).toHaveValue('Stockroom');
  await page.getByRole('button', {name: 'Clear all', exact: true}).click();
  await expect(search).toHaveValue('');
  await expect(page.locator('.paper-project-grid .paper-project')).toHaveCount(6);
  await fits(page);
});

test('empty search recovery never replaces the collection with invented results', async ({page}) => {
  await page.goto('/?q=unfindable-paper-pixel-fixture');
  await expect(page.locator('.paper-project-grid .paper-project')).toHaveCount(0);
  await expect(page.getByRole('heading', {name: 'No projects found.', exact: true})).toBeVisible();
  await page.getByRole('button', {name: /Clear search and filters/}).click();
  await expect(page.locator('.paper-project-grid .paper-project')).toHaveCount(6);
  await expect(page.getByRole('textbox', {name: 'Search projects', exact: true})).toHaveValue('');
});

test('homepage component examples are interactive and label local feedback honestly', async ({page}) => {
  await page.goto('/');
  const examples = page.getByRole('group', {name: 'Component examples', exact: true});
  await examples.getByRole('button', {name: 'Selection', exact: true}).click();
  await expect(examples.getByRole('button', {name: 'Selection', exact: true})).toHaveAttribute('aria-pressed', 'true');
  await page.getByLabel('What are you building?', {exact: true}).selectOption('Component library');
  await expect(page.getByLabel('What are you building?', {exact: true})).toHaveValue('Component library');
  await examples.getByRole('button', {name: 'Feedback', exact: true}).click();
  await page.getByRole('button', {name: 'Try feedback', exact: true}).click();
  await expect(page.getByRole('status').filter({hasText: 'Preview saved. No data was sent.'})).toHaveText('Preview saved. No data was sent.');
  await page.getByRole('button', {name: 'Reset preview', exact: true}).click();
  await expect(page.getByRole('button', {name: 'Try feedback', exact: true})).toBeVisible();
  await fits(page);
});

test('@cross-browser narrow navigation is keyboard recoverable and pages fit at 320px', async ({page}, info) => {
  await page.setViewportSize({width: 320, height: 812});
  await page.goto('/');
  const menu = page.getByRole('button', {name: 'Open navigation menu', exact: true});
  await menu.click();
  await expect(page.getByRole('menu')).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('menu')).toBeHidden();
  await expect(menu).toBeFocused();
  await fits(page);
  for (const route of ['/components', '/college-projects', '/labs', '/report', '/login', '/signup']) {
    await page.goto(route);
    await expect(page.locator('main:not([aria-busy="true"]):visible')).toHaveCount(1);
    await fits(page);
  }
  await capture(page, info, 'narrow-signup');
});

test('@cross-browser reduced motion keeps discovery and auth content visible without infinite motion', async ({page}) => {
  await page.emulateMedia({reducedMotion: 'reduce'});
  for (const route of ['/', '/login', '/preview/workspace']) {
    await page.goto(route);
    await expect(page.locator('main h1')).toBeVisible();
    expect(await page.locator('main').evaluate(node => node.getAnimations({subtree: true})
      .some(animation => animation.playState === 'running' && animation.effect?.getTiming().iterations === Infinity))).toBe(false);
    await fits(page);
  }
});

test('preview workspaces retain truthful sample controls and responsive dialogs', async ({page}, info) => {
  for (const route of ['workspace', 'components', 'college-projects', 'teams', 'business', 'delivery', 'maintenance', 'hosting', 'templates', 'trust', 'operations']) {
    await page.goto('/preview/' + route);
    await expect(page.locator('.studio-page-head h1')).toBeVisible();
    if(route==='operations')await expect(page.getByRole('region',{name:'Sample operations disclosure',exact:true})).toContainText('No real release evidence');
    else await expect(page.locator('.studio-preview')).toContainText('Sample data only');
    await fits(page);
  }
  await page.goto('/preview/maintenance');
  const create = page.getByRole('button', {name: 'Create support offer', exact: true});
  await create.click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByLabel('Monthly price (INR)', {exact: true})).toBeVisible();
  await fits(page);
  const bounds = await dialog.boundingBox();
  expect(bounds!.x).toBeGreaterThanOrEqual(0);
  expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(page.viewportSize()!.width + 1);
  await capture(page, info, 'maintenance-dialog');
  await page.keyboard.press('Escape');
  await expect(create).toBeFocused();
});

test('auth validation and failed sign-in keep input, focus and the real request contract', async ({page}) => {
  await accountServices(page);
  const requests: unknown[] = [];
  await page.route('**/api/v1/auth/login', route => {
    requests.push(route.request().postDataJSON());
    return route.fulfill({status: 401, json: {error: {code: 'INVALID_CREDENTIALS', message: 'Check your email and password.'}}});
  });
  await page.goto('/login');
  const submit = page.getByRole('button', {name: 'Log in', exact: true});
  await expect(submit).toBeEnabled();
  await submit.click();
  const email = page.getByLabel('Email address', {exact: true});
  const password = page.getByLabel('Password', {exact: true});
  await expect(email).toHaveAttribute('aria-invalid', 'true');
  await expect(page.getByRole('alert')).toBeFocused();
  await page.getByRole('alert').getByRole('link', {name: 'Email address', exact: true}).click();
  await expect(email).toBeFocused();
  expect(requests).toEqual([]);
  await email.fill('builder@example.test');
  await password.fill('synthetic-password-123');
  await page.getByRole('button', {name: 'Show password', exact: true}).click();
  await expect(password).toHaveAttribute('type', 'text');
  await page.getByRole('button', {name: 'Hide password', exact: true}).click();
  await expect(password).toHaveAttribute('type', 'password');
  await page.getByLabel('Keep me signed in', {exact: true}).check();
  await submit.click();
  await expect(page.getByRole('alert')).toContainText('Check your email and password.');
  await expect(page.getByRole('alert')).toBeFocused();
  await expect(email).toHaveValue('builder@example.test');
  await expect(password).toHaveValue('synthetic-password-123');
  await expect(submit).toBeEnabled();
  expect(requests).toEqual([{email: 'builder@example.test', password: 'synthetic-password-123', rememberMe: true}]);
});

test('password reset cannot claim success before acknowledgement and permits an explicit retry', async ({page}) => {
  await accountServices(page);
  let release!: () => void;
  const waiting = new Promise<void>(resolve => {release = resolve;});
  const requests: unknown[] = [];
  await page.route('**/api/v1/auth/password-reset/request', async route => {
    requests.push(route.request().postDataJSON());
    if (requests.length === 1) {
      await waiting;
      return route.fulfill({status: 503, json: {error: {code: 'UNAVAILABLE', message: 'Reset email temporarily unavailable.'}}});
    }
    return route.fulfill({json: {message: 'If an account exists, a reset email will be sent.'}});
  });
  await page.goto('/login');
  await expect(page.getByRole('button', {name: 'Log in', exact: true})).toBeEnabled();
  await page.getByLabel('Email address', {exact: true}).fill('builder@example.test');
  await page.getByRole('button', {name: 'Forgot password?', exact: true}).click();
  await page.getByRole('button', {name: 'Send reset link', exact: true}).click();
  await expect(page.getByRole('button', {name: 'Sending link…', exact: true})).toBeDisabled();
  await expect(page.getByLabel('Email address', {exact: true})).toBeDisabled();
  await expect(page.getByText(/If an account exists for this email/)).toHaveCount(0);
  release();
  await expect(page.getByRole('alert')).toContainText('Reset email temporarily unavailable.');
  await expect(page.getByLabel('Email address', {exact: true})).toHaveValue('builder@example.test');
  await page.getByRole('button', {name: 'Send reset link', exact: true}).click();
  await expect(page.getByRole('status').filter({hasText: 'If an account exists'})).toBeVisible();
  expect(requests).toEqual([{email: 'builder@example.test'}, {email: 'builder@example.test'}]);
});

test('provider startup rejects an untrusted redirect and stays recoverable', async ({page}) => {
  await accountServices(page, true, true);
  let starts = 0;
  await page.route('**/api/v1/auth/google/start', route => {
    starts++;
    expect(route.request().method()).toBe('POST');
    expect(route.request().postDataJSON()).toEqual({intent: 'login', acceptedTerms: false, rememberMe: false});
    return route.fulfill({json: {authorizationUrl: 'https://accounts.google.com.attacker.invalid/signin'}});
  });
  await page.goto('/login');
  const google = page.getByRole('button', {name: 'Continue with Google', exact: true});
  await expect(google).toBeEnabled();
  await google.click();
  await expect(page.getByRole('alert')).toContainText('Sign-in could not be started.');
  await expect(google).toBeEnabled();
  await expect(page).toHaveURL(/\/login$/);
  expect(starts).toBe(1);
});

test('failed sign-out retains the account and provides a recoverable response',async({page})=>{
 const {build}=await import('esbuild');
 const {readFileSync}=await import('node:fs');
 const bundle=await build({stdin:{contents:`import React from 'react';import{createRoot}from'react-dom/client';import Header from './app/components/header';createRoot(document.getElementById('root')).render(<Header preview={false}/>);`,loader:'tsx',resolveDir:process.cwd()},bundle:true,write:false,platform:'browser',format:'iife',jsx:'automatic',define:{'process.env.NODE_ENV':'"test"'},plugins:[{name:'header-router-fixture',setup(plugin){
  plugin.onResolve({filter:/^next\/(link|navigation)$/},args=>({path:args.path,namespace:'header-fixture'}));
  plugin.onLoad({filter:/.*/,namespace:'header-fixture'},args=>({contents:args.path==='next/link'?'import React from "react";export default function Link({href,children,...props}){return <a href={href} {...props}>{children}</a>}':'export const usePathname=()=>"/";',loader:'jsx',resolveDir:process.cwd()}));
 }}]});
 await page.route('**/paper-header-fixture',route=>route.fulfill({contentType:'text/html',body:'<!doctype html><html lang="en"><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><div id="root"></div></body></html>'}));
 await page.route('**/api/v1/me',route=>route.fulfill({json:{id:'header-fixture',displayName:'Fixture Builder',email:'builder@example.test',roles:['DEVELOPER']}}));
 let writes=0;
 await page.route('**/api/v1/auth/logout',route=>{writes++;expect(route.request().method()).toBe('POST');expect(route.request().headers()['x-requested-with']).toBe('getlancer');return route.fulfill({status:503,json:{error:{code:'SERVICE_UNAVAILABLE',message:'Sign-out could not be confirmed. Try again.'}}});});
 await page.goto('/paper-header-fixture');
 await page.addStyleTag({content:readFileSync('app/paper.css','utf8')});
 await page.addScriptTag({content:bundle.outputFiles[0].text});
 const account=page.getByRole('button',{name:'Account menu for Fixture Builder',exact:true});
 await account.click();await page.getByRole('menuitem',{name:'Log out',exact:true}).click();
 await expect(page.getByRole('alert')).toContainText('Sign-out could not be confirmed.');
 await expect(account).toBeVisible();expect(writes).toBe(1);
 await page.getByRole('button',{name:'Dismiss sign-out message',exact:true}).click();
 await expect(page.getByRole('alert')).toHaveCount(0);
 await account.click();await expect(page.getByRole('menuitem',{name:'Log out',exact:true})).toBeEnabled();
 await page.keyboard.press('Escape');await expect(account).toBeFocused();
});
