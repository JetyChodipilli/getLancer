/** Capture only original, source-pinned catalogue recipes. No network is allowed. */
import {readFileSync, mkdirSync, writeFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {chromium} from '@playwright/test';
const recipes=JSON.parse(readFileSync('backend/src/main/resources/catalog/components.json','utf8'));
const hash=bytes=>createHash('sha256').update(bytes).digest('hex');
const directory='public/component-covers';mkdirSync(directory,{recursive:true});
const browser=await chromium.launch({headless:true,...(process.env.COVER_BROWSER?{executablePath:process.env.COVER_BROWSER}:{}),args:['--no-sandbox']});
const manifest={};
try {
 const page=await browser.newPage({viewport:{width:1000,height:640},deviceScaleFactor:1,reducedMotion:'reduce'});
 await page.route('**/*',route=>route.abort());
 for(const recipe of recipes){
  if(!/^[a-z0-9-]+$/.test(recipe.slug)||hash(recipe.files['index.html'])!==recipe.sha256)throw Error('Recipe source pin mismatch');
  await page.setContent(recipe.files['index.html'],{waitUntil:'load'});
  await page.evaluate(()=>document.fonts.ready);
  const file=recipe.slug+'.jpg';
  const bytes=await page.screenshot({path:directory+'/'+file,type:'jpeg',quality:82});
  manifest[recipe.slug]={file,sourceSha256:recipe.sha256,imageSha256:hash(bytes)};
 }
 writeFileSync(directory+'/index.json',JSON.stringify(manifest,null,2)+'\n');
 console.log('COMPONENT_COVERS_OK '+recipes.length+' source-pinned, network-isolated captures');
} finally {await browser.close();}
