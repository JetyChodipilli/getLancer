'use client';

import { ChevronDown, ChevronUp } from 'lucide-react';
import { useEffect, useState, type RefObject } from 'react';

export default function FormScrollButtons({ fields }: { fields: RefObject<HTMLDivElement | null> }) {
  const [edges, setEdges] = useState({ up: false, down: false });

  useEffect(() => {
    const region = fields.current;
    if (!region) return;
    const measure = () => {
      const up = region.scrollTop > 1;
      const down = region.scrollHeight - region.clientHeight - region.scrollTop > 1;
      setEdges(previous => previous.up === up && previous.down === down ? previous : { up, down });
    };
    const resize = new ResizeObserver(measure);
    resize.observe(region);
    for (const child of region.children) resize.observe(child);
    const children = new MutationObserver(records => {
      for (const record of records) {
        for (const child of record.addedNodes) if (child instanceof Element) resize.observe(child);
        for (const child of record.removedNodes) if (child instanceof Element) resize.unobserve(child);
      }
      measure();
    });
    children.observe(region, { childList: true });
    region.addEventListener('scroll', measure, { passive: true });
    measure();
    return () => {
      children.disconnect();
      resize.disconnect();
      region.removeEventListener('scroll', measure);
    };
  }, [fields]);

  if (!edges.up && !edges.down) return null;

  function scroll(direction: number) {
    const region = fields.current;
    if (!region) return;
    region.scrollBy({
      top: direction * region.clientHeight * 0.8,
      behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth',
    });
  }

  return (
    <div className="form-scroll-buttons" role="group" aria-label="Scroll form fields">
      <button type="button" className="iconbutton" aria-label="Scroll form up" title="Scroll form up" aria-disabled={!edges.up} onClick={() => edges.up && scroll(-1)}>
        <ChevronUp size={18} aria-hidden="true" />
      </button>
      <button type="button" className="iconbutton" aria-label="Scroll form down" title="Scroll form down" aria-disabled={!edges.down} onClick={() => edges.down && scroll(1)}>
        <ChevronDown size={18} aria-hidden="true" />
      </button>
    </div>
  );
}
