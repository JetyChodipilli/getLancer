import {test} from 'node:test';
import assert from 'node:assert/strict';
import {recordPage} from '../lib/record-page.ts';

test('verified first pages retain terminal state and actual page size',()=>{
 assert.deepEqual(recordPage({items:[],page:0,size:50,hasMore:false,totalItems:0}),{items:[],page:0,size:50,hasMore:false});
 assert.deepEqual(recordPage({items:[{id:'first'}],page:0,size:12,hasMore:true}),{items:[{id:'first'}],page:0,size:12,hasMore:true});
});
test('missing, malformed or later metadata cannot masquerade as a successful first page',()=>{
 for(const value of [null,[],{}, {items:[],hasMore:true},{items:[],page:1,size:50,hasMore:true},{items:[],page:0,size:0,hasMore:false},{items:[],page:0,size:1.5,hasMore:false},{items:[],page:0,size:50,hasMore:'false'},{items:null,page:0,size:50,hasMore:false}])assert.throws(()=>recordPage(value),/could not be verified/);
});
