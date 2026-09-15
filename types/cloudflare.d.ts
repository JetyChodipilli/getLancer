/** Runtime bindings used by this site. Marketplace data is stored by the Spring API. */
declare module 'cloudflare:workers' {
  export const env: { BACKEND_URL?: string; APP_BASE_URL?: string; INDEX_PUBLIC_PAGES?: string };
}
