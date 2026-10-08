import {test,expect} from '@playwright/test';

async function reflow(page: import('@playwright/test').Page) {
  expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(page.viewportSize()!.width+1);
}
test('demo role cards are visible and switch to matching workspaces',async({page})=>{
  await page.goto('/preview/workspace');
  const roles=page.getByRole('group',{name:'Demo roles',exact:true});
  await expect(roles).toBeVisible();await expect(roles.getByRole('button')).toHaveCount(4);
  await roles.getByRole('button',{name:/Builder Leah/}).click();
  await expect(page.getByRole('tab',{name:'Showcases',exact:true})).toHaveAttribute('aria-selected','true');
  await roles.getByRole('button',{name:/Administrator Sam/}).click();
  await expect(page.getByRole('tab',{name:/^Moderation \(/})).toHaveAttribute('aria-selected','true');
  await reflow(page);
});
test('sample contribution uses staged fields, preserves back navigation and can be moderated',async({page})=>{
  await page.goto('/preview/components');
  await page.getByRole('button',{name:'Archive component',exact:true}).first().click();
  await page.getByRole('button',{name:'New component',exact:true}).click();
  const dialog=page.getByRole('dialog');
  await dialog.getByLabel('Component title',{exact:true}).fill('A sample release');
  await dialog.getByLabel('Summary',{exact:true}).fill('A meaningful synthetic component contribution.');
  await dialog.getByRole('button',{name:'Continue to contribution',exact:true}).click();
  await expect(dialog.getByLabel('Your contribution & intended use',{exact:true})).toBeFocused();
  await dialog.getByLabel('Your contribution & intended use',{exact:true}).fill('I selected this reviewed recipe to explain a sample project navigation use case.');
  await dialog.getByRole('button',{name:'Back',exact:true}).click();
  await expect(dialog.getByLabel('Component title',{exact:true})).toHaveValue('A sample release');
  await dialog.getByRole('button',{name:'Continue to contribution',exact:true}).click();
  await dialog.getByRole('checkbox',{name:/I preserve/}).check();
  await dialog.getByRole('button',{name:'Save component draft',exact:true}).click();await expect(dialog).not.toBeVisible();
  const record=page.locator('article').filter({has:page.getByRole('heading',{name:'A sample release',exact:true})});
  await record.getByRole('button',{name:'Submit for review',exact:true}).click();
  await page.getByRole('group',{name:'Demo roles'}).getByRole('button',{name:/Administrator/}).click();
  const review=page.locator('.kit-admin-list article').filter({has:page.getByRole('heading',{name:'A sample release · pending',exact:true})});
  await review.getByLabel('Review reason',{exact:true}).fill('Reviewed the synthetic contribution, original source and MIT attribution.');
  await review.getByRole('button',{name:'Record component review',exact:true}).click();
  await expect(page.getByText('Review decision recorded.',{exact:true})).toBeVisible();await reflow(page);
});
test('sample saves survive navigation and remove idempotently',async({page})=>{
  await page.goto('/components/feedback-form');await page.getByRole('button',{name:'Save component',exact:true}).click();
  await page.getByRole('link',{name:'View saved components',exact:true}).click();
  await expect(page.getByRole('heading',{name:'Feedback form',exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Remove',exact:true}).click();await expect(page.getByRole('heading',{name:'Your next building block is here.',exact:true})).toBeVisible();await reflow(page);
});
test('unfinished contribution and upload fields do not block returning to their step',async({page})=>{
  await page.goto('/preview/components');
  await page.getByRole('button',{name:'New component',exact:true}).click();
  const dialog=page.getByRole('dialog');
  await dialog.getByLabel('Component title',{exact:true}).fill('An unfinished contribution');
  await dialog.getByLabel('Summary',{exact:true}).fill('Preserve partially completed contribution fields across steps.');
  await dialog.getByRole('button',{name:'Continue to contribution',exact:true}).click();
  const contribution=dialog.getByLabel('Your contribution & intended use',{exact:true});
  await contribution.pressSequentially('Work in progress');
  await dialog.getByRole('button',{name:'Back',exact:true}).click();
  await dialog.getByRole('button',{name:'Continue to contribution',exact:true}).click();
  await expect(contribution).toBeVisible();
  await expect(contribution).toHaveValue('Work in progress');
  expect(await contribution.evaluate((field:HTMLTextAreaElement)=>field.validity.tooShort)).toBe(true);
  await contribution.fill('I created this original source with accurate MIT attribution.');
  await dialog.getByRole('checkbox',{name:'Upload my self-contained frontend source',exact:true}).check();
  await dialog.getByLabel('Release version',{exact:true}).fill('unfinished version');
  await dialog.getByRole('button',{name:'Back',exact:true}).click();
  await dialog.getByRole('button',{name:'Continue to contribution',exact:true}).click();
  await expect(dialog.getByLabel('Release version',{exact:true})).toBeVisible();
  await expect(dialog.getByLabel('Release version',{exact:true})).toHaveValue('unfinished version');
  expect(await dialog.getByLabel('Release version',{exact:true}).evaluate((field:HTMLInputElement)=>field.validity.patternMismatch)).toBe(true);
  await dialog.getByRole('button',{name:'Save component draft',exact:true}).click();
  await expect(dialog).toBeVisible();
  await expect(page.getByRole('heading',{name:'An unfinished contribution',exact:true})).toHaveCount(0);
});
test('an empty source ZIP cannot silently save a base recipe as the creator upload',async({page})=>{
  await page.goto('/preview/components');
  await page.getByRole('button',{name:'New component',exact:true}).click();
  const dialog=page.getByRole('dialog');
  await dialog.getByLabel('Component title',{exact:true}).fill('An empty source upload');
  await dialog.getByLabel('Summary',{exact:true}).fill('This upload must fail without creating a recipe draft.');
  await dialog.getByRole('button',{name:'Continue to contribution',exact:true}).click();
  await dialog.getByLabel('Your contribution & intended use',{exact:true}).fill('I created the source and preserve its accurate MIT attribution.');
  await dialog.getByRole('checkbox',{name:'Upload my self-contained frontend source',exact:true}).check();
  await dialog.getByLabel('Source ZIP',{exact:true}).setInputFiles({name:'empty.zip',mimeType:'application/zip',buffer:Buffer.alloc(0)});
  await dialog.getByLabel('Synthetic preview interaction',{exact:true}).fill('Try the synthetic interaction.');
  await dialog.getByRole('checkbox',{name:/I preserve/}).check();
  await dialog.getByRole('button',{name:'Save component draft',exact:true}).click();
  await expect(dialog).toBeVisible();
  await expect(dialog.getByRole('alert')).toContainText('Choose a nonempty source ZIP up to 5 MiB.');
  await expect(dialog.getByLabel('Component title',{exact:true})).toHaveValue('An empty source upload');
  await expect(page.getByRole('heading',{name:'An empty source upload',exact:true})).toHaveCount(0);
});
test('new synthetic form exposes inline error, focus, loading and success',async({page})=>{
  await page.goto('/components/feedback-form');const frame=page.frameLocator('.kit-preview-stage iframe');
  await frame.getByRole('button',{name:'Send feedback',exact:true}).click();
  await expect(frame.getByLabel('Sample feedback',{exact:true})).toHaveAttribute('aria-invalid','true');
  await expect(frame.getByText('Add at least 10 characters of sample feedback.',{exact:true})).toBeVisible();
  await frame.getByLabel('Sample feedback',{exact:true}).fill('A useful synthetic note.');
  await frame.getByRole('button',{name:'Send feedback',exact:true}).click();
  await expect(frame.getByRole('button',{name:'Sending…',exact:true})).toBeDisabled();
  await expect(frame.locator('output')).toContainText('Sample feedback saved');
});
test('18 additional seeds expose distinct working controls',async({page})=>{
  const probes: [string,string,string,string][] = [
    ['workspace-switcher','button','Open projects','Studio projects opened'],
    ['section-tabs','button','Files','Files selected'],
    ['search-navigation','button','Settings','Settings opened'],
    ['folder-rail','button','README','README opened'],
    ['filter-rail','button','Clear filter','3 projects shown'],
    ['settings-rail','checkbox','Project updates','Sample updates disabled'],
    ['feedback-form','form','Sample feedback','Sample feedback saved'],
    ['estimate-form','button','Calculate estimate','8 sample effort days'],
    ['step-form','step','Sample project name','Sample brief saved'],
    ['comparison-card','button','Use Studio','Studio selected'],
    ['expandable-case-study','button','Helpful · 0','Feedback recorded'],
    ['task-card','button','Mark complete','Sample task completed'],
    ['access-code-form','code','Sample access code','Sample access verified'],
    ['recovery-state','button','Try sample recovery','No email was sent'],
    ['session-panel','button','End sample session','No real session was changed'],
    ['activity-dashboard','range','Time range','30-day synthetic summary'],
    ['sortable-inventory','button','Sort names descending','3 items'],
    ['release-checklist','release','','Sample release ready'],
  ];
  for(const [slug,kind,label,result] of probes){
    await page.goto('/components/'+slug);const frame=page.frameLocator('.kit-preview-stage iframe');
    if(kind==='form'){await frame.getByLabel(label,{exact:true}).fill('Useful sample feedback.');await frame.getByRole('button',{name:'Send feedback',exact:true}).click();}
    else if(kind==='step'){await frame.getByLabel(label,{exact:true}).fill('Sample garden');await frame.getByRole('button',{name:'Continue',exact:true}).click();await frame.getByRole('button',{name:'Save sample brief',exact:true}).click();}
    else if(kind==='code'){await frame.getByLabel(label,{exact:true}).fill('246810');await frame.getByRole('button',{name:'Verify sample code',exact:true}).click();}
    else if(kind==='range')await frame.getByLabel(label,{exact:true}).selectOption('30');
    else if(kind==='release'){for(const name of ['Source reviewed','Licence preserved','Keyboard tested'])await frame.getByLabel(name,{exact:true}).check();}
    else if(kind==='checkbox'){await frame.getByRole('button',{name:'Notifications',exact:true}).click();await frame.getByLabel(label,{exact:true}).uncheck();}
    else await frame.getByRole('button',{name:label,exact:true}).click();
    await expect(frame.locator('output')).toContainText(result);await reflow(page);
  }
});
test('new routes and role cards reflow across the five required widths',async({page})=>{
  for(const width of [320,390,768,1024,1440]){
    await page.setViewportSize({width,height:1000});
    for(const path of ['/components','/components/feedback-form','/preview/components','/preview/workspace','/saved/components']){await page.goto(path);await reflow(page);}
  }
});
