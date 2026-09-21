import {examples, type Product} from './catalog';

// This browser-only simulator has no API, credential, upload, or email integration.
// ponytail: one tab's sample records; replace with the real API for integration UAT.
export type DemoPerson = {id:string;name:string;admin:boolean;profile:string;bio:string;availability:string;reason?:string};
export type DemoProject = Product & {ownerId:string;proof:boolean;rightsConfirmed:boolean;moderationReason?:string};
export type DemoReview = {rating:number;reviewText:string;visibility:'ANONYMOUS'|'NAMED';status:'PENDING'|'PUBLISHED'|'HIDDEN'};
export type DemoRequest = {id:string;productId:string;clientId:string;builderId:string;projectTitle:string;description:string;budgetBand:string;timelineBand:string;status:string;events:{label:string;actor:string;at:string}[];review?:DemoReview;reportedValue?:number;currency?:string};
export type DemoState = {version:1;people:DemoPerson[];projects:DemoProject[];requests:DemoRequest[];saved:Record<string,string[]>;reports:{id:string;productId:string;reason:string;detail:string;status:string}[];audit:{id:string;actor:string;message:string;at:string}[]};
export type DemoAction =
 | {type:'save';id:string}
 | {type:'draft';id?:string;title:string;summary:string;description:string;contribution:string;category:string;technology:string;projectType:string;rightsConfirmed:boolean}
 | {type:'project';id:string;decision:'proof'|'submit'|'archive'|'activate'|'approve'|'changes';reason?:string}
 | {type:'profile';bio:string;availability:string}
 | {type:'submit-profile'}
 | {type:'moderate-profile';id:string;approve:boolean;reason:string}
 | {type:'inquire';productId:string;description:string;budgetBand:string;timelineBand:string}
 | {type:'request';id:string;step:string;reportedValue?:number;currency?:string}
 | {type:'review';id:string;rating:number;reviewText:string;visibility:'ANONYMOUS'|'NAMED'}
 | {type:'moderate-review';id:string;publish:boolean;reason:string}
 | {type:'report';productId:string;reason:string;detail:string}
 | {type:'resolve-report';id:string;hide:boolean;reason:string};

export const DEMO_STORAGE_KEY='getlancer-uat-v1';
export const builderSteps:Record<string,{step:string;label:string;next:string}>={
 INQUIRY_RECEIVED:{step:'respond',label:'Mark responded',next:'RESPONDED'},
 RESPONDED:{step:'discuss',label:'Start discussion',next:'DISCUSSION'},
 DISCUSSION:{step:'propose',label:'Mark proposal sent',next:'PROPOSAL_SENT'},
 PROPOSAL_SENT:{step:'request-hire',label:'Request hire confirmation',next:'HIRE_PENDING_CONFIRMATION'},
 HIRED:{step:'start-work',label:'Mark in progress',next:'IN_PROGRESS'},
 IN_PROGRESS:{step:'request-completion',label:'Request completion confirmation',next:'COMPLETION_PENDING_CONFIRMATION'},
};
export function createDemoState():DemoState {
 const people:DemoPerson[]=[
  {id:'alex',name:'Alex Rivers',admin:false,profile:'DRAFT',bio:'',availability:'AVAILABLE_NOW'},
  {id:'leah',name:'Leah Morgan',admin:false,profile:'APPROVED',bio:'I build inventory, operations and workflow tools with React and Spring Boot.',availability:'AVAILABLE_NOW'},
  {id:'daniel',name:'Daniel Kim',admin:false,profile:'APPROVED',bio:'Independent builder creating booking, customer management and education software.',availability:'ONE_SLOT_LEFT'},
  {id:'sam',name:'Sam Parker',admin:true,profile:'APPROVED',bio:'Community moderator for this demonstration.',availability:'NOT_ACCEPTING'},
 ];
 const projects=examples.map(p=>{
  const owner=people.find(u=>u.id===(['stockroom','tableflow','taskline'].includes(p.id)?'leah':'daniel'))!;
  return {...p,ownerId:owner.id,builder:owner.name,builderSlug:owner.name.toLowerCase().replace(' ','-'),proof:true,rightsConfirmed:true};
 });
 projects.push({...projects[0],id:'customer-portal',slug:'customer-portal',title:'Customer portal',summary:'One place for client updates and approvals.',description:'A secure workspace for clients to follow deliverables, review files and keep approvals moving.',category:'Productivity',approvalStatus:'DRAFT',lifecycleStatus:'DRAFT',proof:false});
 return {version:1,people,projects,requests:[],saved:{},reports:[],audit:[]};
}
export function publicDemoProjects(state:DemoState) {
 return state.projects.filter(p=>p.visibility==='PUBLIC'&&p.approvalStatus==='APPROVED'&&p.lifecycleStatus==='ACTIVE'&&state.people.some(u=>u.id===p.ownerId&&u.profile==='APPROVED'));
}
export function applyDemoAction(current:DemoState,actorId:string,action:DemoAction):DemoState {
 const state=structuredClone(current),actor=state.people.find(p=>p.id===actorId);
 const ensure:(condition:unknown,message:string)=>asserts condition=(condition,message)=>{if(!condition)throw Error(message)};
 ensure(actor,'Choose a sample account first.');
 const person=actor!;
 const id=()=>globalThis.crypto?.randomUUID?.()||'demo-'+Date.now().toString(36)+'-'+Math.random().toString(36).slice(2),now=new Date().toISOString();
 const text=(value:string,min:number,max:number,name:string)=>{ensure(value.trim().length>=min&&value.trim().length<=max,`${name} must contain ${min}–${max} characters.`);return value.trim()};
 const admin=()=>ensure(person.admin,'Only the sample administrator can make this decision.');
 const owned=(project:DemoProject)=>ensure(project.ownerId===person.id,'Switch to this project’s builder to make changes.');
 const audit=(message:string)=>state.audit.unshift({id:id(),actor:person.name,message,at:now});
 const project=(value:string)=>{const p=state.projects.find(p=>p.id===value);ensure(p,'Project not found.');return p!};
 const request=(value:string)=>{const r=state.requests.find(r=>r.id===value);ensure(r,'Request not found.');return r!};
 const capacity=(p:DemoProject)=>ensure(state.projects.filter(x=>x.ownerId===p.ownerId&&x.lifecycleStatus==='ACTIVE'&&x.id!==p.id).length<3,'Three active showcases are already in use. Archive one before activating this project.');
 switch(action.type){
  case 'save': {project(action.id);const saved=state.saved[person.id]||[];state.saved[person.id]=saved.includes(action.id)?saved.filter(v=>v!==action.id):[...saved,action.id];break;}
  case 'draft': {
   ensure(person.profile==='APPROVED','Complete your profile and obtain administrator approval before creating showcases.');
   const fields={title:text(action.title,3,120,'Title'),summary:text(action.summary,10,240,'Summary'),description:text(action.description,20,5000,'Description'),contribution:text(action.contribution,10,2000,'Contribution'),category:action.category,technology:action.technology,projectType:action.projectType,rightsConfirmed:action.rightsConfirmed};
   ensure(fields.category&&fields.technology,'Choose a category and technology.');
   if(action.id){const p=project(action.id);owned(p);ensure(p.lifecycleStatus!=='SUSPENDED','This showcase is restricted.');Object.assign(p,fields,{approvalStatus:'DRAFT',lifecycleStatus:'DRAFT',moderationReason:undefined});}
   else {const key=id();state.projects.push({...examples[0],...fields,id:key,slug:key,builder:person.name,builderSlug:person.id,ownerId:person.id,proof:false,approvalStatus:'DRAFT',lifecycleStatus:'DRAFT',updatedAt:now});}
   break;
  }
  case 'project': {
   const p=project(action.id);
   if(['approve','changes'].includes(action.decision)){admin();ensure(p.approvalStatus==='PENDING_REVIEW','This project is not awaiting review.');const reason=text(action.reason||'',3,2000,'Decision reason');p.moderationReason=reason;p.approvalStatus=action.decision==='approve'?'APPROVED':'CHANGES_REQUESTED';if(action.decision==='approve'&&state.people.find(u=>u.id===p.ownerId)?.profile==='APPROVED'&&state.projects.filter(x=>x.ownerId===p.ownerId&&x.lifecycleStatus==='ACTIVE').length<3)p.lifecycleStatus='ACTIVE';audit(`${p.title}: ${p.approvalStatus}. ${reason}`);break;}
   owned(p);ensure(p.lifecycleStatus!=='SUSPENDED','This showcase is restricted.');
   if(action.decision==='proof'){ensure(['DRAFT','CHANGES_REQUESTED'].includes(p.approvalStatus),'Return this showcase to draft before changing its proof.');p.proof=true;}
   if(action.decision==='submit'){ensure(['DRAFT','CHANGES_REQUESTED'].includes(p.approvalStatus),'This draft has already been submitted.');ensure(p.proof&&p.rightsConfirmed,'Add a sample proof image and confirm your right to showcase this work.');ensure(state.people.find(u=>u.id===p.ownerId)?.profile==='APPROVED','Builder profile approval is required.');p.approvalStatus='PENDING_REVIEW';}
   if(action.decision==='archive'){ensure(p.lifecycleStatus==='ACTIVE','Only active projects can be archived.');p.lifecycleStatus='ARCHIVED';}
   if(action.decision==='activate'){ensure(p.approvalStatus==='APPROVED','Administrator approval is required before activation.');ensure(state.people.find(u=>u.id===p.ownerId)?.profile==='APPROVED','Builder profile approval is required.');capacity(p);p.lifecycleStatus='ACTIVE';}
   p.updatedAt=now;break;
  }
  case 'profile': {ensure(!person.admin,'Use a sample builder or client account.');const bio=text(action.bio,20,2000,'Bio');if(bio!==person.bio)person.profile='DRAFT';person.bio=bio;person.availability=action.availability;break;}
  case 'submit-profile': {ensure(['DRAFT','CHANGES_REQUESTED'].includes(person.profile),'This profile is not ready for resubmission.');text(person.bio,20,2000,'Bio');person.profile='PROFILE_PENDING';break;}
  case 'moderate-profile': {admin();const p=state.people.find(p=>p.id===action.id);ensure(p&&p.profile==='PROFILE_PENDING','This profile is not awaiting review.');const reason=text(action.reason,3,2000,'Decision reason');p!.profile=action.approve?'APPROVED':'CHANGES_REQUESTED';p!.reason=reason;audit(`${p!.name}: profile ${p!.profile}. ${reason}`);break;}
  case 'inquire': {
   ensure(!person.admin,'Choose a sample client or builder account to commission work.');const p=project(action.productId);
   ensure(publicDemoProjects(state).some(x=>x.id===p.id),'This project is not available for public inquiries.');ensure(p.ownerId!==person.id,'You cannot send an inquiry to yourself. Choose another builder.');
   ensure(p.availableForSimilarWork,'This builder is not accepting similar-build inquiries for this project.');
   ensure(action.budgetBand&&action.timelineBand,'Choose a budget and timeline.');
   state.requests.unshift({id:id(),productId:p.id,clientId:person.id,builderId:p.ownerId,projectTitle:p.title,description:text(action.description,20,5000,'Project brief'),budgetBand:action.budgetBand,timelineBand:action.timelineBand,status:'CREATED_UNVERIFIED',events:[{label:'Inquiry saved — email confirmation pending',actor:person.name,at:now}]});break;
  }
  case 'request': {
   const r=request(action.id),builder=person.id===r.builderId,client=person.id===r.clientId;
   ensure(builder||client,'Switch to the client or builder for this request.');
   if(action.step==='confirm-email'){ensure(client&&r.status==='CREATED_UNVERIFIED','Only the client can confirm this pending inquiry.');r.status='INQUIRY_RECEIVED';}
   else if(action.step==='accept'||action.step==='reject'){
    ensure(client&&['HIRE_PENDING_CONFIRMATION','COMPLETION_PENDING_CONFIRMATION'].includes(r.status),'A client confirmation must be requested first.');
    r.status=r.status==='HIRE_PENDING_CONFIRMATION'?(action.step==='accept'?'HIRED':'DISCUSSION'):(action.step==='accept'?'COMPLETED':'IN_PROGRESS');
   } else if(action.step==='close'){ensure(['INQUIRY_RECEIVED','RESPONDED','DISCUSSION','PROPOSAL_SENT'].includes(r.status),'This request can no longer be closed as not hired.');r.status='NOT_HIRED';}
   else {const transition=builderSteps[r.status];ensure(builder&&transition?.step===action.step,'This step is not available yet. Follow the request in order.');
    if(action.step==='propose'&&action.reportedValue!==undefined){ensure(Number.isFinite(action.reportedValue)&&action.reportedValue>=0&&action.reportedValue<=999999999999.99&&/^[A-Z]{3}$/.test(action.currency||''),'Enter a valid proposal value and three-letter currency.');r.reportedValue=action.reportedValue;r.currency=action.currency;}
    r.status=transition.next;
   }
   r.events.push({label:r.status,actor:person.name,at:now});break;
  }
  case 'review': {const r=request(action.id);ensure(r.clientId===person.id&&r.status==='COMPLETED'&&!r.review,'Only the client can submit one review after confirmed completion.');ensure(Number.isInteger(action.rating)&&action.rating>=1&&action.rating<=5,'Choose a rating from 1 to 5.');ensure(['NAMED','ANONYMOUS'].includes(action.visibility),'Choose review name visibility.');r.review={rating:action.rating,reviewText:text(action.reviewText,10,2000,'Review'),visibility:action.visibility,status:'PENDING'};break;}
  case 'moderate-review': {admin();const r=request(action.id);ensure(r.review?.status==='PENDING','This review is not awaiting moderation.');text(action.reason,3,2000,'Decision reason');r.review!.status=action.publish?'PUBLISHED':'HIDDEN';audit(`${r.projectTitle}: review ${r.review!.status}. ${action.reason}`);break;}
  case 'report': {project(action.productId);ensure(action.reason,'Select a concern.');state.reports.unshift({id:id(),productId:action.productId,reason:action.reason,detail:text(action.detail,10,3000,'Report detail'),status:'OPEN'});break;}
  case 'resolve-report': {admin();const r=state.reports.find(r=>r.id===action.id);ensure(r?.status==='OPEN','This report is not open.');const reason=text(action.reason,3,2000,'Decision reason');if(action.hide)project(r!.productId).lifecycleStatus='SUSPENDED';r!.status='RESOLVED';audit(`Report ${r!.reason}: ${action.hide?'project restricted':'no restriction'}. ${reason}`);break;}
 }
 return state;
}
