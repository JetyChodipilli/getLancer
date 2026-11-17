import assert from 'node:assert/strict';
import test from 'node:test';
import {readFileSync} from 'node:fs';
import worker from './helpers/built-worker.mjs';
const seeds=JSON.parse(readFileSync('backend/src/main/resources/catalog/education.json','utf8'));
const env={APP_ENV:'test',BACKEND_URL:'',DEMO_MODE:'true'};
const url=(slug,id)=>'https://app.example.test/college-projects/'+slug+'?release='+id;
const first=seeds[0],other=seeds[1];
test('exact historical source bookmarks retain a successful document and frozen hash',async()=>{
 const r=await worker.fetch(new Request(url(first.productSlug,first.release.id)),env);
 assert.equal(r.status,200);assert.match(await r.text(),new RegExp(first.packageAudit.sha256));
});
test('wrong-project, missing, malformed and duplicate bookmark bindings reject before streaming',async()=>{
 for(const path of [url(first.productSlug,other.release.id),url(first.productSlug,'00000000-0000-4000-8000-000000000099'),url(first.productSlug,'invalid'),url(first.productSlug,''),url('missing-project',first.release.id),url(first.productSlug,first.release.id)+'&release='+other.release.id]){
  const r=await worker.fetch(new Request(path),env);assert.equal(r.status,404,path);assert.equal(r.headers.get('cache-control'),'no-store');assert.doesNotMatch(await r.text(),/Exactly what you receive|SHA-256:/);
 }
 const head=await worker.fetch(new Request(url(first.productSlug,other.release.id),{method:'HEAD'}),env);assert.equal(head.status,404);assert.equal(await head.text(),'');
});
