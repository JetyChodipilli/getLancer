import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import test from 'node:test';

const source=stripTypeScriptTypes(readFileSync(new URL('../lib/api.ts',import.meta.url),'utf8'),{mode:'transform'});
const {api,ApiError}=await import('data:text/javascript;base64,'+Buffer.from(source).toString('base64'));

test('API responses preserve request identity, authority errors and confirmed outcomes',async t=>{
 const original=globalThis.fetch;
 t.after(()=>{globalThis.fetch=original;});
 await t.test('Headers objects and tuple headers retain idempotency keys',async()=>{
  for(const headers of [new Headers({'Idempotency-Key':'retained-request'}),[['Idempotency-Key','retained-request']]]){
   globalThis.fetch=async(url,init)=>{
    assert.equal(url,'/api/v1/me/example');
    assert.equal(init.credentials,'same-origin');
    assert.equal(init.headers.get('Idempotency-Key'),'retained-request');
    assert.equal(init.headers.get('X-Requested-With'),'getlancer');
    assert.equal(init.headers.get('Content-Type'),'application/json');
    return Response.json({id:'confirmed'});
   };
   assert.deepEqual(await api('/me/example',{method:'POST',headers,body:'{}'}),{id:'confirmed'});
  }
 });
 await t.test('multipart uploads leave boundary generation to fetch',async()=>{
  const body=new FormData();body.set('file',new File(['source'],'component.zip'));
  globalThis.fetch=async(_url,init)=>{
   assert.equal(init.body,body);assert.equal(init.headers.has('Content-Type'),false);
   return Response.json({id:'uploaded'});
  };
  assert.deepEqual(await api('/me/components/id/source',{method:'POST',body}),{id:'uploaded'});
 });
 await t.test('invalid successful JSON cannot confirm a saved record',async()=>{
  globalThis.fetch=async()=>new Response('<html>Upstream unavailable</html>',{status:200});
  await assert.rejects(api('/me/example'),error=>error instanceof ApiError&&error.code==='INVALID_RESPONSE'&&error.status===502);
 });
 await t.test('empty successful mutation responses remain supported',async()=>{
  for(const status of [204,205]){
   globalThis.fetch=async()=>new Response(null,{status});
   assert.deepEqual(await api('/auth/logout',{method:'POST'}),{});
  }
 });
 await t.test('malformed, null and unstructured denials retain their HTTP status',async()=>{
  for(const status of [401,403])for(const body of ['null','not-json','[]','{"error":null}']){
   globalThis.fetch=async()=>new Response(body,{status});
   await assert.rejects(api('/me/example'),error=>error instanceof ApiError&&error.status===status&&error.code==='REQUEST_FAILED');
  }
 });
 await t.test('server validation details survive while invalid field values are discarded',async()=>{
  globalThis.fetch=async()=>Response.json({error:{code:'INVALID_COMPONENT_ARCHIVE',message:'Choose an individual component ZIP.',requestId:'support-reference',fieldErrors:{file:'Include the three root files.',unexpected:{nested:true}}}},{status:400});
  await assert.rejects(api('/me/example'),error=>{
   assert.equal(error.status,400);assert.equal(error.code,'INVALID_COMPONENT_ARCHIVE');
   assert.equal(error.requestId,'support-reference');assert.deepEqual(error.fieldErrors,{file:'Include the three root files.'});
   return error instanceof ApiError;
  });
 });
});
