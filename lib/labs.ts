/** Public lab control-plane contracts. These helpers never simulate execution. */
export type LabStatus = 'QUEUED' | 'STARTING' | 'RUNNING' | 'CANCELLING' | 'CANCELLED' | 'SUCCEEDED' | 'FAILED' | 'EXPIRED';
export type LabInput = {name:string;label:string;type:'TEXT'|'INTEGER'|'BOOLEAN'|'ENUM';required:boolean;maxLength:number;min:number;max:number;choices:string[]};
export type LabScenario = {id:string;title:string;description:string;inputs:LabInput[];operations:{id:string;label:string}[]};
export type LabManifest = {id:string;title:string;summary:string;language:string;framework:string;sourceUrl:string;sourceSha256?:string;setup:string;executionMode:string;scenarios:LabScenario[];adapter?:{runtime:string;version:string;state:'ACTIVE'|'DISABLED'|'RETIRED'};availability?:{enabled:boolean;reason:string}};
export type LabRuntime = {enabled:boolean;reason:string};
export type LabCatalogue = {items:LabManifest[];runtime:LabRuntime};
export type LabQuota = {dailyLimit:number;remaining:number;resetAt:string;activeRunId:string|null;runtime:LabRuntime};
export type LabRun = {id:string;manifestId:string;scenarioId:string;status:LabStatus;executionMode:string;verified:boolean;requestedAt:string;expiresAt:string;reason:string;eventsUrl:string};
export type LabOperator = {runtime:LabRuntime;paused:boolean;pauseReason:string;queued:number;starting:number;running:number;cancelling:number;attention:number;activeReservations:number;memoryReservedMiB:number;dailyReservedMicros:number;monthlyReservedMicros:number;limits?:LabLimits;certifications?:LabCertificate[];alerts?:{code:string;count:number;message:string}[]};
export type LabRunBody = {manifestId:string;scenarioId:string;inputs:Record<string,string>};
export type LabAttempt<T = LabRunBody> = {key:string;body:T;identity:string};
export type LabEvent = {runId:string;sequence:number;type:string;recordedAt:string;data:{status?:LabStatus;reason?:string;observation?:LabObservation}};
const statuses = new Set<LabStatus>(['QUEUED','STARTING','RUNNING','CANCELLING','CANCELLED','SUCCEEDED','FAILED','EXPIRED']);
const terminal = new Set<LabStatus>(['CANCELLED','SUCCEEDED','FAILED','EXPIRED']);
export function labStatusLabel(status:LabStatus){return status.toLowerCase().replace(/^./,value=>value.toUpperCase());}
export function labTerminal(status:LabStatus){return terminal.has(status);}
export function labExpired(run:Pick<LabRun,'expiresAt'>,now=Date.now()){const expiry=Date.parse(run.expiresAt);return !Number.isFinite(expiry)||expiry<=now;}
export function labCanRequest(run:LabRun|null,now=Date.now()){return !!run&&run.status==='RUNNING'&&run.verified&&!labExpired(run,now);}
export function labCanStop(run:LabRun|null){return !!run&&['QUEUED','STARTING','RUNNING'].includes(run.status);}
export function labExpiryText(run:Pick<LabRun,'expiresAt'>,now=Date.now()){
 const milliseconds=Date.parse(run.expiresAt)-now;
 if(!Number.isFinite(milliseconds))return 'Expiry could not be verified. Requests are unavailable.';
 if(milliseconds<=0)return 'This run has expired. Requests are unavailable.';
 if(milliseconds<=60000)return 'Expires in less than a minute. Save any useful response now.';
 return 'Expires at '+new Date(run.expiresAt).toLocaleTimeString('en-GB',{hour:'2-digit',minute:'2-digit'});
}
export function labFieldErrors(definitions:LabInput[],values:Record<string,string>):Record<string,string>{
 const errors:Record<string,string>={};
 for(const field of definitions){
  const value=values[field.name]??'';
  if(field.required&&!value.trim()){errors[field.name]='Enter '+field.label.toLowerCase()+'.';continue;}
  if(!value)continue;
  if(value.length>Math.min(field.maxLength||1024,4096)){errors[field.name]='Use at most '+Math.min(field.maxLength||1024,4096)+' characters.';continue;}
  if(field.type==='INTEGER'&&(!/^-?\d+$/.test(value)||!Number.isSafeInteger(Number(value))||Number(value)<field.min||Number(value)>field.max))errors[field.name]='Enter a whole number from '+field.min+' to '+field.max+'.';
  else if(field.type==='BOOLEAN'&&!['true','false'].includes(value))errors[field.name]='Choose yes or no.';
  else if(field.type==='ENUM'&&!field.choices.includes(value))errors[field.name]='Choose an available value.';
 }
 return errors;
}
function identity(body:{inputs:Record<string,string>}&Record<string,unknown>){
 const inputs=Object.fromEntries(Object.keys(body.inputs).sort().map(name=>[name,body.inputs[name]]));
 return JSON.stringify({...body,inputs});
}
/** Retain this object after an uncertain response; retrying cannot reserve a new identity. */
export function labAttempt<T extends {inputs:Record<string,string>}&Record<string,unknown>>(body:T,previous:LabAttempt<T>|null=null,newKey:()=>string=()=>crypto.randomUUID()):LabAttempt<T>{
 const nextIdentity=identity(body);
 if(new TextEncoder().encode(nextIdentity).length>65536)throw Error('The request inputs exceed the 64 KiB limit.');
 if(previous){if(previous.identity!==nextIdentity)throw Error('Resolve the previous request before changing its inputs.');return previous;}
 const key=newKey();if(!/^[A-Za-z0-9._-]{8,100}$/.test(key))throw Error('The request identity is invalid.');
 return {key,body:structuredClone(body),identity:nextIdentity};
}
export function labSourceUrl(value:unknown):string|undefined{
 if(typeof value!=='string'||value.length>2048||/[\u0000-\u0020\u007f]/.test(value))return;
 if(/^\/components\/[a-z0-9][a-z0-9-]{0,119}(?:\/source)?$/.test(value))return value;
 try{const url=new URL(value);if(url.protocol==='https:'&&!url.username&&!url.password)return url.href;}catch{return;}
}
/** Fixed same-origin route only. Server-provided gateway/worker URLs are never followed. */
/** Reject an unusable success response instead of showing unverified execution as success. */
export function labRunResponse(value:unknown):LabRun{
 if(!value||typeof value!=='object'||Array.isArray(value))throw Error('The run response could not be verified.');
 const run=value as LabRun;
 labEventsPath(run.id);
 if(!statuses.has(run.status)||typeof run.verified!=='boolean'||typeof run.manifestId!=='string'||typeof run.scenarioId!=='string'||typeof run.executionMode!=='string'||typeof run.reason!=='string'||run.reason.length>2000||!Number.isFinite(Date.parse(run.requestedAt))||!Number.isFinite(Date.parse(run.expiresAt)))throw Error('The run response could not be verified.');
 return {id:run.id,manifestId:run.manifestId,scenarioId:run.scenarioId,status:run.status,executionMode:run.executionMode,verified:run.verified,requestedAt:run.requestedAt,expiresAt:run.expiresAt,reason:run.reason,eventsUrl:labEventsPath(run.id)};
}
export function labEventsPath(runId:string){if(!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(runId))throw Error('The run identity is invalid.');return '/api/v1/lab-runs/'+encodeURIComponent(runId)+'/events';}
/** Bounded finite SSE replay. Cursor is advanced only after a complete validated response. */
export function parseLabEvents(text:string,runId:string,lastSequence=0,sourceHash?:string):LabEvent[]{
 if(new TextEncoder().encode(text).length>65536)throw Error('The activity response exceeded its limit.');
 const events:LabEvent[]=[];let cursor=lastSequence;
 for(const block of text.replaceAll('\r\n','\n').split('\n\n')){
  const lines=block.split('\n'),data=lines.filter(line=>line.startsWith('data:')).map(line=>line.slice(5).trimStart()).join('\n');
  if(!data)continue;
  if(events.length>=100)throw Error('The activity response exceeded its event limit.');
  let event:LabEvent;try{event=JSON.parse(data);}catch{throw Error('The activity response was invalid.');}
  if(!event||event.runId!==runId||!Number.isSafeInteger(event.sequence)||event.sequence<=cursor||typeof event.type!=='string'||event.type.length>80||!Number.isFinite(Date.parse(event.recordedAt))||!event.data||typeof event.data!=='object'||Array.isArray(event.data))throw Error('The activity response could not be verified.');
  const id=lines.find(line=>line.startsWith('id:'))?.slice(3).trim();
  if(id&&id!==String(event.sequence))throw Error('The activity cursor was invalid.');
  if(Object.keys(event.data).some(key=>!['status','reason','observation'].includes(key))||(event.data.status!==undefined&&!statuses.has(event.data.status))||(event.data.reason!==undefined&&(typeof event.data.reason!=='string'||event.data.reason.length>2000)))throw Error('The activity response contained unsupported data.');
  if(event.data.observation!==undefined)event.data.observation=labObservation(event.data.observation,runId,sourceHash);
  events.push({runId:event.runId,sequence:event.sequence,type:event.type,recordedAt:event.recordedAt,data:{...event.data}});cursor=event.sequence;
 }
 return events;
}
/** A stale replay may not resurrect a terminal state in the interface. */
export function labApplyEvents(run:LabRun,events:LabEvent[]):LabRun{
 let current={...run};
 for(const event of events){if(event.runId!==current.id)throw Error('Activity belongs to a different run.');if(event.data.status&&!(labTerminal(current.status)&&!labTerminal(event.data.status)))current={...current,status:event.data.status,reason:event.data.reason??current.reason};}
 return current;
}
export function labMoney(micros:number){return new Intl.NumberFormat('en-GB',{maximumFractionDigits:2}).format(micros/1000000);}

export type LabObservation={runId:string;sourceHash:string;requestId:string;pattern:'cache'|'security'|'payment';status:number;durationMs:number;state:Record<string,string|number>;events:{runId:string;sourceHash:string;requestId:string;sequence:number;type:string;edge:string;recordedAt:string;summary:string}[]};
export type LabLimits={dailyRunLimit:number;maxActiveRuns:number;maxMemoryMiB:number;dailyBudgetMicros:number;monthlyBudgetMicros:number};
export type LabCertificate={id:string;title:string;language:string;framework:string;adapter?:LabManifest['adapter'];approvedUntil:string;manifestSha256:string;status:string;reason:string};
/** No replay recording or client-manufactured transition is used to establish this evidence. */
export function labObservation(value:unknown,runId:string,sourceHash?:string):LabObservation{
 const fail=():never=>{throw Error('Observed native activity could not be verified.');};
 const exact=(value:unknown,keys:string[])=>{if(!value||typeof value!=='object'||Array.isArray(value))return fail();const object=value as Record<string,unknown>;if(Object.keys(object).length!==keys.length||keys.some(key=>!Object.hasOwn(object,key)))return fail();return object;};
 const integer=(value:unknown,min:number,max:number)=>{if(typeof value!=='number'||!Number.isSafeInteger(value)||value<min||value>max)return fail();return value;};
 const text=(value:unknown,max:number)=>{if(typeof value!=='string'||!value.length||value.length>max||/[\u0000-\u001f\u007f]/.test(value))return fail();return value;};
 const one=<T extends string>(value:unknown,choices:readonly T[]):T=>{if(typeof value!=='string'||!choices.includes(value as T))return fail();return value as T;};
 const uuid=/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/;
 const root=exact(value,['runId','sourceHash','requestId','pattern','status','durationMs','state','events']);
 if(root.runId!==runId||!sourceHash||root.sourceHash!==sourceHash||!/^[a-f0-9]{64}$/.test(sourceHash)||!uuid.test(text(root.requestId,36)))return fail();
 const pattern=one(root.pattern,['cache','security','payment'] as const),status=integer(root.status,100,599),durationMs=integer(root.durationMs,0,30000);
 if(!(pattern==='cache'?[200]:pattern==='security'?[200,401,403]:[200,202,401,409]).includes(status))return fail();
 let state:Record<string,string|number>;
 if(pattern==='cache'){const s=exact(root.state,['cache','storeReads','ttlMs']);state={cache:one(s.cache,['MISS','HIT','FALLBACK','EXPIRED','RESET']),storeReads:integer(s.storeReads,0,1000000),ttlMs:integer(s.ttlMs,-2,5000)};}
 else if(pattern==='security'){const s=exact(root.state,['decision','reason','currentRole']);state={decision:one(s.decision,['ALLOW','DENY','RESET']),reason:one(s.reason,['ALLOW','ROLE','TENANT','EXPIRED','SIGNATURE','REVOKED','RESET']),currentRole:one(s.currentRole,['editor','viewer'])};}
 else{const s=exact(root.state,['payment','entitlements','processedEvents']);state={payment:one(s.payment,['PENDING','PAID','REFUNDED']),entitlements:integer(s.entitlements,0,1),processedEvents:integer(s.processedEvents,0,100)};}
 if(pattern==='security'&&((state.decision==='ALLOW')!==(state.reason==='ALLOW')||(state.decision==='RESET')!==(state.reason==='RESET')||state.decision==='ALLOW'&&state.currentRole!=='editor'||status===401&&!['EXPIRED','SIGNATURE'].includes(String(state.reason))||status===403&&!['ROLE','TENANT','REVOKED'].includes(String(state.reason))||status===200&&state.decision==='DENY'&&state.reason!=='REVOKED'))return fail();
 if(pattern==='payment'&&(state.entitlements!==(state.payment==='PAID'?1:0)||status===202&&state.payment!=='PENDING'))return fail();
 if(!Array.isArray(root.events)||root.events.length<1||root.events.length>4)return fail();
 const types=pattern==='cache'?['CACHE_MISS','CACHE_HIT','CACHE_EXPIRED','CACHE_FALLBACK','CACHE_RESET','STORE_READ']:pattern==='security'?['AUTH_ALLOWED','AUTH_DENIED','AUTH_REVOKED','AUTH_RESET']:['PAYMENT_APPLIED','PAYMENT_DUPLICATE','PAYMENT_SIGNATURE_DENIED','PAYMENT_CONFLICT','PAYMENT_PENDING','PAYMENT_RESET','REFUND_PENDING','REFUND_APPLIED'];
 let cursor=0,time=-Infinity;
 const events=root.events.map(value=>{const e=exact(value,['runId','sourceHash','requestId','sequence','type','edge','recordedAt','summary']);if(e.runId!==runId||e.sourceHash!==sourceHash||e.requestId!==root.requestId)return fail();
  const sequence=integer(e.sequence,1,1000000),type=one(e.type,types),edge=text(e.edge,40),recordedAt=text(e.recordedAt,40),summary=text(e.summary,240),nextTime=Date.parse(recordedAt);
  const allowed=type==='STORE_READ'?['service-store']:type.startsWith('CACHE_')?['service-redis']:['AUTH_REVOKED','AUTH_RESET'].includes(type)?['policy-store']:type.startsWith('AUTH_')?['service-policy','policy-store']:['PAYMENT_PENDING','PAYMENT_SIGNATURE_DENIED'].includes(type)?['service-emulator']:['emulator-ledger'];
  if(sequence<=cursor||!Number.isFinite(nextTime)||nextTime<time||!allowed.includes(edge))return fail();
  if(pattern==='cache'&&(type==='CACHE_MISS'&&!['MISS','FALLBACK'].includes(String(state.cache))||type==='STORE_READ'&&(!['MISS','FALLBACK'].includes(String(state.cache))||state.storeReads===0)||['CACHE_HIT','CACHE_EXPIRED','CACHE_FALLBACK','CACHE_RESET'].includes(type)&&type.slice(6)!==state.cache))return fail();
  if(pattern==='security'&&(type==='AUTH_ALLOWED'&&state.decision!=='ALLOW'||type==='AUTH_DENIED'&&state.decision!=='DENY'||type==='AUTH_REVOKED'&&state.reason!=='REVOKED'||type==='AUTH_RESET'&&state.decision!=='RESET'||status!==(type==='AUTH_DENIED'?(['EXPIRED','SIGNATURE'].includes(String(state.reason))?401:403):200)))return fail();
  if(pattern==='payment'&&(type==='PAYMENT_APPLIED'&&(status!==200||state.payment==='PENDING'||state.processedEvents===0)||type==='REFUND_APPLIED'&&(status!==200||state.payment!=='REFUNDED'||state.processedEvents===0)||type==='REFUND_PENDING'&&(status!==202||state.payment!=='PENDING'||state.processedEvents===0)||type==='PAYMENT_RESET'&&(status!==200||state.payment!=='PENDING'||state.processedEvents!==0)||type==='PAYMENT_DUPLICATE'&&(status!==200||state.processedEvents===0)||type==='PAYMENT_SIGNATURE_DENIED'&&status!==401||type==='PAYMENT_CONFLICT'&&status!==409||type==='PAYMENT_PENDING'&&status!==(state.payment==='PENDING'?202:200)))return fail();
  cursor=sequence;time=nextTime;return {runId,sourceHash,requestId:root.requestId as string,sequence,type,edge,recordedAt,summary};
 });return {runId,sourceHash,requestId:root.requestId as string,pattern,status,durationMs,state,events};
}
/** Duplicate exact results are idempotent; new stale or altered batches fail before caller cursors advance. */
export function labMergeObservations(previous:LabObservation[],incoming:LabObservation[],runId:string,sourceHash?:string):LabObservation[]{
 const result=[...previous];let cursor=result.at(-1)?.events.at(-1)?.sequence??0;
 for(const raw of incoming){const observation=labObservation(raw,runId,sourceHash),existing=result.find(item=>item.requestId===observation.requestId);
  if(existing){if(JSON.stringify(existing)!==JSON.stringify(observation))throw Error('Observed response identity changed.');continue;}
  if(observation.events[0].sequence<=cursor)throw Error('Observed activity arrived out of order.');cursor=observation.events.at(-1)!.sequence;result.push(observation);
 }return result.slice(-25);
}
