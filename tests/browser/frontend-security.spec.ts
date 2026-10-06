import { test, expect } from '@playwright/test';
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';

const recipes = JSON.parse(readFileSync('backend/src/main/resources/catalog/components.json', 'utf8')) as { category: string; files: Record<string, string> }[];
function responseNonce(policy: string): string {
  const nonce = policy.match(/(?:^|;)\s*script-src\s[^;]*'nonce-([^']+)'/)?.[1];
  expect(nonce).toMatch(/^[A-Za-z0-9+/]{43}=$/);
  return nonce!;
}

test('the native Worker supplies unique matching script nonces and the streamed page hydrates', async ({ page, request }) => {
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  const first = (await page.goto('/components/quiet-sign-in'))!;
  expect(first.status()).toBe(200);
  const policy = first.headers()['content-security-policy'];
  const nonce = responseNonce(policy);
  expect(policy).not.toMatch(/script-src[^;]*'unsafe-inline'|script-src[^;]*'unsafe-eval'/);
  expect(policy).toContain("script-src-attr 'none'");
  expect(first.headers()['cache-control']).toBe('no-store');
  const html = await first.text();
  const scripts = [...html.matchAll(/<script\b([^>]*)>/gi)];
  expect(scripts.length).toBeGreaterThan(2);
  for (const [, attributes] of scripts) expect(attributes.match(/\bnonce="([^"]+)"/)?.[1]).toBe(nonce);
  // These controls need React hydration, and the iframe controls need the hash allowlist.
  await page.getByRole('button', { name: 'Phone', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Phone', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await page.getByRole('tab', { name: 'Source', exact: true }).click();
  await expect(page.locator('pre[aria-label="index.html source"]')).toBeVisible();
  await page.getByRole('tab', { name: 'Preview', exact: true }).click();
  const frame = page.frameLocator('.kit-preview-stage iframe');
  await frame.getByRole('button', { name: 'Show password', exact: true }).click();
  await expect(frame.getByLabel('Demo password', { exact: true })).toHaveAttribute('type', 'text');
  // A caller-supplied CSP header cannot choose the render nonce.
  const next = await request.get('/components/quiet-sign-in', { headers: { 'Content-Security-Policy': "script-src 'nonce-attacker'" } });
  expect(next.status()).toBe(200);
  const nextNonce = responseNonce(next.headers()['content-security-policy']);
  expect(nextNonce).not.toBe(nonce);
  expect(nextNonce).not.toBe('attacker');
  expect(errors).toEqual([]);
});

test('a matching nonce is a positive control while an arbitrary inline script and handler are blocked', async ({ page }) => {
  await page.goto('/components/quiet-sign-in');
  const result = await page.evaluate(async () => {
    const observed: string[] = [];
    document.addEventListener('securitypolicyviolation', event => observed.push(event.effectiveDirective));
    const state = window as typeof window & { nonceControl?: number; inlineProbe?: number; handlerProbe?: number };
    const control = document.createElement('script');
    control.nonce = document.querySelector<HTMLScriptElement>('script[nonce]')!.nonce;
    control.textContent = 'window.nonceControl=1;';
    document.body.append(control);
    const inline = document.createElement('script');
    inline.textContent = 'window.inlineProbe=1;';
    document.body.append(inline);
    const button = document.createElement('button');
    button.setAttribute('onclick', 'window.handlerProbe=1;');
    document.body.append(button);
    button.click();
    await new Promise(resolve => setTimeout(resolve, 100));
    return { control: state.nonceControl, inline: state.inlineProbe ?? null, handler: state.handlerProbe ?? null, observed };
  });
  expect(result.control).toBe(1);
  expect(result.inline).toBeNull();
  expect(result.handler).toBeNull();
  expect(result.observed).toContain('script-src-elem');
  expect(result.observed).toContain('script-src-attr');
});

test('opaque srcdoc admits the trusted recipe hash and blocks a modified uploaded script', async ({ page }) => {
  const response = (await page.goto('/components/quiet-sign-in'))!;
  const policy = response.headers()['content-security-policy'];
  const card = recipes.find(recipe => recipe.category === 'CARD')!.files['index.html'];
  const trustedBody = card.match(/<script>([\s\S]*?)<\/script>/)![1];
  const hash = (body: string) => `'sha256-${createHash('sha256').update(body).digest('base64')}'`;
  expect(policy).toContain(hash(trustedBody));
  const modifiedBody = trustedBody + ';document.body.dataset.uploadedExecuted="yes";';
  expect(policy).not.toContain(hash(modifiedBody));
  await page.evaluate(({ trusted, modified }) => {
    for (const [id, source] of [['trusted-recipe-control', trusted], ['uploaded-recipe-negative', modified]]) {
      const iframe = document.createElement('iframe');
      iframe.id = id;
      iframe.title = id;
      iframe.setAttribute('sandbox', 'allow-scripts allow-forms');
      iframe.srcdoc = source;
      document.body.append(iframe);
    }
  }, { trusted: card, modified: card.replace(trustedBody, modifiedBody) });
  const trusted = page.frameLocator('#trusted-recipe-control');
  await trusted.getByRole('button', { name: 'Save project', exact: true }).click();
  await expect(trusted.getByRole('button', { name: 'Saved ✓', exact: true })).toHaveAttribute('aria-pressed', 'true');
  const negative = page.frameLocator('#uploaded-recipe-negative');
  await negative.getByRole('button', { name: 'Save project', exact: true }).click();
  await expect(negative.getByRole('button', { name: 'Save project', exact: true })).toHaveAttribute('aria-pressed', 'false');
  await expect(negative.locator('body')).not.toHaveAttribute('data-uploaded-executed', 'yes');
  for (const id of ['trusted-recipe-control', 'uploaded-recipe-negative']) {
    const isolated = await page.locator('#' + id).evaluate((element: HTMLIFrameElement) => {
      try { return element.contentWindow!.document.body.textContent; } catch { return 'opaque'; }
    });
    expect(isolated).toBe('opaque');
  }
});
