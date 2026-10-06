export type DeploymentMode = { BACKEND_URL?: string; DEMO_MODE?: string; APP_ENV?: string };

const localBackendHosts = new Set(['localhost', '127.0.0.1', '[::1]', 'api']);

/** A configured backend always selects the real application, never sample data. */
export function resolveBackendOrigin(config: DeploymentMode): string | undefined {
  const environment = config.APP_ENV?.trim();
  if (environment && !['local', 'test', 'staging', 'production'].includes(environment)) {
    throw Error('APP_ENV must be local, test, staging or production.');
  }
  if (config.DEMO_MODE && !['true', 'false'].includes(config.DEMO_MODE)) throw Error('DEMO_MODE must be true or false.');
  const origin = config.BACKEND_URL?.trim();
  if (origin) {
    const url = new URL(origin);
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash || url.pathname !== '/') {
      throw Error('BACKEND_URL must be an HTTP(S) origin without credentials, a path or query.');
    }
    if (url.protocol === 'http:' && (!['local', 'test'].includes(environment || '') || !localBackendHosts.has(url.hostname))) {
      throw Error('BACKEND_URL must use HTTPS; HTTP is restricted to explicit local/test backend hosts.');
    }
    return url.origin;
  }
  if (config.DEMO_MODE === 'false' || ['staging', 'production'].includes(environment || '')) throw Error('BACKEND_URL is required for a hosted deployment or when DEMO_MODE=false.');
  return undefined;
}
