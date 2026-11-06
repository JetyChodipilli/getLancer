import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import test from 'node:test';
const source=stripTypeScriptTypes(readFileSync(new URL('../lib/templates.ts',import.meta.url),'utf8'),{mode:'transform'});
const {downloadTemplateSource}=await import('data:text/javascript;base64,'+Buffer.from(source).toString('base64'));

test('a source response cannot trigger a browser download after its authority is invalidated',async t=>{
 const fetch=globalThis.fetch,create=URL.createObjectURL;let current=true,finish,requested=false;
 t.after(()=>{globalThis.fetch=fetch;URL.createObjectURL=create;});
 URL.createObjectURL=()=>{throw Error('A stale source response created a browser URL.');};
 globalThis.fetch=async(_path,init)=>{requested=true;assert.equal(init.headers.get('X-GetLancer-Account'),'00000000-0000-4000-8000-000000000001');return {ok:true,headers:new Headers({'Content-Type':'application/zip'}),blob:()=>new Promise(resolve=>{finish=resolve;})};};
 const download=downloadTemplateSource('/admin/education-releases/example/package','dummy.zip','POST',{accountId:'00000000-0000-4000-8000-000000000001',isCurrent:()=>current});
 await Promise.resolve();assert.equal(requested,true);current=false;finish(new Blob(['dummy zip']));await download;
});

test('an already invalidated source inspection performs no request',async t=>{
 const fetch=globalThis.fetch;t.after(()=>{globalThis.fetch=fetch;});globalThis.fetch=()=>{throw Error('A stale inspection started a request.');};
 await downloadTemplateSource('/private/package','dummy.zip','GET',{isCurrent:()=>false});
});
