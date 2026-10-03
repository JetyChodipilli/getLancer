// Root-authored bounded local source oracle. Database/provider/browser coverage is in final-head CI.
import {execFileSync} from 'node:child_process';
const options={stdio:'inherit',timeout:180000};
execFileSync('git',['diff','--check'],options);
execFileSync('npx',['tsc','--noEmit'],options);
execFileSync('node',['--test','tests/maintenance.test.mjs'],options);
execFileSync('node',['scripts/check-frontend-security.mjs'],options);
console.log('V4 local source checks passed.');
