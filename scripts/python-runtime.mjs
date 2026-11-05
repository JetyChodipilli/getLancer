import {spawnSync} from 'node:child_process';

// PYTHON is one executable/path, including spaces; it is never a shell command.
// A selected launcher's prefix is retained for every later Python invocation.
export function resolvePythonRuntime({env=process.env,platform=process.platform,probe=spawnSync}={}){
 const configured=env.PYTHON;
 if(configured!==undefined&&(typeof configured!=='string'||!configured.trim()||/[\0\r\n]/.test(configured)))throw Error('PYTHON must name one Python 3.12+ executable, without command arguments.');
 const candidates=configured!==undefined?[[configured,[]]]:platform==='win32'?[['python',[]],['py',['-3']],['python3',[]]]:[['python3',[]]];
 for(const [command,prefixArgs] of candidates){
  let result;
  try{result=probe(command,[...prefixArgs,'--version'],{env,encoding:'utf8',timeout:5000,maxBuffer:4096,windowsHide:true});}catch{continue;}
  if(result.error||result.status!==0)continue;
  const match=/^Python (3)\.(\d+)\.(\d+)\s*$/.exec((result.stdout||result.stderr||'').trim());
  if(match&&Number(match[2])>=12)return {command,prefixArgs,version:match[1]+'.'+match[2]+'.'+match[3]};
 }
 throw Error('Python 3.12+ is required on this computer for local material verification. Install it and add it to PATH, or set PYTHON to its executable path (no arguments). On Windows the Python launcher py -3 is also supported.');
}
