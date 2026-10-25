import test,{after} from 'node:test';
import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';
import {createServer} from 'vite';

const root=fileURLToPath(new URL('..',import.meta.url));
const vite=await createServer({appType:'custom',configFile:false,root,cacheDir:'node_modules/.vite-test-trust',resolve:{alias:{'@':root}},server:{middlewareMode:true,hmr:false}});
after(()=>vite.close());
const {appendTrustRecords,trustCollectionPage}=await vite.ssrLoadModule('/app/components/trust-workspace.tsx');

test('continuation retains old projects and accepts updated overlapping records without duplicate actions',()=>{
 const initial=Array.from({length:100},(_,n)=>({id:'project-'+n,title:'Project '+n,verification_status:null}));
 const next=[{id:'project-99',title:'Current project 99',verification_status:'PENDING',challenge:'owner-only-proof'},...Array.from({length:6},(_,n)=>({id:'project-'+(100+n),title:'Older project '+(100+n)}))];
 const result=appendTrustRecords(initial,next,'id');
 assert.equal(result.length,106);assert.equal(result[99].challenge,'owner-only-proof');assert.equal(result[105].id,'project-105');
 assert.equal(initial[99].verification_status,null);assert.equal(initial.length,100);
});

test('verified evidence and awards retain their existing record keys across overlapping pages',()=>{
 for(const [key,record] of [['product_id',{product_id:'old-proof',repository_url:'https://github.com/example/proof'}],['inquiry_id',{inquiry_id:'old-award',reason:'Reviewed completion'}]]){
  const updated={...record,reason:'Current review reason'};
  const result=appendTrustRecords([record],[updated,{[key]:'older-record'}],key);
  assert.equal(result.length,2);assert.deepEqual(result[0],updated);assert.equal(result[1][key],'older-record');
 }
});

test('missing metadata, the wrong cursor and malformed collection records cannot advance continuation',()=>{
 const good={verified:[{product_id:'old-proof'}],pages:{verified:{page:1,size:50,hasMore:false}}};
 assert.deepEqual(trustCollectionPage(good,'verified',1),good.pages.verified);
 for(const response of [null,{}, {...good,pages:{}}, {...good,pages:{verified:{page:0,size:50,hasMore:false}}}, {...good,pages:{verified:{page:1,size:0,hasMore:false}}}, {...good,pages:{verified:{page:1,size:101,hasMore:false}}}, {...good,pages:{verified:{page:1,size:50}}}, {...good,verified:{}}, {...good,verified:[{id:'wrong-key'}]}, {...good,verified:Array.from({length:51},(_,n)=>({product_id:'proof-'+n}))}])
  assert.throws(()=>trustCollectionPage(response,'verified',1),/could not be verified/);
 assert.throws(()=>trustCollectionPage(good,'verifications',1),/could not be verified/);
});
