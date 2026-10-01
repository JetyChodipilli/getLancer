/** Public evidence links must never execute scripts or carry URL credentials. */
export function safeExternalUrl(value: unknown): string | undefined {
  if (typeof value !== 'string' || !value || value.length > 2048 || /[\u0000-\u0020\u007f]/.test(value)) return undefined;
  try {
    const url = new URL(value);
    if (url.protocol !== 'https:' || url.username || url.password) return undefined;
    return url.href;
  } catch { return undefined; }
}
