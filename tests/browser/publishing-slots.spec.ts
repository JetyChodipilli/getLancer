import { test, expect, type Page } from '@playwright/test';
import { samplePublishing, type SlotPool } from '../../lib/publishing';

type CheckoutOptions = { handler: (result: Record<string,string>) => Promise<void> };
type TestWindow = { checkoutOpens: number; checkoutOptions: CheckoutOptions; Razorpay: unknown };

async function noOverflow(page: Page) { await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(page.viewportSize()!.width + 1); }
async function fixture(page: Page, admin = false, resumedAmount?: number) {
  const data = samplePublishing(), orders: Record<string, unknown>[] = [], prices: Record<string, unknown>[] = [];
  const state = { actor: 'builder-fixture', orderMode: 'normal' };
  for (const [index, pool] of (['PROJECT', 'TEMPLATE', 'COMPONENT'] as SlotPool[]).entries()) data.prices[pool] = { ...data.prices[pool], amountMinor: (index + 1) * 10000, enabled: true, configured: true, salesEnabled: true, mode: 'live', reason: '' };
  await page.addInitScript(() => {
    (window as unknown as TestWindow).checkoutOpens = 0;
    (window as unknown as TestWindow).Razorpay = class { constructor(options: Record<string, unknown>) { (window as unknown as TestWindow).checkoutOptions = options as unknown as CheckoutOptions; } on() {} open() { (window as unknown as TestWindow).checkoutOpens++; } };
  });
  await page.route('**/api/v1/**', async route => {
    const request = route.request(), url = new URL(request.url()), path = url.pathname.replace('/api/v1', ''), method = request.method();
    if (path === '/me') return route.fulfill({ json: { id: state.actor, roles: admin ? ['ADMIN', 'DEVELOPER'] : ['DEVELOPER'], displayName: 'Test builder', emailVerified: true, profile: { approval_status: 'APPROVED' } } });
    if (path === '/me/publishing-slots') return route.fulfill({ json: data });
    if (path === '/publishing-slots/pricing') return route.fulfill({ json: data.prices });
    if ((path === '/me/publishing-slot-purchases' || path === '/admin/publishing-slot-purchases') && method === 'GET') return route.fulfill({ json: { items: [], page: 0, hasMore: false } });
    if (path === '/me/publishing-slot-purchases' && method === 'POST') {
      const body = request.postDataJSON(); orders.push({ ...body, key: request.headers()['idempotency-key'] });
      if (state.orderMode === 'unknown') return route.fulfill({ status: 409, json: { error: { code: 'PAYMENT_ORDER_UNKNOWN', message: 'Order creation is uncertain. Reconcile before paying again.' } } });
      return route.fulfill({ json: { id: 'receipt-' + body.pool, pool: body.pool, amountMinor: resumedAmount ?? body.amountMinor, mode: 'live', status: 'ORDER_CREATED', refundedMinor: 0, grantsSlot: false, orderId: state.orderMode === 'incomplete' ? null : 'order_' + body.pool, keyId: 'rzp_live_fixture' } });
    }
    if (path.endsWith('/verify')) {
      const pool = path.split('/').at(-2)!.replace('receipt-', '') as SlotPool; data.capacities[pool].purchased++; data.capacities[pool].limit++;
      if (pool === 'PROJECT') { data.capacities.PROJECT.extraLimit++; data.capacities.PROJECT.availableRegular++; data.capacities.PROJECT.availableCollege++; }
      return route.fulfill({ json: { id: 'receipt-' + pool, pool, amountMinor: data.prices[pool].amountMinor, mode: 'live', status: 'CAPTURED', refundedMinor: 0, grantsSlot: true } });
    }
    if (path.startsWith('/admin/publishing-slots/') && method === 'PUT') { const pool = path.split('/')[3] as SlotPool, body = request.postDataJSON(); prices.push({ pool, ...body }); data.prices[pool] = { ...data.prices[pool], amountMinor: body.amountMinor, enabled: body.enabled, salesEnabled: body.enabled }; return route.fulfill({ json: data.prices[pool] }); }
    return route.fulfill({ status: 503, json: { error: { code: 'FIXTURE_UNAVAILABLE', message: 'This isolated fixture does not implement that service.' } } });
  });
  return { data, orders, prices, state };
}

test('an account change before purchasing clears the quote and prevents gateway or order creation', async ({ page }) => {
  const f = await fixture(page); await page.goto('/workspace/slots'); await page.getByRole('button', { name: 'Review price & buy one slot', exact: true }).click();
  const dialog = page.getByRole('dialog'); await dialog.getByRole('checkbox').check(); f.state.actor = 'another-account';
  await dialog.getByRole('button', { name: 'Continue to secure checkout', exact: true }).click();
  await expect(dialog).not.toBeVisible(); await expect(page.getByText('The account changed. Reload publishing slots before purchasing.', { exact: true })).toBeVisible();
  expect(f.orders).toHaveLength(0); expect(await page.evaluate(() => (window as unknown as TestWindow).checkoutOpens)).toBe(0);
});

for (const mode of ['unknown', 'incomplete']) test(mode + ' orders prevent checkout and preserve the key during recovery', async ({ page }) => {
  const f = await fixture(page); f.state.orderMode = mode; await page.goto('/workspace/slots'); await page.getByRole('button', { name: 'Review price & buy one slot', exact: true }).click();
  const dialog = page.getByRole('dialog'), proceed = dialog.getByRole('button', { name: 'Continue to secure checkout', exact: true }); await dialog.getByRole('checkbox').check();
  const message = mode === 'unknown' ? 'Order creation is uncertain. Reconcile before paying again.' : 'Order creation is unresolved. Reconcile this receipt before paying again.';
  await proceed.click(); await expect(dialog.getByText(message, { exact: true })).toBeVisible(); expect(await page.evaluate(() => (window as unknown as TestWindow).checkoutOpens)).toBe(0);
  await proceed.click(); await expect.poll(() => f.orders.length).toBe(2); await expect(proceed).toBeEnabled(); expect(f.orders[1].key).toBe(f.orders[0].key);
  f.state.orderMode = 'normal'; await proceed.click(); await expect(dialog).not.toBeVisible(); await expect.poll(() => page.evaluate(() => (window as unknown as TestWindow).checkoutOpens)).toBe(1);
  expect(f.orders[2].key).toBe(f.orders[0].key);
});

test('four free allowances are visible in preview and every slot purchase stays disabled', async ({ page }, info) => {
  const requests: string[] = []; page.on('request', r => { if (new URL(r.url()).pathname.startsWith('/api/v1/') || (new URL(r.url()).hostname==='razorpay.com'||new URL(r.url()).hostname.endsWith('.razorpay.com'))) requests.push(r.url()); });
  await page.goto('/preview/slots'); await expect(page.getByRole('heading', { name: 'Room for every kind of work.', exact: true })).toBeVisible();
  await expect(page.locator('.studio-metric')).toHaveCount(4);
  for (const card of await page.locator('.studio-metric').all()) await expect(card).toContainText('3 free slots');
  for (const pool of ['PROJECT', 'TEMPLATE', 'COMPONENT']) { await page.getByLabel('Slot category', { exact: true }).selectOption(pool); await expect(page.getByRole('button', { name: 'Review price & buy one slot', exact: true })).toBeDisabled(); await noOverflow(page); }
  await page.getByLabel('Slot category', { exact: true }).selectOption('PROJECT');
  await page.screenshot({ path: info.outputPath('publishing-slots.jpg'), type: 'jpeg', quality: 85, fullPage: true }); expect(requests).toEqual([]);
});

test('category checkout requires explicit consent and verifies the purchased pool', async ({ page }) => {
  const f = await fixture(page); await page.goto('/workspace/slots?pool=TEMPLATE');
  await expect(page.getByLabel('Slot category', { exact: true })).toHaveValue('TEMPLATE');
  await page.getByRole('button', { name: 'Review price & buy one slot', exact: true }).click();
  const dialog = page.getByRole('dialog'), proceed = dialog.getByRole('button', { name: 'Continue to secure checkout', exact: true });
  await expect(proceed).toBeDisabled(); await expect(dialog).toContainText('₹200.00');
  await dialog.getByRole('checkbox').check(); await proceed.click(); await expect(dialog).not.toBeVisible();
  await expect.poll(() => page.evaluate(() => (window as unknown as TestWindow).checkoutOpens)).toBe(1);
  expect(f.orders).toHaveLength(1); expect(f.orders[0]).toMatchObject({ pool: 'TEMPLATE', amountMinor: 20000, purchaseConsent: true });
  await page.evaluate(async () => { await (window as unknown as TestWindow).checkoutOptions.handler({ razorpay_payment_id: 'pay_fixture', razorpay_signature: 'a'.repeat(64) }); });
  await expect(page.getByText('Payment verified. One reusable slot was added.', { exact: true })).toBeVisible();
  await expect(page.locator('.studio-metric').filter({ hasText: 'Templates' })).toContainText('1 / 4');
  await expect(page.locator('.studio-metric').filter({ hasText: 'Components' })).toContainText('3 / 3'); await noOverflow(page);
});

test('resumed orders show their frozen price and require renewed consent before gateway opens', async ({ page }) => {
  const f = await fixture(page, false, 19900); await page.goto('/workspace/slots'); await page.getByRole('button', { name: 'Review price & buy one slot', exact: true }).click();
  const dialog = page.getByRole('dialog'); await dialog.getByRole('checkbox').check(); await dialog.getByRole('button', { name: 'Continue to secure checkout', exact: true }).click();
  await expect(dialog).toContainText('₹199.00'); await expect(dialog.getByRole('checkbox')).not.toBeChecked(); await expect(dialog.getByRole('button', { name: 'Continue to secure checkout', exact: true })).toBeDisabled();
  expect(await page.evaluate(() => (window as unknown as TestWindow).checkoutOpens)).toBe(0);
  await dialog.getByRole('checkbox').check(); await dialog.getByRole('button', { name: 'Continue to secure checkout', exact: true }).click(); await expect(dialog).not.toBeVisible();
  expect(f.orders[1]).toMatchObject({ pool: 'PROJECT', amountMinor: 19900, purchaseConsent: true }); expect(f.orders[1].key).toBe(f.orders[0].key); await noOverflow(page);
});

test('administrator sets independent prices and category switching retains other prices', async ({ page }) => {
  const f = await fixture(page, true); await page.goto('/workspace/slots');
  await page.getByLabel('Price per project slot (INR)', { exact: true }).fill('149'); await page.getByRole('button', { name: 'Save project slot pricing', exact: true }).click();
  await expect(page.getByText('Project slot price saved. Existing orders keep their original category and price.', { exact: true })).toBeVisible();
  expect(f.prices[0]).toMatchObject({ pool: 'PROJECT', amountMinor: 14900, enabled: true });
  await page.getByLabel('Slot category', { exact: true }).selectOption('TEMPLATE'); await expect(page.getByLabel('Price per template slot (INR)', { exact: true })).toHaveValue('200');
  await page.getByLabel('Price per template slot (INR)', { exact: true }).fill('249'); await page.getByRole('button', { name: 'Save template slot pricing', exact: true }).click();
  await expect(page.getByText('Template slot price saved. Existing orders keep their original category and price.', { exact: true })).toBeVisible();
  await page.getByLabel('Slot category', { exact: true }).selectOption('PROJECT'); await expect(page.getByLabel('Price per project slot (INR)', { exact: true })).toHaveValue('149');
  expect(f.data.prices.COMPONENT.amountMinor).toBe(30000); await noOverflow(page);
});
