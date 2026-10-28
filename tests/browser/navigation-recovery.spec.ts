import {test,expect} from '@playwright/test';

for(const kind of ['PASSWORD_RESET','REVIEW'])test(kind+' confirmation locks submitted values until a failed request can be retried',async({page})=>{
 let finish!:()=>void;
 const waiting=new Promise<void>(resolve=>{finish=resolve;});
 const bodies:Record<string,string>[]=[];
 await page.route('**/api/v1/auth/confirmation',route=>route.fulfill({json:{kind,used:false}}));
 await page.route('**/api/v1/auth/confirm',async route=>{
  bodies.push(route.request().postDataJSON());
  if(bodies.length===1){await waiting;return route.fulfill({status:503,json:{error:{code:'SERVICE_UNAVAILABLE',message:'Please retry these details.'}}});}
  return route.fulfill({json:{}});
 });
 await page.goto('/confirm#token=confirmation-fixture');
 const field=kind==='PASSWORD_RESET'?page.getByLabel('New password',{exact:true}):page.getByLabel('Your experience',{exact:true});
 const value=kind==='PASSWORD_RESET'?'Original-password-123':'The project was completed with clear communication.';
 await field.fill(value);
 if(kind==='REVIEW')await page.getByLabel('Rating (1–5)',{exact:true}).fill('5');
 await page.getByRole('button',{name:kind==='REVIEW'?'Submit review':'Confirm',exact:true}).click();
 await expect(field).toBeDisabled();
 await expect(page.locator('form[aria-busy=true]')).toBeVisible();
 if(kind==='REVIEW')await expect(page.getByLabel('Name shown with your review',{exact:true})).toBeDisabled();
 finish();
 await expect(page.getByRole('alert')).toHaveText('Please retry these details.');
 await expect(field).toBeEnabled();
 await expect(field).toHaveValue(value);
 await page.getByRole('button',{name:kind==='REVIEW'?'Submit review':'Confirm',exact:true}).click();
 await expect(page.getByRole('status')).toContainText(kind==='REVIEW'?'Your review has been saved':'Your password has been changed');
 expect(bodies).toHaveLength(2);
 expect(bodies[1]).toEqual(bodies[0]);
});

test('a second email confirmation replaces the completed first response in the same tab',async({page})=>{
 const tokens:string[]=[];
 await page.route('**/api/v1/auth/confirmation',async route=>{
  const token=route.request().postDataJSON().token;tokens.push(token);
  await route.fulfill({json:{kind:token==='first'?'HIRE_CONFIRMATION':'COMPLETION_CONFIRMATION',used:false}});
 });
 await page.route('**/api/v1/auth/confirm',route=>route.fulfill({json:{}}));
 await page.goto('/confirm#token=first');
 await expect(page.getByRole('heading',{name:'Confirm your hire',exact:true})).toBeVisible();
 await page.getByRole('button',{name:'Confirm',exact:true}).click();
 await expect(page.getByRole('status')).toHaveText('Your response has been recorded.');
 await page.goto('/confirm#token=second');
 await expect(page.getByRole('heading',{name:'Confirm project completion',exact:true})).toBeVisible();
 await expect(page.getByRole('button',{name:'Confirm',exact:true})).toBeEnabled();
 await expect(page).toHaveURL(/\/confirm$/);
 expect(tokens).toEqual(['first','second']);
});

test('report target prefills after hydration and remains editable',async({page})=>{
 await page.goto('/report?product=8c56a0d2-cf61-4bf1-8f0e-f1abfbb733ef');
 await expect(page.getByLabel('Record ID',{exact:true})).toHaveValue('8c56a0d2-cf61-4bf1-8f0e-f1abfbb733ef');
 await page.getByLabel('Record ID',{exact:true}).fill('corrected-id');
 await expect(page.getByLabel('Record ID',{exact:true})).toHaveValue('corrected-id');
});
