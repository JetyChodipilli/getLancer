import test from 'node:test';
import assert from 'node:assert/strict';
import {verifyStaging} from '../scripts/verify-staging.mjs';

const config={APP_BASE_URL:'https://frontend.example.test',BACKEND_URL:'https://backend.example.test'};
function service(fault){
 return async(address,options)=>{
  const url=new URL(address),path=url.pathname;
  assert.equal(options.redirect,'manual');
  if(fault==='outage'&&path.includes('readiness'))return Response.json({status:'DOWN'},{status:503});
  if(path.startsWith('/actuator/'))return Response.json({status:'UP'});
  if(path==='/api/v1/auth/providers')return Response.json({google:fault==='proxy'&&url.hostname.startsWith('frontend')});
  if(path==='/api/v1/me')return Response.json({error:{}},{status:fault==='fake-account'?200:401});
  if(path.startsWith('/preview/')){
   const name=path.split('/').pop();
   return new Response('',{status:fault==='samples'?200:307,headers:{location:fault==='external'?'https://elsewhere.example.test/workspace':'/workspace'+(name==='workspace'?'':'/'+name)}});
  }
  return Response.json({items:[]});
 };
}
test('hosted read-only acceptance checks health, proxy identity, auth, redirects and measures requests',async()=>{
 const result=await verifyStaging(config,service());
 assert.equal(result.requests,8);assert.equal(result.concurrency,2);assert.ok(result.p95Ms>=0);
});
test('hosted acceptance rejects outage, proxy mismatch, fabricated login, samples and external redirects',async()=>{
 for(const fault of ['outage','proxy','fake-account','samples','external'])await assert.rejects(()=>verifyStaging(config,service(fault)),fault);
});
test('hosted acceptance rejects HTTP and credential-bearing origins before making a request',async()=>{
 for(const APP_BASE_URL of ['http://frontend.example.test','https://user:secret@frontend.example.test','https://frontend.example.test/path']){
  let calls=0;await assert.rejects(()=>verifyStaging({...config,APP_BASE_URL},async()=>{calls++;throw Error('Should not request.')}));assert.equal(calls,0);
 }
});
