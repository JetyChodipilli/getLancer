import {spawn} from 'node:child_process';
import {dirname,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'..');
async function run(command,args){await new Promise((yes,no)=>{const child=spawn(command,args,{cwd:root,stdio:'inherit'});child.on('error',no);child.on('exit',code=>code===0?yes():no(Error('Frontend check failed: '+command+' (exit '+code+')')));});}
if(process.argv.includes('--browser')){
 await run(process.execPath,['node_modules/@playwright/test/cli.js','test','tests/browser/v48.spec.ts']);
 console.log('V48_BROWSER_OK');
}else{
 await run(process.execPath,['node_modules/typescript/bin/tsc','--noEmit','--incremental','false']);
 await run('bash',['scripts/build-verified.sh']);
 const {readdir}=await import('node:fs/promises');
 const tests=(await readdir(resolve(root,'tests'))).filter(name=>name.endsWith('.test.mjs')).sort().map(name=>'tests/'+name);
 await run(process.execPath,['--experimental-strip-types','--test',...tests]);
 console.log('V48_FRONTEND_OK');
}
