import {test,expect} from '@playwright/test';
import {buildSync} from 'esbuild';

function fixture(fieldError:boolean){
 return buildSync({stdin:{contents:`
  import {useState} from 'react';
  import {createRoot} from 'react-dom/client';
  import {Field,TeamForm} from './app/components/workspace-form';
  function Harness(){
   const [busy,setBusy]=useState(false),[saved,setSaved]=useState(false);
   function save(first){setBusy(true);return new Promise((resolve,reject)=>{
    window.finishSave=()=>{setBusy(false);if(first)reject(Object.assign(new Error('First failure'),{fieldErrors:${fieldError?"{first:'Choose another name.'}":'{}'}}));else{setSaved(true);resolve();}};
   });}
   return <><TeamForm busy={busy} label="Reject first" onSave={()=>save(true)}><Field name="first" label="First name" value="First value"/></TeamForm><TeamForm busy={busy} label="Save second" onSave={()=>save(false)}><Field name="second" label="Second name" value="Second value"/></TeamForm><p role="status">{saved?'Second saved':''}</p></>;
  }
  createRoot(document.getElementById('root')).render(<Harness/>);
 `,loader:'tsx',resolveDir:process.cwd()},bundle:true,write:false,platform:'browser',format:'iife',jsx:'automatic',define:{'process.env.NODE_ENV':'"test"'}}).outputFiles[0].text;
}
for(const fieldError of [false,true])test(`a ${fieldError?'field':'summary'} error requests focus once across shared busy changes`,async({page})=>{
 await page.setContent('<div id="root"></div>');await page.addScriptTag({content:fixture(fieldError)});
 const first=page.getByLabel('First name',{exact:true}),summary=page.getByRole('alert'),target=fieldError?first:summary;
 await page.getByRole('button',{name:'Reject first',exact:true}).click();await expect(first).toBeDisabled();
 await page.evaluate(()=>{(window as any).finishSave();});
 await expect(target).toBeFocused();await expect(first).toBeEnabled();await expect(first).toHaveValue('First value');
 if(fieldError){await expect(first).toHaveAttribute('aria-invalid','true');await expect(first).toHaveAttribute('aria-describedby',/.+-error/);}
 await page.getByRole('button',{name:'Save second',exact:true}).click();await expect(first).toBeDisabled();
 await page.evaluate(()=>{(window as any).finishSave();});await expect(page.getByText('Second saved',{exact:true})).toBeVisible();
 await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
 await expect(target).not.toBeFocused();await expect(first).toHaveValue('First value');
});
