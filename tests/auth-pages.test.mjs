import assert from 'node:assert/strict';
import test from 'node:test';
import worker from '../dist/server/index.js';

const env={ASSETS:{fetch:async()=>new Response('Not found',{status:404})}};
const ctx={waitUntil(){},passThroughOnException(){}};
async function page(path){const r=await worker.fetch(new Request('https://preview.example'+path,{headers:{accept:'text/html'}}),env,ctx);return {status:r.status,html:await r.text()};}
test('login renders Icy Wind and password-manager fields without exposing the admin step',async()=>{
 const {status,html}=await page('/login');assert.equal(status,200);assert.match(html,/icy-arrow.png/);assert.match(html,/Continue with GitHub/);assert.match(html,/autocomplete="current-password"/i);assert.match(html,/Continue with Google/);assert.match(html,/Forgot password/);assert.doesNotMatch(html,/<input[^>]+(?:name="totp"|id="auth-totp")/);assert.match(html,/Keep me signed in/);assert.match(html,/<a href="\/signup">Create account<\/a>/);assert.doesNotMatch(html,/icy-motion|Motion preview|Reduced motion/);assert.match(html,/data-running="true"/);
});
test('signup explains consent and uses the new-password autocomplete hint',async()=>{
 const {status,html}=await page('/signup');assert.equal(status,200);assert.match(html,/autocomplete="new-password"/i);assert.match(html,/name="displayName"/);assert.match(html,/By creating an account/);assert.match(html,/policies#privacy/);assert.match(html,/12 characters/);assert.match(html,/Create account/);assert.match(html,/<a href="\/login">Log in<\/a>/);
});
test('an unconfigured Google endpoint never returns a fabricated authorization URL',async()=>{
 const {status,html}=await page('/api/v1/auth/providers');assert.equal(status,503);assert.equal(JSON.parse(html).error.code,'BACKEND_NOT_CONFIGURED');assert.doesNotMatch(html,/authorizationUrl/);
});
