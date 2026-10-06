import {readFileSync,readdirSync} from 'node:fs';
import {join} from 'node:path';
import assert from 'node:assert/strict';

const root=new URL('../',import.meta.url).pathname;
function files(path){return readdirSync(path,{withFileTypes:true}).flatMap(entry=>entry.isDirectory()?files(join(path,entry.name)):[join(path,entry.name)]);}
const source=files(join(root,'backend/src/main/java')).filter(path=>path.endsWith('.java'));
let controllers=0,bodies=0;
for(const path of source){
 const code=readFileSync(path,'utf8');
 assert.doesNotMatch(code,/^import\s+(?:static\s+)?[^;]+\.\*;/m,`${path}: explicit imports required`);
 assert.doesNotMatch(code,/SELECT\s+(?:\w+\.)?\*/i,`${path}: explicit SQL projections required`);
 assert.doesNotMatch(code,/@org\.springframework\./,`${path}: import annotation types`);
 assert.doesNotMatch(code,/@(?:Autowired|Value\([^)]*\))\s+(?:(?:private|public|protected)\s+)?(?:final\s+)?[\w<>]+\s+\w+\s*(?:=[^;]*)?;/,`${path}: constructor injection required`);
 if(!path.endsWith('Controller.java'))continue;
 controllers++;
 assert.match(code,/@PreAuthorize\("@authorization\.routeAllowed\(authentication\)"\)/,`${path}: declarative authorization required`);
 assert.doesNotMatch(code,/@RequestBody\s+Map\b/,`${path}: concrete request DTO required`);
 bodies+=(code.match(/@RequestBody/g)||[]).length;
 assert.equal((code.match(/@Valid\s+@RequestBody/g)||[]).length,(code.match(/@RequestBody/g)||[]).length,`${path}: every JSON DTO must be validated`);
}
const actual=new Set(readFileSync(join(root,'backend/src/main/resources/api-authorization-policy.txt'),'utf8').split('\n').filter(line=>line&&!line.startsWith('#')).map(line=>{const [method,,path]=line.split(' ');return method+' '+path;}).filter(route=>route.includes('/api/v1/')));
const contract=new Set(readFileSync(join(root,'backend/src/test/resources/api-route-contract.txt'),'utf8').trim().split('\n'));
assert.deepEqual(actual,contract,'published route policy must be complete');
const security=readFileSync(join(root,'backend/src/main/java/com/getlancer/config/SecurityConfiguration.java'),'utf8');
assert.match(security,/EnableMethodSecurity/);assert.match(security,/anyRequest\(\)\.access/);
assert.doesNotMatch(security,/csrf\([^\n]*disable/);
const common=readFileSync(join(root,'backend/src/main/resources/application.properties'),'utf8');
assert.doesNotMatch(common,/^spring\.config\.import=/m);
assert.match(common,/app\.admin-email=\$\{ADMIN_EMAIL:\}/);
assert.equal(readFileSync(join(root,'.env.example'),'utf8').match(/^ADMIN_EMAIL=(.*)$/m)?.[1],'');
console.log(`Security source contract passed: ${controllers} controllers, ${bodies} typed JSON handlers, ${actual.size} explicitly classified application routes. Execution and operator gates remain separate.`);
