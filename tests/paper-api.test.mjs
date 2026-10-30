import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import test from 'node:test';

const source=stripTypeScriptTypes(readFileSync(new URL('../lib/api.ts',import.meta.url),'utf8'),{mode:'transform'});
const {api,ApiError}=await import('data:text/javascript;base64,'+Buffer.from(source).toString('base64'));

test('API failures give safe, actionable feedback without retrying writes',async t=>{
 const original=globalThis.fetch;
 t.after(()=>{globalThis.fetch=original;});
 await t.test('a disconnected mutation has an unknown outcome and is attempted once',async()=>{
  let attempts=0;
  globalThis.fetch=async()=>{attempts++;throw new TypeError('private transport diagnostics');};
  await assert.rejects(api('/inquiries',{method:'POST',body:'{}'}),error=>{
   assert.ok(error instanceof ApiError);assert.equal(error.code,'NETWORK_ERROR');assert.equal(error.status,0);
   assert.match(error.message,/could not confirm/i);assert.match(error.message,/before retrying/i);
   assert.doesNotMatch(error.message,/private transport/);return true;
  });
  assert.equal(attempts,1);
 });
 await t.test('abort remains cancellation rather than a user-facing server failure',async()=>{
  const controller=new AbortController();controller.abort();
  globalThis.fetch=async()=>{throw controller.signal.reason;};
  await assert.rejects(api('/me',{signal:controller.signal}),error=>error.name==='AbortError');
 });
 await t.test('JSON primitives and null cannot confirm a successful record',async()=>{
  for(const value of [null,true,12,'ok']){
   globalThis.fetch=async()=>Response.json(value);
   await assert.rejects(api('/developer/profile',{method:'PUT',body:'{}'}),error=>error instanceof ApiError&&error.code==='INVALID_RESPONSE');
  }
 });
 await t.test('unstructured rate and permission responses remain actionable and preserve references',async()=>{
  globalThis.fetch=async()=>new Response('upstream html',{status:429,headers:{'x-request-id':'req-rate-42'}});
  await assert.rejects(api('/inquiries'),error=>{
   assert.equal(error.status,429);assert.equal(error.code,'REQUEST_FAILED');assert.equal(error.requestId,'req-rate-42');
   assert.match(error.message,/wait/i);return true;
  });
  globalThis.fetch=async()=>Response.json(null,{status:403});
  await assert.rejects(api('/admin/reports'),error=>error.status===403&&/permission/i.test(error.message));
 });
 await t.test('typed provider uncertainty retains the safe reconciliation instruction',async()=>{
  globalThis.fetch=async()=>Response.json({error:{code:'PAYMENT_ORDER_UNKNOWN',message:'Order creation could not be confirmed. Do not pay again; reconcile the receipt.'}},{status:502});
  await assert.rejects(api('/milestones/test/payment-order',{method:'POST'}),error=>error.code==='PAYMENT_ORDER_UNKNOWN'&&/Do not pay again/.test(error.message));
 });
 await t.test('server failures never display private diagnostic text',async()=>{
  globalThis.fetch=async()=>Response.json({error:{code:'INTERNAL_ERROR',message:'SQL password example-secret',requestId:'req-server-42',fieldErrors:{secret:'SQL password example-secret'}}},{status:500});
  await assert.rejects(api('/me'),error=>{
   assert.equal(error.requestId,'req-server-42');assert.equal(error.code,'INTERNAL_ERROR');
   assert.doesNotMatch(error.message,/SQL|example-secret/);assert.deepEqual(error.fieldErrors,{});return true;
  });
 });
});
