import {test} from 'node:test';
import assert from 'node:assert/strict';
import {slotPool} from '../lib/publishing.ts';

test('publishing query categories resolve only to fixed keys, including hostile property names',()=>{
 for(const value of ['PROJECT','TEMPLATE','COMPONENT'])assert.equal(slotPool(value),value);
 for(const value of [null,'','__proto__','constructor','prototype','toString','component','TEMPLATE/../PROJECT','<img src=x onerror=alert(1)>']){
  assert.equal(slotPool(value),'PROJECT');
 }
});
