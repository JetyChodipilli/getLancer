import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync,writeFileSync,readFileSync,copyFileSync,mkdirSync,rmSync,symlinkSync,statSync} from 'node:fs';
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

test('local setup refuses a symlink without touching its target',t=>{
 const dir=mkdtempSync(join(tmpdir(),'getlancer-symlink-'));t.after(()=>rmSync(dir,{recursive:true,force:true}));mkdirSync(join(dir,'scripts'));
 for(const name of ['setup-local.mjs','local-config.mjs'])copyFileSync(new URL('../scripts/'+name,import.meta.url),join(dir,'scripts',name));
 const target=join(dir,'protected-config');writeFileSync(target,'protected provider configuration',{mode:0o644});
 symlinkSync(target,join(dir,'.env'));
 const run=spawnSync(process.execPath,[join(dir,'scripts/setup-local.mjs')],{encoding:'utf8'});
 assert.equal(run.status,1);assert.match(run.stderr,/symlinks are refused/);
 assert.equal(readFileSync(target,'utf8'),'protected provider configuration');assert.equal(statSync(target).mode&0o777,0o644);
});

test('environment checker permits retired bootstrap secrets only with explicit existing-admin mode',t=>{
 const dir=mkdtempSync(join(tmpdir(),'getlancer-check-'));t.after(()=>rmSync(dir,{recursive:true,force:true}));
 const file=join(dir,'.env');
 const values={APP_ENV:'local',APP_BASE_URL:'http://localhost:3000',BACKEND_URL:'http://localhost:8080',DB_URL:'jdbc:postgresql://localhost:5433/getLancer',DB_USERNAME:'postgres',DB_PASSWORD:'private-test@value#only',OBJECT_STORAGE_ENDPOINT:'http://localhost:9000',OBJECT_STORAGE_UPLOAD_ENDPOINT:'http://localhost:9000',OBJECT_STORAGE_BUCKET:'getlancer',OBJECT_STORAGE_REGION:'us-east-1',OBJECT_STORAGE_ACCESS_KEY:'test-access',OBJECT_STORAGE_SECRET_KEY:'test-secret',SMTP_HOST:'localhost',SMTP_PORT:'1025',EMAIL_FROM_ADDRESS:'test@example.test'};
 const save=extra=>writeFileSync(file,Object.entries({...values,...extra}).map(([k,v])=>`${k}=${v}`).join('\n')+'\n');
 const run=(...args)=>spawnSync(process.execPath,[new URL('../scripts/check-environment.mjs',import.meta.url).pathname,file,...args],{encoding:'utf8'});
 save({});assert.equal(run().status,1);assert.equal(run('--existing-admin').status,0);
 save({APP_ENV:'prod'});assert.equal(run('--existing-admin').status,1);
 save({DB_PASSWORD:'"quoted-secret"'});const invalid=run('--existing-admin');assert.equal(invalid.status,1);assert.ok(!invalid.stderr.includes('quoted-secret'));
 save({});writeFileSync(file,readFileSync(file,'utf8')+'DB_PASSWORD=duplicate-secret\n');const duplicate=run('--existing-admin');assert.equal(duplicate.status,1);assert.match(duplicate.stderr,/Duplicate environment key/);assert.ok(!duplicate.stderr.includes('duplicate-secret'));
});

test('payment activation requires coherent real gateway credentials and commercial approval without printing secrets',t=>{
 const dir=mkdtempSync(join(tmpdir(),'getlancer-payments-'));t.after(()=>rmSync(dir,{recursive:true,force:true}));
 const file=join(dir,'.env'),base=readEnvironment(new URL('../.env.example',import.meta.url));
 Object.assign(base,{DB_PASSWORD:'synthetic-database',OBJECT_STORAGE_ACCESS_KEY:'synthetic-access',OBJECT_STORAGE_SECRET_KEY:'synthetic-storage',ADMIN_EMAIL:'ci-admin@example.test',ADMIN_BOOTSTRAP_PASSWORD:'synthetic-admin-password-123',ADMIN_TOTP_SECRET:'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP',MFA_ACTIVE_KEY_ID:'v1',MFA_KEYRING:'v1:'+Buffer.alloc(32,1).toString('base64'),PAYMENTS_ENABLED:'true'});
 const run=extra=>{writeFileSync(file,Object.entries({...base,...extra}).map(([k,v])=>`${k}=${v}`).join('\n')+'\n');return spawnSync(process.execPath,[new URL('../scripts/check-environment.mjs',import.meta.url).pathname,file],{encoding:'utf8'});};
 const missing=run({});assert.equal(missing.status,1);assert.match(missing.stdout,/Missing: RAZORPAY_KEY_SECRET/);assert.match(missing.stdout,/approved commercial policies/);
 const configured={RAZORPAY_MODE:'test',RAZORPAY_KEY_ID:'rzp_test_synthetic',RAZORPAY_KEY_SECRET:'synthetic-provider-private',RAZORPAY_WEBHOOK_SECRET:'synthetic-webhook-private',RAZORPAY_ROUTE_APPROVED:'true',POLICIES_APPROVED:'true'};
 const ready=run(configured);assert.equal(ready.status,0,ready.stdout);assert.ok(!ready.stdout.includes(configured.RAZORPAY_KEY_SECRET));assert.ok(!ready.stdout.includes(configured.RAZORPAY_WEBHOOK_SECRET));
 assert.equal(run({...configured,RAZORPAY_MODE:'live'}).status,1);assert.equal(run({...configured,RAZORPAY_ROUTE_APPROVED:'false'}).status,1);
});
