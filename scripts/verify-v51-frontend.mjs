/** Root integration oracle. Executes real production/type/security checks. */
import {spawnSync} from 'node:child_process';
const commands=[['npm',['run','build']],['node',['node_modules/typescript/bin/tsc','--noEmit']],['node',['scripts/check-security-contract.mjs']],['node',['scripts/check-frontend-security.mjs']],['node',['ops/database/check-permissions.mjs']]];
for(const [command,args] of commands){const result=spawnSync(command,args,{stdio:'inherit',timeout:240000});if(result.error)throw result.error;if(result.status!==0)process.exit(result.status??1);}
console.log('V51_FRONTEND_ACCEPTANCE_OK');
