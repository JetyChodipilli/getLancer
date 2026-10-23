// Synthetic disposable-CI recovery fixtures. Never live admission or payment evidence.
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';

const hash=value=>createHash('sha256').update(value).digest('hex');
const sql=value=>"'"+String(value).replaceAll("'","''")+"'";
const object=value=>sql(JSON.stringify(value))+'::jsonb';
const canonical=value=>Array.isArray(value)?value.map(canonical):value&&typeof value==='object'?Object.fromEntries(Object.keys(value).sort().map(key=>[key,canonical(value[key])])):value;
const contentHash=value=>hash(JSON.stringify(canonical(value)));
const id=number=>'70000000-0000-4000-8000-'+String(number).padStart(12,'0');
export const restoreIds=Object.freeze({seller:id(1),buyer:id(2),product:id(3),freeProduct:id(4),component:id(5),freePackage:id(6),freeRelease:id(7),paidRelease:id(8),template:id(9),version:id(10),purchase:id(11),run:id(12),start:id(13),stop:id(14),preview:id(15),epoch:id(16),idempotency:id(17)});

export function restoreFixture(){
 const files={'index.html':'<!doctype html><html lang="en"><title>Recovery fixture</title><main>Disposable source recovery fixture</main></html>','README.md':'# Recovery fixture\nSynthetic source and immutable licence recovery only.','LICENSE':'MIT License. Preserve the copyright and permission notice.'};
 // Python's standard library is already a CI prerequisite; fix ZIP timestamps for deterministic bytes.
 const archive=execFileSync('python3',['-c','import io,json,sys,zipfile; b=io.BytesIO(); z=zipfile.ZipFile(b,"w",zipfile.ZIP_DEFLATED); [z.writestr(zipfile.ZipInfo(k,(2026,1,1,0,0,0)),v) for k,v in json.load(sys.stdin).items()]; z.close(); sys.stdout.buffer.write(b.getvalue())'],{input:JSON.stringify(files),maxBuffer:65536});
 const archiveSha256=hash(archive),sourceSha256=hash(files['index.html']),manifestSha256=contentHash(files);
 const source={kind:'BACKEND',version:'1.2.3',sha256:sourceSha256,archiveSha256,manifestSha256,archiveBase64:archive.toString('base64'),files};
 const context={title:'CI recovery source',summary:'Synthetic immutable package recovery.',contribution:'Restore byte identity and retained source rights.'};
 const license=files.LICENSE;
 const pack={licenseTerms:license,includedAssets:['Original synthetic source'],excludedAssets:['Production data'],setupSteps:['Open index.html locally'],prerequisites:['A current browser'],limitations:['Disposable CI data only'],supportTerms:'No real service or payment.'};
 const disclosure={category:'FULL_STACK',difficulty:'BEGINNER',demoMode:'SOURCE_ONLY',demoUrl:'',language:'HTML',problem:'Recover exact immutable source.',outcome:'Restore source and accepted rights.',contribution:'Original synthetic source recovery.',categoryEvidence:Object.fromEntries(['modules','schemaApi','demoAccounts','setupMigrations','versions','tests','deployment'].map(key=>[key,'Synthetic source recovery disclosure for '+key+'.'])),package:pack,componentLinks:[]};
 const binding={versionId:restoreIds.version,templateId:restoreIds.template,sha256:archiveSha256,version:'1.2.3',sizeBytes:archive.length,entryCount:3,files:Object.keys(files),manifestFiles:Object.keys(files),licenseTerms:license};
 const paidSnapshot={...disclosure,mode:'PAID',productId:restoreIds.product,slug:'ci-restore-paid',title:'CI paid recovery source',summary:'Immutable paid source recovery.',priceMinor:100,currency:'INR',sourceBinding:binding};
 const freeSnapshot={...disclosure,mode:'FREE',productId:restoreIds.freeProduct,slug:'ci-restore-free',title:'CI free recovery source',summary:'Immutable free source recovery.',sourceBinding:{packageId:restoreIds.freePackage,sha256:archiveSha256,sizeBytes:archive.length,entryCount:3,files:Object.keys(files),licenseTerms:license}};
 const payload=JSON.stringify({id:'ci-restore-revoked',componentId:restoreIds.component,revision:1,sourceSha256,archiveSha256,manifestSha256,operatorEpoch:restoreIds.epoch,fixture:true});
 return {ids:restoreIds,archive,source,context,license,paidSnapshot,freeSnapshot,paidHash:contentHash(paidSnapshot),freeHash:contentHash(freeSnapshot),payload,payloadHash:hash(payload)};
}

export function seedRestoreSql(fixture){
 const f=fixture,i=f.ids,j=object;
 return `BEGIN;
SET LOCAL search_path=getlancer,public;
INSERT INTO users(id,email,password_hash,email_verified_at) VALUES
 (${sql(i.seller)},'ci-restore-seller@example.test','disabled',now()),(${sql(i.buyer)},'ci-restore-buyer@example.test','disabled',now());
INSERT INTO user_roles(user_id,role) VALUES(${sql(i.seller)},'DEVELOPER'),(${sql(i.buyer)},'CLIENT');
INSERT INTO developer_profiles(user_id,slug,display_name,approval_status) VALUES(${sql(i.seller)},'ci-restore-seller','Synthetic recovery owner','APPROVED');
INSERT INTO products(id,owner_user_id,slug,title,summary,description,project_type,category,technology,visibility,contribution_text,approval_status,lifecycle_status,rights_confirmed) VALUES
 (${sql(i.product)},${sql(i.seller)},'ci-restore-paid','CI paid recovery source','Immutable paid source recovery.','Synthetic disclosure and entitlement fixture.','LEARNING','Education','HTML','PUBLIC','Synthetic source rights.','APPROVED','ARCHIVED',true),
 (${sql(i.freeProduct)},${sql(i.seller)},'ci-restore-free','CI free recovery source','Immutable free source recovery.','Synthetic free source recovery fixture.','LEARNING','Education','HTML','PUBLIC','Synthetic source rights.','APPROVED','ARCHIVED',true);
INSERT INTO college_project_metadata(product_id,category,language,problem,outcome,prerequisites,contribution,status) VALUES
 (${sql(i.product)},'FULL_STACK','HTML','Recover exact immutable source.','Restore source and accepted rights.','A current browser','Original synthetic source recovery.','APPROVED'),
 (${sql(i.freeProduct)},'FULL_STACK','HTML','Recover exact immutable source.','Restore source and accepted rights.','A current browser','Original synthetic source recovery.','APPROVED');
INSERT INTO component_entries(id,owner_id,recipe_slug,slug,title,summary,contribution,status,published_at,published_source,published_context,withdrawn_at)
 VALUES(${sql(i.component)},${sql(i.seller)},'ci-recovery','ci-restore-component','CI recovery source','Immutable source recovery.','Synthetic source rights.','ARCHIVED',now(),${j(f.source)},${j(f.context)},now());
INSERT INTO component_releases(component_id,revision,source,context,source_sha256) VALUES(${sql(i.component)},1,${j(f.source)},${j(f.context)},${sql(f.source.sha256)});
INSERT INTO component_previews(deployment_id,component_id,revision,state,expires_at,archive_sha256,manifest_sha256)
 VALUES(${sql(i.preview)},${sql(i.component)},1,'REVOKED',now()+interval '1 hour',${sql(f.source.archiveSha256)},${sql(f.source.manifestSha256)});
INSERT INTO education_free_packages(id,owner_id,storage_key,sha256,size_bytes,entry_count,files)
 VALUES(${sql(i.freePackage)},${sql(i.seller)},'ci-restore/free-source.zip',${sql(f.source.archiveSha256)},${f.archive.length},3,${j(Object.keys(f.source.files))});
INSERT INTO source_templates(id,seller_id,product_id,slug,title,summary,description,price_minor,license_terms,status)
 VALUES(${sql(i.template)},${sql(i.seller)},${sql(i.product)},'ci-restore-source','CI paid recovery source','Synthetic source entitlement.','Synthetic immutable agreement fixture.',100,${sql(f.license)},'ARCHIVED');
INSERT INTO source_versions(id,template_id,version,release_notes,storage_key,sha256,size_bytes,entry_count,license_terms,manifest_files,safe_source_files,rights_consented_at,status,submitted_at,reviewed_at)
 VALUES(${sql(i.version)},${sql(i.template)},'1.2.3','Synthetic recovery release.','ci-restore/paid-source.zip',${sql(f.source.archiveSha256)},${f.archive.length},3,${sql(f.license)},${sql(Object.keys(f.source.files).join('\n'))},${j(Object.keys(f.source.files))},now(),'APPROVED',now(),now());
INSERT INTO education_releases(id,product_id,owner_id,status,draft,snapshot,source_hash,free_package_id,source_version_id,submitted_at,reviewed_at) VALUES
 (${sql(i.freeRelease)},${sql(i.freeProduct)},${sql(i.seller)},'APPROVED',${j(f.freeSnapshot)},${j(f.freeSnapshot)},${sql(f.freeHash)},${sql(i.freePackage)},NULL,now(),now()),
 (${sql(i.paidRelease)},${sql(i.product)},${sql(i.seller)},'APPROVED',${j(f.paidSnapshot)},${j(f.paidSnapshot)},${sql(f.paidHash)},NULL,${sql(i.version)},now(),now());
INSERT INTO template_purchases(id,template_id,version_id,buyer_id,seller_id,idempotency_key,title,version,license_terms,sha256,amount_minor,currency,account_id,mode,status,order_id,payment_id,education_release_id,education_snapshot)
 VALUES(${sql(i.purchase)},${sql(i.template)},${sql(i.version)},${sql(i.buyer)},${sql(i.seller)},${sql(i.idempotency)},'CI paid recovery source','1.2.3',${sql(f.license)},${sql(f.source.archiveSha256)},100,'INR','acc_ciRestoreFixture','test','CAPTURED','order_ciRestoreFixture','pay_ciRestoreFixture',${sql(i.paidRelease)},${j(f.paidSnapshot)});
INSERT INTO lab_manifests(id,payload_text,payload_sha256,signature,revoked_at) VALUES('ci-restore-revoked',${sql(f.payload)},${sql(f.payloadHash)},'synthetic-not-valid-admission',now());
INSERT INTO lab_runs(id,owner_id,manifest_id,scenario_id,request_hash,idempotency_key,inputs,manifest_sha256,image_digest,operator_epoch,lease_generation,status,expires_at,memory_mib,cost_micros,attention)
 VALUES(${sql(i.run)},${sql(i.buyer)},'ci-restore-revoked','request',${sql('a'.repeat(64))},'ci-restore-lab-run','{}'::jsonb,${sql(f.payloadHash)},${sql('sha256:'+'b'.repeat(64))},${sql(i.epoch)},2,'CANCELLING',now()+interval '5 minutes',64,1,true);
INSERT INTO lab_outbox(command_id,run_id,lease_generation,action,delivered_at) VALUES
 (${sql(i.start)},${sql(i.run)},1,'START',now()),(${sql(i.stop)},${sql(i.run)},2,'STOP',NULL);
UPDATE lab_runtime_settings SET paused=true,pause_reason='Disposable restore shutdown fixture.' WHERE id=1;
COMMIT;`;
}

// Canonical identities and values, rather than aggregate row-count evidence.
export function restoreSnapshotSql(){
 const i=restoreIds;
 return `SELECT jsonb_build_object('schema',1,
 'component',(SELECT jsonb_build_object('id',c.id,'status',c.status,'withdrawn',c.withdrawn_at IS NOT NULL,'revision',r.revision,'source',r.source,'context',r.context,'sourceSha256',r.source_sha256,'previewState',p.state) FROM getlancer.component_entries c JOIN getlancer.component_releases r ON r.component_id=c.id AND r.revision=1 JOIN getlancer.component_previews p ON p.component_id=c.id AND p.revision=r.revision WHERE c.id=${sql(i.component)}),
 'free',(SELECT jsonb_build_object('id',e.id,'sourceHash',e.source_hash,'snapshot',e.snapshot,'packageId',p.id,'packageSha256',p.sha256,'sizeBytes',p.size_bytes,'files',p.files) FROM getlancer.education_releases e JOIN getlancer.education_free_packages p ON p.id=e.free_package_id WHERE e.id=${sql(i.freeRelease)}),
 'paid',(SELECT jsonb_build_object('id',e.id,'sourceHash',e.source_hash,'snapshot',e.snapshot,'versionId',v.id,'templateId',v.template_id,'packageSha256',v.sha256,'sizeBytes',v.size_bytes,'licenseTerms',v.license_terms,'files',v.safe_source_files) FROM getlancer.education_releases e JOIN getlancer.source_versions v ON v.id=e.source_version_id WHERE e.id=${sql(i.paidRelease)}),
 'purchase',(SELECT jsonb_build_object('id',p.id,'buyerId',p.buyer_id,'sellerId',p.seller_id,'versionId',p.version_id,'templateId',p.template_id,'releaseId',p.education_release_id,'snapshot',p.education_snapshot,'packageSha256',p.sha256,'licenseTerms',p.license_terms,'status',p.status,'entitlementRevoked',p.entitlement_revoked,'paymentId',p.payment_id,'orderId',p.order_id,'mode',p.mode) FROM getlancer.template_purchases p WHERE p.id=${sql(i.purchase)}),
 'lab',(SELECT jsonb_build_object('id',r.id,'manifestId',r.manifest_id,'manifestSha256',r.manifest_sha256,'operatorEpoch',r.operator_epoch,'generation',r.lease_generation,'status',r.status,'cleanupConfirmed',r.cleanup_confirmed_at IS NOT NULL,'manifestPayload',m.payload_text,'manifestRevoked',m.revoked_at IS NOT NULL,'outbox',(SELECT jsonb_agg(jsonb_build_object('id',o.command_id,'generation',o.lease_generation,'action',o.action,'delivered',o.delivered_at IS NOT NULL) ORDER BY o.lease_generation) FROM getlancer.lab_outbox o WHERE o.run_id=r.id)) FROM getlancer.lab_runs r JOIN getlancer.lab_manifests m ON m.id=r.manifest_id WHERE r.id=${sql(i.run)}),
 'paused',(SELECT paused FROM getlancer.lab_runtime_settings WHERE id=1),
 'publicComponent',(SELECT count(*) FROM getlancer.component_entries WHERE id=${sql(i.component)} AND status='ACTIVE' AND withdrawn_at IS NULL),
 'publicEducation',(SELECT count(*) FROM getlancer.products WHERE id IN (${sql(i.product)},${sql(i.freeProduct)}) AND approval_status='APPROVED' AND lifecycle_status='ACTIVE' AND visibility='PUBLIC'))::text;`;
}

export function expectedRestoreSnapshot(f=restoreFixture()){
 const i=f.ids;
 return {schema:1,component:{id:i.component,status:'ARCHIVED',withdrawn:true,revision:1,source:f.source,context:f.context,sourceSha256:f.source.sha256,previewState:'REVOKED'},free:{id:i.freeRelease,sourceHash:f.freeHash,snapshot:f.freeSnapshot,packageId:i.freePackage,packageSha256:f.source.archiveSha256,sizeBytes:f.archive.length,files:Object.keys(f.source.files)},paid:{id:i.paidRelease,sourceHash:f.paidHash,snapshot:f.paidSnapshot,versionId:i.version,templateId:i.template,packageSha256:f.source.archiveSha256,sizeBytes:f.archive.length,licenseTerms:f.license,files:Object.keys(f.source.files)},purchase:{id:i.purchase,buyerId:i.buyer,sellerId:i.seller,versionId:i.version,templateId:i.template,releaseId:i.paidRelease,snapshot:f.paidSnapshot,packageSha256:f.source.archiveSha256,licenseTerms:f.license,status:'CAPTURED',entitlementRevoked:false,paymentId:'pay_ciRestoreFixture',orderId:'order_ciRestoreFixture',mode:'test'},lab:{id:i.run,manifestId:'ci-restore-revoked',manifestSha256:f.payloadHash,operatorEpoch:i.epoch,generation:2,status:'CANCELLING',cleanupConfirmed:false,manifestPayload:f.payload,manifestRevoked:true,outbox:[{id:i.start,generation:1,action:'START',delivered:true},{id:i.stop,generation:2,action:'STOP',delivered:false}]},paused:true,publicComponent:0,publicEducation:0};
}

export function verifyNewDomainRestore(before,after,f){
 assert.deepEqual(before,expectedRestoreSnapshot(f),'Require complete canonical new-domain fixtures before backup.');
 assert.deepEqual(after,before,'Restored package, licence, purchase, outbox and withdrawal bindings must match.');
 const bytes=Buffer.from(after.component.source.archiveBase64,'base64');
 assert.deepEqual(bytes,f.archive,'Restored archive bytes must be the original ZIP.');
 assert.equal(hash(bytes),after.component.source.archiveSha256,'Restored ZIP SHA-256 must match metadata.');
 assert.equal(hash(after.component.source.files['index.html']),after.component.sourceSha256);
 assert.equal(contentHash(after.free.snapshot),after.free.sourceHash);assert.equal(contentHash(after.paid.snapshot),after.paid.sourceHash);
 assert.equal(after.free.packageSha256,hash(bytes));assert.equal(after.paid.packageSha256,hash(bytes));
 assert.equal(after.purchase.packageSha256,after.paid.packageSha256);
 assert.equal(after.purchase.versionId,after.paid.versionId);assert.equal(after.purchase.releaseId,after.paid.id);
 assert.equal(after.purchase.licenseTerms,after.paid.licenseTerms);assert.deepEqual(after.purchase.snapshot,after.paid.snapshot);
 assert.equal(hash(after.lab.manifestPayload),after.lab.manifestSha256);
 assert.equal(after.lab.outbox.at(-1).generation,after.lab.generation);
 return {componentId:after.component.id,archiveSha256:hash(bytes),educationReleases:2,purchaseId:after.purchase.id,pendingStop:after.lab.outbox.at(-1).id};
}
