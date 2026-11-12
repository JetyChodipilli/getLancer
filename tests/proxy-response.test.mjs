import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import test from 'node:test';

// Load the actual handler with only its environment-origin lookup substituted.
const source=stripTypeScriptTypes(readFileSync(new URL('../app/api/v1/[...path]/route.ts',import.meta.url),'utf8').replace("import { backendOrigin } from '@/lib/server';","const backendOrigin = async () => 'http://backend.test';"),{mode:'transform'});
const {GET}=await import('data:text/javascript;base64,'+Buffer.from(source).toString('base64'));

test('catalogue proxy preserves server timing while retaining its response header boundary',async t=>{
 const original=globalThis.fetch;
 t.after(()=>{globalThis.fetch=original;});
 globalThis.fetch=async(url,init)=>{
  assert.equal(url,'http://backend.test/api/v1/components?page=0&size=20');
  assert.equal(init.redirect,'manual');
  return Response.json({items:[],totalItems:10000},{headers:{'Server-Timing':'app;dur=12.345','X-Request-ID':'support-id','Retry-After':'2','X-Internal-Host':'private-host','Access-Control-Allow-Origin':'*','Cache-Control':'public,max-age=3600'}});
 };
 const response=await GET(new Request('https://app.test/api/v1/components?page=0&size=20'),{params:Promise.resolve({path:['components']})});
 assert.equal(response.status,200);
 assert.equal(response.headers.get('server-timing'),'app;dur=12.345');
 assert.equal(response.headers.get('x-request-id'),'support-id');
 assert.equal(response.headers.get('retry-after'),'2');
 assert.equal(response.headers.get('cache-control'),'no-store');
 assert.equal(response.headers.get('x-content-type-options'),'nosniff');
 assert.equal(response.headers.has('x-internal-host'),false);
 assert.equal(response.headers.has('access-control-allow-origin'),false);
 assert.deepEqual(await response.json(),{items:[],totalItems:10000});
});

test('proxy forwards the expected-account assertion without forwarding arbitrary client authority headers',async t=>{
 const original=globalThis.fetch;t.after(()=>{globalThis.fetch=original;});
 globalThis.fetch=async(_url,init)=>{assert.equal(init.headers.get('X-GetLancer-Account'),'00000000-0000-4000-8000-000000000001');assert.equal(init.headers.get('Cookie'),'gl_session=dummy');assert.equal(init.headers.has('X-User-ID'),false);assert.equal(init.headers.has('Authorization'),false);return Response.json({items:[]});};
 const response=await GET(new Request('https://app.test/api/v1/me/saved-products',{headers:{Cookie:'gl_session=dummy','X-GetLancer-Account':'00000000-0000-4000-8000-000000000001','X-User-ID':'forged','Authorization':'forged'}}),{params:Promise.resolve({path:['me','saved-products']})});
 assert.equal(response.status,200);
});

test('proxy preserves a present empty account assertion for backend rejection',async t=>{
 const original=globalThis.fetch;t.after(()=>{globalThis.fetch=original;});
 globalThis.fetch=async(_url,init)=>{
  assert.equal(init.headers.has('X-GetLancer-Account'),true);
  assert.equal(init.headers.get('X-GetLancer-Account'),'');
  return Response.json({error:{code:'UNAUTHENTICATED',message:'Account assertion rejected.'}},{status:401});
 };
 for(const value of ['', '   ']){
  const response=await GET(new Request('https://app.test/api/v1/me/saved-products',{headers:{Cookie:'gl_session=dummy','X-GetLancer-Account':value}}),{params:Promise.resolve({path:['me','saved-products']})});
  assert.equal(response.status,401);
 }
});
