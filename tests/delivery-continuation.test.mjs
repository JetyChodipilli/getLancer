import test from 'node:test';
import assert from 'node:assert/strict';
import {createDeliveryPreview,engagementPage,mergeEngagements} from '../lib/delivery.ts';

const row=n=>({id:`delivery-${n}`,title:`Delivery ${n}`,status:'OPEN',side:'SELLER',createdAt:'2026-01-01T00:00:00Z'});

test('delivery page parsing preserves existing arrays and items while requiring valid bounded metadata',()=>{
 const items=[row(0)];
 assert.deepEqual(engagementPage(items),{items,page:0,hasMore:false});
 assert.deepEqual(engagementPage({items}),{items,page:0,hasMore:false});
 assert.deepEqual(engagementPage({items,page:3,size:50,hasMore:true}),{items,page:3,size:50,hasMore:true});
 for(const value of [null,{},{items:null},{items:[null]},{items:[{}]},{items,page:-1,size:50,hasMore:true},{items,page:1.5,size:50,hasMore:true},{items,page:1,size:101,hasMore:true},{items,page:1,size:0,hasMore:true},{items,page:1,size:50,hasMore:'true'}]){
  assert.throws(()=>engagementPage(value),/could not be verified/);
 }
 assert.throws(()=>engagementPage({items:[row(0),row(1)],page:1,size:1,hasMore:true}),/exceeded its bound/);
});

test('an independently selected old delivery stays reachable and occurs once after all bounded pages arrive',()=>{
 const all=Array.from({length:206},(_,n)=>row(n)),old=all.at(-1);
 let items=mergeEngagements(all.slice(0,50),[old]);
 assert.equal(items.length,51);assert.equal(items.at(-1).id,old.id);
 for(let page=1;page<5;page++)items=mergeEngagements(items,all.slice(page*50,(page+1)*50));
 assert.equal(items.length,206);assert.equal(items.filter(item=>item.id===old.id).length,1);
 const updated={...old,status:'ACTIVE'};items=mergeEngagements(items,[updated]);
 assert.equal(items.find(item=>item.id===old.id).status,'ACTIVE');
 assert.equal(all.at(-1).status,'OPEN','merging never changes another stored snapshot');
});

test('the existing local delivery preview remains an isolated array collection',()=>{
 const preview=createDeliveryPreview(),other=createDeliveryPreview();
 const first=engagementPage(preview.list());assert.equal(first.hasMore,false);assert.equal(first.items.length,1);
 const created=preview.create({inquiryId:'preview-inquiry'});assert.equal(preview.detail(created.id).id,created.id);
 assert.equal(engagementPage(preview.list()).items.length,2);assert.equal(other.list().length,1);
});
