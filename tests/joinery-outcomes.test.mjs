import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import test from 'node:test';
import ts from 'typescript';

// Execute the production callback itself. Mocking its parent change boundary
// isolates the cancellation result that used to disappear in these wrappers.
function routine(file,name,context){
 const source=ts.createSourceFile(file,readFileSync(new URL('../app/components/'+file,import.meta.url),'utf8'),ts.ScriptTarget.Latest,true,ts.ScriptKind.TSX);
 let found;function visit(node){if(!found&&ts.isFunctionDeclaration(node)&&node.name?.text===name)found=node;ts.forEachChild(node,visit);}visit(source);
 assert.ok(found,'Production callback exists: '+file+' / '+name);
 const body=stripTypeScriptTypes(found.getText(source),{mode:'transform'});
 return Function('context','with(context){return ('+body+');}')(context);
}
function fixture(outcome){
 const state={closed:0,changed:0,selection:0};
 const context={detail:{id:'record-1',requests:[{id:'request-1'}],milestones:[{id:'milestone-1'}]},purchase:{id:'purchase-1'},preview:{},selected:'record-1',base:'/engagements/record-1',dialog:'submit:request-1',
  change:async()=>{state.changed++;if(outcome==='error')throw Error('Write accepted, refresh unavailable.');return outcome==='cancel'?false:{id:'acknowledged-1'};},
  close:()=>state.closed++,setDialog:()=>state.closed++,setSelected:()=>state.selection++,setFileError:()=>{},
  validateHostingUpload:()=>{},uploadFile:{current:null},templateMinor:()=>150000,careLabel:value=>value,
  actionAvailable:()=>true,pendingRequests:{current:[]},setPendingRequestRows:()=>{},crypto:{randomUUID:()=> 'stable-request-key'},
 };
 return {state,context};
}
const data={productId:'product-1',title:'Record',version:'1.0.0',rightsConsent:'on',reason:'Synthetic review reason',description:'Synthetic request details',price:'1500',engagementId:'engagement-1',requestsPerCycle:'2',responseHours:'24',totalCycles:'6'};
const cases=[
 ['hosting-workspace.tsx','upload',[data],true],['hosting-workspace.tsx','submit',[data],true],['hosting-workspace.tsx','sampleReview',[data],true],['hosting-workspace.tsx','withdraw',[],true],
 ['maintenance-workspace.tsx','create',[data],true],['maintenance-workspace.tsx','offerAction',['send',true],true],['maintenance-workspace.tsx','authorize',[true],true],['maintenance-workspace.tsx','openRequest',[data],true],['maintenance-workspace.tsx','requestAction',['submit',data],true],
 ['template-workspace.tsx','create',[data],true],['template-workspace.tsx','upload',[new FormData()],false],['template-workspace.tsx','submit',[{id:'version-1'},true],true],['template-workspace.tsx','archive',[],true],['template-workspace.tsx','dispute',[data],true],
 ['delivery-workspace.tsx','saveSource',[{inquiryId:'inquiry-1'}],false],['delivery-workspace.tsx','milestoneAction',['milestone-1','submit',data],false],
];
for(const[file,name,args,closes]of cases)test(`${file} ${name} preserves cancellation, failure and acknowledgement`,async()=>{
 const cancelled=fixture('cancel');assert.equal(await routine(file,name,cancelled.context)(...args),false);assert.equal(cancelled.state.changed,1);assert.equal(cancelled.state.closed,0);assert.equal(cancelled.state.selection,0);
 const failed=fixture('error');await assert.rejects(routine(file,name,failed.context)(...args),/refresh unavailable/);assert.equal(failed.state.closed,0);
 const confirmed=fixture('confirmed');const result=await routine(file,name,confirmed.context)(...args);assert.notEqual(result,false);assert.notEqual(result,undefined);assert.equal(confirmed.state.changed,1);assert.equal(confirmed.state.closed,closes?1:0);
});

for(const[file,name,args]of cases.filter(([,name])=>!['create','upload','saveSource'].includes(name)))test(`${file} ${name} cannot acknowledge a missing record`,async()=>{
 const {state,context}=fixture('confirmed');context.detail=null;context.purchase=null;assert.equal(await routine(file,name,context)(...args),false);assert.equal(state.changed,0);assert.equal(state.closed,0);
});
