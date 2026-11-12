import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';

// Node 24.22.2 LTS includes the 2026-10-21 security release fixes and the
// subsequent 2026-10-28 patch. A new major needs its own compatibility review.
export function supportedNodeVersion(version) {
  const parts = typeof version === 'string' && /^v?(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/.exec(version);
  if (!parts || parts[0] !== version) return false;
  const [major, minor, patch] = parts.slice(1).map(Number);
  if (![major, minor, patch].every(Number.isSafeInteger)) return false;
  return major === 24 && (minor > 22 || minor === 22 && patch >= 2);
}

export function requireSupportedNodeVersion(version = process.version) {
  if (!supportedNodeVersion(version)) throw new Error('Use stable Node 24.22.2+ in Node 24. CI and Docker use Node 24.22.2.');
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  try {requireSupportedNodeVersion();}
  catch (error) {console.error(error.message); process.exitCode = 1;}
}
