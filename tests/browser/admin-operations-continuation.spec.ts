import {test,expect,type Page,type Route} from '@playwright/test';
import {build} from 'esbuild';

const names={disputes:'Delivery disputes',accounts:'Seller linked accounts',attention:'Orders requiring attention'} as const;
type Collection=keyof typeof names;
async function fixture(page:Page){
 const rows={
  disputes:Array.from({length:206},(_,n)=>({id:'dispute-'+n,title:n===205?'Oldest open dispute':'Open dispute '+n,engagementId:'engagement-'+n,status:'OPEN',reason:'An independent retained dispute requiring operator review.'})),
  accounts:Array.from({length:55},(_,n)=>({id:'account-'+n,builderUserId:'builder-'+n,accountId:'acc_fixture'+n})),
  attention:Array.from({length:55},(_,n)=>({id:'payment-'+n,status:'UNKNOWN',currency:'INR',amountMinor:100,attentionReason:'Receipt requires independent reconciliation.'}))
 };
 const calls:Record<Collection,number[]>={disputes:[],accounts:[],attention:[]},failures=new Set<string>();
 let denied=0,mutationStatus=0,hold=false,held:Route|undefined;
 const writes:Record<string,unknown>[]=[];
 const response=(key:Collection,number:number)=>({items:rows[key].slice(number*50,(number+1)*50),page:number,size:50,hasMore:(number+1)*50<rows[key].length,totalItems:rows[key].length,totalPages:Math.ceil(rows[key].length/50)});
 await page.route('**/api/v1/**',async route=>{
  if(route.request().method()==='POST'){writes.push(route.request().postDataJSON());return route.fulfill(mutationStatus?{status:mutationStatus,json:{error:{code:'OPERATION_UNAVAILABLE',message:'The decision was not saved. Please retry.'}}}:{json:{status:'RESOLVED'}})}
  const url=new URL(route.request().url()),key:Collection=url.pathname.endsWith('/disputes')?'disputes':url.pathname.endsWith('/accounts')?'accounts':'attention',number=Number(url.searchParams.get('page')||0);calls[key].push(number);
  if(number>0&&denied)return route.fulfill({status:denied,json:{error:{code:'FORBIDDEN',message:'Current administrator MFA is required.'}}});
  if(failures.delete(key+':'+number))return route.fulfill({status:503,json:{error:{code:'UNAVAILABLE',message:'Temporary '+key+' failure. Please retry.'}}});
  if(key==='disputes'&&number===1&&hold){held=route;return;}
  return route.fulfill({json:response(key,number)});
 });
 const built=await build({stdin:{contents:"import {createRoot} from 'react-dom/client';import DeliveryOperations from './app/components/delivery-operations';createRoot(document.getElementById('root')).render(<DeliveryOperations/>);",resolveDir:process.cwd(),loader:'tsx'},bundle:true,write:false,platform:'browser',format:'iife',jsx:'automatic',define:{'process.env.NODE_ENV':'"test"'}});
 await page.route('**/admin-operations-fixture',route=>route.fulfill({contentType:'text/html',body:'<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"><style>input,textarea,select{max-width:100%;box-sizing:border-box}form{margin:12px 0;padding:8px;border:1px solid #ccc}label{display:block}section{overflow-wrap:anywhere}</style></head><body><div id="root"></div></body></html>'}));
 await page.goto('/admin-operations-fixture');await page.addScriptTag({content:built.outputFiles[0].text});
 return {calls,failures,rows,writes,failMutation:(status:number)=>{mutationStatus=status;},deny:(status:number)=>{denied=status;},hold:()=>{hold=true;},hasHeld:()=>!!held,release:async()=>{const route=held;held=undefined;hold=false;if(route)await route.fulfill({json:response('disputes',1)})}};
}
const region=(page:Page,key:Collection)=>page.getByRole('region',{name:names[key],exact:true});
const open=async(page:Page)=>{await page.getByText('Delivery disputes and Razorpay operations',{exact:true}).click();await expect(region(page,'disputes').getByText('Open dispute 0',{exact:true})).toBeVisible()};

test('all 206 open disputes and independently bounded seller/payment pages remain reachable with keyboard retry and final focus',async({page})=>{
 const f=await fixture(page);await open(page);f.failures.add('disputes:1');
 const disputes=region(page,'disputes'),more=disputes.getByRole('button',{name:'Load more delivery disputes',exact:true});
 await more.focus();await page.keyboard.press('Enter');await expect(disputes.getByRole('alert')).toHaveText('Temporary disputes failure. Please retry.');await expect(more).toBeFocused();await expect(disputes.locator('form')).toHaveCount(50);
 for(let number=1;number<5;number++){await more.focus();await page.keyboard.press('Enter');await expect(disputes.locator('form')).toHaveCount(number===4?206:(number+1)*50)}
 await expect(disputes.getByText('Oldest open dispute',{exact:true})).toBeVisible();await expect(disputes.getByRole('status')).toBeFocused();await expect(more).toHaveCount(0);
 expect(f.calls.disputes).toEqual([0,1,1,2,3,4]);expect(f.calls.accounts).toEqual([0]);expect(f.calls.attention).toEqual([0]);
 await region(page,'accounts').getByRole('button',{name:'Load more seller accounts',exact:true}).click();await expect(region(page,'accounts').getByText('builder-54 · acc_fixture54',{exact:true})).toBeVisible();
 await region(page,'attention').getByRole('button',{name:'Load more payment attention',exact:true}).click();await expect(region(page,'attention').getByText('Attempt payment-54',{exact:true})).toBeVisible();
 await expect(region(page,'attention').locator('form')).toHaveCount(55);expect(f.calls.accounts).toEqual([0,1]);expect(f.calls.attention).toEqual([0,1]);
 await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(page.viewportSize()!.width+1);
});

test('first-page collection errors preserve the other collections and retry only the failed collection',async({page})=>{
 const f=await fixture(page);f.failures.add('accounts:0');await open(page);
 const accounts=region(page,'accounts');await expect(accounts.getByRole('alert')).toContainText('Temporary accounts failure. Please retry.');
 await expect(region(page,'attention').getByText('Attempt payment-0',{exact:true})).toBeVisible();await expect(region(page,'disputes').locator('form')).toHaveCount(50);
 await accounts.getByRole('button',{name:'Retry seller accounts',exact:true}).click();await expect(accounts.getByText('builder-0 · acc_fixture0',{exact:true})).toBeVisible();
 expect(f.calls.disputes).toEqual([0]);expect(f.calls.accounts).toEqual([0,0]);expect(f.calls.attention).toEqual([0]);
});

test('ordinary mutation failures retain typed decisions, unrelated fields and the current continuation cursor',async({page})=>{
 const f=await fixture(page);await open(page);const disputes=region(page,'disputes');
 await disputes.getByRole('button',{name:'Load more delivery disputes',exact:true}).click();await expect(disputes.locator('form')).toHaveCount(100);
 const form=disputes.locator('form').filter({has:page.getByText('Open dispute 99',{exact:true})});
 const reason='Retain this independently reviewed decision while the service recovers.';
 await form.getByLabel('Decision reason',{exact:true}).fill(reason);await form.getByLabel('Resolution',{exact:true}).selectOption('CANCEL');
 const order=region(page,'attention').getByLabel('Provider order ID',{exact:true}).first();await order.fill('order_retained123');
 for(const status of [400,409,503]){
  f.failMutation(status);await form.getByRole('button',{name:'Record dispute resolution',exact:true}).click();
  await expect(page.getByRole('alert')).toHaveText('The decision was not saved. Please retry.');
  await expect(form.getByLabel('Decision reason',{exact:true})).toHaveValue(reason);await expect(form.getByLabel('Resolution',{exact:true})).toHaveValue('CANCEL');await expect(order).toHaveValue('order_retained123');
  await expect(disputes.locator('form')).toHaveCount(100);await expect(form.getByRole('button',{name:'Record dispute resolution',exact:true})).toBeEnabled();
 }
 expect(f.writes).toEqual(Array.from({length:3},()=>({resolution:'CANCEL',reason})));
 await disputes.getByRole('button',{name:'Load more delivery disputes',exact:true}).click();await expect(disputes.locator('form')).toHaveCount(150);expect(f.calls.disputes).toEqual([0,1,2]);
});

for(const status of [401,403,404])test('current authority denial '+status+' clears every protected admin collection',async({page})=>{
 const f=await fixture(page);await open(page);await expect(region(page,'accounts').getByText('builder-0 · acc_fixture0',{exact:true})).toBeVisible();f.deny(status);
 await region(page,'disputes').getByRole('button',{name:'Load more delivery disputes',exact:true}).click();await expect(page.getByRole('alert')).toHaveText('Current administrator MFA is required.');
 await expect(page.getByRole('region')).toHaveCount(0);await expect(page.getByText('Open dispute 0',{exact:true})).toHaveCount(0);await expect(page.getByText('builder-0 · acc_fixture0',{exact:true})).toHaveCount(0);await expect(page.getByText('Attempt payment-0',{exact:true})).toHaveCount(0);
});

test('an older pending continuation cannot append after refresh or panel close',async({page})=>{
 const f=await fixture(page);await open(page);f.hold();await region(page,'disputes').getByRole('button',{name:'Load more delivery disputes',exact:true}).click();await expect.poll(f.hasHeld).toBe(true);
 await page.getByRole('button',{name:'Refresh payment operations',exact:true}).click();await expect(region(page,'disputes').locator('form')).toHaveCount(50);await f.release();await expect(region(page,'disputes').locator('form')).toHaveCount(50);
 f.hold();await region(page,'disputes').getByRole('button',{name:'Load more delivery disputes',exact:true}).click();await expect.poll(f.hasHeld).toBe(true);await page.getByText('Delivery disputes and Razorpay operations',{exact:true}).click();await f.release();await open(page);await expect(region(page,'disputes').locator('form')).toHaveCount(50);
 expect(f.calls.disputes).toEqual([0,1,0,1,0]);
});
