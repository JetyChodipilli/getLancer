'use client';
import {useEffect,useRef} from 'react';
import {usePathname} from 'next/navigation';
import Link from 'next/link';
import Brand from './brand';
import AuthOrbit from './auth-orbit';

export default function IcyAuthShell({children,preview=false}:{children:React.ReactNode;preview?:boolean}){
 const root=useRef<HTMLElement>(null),path=usePathname(),previousPath=useRef(path);
 useEffect(()=>{
  if(previousPath.current!==path)root.current?.querySelector<HTMLElement>('h1')?.focus({preventScroll:true});
  previousPath.current=path;
 },[path]);
 return <main id="main" ref={root} className="icy-auth paper-auth">
  <div className="icy-shell">
   <aside className="icy-story" aria-label="Discover the work. Meet its maker.">
    <Link href="/" className="icy-logo" aria-label="getLancer home"><Brand/></Link>
    <div className="icy-story-copy"><h2>The craft behind<br/>your next chapter.</h2><p>Explore working software. Share what you make. Find the people to build with.</p></div>
    <AuthOrbit preview={preview}/>
    <nav className="paper-auth-paths" aria-label="Explore getLancer"><Link href="/">Projects</Link><Link href="/components">Components</Link><Link href="/college-projects">College projects</Link><Link href="/labs">Learning</Link></nav>
   </aside>
   {children}
  </div>
 </main>;
}
