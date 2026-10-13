import assert from 'node:assert/strict';
import {test} from 'node:test';
import {copyFile,mkdir,mkdtemp,rm,writeFile} from 'node:fs/promises';
import {join,resolve,sep} from 'node:path';
import {pathToFileURL} from 'node:url';

// Regression: ISSUE-001 — npm start resolved a Windows file URL as C:\C:\... .
// Found by /qa on 2026-10-13.
// Report: docs/audits/2026-10-13-local-audit.md
test('built Worker loads server modules and assets from paths containing spaces and URL metacharacters',async()=>{
 const parent=resolve('.tooling');await mkdir(parent,{recursive:true});
 const root=await mkdtemp(join(parent,'worker path #% '));
 assert(root.startsWith(parent+sep));
 let runtime;
 try{
  for(const path of ['scripts','dist/server','dist/client'])await mkdir(join(root,path),{recursive:true});
  await copyFile(new URL('../scripts/worker-runtime.mjs',import.meta.url),join(root,'scripts/worker-runtime.mjs'));
  await writeFile(join(root,'dist/server/index.js'),'export default {fetch(request,env){return env.ASSETS.fetch(request)}};');
  await writeFile(join(root,'dist/client/probe.txt'),'portable native worker assets');
  const {createBuiltWorker}=await import(pathToFileURL(join(root,'scripts/worker-runtime.mjs')).href);
  runtime=createBuiltWorker();await runtime.ready;
  const response=await runtime.dispatchFetch('http://localhost/probe.txt');
  assert.equal(response.status,200);assert.equal(await response.text(),'portable native worker assets');
 }finally{
  await runtime?.dispose();
  if(root.startsWith(parent+sep))await rm(root,{recursive:true,force:true});
 }
});
