export type DeploymentMode = { BACKEND_URL?: string; DEMO_MODE?: string };

/** Demo deployments never connect to account or database services. */
export function resolveBackendOrigin(config: DeploymentMode): string | undefined {
  if (config.DEMO_MODE === 'true') return undefined;
  return config.BACKEND_URL?.replace(/\/$/, '') || undefined;
}
