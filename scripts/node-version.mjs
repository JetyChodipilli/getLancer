import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';

// Security floors from the 2026-10-21 Node releases. Keep the supported LTS
// families bounded: a new major needs its own compatibility review.
export function supportedNodeVersion(version) {
  const parts = typeof version === 'string' && /^v?(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/.exec(version);
  if (!parts || parts[0] !== version) return false;
  const [major, minor, patch] = parts.slice(1).map(Number);
  if (![major, minor, patch].every(Number.isSafeInteger)) return false;
  return major === 22 && (minor > 23 || minor === 23 && patch >= 4)
    || major === 24 && (minor > 22 || minor === 22 && patch >= 1);
}

export function requireSupportedNodeVersion(version = process.version) {
  if (!supportedNodeVersion(version)) throw new Error('Use stable Node 22.23.4+ in Node 22, or 24.22.1+ in Node 24. CI and Docker use Node 22.23.4.');
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  try {requireSupportedNodeVersion();}
  catch (error) {console.error(error.message); process.exitCode = 1;}
}
