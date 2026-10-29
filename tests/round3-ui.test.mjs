import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import test from 'node:test';
import ts from 'typescript';

const source=readFileSync(new URL('../app/components/explore.tsx',import.meta.url),'utf8');
const tree=ts.createSourceFile('explore.tsx',source,ts.ScriptTarget.Latest,true,ts.ScriptKind.TSX);
const explore=tree.statements.find(node=>ts.isFunctionDeclaration(node)&&node.name?.text==='Explore');
assert.ok(explore?.body,'The actual Explore component is available');
const save=explore.body.statements.find(node=>ts.isFunctionDeclaration(node)&&node.name?.text==='save');
assert.ok(save,'The actual Explore save routine is available');
function hasSavedEndpoint(node){
  return ts.isStringLiteral(node)&&node.text==='/me/saved-products?ids='
    ||Boolean(ts.forEachChild(node,hasSavedEndpoint));
}
const savedRead=explore.body.statements.filter(node=>
  ts.isExpressionStatement(node)&&ts.isCallExpression(node.expression)&&
  ts.isIdentifier(node.expression.expression)&&node.expression.expression.text==='useEffect'&&
  node.expression.arguments[0]&&hasSavedEndpoint(node.expression.arguments[0])
);
assert.equal(savedRead.length,1,'Exactly one actual saved-list effect is available');
const compiled=stripTypeScriptTypes(save.getText(tree),{mode:'transform'});
const compiledRead=stripTypeScriptTypes(savedRead[0].getText(tree),{mode:'transform'});

function fixture(initial=[]){
  const state={saved:[...initial],saving:[],error:'',notice:''},savingRef={current:new Set()},savedRevision={current:0},savedChanges={current:new Map()},requests=[];
  const setSaved=value=>{state.saved=typeof value==='function'?value(state.saved):value;};
  function render(){
    const context={preview:false,saved:state.saved,savingRef,savedRevision,savedChanges,setSaved,setSaving:value=>{state.saving=Array.isArray(value)?value:value?[value]:[];},setError:value=>{state.error=value;},setSaveNotice:value=>{state.notice=value;},api:(path,options)=>{const response=Promise.withResolvers();requests.push({path,method:options.method,...response});return response.promise;}};
    return Function(...Object.keys(context),compiled+';return save;')(...Object.values(context));
  }
  function load(){
    const response=Promise.withResolvers();
    const context={preview:false,visibleIds:'alpha,beta',savedRevision,savedChanges,setSaved,setLoadedSavedIds:()=>{},setError:value=>{state.error=value;},api:()=>response.promise,useEffect:effect=>effect()};
    Function(...Object.keys(context),compiledRead)(...Object.values(context));
    return response;
  }
  return {state,requests,render,load};
}

test('an ordinary acknowledged save and removal preserve the requested state',async()=>{
  const work=fixture();
  const added=work.render()('alpha');assert.equal(work.requests[0].method,'POST');
  work.requests[0].resolve({ok:true});await added;assert.deepEqual(work.state.saved,['alpha']);
  const removed=work.render()('alpha');assert.equal(work.requests[1].method,'DELETE');
  work.requests[1].resolve({ok:true});await removed;assert.deepEqual(work.state.saved,[]);
});

test('parallel project saves cannot re-enable or duplicate a still-pending project',async()=>{
  const work=fixture();
  const alpha=work.render()('alpha'),beta=work.render()('beta');
  const duplicate=work.render()('alpha');
  assert.equal(work.requests.length,2,'A second project must not unlock the pending alpha save');
  assert.deepEqual(new Set(work.state.saving),new Set(['alpha','beta']));
  work.requests[1].resolve({ok:true});await beta;
  assert.deepEqual(work.state.saving,['alpha'],'Finishing beta must leave alpha pending');
  work.requests[0].resolve({ok:true});await Promise.all([alpha,duplicate]);
  assert.deepEqual(new Set(work.state.saved),new Set(['alpha','beta']));
  assert.deepEqual(work.state.saving,[]);
});

test('a save acknowledgement sets its intended state even if a refreshed list already includes it',async()=>{
  const work=fixture(),waiting=work.render()('alpha');
  work.state.saved=['alpha'];
  work.requests[0].resolve({ok:true});await waiting;
  assert.deepEqual(work.state.saved,['alpha']);
});

test('a rejected project save preserves its prior state and can be retried',async()=>{
  const work=fixture(['alpha']),waiting=work.render()('alpha');
  work.requests[0].reject(new Error('Project save unavailable.'));await waiting;
  assert.deepEqual(work.state.saved,['alpha']);assert.deepEqual(work.state.saving,[]);
  assert.equal(work.state.error,'Project save unavailable.');
  const retried=work.render()('alpha');assert.equal(work.requests[1].method,'DELETE');
  work.requests[1].resolve({ok:true});await retried;
  assert.deepEqual(work.state.saved,[]);assert.equal(work.state.error,'');
});

test('an older saved-list read cannot overwrite a save or removal acknowledged while it was pending',async()=>{
  for(const initiallySaved of [false,true]){
    const work=fixture(initiallySaved?['alpha']:[]),write=work.render()('alpha'),read=work.load();
    work.requests[0].resolve({ok:true});await write;
    read.resolve({items:initiallySaved?[{id:'alpha'}]:[]});await read.promise;await Promise.resolve();
    assert.deepEqual(work.state.saved,initiallySaved?[]:['alpha']);
  }
});

test('a current saved-list read with no intervening writes replaces the visible saved state',async()=>{
  const work=fixture(['alpha']),read=work.load();read.resolve({items:[{id:'beta'}]});await read.promise;await Promise.resolve();
  assert.deepEqual(work.state.saved,['beta']);
});
