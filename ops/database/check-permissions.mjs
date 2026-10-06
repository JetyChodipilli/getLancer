#!/usr/bin/env node
// Source coverage is supplementary; real PostgreSQL role tests remain the behavioral gate.
import {readFileSync,readdirSync} from 'node:fs';
import assert from 'node:assert/strict';
const migrations=new URL('../../backend/src/main/resources/db/migration/',import.meta.url);
const java=new URL('../../backend/src/main/java/',import.meta.url);
const permissions=readFileSync(new URL('./10_permissions.sql',import.meta.url),'utf8');
function list(name){const match=new RegExp(name+" text\\[\\] := ARRAY\\[([^;]+)\\];").exec(permissions);assert.ok(match,name+' permission inventory missing');return new Set([...match[1].matchAll(/'([a-z_]+)'/g)].map(value=>value[1]));}
const known=list('known_tables'),readOnly=list('read_only_tables'),updates=list('update_tables'),deletes=list('delete_tables');
const sql=readdirSync(migrations).filter(file=>file.endsWith('.sql')).map(file=>readFileSync(new URL(file,migrations),'utf8')).join('\n');
const tables=new Set([...sql.matchAll(/\bCREATE TABLE\s+([a-z_]+)/gi)].map(match=>match[1]));
for(const table of tables)assert.ok(known.has(table),'Review runtime permission classification for new table '+table);
for(const file of readdirSync(java,{recursive:true}).filter(file=>file.endsWith('.java'))){
 const source=readFileSync(new URL(file,java),'utf8');
 for(const match of source.matchAll(/\b(INSERT INTO|UPDATE|DELETE FROM)\s+([a-z_]+)/gi)){
  const [,operation,table]=match;if(!tables.has(table))continue;
  if(operation.toUpperCase()==='UPDATE')assert.ok(updates.has(table),'Missing UPDATE permission for '+table+' used by '+file);
  else if(operation.toUpperCase()==='DELETE FROM')assert.ok(deletes.has(table),'Missing DELETE permission for '+table+' used by '+file);
  else assert.ok(!readOnly.has(table),'Missing INSERT permission for '+table+' used by '+file);
 }
}
// Also cover reviewed dynamic table names and ON CONFLICT updates that simple SQL scans omit.
for(const table of ['categories','technologies','product_access_grants','request_shortlist','commerce_provider_disputes','payment_provider_disputes','maintenance_provider_disputes','maintenance_refunds','rate_buckets'])assert.ok(updates.has(table),'Missing upsert permission for '+table);
for(const table of ['security_audit_events','inquiry_events','moderation_actions','delivery_activity','payment_ledger','payment_account_audit','commerce_ledger','commerce_audit','maintenance_ledger','maintenance_audit','hosting_audit','component_audit','component_slot_ledger','component_slot_events','publishing_capacity_grants']){
 assert.ok(known.has(table),'Missing immutable table '+table);assert.ok(!updates.has(table)&&!deletes.has(table),'Immutable history grants mutation for '+table);
}
assert.ok(!permissions.includes('GRANT ALL'),'Runtime permissions must enumerate operations');
console.log('Database permission source coverage passed; '+tables.size+' migration tables classified. Real-role PostgreSQL tests are still required.');
