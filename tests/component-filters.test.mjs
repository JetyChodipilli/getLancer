import {test} from 'node:test';
import assert from 'node:assert/strict';
import {componentSeeds,parseComponentFilters,componentFilterParams,filterComponents,seedCatalog} from '../lib/components.ts';
const parse = value => parseComponentFilters(new URLSearchParams(value));
const combined = {language:'JavaScript',framework:'VANILLA',license:'MIT',mode:'Browser local'};

test('all component filters combine before pagination and preserve the free source boundary',()=>{
 const first=seedCatalog('','AUTH','FRONTEND',0,combined);
 assert.equal(first.totalItems,4);assert.equal(first.totalPages,1);assert.equal(first.size,12);assert.equal(first.hasMore,false);
 assert.deepEqual(first.items.map(item=>item.slug),componentSeeds.filter(item=>item.category==='AUTH').map(item=>item.slug));
 assert.equal(seedCatalog('password','AUTH','FRONTEND',0,combined).items[0].slug,'quiet-sign-in');
 for(const item of first.items){assert.equal(item.license,'MIT');assert.equal(item.executionMode,'Browser local');assert.equal('files' in item,false);}
 for(const unsupported of [{language:'Java'},{framework:'React'},{license:'Apache-2.0'},{mode:'Source only'}])assert.equal(seedCatalog('','AUTH','FRONTEND',0,{...combined,...unsupported}).totalItems,0);
});

test('legacy JS and JavaScript metadata share language and plain browser framework identities',()=>{
 for(const language of ['HTML','CSS','JavaScript','javascript','JS']){
  for(const framework of ['VANILLA','vanilla','HTML / CSS / JS','HTML / CSS / JavaScript']){
   const filters=parse({language,framework,license:'mit',mode:'browser local'});
   assert.equal(filterComponents(componentSeeds,filters).length,24);
   assert.equal(filters.framework,'VANILLA');assert.equal(filters.license,'MIT');assert.equal(filters.mode,'Browser local');
  }
 }
 assert.equal(parse({language:' js '}).language,'JavaScript');
});

test('matching is an intersection of independent metadata fields and exact language tokens',()=>{
 const base={...componentSeeds[0],builderSlug:'public-builder',language:'TypeScript',framework:'React',license:'Apache-2.0',executionMode:'Source only'};
 const candidates=[base,{...base,slug:'other-language',language:'JavaScript'},{...base,slug:'other-framework',framework:'Vue'},{...base,slug:'other-license',license:'MIT'},{...base,slug:'other-mode',executionMode:'Browser local'},{...base,slug:'other-builder',builderSlug:'another-builder'}];
 const filters=parse({q:'studio',category:'NAVBAR',kind:'FRONTEND',builder:'public-builder',language:'TypeScript',framework:'React',license:'Apache-2.0',mode:'Source only'});
 assert.deepEqual(filterComponents(candidates,filters).map(item=>item.slug),[base.slug]);
 assert.equal(filterComponents(candidates,{...filters,language:'Script'}).length,0);
 assert.equal(filterComponents(candidates,{...filters,q:'missing'}).length,0);
 assert.equal(filterComponents(candidates,{...filters,category:'AUTH'}).length,0);
});

test('first, next and out-of-range pages have stable disjoint results under combined filters',()=>{
 const first=seedCatalog('','','FRONTEND',0,combined),next=seedCatalog('','','FRONTEND',1,combined),empty=seedCatalog('','','FRONTEND',2,combined);
 assert.equal(first.hasMore,true);assert.equal(next.hasMore,false);assert.equal(empty.hasMore,false);
 assert.equal(first.totalItems,24);assert.equal(next.totalItems,24);assert.equal(empty.totalItems,24);
 assert.equal(empty.page,2);assert.equal(empty.totalPages,2);assert.deepEqual(empty.items,[]);
 assert.deepEqual([...first.items,...next.items].map(item=>item.slug),componentSeeds.map(item=>item.slug));
 assert.equal(new Set([...first.items,...next.items].map(item=>item.slug)).size,24);
});

test('URL round trips retain every filter and builder while next-page and lane changes reset deliberately',()=>{
 const filters=parse({q:'sign in + / &',category:'AUTH',kind:'FRONTEND',builder:'builder-one',...combined,page:'1',ignored:'drop-me'});
 const params=componentFilterParams(filters);
 assert.deepEqual(parse(params),filters);assert.equal(params.has('ignored'),false);
 const next=parse(componentFilterParams(filters,{page:2}));assert.deepEqual(next,{...filters,page:2});
 const backend=parse(componentFilterParams(filters,{kind:'BACKEND',page:0}));assert.deepEqual(backend,{...filters,kind:'BACKEND',page:0});
 const back=parse(componentFilterParams(backend,{kind:'FRONTEND',page:0}));assert.deepEqual(back,{...filters,page:0});
 const reset=parse(componentFilterParams(filters,{q:'',category:'',language:'',framework:'',license:'',mode:'',page:0}));
 assert.equal(reset.builder,'builder-one');assert.equal(reset.kind,'FRONTEND');assert.equal(componentFilterParams(reset).toString(),'builder=builder-one');
});

test('malformed pages, oversized filters, controls and unknown categories/types fail validation',()=>{
 for(const page of ['-1','1.5','NaN','Infinity','10001','1e2','1x',' 1'])assert.throws(()=>parse({page}),/valid component filters/);
 for(const key of ['category','kind','builder','language','framework','license','mode'])assert.throws(()=>parse({[key]:'x'.repeat(101)}),/valid component filters/);
 assert.throws(()=>parse({q:'x'.repeat(201)}),/valid component filters/);
 for(const key of ['q','category','kind','builder','language','framework','license','mode'])assert.throws(()=>parse({[key]:'bad\nvalue'}),/valid component filters/);
 assert.throws(()=>parse({kind:'HOSTED'}),/valid component filters/);assert.throws(()=>parse({category:'NOT_A_CATEGORY'}),/valid component filters/);
 for(const page of [-1,0.5,NaN,Infinity,10001])assert.throws(()=>seedCatalog('','','FRONTEND',page),/valid component filters/);
 assert.equal(parse({page:'10000'}).page,10000);assert.equal(parse({q:'x'.repeat(200)}).q.length,200);
});

test('builder and backend queries stay honest in the standalone catalogue',()=>{
 const builder=seedCatalog('','','FRONTEND',2,{...combined,builder:'connected-builder'});
 assert.deepEqual(builder,{items:[],totalItems:0,page:2,size:12,totalPages:0,hasMore:false});
 const backend=seedCatalog('','','BACKEND',0,{...combined,language:'Java'});
 assert.equal(backend.totalItems,0);assert.deepEqual(backend.items,[]);
 assert.equal(seedCatalog('%').totalItems,0);assert.equal(seedCatalog('_').totalItems,0);
});
