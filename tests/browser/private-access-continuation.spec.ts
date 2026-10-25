import {test,expect,type Page} from '@playwright/test';
import {buildSync} from 'esbuild';

type Grant={client_email:string;expires_at:string};
type Pending={path:string;method:string;body:unknown;resolve(body:unknown,status?:number):void};
type HarnessWindow=Window&{calls:Omit<Pending,'resolve'>[];pending:Pending[]};
const base='/api/v1/developer/products/first/access';
const grants=(start:number,count:number):Grant[]=>Array.from({length:count},(_,index)=>({client_email:'client'+String(start+index).padStart(3,'0')+'@example.test',expires_at:'2030-01-07T12:00:00Z'}));
const first=()=>({items:grants(0,50),page:0,size:50,hasMore:true});

function fixture(){return buildSync({stdin:{contents:`
 import {useState} from 'react';import {createRoot} from 'react-dom/client';import PrivateAccess from './app/components/private-access';
 window.calls=[];window.pending=[];
 window.fetch=(path,init={})=>new Promise(resolve=>{const call={path:String(path),method:init.method||'GET',body:init.body?JSON.parse(init.body):null};window.calls.push(call);window.pending.push({...call,resolve:(body,status=200)=>resolve(new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json'}}))});});
 function Harness(){const [id,setId]=useState('first');return <><button onClick={()=>setId('second')}>Change private project</button><PrivateAccess product={{id}}/></>;}
 createRoot(document.getElementById('root')).render(<Harness/>);
 `,loader:'tsx',resolveDir:process.cwd()},bundle:true,write:false,platform:'browser',format:'iife',jsx:'automatic',define:{'process.env.NODE_ENV':'"test"'}}).outputFiles[0].text;}
async function mount(page:Page){await page.setContent('<div id="root"></div>');await page.addScriptTag({content:fixture()});await expect.poll(()=>page.evaluate(()=>(window as unknown as HarnessWindow).pending.length)).toBe(1)}
async function respond(page:Page,path:string,body:unknown,status=200,method='GET'){
 await expect.poll(()=>page.evaluate(({path,method})=>(window as unknown as HarnessWindow).pending.some(item=>item.path===path&&item.method===method),{path,method})).toBe(true);
 await page.evaluate(({path,body,status,method})=>{const pending=(window as unknown as HarnessWindow).pending;pending.splice(pending.findIndex(item=>item.path===path&&item.method===method),1)[0].resolve(body,status)},{path,body,status,method});
}

test('owner retries page two, reaches an active grant beyond fifty and revokes that email',async({page})=>{
 await mount(page);await expect(page.getByRole('status')).toHaveText('Loading private client access…');await respond(page,base,first());
 await expect(page.getByRole('button',{name:/^Revoke access for /})).toHaveCount(50);
 const more=page.getByRole('button',{name:'Load more client access',exact:true}),next=base+'?page=1&size=50';
 await more.click();await expect(page.getByRole('button',{name:'Loading…',exact:true})).toBeDisabled();await respond(page,next,{error:{message:'Temporary access outage',code:'UNAVAILABLE'}},503);
 await expect(page.getByRole('alert')).toHaveText('Temporary access outage');await expect(page.getByRole('button',{name:/^Revoke access for /})).toHaveCount(50);
 await more.focus();await more.click();await respond(page,next,{items:[{...grants(0,1)[0],client_email:'CLIENT000@example.test'},...grants(50,5)],page:1,size:50,hasMore:false});
 await expect(page.getByRole('button',{name:/^Revoke access for /})).toHaveCount(55);await expect(page.getByRole('status')).toHaveText('6 more records loaded.');await expect(page.getByRole('status')).toBeFocused();
 expect(await page.evaluate(()=>(window as unknown as HarnessWindow).calls.filter(call=>call.path.includes('?')).map(call=>call.path))).toEqual([next,next]);
 const revoke=page.getByRole('button',{name:'Revoke access for client054@example.test',exact:true});await revoke.focus();await revoke.click();
 await expect(page.getByText('Updating private client access…',{exact:true})).toBeVisible();
 await expect.poll(()=>page.evaluate(()=>(window as unknown as HarnessWindow).calls.find(call=>call.method==='DELETE')?.body)).toEqual({email:'client054@example.test'});
 await respond(page,base,{ok:true},200,'DELETE');await respond(page,base,first());await expect(revoke).toHaveCount(0);await expect(page.getByRole('status')).toHaveText('Client access revoked.');await expect(page.getByRole('status')).toBeFocused();
 await more.click();await respond(page,next,{items:grants(50,4),page:1,size:50,hasMore:false});await expect(page.getByRole('button',{name:/^Revoke access for /})).toHaveCount(54);await expect(revoke).toHaveCount(0);
});

test('project changes clear private grants and discard pending continuation responses',async({page})=>{
 await mount(page);await respond(page,base,first());await page.getByRole('button',{name:'Load more client access',exact:true}).click();
 await page.getByRole('button',{name:'Change private project',exact:true}).click();await expect(page.getByRole('button',{name:/^Revoke access for /})).toHaveCount(0);
 const second='/api/v1/developer/products/second/access';await respond(page,second,{items:grants(100,1),page:0,size:50,hasMore:true});
 await respond(page,base+'?page=1&size=50',{items:grants(50,5),page:1,size:50,hasMore:false});await expect(page.getByRole('button',{name:/^Revoke access for /})).toHaveCount(1);
 await page.getByRole('button',{name:'Load more client access',exact:true}).click();await respond(page,second+'?page=1&size=50',{items:grants(101,1),page:1,size:50,hasMore:false});
 await expect(page.getByRole('button',{name:'Revoke access for client101@example.test',exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'Revoke access for client050@example.test',exact:true})).toHaveCount(0);
});

test('lost authority on a later page clears grants and the share link',async({page})=>{
 await mount(page);await respond(page,base,first());await page.getByLabel('Client email',{exact:true}).fill('new-client@example.test');await page.getByRole('button',{name:'Grant access for seven days',exact:true}).click();
 await respond(page,base,{path:'/private/projects/first',expiresInDays:7},200,'POST');await respond(page,base,first());await expect(page.getByRole('link',{name:/\/private\/projects\/first/})).toBeVisible();
 await page.getByRole('button',{name:'Load more client access',exact:true}).click();await respond(page,base+'?page=1&size=50',{error:{message:'Private project not found.',code:'NOT_FOUND'}},404);
 await expect(page.getByRole('alert')).toHaveText('Private project not found.');await expect(page.getByRole('button',{name:/^Revoke access for /})).toHaveCount(0);await expect(page.getByRole('link')).toHaveCount(0);await expect(page.getByRole('button',{name:'Retry loading access',exact:true})).toBeVisible();
});

test('initial grant form survives an outstanding list request and ignores that stale result',async({page})=>{
 await mount(page);await page.getByLabel('Client email',{exact:true}).fill('new-client@example.test');await page.getByRole('button',{name:'Grant access for seven days',exact:true}).click();
 await respond(page,base,first());await expect(page.getByRole('button',{name:/^Revoke access for /})).toHaveCount(0);
 await respond(page,base,{path:'/private/projects/first',expiresInDays:7},200,'POST');await respond(page,base,{items:[{client_email:'new-client@example.test',expires_at:'2030-01-07T12:00:00Z'}],page:0,size:50,hasMore:false});
 await expect(page.getByRole('button',{name:'Revoke access for new-client@example.test',exact:true})).toBeVisible();await expect(page.getByRole('link',{name:/\/private\/projects\/first/})).toBeVisible();
 expect(await page.evaluate(()=>(window as unknown as HarnessWindow).calls.find(call=>call.method==='POST')?.body)).toEqual({email:'new-client@example.test'});
});

test('a successful mutation resets the cursor and ignores a pending old page',async({page})=>{
 await mount(page);await respond(page,base,first());await page.getByRole('button',{name:'Load more client access',exact:true}).click();
 await page.getByLabel('Client email',{exact:true}).fill('new-client@example.test');await page.getByRole('button',{name:'Grant access for seven days',exact:true}).click();
 await respond(page,base,{path:'/private/projects/first',expiresInDays:7},200,'POST');await respond(page,base,{items:grants(0,50),page:0,size:50,hasMore:true});
 await respond(page,base+'?page=1&size=50',{items:grants(80,1),page:1,size:50,hasMore:false});await expect(page.getByRole('button',{name:'Revoke access for client080@example.test',exact:true})).toHaveCount(0);
 await page.getByRole('button',{name:'Load more client access',exact:true}).click();await respond(page,base+'?page=1&size=50',{items:grants(50,1),page:1,size:50,hasMore:false});await expect(page.getByRole('button',{name:'Revoke access for client050@example.test',exact:true})).toBeVisible();
});

test('malformed initial metadata offers a real reload instead of an unverifiable continuation',async({page})=>{
 await mount(page);await respond(page,base,{items:grants(0,1),hasMore:true});await expect(page.getByRole('alert')).toContainText('could not be verified');await expect(page.getByRole('button',{name:'Load more client access',exact:true})).toHaveCount(0);
 await page.getByRole('button',{name:'Retry loading access',exact:true}).click();await respond(page,base,{items:[],page:0,size:50,hasMore:false});await expect(page.getByText('No client access grants yet.',{exact:true})).toBeVisible();await expect(page.getByRole('alert')).toHaveCount(0);
});
