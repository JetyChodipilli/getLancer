import test from 'node:test';
import assert from 'node:assert/strict';
import {percentile,verifyPerformanceSamples} from './acceptance-metrics.mjs';

test('performance acceptance measures independent nearest-rank p75 values without changing samples',()=>{
 assert.equal(percentile([40,10,30,20]),30);
 const samples=[{lcp:1800,cls:0,interactionMs:100},{lcp:2500,cls:.1,interactionMs:200},{lcp:2100,cls:.05,interactionMs:150}],original=structuredClone(samples);
 assert.deepEqual(verifyPerformanceSamples(samples),{samples:3,lcpP75Ms:2500,clsP75:.1,interactionProxyP75Ms:200});assert.deepEqual(samples,original);
});
test('each performance threshold and missing, non-finite or invalid measurement fails closed',()=>{
 const good={lcp:2000,cls:.05,interactionMs:150};
 for(const sample of [{...good,lcp:2501},{...good,cls:.101},{...good,interactionMs:201},{...good,lcp:0},{...good,lcp:NaN},{...good,cls:Infinity},{...good,interactionMs:-1},{lcp:2000,cls:.05},{...good,lcp:'2000'}])assert.throws(()=>verifyPerformanceSamples([good,good,sample]));
 assert.throws(()=>verifyPerformanceSamples([good,good]));
 for(const values of [[],[1,NaN],[-1,1],['2',1]])assert.throws(()=>percentile(values));
 for(const p of [0,1.1,NaN])assert.throws(()=>percentile([1,2,3],p));
});
