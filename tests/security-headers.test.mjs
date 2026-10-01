import {test} from 'node:test';import assert from 'node:assert/strict';import {frontendCsp} from '../lib/security-headers.ts';
test('connected Checkout hosts and strict isolation coexist',()=>{
 const demo=frontendCsp(false),connected=frontendCsp(true);assert.ok(!demo.includes('razorpay.com'));
 for(const directive of ["frame-ancestors 'none'","object-src 'none'","base-uri 'self'","form-action 'self'"])assert.ok(connected.includes(directive));
 assert.match(connected,/script-src[^;]*https:\/\/checkout\.razorpay\.com/);assert.match(connected,/frame-src[^;]*https:\/\/api\.razorpay\.com/);
 assert.ok(!connected.includes('*'));assert.ok(!connected.includes('unsafe-eval'));
});
