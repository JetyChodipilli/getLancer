import { createHash } from 'node:crypto';
import trustedCatalogue from '../backend/src/main/resources/catalog/components.json' with { type: 'json' };

/** Extract the reviewed catalogue's plain script format; this is not an HTML sanitizer. */
export function catalogueScriptSource(html: string): string {
  // Recognize every browser script-tag delimiter before enforcing the much
  // narrower reviewed format. An attributed end tag must not be skipped while
  // searching for a later plain end tag and accidentally included in the hash.
  const tags = [...html.matchAll(/<\/?script(?=[\t\n\f\r />])[^>]*>/gi)];
  const htmlWhitespace = new Set([' ', '\t', '\n', '\f', '\r']);
  const plainTag = (tag: string, prefix: string) => tag.toLowerCase().startsWith(prefix)
    && [...tag.slice(prefix.length, -1)].every(character => htmlWhitespace.has(character));
  if (tags.length !== 2 || !plainTag(tags[0][0], '<script') || !plainTag(tags[1][0], '</script')) {
    throw Error('Each trusted recipe must have one reviewed plain inline script.');
  }
  return html.slice(tags[0].index + tags[0][0].length, tags[1].index);
}

/** Only version-controlled recipes enter this allowlist, never API/uploaded HTML. */
export const trustedRecipeScriptSources: readonly string[] = Object.freeze([...new Set(trustedCatalogue.map(recipe =>
  `'sha256-${createHash('sha256').update(catalogueScriptSource(recipe.files['index.html']), 'utf8').digest('base64')}'`
))]);

export function createFrontendNonce(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(32));
  return btoa(String.fromCharCode(...bytes));
}

const localAppHosts = new Set(['localhost', '127.0.0.1', '[::1]']);

/** A publisher is fixed operator configuration, never a listing's URL. */
export function publisherFrameSource(template: string | undefined, requestUrl: string): string | undefined {
  if (!template?.trim()) return undefined;
  const value = template.trim();
  if (!/^https?:\/\/\{id\}\.[a-z0-9.-]+(?::\d+)?\/?$/.test(value)) throw Error('DEMO_PUBLIC_URL_TEMPLATE must be an isolated origin with one leading {id} label.');
  const publisher = new URL(value.replace('{id}', 'preview'));
  const suffix = publisher.hostname.slice('preview.'.length);
  if (!suffix.includes('.') || !suffix.split('.').every(label => /^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$/i.test(label))) throw Error('DEMO_PUBLIC_URL_TEMPLATE must use a fixed DNS suffix.');
  const app = new URL(requestUrl);
  if (suffix === app.hostname || app.hostname.endsWith('.' + suffix)) throw Error('Demo previews require an origin isolated from the application.');
  if (!localAppHosts.has(app.hostname) && suffix.split('.').slice(-2).join('.') === app.hostname.split('.').slice(-2).join('.')) {
    throw Error('Demo previews must not share the application cookie domain.');
  }
  if (publisher.protocol === 'http:') {
    if (suffix !== 'demo.localhost' || publisher.port !== '8090' || !localAppHosts.has(app.hostname) || app.protocol !== 'http:') {
      throw Error('HTTP demo previews are restricted to http://{id}.demo.localhost:8090 on a local application.');
    }
  } else if (suffix === 'localhost' || suffix.endsWith('.localhost') || /^\d+(?:\.\d+){3}$/.test(suffix)) {
    throw Error('Hosted demo previews must use a fixed isolated DNS origin.');
  }
  return `${publisher.protocol}//*.${suffix}${publisher.port ? ':' + publisher.port : ''}`;
}

// Provider hosts are enabled only for connected pages. Checkout loads on user intent.
export function frontendCsp(connected: boolean, nonce = createFrontendNonce(), publisher?: string): string {
  if (!/^[A-Za-z0-9+/]{43}=$/.test(nonce)) throw Error('A frontend nonce must contain 32 random bytes.');
  // publisherFrameSource produces the sole allowed narrow DNS wildcard.
  if (publisher && !/^https?:\/\/\*\.[a-z0-9.-]+(?::\d+)?$/.test(publisher)) throw Error('Invalid publisher CSP source.');
  const payment = connected ? ' https://checkout.razorpay.com https://api.razorpay.com' : '';
  return `default-src 'self'; script-src 'self' 'nonce-${nonce}' ${trustedRecipeScriptSources.join(' ')}${connected ? ' https://checkout.razorpay.com' : ''}; script-src-attr 'none'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'${payment}; frame-src 'self' https://www.youtube-nocookie.com https://player.vimeo.com https://www.loom.com${payment}${publisher ? ' ' + publisher : ''}; object-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'`;
}

/** Apply the same protections to HTML, API and image-optimizer responses. */
export function secureFrontendResponse(response: Response, requestUrl: string, connected: boolean, nonce = createFrontendNonce(), publisher?: string): Response {
  const secured = new Response(response.body, response);
  secured.headers.set('X-Content-Type-Options', 'nosniff');
  secured.headers.set('Referrer-Policy', 'no-referrer');
  secured.headers.set('Permissions-Policy', 'camera=(), microphone=(), geolocation=()');
  secured.headers.set('Content-Security-Policy', frontendCsp(connected, nonce, publisher));
  if (new URL(requestUrl).protocol === 'https:') secured.headers.set('Strict-Transport-Security', 'max-age=31536000');
  return secured;
}
