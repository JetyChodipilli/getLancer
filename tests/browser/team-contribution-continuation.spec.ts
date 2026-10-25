import {test,expect,type Page} from '@playwright/test';
import {build} from 'esbuild';
import {readFileSync} from 'node:fs';

type State=Window&{productRequests:string[];teamWrites:Record<string,string>[];failProductPage:boolean;denyProductPage:boolean;holdProductPage:boolean;releaseProductPage?:()=>void};

async function fixture(availability='AVAILABLE_NOW'){
 const result=await build({
  stdin:{contents:`import {createRoot} from 'react-dom/client';import TeamWorkspace from './app/components/team-workspace';createRoot(document.getElementById('root')).render(<TeamWorkspace demo/>);`,loader:'tsx',resolveDir:process.cwd()},
  bundle:true,write:false,platform:'browser',format:'iife',jsx:'automatic',loader:{'.css':'empty'},define:{'process.env.NODE_ENV':'"test"'},
  plugins:[{name:'team-contribution-harness',setup(builder){
   builder.onResolve({filter:/^next\/link$/},()=>({path:'next-link',namespace:'harness'}));
   builder.onLoad({filter:/.*/,namespace:'harness'},()=>({contents:`export default function Link({children,...props}){return <a {...props}>{children}</a>}`,loader:'jsx',resolveDir:process.cwd()}));
   builder.onLoad({filter:/\/lib\/team-demo\.ts$/},args=>{
    const source=readFileSync(args.path,'utf8').replace('export function createTeamDemo()','function createBaseTeamDemo()')
     .replace("availability:'AVAILABLE_NOW',projectRange:'$5k–$25k'",`availability:${JSON.stringify(availability)},projectRange:'$5k–$25k'`)
     .replace("contributions=[{teamId:'team-studio',productId:'product-owner',consentedBy:'owner'}];",`
      products.push(...Array.from({length:56},(_,n)=>({...products.find(p=>p.ownerId==='member'),id:'member-older-'+n,slug:'member-older-'+n,title:'Member product '+n,updatedAt:new Date(Date.UTC(2025,0,1,0,0,n)).toISOString()})));
      contributions=[{teamId:'team-studio',productId:'product-owner',consentedBy:'owner'},{teamId:'team-studio',productId:'member-older-0',consentedBy:'member'}];`);
    return {loader:'ts',contents:source+`
     window.productRequests=[];window.teamWrites=[];window.failProductPage=false;window.denyProductPage=false;window.holdProductPage=false;
     export function createTeamDemo(){const demo=createBaseTeamDemo(),original=demo.request;return {...demo,request(path,init){
      if(init?.body&&(path==='/teams'||init.method==='PUT'))window.teamWrites.push(JSON.parse(init.body));
      if(!path.startsWith('/developer/products'))return original(path,init);
      window.productRequests.push(path);const next=Number(new URL(path,'https://demo.invalid').searchParams.get('page')||0)>0;
      if(next&&window.failProductPage){window.failProductPage=false;return Promise.reject(new Error('Product page unavailable. Retry.'))}
      if(next&&window.denyProductPage)return Promise.reject(Object.assign(new Error('Builder access changed.'),{status:403}));
      const result=original(path,init);
      if(next&&window.holdProductPage)return new Promise((resolve,reject)=>{window.releaseProductPage=()=>result.then(resolve,reject)});
      return result;
     }}}`};
   });
  }}],
 });return result.outputFiles[0].text;
}

async function open(page:Page,availability?:string){await page.setContent('<div id="root"></div>');await page.addScriptTag({content:await fixture(availability)});await expect(page.getByRole('heading',{name:'Northstar Studio',exact:true})).toBeVisible()}
async function memberProjects(page:Page){await open(page);await page.locator('.studio-preview > summary').click();await page.getByLabel('Preview actor',{exact:false}).selectOption('member');await expect(page.getByText('Member access',{exact:true})).toBeVisible();await page.getByRole('tab',{name:'Projects',exact:true}).click()}
const row=(page:Page,title:string)=>page.locator('.team-row').filter({has:page.getByRole('heading',{name:title,exact:true})});

test('a member withdraws older consent independently and shares beyond 50 products with retry and retained selection',async({page})=>{
 await memberProjects(page);
 await row(page,'Member product 0').getByRole('button',{name:'Remove from team',exact:true}).click();
 await expect(row(page,'Member product 0')).toHaveCount(0);
 await page.getByText('Contribute your product',{exact:true}).click();
 const picker=page.getByLabel('Your approved public product',{exact:true});
 await expect(picker.locator('option')).toHaveCount(51);await expect(picker.locator('option[value="member-older-0"]')).toHaveCount(0);
 await picker.selectOption('product-member');
 await page.evaluate(()=>(window as unknown as State).failProductPage=true);
 const more=page.getByRole('button',{name:'Load more of your products',exact:true});
 await more.click();await expect(page.getByRole('alert')).toHaveText('Product page unavailable. Retry.');await expect(picker).toHaveValue('product-member');
 await more.focus();await more.press('Enter');
 await expect(picker.locator('option')).toHaveCount(58);await expect(picker).toHaveValue('product-member');
 await expect(page.getByRole('status').filter({hasText:'7 more records loaded.'})).toBeFocused();
 await picker.selectOption('member-older-0');await page.getByRole('button',{name:'Consent and share product',exact:true}).click();
 await expect(row(page,'Member product 0').getByRole('button',{name:'Remove from team',exact:true})).toBeVisible();
 await row(page,'Member product 0').getByRole('button',{name:'Remove from team',exact:true}).click();await expect(row(page,'Member product 0')).toHaveCount(0);
 const paths=await page.evaluate(()=>(window as unknown as State).productRequests);
 expect(paths.filter(path=>path.includes('page=1'))).toHaveLength(2);
 expect(paths.every(path=>path.includes('lifecycleStatus=ACTIVE'))).toBe(true);
});

test('denied product continuation clears private choices and an old actor response cannot append',async({page})=>{
 await memberProjects(page);await page.getByText('Contribute your product',{exact:true}).click();
 await page.evaluate(()=>(window as unknown as State).denyProductPage=true);
 await page.getByRole('button',{name:'Load more of your products',exact:true}).click();
 await expect(page.getByRole('alert')).toContainText('Builder access changed.');
 await expect(page.getByLabel('Your approved public product',{exact:true}).locator('option')).toHaveCount(1);
 await expect(page.getByRole('button',{name:'Consent and share product',exact:true})).toBeDisabled();
 await page.evaluate(()=>(window as unknown as State).denyProductPage=false);
 await page.getByRole('button',{name:'Retry your products',exact:true}).click();
 // Reload restores the initial page and focuses the picker in the reopened panel.
 await expect(page.getByRole('button',{name:'Retry your products',exact:true})).toHaveCount(0);
 await expect(page.getByLabel('Your approved public product',{exact:true})).toBeFocused();
 await page.evaluate(()=>(window as unknown as State).holdProductPage=true);
 await page.getByRole('button',{name:'Load more of your products',exact:true}).click();
 await expect(page.getByRole('button',{name:'Loading…',exact:true})).toBeDisabled();
 await page.getByLabel('Preview actor',{exact:false}).selectOption('owner');
 await expect(page.getByText('Owner access',{exact:true})).toBeVisible();
 await page.evaluate(()=>{const state=window as unknown as State;state.holdProductPage=false;state.releaseProductPage!()});
 await page.getByRole('tab',{name:'Projects',exact:true}).click();await page.getByText('Contribute your product',{exact:true}).click();
 await expect(page.getByLabel('Your approved public product',{exact:true}).locator('option[value^="member-older-"]')).toHaveCount(0);
});

test('ordinary team forms submit canonical availability while custom legacy text remains editable without reclassification',async({page})=>{
 const custom='Ask after our R&D milestone';await open(page,custom);
 await page.getByText('Edit team profile',{exact:true}).click();
 const edit=page.locator('.studio-overview-grid aside form');
 await expect(edit.getByLabel('Availability',{exact:true})).toHaveValue(custom);
 await expect(edit.getByText('Your current custom text is preserved. Choose a status to classify your availability.',{exact:true})).toBeVisible();
 await edit.getByRole('button',{name:'Save changes',exact:true}).click();
 expect((await page.evaluate(()=>(window as unknown as State).teamWrites)).at(-1)?.availability).toBe(custom);
 await page.getByText('Create a team',{exact:true}).click();
 const create=page.locator('.team-create form');
 await create.getByLabel('Team name',{exact:true}).fill('Ordinary form studio');
 await create.getByLabel('What does your team build?',{exact:true}).fill('Accessible customer software for local companies.');
 await create.getByLabel('Typical project budget range',{exact:true}).fill('5k–10k');
 await expect(create.getByLabel('Availability',{exact:true})).toHaveValue('AVAILABLE_NOW');
 await create.getByRole('button',{name:'Create team',exact:true}).click();
 await expect(page.getByRole('heading',{name:'Ordinary form studio',exact:true})).toBeVisible();
 expect((await page.evaluate(()=>(window as unknown as State).teamWrites)).at(-1)?.availability).toBe('AVAILABLE_NOW');
 await page.getByText('Edit team profile',{exact:true}).click();
 await edit.getByLabel('Availability',{exact:true}).selectOption('NOT_ACCEPTING');
 await edit.getByRole('button',{name:'Save changes',exact:true}).click();
 await expect(page.locator('.studio-overview-grid aside').getByText('Not accepting',{exact:true})).toBeVisible();
 expect((await page.evaluate(()=>(window as unknown as State).teamWrites)).at(-1)?.availability).toBe('NOT_ACCEPTING');
});
