import assert from 'node:assert/strict';
import test, {before, after} from 'node:test';
import {createServer} from 'node:http';
import {once} from 'node:events';
import worker from '../dist/server/index.js';

// An HTTP fixture for the frontend contract only. Java/PostgreSQL and SMTP/S3
// behavior are verified separately by Maven integration tests and Docker CI.
const previous={BACKEND_URL:process.env.BACKEND_URL,DEMO_MODE:process.env.DEMO_MODE};
const env={ASSETS:{fetch:async()=>new Response('Not found',{status:404})}};
const ctx={waitUntil(){},passThroughOnException(){}};
let failure=false;
let items=[];
const requests=[];
const server=createServer((req,res)=>{
  requests.push(req.url);
  res.setHeader('Content-Type','application/json');
  if(failure){res.writeHead(503);res.end(JSON.stringify({error:{code:'SERVICE_UNAVAILABLE',message:'Unavailable'}}));return;}
  if(req.url.startsWith('/api/v1/products?'))res.end(JSON.stringify({items,totalItems:items.length,totalPages:items.length?1:0}));
  else if(req.url==='/api/v1/auth/providers')res.end(JSON.stringify({google:false,github:false}));
  else {res.writeHead(req.url==='/api/v1/me'?401:404);res.end(JSON.stringify({error:{code:'NOT_FOUND',message:'Not found'}}));}
});
before(async()=>{
  server.listen(0,'127.0.0.1');await once(server,'listening');
  process.env.BACKEND_URL=`http://127.0.0.1:${server.address().port}`;
  process.env.DEMO_MODE='true'; // A flag retained from the cloud preview must not hide real services.
});
after(async()=>{
  for(const [key,value] of Object.entries(previous)){if(value===undefined)delete process.env[key];else process.env[key]=value;}
  server.closeAllConnections();await new Promise(resolve=>server.close(resolve));
});
async function page(path){const r=await worker.fetch(new Request('https://app.example.test'+path,{headers:{accept:'text/html'}}),env,ctx);return {r,html:await r.text()};}
const noDemo=html=>assert.doesNotMatch(html,/Stockroom|Booklane|Leah Morgan|Northstar Studio|Demo workspace|Interactive team preview|Interactive V1.5 simulation|href="\/preview\//);
function renderedError({r,html}){
  // A streamed response can commit HTTP 200 before RSC reports its error.
  const chunks=[...html.matchAll(/__VINEXT_RSC_CHUNKS__\.push\((.*?)\)<\/script>/gs)].map(m=>JSON.parse(m[1])).join('');
  assert.ok(r.status>=500 || /^\w+:E\{.*"digest":/m.test(chunks) || /"error":"\$Z/.test(chunks),'An API failure must reach the error boundary.');
}

test('connected empty catalog stays empty and never substitutes sample products',async()=>{
  const {r,html}=await page('/');assert.equal(r.status,200);noDemo(html);
  assert.equal((html.match(/<article[^>]*class="project"/g)||[]).length,0);
  assert.ok(requests.some(path=>path.startsWith('/api/v1/products?')));
  assert.match(html,/href="\/workspace"/);
});

test('connected discovery renders only records returned by the API',async()=>{
  items=[{id:'real-id',slug:'actual-build',title:'Actual Builder Product',summary:'A database-backed listing',description:'Delivered software',category:'Inventory',technology:'React',builder:'Actual Builder',builderSlug:'actual-builder',projectType:'SAAS',availability:'AVAILABLE_NOW',contribution:'Built the product',approvalStatus:'APPROVED',lifecycleStatus:'ACTIVE',visibility:'PUBLIC',availableForSimilarWork:true,updatedAt:'2026-09-30T00:00:00Z'}];
  try{const {r,html}=await page('/');assert.equal(r.status,200);assert.match(html,/Actual Builder Product/);noDemo(html);}finally{items=[];}
});

test('configured backend failures produce errors, never demo listings or fake accounts',async()=>{
  failure=true;
  try{
    const home=await page('/');renderedError(home);noDemo(home.html);
    const account=await page('/api/v1/me');assert.equal(account.r.status,503);assert.equal(JSON.parse(account.html).error.code,'SERVICE_UNAVAILABLE');
    const project=await page('/products/stockroom');renderedError(project);assert.doesNotMatch(project.html,/Illustrative project/);
  }finally{failure=false;}
});

test('all V1 through V2 sample routes redirect to real workspaces when connected',async()=>{
  for(const [path,target] of [['/preview/workspace','/workspace'],['/preview/trust','/workspace/trust'],['/preview/teams','/workspace/teams']]){
    const {r,html}=await page(path);assert.equal(r.status,307,path);assert.equal(new URL(r.headers.get('location'),'https://app.example.test').pathname,target);noDemo(html);
  }
});

test('real login and team pages do not offer a sample-mode escape on API errors',async()=>{
  for(const path of ['/login','/signup','/teams','/workspace/teams']){const {r,html}=await page(path);assert.equal(r.status,200,path);noDemo(html);assert.doesNotMatch(html,/Try the sample workspace|Try the interactive team preview/);}
  const auth=await page('/api/v1/auth/providers');assert.equal(auth.r.status,200);assert.deepEqual(JSON.parse(auth.html),{google:false,github:false});
});
