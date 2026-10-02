'use client';
import {useEffect,useRef} from 'react';
import {usePathname} from 'next/navigation';
import Link from 'next/link';
import Brand from './brand';
import AuthOrbit from './auth-orbit';
export default function IcyAuthShell({children}:{children:React.ReactNode}){
 const root=useRef<HTMLElement>(null),path=usePathname();
 useEffect(()=>{root.current?.querySelector<HTMLElement>('h1')?.focus({preventScroll:true});},[path]);
 return <main id="main" ref={root} className="icy-auth">
  <div className="icy-shell">
   <aside className="icy-story" aria-label="Real projects. Real talent. Real opportunities.">
    <Link href="/" className="icy-logo" aria-label="getLancer home"><Brand/></Link>
    <AuthOrbit/>
    <div className="icy-story-copy"><h2>Real projects.<br/>Real talent.<br/><span>Real opportunities.</span></h2><p>Connect. Collaborate. Create the next big thing.</p><div className="icy-signature">Code builds opportunity</div></div>
   </aside>
   {children}
  </div>
 </main>;
}
