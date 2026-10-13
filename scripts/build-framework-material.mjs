/** Reviewed native examples only. Never compile caller-controlled or uploaded source. */
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {constants} from 'node:fs';
import {open,lstat,readdir,readFile,writeFile,mkdir,rm} from 'node:fs/promises';
import {dirname,join,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {execFileSync} from 'node:child_process';
import {archiveFor} from './build-data-ai-material.mjs';
export const root=resolve(dirname(fileURLToPath(import.meta.url)),'..');
export const frameworkIds=['vue','svelte','angular'];
export const catalogue=[
 {id:'vue',label:'Vue',version:'3.5.43',title:'Project brief',summary:'A reactive form with validation and local save feedback.',primaryFile:'src/App.vue',compiler:'@vue/compiler-sfc 3.5.43',mount:'<div id="app"></div>'},
 {id:'svelte',label:'Svelte',version:'5.57.2',title:'Build queue',summary:'A filtered list with reactive completion state.',primaryFile:'src/App.svelte',compiler:'svelte/compiler 5.57.2',mount:'<div id="app"></div>'},
 {id:'angular',label:'Angular',version:'20.3.33',title:'Release checklist',summary:'A standalone component with signals and checked-state feedback.',primaryFile:'src/app.ts',compiler:'@angular/compiler-cli 20.3.33 · AOT',mount:'<native-preview></native-preview>'},
];
export const sha=bytes=>createHash('sha256').update(bytes).digest('hex');
const cspHash=bytes=>"'sha256-"+createHash('sha256').update(bytes).digest('base64')+"'";
export async function safeRead(path,limit=262144){
 const file=await open(path,constants.O_RDONLY|constants.O_NOFOLLOW|constants.O_NONBLOCK);
 try{const before=await file.stat();assert(before.isFile()&&before.size<=limit,'Unsafe or oversized framework file');const buffer=Buffer.alloc(limit+1);let size=0;while(size<buffer.length){const {bytesRead}=await file.read(buffer,size,buffer.length-size,size);if(!bytesRead)break;size+=bytesRead;}assert(size<=limit,'Framework file exceeds byte budget');const after=await file.stat();assert(before.dev===after.dev&&before.ino===after.ino&&before.size===after.size&&before.mtimeMs===after.mtimeMs&&before.ctimeMs===after.ctimeMs,'Framework file changed while reading');return buffer.subarray(0,size);}finally{await file.close();}
}
export async function frameworkSource(id){
 assert(frameworkIds.includes(id),'Unknown framework');const base=join(root,'examples/frameworks',id),files=[];for(const directory of [join(root,'examples'),join(root,'examples/frameworks')]){const stat=await lstat(directory);assert(stat.isDirectory()&&!stat.isSymbolicLink(),'Unsafe framework ancestor');}
 async function walk(relative=''){const path=join(base,relative);assert((await lstat(path)).isDirectory()&&!(await lstat(path)).isSymbolicLink(),'Unsafe framework directory');for(const name of (await readdir(path)).sort()){if(['node_modules','dist','.compiled'].includes(name))continue;const entry=relative?relative+'/'+name:name;assert(/^[a-zA-Z0-9_.\/-]{1,99}$/.test(entry)&&!entry.split('/').includes('..'),'Unsafe source name');const stat=await lstat(join(base,entry));assert(!stat.isSymbolicLink(),'Framework source symlink rejected');if(stat.isDirectory()){assert(entry==='src','Unexpected framework source directory');await walk(entry);}else {assert(stat.isFile(),'Non-file framework source');files.push([entry,await safeRead(join(base,entry))]);}}}
 await walk();assert(files.length>=9&&files.length<=20,'Framework source inventory budget');files.sort(([a],[b])=>a.localeCompare(b,'en'));
 const get=name=>files.find(([path])=>path===name)?.[1];for(const name of ['package.json','package-lock.json','LICENSE','README.md','THIRD_PARTY_NOTICES.md','THIRD_PARTY_LICENSES.md','build.mjs',catalogue.find(item=>item.id===id).primaryFile])assert(get(name),'Missing framework source '+name);
 const pkg=JSON.parse(get('package.json')),lock=JSON.parse(get('package-lock.json'));assert.deepEqual(pkg.scripts,{build:'node build.mjs'},'Unexpected framework lifecycle script');assert.equal(pkg.license,'MIT');assert.equal(lock.lockfileVersion,3);assert.equal(lock.name,pkg.name);for(const section of ['dependencies','devDependencies']){assert.deepEqual(lock.packages[''][section],pkg[section],'Framework lock root drift');for(const version of Object.values(pkg[section]))assert(/^\d+\.\d+\.\d+$/.test(version),'Framework dependency must be exact pinned');}
 for(const [name,dependency] of Object.entries(lock.packages).filter(([name])=>name)){assert(name.startsWith('node_modules/')&&/^https:\/\/registry\.npmjs\.org\//.test(dependency.resolved)&&/^sha512-[A-Za-z0-9+/]+=*$/.test(dependency.integrity),'Unpinned or foreign dependency');}
 const sourceSha256=sha(Buffer.from(files.map(([name,bytes])=>name+'\0'+sha(bytes)+'\n').join('')));return {base,files,sourceSha256};
}
export function nativeDocument(item,script,style,licenses=''){
 assert(Buffer.byteLength(script)<=1048576&&Buffer.byteLength(style)<=65536,'Native preview exceeds byte budget');assert(!/<\/script/i.test(script)&&!/<\/style/i.test(style),'Native bundle contains unsafe HTML delimiter');
 // Compiler output is fully bundled; classic IIFE needs no ESM imports, eval or CDN.
 assert(!/\b(?:eval\(|new Function\(|import\s*\()/.test(script),'Unexpected native runtime code loading');
 assert(typeof licenses==='string'&&Buffer.byteLength(licenses)<=65536&&!/-->|<!--/.test(licenses),'Unsafe runtime license comment');const scriptHash=cspHash(script),styleHash=cspHash(style);
 const policy=`default-src 'none'; script-src ${scriptHash}; script-src-attr 'none'; style-src ${styleHash}; style-src-attr 'none'; img-src 'none'; font-src 'none'; connect-src 'none'; frame-src 'none'; worker-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'self'; sandbox allow-scripts`;
 const html='<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>'+item.label+' · '+item.title+'</title><style>'+style+'</style></head><body>'+item.mount+'<!-- Bundled runtime licenses\n'+licenses+'\n--><script>'+script+'</script></body></html>';
 return {html,policy,previewSha256:sha(html),scriptHash,styleHash};
}
export async function buildFrameworkMaterial({native=false,verify=false}={}){
 const materialPath=join(root,'app/components/frameworks/material.json'),indexPath=join(root,'app/components/frameworks/index.json');let existing;
 try{existing=JSON.parse(await readFile(materialPath,'utf8'));}catch{assert(native,'Native material is absent; run the trusted native builder first');}
 const entries=[];
 for(const item of catalogue){const before=await frameworkSource(item.id);let compiled;
  if(native){await rm(join(before.base,'dist'),{recursive:true,force:true});await rm(join(before.base,'.compiled'),{recursive:true,force:true});execFileSync('npm',['ci','--ignore-scripts','--no-audit','--no-fund'],{cwd:before.base,stdio:'inherit',timeout:180000});execFileSync(process.execPath,['build.mjs'],{cwd:before.base,stdio:'inherit',timeout:120000});compiled=nativeDocument(item,(await safeRead(join(before.base,'dist/native.js'),1048576)).toString('utf8'),(await safeRead(join(before.base,'dist/native.css'),65536)).toString('utf8'),before.files.find(([name])=>name==='THIRD_PARTY_LICENSES.md')[1].toString('utf8'));}
  else {const stored=existing?.items?.find(entry=>entry.id===item.id);assert(stored,'Native preview is missing');const script=stored.html.match(/<script>([\s\S]*?)<\/script>/)?.[1],style=stored.html.match(/<style>([\s\S]*?)<\/style>/)?.[1];assert(typeof script==='string'&&typeof style==='string','Native document is malformed');compiled=nativeDocument(item,script,style,before.files.find(([name])=>name==='THIRD_PARTY_LICENSES.md')[1].toString('utf8'));assert.equal(compiled.html,stored.html,'Native document contains unreviewed markup');assert.equal(stored.sourceSha256,before.sourceSha256,'Native source changed; trusted recompilation required');}
  const after=await frameworkSource(item.id);assert.equal(before.sourceSha256,after.sourceSha256,'Framework source changed during native build');const archive=archiveFor(before.files),sourceUrl='/components/frameworks/source/'+item.id,previewUrl='/components/frameworks/preview/'+item.id;
  entries.push({...item,mount:undefined,...compiled,sourceSha256:before.sourceSha256,archiveSha256:sha(archive),archiveBase64:archive.toString('base64'),sourceUrl,previewUrl,files:before.files.map(([name,bytes])=>({name,sha256:sha(bytes),bytes:bytes.length})),source:before.files.find(([name])=>name===item.primaryFile)[1].toString('utf8')});
 }
 const material={schemaVersion:'getlancer-native-framework-material-v1',items:entries},index={schemaVersion:'getlancer-native-framework-index-v1',items:entries.map(({html,policy,scriptHash,styleHash,archiveBase64,source,mount,...entry})=>entry)};
 const outputs=[[materialPath,JSON.stringify(material,null,2)+'\n'],[indexPath,JSON.stringify(index,null,2)+'\n']];
 for(const [path,bytes] of outputs){if(verify)assert.equal(await readFile(path,'utf8'),bytes,'Native preview material drift');else{await mkdir(dirname(path),{recursive:true});await writeFile(path,bytes);}}
 return {frameworks:3,nativeBuild:native,sourceSha256:entries.map(({id,sourceSha256,previewSha256})=>({id,sourceSha256,previewSha256}))};
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 try{assert(process.argv.slice(2).every(arg=>['--native','--verify'].includes(arg)),'Unknown framework builder argument');console.log('FRAMEWORK_MATERIAL_OK '+JSON.stringify(await buildFrameworkMaterial({native:process.argv.includes('--native'),verify:process.argv.includes('--verify')})));}catch(error){console.error('FRAMEWORK_MATERIAL_REJECTED '+error.message);process.exitCode=1;}
}
