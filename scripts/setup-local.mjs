import {readFileSync,openSync,closeSync,fstatSync,fchmodSync,writeSync,ftruncateSync,fsyncSync,constants} from 'node:fs';
import {randomBytes} from 'node:crypto';
import {parseEnvironment,localDatabase,placeholder} from './local-config.mjs';

const file=new URL('../.env',import.meta.url);
let descriptor;
try {
  if(['staging','production'].includes(process.env.APP_ENV))throw Error('Local setup cannot modify hosted configuration.');
  const flags=constants.O_RDWR|constants.O_NOFOLLOW;
  try{descriptor=openSync(file,flags);}
  catch(error){
    if(error.code!=='ENOENT')throw error;
    const example=readFileSync(new URL('../.env.example',import.meta.url));
    let created=false;
    try{descriptor=openSync(file,flags|constants.O_CREAT|constants.O_EXCL,0o600);created=true;}
    catch(createError){
      if(createError.code!=='EEXIST')throw createError;
      descriptor=openSync(file,flags);
    }
    if(created){
      let seeded=0;
      while(seeded<example.length)seeded+=writeSync(descriptor,example,seeded,example.length-seeded,seeded);
    }
  }
  if(!fstatSync(descriptor).isFile())throw Error('Local configuration must be a regular file.');
  const original=readFileSync(descriptor,'utf8');
  const values=parseEnvironment(original);localDatabase(values);
  for(const key of ['SMTP_HOST','OBJECT_STORAGE_ENDPOINT','OBJECT_STORAGE_UPLOAD_ENDPOINT']) {
    const host=key==='SMTP_HOST'?values[key]:new URL(values[key]).hostname;
    if(!['localhost','127.0.0.1','[::1]'].includes(host))throw Error('Local setup will not modify configured remote services. Check '+key+'.');
  }
  const alphabet='ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  const base32=()=>Array.from(randomBytes(32),byte=>alphabet[byte&31]).join('');
  const generators={OBJECT_STORAGE_ACCESS_KEY:()=>randomBytes(12).toString('hex'),OBJECT_STORAGE_SECRET_KEY:()=>randomBytes(24).toString('hex')};
  const updates={};
  if(values.ADMIN_EMAIL||values.ADMIN_BOOTSTRAP_PASSWORD||values.ADMIN_TOTP_SECRET) {
    generators.ADMIN_BOOTSTRAP_PASSWORD=()=>randomBytes(24).toString('base64url');
    generators.ADMIN_TOTP_SECRET=base32;
  }
  if(!values.MFA_KEYRING&&!values.MFA_ACTIVE_KEY_ID) {
    updates.MFA_ACTIVE_KEY_ID='local-v1';
    updates.MFA_KEYRING='local-v1:'+randomBytes(32).toString('base64');
  }else if(!values.MFA_KEYRING||!values.MFA_ACTIVE_KEY_ID)throw Error('Configure MFA_KEYRING and MFA_ACTIVE_KEY_ID together; existing keys are never replaced.');
  const docker=process.argv.includes('--docker');
  if(docker){
    Object.assign(updates,{DB_URL:'jdbc:postgresql://localhost:5433/getLancer',DB_USERNAME:'postgres',BACKEND_URL:'http://localhost:8080'});
    if(placeholder(values.DB_PASSWORD))updates.DB_PASSWORD='postgres';
  }
  for(const [key,generate] of Object.entries(generators))if(placeholder(values[key]))updates[key]=generate();
  let lines=original.split(/\r?\n/);const found=new Set();
  lines=lines.map(line=>{const key=line.split('=',1)[0].trim();if(key in updates){found.add(key);return key+'='+updates[key]}return line});
  for(const [key,value]of Object.entries(updates))if(!found.has(key))lines.push(key+'='+value);
  const output=Buffer.from(lines.join('\n').replace(/\n*$/,'\n'));
  fchmodSync(descriptor,0o600);
  let written=0;
  while(written<output.length)written+=writeSync(descriptor,output,written,output.length-written,written);
  ftruncateSync(descriptor,output.length);fsyncSync(descriptor);
  console.log(docker?'Docker configuration prepared for getLancer on localhost:5433. Existing passwords and administrator keys were preserved.':'Local configuration prepared. Existing database and administrator values were preserved.');
  console.log('Generated secrets stay in .env and existing values are preserved. Set ADMIN_EMAIL explicitly to enable local administrator bootstrap. Back up the local MFA key before storing administrator TOTP data.');
  console.log(docker?'Next: npm run services:docker, then npm run dev:local. Startup creates records only after the database is healthy.':'Next: npm run services:local, then npm run doctor:local. No database records have been created.');
}catch(error){console.error(error.code==='ENOENT'?'Configuration file unavailable.':error.code==='ELOOP'?'Local configuration symlinks are refused.':error.message);process.exitCode=1}
finally{if(descriptor!==undefined)closeSync(descriptor);}
