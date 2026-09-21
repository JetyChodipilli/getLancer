import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync,writeFileSync,readFileSync,copyFileSync,mkdirSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {spawnSync} from 'node:child_process';
import {readEnvironment,localDatabase} from '../scripts/local-config.mjs';

test('local config preserves password punctuation and properties escapes',t=>{
  const dir=mkdtempSync(join(tmpdir(),'getlancer-env-'));t.after(()=>rmSync(dir,{recursive:true,force:true}));
  const file=join(dir,'.env');writeFileSync(file,'DB_PASSWORD=example@pass#with=equals\nCERT=C:\\\\data\\\\root.crt\n');
  assert.equal(readEnvironment(file).DB_PASSWORD,'example@pass#with=equals');
  assert.equal(readEnvironment(file).CERT,'C:\\data\\root.crt');
  writeFileSync(file,'DB_PASSWORD=first\nDB_PASSWORD=second\n');assert.throws(()=>readEnvironment(file),/Duplicate/);
});
test('local setup refuses remote hosts and preserves database name case',()=>{
  const config={APP_ENV:'local',DB_URL:'jdbc:postgresql://localhost:5432/getLancer',DB_SCHEMA:'getlancer'};
  assert.deepEqual(localDatabase(config),{host:'localhost',port:5432,database:'getLancer'});
  for(const url of ['jdbc:postgresql://db.example.com:5432/getLancer','jdbc:postgresql://user:secret@localhost:5432/getLancer','jdbc:postgresql://localhost:5432/getLancer?options=unexpected'])assert.throws(()=>localDatabase({...config,DB_URL:url}));
  assert.throws(()=>localDatabase({...config,APP_ENV:'production'}));
});
test('setup generates missing service keys once without changing existing credentials',t=>{
  const dir=mkdtempSync(join(tmpdir(),'getlancer-setup-'));t.after(()=>rmSync(dir,{recursive:true,force:true}));mkdirSync(join(dir,'scripts'));
  for(const file of ['setup-local.mjs','local-config.mjs'])copyFileSync(new URL('../scripts/'+file,import.meta.url),join(dir,'scripts',file));
  const file=join(dir,'.env');writeFileSync(file,'APP_ENV=local\nDB_URL=jdbc:postgresql://localhost:5432/getLancer\nDB_USERNAME=postgres\nDB_PASSWORD=existing-local-test-password\nDB_SCHEMA=getlancer\nADMIN_BOOTSTRAP_PASSWORD=existing-admin-test-password\nSMTP_HOST=localhost\nOBJECT_STORAGE_ENDPOINT=http://localhost:9000\nOBJECT_STORAGE_UPLOAD_ENDPOINT=http://localhost:9000\nOBJECT_STORAGE_ACCESS_KEY=REPLACE_WITH_LOCAL_ACCESS_KEY\n');
  const run=()=>spawnSync(process.execPath,[join(dir,'scripts/setup-local.mjs')],{encoding:'utf8'});
  const first=run();assert.equal(first.status,0,first.stderr);const values=readEnvironment(file);
  assert.equal(values.DB_PASSWORD,'existing-local-test-password');assert.equal(values.ADMIN_BOOTSTRAP_PASSWORD,'existing-admin-test-password');
  assert.match(values.ADMIN_TOTP_SECRET,/^[A-Z2-7]{32}$/);assert.ok(values.OBJECT_STORAGE_SECRET_KEY.length>=32);
  for(const key of ['DB_PASSWORD','ADMIN_BOOTSTRAP_PASSWORD','ADMIN_TOTP_SECRET','OBJECT_STORAGE_SECRET_KEY'])assert.ok(!first.stdout.includes(values[key]));
  const before=readFileSync(file,'utf8');assert.equal(run().status,0);assert.equal(readFileSync(file,'utf8'),before);
  const docker=spawnSync(process.execPath,[join(dir,'scripts/setup-local.mjs'),'--docker'],{encoding:'utf8'});
  assert.equal(docker.status,0,docker.stderr);const container=readEnvironment(file);
  assert.deepEqual(localDatabase(container),{host:'localhost',port:5433,database:'getLancer'});
  for(const key of ['DB_PASSWORD','ADMIN_BOOTSTRAP_PASSWORD','ADMIN_TOTP_SECRET','OBJECT_STORAGE_SECRET_KEY'])assert.equal(container[key],values[key]);
  assert.equal(container.DB_USERNAME,'postgres');assert.equal(container.BACKEND_URL,'http://localhost:8080');
});
