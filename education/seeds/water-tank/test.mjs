import assert from 'node:assert/strict'; import { simulate } from './simulate.mjs';
const rows = simulate(); assert.deepEqual(rows.map(row => row.pump), [true, true, false, false, false, true]);
assert.equal(rows[4].alarm, true); assert.equal(rows[4].levelPercent, null); assert.ok(rows.every(row => row.simulated)); assert.deepEqual(rows, simulate());
assert.deepEqual(simulate([20, 21, 79, 80, 21]).map(row => row.pump), [true, true, true, false, false]);
assert.deepEqual(simulate([5, null, 50]).map(row => row.pump), [true, false, false]);
for (const bad of [[-1], [101], [NaN], [Infinity], ['20'], Array(1001).fill(0), null]) assert.throws(() => simulate(bad));
for (const settings of [{ low: 80, high: 20 }, { low: NaN, high: 80 }, { low: 0, high: 101 }]) assert.throws(() => simulate([], settings));
console.log('Simulated hysteresis and sensor fault regression passed; no pump or board was controlled.');
