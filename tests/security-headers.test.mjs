import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {readFileSync} from 'node:fs';
import {catalogueScriptSource,createFrontendNonce,frontendCsp,publisherFrameSource,secureFrontendResponse,trustedRecipeScriptSources} from '../lib/security-headers.ts';
const directiveSources=(policy,directive)=>new Set(policy.split(';').map(value=>value.trim().split(/\s+/)).find(([name])=>name===directive)?.slice(1)||[]);
test('connected Checkout hosts and strict isolation coexist',()=>{
 const demo=frontendCsp(false),connected=frontendCsp(true);
 for(const directive of ['script-src','frame-src','connect-src'])for(const origin of ['https://checkout.razorpay.com','https://api.razorpay.com'])assert.ok(!directiveSources(demo,directive).has(origin));
 for(const directive of ["frame-ancestors 'none'","object-src 'none'","base-uri 'self'","form-action 'self'"])assert.ok(connected.includes(directive));
 assert.ok(directiveSources(connected,'script-src').has('https://checkout.razorpay.com'));assert.ok(directiveSources(connected,'frame-src').has('https://api.razorpay.com'));
 assert.ok(!connected.includes('*'));assert.ok(!connected.includes('unsafe-eval'));
 assert.ok(directiveSources(demo,'frame-src').has('https://www.loom.com'));
 assert.ok(!directiveSources(demo,'connect-src').has('https://www.loom.com'));assert.ok(!directiveSources(demo,'script-src').has('https://www.loom.com'));
});

test('random request nonces authorize scripts while script attributes and untrusted inline code stay denied',()=>{
 const nonces=Array.from({length:128},()=>createFrontendNonce());assert.equal(new Set(nonces).size,nonces.length);
 for(const nonce of nonces){assert.equal(Buffer.from(nonce,'base64').length,32);assert.match(nonce,/^[A-Za-z0-9+/]{43}=$/);}
 const csp=frontendCsp(false,nonces[0]);
 assert.ok(directiveSources(csp,'script-src').has(`'nonce-${nonces[0]}'`));
 assert.doesNotMatch(csp,/script-src[^;]*'unsafe-inline'|script-src[^;]*'unsafe-eval'/);assert.match(csp,/script-src-attr 'none'/);
 assert.match(csp,/style-src 'self' 'unsafe-inline'/);
 for(const invalid of ['',"test' 'unsafe-inline",'a'.repeat(32)])assert.throws(()=>frontendCsp(false,invalid),/nonce/);
 const response=secureFrontendResponse(new Response('safe'),'https://app.example.test',false,nonces[0]);
 assert.equal(response.headers.get('content-security-policy'),csp);
});

test('only the six trusted catalogue script bodies are hashed; modified or uploaded bodies do not gain authority',()=>{
 const recipes=JSON.parse(readFileSync('backend/src/main/resources/catalog/components.json','utf8'));
 const hash=body=>`'sha256-${createHash('sha256').update(body).digest('base64')}'`;
 const expected=recipes.map(recipe=>{const scripts=[...recipe.files['index.html'].matchAll(/<script>([\s\S]*?)<\/script>/gi)];assert.equal(scripts.length,1);return hash(scripts[0][1]);});
 assert.equal(expected.length,6);assert.deepEqual([...trustedRecipeScriptSources],expected);
 const policy=frontendCsp(false),script=policy.split(';').find(value=>value.trim().startsWith('script-src '));
 assert.equal([...script.matchAll(/'sha256-[^']+'/g)].length,6);
 for(const source of expected)assert.ok(script.includes(source));
 const positive=recipes[0].files['index.html'].match(/<script>([\s\S]*?)<\/script>/i)[1];
 assert.ok(trustedRecipeScriptSources.includes(hash(positive)));
 assert.ok(!trustedRecipeScriptSources.includes(hash(positive+';self.uploadedExecuted=true;')));
 assert.ok(!trustedRecipeScriptSources.includes(hash('self.uploadedExecuted=true;')));
 const worker=readFileSync('worker/index.ts','utf8');assert.match(worker,/new HTMLRewriter\(\)\.on\('script'/);assert.match(worker,/!decodeURIComponent\(url\.pathname\)\.startsWith\('\/api\/'\)/);assert.doesNotMatch(worker,/await (?:response|secured)\.text\(/);
});

test('catalogue parsing respects HTML tag case and rejects extra or non-plain scripts',()=>{
 const body='const reviewed = true;';
 for(const tag of ['script','SCRIPT','ScRiPt'])assert.equal(catalogueScriptSource(`<${tag}>${body}</${tag}>`),body);
 assert.equal(catalogueScriptSource(`<SCRIPT >${body}</script >`),body);
 for(const html of [`<script>${body}</script><SCRIPT>self.extra=true</SCRIPT>`,`<script src="https://example.test/extra.js"></script>`,`<script type="module">${body}</script>`,`<scripture>${body}</scripture>`,'<script>unterminated'])assert.throws(()=>catalogueScriptSource(html),/one reviewed plain inline script/);
});

test('publisher frames use a fixed isolated operator suffix and the local exception is request-scoped',()=>{
 const hosted=publisherFrameSource('https://{id}.demos.publisher.test:8443/','https://marketplace.example.test');
 assert.equal(hosted,'https://*.demos.publisher.test:8443');
 const policy=frontendCsp(false,createFrontendNonce(),hosted);assert.match(policy,/frame-src[^;]*https:\/\/\*\.demos\.publisher\.test:8443/);assert.doesNotMatch(policy,/script-src[^;]*publisher|connect-src[^;]*publisher/);
 for(const host of ['localhost','127.0.0.1','[::1]'])assert.equal(publisherFrameSource('http://{id}.demo.localhost:8090',`http://${host}:3000`),'http://*.demo.localhost:8090');
 assert.equal(publisherFrameSource('', 'https://marketplace.example.test'),undefined);
 for(const template of ['https://*.publisher.test','https://{id}.test','https://user:pass@{id}.publisher.test','https://{id}.publisher.test/path','https://{id}.publisher.test?x=y','https://{id}.{id}.publisher.test','https://{id}.publisher.test; script-src *','http://{id}.publisher.test','http://{id}.demo.localhost:8091','https://{id}.demo.localhost'])assert.throws(()=>publisherFrameSource(template,'http://localhost:3000'));
 for(const app of ['https://marketplace.example.test','http://marketplace.example.test','https://localhost:3000'])assert.throws(()=>publisherFrameSource('http://{id}.demo.localhost:8090',app));
 assert.throws(()=>publisherFrameSource('https://{id}.marketplace.example.test','https://marketplace.example.test'));
 assert.throws(()=>publisherFrameSource('https://{id}.demos.example.test','https://marketplace.example.test'),/cookie domain/);
});
test('response security wraps all response types without losing cookies or private cache rules',async()=>{
 const original=new Response('protected',{status:403,headers:{'Content-Type':'application/json','Cache-Control':'no-store','Set-Cookie':'session=value; HttpOnly; Secure; SameSite=Lax'}});
 const secured=secureFrontendResponse(original,'https://example.com/_vinext/image',false);
 assert.equal(secured.status,403);assert.equal(await secured.text(),'protected');assert.equal(secured.headers.get('cache-control'),'no-store');assert.match(secured.headers.get('set-cookie'),/HttpOnly/);
 assert.equal(secured.headers.get('x-content-type-options'),'nosniff');assert.equal(secured.headers.get('referrer-policy'),'no-referrer');assert.equal(secured.headers.get('strict-transport-security'),'max-age=31536000');assert.match(secured.headers.get('content-security-policy'),/frame-ancestors 'none'/);
 assert.equal(secureFrontendResponse(new Response('local'),'http://localhost/',false).headers.get('strict-transport-security'),null);
});
