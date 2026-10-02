const cards=['freelancers','projects','clients','opportunities'] as const;
export default function AuthOrbit(){
 return <div className="auth-scene">
  <div className="auth-scene-art" aria-hidden="true">
   <img className="auth-scene-frame" src="/auth/pearlescent-frame-complete.webp" alt="" width={1536} height={1024}/>
   <img className="auth-scene-laptop" src="/auth/laptop.webp" alt="" width={1448} height={1086} fetchPriority="high"/>
   {cards.map(card=><img key={card} className={`auth-orbit-card auth-orbit-${card}`} src={`/auth/${card}.webp`} alt="" width={1254} height={1254}/>)}
  </div>
 </div>;
}
