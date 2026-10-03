export type DemoStatus = 'DRAFT' | 'PENDING' | 'APPROVED' | 'CHANGES_REQUESTED' | 'REJECTED' | 'SUSPENDED';
export type DeploymentState = 'NOT_CREATED' | 'CREATING' | 'UNKNOWN' | 'READY' | 'DELETE_PENDING' | 'DELETED';
export type HostedDemo = {
  id:string; productId:string; productTitle:string; title:string; version:string; status:DemoStatus;
  deploymentState:DeploymentState; desiredState:'PUBLISHED'|'WITHDRAWN'; archiveSha256:string; manifestSha256:string;
  sizeBytes:number; expandedBytes:number; fileCount:number; files:{path:string;sha256:string;sizeBytes:number}[];
  deploymentId:string|null; url:string|null; expiresAt:string|null; reviewReason:string|null; rightsConsentAt:string|null;
  createdAt:string; updatedAt:string; audit:{id:string;action:string;detail:string;createdAt:string}[];
};
export type HostingSource = {id:string;title:string};
export type HostingConfiguration = {enabled:boolean;reason:string;maxCompressedBytes:number;maxExpandedBytes:number;maxFiles:number;maxActive:number;maxRecords:number;expiryDays:number};
export type HostingPage<T> = {items:T[];page:number;size:number;hasMore:boolean;totalItems:number;totalPages:number};
export const hostingLimits = {maxCompressedBytes:5242880,maxExpandedBytes:10485760,maxFiles:256,maxActive:3,maxRecords:10,expiryDays:7};
export function hostingPage<T>(items:T[],page=0,size=12):HostingPage<T>{return {items:structuredClone(items.slice(page*size,(page+1)*size)),page,size,hasMore:(page+1)*size<items.length,totalItems:items.length,totalPages:Math.ceil(items.length/size)};}
export function hostingLabel(value:string){return value.toLowerCase().replaceAll('_',' ').replace(/^./,c=>c.toUpperCase());}
export function hostingBytes(value:number){return value<1024?value+' B':value<1048576?(value/1024).toFixed(1)+' KiB':(value/1048576).toFixed(1)+' MiB';}
export function hostingDate(value:string|null){return value?new Date(value).toLocaleString('en-GB',{dateStyle:'medium',timeStyle:'short'}):'Not reserved';}
export function hostingAttention(demo:HostedDemo){return ['CREATING','UNKNOWN','DELETE_PENDING'].includes(demo.deploymentState);}
export function hostingCanPublish(demo:HostedDemo){return demo.status==='APPROVED'&&demo.deploymentState==='NOT_CREATED'&&demo.desiredState==='PUBLISHED';}
export function safeHostedUrl(value:unknown):string|undefined{
  if(typeof value!=='string'||value.length>2048||/[\u0000-\u0020\u007f]/.test(value))return;
  try{const url=new URL(value);const local=url.protocol==='http:'&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.demo\.localhost$/.test(url.hostname);if((url.protocol!=='https:'&&!local)||url.username||url.password)return;return url.href;}catch{return;}
}
export function hostedDemoUrl(demo:Pick<HostedDemo,'status'|'deploymentState'|'desiredState'|'expiresAt'|'url'>,now=Date.now()):string|undefined{
  if(demo.status!=='APPROVED'||demo.deploymentState!=='READY'||demo.desiredState!=='PUBLISHED'||!demo.expiresAt||new Date(demo.expiresAt).getTime()<=now||!Number.isFinite(new Date(demo.expiresAt).getTime())||!demo.url)return;
  return safeHostedUrl(demo.url);
}
export function validateHostingUpload(file:{name:string;size:number}|null,title:string,version:string,consent:boolean){
  const fieldErrors:Record<string,string>={};
  if(!file||!file.name.toLowerCase().endsWith('.zip')||file.size<=0||file.size>hostingLimits.maxCompressedBytes)fieldErrors.file='Choose a nonempty ZIP of at most 5 MiB.';
  if(title.trim().length<3||title.trim().length>100)fieldErrors.title='Use a title of 3–100 characters.';
  if(!/^[A-Za-z0-9][A-Za-z0-9._+-]{0,39}$/.test(version))fieldErrors.version='Use 1–40 letters, numbers, dots, underscores, plus or hyphen; begin with a letter or number.';
  if(!consent)fieldErrors.rightsConsent='Confirm your rights to this package before uploading.';
  if(Object.keys(fieldErrors).length)throw Object.assign(new Error('Check the package fields and rights consent.'),{fieldErrors});
}

/** Bundled metadata exercise only: no archive upload, API, publisher or externally reachable demo. */
export function createHostingPreview(now=new Date()){
  const stamp=()=>now.toISOString(),copy=<T,>(value:T):T=>structuredClone(value);
  let actor:'BUILDER'|'OPERATOR'='BUILDER';
  const initial=():HostedDemo=>({id:'sample-hosted-frontend',productId:'sample-approved-proof',productTitle:'Customer approval portal',title:'Approval portal frontend',version:'1.0.0',status:'DRAFT',deploymentState:'NOT_CREATED',desiredState:'PUBLISHED',archiveSha256:'a'.repeat(64),manifestSha256:'b'.repeat(64),sizeBytes:18432,expandedBytes:36864,fileCount:4,files:[{path:'index.html',sha256:'c'.repeat(64),sizeBytes:8192},{path:'assets/app.css',sha256:'d'.repeat(64),sizeBytes:12288},{path:'assets/app.js',sha256:'e'.repeat(64),sizeBytes:15360},{path:'LICENSE.txt',sha256:'f'.repeat(64),sizeBytes:1024}],deploymentId:null,url:null,expiresAt:null,reviewReason:null,rightsConsentAt:null,createdAt:stamp(),updatedAt:stamp(),audit:[]});
  let records=[initial()];
  const find=(id:string)=>{const record=records.find(item=>item.id===id);if(!record)throw Error('Sample package not found.');return record;};
  const requireActor=(value:typeof actor)=>{if(actor!==value)throw Error('Switch the labelled sample actor to '+value.toLowerCase()+'.');};
  const event=(record:HostedDemo,action:string,detail:string)=>{record.updatedAt=stamp();record.audit.unshift({id:crypto.randomUUID(),action,detail,createdAt:stamp()});};
  const reserve=(record:HostedDemo)=>{requireActor('BUILDER');if(!hostingCanPublish(record))throw Error('Only an approved, unpublished package can be published.');if(records.filter(item=>hostingAttention(item)||item.deploymentState==='READY').length>=3)throw Error('The sample has three unresolved deployment reservations.');record.deploymentId=crypto.randomUUID();record.expiresAt=new Date(now.getTime()+7*86400000).toISOString();record.desiredState='PUBLISHED';record.deploymentState='CREATING';};
  return {
    setActor(value:typeof actor){actor=value;},reset(){records=[initial()];actor='BUILDER';},
    sources():HostingSource[]{return [{id:'sample-approved-proof',title:'Customer approval portal'}];},
    list(){return copy(records);},detail(id:string){return copy(find(id));},
    create(title:string,version:string,consent:boolean){requireActor('BUILDER');validateHostingUpload({name:'bundled-sample.zip',size:18432},title,version,consent);if(records.length>=10)throw Error('The sample retains at most ten immutable packages.');if(records.some(item=>item.version===version))throw Error('Use a new immutable version for this sample proof.');const record={...initial(),id:crypto.randomUUID(),title:title.trim(),version,rightsConsentAt:stamp()};records.unshift(record);event(record,'SAMPLE_PACKAGE_COPIED','Bundled package metadata copied locally; no upload occurred.');return copy(record);},
    submit(id:string,consent:boolean){requireActor('BUILDER');const record=find(id);if(record.deploymentState!=='NOT_CREATED'||!['DRAFT','CHANGES_REQUESTED'].includes(record.status)||!consent)throw Error('Explicit rights consent is required for an unpublished draft review submission.');record.status='PENDING';record.rightsConsentAt=stamp();event(record,'SAMPLE_SUBMITTED','Rights consent recorded locally. Package is waiting for simulated operator review.');},
    review(id:string,body:{action:string;reason:string;packageReviewed:boolean;rightsReviewed:boolean}){requireActor('OPERATOR');const record=find(id);if(!['APPROVE','CHANGES_REQUESTED','REJECT','SUSPEND'].includes(body.action)||body.reason.trim().length<10||body.reason.length>2000||!body.packageReviewed||!body.rightsReviewed)throw Error('Both sample review checks and a reason of 10–2000 characters are required.');if(body.action==='SUSPEND'){if(!['PENDING','APPROVED','SUSPENDED'].includes(record.status))throw Error('Only a pending or reviewed sample package can be suspended.');}else if(!['PENDING','SUSPENDED'].includes(record.status))throw Error('Only a pending or suspended sample package can receive this review.');if(body.action==='APPROVE'&&record.desiredState==='WITHDRAWN')throw Error('A withdrawn sample package cannot be approved for publication.');record.status=({APPROVE:'APPROVED',CHANGES_REQUESTED:'CHANGES_REQUESTED',REJECT:'REJECTED',SUSPEND:'SUSPENDED'} as const)[body.action as 'APPROVE'|'CHANGES_REQUESTED'|'REJECT'|'SUSPEND'];record.reviewReason=body.reason.trim();record.url=null;event(record,'SAMPLE_REVIEW_'+body.action,record.reviewReason);},
    publish(id:string,interrupted=false){const record=find(id);reserve(record);record.deploymentState=interrupted?'UNKNOWN':'READY';event(record,interrupted?'SAMPLE_PUBLICATION_UNKNOWN':'SAMPLE_PUBLICATION_READY',interrupted?'Simulated lost response. Recover the same reservation; no new identity is created.':'Publication simulated locally. No externally reachable demo exists.');},
    withdraw(id:string,interrupted=false){requireActor('BUILDER');const record=find(id);record.desiredState='WITHDRAWN';record.url=null;record.deploymentState=interrupted&&record.deploymentId?'DELETE_PENDING':'DELETED';event(record,'SAMPLE_WITHDRAWAL',interrupted?'Access denied; simulated deletion awaits reconciliation.':'Sample withdrawn; this immutable identity cannot be republished.');},
    reconcile(id:string){requireActor('BUILDER');const record=find(id);if(!hostingAttention(record))throw Error('This sample has no uncertain deployment to reconcile.');record.deploymentState=record.desiredState==='WITHDRAWN'?'DELETED':'READY';event(record,'SAMPLE_RECONCILED','The same fixed deployment identity was reconciled locally.');},
  };
}
