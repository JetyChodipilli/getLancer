import assert from 'node:assert/strict';
export function percentile(values,p=.75){
 assert.ok(values.length>0&&Number.isFinite(p)&&p>0&&p<=1,'Finite percentile and samples required');
 assert.ok(values.every(x=>Number.isFinite(x)&&x>=0),'Nonnegative finite samples required');
 return [...values].sort((a,b)=>a-b)[Math.ceil(values.length*p)-1];
}
export function verifyPerformanceSamples(samples){
 assert.ok(Array.isArray(samples)&&samples.length>=3,'At least three independent page samples required');
 for(const x of samples) assert.ok(x&&Number.isFinite(x.lcp)&&x.lcp>0&&Number.isFinite(x.cls)&&x.cls>=0&&Number.isFinite(x.interactionMs)&&x.interactionMs>=0,'Complete browser samples required');
 const result={samples:samples.length,lcpP75Ms:percentile(samples.map(x=>x.lcp)),clsP75:percentile(samples.map(x=>x.cls)),interactionProxyP75Ms:percentile(samples.map(x=>x.interactionMs))};
 assert.ok(result.lcpP75Ms<=2500,'LCP p75 exceeds 2500ms');
 assert.ok(result.clsP75<=.1,'CLS p75 exceeds 0.1');
 assert.ok(result.interactionProxyP75Ms<=200,'Interaction proxy p75 exceeds 200ms');
 return result; // Lab interaction proxy is not field INP.
}
