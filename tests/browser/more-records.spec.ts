import {test,expect} from '@playwright/test';
import {buildSync} from 'esbuild';
type HarnessWindow=Window&{requests:string[];pending:{resolve(value:object):void;reject(error:Error):void}[]};

function fixture(){return buildSync({stdin:{contents:`
 import {useState} from 'react';import {createRoot} from 'react-dom/client';import MoreRecords from './app/components/more-records';
 window.requests=[];window.pending=[];
 const request=path=>{window.requests.push(path);return new Promise((resolve,reject)=>window.pending.push({resolve,reject}));};
 function Harness(){const [path,setPath]=useState('/records?kind=TEAM&page=0'),[items,setItems]=useState(['Initial']);return <><button onClick={()=>{setPath('/records?kind=BUSINESS');setItems(['Business']);}}>Change collection</button><ul>{items.map(i=><li key={i}>{i}</li>)}</ul><MoreRecords path={path} request={request} firstPage={{page:0,size:2,hasMore:true}} label="Load more records" onItems={rows=>setItems(old=>[...old,...rows])}/></>;}
 createRoot(document.getElementById('root')).render(<Harness/>);
 `,loader:'tsx',resolveDir:process.cwd()},bundle:true,write:false,platform:'browser',format:'iife',jsx:'automatic',define:{'process.env.NODE_ENV':'"test"'}}).outputFiles[0].text;}

test('continuation preserves filters, retries the same failed page and announces appended results',async({page})=>{
 await page.setContent('<div id="root"></div>');await page.addScriptTag({content:fixture()});
 await page.getByRole('button',{name:'Load more records',exact:true}).click();await expect(page.getByRole('button',{name:'Loading…',exact:true})).toBeDisabled();
 expect(await page.evaluate(()=>(window as unknown as HarnessWindow).requests)).toEqual(['/records?kind=TEAM&page=1&size=2']);
 await page.evaluate(()=>(window as unknown as HarnessWindow).pending.shift()!.reject(new Error('Temporary outage')));await expect(page.getByRole('alert')).toHaveText('Temporary outage');await expect(page.getByText('Initial',{exact:true})).toBeVisible();
 await page.getByRole('button',{name:'Load more records',exact:true}).click();
 expect(await page.evaluate(()=>(window as unknown as HarnessWindow).requests)).toEqual(['/records?kind=TEAM&page=1&size=2','/records?kind=TEAM&page=1&size=2']);
 await page.evaluate(()=>(window as unknown as HarnessWindow).pending.shift()!.resolve({page:1,size:2,hasMore:false,items:['Next']}));await expect(page.getByRole('listitem')).toHaveText(['Initial','Next']);await expect(page.getByRole('status')).toHaveText('1 more records loaded.');await expect(page.getByRole('status')).toBeFocused();await expect(page.getByRole('button',{name:'Load more records',exact:true})).toHaveCount(0);
});

test('a changed collection ignores the previous request and resets continuation identity',async({page})=>{
 await page.setContent('<div id="root"></div>');await page.addScriptTag({content:fixture()});await page.getByRole('button',{name:'Load more records',exact:true}).click();
 await page.getByRole('button',{name:'Change collection',exact:true}).click();await expect(page.getByRole('button',{name:'Load more records',exact:true})).toBeEnabled();
 await page.evaluate(()=>(window as unknown as HarnessWindow).pending.shift()!.resolve({page:1,hasMore:false,items:['Stale private record']}));await expect(page.getByText('Stale private record',{exact:true})).toHaveCount(0);
 await page.getByRole('button',{name:'Load more records',exact:true}).click();expect(await page.evaluate(()=>(window as unknown as HarnessWindow).requests.at(-1))).toBe('/records?kind=BUSINESS&page=1&size=2');
 await page.evaluate(()=>(window as unknown as HarnessWindow).pending.shift()!.resolve({page:1,hasMore:false,items:['Current record']}));await expect(page.getByRole('listitem')).toHaveText(['Business','Current record']);
});
