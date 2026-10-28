'use client';
import {useEffect,useRef} from 'react';
import {usePathname} from 'next/navigation';
import Link from 'next/link';
import {ArrowUpRight} from 'lucide-react';
import Brand from './brand';
import Preview from './preview';
import {examples} from '@/lib/catalog';
export default function IcyAuthShell({children,preview=false}:{children:React.ReactNode;preview?:boolean}){
 const root=useRef<HTMLElement>(null),path=usePathname();
 useEffect(()=>{root.current?.querySelector<HTMLElement>('h1')?.focus({preventScroll:true});},[path]);
 const project=examples[0];
 return <main id="main" ref={root} className="icy-auth">
  <div className="icy-shell">
   <aside className="icy-story" aria-label="Real projects. Real talent. Real opportunities.">
    <Link href="/" className="icy-logo" aria-label="getLancer home"><Brand/></Link>
    <div className="icy-story-copy"><h2>Real work.<br/>New possibilities.</h2><p>Find a project that speaks for itself. Meet the builder behind it.</p></div>
    {preview&&project&&<Link className="auth-work-preview" href={'/products/'+project.slug} aria-label={'Explore '+project.title}><div data-ui="true"><Preview product={project}/></div><span><strong>{project.title}</strong><span>Illustrative project · explore the demo</span><ArrowUpRight size={20} aria-hidden="true"/></span></Link>}
    <p className="auth-story-note">Real projects. Real talent. Real opportunities.</p>
   </aside>
   {children}
  </div>
 </main>;
}
