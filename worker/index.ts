/** Cloudflare Worker entry point for the vinext-starter template. */
import { handleImageOptimization, DEFAULT_DEVICE_SIZES, DEFAULT_IMAGE_SIZES } from "vinext/server/image-optimization";
import handler from "vinext/server/app-router-entry";
import { createFrontendNonce, frontendCsp, publisherFrameSource, secureFrontendResponse } from '../lib/security-headers';
import { resolveBackendOrigin } from '../lib/deployment-mode';

interface Env {
  BACKEND_URL?: string;
  DEMO_MODE?: string;
  APP_ENV?: string;
  DEMO_PUBLIC_URL_TEMPLATE?: string;
  ASSETS: { fetch(request: Request): Promise<Response> };
  IMAGES: {
    input(stream: ReadableStream): {
      transform(options: Record<string, unknown>): {
        output(options: { format: string; quality: number }): Promise<{ response(): Response }>;
      };
    };
  };
}

interface ExecutionContext {
  waitUntil(promise: Promise<unknown>): void;
  passThroughOnException(): void;
}

// The Worker runtime supplies its native streaming parser. Never buffer SSR HTML
// or rewrite a download/API body, iframe srcdoc attribute, or uploaded source.
interface RewriterElement { setAttribute(name: string, value: string): void }
interface StreamingHtmlRewriter {
  on(selector: string, handler: { element(element: RewriterElement): void }): StreamingHtmlRewriter;
  transform(response: Response): Response;
}
declare const HTMLRewriter: { new(): StreamingHtmlRewriter };

// Image security config. SVG sources with .svg extension auto-skip the
// optimization endpoint on the client side (served directly, no proxy).
// To route SVGs through the optimizer (with security headers), set
// dangerouslyAllowSVG: true in next.config.js and uncomment below:
// const imageConfig: ImageConfig = { dangerouslyAllowSVG: true };

const worker = {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(request.url);
    const nonce = createFrontendNonce();
    let connected: boolean, publisher: string | undefined;
    try {
      connected = Boolean(resolveBackendOrigin(env));
      publisher = publisherFrameSource(env.DEMO_PUBLIC_URL_TEMPLATE, request.url);
    } catch {
      return secureFrontendResponse(Response.json({ error: { code: 'SERVICE_UNAVAILABLE', message: 'The marketplace is temporarily unavailable.' } }, { status: 503, headers: { 'Cache-Control': 'no-store' } }), request.url, false, nonce);
    }

    if (url.pathname === "/_vinext/image") {
      const allowedWidths = [...DEFAULT_DEVICE_SIZES, ...DEFAULT_IMAGE_SIZES];
      const imageResponse=await handleImageOptimization(request, {
        fetchAsset: (path) => env.ASSETS.fetch(new Request(new URL(path, request.url))),
        transformImage: async (body, { width, format, quality }) => {
          const result = await env.IMAGES.input(body).transform(width > 0 ? { width } : {}).output({ format, quality });
          return result.response();
        },
      }, allowedWidths);
      return secureFrontendResponse(imageResponse, request.url, connected, nonce, publisher);
    }

    // Supply vinext/React with this request's trusted nonce before rendering. It
    // also prevents replaying a cached prerender containing another nonce.
    const headers = new Headers(request.headers);
    headers.set('Content-Security-Policy', frontendCsp(connected, nonce, publisher));
    headers.delete('Content-Security-Policy-Report-Only');
    const response = await handler.fetch(new Request(request, { headers }), env, ctx);
    const secured = secureFrontendResponse(response, request.url, connected, nonce, publisher);
    let applicationHtml = false;
    try { applicationHtml = !decodeURIComponent(url.pathname).startsWith('/api/'); } catch { /* Malformed paths cannot acquire a render nonce. */ }
    if (applicationHtml && /^text\/html(?:\s*;|$)/i.test(secured.headers.get('Content-Type') || '') && secured.body) {
      secured.headers.set('Cache-Control', 'no-store');
      secured.headers.delete('Content-Length');
      secured.headers.delete('ETag');
      return new HTMLRewriter().on('script', { element(element) { element.setAttribute('nonce', nonce); } }).transform(secured);
    }
    return secured;
  },
};

export default worker;
