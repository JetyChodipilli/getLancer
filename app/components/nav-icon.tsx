'use client';

import {LazyMotion,domAnimation,useAnimation} from 'motion/react';
import * as m from 'motion/react-m';
import {useEffect,useRef} from 'react';
import {IconParts,type NavIconName} from './animated-icons/parts';

export type {NavIconName} from './animated-icons/parts';

// Local MIT adaptations are attributed in parts.tsx and the third-party notices.
// Labels belong to the enclosing control; the SVG is always decorative.
export default function NavIcon({name}:{name:NavIconName}){
 const icon=useRef<SVGSVGElement>(null),controls=useAnimation();
 useEffect(()=>{
  const control=icon.current?.closest<HTMLElement>('a,button,[role="menuitem"]');
  if(!control)return;
  const play=()=>{
   // Check at activation too: preferences can change between React renders.
   if(window.matchMedia('(prefers-reduced-motion: reduce)').matches)void controls.start('normal',{duration:0});
   else void controls.start('animate');
  };
  const enter=(event:PointerEvent)=>{if(event.pointerType!=='touch'&&window.matchMedia('(hover:hover) and (pointer:fine)').matches)play();};
  const focus=()=>{if(control.matches(':focus-visible'))play();};
  const key=(event:KeyboardEvent)=>{if(!event.repeat&&(event.key==='Enter'||event.key===' '&&(control.tagName==='BUTTON'||control.getAttribute('role')==='menuitem')))play();};
  const click=(event:MouseEvent)=>{if(event.detail===0)play();};
  control.addEventListener('pointerenter',enter);control.addEventListener('focusin',focus);
  control.addEventListener('pointerdown',play);control.addEventListener('keydown',key);control.addEventListener('click',click);
  return()=>{
   control.removeEventListener('pointerenter',enter);control.removeEventListener('focusin',focus);
   control.removeEventListener('pointerdown',play);control.removeEventListener('keydown',key);control.removeEventListener('click',click);controls.stop();
  };
 },[controls,name]);
 useEffect(()=>{
  const preference=window.matchMedia('(prefers-reduced-motion: reduce)');
  // start() replaces each descendant animation; root stop()+set() leaves the
  // descendant drivers running, able to overwrite their newly reset values.
  const reset=()=>{if(preference.matches)void controls.start('normal',{duration:0});};
  preference.addEventListener('change',reset);reset();
  return()=>preference.removeEventListener('change',reset);
 },[controls]);
 return <LazyMotion features={domAnimation} strict><m.svg ref={icon} className="paper-nav-icon" data-nav-icon={name} width={18} height={18} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.7} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false" initial="normal" animate={controls}><IconParts name={name}/></m.svg></LazyMotion>;
}
