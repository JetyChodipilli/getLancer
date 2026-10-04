import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {readFileSync} from 'node:fs';
import {componentSeeds,seedCatalog,componentZip} from '../lib/components.ts';
test('six original working components cover every required frontend category with verifiable free source',()=>{
 assert.equal(componentSeeds.length,6);assert.deepEqual(new Set(componentSeeds.map(c=>c.category)),new Set(['NAVBAR','SIDEBAR','FORM','CARD','AUTH','DASHBOARD']));
 assert.equal(new Set(componentSeeds.map(c=>c.slug)).size,6);
 for(const c of componentSeeds){assert.equal(c.license,'MIT');assert.equal(c.executionMode,'Browser local');assert.equal(c.version,'1.0.0');assert.equal(c.sha256,createHash('sha256').update(c.files['index.html']).digest('hex'));assert.match(c.files.LICENSE,/Permission is hereby granted, free of charge/);assert.match(c.files['README.md'],/No packages, installation, API keys or external assets/);assert.match(c.files['index.html'],/<script>[^]*addEventListener/);assert.match(c.files['index.html'],/connect-src 'none'/);assert.match(c.files['index.html'],/form-action 'none'/);assert.doesNotMatch(c.files['index.html'],/fetch\(|XMLHttpRequest|https?:\/\//);}
});
test('catalogue filters and backend boundary return real results without imaginary runtimes',()=>{
 assert.equal(seedCatalog('password').items.length,1);assert.equal(seedCatalog('','AUTH').items[0].slug,'quiet-sign-in');assert.equal(seedCatalog('no-such-component').totalItems,0);assert.deepEqual(seedCatalog('','','BACKEND').items,[]);assert.deepEqual(seedCatalog('','','FRONTEND',1).items,[]);assert.equal('files' in seedCatalog().items[0],false);
});
test('source ZIPs round-trip through an independent standard-library decoder including CRC validation',()=>{
 for(const item of componentSeeds){const zip=componentZip(item.files);const output=execFileSync('python3',['-c',"import sys,io,zipfile,json; z=zipfile.ZipFile(io.BytesIO(sys.stdin.buffer.read())); assert z.testzip() is None; print(json.dumps({n:z.read(n).decode() for n in z.namelist()}))"],{input:zip,encoding:'utf8'});assert.deepEqual(JSON.parse(output),item.files);}
 assert.throws(()=>componentZip({'../wrong':'no'}),/manifest/);assert.throws(()=>componentZip({'index.html':'a'.repeat(100001),'README.md':'setup',LICENSE:'MIT'}),/limit/);
 const bytes=componentZip(componentSeeds[0].files),bad=bytes.slice();bad[50]^=1;assert.throws(()=>execFileSync('python3',['-c',"import sys,io,zipfile; z=zipfile.ZipFile(io.BytesIO(sys.stdin.buffer.read())); assert z.testzip() is None"],{input:bad,stdio:['pipe','pipe','pipe']}));
});
test('free source does not change the positive-price source-commerce or existing full-project capacity contract',()=>{
 const migration=readFileSync('backend/src/main/resources/db/migration/V24__components_college_and_publishing_slots.sql','utf8'),slots=readFileSync('backend/src/main/java/com/getlancer/components/ComponentSlotService.java','utf8');
 assert.match(migration,/CHECK\(amount_minor BETWEEN 100 AND 1000000000\)/);assert.match(migration,/UNIQUE\(owner_id,idempotency_key\)/);assert.match(migration,/component_slot_purchase_immutable/);assert.match(slots,/"free",3/);assert.match(slots,/mode='live' AND status='CAPTURED' AND refunded_minor=0/);assert.doesNotMatch(slots,/UPDATE showcase_entitlements/);
 const ui=readFileSync('app/components/component-library.tsx','utf8');assert.match(ui,/sandbox="allow-scripts allow-forms"/);assert.doesNotMatch(ui,/allow-same-origin|allow-top-navigation|allow-popups/);
});
