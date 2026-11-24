import {expect, test, type Page} from '@playwright/test';

async function fits(page: Page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(page.viewportSize()!.width + 1);
}

test('sample project actions open the right form and complete a sample inquiry', async ({page}) => {
  const liveWrites: string[] = [];
  page.on('request', request => {
    const path = new URL(request.url()).pathname;
    if (path.startsWith('/api/v1/') && request.method() !== 'GET') liveWrites.push(path);
  });
  await page.goto('/products/stockroom');
  const inquiry = page.getByRole('link', {name:'Try a sample inquiry'});
  await expect(inquiry).toBeInViewport();
  await fits(page);
  await inquiry.click();

  const dialog = page.getByRole('dialog');
  await expect(dialog.getByRole('heading', {name:'Tell the builder what you need'})).toBeVisible();
  await expect(dialog.getByText(/Inspired by Stockroom/)).toBeVisible();
  await dialog.getByLabel('Describe your project').fill('Create a sample stock workflow with searchable inventory and purchase approvals.');
  await dialog.getByLabel('Budget', {exact:true}).selectOption('NEED_ESTIMATE');
  await dialog.getByLabel('Timeline', {exact:true}).selectOption('FLEXIBLE');
  await dialog.getByRole('button', {name:'Send inquiry'}).click();
  await expect(dialog).toBeHidden();
  const requests = page.getByRole('tab', {name:'My requests (1)'});
  await expect(requests).toHaveAttribute('aria-selected', 'true');
  await expect(requests).toBeFocused();
  await expect(page.getByText(/Inquiry saved. Open the demo inbox/)).toBeVisible();

  await page.goto('/products/stockroom');
  await page.getByRole('link', {name:'Try a sample report'}).click();
  await expect(dialog.getByRole('heading', {name:'Report a concern'})).toBeVisible();
  await expect(dialog.getByText(/Stockroom/)).toBeVisible();
  expect(liveWrites).toEqual([]);
});

test('demo save and publish links open a usable destination', async ({page}) => {
  await page.goto('/');
  await page.getByRole('link', {name:'Save Stockroom in the demo'}).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByRole('heading', {name:'Stockroom',exact:true})).toBeVisible();
  await dialog.getByRole('button', {name:'Save project',exact:true}).click();
  await expect(dialog.getByRole('button', {name:'Unsave project',exact:true})).toBeVisible();

  await page.goto('/preview/workspace?view=saved');
  await expect(page.getByRole('tab', {name:'Saved (1)',exact:true})).toHaveAttribute('aria-selected','true');
  await expect(page.getByRole('heading', {name:'Stockroom',exact:true})).toBeVisible();
  await page.goto('/');
  await page.getByRole('link', {name:'Try publishing',exact:true}).click();
  await expect(page.getByRole('tab', {name:'Showcases',exact:true})).toHaveAttribute('aria-selected','true');
  await expect(page.getByRole('button', {name:'New draft',exact:true})).toBeEnabled();
  await page.getByRole('button', {name:'New draft',exact:true}).click();
  await expect(dialog.getByRole('heading', {name:'Create a showcase'})).toBeVisible();
  await fits(page);
});

test('missing projects and empty tool searches provide a way forward', async ({page}) => {
  for (const [url, name] of [['/inquiry', 'Choose a project to inquire about'], ['/inquiry?product=missing-project', 'Project unavailable']]) {
    await page.goto(url);
    await expect(page.getByRole('heading',{name,exact:true})).toBeVisible();
    await expect(page.getByRole('main').getByRole('link', {name:'Explore projects'})).toHaveAttribute('href','/#project-collection');
    await expect(page.locator('main form')).toHaveCount(0);
  }

  await page.goto('/preview/workspace?view=inquiry&project=missing-project');
  await expect(page.getByRole('alert')).toContainText('Choose another project to continue.');
  await expect(page.getByRole('dialog')).toHaveCount(0);

  if (page.viewportSize()!.width < 400) await page.setViewportSize({width:320,height:720});
  await page.goto('/preview/areas/clients');
  if (page.viewportSize()!.width === 320) {
    const tabs = page.getByRole('navigation', {name:'Workspace navigation'}).getByRole('button');
    const positions = await tabs.evaluateAll(buttons => buttons.map(button => button.getBoundingClientRect().top));
    expect(new Set(positions).size).toBe(1);
  }
  const main = page.getByRole('main');
  const directorySearch = main.getByRole('searchbox', {name:'Find a tool in Clients'});
  await directorySearch.fill('missing-tool');
  await expect(main.getByRole('status')).toHaveText('No matching tools.');
  await main.getByRole('button', {name:'Clear search'}).click();
  await expect(directorySearch).toBeFocused();
  await expect(main.getByRole('link', {name:/Delivery & payments/})).toBeVisible();

  await page.getByRole('button', {name:'Find a tool',exact:true}).click();
  const panel = page.getByRole('region', {name:'Find a workspace tool'});
  const toolSearch = panel.getByRole('searchbox', {name:'Search workspace tools'});
  await toolSearch.fill('missing-tool');
  await expect(panel.getByRole('status')).toHaveText('No tools match your search.');
  await panel.getByRole('button', {name:'Clear search'}).click();
  await expect(toolSearch).toBeFocused();
  await expect(panel.getByRole('link', {name:'Delivery & payments',exact:true})).toBeVisible();
  await fits(page);
});
