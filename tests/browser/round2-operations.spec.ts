import {test,expect,type Page} from '@playwright/test';
import {build} from 'esbuild';
import {readFileSync} from 'node:fs';

type OperationsState={writes:string[];hold:boolean;reject:boolean;release?:()=>void;settled:number};
type OperationsWindow=Window&{round2Operations:OperationsState};

async function render(page:Page){
 const result=await build({stdin:{contents:`
  import {useState} from 'react';import {createRoot} from 'react-dom/client';
  import TeamPublic from './app/components/team-public';import {createTeamDemo} from './lib/team-demo';
  const demo=createTeamDemo(),state=window.round2Operations={writes:[],hold:true,reject:false,settled:0};
  async function start(){
   const beta=await demo.request('/teams',{method:'POST',body:JSON.stringify({name:'Second studio',summary:'A separate team for accessible customer applications.',availability:'AVAILABLE_NOW',projectRange:'5k to 15k'})});
   await demo.request('/teams/'+beta.id+'/roles',{method:'POST',body:JSON.stringify({title:'Second team builder',description:'Build accessible customer applications with a separate team.',skills:'React',contractType:'PERMANENT',compensationBand:'5k'})});
   demo.setActor('applicant');
   const request=async(path,init)=>{
    const write=init?.method==='POST';
    if(write){state.writes.push(path);if(state.reject)throw new Error('This team request was rejected. Please review your details.');}
    const response=await demo.request(path,init);
    if(write&&state.hold)await new Promise(resolve=>state.release=resolve);
    if(write)state.settled++;return response;
   };
   function Harness(){const [id,setId]=useState('team-studio');return <><button className="button" onClick={()=>setId(beta.id)}>Select second team</button><TeamPublic embedded id={id} request={request}/></>}
   createRoot(document.getElementById('root')).render(<Harness/>);
  }void start();
 `,resolveDir:process.cwd(),loader:'tsx'},bundle:true,write:false,format:'iife',jsx:'automatic',loader:{'.css':'empty'},define:{'process.env.NODE_ENV':'"test"'},plugins:[{name:'round2-team-link',setup(plugin){
  plugin.onResolve({filter:/^next\/link$/},()=>({path:'link',namespace:'round2-link'}));
  plugin.onLoad({filter:/.*/,namespace:'round2-link'},()=>({contents:'export default function Link({children,...props}){return <a {...props}>{children}</a>}',loader:'jsx',resolveDir:process.cwd()}));
 }}]});
 await page.setContent('<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"></head><body><div id="root"></div></body></html>');
 // Use the component's production rules and shared controls at each real project viewport.
 for(const file of ['app/globals.css','app/components/teams.css','app/form-controls.css'])await page.addStyleTag({content:readFileSync(file,'utf8')});
 await page.addScriptTag({content:result.outputFiles[0].text});
 await expect(page.getByRole('heading',{name:'Northstar Studio',exact:true})).toBeVisible();
}
async function submit(page:Page,kind:'interest'|'application'){
 if(kind==='interest'){
  await page.getByLabel('Project title',{exact:true}).fill('Accessible client portal');
  await page.getByLabel('What would you like to build?',{exact:true}).fill('A shared place to review and approve client projects.');
  await page.getByLabel('Budget range',{exact:true}).fill('5k to 10k');
  await page.getByLabel('Expected timeline',{exact:true}).fill('Within two months');
  await page.getByRole('button',{name:'Send project interest',exact:true}).click();
 }else{
  await page.getByText('Apply for this role',{exact:true}).click();
  await page.getByLabel('Introduce yourself and your relevant work',{exact:true}).fill('I build accessible React applications and client portals.');
  await page.getByRole('button',{name:'Send application',exact:true}).click();
 }
 await expect.poll(()=>page.evaluate(()=>(window as unknown as OperationsWindow).round2Operations.writes.length)).toBe(1);
}
async function release(page:Page){
 await expect.poll(()=>page.evaluate(()=>typeof (window as unknown as OperationsWindow).round2Operations.release)).toBe('function');
 await page.evaluate(()=>(window as unknown as OperationsWindow).round2Operations.release!());
 await expect.poll(()=>page.evaluate(()=>(window as unknown as OperationsWindow).round2Operations.settled)).toBe(1);
}

for(const kind of ['interest','application'] as const){
 test('accepted '+kind+' acknowledgement cannot announce success for a newly selected team',async({page})=>{
  const errors:string[]=[];page.on('pageerror',error=>errors.push(error.message));await render(page);await submit(page,kind);
  await page.getByRole('button',{name:'Select second team',exact:true}).click();
  await expect(page.getByRole('heading',{name:'Second studio',exact:true})).toBeVisible();await release(page);
  await expect(page.getByText(/^(Project interest sent\.|Application sent\.)/)).toHaveCount(0);
  await expect(page.getByText('Application submitted. Check your workspace for updates.',{exact:true})).toHaveCount(0);
  await expect(page.getByText('Apply for this role',{exact:true})).toBeVisible();expect(errors).toEqual([]);
 });
 test('accepted '+kind+' acknowledgement still updates the unchanged team',async({page})=>{
  await render(page);await submit(page,kind);await release(page);
  await expect(page.getByText(kind==='interest'?'Project interest sent. Follow its progress in your team workspace.':'Application sent. Membership requires your acceptance of a separate invitation.',{exact:true})).toBeVisible();
  if(kind==='application')await expect(page.getByText('Application submitted. Check your workspace for updates.',{exact:true})).toBeVisible();
 });
 test('rejected '+kind+' preserves the entered form and never displays an accepted outcome',async({page})=>{
  await render(page);await page.evaluate(()=>(window as unknown as OperationsWindow).round2Operations.reject=true);await submit(page,kind);
  await expect(page.getByRole('alert')).toContainText('This team request was rejected.');
  await expect(page.getByText(/^(Project interest sent\.|Application sent\.)/)).toHaveCount(0);
  await expect(page.getByLabel(kind==='interest'?'Project title':'Introduce yourself and your relevant work',{exact:true})).toHaveValue(kind==='interest'?'Accessible client portal':'I build accessible React applications and client portals.');
 });
}
