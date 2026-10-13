import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {readFile} from 'node:fs/promises';
import {dirname} from 'node:path';
import {build} from 'esbuild';
import {transformAsync} from '@babel/core';
import linkerPlugin from '@angular/compiler-cli/linker/babel';
execFileSync(process.execPath,['node_modules/@angular/compiler-cli/bundles/src/bin/ngc.js','-p','tsconfig.json'],{stdio:'inherit',timeout:120000});
let linkedFiles=0;
await build({entryPoints:['src/main.js'],bundle:true,format:'iife',minify:true,legalComments:'inline',outfile:'dist/native.js',target:['es2020'],define:{ngDevMode:'false',ngJitMode:'false','process.env.NODE_ENV':'"production"'},plugins:[{name:'trusted-angular-aot-linker',setup(builder){builder.onLoad({filter:/\.m?js$/},async({path})=>{
 if(!path.replaceAll('\\','/').includes('/node_modules/@angular/'))return;
 const source=await readFile(path,'utf8');if(!source.includes('ɵɵngDeclare'))return;
 const result=await transformAsync(source,{filename:path,configFile:false,babelrc:false,compact:false,sourceMaps:false,plugins:[[linkerPlugin,{linkerJitMode:false,unknownDeclarationVersionHandling:'error'}]]});
 assert(typeof result?.code==='string','Angular linker did not return compiled code');assert(!/\.ɵɵngDeclare[A-Za-z]+\(/.test(result.code),'Angular partial declaration remains unlinked');linkedFiles++;
 return {contents:result.code,loader:'js',resolveDir:dirname(path)};
 });}}]});
assert(linkedFiles>0,'The reviewed Angular partial libraries were not linked during the build');
