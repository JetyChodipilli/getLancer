// Read-only verification of the production frontend against disposable Java CI.
import assert from 'node:assert/strict';
assert.equal(process.env.CI,'true');
assert.match(process.env.COMPOSE_PROJECT_NAME||'',/^getlancer-ci-[0-9]+$/);
process.env.BACKEND_URL='http://localhost:8080';
process.env.DEMO_MODE='true'; // Connecting Java must supersede a preview flag.
const {default:worker}=await import('../dist/server/index.js');
const env={ASSETS:{fetch:async()=>new Response('Not found',{status:404})}};
const ctx={waitUntil(){},passThroughOnException(){}};
const origin='http://localhost:3000';
const page=path=>worker.fetch(new Request(origin+path,{headers:{accept:'text/html'}}),env,ctx);

const direct=await fetch(process.env.BACKEND_URL+'/api/v1/products?size=12');
assert.equal(direct.status,200);
const catalog=await direct.json();
const proxied=await page('/api/v1/products?size=12');
assert.equal(proxied.status,200);
assert.deepEqual(await proxied.json(),catalog,'Frontend proxy must return the real Java response.');
const home=await page('/');assert.equal(home.status,200);
const html=await home.text();
assert.doesNotMatch(html,/Stockroom|Booklane|Leah Morgan|Northstar Studio|Projects are illustrative|href="\/preview\//);
assert.equal((html.match(/<article[^>]*class="project"/g)||[]).length,catalog.items.length,'Rendered listing count must equal the real database result.');
assert.equal((await page('/api/v1/me')).status,401,'No fabricated authenticated account.');
const providers=await page('/api/v1/auth/providers');assert.equal(providers.status,200);
assert.deepEqual(await providers.json(),await fetch(process.env.BACKEND_URL+'/api/v1/auth/providers').then(r=>r.json()));
for(const [path,target] of [['/preview/workspace','/workspace'],['/preview/trust','/workspace/trust'],['/preview/teams','/workspace/teams']]){
  const response=await page(path);assert.equal(response.status,307,path);
  assert.equal(new URL(response.headers.get('location'),origin).pathname,target);
}
console.log(`Connected production frontend verified against Java/PostgreSQL: ${catalog.items.length} actual listings, exact proxy responses, anonymous 401 and no sample workspaces.`);
