'use client';
import {useEffect,useRef} from 'react';
const cards=['freelancers','projects','clients','opportunities'] as const;
function poseAt(progress:number){
 const angle=-Math.PI/4-progress*2*Math.PI;
 return {x:.5+.35*Math.cos(angle),y:.5+.275*Math.sin(angle)};
}
export default function AuthOrbit(){
 const scene=useRef<HTMLDivElement>(null),elapsed=useRef(0);
 useEffect(()=>{
  const node=scene.current;if(!node)return;
  const images=Array.from(node.querySelectorAll<HTMLElement>('.auth-orbit-card'));
  const reduced=matchMedia('(prefers-reduced-motion: reduce)'),compact=matchMedia('(max-width: 760px)');
  let frame=0,last=0,inView=true,disposed=false,width=node.clientWidth,height=node.clientHeight;
  const paint=()=>images.forEach((image,index)=>{
   const initial=poseAt(index/4),pose=poseAt(elapsed.current/72000+index/4);
   image.style.transform=`translate3d(${(pose.x-initial.x)*width}px,${(pose.y-initial.y)*height}px,0) translate(-50%,-50%)`;
  });
  const tick=(now:number)=>{if(last)elapsed.current+=Math.min(now-last,64);last=now;paint();frame=requestAnimationFrame(tick);};
  const sync=()=>{
   if(disposed)return;
   cancelAnimationFrame(frame);last=0;
   const running=!reduced.matches&&!compact.matches&&!document.hidden&&inView;
   node.dataset.running=String(running);
   if(reduced.matches){elapsed.current=0;paint();}
   if(running)frame=requestAnimationFrame(tick);
  };
  const observer=new IntersectionObserver(entries=>{inView=entries[0].isIntersecting;sync();});observer.observe(node);
  const resize=new ResizeObserver(()=>{width=node.clientWidth;height=node.clientHeight;paint();});resize.observe(node);
  document.addEventListener('visibilitychange',sync);reduced.addEventListener('change',sync);compact.addEventListener('change',sync);paint();sync();
  return()=>{disposed=true;cancelAnimationFrame(frame);observer.disconnect();resize.disconnect();document.removeEventListener('visibilitychange',sync);reduced.removeEventListener('change',sync);compact.removeEventListener('change',sync);};
 },[]);
 return <div className="auth-scene" ref={scene} data-running="false">
  <div className="auth-scene-art" aria-hidden="true">
   <img className="auth-scene-frame" src="/auth/pearlescent-frame-complete.webp" alt="" width={1536} height={1024}/>
   <img className="auth-scene-laptop" src="/auth/laptop.webp" alt="" width={1448} height={1086} fetchPriority="high"/>
   {cards.map(card=><img key={card} className={`auth-orbit-card auth-orbit-${card}`} src={`/auth/${card}.webp`} alt="" width={1254} height={1254}/>)}
  </div>
 </div>;
}
