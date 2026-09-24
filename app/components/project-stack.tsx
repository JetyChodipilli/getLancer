'use client';
import {useState} from 'react';
import Link from 'next/link';
import {ArrowUpRight, Pause, Play} from 'lucide-react';
import Preview from './preview';
import type {Product} from '@/lib/catalog';

export default function ProjectStack({products=[]}:{products:Product[]}){
 const [paused,setPaused]=useState(false);
 return <div className="project-stack" data-paused={paused} aria-label="Featured project demos">{products.slice(0,3).map((product,index)=><article className={`stack-card stack-card-${index}`} key={product.id}>
  <div className="stack-card-label"><span>{product.illustrative?'Sample project':'Project showcase'} / {product.category}</span><span>0{index+1}</span></div>
  <Link className="stack-card-media" href={'/products/'+product.slug} aria-label={'Explore '+product.title}><Preview product={product}/></Link>
  <div className="stack-card-caption"><div><span>{product.technology} · Built by {product.builder}</span><h2>{product.title}</h2><p>{product.summary}</p></div><Link className="stack-card-open" href={'/products/'+product.slug} aria-label={'View '+product.title}><ArrowUpRight size={21}/></Link></div>
 </article>)}<button type="button" className="stack-motion" aria-pressed={paused} onClick={()=>setPaused(!paused)}>{paused?<Play size={14}/>:<Pause size={14}/>}<span>{paused?'Play animation':'Pause animation'}</span></button></div>;
}
