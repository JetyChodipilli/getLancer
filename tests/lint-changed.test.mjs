import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import {copyFileSync,existsSync,mkdirSync,mkdtempSync,readFileSync,rmSync,writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {dirname,join} from 'node:path';
import test from 'node:test';

const lintScript=new URL('../scripts/lint-changed.mjs',import.meta.url);
const missing='f'.repeat(40);
const sources=['source/unchanged.js','source/early.ts','source/latest.ts','source/with spaces;literal.tsx','source/deleted.js'];

function repository(t){
  const root=mkdtempSync(join(tmpdir(),'getlancer-lint-changed-'));
  t.after(()=>rmSync(root,{recursive:true,force:true}));
  const env=Object.fromEntries(Object.entries(process.env).filter(([key])=>!key.startsWith('GIT_')&&!key.startsWith('LINT_')));
  Object.assign(env,{CI:'true',GIT_CONFIG_NOSYSTEM:'1',GIT_CONFIG_GLOBAL:join(root,'unused-global-gitconfig'),GIT_TERMINAL_PROMPT:'0',LC_ALL:'C'});
  function git(...args){
    const result=spawnSync('git',['-c','core.hooksPath=/dev/null','-c','commit.gpgSign=false','-c','user.name=Lint fixture','-c','user.email=lint-fixture@example.test',...args],{cwd:root,env,encoding:'utf8',timeout:10000});
    assert.equal(result.status,0,String(result.error??'')+result.stdout+result.stderr);
    return result.stdout.trim();
  }
  function write(path,contents){const target=join(root,path);mkdirSync(dirname(target),{recursive:true});writeFileSync(target,contents);}
  function commit(message){git('add','--all');git('commit','--quiet','-m',message);return git('rev-parse','HEAD');}
  git('init','--quiet','--initial-branch=fixture');
  write('.gitignore','node_modules/\nlint-received.jsonl\n');
  write('package.json','{"type":"module"}\n');
  mkdirSync(join(root,'scripts'));copyFileSync(lintScript,join(root,'scripts/lint-changed.mjs'));
  for(const path of sources)write(path,'export const value=0;\n');
  const initial=commit('Initial tracked sources');
  write('node_modules/eslint/package.json','{"type":"module"}\n');
  write('node_modules/eslint/bin/eslint.js',`
import {appendFileSync} from 'node:fs';
appendFileSync(process.env.LINT_CAPTURE,JSON.stringify(process.argv.slice(2))+'\\n');
process.exitCode=Number(process.env.LINT_RECEIVER_STATUS||0);
`);
  function lint(base,head=git('rev-parse','HEAD'),receiverStatus=0){
    const capture=join(root,'lint-received.jsonl');rmSync(capture,{force:true});
    const runEnv={...env,LINT_HEAD_SHA:head,LINT_CAPTURE:capture,LINT_RECEIVER_STATUS:String(receiverStatus)};
    if(base!==undefined)runEnv.LINT_BASE_SHA=base;
    const result=spawnSync(process.execPath,[join(root,'scripts/lint-changed.mjs')],{cwd:root,env:runEnv,encoding:'utf8',timeout:10000});
    const calls=existsSync(capture)?readFileSync(capture,'utf8').trim().split('\n').filter(Boolean).map(line=>JSON.parse(line)):[];
    for(const call of calls)assert.deepEqual(call.slice(0,2),['--no-warn-ignored','--']);
    return {result,calls,files:calls.flatMap(call=>call.slice(2))};
  }
  return {root,git,write,commit,initial,lint};
}
function passed(run){assert.equal(run.result.status,0,String(run.result.error??'')+run.result.stdout+run.result.stderr);}

test('available before commit selects its exact diff and preserves filenames as arguments',t=>{
  const repo=repository(t);repo.git('update-ref','refs/remotes/origin/main',repo.initial);
  repo.write('source/early.ts','export const earlierBranchChange=1;\n');const base=repo.commit('Change before the supplied base');
  repo.write('source/with spaces;literal.tsx','export const changed=1;\n');
  repo.write('source/added.cjs','module.exports=1;\n');repo.write('notes.md','Not a lint target.\n');
  rmSync(join(repo.root,'source/deleted.js'));const head=repo.commit('Current push changes');
  repo.write('source/untracked.ts','export const untracked=1;\n');
  const run=repo.lint(base,head);passed(run);
  assert.deepEqual(run.files,['source/added.cjs','source/with spaces;literal.tsx']);
});

for(const before of ['missing','zero','unset'])test(`${before} before commit covers cumulative branch changes`,t=>{
  const repo=repository(t);repo.git('update-ref','refs/remotes/origin/main',repo.initial);
  repo.write('source/early.ts','export const earlierBranchChange=1;\n');repo.commit('Earlier branch commit');
  repo.write('source/latest.ts','export const latestBranchChange=1;\n');const head=repo.commit('Latest branch commit');
  const run=repo.lint(before==='missing'?missing:before==='zero'?'0'.repeat(40):undefined,head);passed(run);
  assert.deepEqual(run.files,['source/early.ts','source/latest.ts']);
});

for(const anchor of ['missing','head'])test(`${anchor} default-branch anchor covers all tracked HEAD sources`,t=>{
  const repo=repository(t);
  repo.write('source/early.ts','export const earlierBranchChange=1;\n');repo.commit('Earlier branch commit');
  repo.write('source/latest.ts','export const latestBranchChange=1;\n');const head=repo.commit('Latest branch commit');
  if(anchor==='head')repo.git('update-ref','refs/remotes/origin/main',head);
  const run=repo.lint(missing,head);passed(run);
  assert.deepEqual(run.files,['scripts/lint-changed.mjs',...sources].sort());
});

test('malformed and abbreviated SHAs fail before lint can fall back',t=>{
  const repo=repository(t);
  for(const [base,head,name] of [['abc123',repo.initial,'LINT_BASE_SHA'],['main;echo unsafe',repo.initial,'LINT_BASE_SHA'],[missing,'HEAD','LINT_HEAD_SHA'],[missing,'0'.repeat(39),'LINT_HEAD_SHA']]){
    const run=repo.lint(base,head);
    assert.equal(run.result.status,1);assert.match(run.result.stderr,new RegExp(name+' must be a full Git commit SHA\\.'));
    assert.deepEqual(run.calls,[]);
  }
});

test('present non-commit objects and a missing head cannot turn into fallback lint success',t=>{
  const repo=repository(t);repo.git('tag','--annotate','fixture-tag','-m','Annotated fixture tag');
  const objects=[repo.git('rev-parse','HEAD:source/early.ts'),repo.git('rev-parse','HEAD^{tree}'),repo.git('rev-parse','refs/tags/fixture-tag')];
  for(const object of objects)for(const name of ['LINT_BASE_SHA','LINT_HEAD_SHA']){
    const run=repo.lint(name==='LINT_BASE_SHA'?object:repo.initial,name==='LINT_HEAD_SHA'?object:repo.initial);
    assert.equal(run.result.status,1);assert.match(run.result.stderr,new RegExp(name+' must identify a Git commit\\.'));assert.deepEqual(run.calls,[]);
  }
  const run=repo.lint(repo.initial,missing);assert.equal(run.result.status,1);assert.match(run.result.stderr,/LINT_HEAD_SHA must identify an available Git commit\./);assert.deepEqual(run.calls,[]);
});

test('a corrupt present base object fails closed instead of enabling fallback',t=>{
  const repo=repository(t);
  const base=repo.git('commit-tree',repo.git('rev-parse','HEAD^{tree}'),'-p',repo.initial,'-m','Unreferenced previous push');
  const object=join(repo.root,'.git/objects',base.slice(0,2),base.slice(2));
  rmSync(object);writeFileSync(object,'Corrupt disposable commit fixture.\n');
  const run=repo.lint(base,repo.initial);
  assert.equal(run.result.status,1);assert.match(run.result.stderr,/Changed-file lint could not inspect Git:/);assert.deepEqual(run.calls,[]);
});

test('fallback preserves ESLint failure instead of treating coverage as a passing gate',t=>{
  const repo=repository(t);const run=repo.lint(missing,repo.initial,17);
  assert.equal(run.result.status,17);assert.deepEqual(run.files,['scripts/lint-changed.mjs',...sources].sort());
  assert.doesNotMatch(run.result.stdout,/Changed-file ESLint passed:/);
});
