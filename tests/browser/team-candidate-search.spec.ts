import {test,expect,type Page} from '@playwright/test';
import {build} from 'esbuild';
import {readFileSync} from 'node:fs';

type SearchWindow=Window&{
 candidateRequests:string[];
 holdCandidates:boolean;
 releaseCandidates?:()=>void;
};

async function bundle(){
 const result=await build({
  stdin:{contents:`import {createRoot} from 'react-dom/client';import TeamWorkspace from './app/components/team-workspace';createRoot(document.getElementById('root')).render(<TeamWorkspace demo/>);`,loader:'tsx',resolveDir:process.cwd()},
  bundle:true,write:false,platform:'browser',format:'iife',jsx:'automatic',loader:{'.css':'empty'},define:{'process.env.NODE_ENV':'"test"'},
  plugins:[{
   name:'team-search-harness',setup(builder){
    builder.onResolve({filter:/^next\/link$/},()=>({path:'next-link',namespace:'harness'}));
    builder.onLoad({filter:/.*/,namespace:'harness'},()=>({contents:`export default function Link({children,...props}){return <a {...props}>{children}</a>}`,loader:'jsx',resolveDir:process.cwd()}));
    builder.onLoad({filter:/\/lib\/team-demo\.ts$/},args=>({loader:'ts',contents:readFileSync(args.path,'utf8').replace('export function createTeamDemo()','function createBaseTeamDemo()')+`
     window.candidateRequests=[];window.holdCandidates=false;
     export function createTeamDemo(){const demo=createBaseTeamDemo(),original=demo.request;return {...demo,request(path,init){
      if(!path.includes('/candidates?'))return original(path,init);
      window.candidateRequests.push(path);const page=Number(new URL(path,'https://demo.invalid').searchParams.get('page')||0);
      const result={items:[{id:'candidate-'+page,name:'Search candidate '+page,headline:'Accessible frontend work',skills:'React'}],page,size:1,totalItems:2,totalPages:2,hasMore:page===0};
      if(page>0&&window.holdCandidates)return new Promise(resolve=>window.releaseCandidates=()=>resolve({...result,items:[{id:'stale',name:'Stale candidate',headline:'Previous search result',skills:'React'}]}));
      return Promise.resolve(result);
     }}}` }));
   }
  }],
 });
 return result.outputFiles[0].text;
}

async function search(page:Page){
 await page.setContent('<div id="root"></div>');await page.addScriptTag({content:await bundle()});
 await page.getByRole('tab',{name:'Members',exact:true}).click();await page.getByText('Find and invite a builder',{exact:true}).click();
 await page.getByLabel('Builder name or skill',{exact:true}).fill('React');
 await page.getByRole('button',{name:'Search builders',exact:true}).click();
 await expect(page.getByText('Search candidate 0 · Accessible frontend work',{exact:true})).toBeVisible();
}

test('repeating the same builder search restarts an exhausted continuation',async({page})=>{
 await search(page);await page.getByRole('button',{name:'Load more builders',exact:true}).click();
 await expect(page.getByText('Search candidate 1 · Accessible frontend work',{exact:true})).toBeVisible();
 await expect(page.getByRole('button',{name:'Load more builders',exact:true})).toHaveCount(0);
 await page.getByRole('button',{name:'Search builders',exact:true}).click();
 await expect(page.getByText('Search candidate 1 · Accessible frontend work',{exact:true})).toHaveCount(0);
 await page.getByRole('button',{name:'Load more builders',exact:true}).click();
 await expect(page.getByText('Search candidate 1 · Accessible frontend work',{exact:true})).toBeVisible();
 expect(await page.evaluate(()=>(window as unknown as SearchWindow).candidateRequests.map(path=>new URL(path,'https://demo.invalid').searchParams.get('page')||'0'))).toEqual(['0','1','0','1']);
});

test('repeating the same builder search ignores an older pending continuation',async({page})=>{
 await search(page);await page.evaluate(()=>(window as unknown as SearchWindow).holdCandidates=true);
 await page.getByRole('button',{name:'Load more builders',exact:true}).click();
 await expect(page.getByRole('button',{name:'Loading…',exact:true})).toBeDisabled();
 await page.getByRole('button',{name:'Search builders',exact:true}).click();
 await expect(page.getByRole('button',{name:'Load more builders',exact:true})).toBeEnabled();
 await page.evaluate(()=>{const state=window as unknown as SearchWindow;state.holdCandidates=false;state.releaseCandidates!()});
 await expect(page.getByText('Stale candidate · Previous search result',{exact:true})).toHaveCount(0);
 await page.getByRole('button',{name:'Load more builders',exact:true}).click();
 await expect(page.getByText('Search candidate 1 · Accessible frontend work',{exact:true})).toBeVisible();
 await expect(page.getByText('Stale candidate · Previous search result',{exact:true})).toHaveCount(0);
});
