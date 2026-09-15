#!/usr/bin/env node
import {readFileSync} from 'node:fs';
const file=process.argv[2]||'.env';
let text;try{text=readFileSync(file,'utf8')}catch{console.error('Environment file is missing. Copy .env.example first.');process.exit(1)}
const values={};const duplicates=[];
for(const line of text.split(/\r?\n/)){if(!line.trim()||line.trim().startsWith('#'))continue;const i=line.indexOf('=');if(i<1)continue;const k=line.slice(0,i).trim();if(k in values)duplicates.push(k);values[k]=line.slice(i+1).trim()}
const missing=[];const problems=[];
const required=['APP_ENV','APP_BASE_URL','BACKEND_URL','DB_URL','DB_USERNAME','DB_PASSWORD','OBJECT_STORAGE_ENDPOINT','OBJECT_STORAGE_UPLOAD_ENDPOINT','OBJECT_STORAGE_BUCKET','OBJECT_STORAGE_REGION','OBJECT_STORAGE_ACCESS_KEY','OBJECT_STORAGE_SECRET_KEY','SMTP_HOST','SMTP_PORT','EMAIL_FROM_ADDRESS'];
for(const key of required)if(!values[key]||/REPLACE_|YOUR_|CHANGE_ME/.test(values[key]))missing.push(key);
const hosted=['staging','production'].includes(values.APP_ENV);
if(hosted){for(const key of ['APP_BASE_URL','BACKEND_URL','OBJECT_STORAGE_ENDPOINT','OBJECT_STORAGE_UPLOAD_ENDPOINT'])if(!values[key]?.startsWith('https://'))problems.push(key+' must use HTTPS');if(!values.DB_URL?.includes('sslmode=verify-full'))problems.push('DB_URL must verify database TLS');if(values.DB_SCHEMA==='public'||!values.DB_SCHEMA)problems.push('DB_SCHEMA must be a private application schema');if(values.SECURE_COOKIES!=='true')problems.push('SECURE_COOKIES must be true');if((values.BACKEND_PROXY_SECRET||'').length<32)missing.push('BACKEND_PROXY_SECRET');if(values.SMTP_TLS!=='true'&&values.SMTP_SSL!=='true')problems.push('SMTP_TLS or SMTP_SSL must be true')}
for(const name of ['GOOGLE','GITHUB']){const id=!!values[name+'_CLIENT_ID'],secret=!!values[name+'_CLIENT_SECRET'];if(id!==secret)problems.push(name+' OAuth needs both CLIENT_ID and CLIENT_SECRET');else if(!id)console.log('Optional: '+name+' OAuth is not configured. Email/password remains available when the backend is connected.');}
if(!values.ADMIN_BOOTSTRAP_PASSWORD||!values.ADMIN_TOTP_SECRET)problems.push('First-time administrator seeding needs ADMIN_BOOTSTRAP_PASSWORD and ADMIN_TOTP_SECRET; leave blank only if the administrator already exists');
if(values.ANALYTICS_ENABLED==='true'&&(values.ANALYTICS_HASH_SALT||'').length<32)missing.push('ANALYTICS_HASH_SALT');
if(duplicates.length)problems.push('Duplicate keys: '+duplicates.join(', '));
console.log('Configuration check (values are never printed)');for(const key of missing)console.log('Missing: '+key);for(const p of problems)console.log('Check: '+p);if(!missing.length&&!problems.length)console.log('Configuration is structurally ready. Connectivity and provider flows still need verification.');process.exitCode=missing.length||problems.length?1:0;
