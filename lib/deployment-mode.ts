export type DeploymentMode = { BACKEND_URL?: string; DEMO_MODE?: string };

/** A configured backend always selects the real application, never sample data. */
export function resolveBackendOrigin(config: DeploymentMode): string | undefined {
  const origin = config.BACKEND_URL?.trim();
  if (origin) {
    const url = new URL(origin);
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash || url.pathname !== '/') {
      throw Error('BACKEND_URL must be an HTTP(S) origin without credentials, a path or query.');
    }
    return url.origin;
  }
  if (config.DEMO_MODE === 'false') throw Error('BACKEND_URL is required when DEMO_MODE=false.');
  if (config.DEMO_MODE && config.DEMO_MODE !== 'true') throw Error('DEMO_MODE must be true or false.');
  return undefined;
}
