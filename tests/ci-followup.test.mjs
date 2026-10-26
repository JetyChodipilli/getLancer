import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {catalogue} from '../scripts/build-framework-material.mjs';

test('downloaded Angular source and AOT compiler use the patched coordinated release',async()=>{
 const pkg=JSON.parse(await readFile('examples/frameworks/angular/package.json','utf8'));
 const lock=JSON.parse(await readFile('examples/frameworks/angular/package-lock.json','utf8'));
 const item=catalogue.find(item=>item.id==='angular');
 // GHSA-jfgx-762w-99vr: Angular 20 is fixed from 20.3.34. Upgrade majors deliberately.
 assert.match(item.version,/^20\.\d+\.\d+$/);
 const [,minor,patch]=item.version.split('.').map(Number);
 assert(minor>3||(minor===3&&patch>=34),'Angular 20 source regressed below the XSRF patch');
 for(const [name,version] of Object.entries({...pkg.dependencies,...pkg.devDependencies}).filter(([name])=>name.startsWith('@angular/'))){
  assert.equal(version,item.version,name+' coordinated version');
  assert.equal(lock.packages['node_modules/'+name].version,version,name+' locked version');
 }
 assert(item.compiler.includes(item.version));
 const index=JSON.parse(await readFile('app/components/frameworks/index.json','utf8'));
 assert.equal(index.items.find(item=>item.id==='angular').version,item.version);
 const notices=await readFile('examples/frameworks/angular/THIRD_PARTY_NOTICES.md','utf8');
 const licenses=await readFile('examples/frameworks/angular/THIRD_PARTY_LICENSES.md','utf8');
 for(const name of ['core','common','platform-browser']){
  assert(notices.includes('@angular/'+name+' '+item.version+': MIT.'));
  assert(licenses.includes('## @angular/'+name+' '+item.version+' — MIT'));
 }
});

test('native source package advisories fail CI at moderate severity',async()=>{
 const workflow=await readFile('.github/workflows/ci.yml','utf8');
 assert.match(workflow,/for framework in vue svelte angular; do npm audit --prefix examples\/frameworks\/\$framework --audit-level=moderate; done/);
 assert(!workflow.includes('pull_request_target'),'Untrusted PR code must not receive the privileged target workflow context');
 assert.match(workflow,/Block high and critical semantic security findings/);
 assert.match(workflow,/Require both secret scans to pass/);
 assert.match(workflow,/Block high and critical runtime image vulnerabilities/);
});

test('root build tools retain reviewed security floors and a full dependency audit',async()=>{
 const lock=JSON.parse(await readFile('package-lock.json','utf8'));
 const floors={vite:[8,3,5],undici:[7,30,1],'fast-uri':[3,1,9],zod:[4,6,6]};
 for(const [name,floor] of Object.entries(floors)){
  const entries=Object.entries(lock.packages).filter(([path])=>path.endsWith('/'+name));
  assert(entries.length>0,name+' remains represented in the lock');
  for(const [path,item] of entries){
   const version=item.version.split('.').map(Number);
   assert.equal(version[0],floor[0],path+' major changes need compatibility review');
   assert(version[1]>floor[1]||(version[1]===floor[1]&&version[2]>=floor[2]),path+' regressed below a reviewed patch');
   assert(item.resolved.startsWith('https://registry.npmjs.org/'),path+' registry binding');
   assert.match(item.integrity,/^sha512-/);
  }
 }
 const workflow=await readFile('.github/workflows/ci.yml','utf8');
 assert(workflow.includes('npm audit --audit-level=moderate --json > dependency-evidence/npm-full-audit.json'));
});
