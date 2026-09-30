import { examples, type Product } from './catalog';
import { emptyFilters, filterExamples, filterParams, type Filters } from './discovery';
import { resolveBackendOrigin, type DeploymentMode } from './deployment-mode';
export async function backendOrigin() {
  const config: DeploymentMode = { BACKEND_URL: process.env.BACKEND_URL, DEMO_MODE: process.env.DEMO_MODE };
  try {
    const { env } = await import('cloudflare:workers');
    const runtime = env as DeploymentMode;
    config.BACKEND_URL = runtime.BACKEND_URL || config.BACKEND_URL;
    config.DEMO_MODE = runtime.DEMO_MODE ?? config.DEMO_MODE;
  } catch {}
  return resolveBackendOrigin(config);
}
export type Catalog = { items: Product[]; preview: boolean; totalItems: number; totalPages: number; filters: Filters };
export async function getCatalog(filters: Filters = emptyFilters, builder = ''): Promise<Catalog> {
  const origin = await backendOrigin();
  if (!origin) {
    const items = filterExamples(builder ? examples.filter(p => p.builderSlug === builder) : examples, filters);
    return { items, preview: true, totalItems: items.length, totalPages: 1, filters };
  }
  const params = filterParams(filters); params.set('size', '12');
  if (builder) params.set('builder', builder);
  const res = await fetch(origin + '/api/v1/products?' + params, { cache: 'no-store', signal: AbortSignal.timeout(15000) });
  if (!res.ok) throw Error('Marketplace is temporarily unavailable');
  return { ...(await res.json()), preview: false, filters };
}
export async function getProduct(slug: string): Promise<Product | undefined> {
  const origin = await backendOrigin();
  if (!origin) return examples.find(p => p.slug === slug);
  const r = await fetch(origin + '/api/v1/products/' + encodeURIComponent(slug), { cache: 'no-store', signal: AbortSignal.timeout(15000) });
  if (r.status === 404) return undefined;
  if (!r.ok) throw Error('Project unavailable');
  return r.json();
}
