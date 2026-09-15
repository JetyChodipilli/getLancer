export async function seoSettings() {
  let base = process.env.APP_BASE_URL;
  let enabled = process.env.INDEX_PUBLIC_PAGES;
  try { const {env}=await import('cloudflare:workers');base=env.APP_BASE_URL||base;enabled=env.INDEX_PUBLIC_PAGES||enabled; } catch {}
  return { base: base?.replace(/\/$/,''), index: enabled==='true' && !!base };
}
