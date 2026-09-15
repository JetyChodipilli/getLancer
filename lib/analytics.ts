const channels=['direct','organic','social','referral','builder_share','founder_outreach','agency_referral'] as const;
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
export function track(eventName:string,entityId?:string,properties:Record<string,unknown>={}){
 if(!analyticsConsent()||!globalThis.crypto?.randomUUID)return;
 try{
  let sessionId=sessionStorage.getItem('getlancer.analytics-session');if(!sessionId){sessionId=crypto.randomUUID();sessionStorage.setItem('getlancer.analytics-session',sessionId)}
  const sourcePage=location.pathname==='/'?'explore':location.pathname.startsWith('/products/')?'product':location.pathname.startsWith('/builders/')?'builder':location.pathname==='/inquiry'?'inquiry':'other';
  void fetch('/api/v1/analytics/events',{method:'POST',credentials:'same-origin',keepalive:true,headers:{'Content-Type':'application/json','X-Requested-With':'getlancer'},body:JSON.stringify({eventName,entityId,eventId:crypto.randomUUID(),sessionId,occurredAt:new Date().toISOString(),source:'web',properties:{...properties,sourcePage,acquisitionSource:attribution()}})}).catch(()=>{});
 }catch{/* Optional analytics never block a core flow. */}
}
