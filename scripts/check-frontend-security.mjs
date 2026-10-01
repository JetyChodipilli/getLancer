import {readdirSync,readFileSync,statSync} from 'node:fs';
import {join,relative} from 'node:path';

const root='dist/client';
const patterns=[[/\bAKIA[A-Z0-9]{16}\b/,'AWS access key'],[/\b(?:ghp|gho|ghu|ghs|github_pat)_[A-Za-z0-9_]{30,}\b/,'GitHub token'],[/\bsk-(?:proj-)?[A-Za-z0-9_-]{40,}\b/,'private API key'],[/-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/,'private key']];
const failures=[];let checked=0;
function visit(directory){for(const name of readdirSync(directory)){const path=join(directory,name);if(statSync(path).isDirectory()){visit(path);continue;}if(path.endsWith('.map'))failures.push(`${relative(root,path)}: public source map`);if(!/\.(?:js|css|html|json|txt|xml)$/.test(path))continue;checked++;const content=readFileSync(path,'utf8');for(const [pattern,label]of patterns)if(pattern.test(content))failures.push(`${relative(root,path)}: ${label}`);for(const key of ['BACKEND_PROXY_SECRET','OPENAI_API_KEY','DATABASE_PASSWORD','RAZORPAY_KEY_SECRET']){const value=process.env[key];if(value&&value.length>=8&&content.includes(value))failures.push(`${relative(root,path)}: server secret ${key}`);}}}
visit(root);
if(failures.length){console.error(failures.join('\n'));process.exitCode=1;}else console.log(`Frontend security check passed: ${checked} public text assets; no credential signatures, supplied server secrets or source maps.`);
