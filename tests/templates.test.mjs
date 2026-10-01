import test from 'node:test';
import assert from 'node:assert/strict';
import {createTemplatePreview,previewTemplateCatalog,previewTemplateDetail} from '../lib/template-preview.ts';
import {eligibleTemplateDownload,templateMinor,templateFilterParams} from '../lib/templates.ts';

test('INR prices preserve paise exactly and reject overprecision, unsafe values and missing rupee minimum',()=>{
 assert.equal(templateMinor('4900.25'),490025);assert.equal(templateMinor('1.01'),101);assert.equal(templateMinor('10000000.00'),1000000000);
 for(const value of ['1.001','-2','0.99','1e4','10000000.01','9999999999999999999999',''])assert.throws(()=>templateMinor(value));
 assert.equal(templateFilterParams({q:'roles & reviews',category:'BUSINESS',technology:'REACT'},2).get('q'),'roles & reviews');
});
test('sample source exercise remains isolated with fetch forbidden and output copies protected',()=>{
 const original=globalThis.fetch;globalThis.fetch=()=>{throw Error('A preview contacted the network.');};try{const a=createTemplatePreview(),b=createTemplatePreview(),initial=a.list()[0];initial.title='Changed outside';assert.equal(a.list()[0].title,'Approval portal starter');const purchase=a.buy(initial.id);assert.equal(purchase.mode,'test');assert.equal(purchase.downloadAvailable,false);assert.equal(b.purchases().length,0);a.reset();assert.equal(a.purchases().length,0);assert.equal(previewTemplateCatalog().items.length,2);assert.equal(previewTemplateDetail('missing'),undefined);}finally{globalThis.fetch=original;}
});
test('source review needs rights consent and preserves purchased release snapshots through new versions and archive',()=>{
 const sample=createTemplatePreview(),template=sample.list()[0],purchase=sample.buy(template.id);assert.throws(()=>sample.upload(template.id,{version:'1.1.0',releaseNotes:'New accessible settings flow.',rightsConsent:false}));
 const version=sample.upload(template.id,{version:'1.1.0',releaseNotes:'New accessible settings flow.',rightsConsent:true});assert.throws(()=>sample.submit(template.id,version.id,false));sample.submit(template.id,version.id,true);sample.review(template.id,version.id,'APPROVE','Ownership evidence and immutable package checked.');assert.equal(sample.list()[0].version,'1.1.0');assert.equal(sample.purchases()[0].version,purchase.version);assert.equal(sample.purchases()[0].sha256,purchase.sha256);sample.archive(template.id);assert.throws(()=>sample.buy(template.id));assert.equal(sample.purchases()[0].licenseTerms,purchase.licenseTerms);
});
test('download UI requires explicit live entitlement and does not infer capture, refund or dispute access',()=>{
 const purchase=createTemplatePreview().buy('sample-template-portal');assert.equal(eligibleTemplateDownload({...purchase,downloadAvailable:true}),false);
 const live={...purchase,mode:'live',downloadAvailable:true};assert.equal(eligibleTemplateDownload(live),true);assert.equal(eligibleTemplateDownload({...live,dispute:{status:'RESUMED',reason:'Concern resolved with documented source correction.'}}),true);for(const state of [{downloadAvailable:false},{refundedMinor:1},{status:'AUTHORIZED'},{dispute:{status:'OPEN',reason:'Open review concern'}},{dispute:{status:'REVOKED',reason:'Confirmed license violation'}}])assert.equal(eligibleTemplateDownload({...live,...state}),false);
});
test('a source concern pauses sample access and never fabricates a refund',()=>{
 const sample=createTemplatePreview(),purchase=sample.buy('sample-template-portal');sample.dispute(purchase.id,'The handover notes need a clarification.');const changed=sample.purchases()[0];assert.equal(changed.status,'DISPUTED');assert.equal(changed.dispute.status,'OPEN');assert.equal(changed.refundedMinor,0);assert.throws(()=>sample.dispute(purchase.id,'Another concern'));assert.equal(eligibleTemplateDownload(changed),false);
});
test('production build renders the demo source catalog and license detail as labelled HTML without checkout scripts',async()=>{
 const {default:worker}=await import('../dist/server/index.js');
 const environment={DEMO_MODE:'true',ASSETS:{fetch:async()=>new Response('Not found',{status:404})}},context={waitUntil(){},passThroughOnException(){}};
 for(const [path,title] of [['/templates','A head start, with the source.'],['/templates/sample-approval-portal','Approval portal starter']]){
  const response=await worker.fetch(new Request('http://localhost'+path,{headers:{accept:'text/html'}}),environment,context);assert.equal(response.status,200);const html=await response.text();assert.ok(html.includes(title));assert.ok(html.includes('Frontend preview'));assert.ok(!html.includes('src="https://checkout.razorpay.com'));
 }
});
