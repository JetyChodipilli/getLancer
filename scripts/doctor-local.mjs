import net from 'node:net';
import {spawnSync} from 'node:child_process';
import {readEnvironment,localDatabase,placeholder} from './local-config.mjs';

const connect=(host,port)=>new Promise(resolve=>{
  const socket=net.createConnection({host,port});const finish=ok=>{socket.destroy();resolve(ok)};
  socket.setTimeout(2500,()=>finish(false));socket.once('connect',()=>finish(true));socket.once('error',()=>finish(false));
});
try {
  const values={...readEnvironment(),...process.env};const db=localDatabase(values);let failed=false;
  const report=(ok,message)=>{console.log((ok?'OK: ':'NEEDS SETUP: ')+message);if(!ok)failed=true};
  for(const key of ['DB_USERNAME','DB_PASSWORD','OBJECT_STORAGE_ACCESS_KEY','OBJECT_STORAGE_SECRET_KEY'])report(!placeholder(values[key]),key+' configured');
  const env={...process.env,PGHOST:db.host,PGPORT:String(db.port),PGDATABASE:db.database,PGUSER:values.DB_USERNAME,PGPASSWORD:values.DB_PASSWORD,PGCONNECT_TIMEOUT:'4',PGOPTIONS:'-c default_transaction_read_only=on'};
  const psql=process.env.PSQL_PATH||'psql';
  const query=sql=>spawnSync(psql,['-X','-w','-tA','-v','ON_ERROR_STOP=1','-c',sql],{env,encoding:'utf8',timeout:7000});
  const probe=query('SELECT current_database()');
  if(probe.error?.code==='ENOENT') {
    report(false,'psql not found. Add PostgreSQL bin to PATH or set PSQL_PATH to the psql executable.');
    report(await connect(db.host,db.port),'PostgreSQL TCP listener (credentials not verified)');
  }else if(probe.status!==0)report(false,'Database connection failed. Check the PostgreSQL service, exact database name, username and password.');
  else {
    report(true,'Authenticated to configured PostgreSQL database (read-only check).');
    const schema=values.DB_SCHEMA||'public';
    const tables=query("SELECT to_regclass('"+schema+".user_roles') IS NOT NULL");
    if(tables.stdout?.trim()==='t'){
      const admin=query('SELECT count(*) FROM '+schema+".user_roles r JOIN "+schema+".users u ON u.id=r.user_id WHERE r.role='ADMIN' AND u.account_status='ACTIVE' AND u.admin_totp IS NOT NULL");
      report(admin.status===0&&admin.stdout?.trim()==='1','Exactly one active administrator with authenticator configured');
    }else console.log('PENDING: Start the backend to apply migrations and create the first administrator.');
  }
  const password=values.ADMIN_BOOTSTRAP_PASSWORD||'',totp=values.ADMIN_TOTP_SECRET||'';
  if(password||totp)report(password.length>=16&&Buffer.byteLength(password)<=72&&/^[A-Z2-7]{32,}$/.test(totp),'First-administrator password and authenticator settings');
  report(await connect(values.SMTP_HOST,Number(values.SMTP_PORT||1025)),'Local email service');
  const storage=new URL(values.OBJECT_STORAGE_ENDPOINT);
  report(await connect(storage.hostname,Number(storage.port|| (storage.protocol==='https:'?443:80))),'Object storage service');
  for(const provider of ['GOOGLE','GITHUB'])console.log((values[provider+'_CLIENT_ID']&&values[provider+'_CLIENT_SECRET']?'CONFIGURED: ':'OPTIONAL: ')+provider+' OAuth requires registered provider credentials and callback.');
  console.log('No migrations or account changes were run. See ops/LOCAL_POSTGRESQL.md for startup and verification.');
  process.exitCode=failed?1:0;
}catch(error){console.error(error.code==='ENOENT'?'Run npm run setup:local first.':error.message);process.exitCode=1}
