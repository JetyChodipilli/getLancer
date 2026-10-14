export type OperationFunnel = {stage:string;mode:string;version:string;observations:number;denominator:number;ratePercent:number|null;denominatorLabel:string};
export type OperationsSnapshot = {
 window:{from:string;to:string;days:30;privacy:string;observationStartedAt:string};
 funnels:OperationFunnel[];
 runtime:{admitted:number;completed:number;healthy:number;pending:number;infraFailures:number;infraSuccessPercent:number|null;coveragePercent:number|null;scenarioFailures:number;quotaDenials:number;validationDenials:number;counterEpoch:string;operatorEpoch:string|null;measuredFrom:string};
 alerts:{code:string;severity:'INFO'|'WARNING'|'CRITICAL';count:number;detail:string}[];
 readiness:{runtime:{enabled:boolean;configurationReady:boolean;reason:string};commerce:{enabled:boolean;configurationReady:boolean;mode:string;reason:string};productionCertified:false};
 resources:{active:number;memoryMiB:number;dailyReservedMicros:number;monthlyReservedMicros:number;dailyBudgetMicros:number;monthlyBudgetMicros:number};
};

export const operationsFreshnessMs=5*60*1000;
const invalid=()=>{throw Error('The operations response is incomplete, inconsistent or stale. Refresh to obtain current evidence.');};
function record(value:unknown):Record<string,unknown>{if(!value||typeof value!=='object'||Array.isArray(value))return invalid();return value as Record<string,unknown>;}
function text(value:unknown,max=2000):string{if(typeof value!=='string'||!value.trim()||value.length>max)return invalid();return value;}
function count(value:unknown):number{if(typeof value!=='number'||!Number.isSafeInteger(value)||value<0)return invalid();return value;}
function bool(value:unknown):boolean{if(typeof value!=='boolean')return invalid();return value;}
function date(value:unknown):string{const result=text(value,50),parsed=Date.parse(result);if(!/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d{1,9})?Z$/.test(result)||!Number.isFinite(parsed)||new Date(parsed).toISOString().slice(0,19)!==result.slice(0,19))return invalid();return result;}
function rate(value:unknown,numerator:number,denominator:number):number|null{
 if(!denominator){if(value!==null)return invalid();return null;}
 if(typeof value!=='number'||!Number.isFinite(value)||value<0||value>100||Math.abs(value-numerator/denominator*100)>.011)return invalid();return value;
}
function lane(value:unknown){const row=record(value),enabled=bool(row.enabled),configurationReady=bool(row.configurationReady);if(enabled&&!configurationReady)return invalid();return {enabled,configurationReady,reason:text(row.reason)};}

/** Reject malformed or aged operational data before it can look like release evidence. */
export function operationsSnapshot(value:unknown,now=Date.now()):OperationsSnapshot{
 const body=record(value),window=record(body.window),from=date(window.from),to=date(window.to),observationStartedAt=date(window.observationStartedAt);
 const end=Date.parse(to),start=Date.parse(from);
 const expectedStart=new Date(end);expectedStart.setUTCHours(0,0,0,0);expectedStart.setUTCDate(expectedStart.getUTCDate()-29);
 if(window.days!==30||start!==expectedStart.getTime()||start>=end||end>now+60000||now-end>operationsFreshnessMs||Date.parse(observationStartedAt)>end+60000)return invalid();
 if(!Array.isArray(body.funnels)||body.funnels.length>2000||!Array.isArray(body.alerts)||body.alerts.length>30)return invalid();
 const keys=new Set<string>();
 const funnels=body.funnels.map(value=>{const row=record(value),stage=text(row.stage,60),mode=text(row.mode,80),version=text(row.version,100),observations=count(row.observations),denominator=count(row.denominator),key=JSON.stringify([stage,mode,version]);if(observations>denominator||keys.has(key))return invalid();keys.add(key);return {stage,mode,version,observations,denominator,ratePercent:rate(row.ratePercent,observations,denominator),denominatorLabel:text(row.denominatorLabel,500)};});
 const r=record(body.runtime),admitted=count(r.admitted),completed=count(r.completed),healthy=count(r.healthy),pending=count(r.pending),infraFailures=count(r.infraFailures);
 if(completed!==admitted-pending||healthy>completed||infraFailures!==completed-healthy)return invalid();
 const measuredFrom=date(r.measuredFrom);if(Date.parse(measuredFrom)>end+60000)return invalid();
 const operatorEpoch=r.operatorEpoch===null?null:text(r.operatorEpoch,36);if(operatorEpoch&&!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(operatorEpoch))return invalid();
 const runtime={admitted,completed,healthy,pending,infraFailures,infraSuccessPercent:rate(r.infraSuccessPercent,healthy,completed),coveragePercent:rate(r.coveragePercent,completed,admitted),scenarioFailures:count(r.scenarioFailures),quotaDenials:count(r.quotaDenials),validationDenials:count(r.validationDenials),counterEpoch:date(r.counterEpoch),operatorEpoch,measuredFrom};
 const alerts=body.alerts.map(value=>{const row=record(value),severity=text(row.severity,10);if(!['INFO','WARNING','CRITICAL'].includes(severity))return invalid();return {code:text(row.code,80),severity:severity as 'INFO'|'WARNING'|'CRITICAL',count:count(row.count),detail:text(row.detail)};});
 const readiness=record(body.readiness),commerce=record(readiness.commerce);
 if(readiness.productionCertified!==false)return invalid();
 const commerceMode=text(commerce.mode,20),commerceLane=lane(commerce);if(!['live','test','disabled'].includes(commerceMode.toLowerCase())||commerceLane.enabled&&commerceMode.toLowerCase()==='disabled')return invalid();
 const resources=record(body.resources);
 return {window:{from,to,days:30,privacy:text(window.privacy),observationStartedAt},funnels,runtime,alerts,readiness:{runtime:lane(readiness.runtime),commerce:{...commerceLane,mode:commerceMode},productionCertified:false},resources:{active:count(resources.active),memoryMiB:count(resources.memoryMiB),dailyReservedMicros:count(resources.dailyReservedMicros),monthlyReservedMicros:count(resources.monthlyReservedMicros),dailyBudgetMicros:count(resources.dailyBudgetMicros),monthlyBudgetMicros:count(resources.monthlyBudgetMicros)}};
}

export const operationLabel=(value:string)=>value.replaceAll('_',' ').toLowerCase();
export const operationPercent=(value:number|null)=>value===null?'No eligible observations':value.toFixed(2)+'%';
