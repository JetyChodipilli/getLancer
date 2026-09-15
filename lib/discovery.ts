import { type Product } from './catalog';
export type Filters = { q: string; category: string; technology: string; projectType: string; availability: string; liveDemo: boolean; sort: string; page: number };
export const emptyFilters: Filters = { q: '', category: '', technology: '', projectType: '', availability: '', liveDemo: false, sort: 'relevance', page: 0 };
export function parseFilters(params: URLSearchParams): Filters {
  const page = Number(params.get('page') || 0);
  return { q: (params.get('q') || '').slice(0, 200), category: params.get('category') || '', technology: params.get('technology') || '', projectType: params.get('projectType') || '', availability: params.get('availability') || '', liveDemo: params.get('liveDemo') === 'true', sort: ['relevance', 'newest', 'updated'].includes(params.get('sort') || '') ? params.get('sort')! : 'relevance', page: Number.isInteger(page) && page >= 0 && page <= 10000 ? page : 0 };
}
export function filterParams(filters: Filters): URLSearchParams {
  const params = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => { if (value !== '' && value !== false && !(key === 'page' && value === 0)) params.set(key, String(value)); });
  return params;
}
export function filterExamples(items: Product[], f: Filters): Product[] {
  const words = f.q.toLowerCase().trim().split(/\s+/).filter(Boolean);
  return items.filter(p => (!f.category || p.category === f.category) && (!f.technology || p.technology.split(',').includes(f.technology)) && (!f.projectType || p.projectType === f.projectType) && (!f.availability || p.availability === f.availability) && (!f.liveDemo || !!p.liveUrl) && words.every(word => `${p.title} ${p.summary} ${p.description} ${p.category} ${p.technology} ${p.builder}`.toLowerCase().includes(word)))
    .sort((a, b) => f.sort === 'relevance' && f.q.trim() ? Number(b.title.toLowerCase().includes(f.q.toLowerCase())) - Number(a.title.toLowerCase().includes(f.q.toLowerCase())) || b.updatedAt.localeCompare(a.updatedAt) : b.updatedAt.localeCompare(a.updatedAt));
}
