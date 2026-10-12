/** Trusted disposable local reproduction; every child must succeed. */
import {spawnSync} from 'node:child_process';
for (const script of ['scripts/verify-lab-scenarios.mjs','scripts/verify-data-ai.mjs']) {
 const result=spawnSync(process.execPath,[script],{stdio:'inherit',timeout:180000});
 if(result.error)throw result.error;
 if(result.status!==0)process.exit(result.status??1);
}
