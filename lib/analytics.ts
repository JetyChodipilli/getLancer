const channels=['direct','organic','social','referral','builder_share','founder_outreach','agency_referral'] as const;
const events=['home_view','search_performed','unavailable_builder_fallback','builder_profile_view','product_impression','product_view','demo_video_click','live_demo_click','build_similar_click','inquiry_started','component_discovery','component_view','component_source_view','lab_discovery','lab_view'] as const;
export function analyticsConsent(){try{return localStorage.getItem('getlancer.analytics')==='yes'}catch{return false}}
export function attribution(){
 if(!analyticsConsent())return undefined;
 try{
  const prior=sessionStorage.getItem('getlancer.source');if(prior&&channels.includes(prior as typeof channels[number]))return prior;
  const campaign=new URLSearchParams(location.search).get('source');let source=channels.includes(campaign as typeof channels[number])?campaign!:'direct';
  if(!campaign&&document.referrer){const host=new URL(document.referrer).hostname;if(host!==location.hostname)source=/(^|\.)(google\.[a-z.]+|bing\.com|duckduckgo\.com)$/.test(host)?'organic':/(^|\.)(linkedin\.com|twitter\.com|x\.com|facebook\.com)$/.test(host)?'social':'referral'}
  sessionStorage.setItem('getlancer.source',source);return source;
 }catch{return undefined}
}
export function track(eventName:string,entityId?:string,properties:Record<string,unknown>={},artifactSlug?:string){
 if(!analyticsConsent()||!globalThis.crypto?.randomUUID||!events.includes(eventName as typeof events[number]))return;
 if(entityId&&!/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i.test(entityId))return;
 if(artifactSlug&&!/^[a-z0-9][a-z0-9-]{0,139}$/.test(artifactSlug))return;
 try{
  let sessionId=sessionStorage.getItem('getlancer.analytics-session');if(!sessionId){sessionId=crypto.randomUUID();sessionStorage.setItem('getlancer.analytics-session',sessionId)}
  const sourcePage=location.pathname==='/'?'explore':location.pathname.startsWith('/products/')?'product':location.pathname.startsWith('/builders/')?'builder':location.pathname==='/inquiry'?'inquiry':'other';
  const clean:Record<string,unknown>={sourcePage,acquisitionSource:attribution()};
  for(const key of ['resultCount','rankPosition','queryLength']){const value=properties[key];if(typeof value==='number'&&Number.isInteger(value)&&value>=0&&value<=100000)clean[key]=value;}
  if(['youtube','loom','vimeo','external'].includes(String(properties.provider)))clean.provider=properties.provider;
  void fetch('/api/v1/analytics/events',{method:'POST',credentials:'same-origin',keepalive:true,headers:{'Content-Type':'application/json','X-Requested-With':'getlancer'},body:JSON.stringify({eventName,entityId,artifactSlug,consent:true,eventId:crypto.randomUUID(),sessionId,occurredAt:new Date().toISOString(),source:'web',properties:clean})}).catch(()=>{});
 }catch{/* Optional analytics never block a core flow. */}
}
export function observeArtifact(eventName:'component_discovery'|'component_view'|'component_source_view'|'lab_discovery'|'lab_view',artifactSlug?:string){track(eventName,undefined,{},artifactSlug);}
