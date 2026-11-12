import {test,expect,type Route} from '@playwright/test';
import {readFileSync} from 'node:fs';
type CheckoutOptions={handler:(result:{razorpay_payment_id:string;razorpay_signature:string})=>void|Promise<void>};
type CheckoutFixtureWindow=Omit<Window,'Razorpay'>&{checkoutOptions:CheckoutOptions|null;checkoutClosed:number;Razorpay:new(options:CheckoutOptions)=>{open:()=>void;close:()=>void;on:()=>void}};
const A='00000000-0000-4000-8000-000000000001',B='00000000-0000-4000-8000-000000000002';
const info=(page=0,hasMore=false)=>({page,size:50,hasMore,totalItems:hasMore?51:1,totalPages:hasMore?2:1});
const denied={error:{code:'UNAUTHENTICATED',message:'The account bound to this page is no longer current.'}};

test('a successful business reload under another actor drops the previous private index',async({page})=>{
 let actor=A;const calls:{path:string;account?:string}[]=[];
 const shared=[{id:'shared-one',name:'Shared business one',summary:'Shared',ownerId:A},{id:'shared-two',name:'Shared business two',summary:'Shared',ownerId:A}],privateBusiness={id:'private',name:'A-only private business',summary:'Private',ownerId:A};
 await page.route('**/api/v1/**',async route=>{const url=new URL(route.request().url()),path=url.pathname.replace('/api/v1',''),bound=route.request().headers()['x-getlancer-account'];calls.push({path,account:bound});
  if(path==='/me')return route.fulfill({json:{id:actor,roles:['CLIENT']}});
  if(path.startsWith('/businesses')&&bound!==actor)return route.fulfill({status:401,json:denied});
  if(path==='/businesses')return route.fulfill({json:{items:actor===A?[...shared,privateBusiness]:shared,invitations:[],itemsPage:info(),invitationsPage:info()}});
  const business=shared.find(item=>path==='/businesses/'+item.id);if(business)return route.fulfill({json:{business,myRole:'OWNER',members:[],invitations:[],activity:[],membersPage:info(),invitationsPage:info(),activityPage:info()}});
  return route.fulfill({json:{items:[],...info()}});
 });
 await page.goto('/workspace/business');await expect(page.getByRole('option',{name:'A-only private business',exact:true})).toHaveCount(1);
 actor=B;await page.getByLabel('Business workspace',{exact:true}).selectOption('shared-two');
 await expect(page.getByRole('option',{name:'A-only private business',exact:true})).toHaveCount(0);await expect(page.getByLabel('Business workspace',{exact:true})).toHaveValue('shared-two');
 expect(calls.filter(call=>call.path==='/businesses').map(call=>call.account)).toEqual([A,B]);
});

test('team continuation cannot append a current-cookie page to another actor’s invitations',async({page})=>{
 let actor=A,continuations=0;
 await page.route('**/api/v1/**',route=>{const url=new URL(route.request().url()),path=url.pathname.replace('/api/v1','');
  if(path==='/me')return route.fulfill({json:{id:actor,roles:['CLIENT'],emailVerified:true}});
  if(path==='/me/teams'){
   expect(route.request().headers()['x-getlancer-account']).toBe(A);
   if(url.searchParams.has('collection')){continuations++;expect(actor).toBe(B);return route.fulfill({status:401,json:denied});}
   return route.fulfill({json:{items:[],invitations:[{id:'invitation',userId:A,teamId:'team',teamName:'A private membership invitation',name:'A',role:'MEMBER',membershipType:'CONTRACT',status:'PENDING',expiresAt:null,projectLabel:null}],applications:[],requests:[],canCreate:false,pages:{invitations:info(0,true)}}});
  }return route.fulfill({json:{items:[]}});
 });
 await page.goto('/workspace/teams');await expect(page.getByRole('heading',{name:'A private membership invitation',exact:true})).toBeVisible();actor=B;
 await page.getByRole('button',{name:'Load more of your invitations',exact:true}).click();await expect(page.getByRole('heading',{name:'A private membership invitation',exact:true})).toHaveCount(0);await expect(page.getByRole('alert')).toContainText('no longer current');expect(continuations).toBe(1);
});

test('saved continuation sends its original actor and clears all retained associations on handoff',async({page})=>{
 let actor=A;
 await page.route('**/api/v1/**',route=>{const url=new URL(route.request().url());if(url.pathname==='/api/v1/me')return route.fulfill({json:{id:actor,roles:['CLIENT']}});
  expect(route.request().headers()['x-getlancer-account']).toBe(A);
  if(url.searchParams.get('page')==='1'){expect(actor).toBe(B);return route.fulfill({status:401,json:denied});}
  return route.fulfill({json:{items:[{id:'saved',slug:'saved',title:'A saved association',summary:'Original work',available:true}],...info(0,true)}});
 });
 await page.goto('/saved');await expect(page.getByRole('link',{name:'A saved association',exact:true})).toBeVisible();actor=B;await page.getByRole('button',{name:'Load more saved projects',exact:true}).click();await expect(page.getByRole('link',{name:'A saved association',exact:true})).toHaveCount(0);await expect(page.getByRole('alert')).toContainText('no longer current');
});

test('education child denial invalidates an older successful parent queue response',async({page})=>{
 const seed=JSON.parse(readFileSync('backend/src/main/resources/catalog/education.json','utf8'))[0].release;
 const release={...seed,status:'PENDING',academic:{revision:1,shareAcademicDetails:false,institution:'Synthetic private institution',academicYear:'2026',branch:'Synthetic private branch'},reviewReason:'Synthetic private review feedback'};
 let reads=0,queue:Route|undefined,inspection:Route|undefined;
 await page.route('**/api/v1/me',route=>route.fulfill({json:{id:A,roles:['ADMIN']}}));
 await page.route('**/api/v1/admin/education-releases**',route=>{const path=new URL(route.request().url()).pathname;
  expect(route.request().headers()['x-getlancer-account']).toBe(A);
  if(path.endsWith('/package')){inspection=route;return;}
  if(reads++===0)return route.fulfill({json:{items:[release],hasMore:false}});queue=route;
 });
 await page.goto('/admin/education');await expect(page.getByText('Private review reason: Synthetic private review feedback',{exact:true})).toBeVisible();await page.getByRole('button',{name:'Inspect private source ZIP',exact:true}).click();await expect.poll(()=>Boolean(inspection)).toBe(true);
 await page.getByRole('button',{name:'Refresh review queue',exact:true}).click();await expect.poll(()=>Boolean(queue)).toBe(true);
 await inspection!.fulfill({status:401,json:denied});await expect(page.getByRole('alert')).toContainText('no longer current');await queue!.fulfill({json:{items:[release],hasMore:false}});
 await expect(page.getByText('Private review reason: Synthetic private review feedback',{exact:true})).toHaveCount(0);await expect(page.getByRole('heading',{name:release.snapshot.title,exact:true})).toHaveCount(0);
});

test('college child denial prevents a pending parent save refresh from restoring private contexts',async({page})=>{
 const product='00000000-0000-4000-8000-000000000010',record={productId:product,title:'Synthetic private college context',category:'FULL_STACK',language:'Java',problem:'An original problem with sufficiently clear source evidence.',outcome:'A reproducible outcome with verified behavior.',prerequisites:'A local Java runtime.',contribution:'An original implementation with documented tests.',institution:'Private college',shareAcademicDetails:false,status:'PENDING',reviewReason:'Synthetic private college feedback'};
 let metadataReads=0,metadata:Route|undefined,child:Route|undefined;
 await page.route('**/api/v1/**',route=>{const url=new URL(route.request().url()),path=url.pathname.replace('/api/v1','');if(path==='/me')return route.fulfill({json:{id:A,roles:['DEVELOPER']}});
  expect(route.request().headers()['x-getlancer-account']).toBe(A);
  if(path==='/me/college-projects'){if(metadataReads++===0)return route.fulfill({json:{items:[record],hasMore:false}});metadata=route;return;}
  if(path==='/developer/products')return route.fulfill({json:{items:[{id:product,title:'Owned showcase'}],hasMore:false}});
  if(path==='/me/education-releases'){child=route;return;}
  return route.fulfill({json:{ok:true}});
 });
 await page.goto('/workspace/college-projects');await expect.poll(()=>Boolean(child)).toBe(true);await page.getByRole('button',{name:'Edit college context',exact:true}).click();await page.getByRole('checkbox',{name:/I have permission to showcase/}).check();await page.getByRole('button',{name:'Save context for review',exact:true}).click();await expect.poll(()=>Boolean(metadata)).toBe(true);
 await child!.fulfill({status:403,json:denied});await expect(page.getByRole('alert').first()).toContainText('no longer current');await metadata!.fulfill({json:{items:[record],hasMore:false}});
 await expect(page.getByRole('heading',{name:'Synthetic private college context',exact:true})).toHaveCount(0);await expect(page.getByRole('button',{name:'New education release',exact:true})).toHaveCount(0);await expect(page.getByRole('link',{name:'Log in',exact:true})).toBeVisible();
});

test('trust continuation cannot merge owner records after the session actor changes',async({page})=>{
 let actor=A;
 const profile={availability_status:'AVAILABLE_NOW',booked_until:null,availability_confirmed_at:null};
 await page.route('**/api/v1/**',route=>{const url=new URL(route.request().url());if(url.pathname==='/api/v1/me')return route.fulfill({json:{id:actor,roles:['DEVELOPER']}});expect(route.request().headers()['x-getlancer-account']).toBe(A);if(url.searchParams.has('collection')){expect(actor).toBe(B);return route.fulfill({status:401,json:denied});}return route.fulfill({json:{profile,activeSlotLimit:3,projects:[{id:'private',title:'A private repository evidence',repository_url:'https://github.com/example/private',verification_status:'PENDING',challenge:'A private proof challenge',demo_health:'UNKNOWN',demo_checked_at:null}],awards:[],pages:{projects:info(0,true),awards:info()}}});});
 await page.goto('/workspace/trust');await page.getByRole('tab',{name:'Project evidence',exact:true}).click();await expect(page.getByText('A private proof challenge',{exact:true})).toBeVisible();actor=B;await page.getByRole('button',{name:'Load more project evidence',exact:true}).click();await expect(page.getByText('A private proof challenge',{exact:true})).toHaveCount(0);await expect(page.getByRole('alert')).toContainText('no longer current');
});

test('legacy component checkout callbacks cannot verify after an actor denial closes the operation',async({page})=>{
 let actor=A,verified=0;
 const pricing={amountMinor:10000,enabled:true,salesEnabled:true,configured:true,currency:'INR',mode:'test',reason:''},purchase={id:'receipt',amountMinor:10000,currency:'INR',mode:'test',status:'ORDER_CREATED',orderId:'order_fixture',keyId:'key_fixture',grantsSlot:false};
 await page.addInitScript(()=>{const state=window as unknown as CheckoutFixtureWindow;state.checkoutOptions=null;state.checkoutClosed=0;state.Razorpay=class{constructor(options:CheckoutOptions){state.checkoutOptions=options;}open(){}close(){state.checkoutClosed++;}on(){}};});
 await page.route('**/api/v1/**',route=>{const path=new URL(route.request().url()).pathname.replace('/api/v1','');if(path==='/me')return route.fulfill({json:{id:actor,roles:['DEVELOPER']}});expect(route.request().headers()['x-getlancer-account']).toBe(A);if(path.endsWith('/verify'))verified++;if(route.request().method()==='POST'&&actor!==A)return route.fulfill({status:401,json:denied});if(path.endsWith('/verify'))return route.fulfill({json:{...purchase,status:'CAPTURED'}});if(path==='/me/components')return route.fulfill({json:{items:[],capacity:{free:3,purchased:0,used:0,limit:3},pricing}});if(path==='/me/component-slot-purchases')return route.fulfill({json:route.request().method()==='POST'?purchase:{items:[purchase],hasMore:false}});return route.fulfill({json:purchase});});
 await page.goto('/workspace/components');await page.getByRole('button',{name:'Review price & buy one slot',exact:true}).click();await page.getByRole('checkbox',{name:'I accept the displayed price and publishing-capacity policy.',exact:true}).check();await page.getByRole('button',{name:'Continue to secure payment',exact:true}).click();await expect.poll(()=>page.evaluate(()=>Boolean((window as unknown as CheckoutFixtureWindow).checkoutOptions))).toBe(true);
 actor=B;await page.getByRole('button',{name:'Reconcile payment',exact:true}).click();await expect(page.getByRole('link',{name:'Log in',exact:true})).toBeVisible();expect(await page.evaluate(()=>(window as unknown as CheckoutFixtureWindow).checkoutClosed)).toBe(1);await page.evaluate(async()=>{const options=(window as unknown as CheckoutFixtureWindow).checkoutOptions;if(!options)throw Error('Checkout was not opened.');await options.handler({razorpay_payment_id:'pay_dummy',razorpay_signature:'dummy'});});expect(verified).toBe(0);
});
