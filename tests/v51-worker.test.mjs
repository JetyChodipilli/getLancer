import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {readFileSync} from 'node:fs';
import worker from './helpers/built-worker.mjs';

const material=JSON.parse(readFileSync(new URL('../app/components/frameworks/material.json',import.meta.url)));
const env={DEMO_MODE:'true',BACKEND_URL:'',ASSETS:{fetch:()=>new Response('Not found',{status:404})}};
const hash=value=>"'sha256-"+createHash('sha256').update(value).digest('base64')+"'";
test('production Worker serves only reviewed framework bytes under their exact opaque-origin hash CSP',async()=>{
 for(const item of material.items){
  const response=await worker.fetch(new Request('https://preview.example/components/frameworks/preview/'+item.id,{headers:{'Content-Security-Policy':"script-src 'unsafe-inline'",'Accept':'text/html'}}),env);
  assert.equal(response.status,200);assert.equal(response.headers.get('Content-Security-Policy'),item.policy);
  assert.equal(response.headers.get('Cache-Control'),'no-store');assert.equal(response.headers.get('X-Content-Type-Options'),'nosniff');
  const html=await response.text();assert.equal(html,item.html);
  const script=/<script>([\s\S]*?)<\/script>/.exec(html),style=/<style>([\s\S]*?)<\/style>/.exec(html);
  assert(script&&style);assert.equal(hash(script[1]),item.scriptHash);assert.equal(hash(style[1]),item.styleHash);
  assert(!/<script\b[^>]*\bnonce\s*=/.test(html));assert(!item.policy.includes('unsafe-'));assert(!item.policy.includes('allow-same-origin'));
 }
});
test('unknown or encoded preview destinations cannot acquire the native preview CSP',async()=>{
 for(const path of ['/components/frameworks/preview/unknown','/components/frameworks/preview/%76ue','/api/v1/unknown']){
  const response=await worker.fetch(new Request('https://preview.example'+path,{headers:{'Content-Security-Policy':material.items[0].policy}}),env);
  assert(!material.items.some(item=>item.policy===response.headers.get('Content-Security-Policy')));
 }
});
