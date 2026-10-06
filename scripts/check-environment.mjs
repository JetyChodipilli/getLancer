#!/usr/bin/env node
import {readEnvironment,placeholder} from './local-config.mjs';
const args=process.argv.slice(2),existingAdmin=args.includes('--existing-admin');
if(args.some(value=>value.startsWith('--')&&!['--existing-admin','--from-env'].includes(value))){console.error('Unknown option. Use [local-file] [--existing-admin] or --from-env.');process.exit(1)}
const file=args.find(value=>!value.startsWith('--'));
const injected=args.includes('--from-env')||['staging','production'].includes(process.env.APP_ENV);
let values;try{
 if(injected&&file)throw Error('Hosted/injected configuration cannot read an environment file.');
 values=injected?{...process.env}:readEnvironment(file||'.env');
 if(!injected&&['staging','production'].includes(values.APP_ENV))throw Error('Hosted configuration must use injected environment and --from-env.');
}catch(error){console.error(error.code==='ENOENT'?'Local environment file is missing. Copy .env.example first.':error.message);process.exit(1)}
const missing=[],problems=[];
const required=['APP_ENV','APP_BASE_URL','BACKEND_URL','DB_URL','DB_USERNAME','DB_PASSWORD','OBJECT_STORAGE_ENDPOINT','OBJECT_STORAGE_UPLOAD_ENDPOINT','OBJECT_STORAGE_BUCKET','OBJECT_STORAGE_REGION','OBJECT_STORAGE_ACCESS_KEY','OBJECT_STORAGE_SECRET_KEY','SMTP_HOST','SMTP_PORT','EMAIL_FROM_ADDRESS'];
for(const key of required)if(placeholder(values[key]))missing.push(key);
if(!['local','staging','production'].includes(values.APP_ENV))problems.push('APP_ENV must be local, staging or production');
const hosted=['staging','production'].includes(values.APP_ENV);
const localHost=host=>host==='localhost'||host.endsWith('.localhost')||host.endsWith('.local')||host==='[::1]'||host==='0.0.0.0'||host.startsWith('127.');
function https(key,origin=false){try{const url=new URL(values[key]);if(url.protocol!=='https:'||!url.hostname||localHost(url.hostname)||url.username||url.password||url.hash||(origin&&values[key]!==url.origin))throw Error();}catch{problems.push(key+' must be a valid remote HTTPS '+(origin?'origin':'URL'));}}
if(hosted){
 for(const key of ['APP_BASE_URL','BACKEND_URL','OBJECT_STORAGE_ENDPOINT','OBJECT_STORAGE_UPLOAD_ENDPOINT'])https(key,['APP_BASE_URL','BACKEND_URL'].includes(key));
 try{
  if(!values.DB_URL?.startsWith('jdbc:postgresql://'))throw Error();
  const db=new URL(values.DB_URL.slice(5));const seen=new Set();for(const entry of db.search.slice(1).split('&'))if(entry&&!/^[A-Za-z][A-Za-z0-9]*=/.test(entry))throw Error();
  if(!db.hostname||localHost(db.hostname)||db.username||db.password||db.hash||!/^\/[^/]+$/.test(db.pathname))throw Error();
  for(const [name] of db.searchParams){const key=name.toLowerCase();if(['gssencmode','authenticationpluginclassname','service','pghost','pgport','pgdbname','host','port','dbname','currentschema'].includes(key))throw Error();if(['sslmode','ssl','sslfactory','sslhostnameverifier','sslpasswordcallback','socketfactory','user','password','options'].includes(key)&&name!==key)throw Error();if(seen.has(key))throw Error();seen.add(key);if(['sslfactory','sslhostnameverifier','sslpasswordcallback','socketfactory','user','password','options'].includes(key))throw Error();}
  if(db.searchParams.get('sslmode')!=='verify-full'||(db.searchParams.has('ssl')&&db.searchParams.get('ssl')!=='true'))throw Error();
 }catch{problems.push('DB_URL must verify database TLS without duplicate parameters or connection overrides');}
 if(!/^[a-z][a-z0-9_]{0,62}$/.test(values.DB_SCHEMA||'')||values.DB_SCHEMA==='public')problems.push('DB_SCHEMA must be a private application schema');
 if(values.DB_USERNAME!=='getlancer_runtime')problems.push('DB_USERNAME must be getlancer_runtime');
 if(values.DB_MIGRATION_PASSWORD||values.SPRING_FLYWAY_PASSWORD||values.SPRING_FLYWAY_USER)problems.push('Migration credentials must not be present in runtime');
 if(values.SPRING_PROFILES_ACTIVE!==values.APP_ENV)problems.push('SPRING_PROFILES_ACTIVE must equal APP_ENV');
 if(values.SPRING_FLYWAY_ENABLED&&values.SPRING_FLYWAY_ENABLED!=='false')problems.push('Hosted runtime cannot enable Flyway');
 if(values.SPRING_CONFIG_IMPORT||values.SPRING_CONFIG_LOCATION||values.SPRING_CONFIG_ADDITIONAL_LOCATION)problems.push('Hosted configuration cannot import configuration files');
 if(values.SECURE_COOKIES!=='true')problems.push('SECURE_COOKIES must be true');
 if(values.DEMO_MODE&&values.DEMO_MODE!=='false')problems.push('Hosted deployments cannot enable DEMO_MODE');
 if(placeholder(values.BACKEND_PROXY_SECRET)||(values.BACKEND_PROXY_SECRET||'').length<32)missing.push('BACKEND_PROXY_SECRET');
 if(values.SMTP_TLS!=='true'&&values.SMTP_SSL!=='true')problems.push('SMTP_TLS or SMTP_SSL must be true');
 if(localHost(values.SMTP_HOST||'')||['mail','mailpit'].includes(values.SMTP_HOST))problems.push('SMTP_HOST must be a hosted mail service');
 if(values.APP_ENV==='production'){
  if(values.POLICIES_APPROVED!=='true'||placeholder(values.LEGAL_DOCUMENT_VERSION)||values.LEGAL_DOCUMENT_VERSION.includes('draft'))problems.push('Production policies must be approved and versioned');
  for(const key of ['SUPPORT_EMAIL','PRIVACY_EMAIL','COPYRIGHT_EMAIL'])if(!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(values[key]||''))problems.push(key+' must be an operator email');
 }
}
for(const name of ['GOOGLE','GITHUB']){const id=!!values[name+'_CLIENT_ID'],secret=!!values[name+'_CLIENT_SECRET'];if(id!==secret)problems.push(name+' OAuth needs both CLIENT_ID and CLIENT_SECRET');else if(id&&(placeholder(values[name+'_CLIENT_ID'])||placeholder(values[name+'_CLIENT_SECRET'])))problems.push(name+' OAuth contains placeholders');}
if(!existingAdmin&&(!values.ADMIN_BOOTSTRAP_PASSWORD||!values.ADMIN_TOTP_SECRET))problems.push('First-time administrator seeding needs ADMIN_BOOTSTRAP_PASSWORD and ADMIN_TOTP_SECRET; use --existing-admin only after verifying the administrator already exists');
if(values.ADMIN_BOOTSTRAP_PASSWORD||values.ADMIN_TOTP_SECRET){
 if(!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(values.ADMIN_EMAIL||''))problems.push('Administrator bootstrap requires an explicit ADMIN_EMAIL');
 if((values.ADMIN_BOOTSTRAP_PASSWORD||'').length<16||Buffer.byteLength(values.ADMIN_BOOTSTRAP_PASSWORD||'')>72||!/^[A-Z2-7]{32,}$/.test(values.ADMIN_TOTP_SECRET||''))problems.push('Administrator bootstrap requires strong password and Base32 TOTP');
}
if(hosted||values.MFA_KEYRING||values.MFA_ACTIVE_KEY_ID||values.ADMIN_TOTP_SECRET){
 try{
  const entries=(values.MFA_KEYRING||'').split(',');if(!entries.length||entries.length>16)throw Error();const keys=new Map(),materials=new Set();
  for(const entry of entries){const match=/^([A-Za-z0-9_-]{1,32}):([A-Za-z0-9+/]+={0,2})$/.exec(entry);if(!match||keys.has(match[1]))throw Error();const key=Buffer.from(match[2],'base64');if(key.length!==32||key.toString('base64')!==match[2]||materials.has(match[2]))throw Error();materials.add(match[2]);keys.set(match[1],key);}
  if(!keys.has(values.MFA_ACTIVE_KEY_ID))throw Error();
 }catch{problems.push('MFA_KEYRING must contain distinct version:base64 32-byte keys and MFA_ACTIVE_KEY_ID must select one');}
}
if(values.ANALYTICS_ENABLED==='true'&&(placeholder(values.ANALYTICS_HASH_SALT)||(values.ANALYTICS_HASH_SALT||'').length<32))missing.push('ANALYTICS_HASH_SALT');
if(values.PAYMENTS_ENABLED==='true'){
 if(!['test','live'].includes(values.RAZORPAY_MODE))problems.push('RAZORPAY_MODE must be test or live');
 for(const key of ['RAZORPAY_KEY_ID','RAZORPAY_KEY_SECRET','RAZORPAY_WEBHOOK_SECRET'])if(placeholder(values[key]))missing.push(key);
 for(const key of ['RAZORPAY_KEY_SECRET','RAZORPAY_WEBHOOK_SECRET'])if((values[key]||'').length<16)problems.push(key+' must contain at least 16 characters');
 if(values.RAZORPAY_ROUTE_APPROVED!=='true')problems.push('Payments require approved Razorpay Route and seller onboarding');
 if(values.POLICIES_APPROVED!=='true')problems.push('Payments require approved commercial policies');
 if(values.RAZORPAY_MODE==='live'&&!values.RAZORPAY_KEY_ID?.startsWith('rzp_live_'))problems.push('Live mode requires live Razorpay keys');
 if(values.RAZORPAY_MODE==='test'&&!values.RAZORPAY_KEY_ID?.startsWith('rzp_test_'))problems.push('Test mode requires test Razorpay keys');
}
console.log('Configuration check (values are never printed)');for(const key of new Set(missing))console.log('Missing: '+key);for(const p of problems)console.log('Check: '+p);if(!missing.length&&!problems.length)console.log('Configuration is structurally ready. Database role privileges, connectivity and provider flows still need verification.');process.exitCode=missing.length||problems.length?1:0;
