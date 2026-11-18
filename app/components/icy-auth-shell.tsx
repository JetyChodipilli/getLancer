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
   <aside className="icy-story" aria-label="Discover the work. Meet its maker.">
    <Link prefetch={false} href="/" className="icy-logo" aria-label="getLancer home"><Brand/></Link>
    <div className="icy-story-copy"><h2>The craft behind<br/>your next chapter.</h2><p>Explore working software. Share what you make. Find the people to build with.</p></div>
   </aside>
   {children}
   <nav className="paper-auth-resources" aria-label="Helpful resources">
    <Link prefetch={false} href="/how-it-works"><span><strong>How getLancer works</strong><small>Find a project, meet its builder and start a conversation.</small></span><ArrowUpRight size={17} aria-hidden="true"/></Link>
    <Link prefetch={false} href="/#project-collection"><span><strong>Browse projects</strong><small>Try working software and see who built it.</small></span><ArrowUpRight size={17} aria-hidden="true"/></Link>
    <Link prefetch={false} href="/labs"><span><strong>Explore practical labs</strong><small>Follow examples with source and setup notes.</small></span><ArrowUpRight size={17} aria-hidden="true"/></Link>
   </nav>
  </div>
 </main>;
}
