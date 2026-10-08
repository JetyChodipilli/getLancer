/** Sample saves are actor-scoped browser data, never backend account state. */
export const componentSaveKey = (actor: string) => 'getlancer.component-saves.v1.' + actor;
export function parseComponentSaves(raw: string | null): string[] {
  if (!raw) return [];
  const parsed: unknown = JSON.parse(raw);
  if (!Array.isArray(parsed) || parsed.length > 200 || parsed.some(value => typeof value !== 'string' || !/^[a-z0-9-]{1,140}$/.test(value))) throw Error('Stored component saves are invalid.');
  return [...new Set(parsed)];
}
export function setComponentSave(items: string[], slug: string, saved: boolean): string[] {
  if (!/^[a-z0-9-]{1,140}$/.test(slug)) throw Error('Invalid component reference.');
  const result = saved ? [...new Set([...items, slug])] : items.filter(value => value !== slug);
  if (result.length > 200) throw Error('Remove a saved component before adding more than 200.');
  return result;
}
