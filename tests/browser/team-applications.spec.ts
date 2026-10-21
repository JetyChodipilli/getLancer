import {test,expect} from '@playwright/test';
import {buildSync} from 'esbuild';

type HarnessWindow=Window&{requests:string[];failNextLookup:boolean};
function fixture(){return buildSync({stdin:{contents:`
 import {useState} from 'react';import {createRoot} from 'react-dom/client';
 import TeamPublic from './app/components/team-public';import {createTeamDemo} from './lib/team-demo';
 window.requests=[];window.failNextLookup=false;
 const demo=createTeamDemo(),base='/teams/team-studio';
 const send=(path,body)=>demo.request(path,{method:'POST',body:JSON.stringify(body)});
 const request=async(path,init)=>{window.requests.push(path);if(path.includes('roleIds=')&&window.failNextLookup){window.failNextLookup=false;throw new Error('Application lookup unavailable. Retry.')}return demo.request(path,init)};
 function Harness(){const [version,setVersion]=useState(0);return <><button onClick={()=>{demo.setActor('member');setVersion(n=>n+1)}}>Use member account</button><TeamPublic key={version} embedded id="team-studio" request={request}/></>}
 (async()=>{const roles=[];for(let n=0;n<53;n++)roles.push(await send(base+'/roles',{title:'Application role '+n,description:'Build accessible software with our team.',skills:'Java',contractType:'PERMANENT',compensationBand:'5k'}));demo.setActor('applicant');for(const role of roles.reverse())await send(base+'/roles/'+role.id+'/applications',{message:'I build accessible products.'});createRoot(document.getElementById('root')).render(<Harness/>);})();
 `,loader:'tsx',resolveDir:process.cwd()},bundle:true,write:false,platform:'browser',format:'iife',jsx:'automatic',loader:{'.css':'empty'},define:{'process.env.NODE_ENV':'"test"'}}).outputFiles[0].text;}

test('public profiles preserve old application status across role pages and retry failed lookups',async({page})=>{
 await page.setContent('<div id="root"></div>');await page.addScriptTag({content:fixture()});
 const role=(name:string)=>page.locator('.team-role-card').filter({has:page.getByRole('heading',{name,exact:true})});
 const oldest=role('Application role 52');
 await expect(oldest.getByRole('status')).toHaveText('Application submitted. Check your workspace for updates.');
 await expect(oldest.getByText('Apply for this role',{exact:true})).toHaveCount(0);
 await expect(page.locator('.team-role-card')).toHaveCount(50);
 await page.evaluate(()=>(window as unknown as HarnessWindow).failNextLookup=true);
 await page.getByRole('button',{name:'Load more roles',exact:true}).click();
 await expect(page.getByRole('alert')).toHaveText('Application lookup unavailable. Retry.');
 await expect(page.locator('.team-role-card')).toHaveCount(50);
 await expect(oldest.getByRole('status')).toHaveText('Application submitted. Check your workspace for updates.');
 await page.getByRole('button',{name:'Load more roles',exact:true}).click();
 await expect(role('Application role 0').getByRole('status')).toHaveText('Application submitted. Check your workspace for updates.');
 await expect(page.locator('.team-role-card')).toHaveCount(54);
 await expect(role('Frontend builder').getByText('Apply for this role',{exact:true})).toBeVisible();
 await expect(page.getByRole('button',{name:'Load more roles',exact:true})).toHaveCount(0);
 const requests=await page.evaluate(()=>(window as unknown as HarnessWindow).requests);
 const lookups=requests.filter(path=>path.includes('roleIds='));
 expect(lookups.map(path=>new URL(path,'https://demo.invalid').searchParams.get('roleIds')!.split(',').length)).toEqual([50,4,4]);
 expect(requests).not.toContain('/me/teams');
 await page.getByRole('button',{name:'Use member account',exact:true}).click();
 await expect(role('Application role 52').getByText('You are already a team member.',{exact:true})).toBeVisible();
 await expect(page.getByText('Apply for this role',{exact:true})).toHaveCount(0);
});
