import {test} from 'node:test';
import assert from 'node:assert/strict';
import {componentReportBody,componentSeeds} from '../lib/components.ts';
const id='a85d6995-2c53-41a6-9d17-22317943ada4';
const item={...componentSeeds[0],id,revision:4,sha256:'a'.repeat(64),files:{'private.txt':'must not escape'},sourceHash:'review-context-hash',reviewReason:'private reason'};
const detail='This exact source version has a misleading execution claim.';
test('report body binds the viewed source only without raw files or private fields',()=>{
 const before=structuredClone(item);assert.deepEqual(componentReportBody(item,'MISLEADING_CLAIM',' '+detail+' '),{targetType:'COMPONENT',targetId:id,componentRevision:4,componentSourceHash:'a'.repeat(64),reason:'MISLEADING_CLAIM',detail});assert.deepEqual(item,before);
});
test('server report identity and revision override a display identity for curated entries',()=>{
 const target='f84ca3e3-aabb-3dda-b809-b3b1109e4423';const body=componentReportBody({...item,reportTargetId:target,reportRevision:1},'COPYRIGHT_IP',detail);assert.equal(body.targetId,target);assert.equal(body.componentRevision,1);assert.equal(body.componentSourceHash,item.sha256);
});
test('missing, malformed, unsafe or forged-looking binding fields cannot make a report body',()=>{
 for(const change of [{id:undefined},{id:'wrong'},{revision:undefined},{revision:0},{revision:1.2},{revision:Number.MAX_SAFE_INTEGER+1},{sha256:'A'.repeat(64)},{sha256:'f'.repeat(63)},{reportTargetId:'not-a-uuid'},{reportRevision:0}])assert.throws(()=>componentReportBody({...item,...change},'OTHER',detail),/Refresh/);
 assert.equal(componentReportBody(item,'OTHER',detail).targetId,id);
});
test('report reason and detail validation retain valid positive controls',()=>{
 assert.throws(()=>componentReportBody(item,'SUSPEND',detail),/reason/);for(const value of ['short',' '.repeat(12),'a'.repeat(3001)])assert.throws(()=>componentReportBody(item,'OTHER',value),/10 to 3000/);assert.equal(componentReportBody(item,'OTHER','a'.repeat(3000)).detail.length,3000);
});
