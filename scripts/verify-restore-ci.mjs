// Recovery rehearsal for synthetic CI data only. Not a production backup command.
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {createCipheriv,createDecipheriv,randomBytes} from 'node:crypto';
import {mkdtempSync,writeFileSync,readFileSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {readEnvironment} from './local-config.mjs';
assert.equal(process.env.CI,'true');
assert.match(process.env.COMPOSE_PROJECT_NAME||'',/^getlancer-ci-[0-9]+$/);
assert.equal(readEnvironment().ADMIN_EMAIL,'ci-admin@example.test');
const command=(args,input)=>execFileSync('docker',['compose','exec','-T','db',...args],{input,maxBuffer:16*1024*1024,stdio:['pipe','pipe','pipe']});
const query=(database,sql)=>command(['psql','-U','postgres','-d',database,'-At','--set','ON_ERROR_STOP=1','-c',sql]).toString().trim();
const target='getlancer_restore_ci';
assert.equal(query('postgres',`SELECT count(*) FROM pg_database WHERE datname='${target}'`),'0','Refuse to overwrite an existing restore target.');
const countsSql="SELECT (SELECT count(*) FROM getlancer.users),(SELECT count(*) FROM getlancer.products),(SELECT count(*) FROM getlancer.product_media),(SELECT count(*) FROM getlancer.inquiries),(SELECT count(*) FROM getlancer.reviews),(SELECT count(*) FROM getlancer.moderation_actions),(SELECT count(*) FROM getlancer.delivery_engagements),(SELECT count(*) FROM getlancer.delivery_agreements),(SELECT count(*) FROM getlancer.delivery_milestones),(SELECT count(*) FROM getlancer.delivery_disputes),(SELECT count(*) FROM getlancer.payment_attempts),(SELECT count(*) FROM getlancer.payment_ledger),(SELECT count(*) FROM getlancer.source_templates),(SELECT count(*) FROM getlancer.source_versions),(SELECT count(*) FROM getlancer.template_purchases),(SELECT count(*) FROM getlancer.commerce_ledger),(SELECT count(*) FROM getlancer.commerce_audit),(SELECT count(*) FROM getlancer.flyway_schema_history WHERE success)";
const before=query('getLancer',countsSql);
const archive=command(['pg_dump','-U','postgres','-d','getLancer','--schema=getlancer','--format=custom','--no-owner','--no-acl']);
assert.ok(archive.length>1000);
const directory=mkdtempSync(join(tmpdir(),'getlancer-restore-')),key=randomBytes(32),nonce=randomBytes(12);
let created=false;
try{
 const cipher=createCipheriv('aes-256-gcm',key,nonce);
 const encrypted=Buffer.concat([cipher.update(archive),cipher.final()]),tag=cipher.getAuthTag();
 const file=join(directory,'backup.enc');writeFileSync(file,encrypted,{mode:0o600});
 // A changed authentication tag must fail before any archive reaches PostgreSQL.
 const invalid=createDecipheriv('aes-256-gcm',key,nonce),badTag=Buffer.from(tag);badTag[0]^=1;invalid.setAuthTag(badTag);invalid.update(encrypted);assert.throws(()=>invalid.final());
 const decipher=createDecipheriv('aes-256-gcm',key,nonce);decipher.setAuthTag(tag);
 const restoredArchive=Buffer.concat([decipher.update(readFileSync(file)),decipher.final()]);
 const started=Date.now();
 command(['createdb','-U','postgres',target]);created=true;
 command(['pg_restore','-U','postgres','-d',target,'--no-owner','--no-acl','--exit-on-error'],restoredArchive);
 assert.equal(query(target,countsSql),before);
 assert.equal(query(target,"SELECT count(*) FROM getlancer.user_roles WHERE role='ADMIN'"),'1');
 assert.equal(query(target,"SELECT count(*) FROM getlancer.inquiries WHERE current_status='COMPLETED' AND email_confirmed_at IS NOT NULL"),'1');
 assert.equal(query(target,"SELECT count(*) FROM getlancer.reviews WHERE moderation_status='PUBLISHED' AND visibility='ANONYMOUS'"),'1');
 assert.equal(query(target,"SELECT count(*) FROM getlancer.products WHERE approval_status='SUSPENDED'"),'1');
 console.log(`Disposable encrypted database restore passed: application row counts, migrations, sole admin, completed inquiry, published review and suspension; restore ${Date.now()-started}ms.`);
}finally{
 if(created)command(['dropdb','-U','postgres',target]);
 key.fill(0);rmSync(directory,{recursive:true,force:true});
}
