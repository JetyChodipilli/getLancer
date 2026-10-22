import {test,expect,type Page} from '@playwright/test';
import {build} from 'esbuild';
import {createHash} from 'node:crypto';
import {readFileSync} from 'node:fs';
import {execFileSync} from 'node:child_process';
const recipe=JSON.parse(readFileSync('backend/src/main/resources/catalog/components.json','utf8'))[0];

async function fixture(page:Page,module:string,props:unknown,editor=false){
 const errors:string[]=[];page.on('pageerror',error=>errors.push(error.message));
 const result=await build({stdin:{contents:`import React from 'react';import {createRoot} from 'react-dom/client';import Component from './app/components/${module}';import {ApiError} from './lib/api';function Fixture(){const [error,setError]=React.useState(null);return <Component {...${JSON.stringify(props)}} ${editor?`error={error} onSave={event=>{event.preventDefault();setError(new ApiError('Correct the source details.','VALIDATION_ERROR',400,undefined,{title:'Use the reviewed title.',file:'Choose the reviewed ZIP.'}));}}`:''}/>;}createRoot(document.getElementById('root')).render(<Fixture/>);`,resolveDir:process.cwd(),loader:'tsx'},bundle:true,write:false,format:'iife',define:{'process.env.NODE_ENV':'"production"'},plugins:[{name:'fixture-link',setup(plugin){plugin.onResolve({filter:/^next\/link$/},()=>({path:'link',namespace:'fixture'}));plugin.onLoad({filter:/.*/,namespace:'fixture'},()=>({contents:'import React from "react";export default function Link({href,children,...props}){return <a href={href} {...props}>{children}</a>}',loader:'jsx',resolveDir:process.cwd()}));}}]});
 await page.route('**/requirements-fixture.js',route=>route.fulfill({contentType:'text/javascript',body:result.outputFiles[0].text}));
 await page.route('**/requirements-fixture',route=>route.fulfill({contentType:'text/html',body:'<!doctype html><html lang="en"><head><title>Acceptance regression</title></head><body><div id="root"></div><script src="/requirements-fixture.js"></script></body></html>'}));
 await page.goto('/requirements-fixture');return errors;
}

test('copy failure clears a previous success label',async({page})=>{
 const errors=await fixture(page,'component-library',{item:recipe,preview:true});
 await page.evaluate(()=>{let attempts=0;Object.defineProperty(navigator,'clipboard',{configurable:true,value:{writeText:async()=>{if(++attempts>1)throw Error('denied');}}});});
 await page.getByRole('tab',{name:'Source',exact:true}).click();await page.getByRole('button',{name:'Copy file',exact:true}).click();
 await page.getByRole('button',{name:'Copied',exact:true}).click();await expect(page.getByRole('alert')).toContainText('Copy is unavailable');
 await expect(page.getByRole('button',{name:'Copy file',exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'Copied',exact:true})).toHaveCount(0);expect(errors).toEqual([]);
});

test('uploaded downloads preserve reviewed ZIP bytes and reject a changed archive',async({page})=>{
 const archive=execFileSync('python3',['-c','import sys,io,json,zipfile; out=io.BytesIO(); z=zipfile.ZipFile(out,"w",zipfile.ZIP_DEFLATED); [z.writestr(k,v) for k,v in json.load(sys.stdin).items()]; z.comment=b"reviewed archive identity"; z.close(); sys.stdout.buffer.write(out.getvalue())'],{input:JSON.stringify(recipe.files)});
 const sha=createHash('sha256').update(archive).digest('hex');let corrupt=false,downloads=0;
 await page.route('**/api/v1/me/saved-components**',route=>route.fulfill({contentType:'application/json',body:JSON.stringify({items:[]})}));
 await page.route('**/api/v1/components/*/source?*',route=>{expect(new URL(route.request().url()).searchParams.get('sha256')).toBe(sha);return route.fulfill({contentType:'application/zip',body:corrupt?Buffer.from('changed archive'):archive});});
 const errors=await fixture(page,'component-library',{item:{...recipe,uploaded:true,archiveSha256:sha},preview:false});page.on('download',()=>downloads++);
 const downloaded=page.waitForEvent('download');await page.getByRole('button',{name:'Download source',exact:true}).click();const path=await (await downloaded).path();expect(readFileSync(path!)).toEqual(archive);
 corrupt=true;await page.getByRole('button',{name:'Download source',exact:true}).click();await expect(page.getByRole('alert')).toContainText('Archive integrity could not be verified');expect(downloads).toBe(1);expect(errors).toEqual([]);
});

test('draft error links reveal the right step and focus associated invalid fields',async({page})=>{
 const errors=await fixture(page,'component-draft-editor',{entry:null,busy:false},true);
 await page.getByLabel('Component title',{exact:true}).fill('Original component');await page.getByLabel('Summary',{exact:true}).fill('A complete synthetic source summary.');await page.getByRole('button',{name:'Continue to contribution',exact:true}).click();
 await page.getByLabel('Your contribution & intended use',{exact:true}).fill('I implemented the original behavior and preserved attribution.');await page.getByRole('checkbox',{name:/I preserve/}).check();await page.getByRole('button',{name:'Save component draft',exact:true}).click();
 await page.getByRole('link',{name:'title: Use the reviewed title.',exact:true}).click();const title=page.getByLabel('Component title',{exact:true});await expect(title).toBeVisible();await expect(title).toBeFocused();await expect(title).toHaveAttribute('aria-invalid','true');await expect(title).toHaveAttribute('aria-describedby','title-error');
 await page.getByRole('link',{name:'file: Choose the reviewed ZIP.',exact:true}).click();const file=page.getByLabel('Source ZIP',{exact:true});await expect(file).toBeVisible();await expect(file).toBeFocused();await expect(file).toHaveAttribute('aria-describedby','file-error');expect(errors).toEqual([]);
});
