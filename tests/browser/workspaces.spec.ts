import {test,expect,type Page} from '@playwright/test';

async function noOverflow(page:Page){const width=page.viewportSize()!.width;await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(width+1);}
test('all workspace previews reflow and retain their core controls',async({page},info)=>{
 for(const [path,role,ready]of [['workspace','tab','Showcases'],['trust','button','Confirm availability'],['teams','heading','Northstar Studio'],['business','heading','Customer approval portal']] as const){
  await page.goto('/preview/'+path);await expect(page.getByRole(role,{name:ready,exact:true})).toBeVisible();await noOverflow(page);
  await page.screenshot({path:info.outputPath(path+'.png'),fullPage:true});
 }
});
test('private brief form can create, open and close a request',async({page})=>{
 await page.goto('/preview/business');await page.getByRole('button',{name:'New project request',exact:true}).click();const dialog=page.getByRole('dialog');
 await dialog.getByLabel('Project title',{exact:true}).fill('Warehouse approvals');
 await dialog.getByLabel('Problem, users and essential requirements',{exact:true}).fill('Build an accessible inventory approval workspace for our warehouse operations team.');
 await dialog.getByLabel('Business category (optional if technology provided)',{exact:true}).fill('Inventory');
 await dialog.getByLabel('Technology (one, optional if category provided)',{exact:true}).fill('React');
 await dialog.getByLabel('Budget range',{exact:true}).fill('$5k–$10k');await dialog.getByLabel('Timeline',{exact:true}).fill('2 months');
 await noOverflow(page);await dialog.getByRole('button',{name:'Create project request',exact:true}).click();await expect(dialog).not.toBeVisible();
 await expect(page.getByRole('heading',{name:'Warehouse approvals',exact:true})).toBeVisible();await page.getByRole('button',{name:'Open for matching',exact:true}).click();await expect(page.getByRole('heading',{name:'Evidence that fits your brief'})).toBeVisible();
 await page.getByRole('button',{name:'Close request',exact:true}).click();await page.getByRole('dialog').getByRole('button',{name:'Close request',exact:true}).click();await expect(page.getByRole('button',{name:'Edit brief',exact:true})).toHaveCount(0);
});
test('hiring manager consent and owner-only settings are reflected in the interface',async({page})=>{
 await page.goto('/preview/business');await expect(page.getByRole('heading',{name:'Customer approval portal',exact:true})).toBeVisible();await page.getByText('Interactive business preview',{exact:true}).click();await page.getByLabel('Preview actor',{exact:true}).selectOption('outsider');
 await expect(page.getByText('Meridian Labs invited you to join.',{exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'New project request',exact:true})).toHaveCount(0);
 await page.getByRole('button',{name:'Accept invitation',exact:true}).click();await expect(page.getByRole('heading',{name:'Customer approval portal',exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'Business settings',exact:true})).toHaveCount(0);await noOverflow(page);
});
test('concierge opt-in completes through the isolated administrator preview',async({page})=>{
 await page.goto('/preview/business');await page.getByRole('button',{name:'Request concierge sourcing',exact:true}).click();await page.getByRole('button',{name:'Share brief for sourcing',exact:true}).click();await expect(page.getByRole('dialog')).not.toBeVisible();
 await page.getByText('Interactive business preview',{exact:true}).click();await page.getByLabel('Preview actor',{exact:true}).selectOption('admin');await expect(page.getByRole('heading',{name:'Concierge requests',exact:true})).toBeVisible();
 await page.getByRole('button',{name:'Start sourcing',exact:true}).click();await page.getByRole('button',{name:'Review matching evidence',exact:true}).click();
 const candidate=page.locator('.business-candidate').filter({has:page.getByRole('heading',{name:'Northstar Studio',exact:true})});await candidate.getByLabel('Client-visible recommendation reason',{exact:true}).fill('Consented CRM proof and active approved builders fit this brief.');await candidate.getByRole('button',{name:'Recommend candidate',exact:true}).click();await page.getByRole('button',{name:'Mark fulfilled',exact:true}).click();
 await expect(page.getByText('Fulfilled',{exact:true})).toBeVisible();await noOverflow(page);
});
test('keyboard dialog dismissal restores the primary action',async({page})=>{
 await page.goto('/preview/business');await page.getByRole('button',{name:'New project request',exact:true}).click();await expect(page.getByLabel('Project title',{exact:true})).toBeFocused();await page.keyboard.press('Escape');await expect(page.getByRole('dialog')).not.toBeVisible();await expect(page.getByRole('button',{name:'New project request',exact:true})).toBeFocused();
});

test('rejected brief submission focuses the committed error summary and preserves entered values',async({page})=>{
 await page.goto('/preview/business');await page.getByRole('button',{name:'New project request',exact:true}).click();const dialog=page.getByRole('dialog');
 await dialog.getByLabel('Project title',{exact:true}).fill('Inventory approvals');
 await dialog.getByLabel('Problem, users and essential requirements',{exact:true}).fill('Build an accessible inventory approval workspace for our warehouse team.');
 await dialog.getByLabel('Budget range',{exact:true}).fill('10k');await dialog.getByLabel('Timeline',{exact:true}).fill('2 months');
 await dialog.getByRole('button',{name:'Create project request',exact:true}).click();
 await expect(dialog.getByRole('alert')).toContainText('Choose a category or technology');await expect(dialog.getByRole('alert')).toBeFocused();
 await expect(dialog.getByLabel('Project title',{exact:true})).toHaveValue('Inventory approvals');await expect(dialog.getByRole('button',{name:'Create project request',exact:true})).toBeEnabled();
});
