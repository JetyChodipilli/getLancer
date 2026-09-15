'use client';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import { ArrowUpRight, ChevronLeft, ChevronRight, Pause, Play } from 'lucide-react';
import Preview from './preview';
import type { Product } from '@/lib/catalog';

export default function ProductSpotlight({ products }: { products: Product[] }) {
 const [index, setIndex] = useState(0);
 const [paused, setPaused] = useState(false);
 const [hovered, setHovered] = useState(false);
 const [focused, setFocused] = useState(false);
 const held = hovered || focused;
 const [reduced, setReduced] = useState(true);
 useEffect(() => {
  const query = window.matchMedia('(prefers-reduced-motion: reduce)');
  const update = () => setReduced(query.matches);
  update(); query.addEventListener('change', update);
  return () => query.removeEventListener('change', update);
 }, []);
 useEffect(() => {
  if (paused || held || reduced || products.length < 2) return;
  const timer = window.setInterval(() => {
   if (!document.hidden) setIndex(i => (i + 1) % products.length);
  }, 6000);
  return () => window.clearInterval(timer);
 }, [paused, held, reduced, products.length]);
 if (!products.length) return null;
 const current = index % products.length;
 const p = products[current];
 function step(delta: number) { setPaused(true); setIndex((current + delta + products.length) % products.length); }
 return <section className="spotlight" aria-label="Project spotlight" aria-roledescription="carousel" onMouseEnter={() => setHovered(true)} onMouseLeave={() => setHovered(false)} onFocusCapture={() => setFocused(true)} onBlurCapture={e => { if (!e.currentTarget.contains(e.relatedTarget as Node)) setFocused(false); }}>
  <div className="spotlight-label"><span>{p.illustrative ? 'Inside a sample build' : 'Inside the build'}</span><span>{String(current + 1).padStart(2, '0')} / {String(products.length).padStart(2, '0')}</span></div>
  <div className="spotlight-stage" key={p.id}><Link href={'/products/' + p.slug} className={'spotlight-proof tone-' + current % 6} aria-label={'Explore ' + p.title}><Preview product={p}/></Link>
   <div className="spotlight-caption"><div><span>{p.category} · {p.technology}</span><h2>{p.title}</h2></div><Link className="spotlight-open" href={'/products/' + p.slug} aria-label={'View ' + p.title}><ArrowUpRight size={22}/></Link></div>
  </div>
  <div className="spotlight-controls"><span>Built by {p.builder}</span><div><button className="iconbutton" onClick={() => step(-1)} aria-label="Previous project"><ChevronLeft size={18}/></button><button className="iconbutton" disabled={reduced || products.length < 2} aria-label={paused ? 'Play product animation' : 'Pause product animation'} aria-pressed={paused} onClick={() => setPaused(!paused)}>{paused || reduced ? <Play size={16}/> : <Pause size={16}/>}</button><button className="iconbutton" onClick={() => step(1)} aria-label="Next project"><ChevronRight size={18}/></button></div></div>
 </section>;
}
