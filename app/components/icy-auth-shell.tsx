'use client';
import {useEffect,useRef} from 'react';
import {usePathname} from 'next/navigation';
import Link from 'next/link';
import {ArrowUpRight} from 'lucide-react';
import Brand from './brand';

export default function IcyAuthShell({children}:{children:React.ReactNode}){
 const root=useRef<HTMLElement>(null),path=usePathname(),previousPath=useRef(path);
 useEffect(()=>{
  if(previousPath.current!==path)root.current?.querySelector<HTMLElement>('h1')?.focus({preventScroll:true});
  previousPath.current=path;
 },[path]);
 return <main id="main" ref={root} className="icy-auth paper-auth">
  <div className="icy-shell">
   <aside className="icy-story" aria-labelledby="auth-story-title">
    <Link prefetch={false} href="/" className="icy-logo" aria-label="getLancer home"><Brand onDark/></Link>
    <div className="icy-story-copy"><h2 id="auth-story-title">Find your next idea.</h2><p>A workspace for the projects you save, share, and build.</p></div>
   </aside>
   {children}
   <nav className="paper-auth-resources" aria-label="Helpful resources">
    <Link prefetch={false} href="/how-it-works">How getLancer works <ArrowUpRight size={15} aria-hidden="true"/></Link>
    <Link prefetch={false} href="/#project-collection">Browse projects <ArrowUpRight size={15} aria-hidden="true"/></Link>
    <Link prefetch={false} href="/labs">Explore practical labs <ArrowUpRight size={15} aria-hidden="true"/></Link>
   </nav>
  </div>
 </main>;
}
