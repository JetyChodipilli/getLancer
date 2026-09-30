/** Isolated sample workspace. All records are fictional, held in memory and resettable. */
type Row = Record<string, any>;
const roleNames = ['OWNER', 'BUSINESS_MANAGER', 'RECRUITER', 'PROJECT_MANAGER', 'MEMBER'];
const transitions: Record<string, string[]> = {
 NEW: ['INTERESTED', 'NEEDS_INFORMATION', 'DECLINED'], INTERESTED: ['NEEDS_INFORMATION', 'PROPOSAL_SENT', 'DECLINED', 'LOST'],
 NEEDS_INFORMATION: ['INTERESTED', 'DECLINED'], PROPOSAL_SENT: ['WON', 'LOST', 'NEEDS_INFORMATION'], WON: [], LOST: [], DECLINED: [],
};
export function createTeamDemo() {
 const actors = [
  {id:'owner',name:'Leah Morgan',role:'Studio owner'}, {id:'recruiter',name:'Alex Rivers',role:'Recruiter'},
  {id:'pm',name:'Priya Shah',role:'Project manager'}, {id:'member',name:'Daniel Kim',role:'Member'},
  {id:'business',name:'Jamie Chen',role:'Business manager'}, {id:'client',name:'Robin Ellis',role:'Client'},
  {id:'applicant',name:'Amara Okafor',role:'Applicant'}, {id:'admin',name:'Sam Parker',role:'MFA administrator'},
  {id:'unverified',name:'Taylor Reed',role:'Unverified account'},
 ];
 let actorId = 'owner', serial = 0;
 let teams: Row[], memberships: Row[], invitations: Row[], roles: Row[], applications: Row[], leads: Row[], products: Row[], contributions: Row[], staffing: Row[], activity: Row[];
 const now = () => new Date(Date.now()).toISOString();
 const future = (days:number) => new Date(Date.now()+days*86400000).toISOString();
 const id = () => `sample-${++serial}`;
 const fail = (status:number,message:string):never => {throw Object.assign(new Error(message),{name:'ApiError',status,code:status===403?'FORBIDDEN':status===404?'NOT_FOUND':status===409?'INVALID_STATE_TRANSITION':status===401?'UNAUTHORIZED':'VALIDATION_ERROR',fieldErrors:{}})};
 const ensure = (ok:unknown,message:string,status=400) => {if(!ok) fail(status,message)};
 const person = (userId:string) => actors.find(a=>a.id===userId);
 const approved = (userId:string) => !!person(userId)&&!['client','admin','unverified'].includes(userId);
 const active = (m:Row) => !m.expiresAt || Date.parse(m.expiresAt)>Date.now();
 const members = (teamId:string) => memberships.filter(m=>m.teamId===teamId&&active(m));
 const mine = (teamId:string) => members(teamId).find(m=>m.userId===actorId);
 const auth = (teamId:string,allowed:string[]) => {const m=mine(teamId);ensure(m&&allowed.includes(m.role),'Your team role does not permit this action.',403);return m!};
 const audit = (teamId:string,action:string,detail:string,scope='GENERAL') => activity.push({id:id(),teamId,action,detail,scope,actorName:person(actorId)!.name,createdAt:now()});
 const teamDto = (t:Row,privateView=false) => ({...t,memberCount:members(t.id).filter(m=>approved(m.userId)).length,roleCount:roles.filter(r=>r.teamId===t.id&&r.status==='OPEN').length,...(privateView?{myRole:mine(t.id)?.role}: {})});
 const restricted = (team:Row) => ensure(team.status==='ACTIVE','This team is suspended.',409);
 const skills = (value:unknown) => {ensure(value==null||typeof value==='string'||Array.isArray(value),'Skills must be text.');return Array.isArray(value)?value.map(s=>required(s,'Skill',80)).join(', '):value==null||value===''?'':required(value,'Skills',500)};
 const memberDto = (m:Row) => {const {teamId:_,...rest}=m;return rest};
 const teamProjects = (teamId:string) => contributions.filter(c=>c.teamId===teamId).map(c=>products.find(p=>p.id===c.productId)).filter(p=>p&&p.visibility==='PUBLIC'&&p.approvalStatus==='APPROVED'&&p.lifecycleStatus==='ACTIVE'&&members(teamId).some(m=>m.userId===p.ownerId)&&approved(p.ownerId)).map(p=>{const {ownerId:_,...rest}=p!;return rest});
 const required = (value:unknown,label:string,max=2000) => {ensure(typeof value==='string'&&value.trim().length>0&&value.trim().length<=max,`${label} is required (maximum ${max} characters).`);return (value as string).trim()};
 const date = (value:unknown,label:string) => {ensure(typeof value==='string'&&Number.isFinite(Date.parse(value))&&Date.parse(value)>Date.now(),`${label} must be a future date.`);return new Date(value as string).toISOString()};
 function reset() {
  serial=0;actorId='owner';
  teams=[{id:'team-studio',slug:'northstar-studio',name:'Northstar Studio',summary:'A small independent team building useful operations software.',availability:'AVAILABLE_NOW',projectRange:'$5k–$25k',ownerId:'owner',status:'ACTIVE'}];
  memberships=['owner','recruiter','pm','member','business'].map((userId,i)=>({teamId:'team-studio',userId,name:person(userId)!.name,role:['OWNER','RECRUITER','PROJECT_MANAGER','MEMBER','BUSINESS_MANAGER'][i],membershipType:'PERMANENT',expiresAt:null,projectLabel:null}));
  invitations=[];applications=[];staffing=[];activity=[];
  roles=[{id:'role-frontend',teamId:'team-studio',title:'Frontend builder',description:'Help ship an accessible client portal with our product team.',skills:'React, Accessibility',contractType:'CONTRACT',compensationBand:'$60–$90/hour',status:'OPEN'}];
  products=['owner','member','applicant'].map((ownerId,i)=>({id:`product-${ownerId}`,slug:`sample-project-${i}`,ownerId,title:['Stockroom operations','Customer portal','Booking workspace'][i],summary:'A working example of a practical business application.',description:'An illustrative product built by an independent software builder.',category:'Business',technology:'React',projectType:'WEB_APP',builder:person(ownerId)!.name,builderSlug:ownerId,availability:'AVAILABLE_NOW',contribution:'Product design and implementation',approvalStatus:'APPROVED',lifecycleStatus:'ACTIVE',visibility:'PUBLIC',availableForSimilarWork:true,updatedAt:now(),illustrative:true}));
  contributions=[{teamId:'team-studio',productId:'product-owner'}];
  leads=[{id:'lead-portal',teamId:'team-studio',teamName:'Northstar Studio',clientId:'client',clientName:person('client')!.name,title:'Customer portal for a design practice',description:'We need a shared place for client approvals and project updates.',budget:'$10k–$20k',timeline:'Within 2 months',status:'NEW',assigneeId:'business',followUpAt:future(3),notes:[{text:'Sample note: ask about existing approval process.',createdAt:now(),actorName:person('business')!.name}]}];
 }
 reset();
 function invite(team:Row,b:Row) {
  ensure(team.status==='ACTIVE','This team cannot recruit while suspended.',403);
  ensure(approved(b.userId),'Invite an approved builder.');ensure(!members(team.id).some(m=>m.userId===b.userId),'This builder is already an active member.');
  const role=b.role||'MEMBER';ensure(roleNames.includes(role)&&role!=='OWNER','Choose a non-owner role.');
  const membershipType=b.membershipType||'PERMANENT';ensure(['PERMANENT','CONTRACT'].includes(membershipType),'Choose PERMANENT or CONTRACT membership.');
  const expiresAt=b.expiresAt?date(b.expiresAt,'Expiry'):null;ensure(membershipType!=='CONTRACT'||expiresAt,'Contract membership requires an expiry.');
  ensure(!invitations.some(i=>i.teamId===team.id&&i.userId===b.userId&&i.status==='PENDING'&&active(i)),'A pending invitation already exists.');
  const invitation={id:id(),teamId:team.id,teamName:team.name,userId:b.userId,name:person(b.userId)!.name,role,membershipType,expiresAt,projectLabel:b.projectLabel?required(b.projectLabel,'Project label',160):null,status:'PENDING',respondBy:future(14)};
  invitations.push(invitation);audit(team.id,'INVITATION_SENT',`Invited ${invitation.name}.`,'RECRUITMENT');return invitation;
 }
 function handle(path:string,init:RequestInit={}) {
  const url=new URL(path,'https://demo.invalid');const parts=url.pathname.replace(/^\/api\/v1/,'').split('/').filter(Boolean);const method=(init.method||'GET').toUpperCase();
  const route=parts.join('/');
  const routes:Record<string,RegExp>={GET:/^(me|developer\/products|me\/teams|admin\/teams|teams(?:\/[^/]+(?:\/(?:workspace|candidates))?)?)$/,POST:/^(teams|teams\/[^/]+\/(?:invitations|roles|leads|projects|staffing)|teams\/[^/]+\/roles\/[^/]+\/applications|teams\/[^/]+\/applications\/[^/]+\/decision|teams\/[^/]+\/leads\/[^/]+\/respond|team-invitations\/[^/]+\/respond|admin\/teams\/[^/]+\/moderate)$/,PUT:/^teams\/[^/]+$/,PATCH:/^teams\/[^/]+\/(?:members|roles|leads|staffing)\/[^/]+$/,DELETE:/^teams\/[^/]+\/(?:members|projects)\/[^/]+$/};
  ensure(routes[method]?.test(route),'Demo endpoint not found.',404);
  let b:Row={};if(init.body){try{b=JSON.parse(String(init.body));ensure(b&&typeof b==='object'&&!Array.isArray(b),'Expected a JSON object.')}catch{fail(400,'Expected a JSON object.')}}
  ensure(person(actorId),'Choose a sample account.',401);
  for(const i of invitations)if(i.status==='PENDING'&&(!active(i)||Date.parse(i.respondBy)<=Date.now()))i.status='EXPIRED';
  if(parts.join('/')==='me'&&method==='GET')return {id:actorId,name:person(actorId)!.name,emailVerified:actorId!=='unverified',roles:actorId==='admin'?['ADMIN','CLIENT']:approved(actorId)?['DEVELOPER','CLIENT']:['CLIENT'],profile:approved(actorId)?{approval_status:'APPROVED',display_name:person(actorId)!.name}:null,role:actorId==='admin'?'ADMIN':'USER',profileStatus:approved(actorId)?'APPROVED':'DRAFT'};
  if(parts.join('/')==='developer/products'&&method==='GET')return {items:products.filter(p=>p.ownerId===actorId).map(p=>{const {ownerId:_,...rest}=p;return rest})};
  if(parts.join('/')==='me/teams'&&method==='GET')return {items:teams.filter(t=>mine(t.id)).map(t=>teamDto(t,true)),invitations:invitations.filter(i=>i.userId===actorId),applications:applications.filter(a=>a.userId===actorId),requests:leads.filter(l=>l.clientId===actorId).map(l=>({...l,notes:[],assigneeId:null,followUpAt:null}))};
  if(parts[0]==='team-invitations'&&parts[2]==='respond'&&method==='POST') {
   const i=invitations.find(i=>i.id===parts[1]);ensure(i,'Invitation not found.',404);ensure(i!.userId===actorId,'Only the recipient can respond.',403);ensure(i!.status==='PENDING','This invitation is no longer pending.');ensure(['ACCEPT','DECLINE'].includes(b.action),'Choose ACCEPT or DECLINE.');
   const t=teams.find(t=>t.id===i!.teamId)!;if(b.action==='ACCEPT'){ensure(t.status==='ACTIVE','This team is suspended.',403);ensure(approved(actorId),'Builder approval is required.',403);ensure(!mine(t.id),'You already belong to this team.');memberships.push({teamId:t.id,role:i!.role,userId:actorId,name:person(actorId)!.name,membershipType:i!.membershipType,expiresAt:i!.expiresAt,projectLabel:i!.projectLabel});}
   i!.status=b.action==='ACCEPT'?'ACCEPTED':'DECLINED';audit(t.id,'INVITATION_RESPONDED',`${person(actorId)!.name} ${i!.status.toLowerCase()} an invitation.`,'RECRUITMENT');return i;
  }
  if(parts[0]==='admin'&&parts[1]==='teams') {
   ensure(actorId==='admin','An MFA administrator is required.',403);
   if(parts.length===2&&method==='GET')return {items:teams.map(t=>teamDto(t))};
   if(parts.length===4&&parts[3]==='moderate'&&method==='POST'){const t=teams.find(t=>t.id===parts[2]);ensure(t,'Team not found.',404);ensure(['ACTIVE','SUSPENDED'].includes(b.status),'Choose ACTIVE or SUSPENDED.');const reason=required(b.reason,'Moderation reason');t!.status=b.status;audit(t!.id,'TEAM_MODERATED',reason);return teamDto(t!);}
  }
  if(parts[0]!=='teams')fail(404,'Demo endpoint not found.');
  if(parts.length===1) {
   if(method==='GET'){const q=(url.searchParams.get('q')||'').toLowerCase();return {items:teams.filter(t=>t.status==='ACTIVE'&&`${t.name} ${t.summary}`.toLowerCase().includes(q)).map(t=>teamDto(t))};}
   if(method==='POST'){ensure(approved(actorId)&&products.some(p=>p.ownerId===actorId&&p.approvalStatus==='APPROVED'),'Creation requires an approved, verified builder with an approved product.',403);const teamId=id();const t={id:teamId,slug:teamId,name:required(b.name,'Name',120),summary:required(b.summary,'Summary'),availability:required(b.availability,'Availability',80),projectRange:required(b.projectRange,'Project range',120),ownerId:actorId,status:'ACTIVE'};teams.push(t);memberships.push({teamId,userId:actorId,name:person(actorId)!.name,role:'OWNER',membershipType:'PERMANENT',expiresAt:null,projectLabel:null});audit(teamId,'TEAM_CREATED',t.name);return teamDto(t,true);}
  }
  const t=teams.find(t=>t.id===parts[1]);ensure(t,'Team not found.',404);const team=t!;const sub=parts[2];
  const recruit=()=>{auth(team.id,['OWNER','RECRUITER']);ensure(team.status==='ACTIVE','This team cannot recruit while suspended.',403)};
  if(parts.length===2&&method==='GET'){ensure(team.status==='ACTIVE','Team not found.',404);return {team:teamDto(team),members:members(team.id).filter(m=>approved(m.userId)).map(m=>({userId:m.userId,name:m.name,role:m.role,membershipType:m.membershipType,expiresAt:null,projectLabel:null})),projects:teamProjects(team.id),roles:roles.filter(r=>r.teamId===team.id&&r.status==='OPEN'),reviews:[]};}
  if(parts.length===2&&method==='PUT'){auth(team.id,['OWNER']);restricted(team);const fields={name:required(b.name,'Name',120),summary:required(b.summary,'Summary'),availability:required(b.availability,'Availability',80),projectRange:required(b.projectRange,'Project range',120)};Object.assign(team,fields);invitations.filter(i=>i.teamId===team.id).forEach(i=>i.teamName=team.name);leads.filter(l=>l.teamId===team.id).forEach(l=>l.teamName=team.name);audit(team.id,'TEAM_UPDATED',team.name);return teamDto(team,true);}
  if(sub==='workspace'&&method==='GET'){const m=auth(team.id,roleNames);const commercial=['OWNER','BUSINESS_MANAGER'].includes(m.role),recruiting=['OWNER','RECRUITER'].includes(m.role),planning=['OWNER','PROJECT_MANAGER'].includes(m.role);return {team:teamDto(team,true),myRole:m.role,members:members(team.id).map(memberDto),invitations:recruiting?invitations.filter(i=>i.teamId===team.id):[],roles:roles.filter(r=>r.teamId===team.id),applications:recruiting?applications.filter(a=>a.teamId===team.id):[],leads:commercial?leads.filter(l=>l.teamId===team.id):[],projects:teamProjects(team.id),staffing:planning?staffing.filter(s=>s.teamId===team.id).map(s=>({...s,status:s.status==='ACTIVE'&&(Date.parse(s.endsAt)<=Date.now()||!members(team.id).some(m=>m.userId===s.userId))?'COMPLETED':s.status})):[],activity:activity.filter(a=>a.teamId===team.id&&(a.scope==='GENERAL'||a.scope==='COMMERCIAL'&&commercial||a.scope==='RECRUITMENT'&&recruiting||a.scope==='STAFFING'&&planning)).map(a=>{const {scope:_,teamId:__,...rest}=a;return rest})};}
  if(sub==='candidates'&&method==='GET'){recruit();const q=(url.searchParams.get('q')||'').toLowerCase();return {items:actors.filter(a=>approved(a.id)&&a.name.toLowerCase().includes(q)&&!members(team.id).some(m=>m.userId===a.id)).map(a=>({id:a.id,name:a.name,headline:'Independent software builder',skills:'React, Product design'}))};}
  if(sub==='invitations'&&method==='POST'){recruit();if(mine(team.id)!.role==='RECRUITER')ensure(!b.role||b.role==='MEMBER','Recruiters may only invite members.',403);return invite(team,b);}
  if(sub==='members') {
   const m=members(team.id).find(m=>m.userId===parts[3]);ensure(m,'Active member not found.',404);
   if(method==='PATCH'){auth(team.id,['OWNER']);ensure(m!.role!=='OWNER'&&roleNames.includes(b.role)&&b.role!=='OWNER','Owner roles cannot be changed through this action.');m!.role=b.role;audit(team.id,'MEMBER_ROLE_UPDATED',`${m!.name}: ${b.role}`);return memberDto(m!);}
   if(method==='DELETE'){if(actorId!==m!.userId)auth(team.id,['OWNER']);ensure(m!.role!=='OWNER','The team owner cannot be removed or leave without an ownership transfer.');memberships=memberships.filter(x=>x!==m);contributions=contributions.filter(c=>c.teamId!==team.id||!products.some(p=>p.id===c.productId&&p.ownerId===m!.userId));staffing.filter(s=>s.teamId===team.id&&s.userId===m!.userId).forEach(s=>s.status='COMPLETED');leads.filter(l=>l.teamId===team.id&&l.assigneeId===m!.userId).forEach(l=>l.assigneeId=null);audit(team.id,'MEMBER_LEFT',m!.name);return {ok:true};}
  }
  if(sub==='roles') {
   if(parts.length===3&&method==='POST'){recruit();const r={id:id(),teamId:team.id,title:required(b.title,'Title',160),description:required(b.description,'Description'),skills:skills(b.skills),contractType:(ensure(['PERMANENT','CONTRACT'].includes(b.contractType),'Choose PERMANENT or CONTRACT.'),b.contractType),compensationBand:required(b.compensationBand,'Compensation band',160),status:'OPEN'};roles.push(r);audit(team.id,'ROLE_CREATED',r.title,'RECRUITMENT');return r;}
   const r=roles.find(r=>r.id===parts[3]&&r.teamId===team.id);ensure(r,'Role not found.',404);
   if(parts.length===4&&method==='PATCH'){recruit();ensure(['OPEN','CLOSED'].includes(b.status),'Choose OPEN or CLOSED.');r!.status=b.status;audit(team.id,'ROLE_UPDATED',r!.title,'RECRUITMENT');return r;}
   if(parts[4]==='applications'&&method==='POST'){ensure(team.status==='ACTIVE'&&r!.status==='OPEN','This role is not accepting applications.');ensure(approved(actorId),'An approved builder is required.',403);ensure(!mine(team.id),'Existing members cannot apply to their team.');ensure(!applications.some(a=>a.roleId===r!.id&&a.userId===actorId),'You have already applied.');const a={id:id(),teamId:team.id,roleId:r!.id,roleTitle:r!.title,userId:actorId,name:person(actorId)!.name,message:required(b.message,'Message'),status:'APPLIED'};applications.push(a);audit(team.id,'APPLICATION_SUBMITTED',r!.title,'RECRUITMENT');return a;}
  }
  if(sub==='applications'&&parts[4]==='decision'&&method==='POST'){recruit();const a=applications.find(a=>a.id===parts[3]&&a.teamId===team.id);ensure(a,'Application not found.',404);ensure(['SHORTLISTED','REJECTED','INVITED'].includes(b.status),'Invalid decision.');ensure(['APPLIED','SHORTLISTED'].includes(a!.status),'This application already has a final decision.');const openRole=roles.find(r=>r.id===a!.roleId&&r.status==='OPEN');ensure(openRole,'Role is no longer open.',404);ensure(!(a!.status==='SHORTLISTED'&&b.status==='SHORTLISTED'),'This application is already shortlisted.',409);if(b.status==='INVITED')invite(team,{userId:a!.userId,role:'MEMBER',membershipType:openRole!.contractType,expiresAt:openRole!.contractType==='CONTRACT'?future(30):null});a!.status=b.status;audit(team.id,'APPLICATION_DECISION',`${a!.name}: ${b.status}`,'RECRUITMENT');return a;}
  if(sub==='leads') {
   if(parts.length===3&&method==='POST'){ensure(actorId!=='unverified','Verify your email before submitting interest.',403);ensure(team.status==='ACTIVE','This team cannot receive inquiries.',403);ensure(!mine(team.id),'Team members cannot submit inquiries to their own team.',403);const l={id:id(),teamId:team.id,teamName:team.name,clientId:actorId,clientName:person(actorId)!.name,title:required(b.title,'Title',160),description:required(b.description,'Description'),budget:required(b.budget,'Budget',160),timeline:required(b.timeline,'Timeline',160),status:'NEW',assigneeId:null,followUpAt:null,notes:[]};leads.push(l);audit(team.id,'LEAD_RECEIVED',l.title,'COMMERCIAL');return l;}
   if(parts.length===5&&parts[4]==='respond'&&method==='POST'){const l=leads.find(l=>l.id===parts[3]&&l.teamId===team.id&&l.clientId===actorId);ensure(l,'Client request not found.',404);ensure(actorId!=='unverified','Verify your email first.',403);restricted(team);ensure(l!.status==='PROPOSAL_SENT','Only proposals awaiting your response can be acknowledged.',409);ensure(['ACCEPT','DECLINE'].includes(b.action),'Choose ACCEPT or DECLINE.');l!.status=b.action==='ACCEPT'?'WON':'LOST';audit(team.id,'CLIENT_'+b.action,'Client reported a proposal decision; external agreement required','COMMERCIAL');return {...l,notes:[],assigneeId:null,followUpAt:null};}
   if(method==='PATCH'){auth(team.id,['OWNER','BUSINESS_MANAGER']);restricted(team);const l=leads.find(l=>l.id===parts[3]&&l.teamId===team.id);ensure(l,'Lead not found.',404);if('status' in b)ensure(b.status===l!.status||transitions[l!.status]?.includes(b.status),'This lead stage transition is not allowed.',409);if(b.assigneeId!=null)ensure(members(team.id).some(m=>m.userId===b.assigneeId),'Assign an active team member.');const followUpAt=b.followUpAt===null?null:b.followUpAt===undefined?undefined:date(b.followUpAt,'Follow-up');const note=b.note===undefined||b.note===''?null:required(b.note,'Note',3000);if('status' in b)l!.status=b.status;if('assigneeId' in b)l!.assigneeId=b.assigneeId;if(followUpAt!==undefined)l!.followUpAt=followUpAt;if(note)l!.notes.push({text:note,createdAt:now(),actorName:person(actorId)!.name});audit(team.id,'LEAD_UPDATED',note||`${l!.title}: ${l!.status}`,'COMMERCIAL');return l;}
  }
  if(sub==='projects') {
   if(method==='POST'){auth(team.id,roleNames);restricted(team);const p=products.find(p=>p.id===b.productId);ensure(p&&p.ownerId===actorId&&p.visibility==='PUBLIC'&&p.approvalStatus==='APPROVED'&&p.lifecycleStatus==='ACTIVE','Only the product owner may contribute their approved public product.',403);ensure(!contributions.some(c=>c.teamId===team.id&&c.productId===p!.id),'This product is already contributed.');contributions.push({teamId:team.id,productId:p!.id});audit(team.id,'PROJECT_CONTRIBUTED',p!.title);return teamProjects(team.id).find(x=>x.id===p!.id);}
   if(method==='DELETE'){const c=contributions.find(c=>c.teamId===team.id&&c.productId===parts[3]);ensure(c,'Contribution not found.',404);const p=products.find(p=>p.id===parts[3]);ensure(p?.ownerId===actorId||mine(team.id)?.role==='OWNER','Only the original product owner or team owner may remove this contribution.',403);contributions=contributions.filter(x=>x!==c);audit(team.id,'PROJECT_REMOVED',p!.title);return {ok:true};}
  }
  if(sub==='staffing'){auth(team.id,['OWNER','PROJECT_MANAGER']);restricted(team);if(parts.length===3&&method==='POST'){const m=members(team.id).find(m=>m.userId===b.userId);ensure(m,'Staffing requires an active team member.');const endsAt=date(b.endsAt,'End date');ensure(!m!.expiresAt||Date.parse(endsAt)<=Date.parse(m!.expiresAt),'Staffing cannot outlast contract membership.');const s={id:id(),teamId:team.id,userId:m!.userId,name:m!.name,projectLabel:required(b.projectLabel,'Project label',160),skills:skills(b.skills),endsAt,status:'ACTIVE'};staffing.push(s);audit(team.id,'STAFFING_CREATED',s.projectLabel,'STAFFING');return s;}if(method==='PATCH'){const s=staffing.find(s=>s.id===parts[3]&&s.teamId===team.id);ensure(s,'Staffing not found.',404);ensure(['ACTIVE','COMPLETED'].includes(b.status),'Invalid staffing status.');if(b.status==='ACTIVE')ensure(s!.status==='ACTIVE'&&Date.parse(s!.endsAt)>Date.now()&&members(team.id).some(m=>m.userId===s!.userId),'An active member and future end date are required.');s!.status=b.status;audit(team.id,'STAFFING_UPDATED',s!.projectLabel,'STAFFING');return s;}}
  fail(404,'Demo endpoint not found.');
 }
 return {actors:structuredClone(actors),reset,setActor:(value:string)=>{ensure(person(value),'Unknown sample actor.');actorId=value},request:async(path:string,init?:RequestInit)=>structuredClone(handle(path,init))};
}
