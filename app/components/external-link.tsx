import type { AnchorHTMLAttributes } from 'react';
import { safeExternalUrl } from '@/lib/safe-url';

export default function ExternalLink({ href, children, ...props }: Omit<AnchorHTMLAttributes<HTMLAnchorElement>, 'href'> & { href?: unknown }) {
  const safe = safeExternalUrl(href);
  return safe ? <a {...props} href={safe} target="_blank" rel="noopener noreferrer" referrerPolicy="no-referrer">{children}</a> : null;
}
