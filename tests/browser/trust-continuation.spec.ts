import {test,expect,type Route} from '@playwright/test';

const profile={availability_status:'AVAILABLE_NOW',booked_until:null,availability_confirmed_at:null};
const project=(n:number)=>({id:'project-'+n,title:'Retained owner project '+n,repository_url:'https://github.com/example/project-'+n,demo_health:'UNKNOWN',demo_checked_at:null,verification_status:null});
const verified=(n:number)=>({product_id:'verified-'+n,title:'Verified repository '+n,repository_url:'https://github.com/example/verified-'+n});
function metadata(page:number,total:number,size=100){return {page,size,hasMore:(page+1)*size<total,totalItems:total,totalPages:Math.ceil(total/size)}}
const emptyQueue=()=>({verified:[],verifications:[],eligibleOutcomes:[],pages:{verified:metadata(0,0),verifications:metadata(0,0),eligibleOutcomes:metadata(0,0)}});

test('administrator reaches retained verified evidence beyond 100 and revokes it',async({page})=>{
 const records=Array.from({length:105},(_,n)=>verified(n)),revoked:string[]=[],requests:string[]=[];
 await page.route('**/api/v1/me',route=>route.fulfill({json:{roles:['ADMIN']}}));
 await page.route('**/api/v1/admin/trust*',route=>{const url=new URL(route.request().url()),collection=url.searchParams.get('collection'),n=Number(url.searchParams.get('page')||0);requests.push(url.search);const rows=records.filter(row=>!revoked.includes(row.product_id));return route.fulfill({json:collection?{verified:rows.slice(n*100,(n+1)*100),pages:{verified:metadata(n,rows.length)}}:{...emptyQueue(),verified:rows.slice(0,100),pages:{...emptyQueue().pages,verified:metadata(0,rows.length)}}})});
 await page.route('**/api/v1/admin/verifications/*/revoke',async route=>{expect(route.request().postDataJSON().reason).toBe('README no longer contains the verified ownership proof.');revoked.push(route.request().url().split('/').at(-2)!);await route.fulfill({json:{ok:true}})});
 await page.goto('/workspace/trust');
 await expect(page.getByRole('heading',{name:'Verified repository 99',exact:true})).toBeVisible();
 await expect(page.getByRole('heading',{name:'Verified repository 104',exact:true})).toHaveCount(0);
 expect(requests).toEqual(['']);
 const more=page.getByRole('button',{name:'Load more reviewed repository evidence',exact:true});await more.focus();await more.press('Enter');
 const retained=page.locator('form').filter({has:page.getByRole('heading',{name:'Verified repository 104',exact:true})});
 await expect(retained).toBeVisible();
 await expect(page.getByRole('status')).toHaveText('5 more records loaded.');
 await expect(page.getByRole('status')).toBeFocused();
 expect(requests[1]).toBe('?collection=verified&page=1&size=100');
 await retained.getByLabel('Reason for revoking evidence').fill('README no longer contains the verified ownership proof.');
 await retained.getByRole('button',{name:'Revoke repository evidence',exact:true}).click();
 await expect(page.getByRole('status')).toHaveText('Your changes have been saved.');
 expect(revoked).toEqual(['verified-104']);
 await page.getByRole('button',{name:'Load more reviewed repository evidence',exact:true}).click();
 await expect(page.getByRole('heading',{name:'Verified repository 103',exact:true})).toBeVisible();
 await expect(page.getByRole('heading',{name:'Verified repository 104',exact:true})).toHaveCount(0);
});

test('owner continues projects and awards, retries the same page and can request old draft evidence',async({page})=>{
 const projects=Array.from({length:105},(_,n)=>project(n)),awards=Array.from({length:103},(_,n)=>({inquiry_id:'award-'+n,reason:'Retained award '+n,created_at:'2026-01-01T00:00:00Z'})),requests:string[]=[];
 let fail=true,requested='';
 await page.route('**/api/v1/me',route=>route.fulfill({json:{roles:['DEVELOPER']}}));
 await page.route('**/api/v1/developer/trust*',async route=>{const url=new URL(route.request().url()),collection=url.searchParams.get('collection'),n=Number(url.searchParams.get('page')||0);requests.push(url.search);if(collection==='projects'&&fail){fail=false;await route.fulfill({status:503,json:{error:{message:'Trust page temporarily unavailable.'}}});return}const current=projects.map(row=>row.id===requested?{...row,verification_status:'PENDING',challenge:'private-old-draft-challenge',expires_at:'2026-12-01T00:00:00Z'}:row);await route.fulfill({json:collection?{[collection]:(collection==='projects'?current:awards).slice(n*100,(n+1)*100),pages:{[collection]:metadata(n,collection==='projects'?105:103)}}:{profile,activeSlotLimit:3,projects:current.slice(0,100),awards:awards.slice(0,100),pages:{projects:metadata(0,105),awards:metadata(0,103)}}})});
 await page.route('**/api/v1/developer/products/*/verification',async route=>{requested=route.request().url().split('/').at(-2)!;await route.fulfill({json:{status:'PENDING'}})});
 await page.goto('/workspace/trust');await page.getByRole('tab',{name:'Project evidence',exact:true}).click();
 await page.getByRole('button',{name:'Load more project evidence',exact:true}).click();
 await expect(page.getByRole('alert')).toHaveText('Trust page temporarily unavailable.');
 await expect(page.getByRole('heading',{name:'Retained owner project 99',exact:true})).toBeVisible();
 await page.getByRole('button',{name:'Load more project evidence',exact:true}).click();
 await expect(page.getByRole('heading',{name:'Retained owner project 104',exact:true})).toBeVisible();
 expect(requests.slice(1,3)).toEqual(['?collection=projects&page=1&size=100','?collection=projects&page=1&size=100']);
 await page.getByRole('tab',{name:'Earned capacity',exact:true}).click();await page.getByRole('button',{name:'Load more earned-capacity awards',exact:true}).click();
 await expect(page.getByText('Retained award 102',{exact:true})).toBeVisible();
 await page.getByRole('tab',{name:'Project evidence',exact:true}).click();
 await expect(page.getByRole('button',{name:'Load more project evidence',exact:true})).toHaveCount(0);
 const retained=page.locator('article').filter({has:page.getByRole('heading',{name:'Retained owner project 104',exact:true})});
 await retained.getByRole('button',{name:'Request repository review',exact:true}).click();
 await expect(page.getByRole('status')).toHaveText('Your changes have been saved.');expect(requested).toBe('project-104');
 await page.getByRole('button',{name:'Load more project evidence',exact:true}).click();
 await expect(page.getByText('private-old-draft-challenge',{exact:true})).toBeVisible();
});

test('a page arriving after an administrator decision cannot restore stale evidence',async({page})=>{
 let held:Route|undefined,reviewed=false,pageCount=0;
 const pending={product_id:'pending',title:'Pending repository',repository_url:'https://github.com/example/pending',challenge:'private-pending-challenge'};
 await page.route('**/api/v1/me',route=>route.fulfill({json:{roles:['ADMIN']}}));
 await page.route('**/api/v1/admin/trust*',async route=>{const url=new URL(route.request().url());if(url.searchParams.has('collection')){if(pageCount++===0){held=route;return}expect(url.searchParams.get('page')).toBe('1');await route.fulfill({json:{verified:[{...verified(103),title:'Current verified repository'}],pages:{verified:metadata(1,104)}}});return}await route.fulfill({json:{...emptyQueue(),verified:[verified(0)],verifications:reviewed?[]:[pending],pages:{...emptyQueue().pages,verified:metadata(0,105),verifications:metadata(0,reviewed?0:1)}}})});
 await page.route('**/api/v1/admin/verifications/pending/approve',async route=>{reviewed=true;await route.fulfill({json:{ok:true}})});
 await page.goto('/workspace/trust');await page.getByRole('button',{name:'Load more reviewed repository evidence',exact:true}).click();
 await expect.poll(()=>Boolean(held)).toBe(true);
 const form=page.locator('form').filter({has:page.getByRole('heading',{name:'Pending repository',exact:true})});await form.getByLabel('Review evidence').fill('Exact README proof was checked against this challenge.');await form.getByRole('button',{name:'Save decision',exact:true}).click();
 await expect(page.getByRole('status')).toHaveText('Your changes have been saved.');
 await held!.fulfill({json:{verified:[{...verified(104),title:'Stale revoked repository'}],pages:{verified:metadata(1,105)}}});
 await page.getByRole('button',{name:'Load more reviewed repository evidence',exact:true}).click();
 await expect(page.getByRole('heading',{name:'Current verified repository',exact:true})).toBeVisible();
 await expect(page.getByRole('heading',{name:'Stale revoked repository',exact:true})).toHaveCount(0);
 await expect(page.getByRole('heading',{name:'Pending repository',exact:true})).toHaveCount(0);
});

test('changed administrator authority clears private records when a later page is denied',async({page})=>{
 await page.route('**/api/v1/me',route=>route.fulfill({json:{roles:['ADMIN']}}));
 await page.route('**/api/v1/admin/trust*',route=>{const url=new URL(route.request().url());return url.searchParams.has('collection')?route.fulfill({status:403,json:{error:{message:'Administrator authentication is required.',code:'FORBIDDEN'}}}):route.fulfill({json:{...emptyQueue(),verifications:[{product_id:'private',title:'Private pending repository',repository_url:'https://github.com/example/private',challenge:'private-proof-challenge'}],pages:{...emptyQueue().pages,verifications:metadata(0,101)}}})});
 await page.goto('/workspace/trust');await expect(page.getByText('private-proof-challenge',{exact:true})).toBeVisible();
 await page.getByRole('button',{name:'Load more repository requests',exact:true}).click();
 await expect(page.getByRole('alert')).toContainText('Administrator authentication is required.');
 await expect(page.getByText('private-proof-challenge',{exact:true})).toHaveCount(0);
 await expect(page.getByRole('heading',{name:'Private pending repository',exact:true})).toHaveCount(0);
});
