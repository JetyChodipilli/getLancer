'use client';
import {useEffect,useRef,useState} from 'react';
import {ArrowRight,ArrowUpRight,Check,Code2} from 'lucide-react';
import Link from 'next/link';
import {examples} from '@/lib/catalog';
import Preview from './preview';

// Render the miniature at its own usable width and scale it as one picture.
// This keeps the sidebar and labels intact instead of compressing live UI columns.
export default function AuthOrbit({preview=false}:{preview?:boolean}){
 const frame=useRef<HTMLDivElement>(null),[scale,setScale]=useState(1);
 useEffect(()=>{
  const node=frame.current;if(!node||!preview)return;
  const resize=()=>setScale(node.clientWidth/720);
  const observer=new ResizeObserver(resize);observer.observe(node);resize();
  return()=>observer.disconnect();
 },[preview]);
 if(!preview)return <figure className="paper-auth-exhibit">
  <div className="paper-auth-interface-study" role="img" aria-label="getLancer interface study: considered components with clear labels and useful feedback">
   <div aria-hidden="true">
    <div className="paper-auth-study-top"><span><Code2 size={18}/> getLancer</span><span>Interface study</span></div>
    <div className="paper-auth-study-body"><div className="paper-auth-study-copy"><span>Small details.</span><strong>Better<br/>beginnings.</strong><p>Every useful thing starts with a thoughtful first step.</p></div>
     <div className="paper-auth-study-fields"><span className="paper-auth-study-label">Email address</span><div className="paper-auth-study-input">you@yourdomain.com</div><span className="paper-auth-study-hint">A clear label. A little guidance.</span><div className="paper-auth-study-action">Continue <ArrowRight size={16}/></div><div className="paper-auth-study-feedback"><Check size={16}/> Ready when you are.</div></div>
    </div>
    <div className="paper-auth-study-code"><span>label</span><span>input</span><span>feedback</span><span>your next idea</span></div>
   </div>
  </div>
  <figcaption><div><strong>Built around the work.</strong><span>A considered space to discover, share and build.</span></div></figcaption>
  <p className="paper-auth-sample">getLancer interface study</p>
 </figure>;
 const project=examples[1];
 return <figure className="paper-auth-exhibit">
  <div className="paper-auth-preview" data-ui ref={frame}><div className="paper-auth-miniature" style={{transform:`scale(${scale})`}}><Preview product={project}/></div></div>
  <figcaption><div><strong>{project.title}</strong><span>{project.summary}</span></div><Link href={'/products/'+project.slug} aria-label={'Explore '+project.title+' illustrative project'}><ArrowUpRight size={20} aria-hidden="true"/></Link></figcaption>
  <p className="paper-auth-sample">Illustrative project · Explore what you can build.</p>
 </figure>;
}
