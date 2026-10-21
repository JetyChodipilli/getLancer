import {test,expect} from '@playwright/test';

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
