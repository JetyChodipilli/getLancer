import {test} from 'node:test';
import assert from 'node:assert/strict';
import {componentSaveKey,parseComponentSaves,setComponentSave} from '../lib/component-saves.ts';
test('sample saves are idempotent, removable and isolated by actor',()=>{
  const first=setComponentSave([], 'quiet-sign-in', true);
  assert.deepEqual(setComponentSave(first,'quiet-sign-in',true),first);
  assert.deepEqual(setComponentSave(first,'quiet-sign-in',false),[]);
  assert.notEqual(componentSaveKey('visitor'),componentSaveKey('builder'));
  assert.deepEqual(parseComponentSaves(JSON.stringify(['quiet-sign-in','quiet-sign-in'])),first);
});
test('invalid stored references and excessive lists fail without partial success',()=>{
  assert.throws(()=>parseComponentSaves('{}'));
  assert.throws(()=>parseComponentSaves('["../private"]'));
  assert.throws(()=>parseComponentSaves('[123]'));
  assert.throws(()=>setComponentSave([], '../private',true));
  assert.throws(()=>setComponentSave(Array.from({length:200},(_,n)=>'entry-'+n),'new-entry',true));
  assert.deepEqual(parseComponentSaves(null),[]);
});
