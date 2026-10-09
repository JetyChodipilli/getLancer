/** Public lab control-plane contracts. These helpers never simulate execution. */
export type LabStatus = 'QUEUED' | 'STARTING' | 'RUNNING' | 'CANCELLING' | 'CANCELLED' | 'SUCCEEDED' | 'FAILED' | 'EXPIRED';
export type LabInput = {name:string;label:string;type:'TEXT'|'INTEGER'|'BOOLEAN'|'ENUM';required:boolean;maxLength:number;min:number;max:number;choices:string[]};
export type LabScenario = {id:string;title:string;description:string;inputs:LabInput[];operations:{id:string;label:string}[]};
export type LabManifest = {id:string;title:string;summary:string;language:string;framework:string;sourceUrl:string;setup:string;executionMode:string;scenarios:LabScenario[]};
export type LabRuntime = {enabled:boolean;reason:string};
export type LabCatalogue = {items:LabManifest[];runtime:LabRuntime};
export type LabQuota = {dailyLimit:number;remaining:number;resetAt:string;activeRunId:string|null;runtime:LabRuntime};
export type LabRun = {id:string;manifestId:string;scenarioId:string;status:LabStatus;executionMode:string;verified:boolean;requestedAt:string;expiresAt:string;reason:string;eventsUrl:string};
export type LabOperator = {runtime:LabRuntime;paused:boolean;pauseReason:string;queued:number;starting:number;running:number;cancelling:number;attention:number;activeReservations:number;memoryReservedMiB:number;dailyReservedMicros:number;monthlyReservedMicros:number};
export type LabRunBody = {manifestId:string;scenarioId:string;inputs:Record<string,string>};
export type LabAttempt<T = LabRunBody> = {key:string;body:T;identity:string};
export type LabEvent = {runId:string;sequence:number;type:string;recordedAt:string;data:{status?:LabStatus;reason?:string}};
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
export function parseLabEvents(text:string,runId:string,lastSequence=0):LabEvent[]{
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
  if(Object.keys(event.data).some(key=>!['status','reason'].includes(key))||(event.data.status!==undefined&&!statuses.has(event.data.status))||(event.data.reason!==undefined&&(typeof event.data.reason!=='string'||event.data.reason.length>2000)))throw Error('The activity response contained unsupported data.');
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
