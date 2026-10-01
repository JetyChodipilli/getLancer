import {test} from 'node:test';import assert from 'node:assert/strict';import {frontendCsp,secureFrontendResponse} from '../lib/security-headers.ts';
test('connected Checkout hosts and strict isolation coexist',()=>{
 const demo=frontendCsp(false),connected=frontendCsp(true);assert.ok(!demo.includes('razorpay.com'));
 for(const directive of ["frame-ancestors 'none'","object-src 'none'","base-uri 'self'","form-action 'self'"])assert.ok(connected.includes(directive));
 assert.match(connected,/script-src[^;]*https:\/\/checkout\.razorpay\.com/);assert.match(connected,/frame-src[^;]*https:\/\/api\.razorpay\.com/);
 assert.ok(!connected.includes('*'));assert.ok(!connected.includes('unsafe-eval'));
 assert.match(demo,/frame-src[^;]*https:\/\/www\.loom\.com/);
 assert.doesNotMatch(demo,/connect-src[^;]*loom\.com|script-src[^;]*loom\.com/);
});
test('response security wraps all response types without losing cookies or private cache rules',async()=>{
 const original=new Response('protected',{status:403,headers:{'Content-Type':'application/json','Cache-Control':'no-store','Set-Cookie':'session=value; HttpOnly; Secure; SameSite=Lax'}});
 const secured=secureFrontendResponse(original,'https://example.com/_vinext/image',false);
 assert.equal(secured.status,403);assert.equal(await secured.text(),'protected');assert.equal(secured.headers.get('cache-control'),'no-store');assert.match(secured.headers.get('set-cookie'),/HttpOnly/);
 assert.equal(secured.headers.get('x-content-type-options'),'nosniff');assert.equal(secured.headers.get('referrer-policy'),'no-referrer');assert.equal(secured.headers.get('strict-transport-security'),'max-age=31536000');assert.match(secured.headers.get('content-security-policy'),/frame-ancestors 'none'/);
 assert.equal(secureFrontendResponse(new Response('local'),'http://localhost/',false).headers.get('strict-transport-security'),null);
});
