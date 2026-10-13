/** Root content oracle: actual native execution, source reproduction and clean framework builds. */
import {spawnSync} from 'node:child_process';
const commands=[['scripts/verify-lab-scenarios.mjs'],['scripts/build-lab-material.mjs'],['scripts/build-lab-material.mjs','--verify'],['scripts/verify-lab-packages.mjs'],['scripts/verify-iot.mjs'],['scripts/build-iot-material.mjs'],['scripts/build-iot-material.mjs','--verify'],['scripts/verify-framework-previews.mjs','--native']];
for(const args of commands){const result=spawnSync(process.execPath,args,{stdio:'inherit',timeout:300000});if(result.error)throw result.error;if(result.status!==0)process.exit(result.status??1);}
console.log('V51_CONTENT_ACCEPTANCE_OK');
