import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import test from 'node:test';

// Execute the production value mapping without substituting a second copy.
// Real Radix interaction and the rendered stylesheet cascade are covered by
// tests/browser/round2-ui.spec.ts in the complete browser workflow.
const source=readFileSync(new URL('../app/components/ui.tsx',import.meta.url),'utf8');
const start=source.indexOf('function encodePickerValue('),end=source.indexOf('export function Picker(');
assert.ok(start>=0&&end>start,'The production picker mapping is available');
const {encodePickerValue,decodePickerValue}=Function(stripTypeScriptTypes(source.slice(start,end),{mode:'transform'})+';return {encodePickerValue,decodePickerValue};')();

test('the legitimate ALL taxonomy name stays distinct from clearing a picker',()=>{
  assert.notEqual(encodePickerValue('ALL'),encodePickerValue(''));
  assert.equal(decodePickerValue(encodePickerValue('ALL')),'ALL');
  assert.equal(decodePickerValue(encodePickerValue('')),'');
});

test('normal and prefix-like taxonomy names preserve their exact API value',()=>{
  const values=['Inventory','ALL','value:Inventory','value:ALL','__ALL__','a:b','Öffentliche Projekte','W'.repeat(100)];
  assert.equal(new Set(values.map(encodePickerValue)).size,values.length);
  for(const value of values)assert.equal(decodePickerValue(encodePickerValue(value)),value);
});
