import {test,expect,type Page} from '@playwright/test';
import {build} from 'esbuild';
import {readFileSync} from 'node:fs';
import {componentZip} from '../../lib/components';

const id='afc46138-fd02-4db4-b445-013086b924b2';
const recipe=JSON.parse(readFileSync('backend/src/main/resources/catalog/components.json','utf8'))[0];
const archive=Buffer.from(componentZip(recipe.files));
const pricing={amountMinor:null,enabled:false,salesEnabled:false,configured:false,currency:'INR',mode:'disabled',reason:'Collection is disabled.'};
const own={items:[{...recipe,id:'existing',status:'DRAFT',title:'Private existing draft'}],capacity:{free:3,purchased:0,used:0,limit:3},pricing};

async function render(page:Page,demo=false){
 const result=await build({stdin:{contents:`import {createRoot} from 'react-dom/client';import ComponentWorkspace from './app/components/component-workspace';createRoot(document.getElementById('root')).render(<ComponentWorkspace demo={${demo}}/>);`,resolveDir:process.cwd(),loader:'tsx'},bundle:true,write:false,format:'iife',jsx:'automatic',define:{'process.env.NODE_ENV':'"production"'},plugins:[{name:'fixture-link',setup(plugin){plugin.onResolve({filter:/^next\/link$/},()=>({path:'link',namespace:'fixture'}));plugin.onLoad({filter:/.*/,namespace:'fixture'},()=>({contents:'export default function Link({href,children,...props}){return <a href={href} {...props}>{children}</a>}',loader:'jsx',resolveDir:process.cwd()}));}}]});
 await page.route('**/component-upload-fixture',route=>route.fulfill({contentType:'text/html',body:'<!doctype html><html lang="en"><head><title>Component upload recovery</title></head><body><div id="root"></div></body></html>'}));
 await page.goto('/component-upload-fixture');await page.addScriptTag({content:result.outputFiles[0].text});
}
function reads(path:string){if(path==='/api/v1/me')return{roles:['DEVELOPER']};if(path==='/api/v1/me/components')return own;return{items:[],hasMore:false};}
async function draft(page:Page){
 await page.getByRole('button',{name:'New component',exact:true}).click();const dialog=page.getByRole('dialog');
 await dialog.getByLabel('Component title',{exact:true}).fill('Original upload for recovery');await dialog.getByLabel('Summary',{exact:true}).fill('A complete source ZIP with original attribution.');await dialog.getByRole('button',{name:'Continue to contribution',exact:true}).click();
 await dialog.getByLabel('Your contribution & intended use',{exact:true}).fill('I implemented the original interaction and preserve MIT attribution.');await dialog.getByRole('checkbox',{name:'Upload my self-contained frontend source',exact:true}).check();
 await dialog.getByLabel('Source ZIP',{exact:true}).setInputFiles({name:'original.zip',mimeType:'application/zip',buffer:archive});await dialog.getByLabel('Synthetic preview interaction',{exact:true}).fill('Try the original synthetic interaction.');await dialog.getByRole('checkbox',{name:/I preserve/}).check();return dialog;
}

test('source failure retains file and version diagnostics, then retries the saved draft without duplication',async({page})=>{
 const writes:string[]=[];let uploads=0;const reference='7aed64cc-a3f3-454f-94d4-5dc9d8a54201';
 await page.route('**/api/v1/**',route=>{
  const request=route.request(),path=new URL(request.url()).pathname;
  if(request.method()==='GET')return route.fulfill({json:reads(path)});
  writes.push(request.method()+' '+path);
  if(path.endsWith('/source')){
   uploads++;expect(request.headers()['content-type']).toMatch(/^multipart\/form-data; boundary=/);
   expect(request.postDataBuffer()!.includes(archive)).toBe(true);
   return route.fulfill(uploads===1?{status:409,json:{error:{code:'VERSION_EXISTS',message:'Choose a new immutable version.',requestId:reference,fieldErrors:{version:'Choose a new immutable version.'}}}}:{json:{id,version:'1.0.1'}});
  }
  return route.fulfill({json:{id}});
 });
 await render(page);const dialog=await draft(page);await dialog.getByRole('button',{name:'Save component draft',exact:true}).click();
 const alert=dialog.getByRole('alert');await expect(alert).toContainText('Your draft details were saved.');await expect(alert).toContainText(reference);expect((await alert.textContent())!.split(reference)).toHaveLength(2);
 await dialog.getByRole('link',{name:'version: Choose a new immutable version.',exact:true}).click();const version=dialog.getByLabel('Release version',{exact:true});await expect(version).toBeFocused();await expect(version).toHaveAttribute('aria-invalid','true');
 expect(await dialog.getByLabel('Source ZIP',{exact:true}).evaluate((input:HTMLInputElement)=>input.files?.[0]?.name)).toBe('original.zip');await version.fill('1.0.1');
 await dialog.getByRole('button',{name:'Save component draft',exact:true}).click();await expect(dialog).not.toBeVisible();await expect(page.getByRole('status')).toContainText('Draft saved.');
 expect(writes).toEqual(['POST /api/v1/me/components','POST /api/v1/me/components/'+id+'/source','PATCH /api/v1/me/components/'+id,'POST /api/v1/me/components/'+id+'/source']);
});

for(const status of [401,403])test('source authority denial '+status+' clears private records and closes the editor',async({page})=>{
 await page.route('**/api/v1/**',route=>{const request=route.request(),path=new URL(request.url()).pathname;return request.method()==='GET'?route.fulfill({json:reads(path)}):path.endsWith('/source')?route.fulfill({status,json:{error:{code:status===401?'UNAUTHENTICATED':'FORBIDDEN',message:'Current builder authentication is required.'}}}):route.fulfill({json:{id}});});
 await render(page);await expect(page.getByRole('heading',{name:'Private existing draft',exact:true})).toBeVisible();const dialog=await draft(page);await dialog.getByRole('button',{name:'Save component draft',exact:true}).click();
 await expect(dialog).not.toBeVisible();await expect(page.getByRole('heading',{name:'Private existing draft',exact:true})).toHaveCount(0);await expect(page.getByRole('alert')).toContainText('Current builder authentication is required.');await expect(page.getByRole('link',{name:'Log in',exact:true})).toBeVisible();
});

test('failed refresh after an accepted upload preserves the editor and never reports false success',async({page})=>{
 let uploaded=false,failedRefresh=false,creates=0;
 await page.route('**/api/v1/**',route=>{
  const request=route.request(),path=new URL(request.url()).pathname;
  if(request.method()==='GET'){
   if(path==='/api/v1/me/components'&&uploaded&&!failedRefresh){failedRefresh=true;return route.fulfill({status:503,json:{error:{code:'TEMPORARILY_UNAVAILABLE',message:'Refresh is temporarily unavailable.'}}});}
   return route.fulfill({json:reads(path)});
  }
  if(path==='/api/v1/me/components')creates++;if(path.endsWith('/source'))uploaded=true;return route.fulfill({json:{id}});
 });
 await render(page);const dialog=await draft(page);await dialog.getByRole('button',{name:'Save component draft',exact:true}).click();await expect(dialog).toBeVisible();await expect(dialog.getByRole('alert')).toContainText('Refresh is temporarily unavailable.');await expect(page.getByText('Draft saved. Submit its exact source and attribution for review.',{exact:true})).toHaveCount(0);
 await dialog.getByRole('button',{name:'Save component draft',exact:true}).click();await expect(dialog).not.toBeVisible();expect(creates).toBe(1);
});

test('sample mode explains the connected-account upload requirement before data entry',async({page})=>{
 await render(page,true);await page.getByRole('button',{name:'New component',exact:true}).click();await expect(page.getByRole('dialog').getByText('This preview saves sample recipe drafts only. Uploading your own ZIP requires a connected builder account.',{exact:true})).toBeVisible();
});
