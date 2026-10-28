'use client';
import {useEffect, useRef, type ReactNode} from 'react';
import {ChevronDown} from 'lucide-react';

export default function WorkspaceNavigation({current, children}:{current:string;children:ReactNode}) {
 const disclosure=useRef<HTMLDetailsElement>(null);
 useEffect(()=>{
  const media=matchMedia('(max-width: 1000px)');
  const resize=()=>{if(disclosure.current)disclosure.current.open=!media.matches;};
  resize();media.addEventListener('change',resize);
  return()=>media.removeEventListener('change',resize);
 },[current]);
 return <details className="studio-navigation" ref={disclosure} open>
  <summary className="studio-navigation-toggle"><span>Workspace / {current}</span><ChevronDown size={18} aria-hidden="true"/></summary>
  {children}
 </details>;
}
