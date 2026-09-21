import test,{after} from 'node:test';
import assert from 'node:assert/strict';
import {createServer} from 'vite';
const vite=await createServer({appType:'custom',configFile:false,server:{middlewareMode:true}});
after(()=>vite.close());
const {createDemoState,applyDemoAction:act,publicDemoProjects}=await vite.ssrLoadModule('/lib/demo-state.ts');
const brief={type:'inquire',productId:'stockroom',description:'Manage stock transfers between our five regional stores.',budgetBand:'USD_3K_10K',timelineBand:'ONE_TO_THREE_MONTHS'};

test('demo inquiry follows qualification, client decisions and moderated review without network requests',()=>{
 const originalFetch=globalThis.fetch;globalThis.fetch=()=>{throw Error('Demo must not contact a real API')};
 try{
  let s=createDemoState();const initial=structuredClone(s);
  s=act(s,'alex',brief);const id=s.requests[0].id;
  assert.equal(s.requests[0].status,'CREATED_UNVERIFIED');assert.deepEqual(initial.requests,[]);
  const step=(person,value)=>{s=act(s,person,{type:'request',id,step:value})};
  assert.throws(()=>step('leah','respond'),/not available/);
  assert.throws(()=>step('leah','confirm-email'),/Only the client/);
  step('alex','confirm-email');step('leah','respond');step('leah','discuss');step('leah','propose');step('leah','request-hire');
  assert.throws(()=>step('leah','accept'),/client confirmation/);
  step('alex','reject');assert.equal(s.requests[0].status,'DISCUSSION');
  step('leah','propose');step('leah','request-hire');step('alex','accept');step('leah','start-work');step('leah','request-completion');
  step('alex','reject');assert.equal(s.requests[0].status,'IN_PROGRESS');
  step('leah','request-completion');step('alex','accept');assert.equal(s.requests[0].status,'COMPLETED');
  s=act(s,'alex',{type:'review',id,rating:5,reviewText:'The stock transfer workflow meets the agreed requirements.',visibility:'ANONYMOUS'});
  assert.equal(s.requests[0].review.status,'PENDING');
  assert.throws(()=>act(s,'alex',{type:'review',id,rating:4,reviewText:'Duplicate review attempt.',visibility:'NAMED'}),/one review/);
  assert.throws(()=>act(s,'leah',{type:'moderate-review',id,publish:true,reason:'Publish this'}),/administrator/);
  s=act(s,'sam',{type:'moderate-review',id,publish:true,reason:'Eligible completed engagement, no confidential details.'});
  assert.equal(s.requests[0].review.status,'PUBLISHED');assert.equal(s.requests[0].review.visibility,'ANONYMOUS');assert.ok(s.requests[0].events.length>10);
 }finally{globalThis.fetch=originalFetch;}
});
test('demo publishing enforces proof, review and three active slots; a failed action leaves state unchanged',()=>{
 let s=createDemoState();const change=(person,decision)=>{s=act(s,person,{type:'project',id:'customer-portal',decision,reason:'Evidence and contribution reviewed.'})};
 assert.throws(()=>change('leah','submit'),/proof/);change('leah','proof');change('leah','submit');
 assert.throws(()=>change('leah','approve'),/administrator/);change('sam','approve');
 const before=structuredClone(s);assert.throws(()=>change('leah','activate'),/Three active/);assert.deepEqual(s,before);
 assert.equal(publicDemoProjects(s).some(p=>p.id==='customer-portal'),false);
 s=act(s,'leah',{type:'project',id:'stockroom',decision:'archive'});change('leah','activate');
 assert.equal(publicDemoProjects(s).some(p=>p.id==='customer-portal'),true);assert.equal(publicDemoProjects(s).some(p=>p.id==='stockroom'),false);
 assert.equal(s.projects.filter(p=>p.ownerId==='leah'&&p.lifecycleStatus==='ACTIVE').length,3);
});
test('a developer can commission another builder; self inquiries and early reviews are rejected',()=>{
 let s=createDemoState();assert.throws(()=>act(s,'leah',brief),/yourself/);
 s=act(s,'leah',{...brief,productId:'booklane'});assert.equal(s.requests[0].builderId,'daniel');assert.equal(s.requests[0].clientId,'leah');
 assert.throws(()=>act(s,'leah',{type:'review',id:s.requests[0].id,rating:5,reviewText:'An early review should not be accepted.',visibility:'ANONYMOUS'}),/confirmed completion/);
});
test('sample profile approval, automatic activation with a free slot, and report restriction update discovery',()=>{
 let s=createDemoState();s=act(s,'alex',{type:'profile',bio:'I build booking software for local service businesses.',availability:'AVAILABLE_NOW'});s=act(s,'alex',{type:'submit-profile'});
 s=act(s,'sam',{type:'moderate-profile',id:'alex',approve:true,reason:'Profile ready for the community.'});
 s=act(s,'alex',{type:'draft',title:'Salon schedule',summary:'Booking for independent salons.',description:'An appointment scheduler with a shared view for salon teams.',contribution:'Designed and built scheduling and team availability.',category:'Booking',technology:'React',projectType:'SAAS',rightsConfirmed:true});const id=s.projects.at(-1).id;
 for(const [actor,decision]of [['alex','proof'],['alex','submit'],['sam','approve']])s=act(s,actor,{type:'project',id,decision,reason:'Evidence reviewed.'});
 assert.ok(publicDemoProjects(s).some(p=>p.id===id));
 s=act(s,'leah',{type:'report',productId:id,reason:'CONFIDENTIAL_DATA',detail:'Sample report of confidential information in the showcase.'});
 s=act(s,'sam',{type:'resolve-report',id:s.reports[0].id,hide:true,reason:'Restrict the sample showcase for review.'});
 assert.ok(!publicDemoProjects(s).some(p=>p.id===id));
});
test('demo saves are per account, JSON persistence retains state, and reset starts clean',()=>{
 let s=act(createDemoState(),'alex',{type:'save',id:'stockroom'});assert.deepEqual(s.saved.alex,['stockroom']);assert.equal(s.saved.leah,undefined);
 s=JSON.parse(JSON.stringify(s));assert.equal(s.version,1);assert.deepEqual(s.saved.alex,['stockroom']);
 s=act(s,'alex',{type:'save',id:'stockroom'});assert.deepEqual(s.saved.alex,[]);assert.deepEqual(createDemoState().saved,{});
});
