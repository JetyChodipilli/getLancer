import assert from 'node:assert/strict';
import {test} from 'node:test';
import {readFile} from 'node:fs/promises';
import {archiveFor,sourceFiles,sha,unpackArchive} from '../scripts/build-data-ai-material.mjs';

// Regression: ISSUE-003 — Windows gzip metadata changed the reviewed archive identity.
// Found by /qa on 2026-10-13. Report: docs/audits/2026-10-13-local-audit.md
test('the analytics source download reproduces the existing reviewed archive bytes on every platform',async()=>{
 const manifest=JSON.parse(await readFile(new URL('../labs/data-ai/evidence-manifest.json',import.meta.url),'utf8'));
 const {files}=await sourceFiles(),archive=archiveFor(files);
 assert.equal(sha(archive),manifest.archiveSha256);
 assert.deepEqual([...unpackArchive(archive,files)],files);
});
