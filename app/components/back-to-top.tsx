'use client';

import { ArrowUp } from 'lucide-react';
import { usePathname } from 'next/navigation';
import { useEffect, useState } from 'react';

export default function BackToTop() {
  const pathname = usePathname();
  const [visiblePath, setVisiblePath] = useState<string | null>(null);

  useEffect(() => {
    if (pathname === '/login' || pathname === '/signup') return;
    const measure = () => setVisiblePath(window.scrollY >= 360 ? pathname : null);
    measure();
    window.addEventListener('scroll', measure, { passive: true });
    return () => window.removeEventListener('scroll', measure);
  }, [pathname]);

  if (!visiblePath || visiblePath !== pathname) return null;

  function returnToStart() {
    const target = document.getElementById('paper-project-search') ??
      document.querySelector<HTMLElement>('main h1') ?? document.querySelector('main');
    if (target) {
      if (target.tabIndex < 0 && !target.hasAttribute('tabindex')) {
        target.tabIndex = -1;
        target.addEventListener('blur', () => target.removeAttribute('tabindex'), { once: true });
      }
      target.focus({ preventScroll: true });
    }
    window.scrollTo({
      top: 0,
      behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth',
    });
  }

  return (
    <button type="button" className="iconbutton paper-back-top" aria-label="Back to top" onClick={returnToStart}>
      <ArrowUp size={20} aria-hidden="true" />
    </button>
  );
}
