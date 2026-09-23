import assert from 'node:assert/strict';
import test from 'node:test';
import {default as worker} from '../dist/server/index.js';

const env={ASSETS:{fetch:async()=>new Response('Not found',{status:404})}};
const context={waitUntil(){},passThroughOnException(){}};
async function page(path){const response=await worker.fetch(new Request('https://preview.example'+path,{headers:{accept:'text/html'}}),env,context);return {response,html:await response.text()};}

test('project discovery renders six clearly illustrative projects',async()=>{
 const {response,html}=await page('/');assert.equal(response.status,200);
 assert.match(html,/Find software for your business/);assert.match(html,/Projects are illustrative/);
 assert.equal((html.match(/<article[^>]*class="project"/g)||[]).length,6);
 assert.equal(response.headers.get('referrer-policy'),'no-referrer');
});
test('technology and category filters work through URL state',async()=>{
 const react=await page('/?technology=React');assert.equal((react.html.match(/<article[^>]*class="project"/g)||[]).length,3);
 const inventory=await page('/?technology=React&category=Inventory');assert.equal((inventory.html.match(/<article[^>]*class="project"/g)||[]).length,1);assert.match(inventory.html,/Stockroom/);
});
test('a query with no matches has a recovery action',async()=>{
 const {html}=await page('/?q=nonexistentquantumservice');assert.match(html,/We couldn’t find an exact match/);assert.match(html,/Clear search and filters/);
});
test('project details and a matching builder profile resolve',async()=>{
 const project=await page('/products/stockroom');assert.equal(project.response.status,200);assert.match(project.html,/Builder’s contribution/);assert.match(project.html,/Illustrative project/);
 const builder=await page('/builders/leah-morgan');assert.equal(builder.response.status,200);assert.match(builder.html,/Leah Morgan/);assert.match(builder.html,/Client reviews/);
});
test('interactive demo identifies sample data and keeps real API authentication separate',async()=>{
 const {response,html}=await page('/preview/workspace');assert.equal(response.status,200);assert.match(html,/Demo workspace/);assert.match(html,/Sample data/);assert.match(html,/My requests/);assert.match(html,/Received inquiries/);assert.match(html,/Reset demo/);assert.match(html,/aria-orientation="horizontal"/);
});
test('a disconnected backend never fabricates an authenticated account',async()=>{
 const {response,html}=await page('/api/v1/me');assert.equal(response.status,503);assert.equal(JSON.parse(html).error.code,'BACKEND_NOT_CONFIGURED');
});
test('design preview is excluded from search indexing',async()=>{
 const robots=await page('/robots.txt');assert.match(robots.html,/Disallow: \//);
 const sitemap=await page('/sitemap.xml');assert.doesNotMatch(sitemap.html,/<url>/);
});

test('featured project stack remains available when collection filters have no matches',async()=>{
 const {html}=await page('/?q=nonexistentquantumservice');
 assert.equal((html.match(/class="stack-card stack-card-/g)||[]).length,3);
 assert.match(html,/Featured project demos/);
 assert.match(html,/href="#project-collection"/);
 assert.match(html,/No|We couldn’t find an exact match/);
 assert.match(html,/Remove Search: nonexistentquantumservice/);
});
