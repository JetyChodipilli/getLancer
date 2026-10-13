import {readFile} from 'node:fs/promises';
import {build} from 'esbuild';
import {compile} from 'svelte/compiler';
await build({entryPoints:['src/main.js'],bundle:true,format:'iife',minify:true,legalComments:'inline',outfile:'dist/native.js',target:['es2020'],conditions:['svelte','browser','production'],define:{'process.env.NODE_ENV':'"production"'},plugins:[{name:'original-svelte-compiler',setup(builder){builder.onLoad({filter:/\.svelte$/},async({path})=>({contents:compile(await readFile(path,'utf8'),{filename:'src/App.svelte',generate:'client',dev:false,css:'external'}).js.code,loader:'js',resolveDir:new URL('./src',import.meta.url).pathname}));}}]});
