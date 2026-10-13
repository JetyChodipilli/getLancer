import {execFileSync} from 'node:child_process';
import {build} from 'esbuild';
execFileSync(process.execPath,['node_modules/@angular/compiler-cli/bundles/src/bin/ngc.js','-p','tsconfig.json'],{stdio:'inherit',timeout:120000});
await build({entryPoints:['src/main.js'],bundle:true,format:'iife',minify:true,legalComments:'inline',outfile:'dist/native.js',target:['es2020'],define:{ngDevMode:'false',ngJitMode:'false','process.env.NODE_ENV':'"production"'}});
