import {readFileSync,writeFileSync,chmodSync,existsSync,copyFileSync} from 'node:fs';
import {randomBytes} from 'node:crypto';
import {readEnvironment,localDatabase,placeholder} from './local-config.mjs';

const file=new URL('../.env',import.meta.url);
try {
  if(!existsSync(file))copyFileSync(new URL('../.env.example',import.meta.url),file);
  const values=readEnvironment(file);localDatabase(values);
  for(const key of ['SMTP_HOST','OBJECT_STORAGE_ENDPOINT','OBJECT_STORAGE_UPLOAD_ENDPOINT']) {
    const host=key==='SMTP_HOST'?values[key]:new URL(values[key]).hostname;
    if(!['localhost','127.0.0.1','[::1]'].includes(host))throw Error('Local setup will not modify configured remote services. Check '+key+'.');
  }
  const alphabet='ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  const base32=()=>Array.from(randomBytes(32),byte=>alphabet[byte&31]).join('');
  const generators={ADMIN_BOOTSTRAP_PASSWORD:()=>randomBytes(24).toString('base64url'),ADMIN_TOTP_SECRET:base32,OBJECT_STORAGE_ACCESS_KEY:()=>randomBytes(12).toString('hex'),OBJECT_STORAGE_SECRET_KEY:()=>randomBytes(24).toString('hex')};
  const updates={};
  const docker=process.argv.includes('--docker');
  if(docker){
    Object.assign(updates,{DB_URL:'jdbc:postgresql://localhost:5433/getLancer',DB_USERNAME:'postgres',BACKEND_URL:'http://localhost:8080'});
    if(placeholder(values.DB_PASSWORD))updates.DB_PASSWORD='postgres';
  }
  for(const [key,generate] of Object.entries(generators))if(placeholder(values[key]))updates[key]=generate();
  let lines=readFileSync(file,'utf8').split(/\r?\n/);const found=new Set();
  lines=lines.map(line=>{const key=line.split('=',1)[0].trim();if(key in updates){found.add(key);return key+'='+updates[key]}return line});
  for(const [key,value]of Object.entries(updates))if(!found.has(key))lines.push(key+'='+value);
  writeFileSync(file,lines.join('\n').replace(/\n*$/,'\n'));chmodSync(file,0o600);
  console.log(docker?'Docker configuration prepared for getLancer on localhost:5433. Existing passwords and administrator keys were preserved.':'Local configuration prepared. Existing database and administrator values were preserved.');
  console.log('Generated secrets, if missing, are in .env. Add ADMIN_TOTP_SECRET to your authenticator using a time-based setup key.');
  console.log(docker?'Next: npm run services:docker, then npm run dev:local. Startup creates records only after the database is healthy.':'Next: npm run services:local, then npm run doctor:local. No database records have been created.');
}catch(error){console.error(error.code==='ENOENT'?'Configuration file unavailable.':error.message);process.exitCode=1}
