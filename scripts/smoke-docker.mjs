// Ephemeral CI stack only; never use the owner's database or credentials here.
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {createHmac} from 'node:crypto';
import {readEnvironment} from './local-config.mjs';
const env=readEnvironment(new URL('../.env',import.meta.url));
assert.equal(process.env.CI,'true','This smoke check runs only in disposable CI.');
assert.equal(env.ADMIN_EMAIL,'ci-admin@example.test','Use the synthetic CI administrator.');
const query=sql=>execFileSync('docker',['compose','exec','-T','db','psql','-U','postgres','-d','getLancer','-Atc',sql],{encoding:'utf8'}).trim();
assert.equal(query('SELECT current_database()'),'getLancer');
assert.equal(query("SELECT count(*) FROM getlancer.user_roles WHERE role='ADMIN'"),'1');
assert.equal(query('SELECT count(*) FROM getlancer.flyway_schema_history WHERE success'), '10');
const cookies=new Map();
async function api(path,body){
 const response=await fetch('http://localhost:8080'+path,{method:body?'POST':'GET',headers:{Origin:'http://localhost:3000','X-Requested-With':'getlancer','Content-Type':'application/json',Cookie:[...cookies].map(([k,v])=>k+'='+v).join('; ')},body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(10000)});
 for(const value of response.headers.getSetCookie()){const pair=value.split(';')[0],split=pair.indexOf('=');cookies.set(pair.slice(0,split),pair.slice(split+1));}
 assert.ok(response.ok,`Request ${path} returned HTTP ${response.status}`);
 return response.status===204?{}:response.json();
}
assert.equal((await api('/actuator/health/readiness')).status,'UP');
assert.equal((await api('/api/v1/auth/providers')).google,false);
assert.equal((await api('/api/v1/auth/login',{email:env.ADMIN_EMAIL,password:env.ADMIN_BOOTSTRAP_PASSWORD})).mfaRequired,true);
// RFC 6238, matching the backend's SHA-1 / six digit / 30 second authenticator.
const bits=[...env.ADMIN_TOTP_SECRET].map(c=>'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'.indexOf(c).toString(2).padStart(5,'0')).join('');
const secret=Buffer.from(bits.match(/.{8}/g).map(b=>parseInt(b,2)));
const counter=Buffer.alloc(8);counter.writeBigUInt64BE(BigInt(Math.floor(Date.now()/30000)));
const digest=createHmac('sha1',secret).update(counter).digest(),offset=digest[digest.length-1]&15;
const totp=String((digest.readUInt32BE(offset)&0x7fffffff)%1000000).padStart(6,'0');
await api('/api/v1/auth/login/mfa',{totp});
assert.ok((await api('/api/v1/me')).roles.includes('ADMIN'));
assert.deepEqual((await api('/api/v1/admin/products/pending')).items,[]);
await api('/api/v1/auth/logout',{});
const storage=await fetch('http://localhost:9000/getlancer',{signal:AbortSignal.timeout(10000)});
assert.equal(storage.status,403,'Proof bucket must not allow anonymous listing.');
console.log('Docker smoke passed: exact database, ten migrations, one admin, password + MFA, protected admin API, private bucket.');
