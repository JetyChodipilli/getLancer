'use client';
import {useEffect,useRef,useState} from 'react';
import {usePathname} from 'next/navigation';
import Link from 'next/link';
import Brand from './brand';
export default function IcyAuthShell({children}:{children:React.ReactNode}){
 const root=useRef<HTMLElement>(null),art=useRef<HTMLDivElement>(null);const path=usePathname();
 const [visible,setVisible]=useState(true),[focused,setFocused]=useState(false);
 useEffect(()=>{let inView=true;const visibility=()=>setVisible(inView&&!document.hidden);document.addEventListener('visibilitychange',visibility);const observer=new IntersectionObserver(entries=>{inView=entries[0].isIntersecting;visibility()});if(art.current)observer.observe(art.current);return()=>{document.removeEventListener('visibilitychange',visibility);observer.disconnect()}},[]);
 useEffect(()=>{root.current?.querySelector<HTMLElement>('h1')?.focus({preventScroll:true})},[path]);
 const running=visible&&!focused;
 useEffect(()=>{if(!running&&art.current)art.current.style.transform='';},[running]);
 return <main id="main" ref={root} className="icy-auth" data-running={running} onFocusCapture={event=>{if((event.target as HTMLElement).matches('input,textarea,select'))setFocused(true)}} onBlurCapture={event=>{if(!(event.relatedTarget as HTMLElement|null)?.matches('input,textarea,select'))setFocused(false)}}>
  <div className="icy-shell">
   <aside className="icy-story" aria-label="Build. Show. Get hired." onPointerMove={event=>{if(!running||matchMedia('(prefers-reduced-motion: reduce)').matches||!matchMedia('(pointer: fine)').matches||event.pointerType!=='mouse'||!art.current)return;const b=event.currentTarget.getBoundingClientRect();const x=(event.clientX-b.left)/b.width-.5,y=(event.clientY-b.top)/b.height-.5;art.current.style.transform=`translate(${x*12}px, ${y*12}px) rotate(${x*2}deg)`}} onPointerLeave={()=>{if(art.current)art.current.style.transform=''}}>
    <div className="icy-art-parallax" ref={art} aria-hidden="true"><img className="spectral-auth-art" src="/spectral/glass-ribbon.webp" alt="" width={1536} height={1024}/></div>
    <Link href="/" className="icy-logo" aria-label="getLancer home"><Brand/></Link>
    <div className="icy-story-copy"><h2>Build.<br/>Show.<br/><span>Get hired.</span></h2><p>A community where developers showcase real work and clients discover and hire the people who built it.</p><div className="icy-signature">Code builds opportunity</div></div>
    <p className="icy-manifesto">Ideas<br/>People<br/>Projects<br/>A brighter<br/>tomorrow<span>—</span></p>

   </aside>
   {children}
  </div>
 </main>
}
