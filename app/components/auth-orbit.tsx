'use client';
import {useEffect,useRef,useState} from 'react';
import {Pause,Play} from 'lucide-react';

// Shared closed path: the four starting poses follow the supplied reference.
const poses=[{x:.74,y:.19,w:.37},{x:.19,y:.43,w:.38},{x:.19,y:.74,w:.29},{x:.78,y:.64,w:.43}];
const cards=['freelancers','projects','clients','opportunities'] as const;
function poseAt(progress:number){
 const step=(progress%1)*4,index=Math.floor(step),t=step-index;
 const p0=poses[(index+3)%4],p1=poses[index],p2=poses[(index+1)%4],p3=poses[(index+2)%4];
 const smooth=(key:'x'|'y'|'w')=>.5*((2*p1[key])+(-p0[key]+p2[key])*t+(2*p0[key]-5*p1[key]+4*p2[key]-p3[key])*t*t+(-p0[key]+3*p1[key]-3*p2[key]+p3[key])*t*t*t);
 return {x:smooth('x'),y:smooth('y'),w:smooth('w')};
}
export default function AuthOrbit(){
 const scene=useRef<HTMLDivElement>(null),elapsed=useRef(0);
 const [paused,setPaused]=useState(false);
 useEffect(()=>{
  const node=scene.current;if(!node)return;
  const root=node.closest('.icy-auth')!;
  const images=Array.from(node.querySelectorAll<HTMLElement>('.auth-orbit-card'));
  const reduced=matchMedia('(prefers-reduced-motion: reduce)'),compact=matchMedia('(max-width: 760px)');
  let frame=0,last=0,inView=true,disposed=false,width=node.clientWidth,height=node.clientHeight;
  const paint=()=>images.forEach((image,index)=>{
   const initial=poses[index],pose=poseAt(elapsed.current/72000+index/4);
   image.style.transform=`translate3d(${(pose.x-initial.x)*width}px,${(pose.y-initial.y)*height}px,0) translate(-50%,-50%) scale(${pose.w/initial.w})`;
   image.style.zIndex=pose.y>.5?'4':'2';
  });
  const tick=(now:number)=>{if(last)elapsed.current+=Math.min(now-last,64);last=now;paint();frame=requestAnimationFrame(tick);};
  const sync=()=>{
   if(disposed)return;
   cancelAnimationFrame(frame);last=0;
   const typing=root.contains(document.activeElement)&&document.activeElement?.matches('input,textarea,select');
   const running=!paused&&!reduced.matches&&!compact.matches&&!document.hidden&&inView&&!typing;
   node.dataset.running=String(running);
   if(reduced.matches){elapsed.current=0;paint();}
   if(running)frame=requestAnimationFrame(tick);
  };
  const observer=new IntersectionObserver(entries=>{inView=entries[0].isIntersecting;sync();});observer.observe(node);
  const resize=new ResizeObserver(()=>{width=node.clientWidth;height=node.clientHeight;paint();});resize.observe(node);
  const afterFocus=()=>queueMicrotask(sync);
  document.addEventListener('visibilitychange',sync);root.addEventListener('focusin',sync);root.addEventListener('focusout',afterFocus);
  reduced.addEventListener('change',sync);compact.addEventListener('change',sync);paint();sync();
  return()=>{disposed=true;cancelAnimationFrame(frame);observer.disconnect();resize.disconnect();document.removeEventListener('visibilitychange',sync);root.removeEventListener('focusin',sync);root.removeEventListener('focusout',afterFocus);reduced.removeEventListener('change',sync);compact.removeEventListener('change',sync);};
 },[paused]);
 return <div className="auth-scene" ref={scene} data-running="false">
  <div className="auth-scene-art" aria-hidden="true">
   <img className="auth-scene-frame" src="/auth/pearlescent-frame.webp" alt="" width={1086} height={1448}/>
   <img className="auth-scene-laptop" src="/auth/laptop.webp" alt="" width={1448} height={1086} fetchPriority="high"/>
   {cards.map(card=><img key={card} className={`auth-orbit-card auth-orbit-${card}`} src={`/auth/${card}.webp`} alt="" width={1254} height={1254}/>)}
  </div>
  <button className="auth-orbit-toggle" type="button" aria-label={paused?'Play animation':'Pause animation'} onClick={()=>setPaused(value=>!value)}>{paused?<Play size={14} aria-hidden="true"/>:<Pause size={14} aria-hidden="true"/>}<span>{paused?'Play':'Pause'}</span></button>
 </div>;
}
