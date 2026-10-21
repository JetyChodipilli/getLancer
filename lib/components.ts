import seeds from '../backend/src/main/resources/catalog/components.metadata.json' with {type: 'json'};
export type ComponentEntry = { revision?: number; uploaded?: boolean; previewUrl?: string; previewExpiresAt?: string; archiveSha256?: string; manifestSha256?: string; sourceHash?: string; withdrawn?: boolean; published?: boolean; id?: string; slug: string; recipeSlug?: string; title: string; summary: string; category: string; kind: string; language?: string; framework: string; executionMode: string; creator: string; builderSlug?: string; contribution?: string; license: string; version: string; sha256: string; scenario: string; status?: string; reviewReason?: string; files?: Record<string, string> };
export type ComponentPage = { items: ComponentEntry[]; totalItems: number; page: number; size: number; totalPages: number; hasMore: boolean };
export type ComponentFilters = { q: string; category: string; kind: string; builder: string; language: string; framework: string; license: string; mode: string; page: number };
export type Capacity = { free: number; purchased: number; used: number; limit: number };
export type SlotPricing = { amountMinor: number | null; enabled: boolean; salesEnabled: boolean; configured: boolean; currency: string; mode: string; reason: string };
export type SlotPurchase = { id: string; amountMinor: number; mode: string; status: string; orderId?: string; keyId?: string; refundedMinor: number; grantsSlot: boolean };
export const componentSeeds: ComponentEntry[] = seeds;
export const componentCategories = [['NAVBAR', 'Navigation bars'], ['SIDEBAR', 'Sidebars'], ['FORM', 'Forms'], ['CARD', 'Cards'], ['AUTH', 'Login & auth'], ['DASHBOARD', 'Dashboards']] as const;
export const componentLanguages = [['HTML', 'HTML'], ['CSS', 'CSS'], ['JavaScript', 'JavaScript']] as const;
export const componentFrameworks = [['VANILLA', 'Plain browser (no framework)']] as const;
export const componentLicenses = [['MIT', 'MIT']] as const;
export const componentModes = [['Browser local', 'Browser local'], ['Source only', 'Source only']] as const;
export const emptyComponentFilters: ComponentFilters = { q: '', category: '', kind: 'FRONTEND', builder: '', language: '', framework: '', license: '', mode: '', page: 0 };
const languageKey = (value: string) => value.trim().toLowerCase().replace(/^javascript$/, 'js');
const frameworkKey = (value: string) => /^(html\/css\/(javascript|js)|vanilla)$/.test(value.toLowerCase().replaceAll(/\s/g, '')) ? 'vanilla' : value.trim().toLowerCase();
export function parseComponentFilters(params: URLSearchParams): ComponentFilters {
  const filters = { ...emptyComponentFilters };
  for (const key of ['q', 'category', 'kind', 'builder', 'language', 'framework', 'license', 'mode'] as const) {
    const value = params.get(key) ?? filters[key];
    if (value.length > (key === 'q' ? 200 : 100) || /[\u0000-\u001f\u007f]/.test(value)) throw Error('Choose valid component filters.');
    filters[key] = value.trim();
  }
  const page = params.get('page') || '0';
  if (!/^\d+$/.test(page) || Number(page) > 10000 || !['FRONTEND', 'BACKEND'].includes(filters.kind) || (filters.category && !componentCategories.some(([key]) => key === filters.category))) throw Error('Choose valid component filters.');
  filters.page = Number(page);
  filters.language = componentLanguages.find(([key]) => languageKey(key) === languageKey(filters.language))?.[0] ?? filters.language;
  if (frameworkKey(filters.framework) === 'vanilla') filters.framework = 'VANILLA';
  filters.license = componentLicenses.find(([key]) => key.toLowerCase() === filters.license.toLowerCase())?.[0] ?? filters.license;
  filters.mode = componentModes.find(([key]) => key.toLowerCase() === filters.mode.toLowerCase())?.[0] ?? filters.mode;
  return filters;
}
export function componentFilterParams(filters: ComponentFilters, changes: Partial<ComponentFilters> = {}): URLSearchParams {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries({ ...filters, ...changes })) if (value !== '' && !(key === 'page' && value === 0) && !(key === 'kind' && value === 'FRONTEND')) params.set(key, String(value));
  return params;
}
export function filterComponents(items: ComponentEntry[], filters: ComponentFilters): ComponentEntry[] {
  return items.filter(c => c.kind === filters.kind && (!filters.builder || c.builderSlug === filters.builder)
    && (!filters.category || c.category === filters.category)
    && (!filters.q || (c.title + ' ' + c.summary).toLowerCase().includes(filters.q.toLowerCase()))
    && (!filters.language || (c.language ?? c.framework).split(/[\s/,]+/).some(token => languageKey(token) === languageKey(filters.language)))
    && (!filters.framework || frameworkKey(c.framework) === frameworkKey(filters.framework))
    && (!filters.license || c.license.toLowerCase() === filters.license.toLowerCase())
    && (!filters.mode || c.executionMode.toLowerCase() === filters.mode.toLowerCase()));
}
export const collegeCategories = [['FULL_STACK', 'Full stack'], ['DATA_ANALYTICS', 'Data analytics'], ['AI_ML', 'AI & machine learning'], ['IOT', 'IoT']] as const;
export const componentLabel = (value: string) => componentCategories.find(([key]) => key === value)?.[1] || collegeCategories.find(([key]) => key === value)?.[1] || value.toLowerCase().replaceAll('_', ' ');
export function seedCatalog(q = '', category = '', kind = 'FRONTEND', page = 0, additional: Partial<Pick<ComponentFilters, 'language' | 'framework' | 'license' | 'mode' | 'builder'>> = {}): ComponentPage {
  const filters = parseComponentFilters(new URLSearchParams({ ...additional, q, category, kind, page: String(page) }));
  const all = filterComponents(componentSeeds, filters);
  return { items: all.slice(filters.page * 12, (filters.page + 1) * 12).map(item => { const card = { ...item }; delete card.files; return card; }), totalItems: all.length, page: filters.page, size: 12, totalPages: Math.ceil(all.length / 12), hasMore: (filters.page + 1) * 12 < all.length };
}

/** Uncompressed ZIP, bounded to curated text files; browsers need no package/build dependency. */
export function componentZip(files: Record<string, string>): Uint8Array {
  const encoder = new TextEncoder(), chunks: Uint8Array[] = [], central: Uint8Array[] = []; let offset = 0;
  const u16 = (view: DataView, at: number, n: number) => view.setUint16(at, n, true), u32 = (view: DataView, at: number, n: number) => view.setUint32(at, n, true);
  const crc = (bytes: Uint8Array) => { let value = 0xffffffff; for (const b of bytes) { value ^= b; for (let i = 0; i < 8; i++)value = (value >>> 1) ^ ((value & 1) ? 0xedb88320 : 0); } return (value ^ 0xffffffff) >>> 0; };
  const entries = Object.entries(files); if (entries.length !== 3 || !['index.html', 'README.md', 'LICENSE'].every(k => k in files)) throw Error('The curated source manifest is incomplete.');
  for (const [name, content] of entries) {
const path = encoder.encode(name), data = encoder.encode(content); if (data.length > 100000) throw Error('Source file exceeds its reviewed limit.'); const checksum = crc(data), header = new Uint8Array(30 + path.length), h = new DataView(header.buffer); u32(h, 0, 0x04034b50); u16(h, 4, 20); u16(h, 6, 0x800); u16(h, 12, 0x21); u32(h, 14, checksum); u32(h, 18, data.length); u32(h, 22, data.length); u16(h, 26, path.length); header.set(path, 30); chunks.push(header, data);
    const directory = new Uint8Array(46 + path.length), d = new DataView(directory.buffer); u32(d, 0, 0x02014b50); u16(d, 4, 20); u16(d, 6, 20); u16(d, 8, 0x800); u16(d, 14, 0x21); u32(d, 16, checksum); u32(d, 20, data.length); u32(d, 24, data.length); u16(d, 28, path.length); u32(d, 42, offset); directory.set(path, 46); central.push(directory); offset += header.length + data.length;
  }
  const end = new Uint8Array(22), e = new DataView(end.buffer), directorySize = central.reduce((n, c) => n + c.length, 0); u32(e, 0, 0x06054b50); u16(e, 8, entries.length); u16(e, 10, entries.length); u32(e, 12, directorySize); u32(e, 16, offset); const zip = new Uint8Array(offset + directorySize + 22); let at = 0; for (const part of [...chunks, ...central, end]) { zip.set(part, at); at += part.length; } return zip;
}
