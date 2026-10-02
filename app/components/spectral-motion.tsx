'use client';
import {useEffect,useRef,type ReactNode} from 'react';

export default function SpectralMotion({children}:{children:ReactNode}) {
 const scene=useRef<HTMLDivElement>(null);
 useEffect(()=>{
  const node=scene.current;if(!node)return;
  const reduce=matchMedia('(prefers-reduced-motion: reduce)'),fine=matchMedia('(hover: hover) and (pointer: fine)');
  let visible=true,frame=0,targetX=0,targetY=0,x=0,y=0,last=0;
  const reset=()=>{targetX=targetY=x=y=0;node.style.setProperty('--pointer-x','0px');node.style.setProperty('--pointer-y','0px');};
  const draw=(time:number)=>{
   frame=0;
   if(reduce.matches||!visible||document.hidden)return;
   const step=1-Math.exp(-Math.min(time-last||16,50)/100);last=time;
   x+=(targetX-x)*step;y+=(targetY-y)*step;
   node.style.setProperty('--pointer-x',`${x.toFixed(3)}px`);node.style.setProperty('--pointer-y',`${y.toFixed(3)}px`);
   if(Math.abs(targetX-x)+Math.abs(targetY-y)>.02)frame=requestAnimationFrame(draw);
  };
  const schedule=()=>{if(!frame&&!reduce.matches&&visible&&!document.hidden){last=0;frame=requestAnimationFrame(draw);}};
  const pointer=(event:PointerEvent)=>{
   if(!fine.matches||reduce.matches||node.contains(document.activeElement)&&document.activeElement!==document.body)return;
   const bounds=node.getBoundingClientRect();targetX=((event.clientX-bounds.left)/bounds.width-.5)*12;targetY=((event.clientY-bounds.top)/bounds.height-.5)*8;schedule();
  };
  const leave=()=>{targetX=targetY=0;schedule();};
  const state=()=>{const running=visible&&!document.hidden&&!reduce.matches;node.dataset.motion=running?'running':'paused';if(!running){cancelAnimationFrame(frame);frame=0;reset();}};
  const observer=new IntersectionObserver(entries=>{visible=entries[0].isIntersecting;state();},{threshold:.1});observer.observe(node);
  node.addEventListener('pointermove',pointer,{passive:true});node.addEventListener('pointerleave',leave);node.addEventListener('focusin',leave);
  document.addEventListener('visibilitychange',state);reduce.addEventListener('change',state);state();
  return()=>{observer.disconnect();cancelAnimationFrame(frame);node.removeEventListener('pointermove',pointer);node.removeEventListener('pointerleave',leave);node.removeEventListener('focusin',leave);document.removeEventListener('visibilitychange',state);reduce.removeEventListener('change',state);};
 },[]);
 return <div className="spectral-scene" ref={scene}>{children}</div>;
}
