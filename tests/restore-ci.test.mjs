import test from 'node:test';
import {restoreFixture,expectedRestoreSnapshot} from '../scripts/acceptance-restore.mjs';
import assert from 'node:assert/strict';
import {mkdtempSync,mkdirSync,copyFileSync,writeFileSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {spawnSync} from 'node:child_process';

function rehearse(t,scenario='valid',count=4){
 const root=mkdtempSync(join(tmpdir(),'restore-ci-test-'));
 t.after(()=>rmSync(root,{recursive:true,force:true}));
 mkdirSync(join(root,'scripts'));mkdirSync(join(root,'bin'));
 for(const name of ['verify-restore-ci.mjs','local-config.mjs','acceptance-restore.mjs'])copyFileSync(new URL('../scripts/'+name,import.meta.url),join(root,'scripts',name));
 writeFileSync(join(root,'.env'),'ADMIN_EMAIL=ci-admin@example.test\n');
 // Exercise the real encryption and restore command flow against a disposable
 // command fixture; real PostgreSQL recovery remains a separate CI acceptance.
 writeFileSync(join(root,'bin','docker'),`#!/usr/bin/env node
const fs=require('node:fs'),assert=require('node:assert/strict');
const args=process.argv.slice(2),database=args[args.indexOf('-d')+1],sql=args.at(-1);
const archive=Buffer.from('synthetic CI archive'.repeat(100));
if(args.includes('pg_dump'))process.stdout.write(archive);
else if(args.includes('pg_restore'))assert.deepEqual(fs.readFileSync(0),archive);
else if(args.includes('psql')){
 let value='1',restored=database==='getlancer_restore_ci',scenario=process.env.RESTORE_CASE;
 if(sql.startsWith("SELECT jsonb_build_object('schema',1,")){
  const snapshot=JSON.parse(process.env.RESTORE_DOMAIN_SNAPSHOT);
  if(restored&&scenario==='domain-package')snapshot.component.source.archiveBase64='Y29ycnVwdA==';
  if(restored&&scenario==='domain-purchase')snapshot.purchase.versionId='00000000-0000-0000-0000-000000000000';
  if(restored&&scenario==='domain-fence')snapshot.lab.outbox[1].generation=1;
  if(restored&&scenario==='domain-withdrawal')snapshot.publicComponent=1;
  value=JSON.stringify(snapshot);
 }else if(sql.includes('pg_database'))value='0';
 else if(sql.startsWith('SELECT (SELECT count(*)'))value=restored&&scenario==='rows'?'7|8|8':'7|9|8';
 else if(sql.includes('current_status=')||sql.includes('moderation_status=')||sql.includes('approval_status=')){
  value=process.env.RESTORE_OUTCOMES;
  if(!restored&&scenario==='empty')value='0';
  if(restored&&((scenario==='inquiry'&&sql.includes('current_status='))||(scenario==='review'&&sql.includes('moderation_status='))||(scenario==='suspension'&&sql.includes('approval_status='))))value=String(Number(value)-1);
 }else if(restored&&scenario==='admin'&&sql.includes('user_roles'))value='2';
 process.stdout.write(value);
}else assert.ok(args.includes('createdb')||args.includes('dropdb'));
`,{mode:0o700});
 return spawnSync(process.execPath,[join(root,'scripts','verify-restore-ci.mjs')],{
  cwd:root,encoding:'utf8',timeout:20000,
  env:{...process.env,APP_ENV:'local',CI:'true',COMPOSE_PROJECT_NAME:'getlancer-ci-123',PATH:join(root,'bin')+':'+process.env.PATH,RESTORE_CASE:scenario,RESTORE_OUTCOMES:String(count),RESTORE_DOMAIN_SNAPSHOT:JSON.stringify(expectedRestoreSnapshot(restoreFixture()))},
 });
}

test('encrypted CI restore retains both original and multi-viewport outcome counts',t=>{
 for(const count of [1,4]){
  const result=rehearse(t,'valid',count);
  assert.equal(result.status,0,result.stderr);assert.match(result.stdout,/encrypted database restore passed/);
 }
});
test('encrypted CI restore rejects missing or changed outcomes despite matching total rows',t=>{
 for(const scenario of ['empty','inquiry','review','suspension','rows','admin','domain-package','domain-purchase','domain-fence','domain-withdrawal']){
  const result=rehearse(t,scenario);
  assert.notEqual(result.status,0,scenario+' must fail recovery acceptance');
  assert.match(result.stderr,/AssertionError/);
 }
});
