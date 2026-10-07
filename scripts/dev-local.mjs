import {spawn} from 'node:child_process';
import {readEnvironment,localDatabase,projectRoot} from './local-config.mjs';

try {
  const values={...readEnvironment(),...process.env};localDatabase(values);
  const backend=process.argv[2]==='backend';
  const win=process.platform==='win32';
  const env={...process.env};
  // Only the explicit local Spring profile imports the local backend environment.
  if(backend)env.SPRING_PROFILES_ACTIVE='local';
  for(const key of ['APP_ENV','APP_BASE_URL','BACKEND_URL','DEMO_MODE','BACKEND_PROXY_SECRET','INDEX_PUBLIC_PAGES','DEMO_PUBLIC_URL_TEMPLATE'])if(values[key]!==undefined)env[key]=values[key];
  const command=backend?(win?'cmd.exe':'mvn'):process.execPath;
  const args=backend?(win?['/d','/s','/c','mvn -f backend/pom.xml spring-boot:run']:['-f','backend/pom.xml','spring-boot:run']):['node_modules/vite/bin/vite.js','--host','127.0.0.1','--port','3000','--strictPort'];
  const child=spawn(command,args,{cwd:projectRoot,env,stdio:'inherit'});
  child.once('error',()=>{console.error(backend?'Install Java 17+ and Maven, then retry.':'Install dependencies with npm ci, then retry.');process.exitCode=1});
  child.once('exit',code=>{process.exitCode=code??1});
  for(const signal of ['SIGINT','SIGTERM'])process.on(signal,()=>child.kill(signal));
}catch(error){console.error(error.code==='ENOENT'?'Run npm run setup:local first.':error.message);process.exitCode=1}
