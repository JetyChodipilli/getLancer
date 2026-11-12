import test from 'node:test';
import assert from 'node:assert/strict';
import {resolvePythonRuntime} from '../scripts/python-runtime.mjs';

const version=value=>({status:0,stdout:'Python '+value+'\n',stderr:''});
test('Python selection retains a configured executable path without shell splitting',()=>{
 const calls=[],env={PYTHON:'C:\\Program Files\\Python312\\python.exe'};
 const runtime=resolvePythonRuntime({env,platform:'win32',probe:(command,args,options)=>{
  calls.push({command,args});assert.equal(options.env,env);assert.equal(options.timeout,5000);assert.equal(options.maxBuffer,4096);assert.equal(options.shell,undefined);return version('3.12.9');
 }});
 assert.deepEqual(runtime,{command:env.PYTHON,prefixArgs:[],version:'3.12.9'});
 assert.deepEqual(calls,[{command:env.PYTHON,args:['--version']}]);
});
test('Windows falls back from missing or old python to the Python 3 launcher',()=>{
 for(const first of [{error:{code:'ENOENT'},status:null},version('3.11.9')]){
  const calls=[];
  const runtime=resolvePythonRuntime({env:{},platform:'win32',probe:(command,args)=>{calls.push([command,args]);return command==='python'?first:version('3.13.2');}});
  assert.deepEqual(runtime,{command:'py',prefixArgs:['-3'],version:'3.13.2'});
  assert.deepEqual(calls,[['python',['--version']],['py',['-3','--version']]]);
 }
});
test('Unix uses python3, accepts version output on stderr, and fails actionably for missing or unsupported Python',()=>{
 const runtime=resolvePythonRuntime({env:{},platform:'linux',probe:(command,args)=>{assert.equal(command,'python3');assert.deepEqual(args,['--version']);return {status:0,stdout:'',stderr:'Python 3.14.0\n'};}});
 assert.equal(runtime.version,'3.14.0');
 for(const result of [{error:{code:'ENOENT'},status:null},{error:{code:'ETIMEDOUT'},status:null},version('3.11.9'),version('2.7.18'),version('3.12.0rc1'),version('4.0.0'),{status:1,stdout:'Python 3.12.9'}]){
  assert.throws(()=>resolvePythonRuntime({env:{},platform:'linux',probe:()=>result}),/Python 3\.12\+.*set PYTHON/);
 }
});
test('Explicit PYTHON never silently falls back and malformed values do not spawn',()=>{
 let calls=0;
 assert.throws(()=>resolvePythonRuntime({env:{PYTHON:'chosen-python'},platform:'win32',probe:()=>{calls++;return version('3.11.9');}}),/Python 3\.12\+/);
 assert.equal(calls,1);
 for(const PYTHON of ['', ' ', 'python\n--unsafe', 'python\0other'])assert.throws(()=>resolvePythonRuntime({env:{PYTHON},probe:()=>{throw Error('Must not spawn');}}),/PYTHON must name/);
});
